package com.pricemanagement.service;

import com.pricemanagement.config.properties.SecurityProperties;
import com.pricemanagement.entity.IpBlacklist;
import com.pricemanagement.entity.SecurityEvent;
import com.pricemanagement.repository.IpBlacklistRepository;
import com.pricemanagement.repository.SecurityEventRepository;
import com.pricemanagement.util.IpAddressUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class IpBlacklistService {

    private final IpBlacklistRepository ipBlacklistRepository;
    private final SecurityEventRepository securityEventRepository;
    private final SecurityProperties securityProperties;
    private final ClientIpResolver clientIpResolver;
    private final ConcurrentMap<String, CachedDecision> decisionCache = new ConcurrentHashMap<>();

    public String resolveClientIp(HttpServletRequest request) {
        return clientIpResolver.resolve(request);
    }

    @Transactional
    public MatchResult match(String ipAddress) {
        SecurityProperties.IpBlacklist properties = securityProperties.getIpBlacklist();
        if (!properties.isEnabled() || !hasText(ipAddress)) {
            return MatchResult.notBlocked();
        }
        if (IpAddressUtil.matchesAny(ipAddress, properties.getBypassSources())) {
            return MatchResult.notBlocked();
        }

        LocalDateTime now = LocalDateTime.now();
        CachedDecision cached = decisionCache.get(ipAddress);
        if (cached != null) {
            long ttlSeconds = cached.blocked()
                    ? properties.getCacheTtlSeconds()
                    : properties.getNegativeCacheTtlSeconds();
            if (cached.isFresh(now, ttlSeconds)) {
                return cached.toResult(properties.isObservationMode());
            }
            decisionCache.remove(ipAddress, cached);
        }

        Optional<IpBlacklist> activeRecord = ipBlacklistRepository.findByIpAddressAndActiveTrue(ipAddress);
        if (activeRecord.isEmpty()) {
            if (properties.getNegativeCacheTtlSeconds() > 0) {
                decisionCache.put(ipAddress, CachedDecision.notBlocked(now));
            } else {
                decisionCache.remove(ipAddress);
            }
            return MatchResult.notBlocked();
        }

        IpBlacklist record = activeRecord.get();
        if (record.getExpiresAt() != null && !record.getExpiresAt().isAfter(now)) {
            record.setActive(false);
            record.setUnbanAt(now);
            record.setUnbanReason("黑名单已过期，系统自动失效");
            ipBlacklistRepository.save(record);
            decisionCache.remove(ipAddress);
            return MatchResult.notBlocked();
        }

        CachedDecision decision = CachedDecision.blocked(record, now);
        decisionCache.put(ipAddress, decision);
        return decision.toResult(properties.isObservationMode());
    }

    public void recordHit(HttpServletRequest request, MatchResult matchResult, int statusCode) {
        if (!matchResult.blocked()) {
            return;
        }
        try {
            SecurityEvent event = new SecurityEvent();
            event.setEventType(SecurityEvent.EventType.IP_BLACKLIST_HIT);
            event.setSeverity(matchResult.observationMode() ? SecurityEvent.Severity.WARN : SecurityEvent.Severity.ERROR);
            event.setSourceIp(resolveClientIp(request));
            event.setUserAgent(truncate(request.getHeader("User-Agent"), 500));
            event.setRequestMethod(request.getMethod());
            event.setRequestUri(truncate(request.getRequestURI(), 500));
            event.setRequestParams(truncate(request.getQueryString(), 1000));
            event.setStatusCode(statusCode);
            event.setDescription(truncate("IP黑名单命中: " + matchResult.reason(), 1000));
            event.setActionTaken(matchResult.observationMode() ? "OBSERVE_ONLY" : "BLOCK_REQUEST");
            securityEventRepository.save(event);
        } catch (Exception ex) {
            log.warn("Failed to record IP blacklist security event: {}", ex.getMessage());
        }
    }

    public void evict(String ipAddress) {
        if (ipAddress != null) {
            decisionCache.remove(ipAddress);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public record MatchResult(boolean blocked, boolean observationMode, String reason, Long blacklistId) {
        static MatchResult notBlocked() {
            return new MatchResult(false, false, null, null);
        }
    }

    private record CachedDecision(boolean blocked, String reason, Long blacklistId, LocalDateTime loadedAt,
                                  LocalDateTime expiresAt) {
        static CachedDecision notBlocked(LocalDateTime loadedAt) {
            return new CachedDecision(false, null, null, loadedAt, null);
        }

        static CachedDecision blocked(IpBlacklist record, LocalDateTime loadedAt) {
            return new CachedDecision(true, record.getReason(), record.getId(), loadedAt, record.getExpiresAt());
        }

        boolean isFresh(LocalDateTime now, long ttlSeconds) {
            if (ttlSeconds <= 0) {
                return false;
            }
            if (blocked && expiresAt != null && !expiresAt.isAfter(now)) {
                return false;
            }
            return Duration.between(loadedAt, now).compareTo(Duration.ofSeconds(ttlSeconds)) < 0;
        }

        MatchResult toResult(boolean observationMode) {
            return new MatchResult(blocked, blocked && observationMode, reason, blacklistId);
        }
    }
}
