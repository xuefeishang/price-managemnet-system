package com.pricemanagement.service;

import com.pricemanagement.entity.ApiKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiRateLimitService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final Map<String, LocalCounter> localCounters = new ConcurrentHashMap<>();

    public boolean allow(ApiKey apiKey) {
        return allowMinute(apiKey) && allowDaily(apiKey);
    }

    private boolean allowMinute(ApiKey apiKey) {
        Integer limit = apiKey.getRateLimitPerMinute();
        if (isUnlimited(limit)) {
            return true;
        }
        String key = "api:rate:min:" + apiKey.getAppId() + ":" + LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        return incrementAndCheck(key, limit, Duration.ofMinutes(2));
    }

    private boolean allowDaily(ApiKey apiKey) {
        Integer limit = apiKey.getDailyLimit();
        if (isUnlimited(limit)) {
            return true;
        }
        String key = "api:rate:day:" + apiKey.getAppId() + ":" + LocalDate.now();
        return incrementAndCheck(key, limit, Duration.ofDays(2));
    }

    private boolean incrementAndCheck(String key, int limit, Duration ttl) {
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, ttl);
            }
            if (count != null) {
                return count <= limit;
            }
        } catch (Exception ex) {
            log.warn("Redis rate limit failed, using local fallback: {}", ex.getMessage());
        }
        LocalCounter counter = localCounters.compute(key, (ignored, existing) -> {
            if (existing == null || existing.expiresAt().isBefore(LocalDateTime.now())) {
                return new LocalCounter(new AtomicInteger(1), LocalDateTime.now().plus(ttl));
            }
            existing.count().incrementAndGet();
            return existing;
        });
        cleanupLocalCounters();
        return counter.count().get() <= limit;
    }

    private void cleanupLocalCounters() {
        LocalDateTime now = LocalDateTime.now();
        localCounters.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private boolean isUnlimited(Integer value) {
        return value != null && value == 0;
    }

    private record LocalCounter(AtomicInteger count, LocalDateTime expiresAt) {
    }
}
