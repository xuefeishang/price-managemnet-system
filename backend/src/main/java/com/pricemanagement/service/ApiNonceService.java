package com.pricemanagement.service;

import com.pricemanagement.config.properties.ApiKeyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiNonceService {

    private final ApiKeyProperties properties;
    private final RedisTemplate<String, Object> redisTemplate;
    private final Map<String, Instant> localNonceCache = new ConcurrentHashMap<>();

    public boolean registerNonce(String appId, String nonce) {
        cleanupLocalCache();
        String key = "api:nonce:" + appId + ":" + nonce;
        try {
            Boolean success = redisTemplate.opsForValue().setIfAbsent(
                    key,
                    "1",
                    Duration.ofSeconds(properties.getNonceTtlSeconds()));
            if (success != null) {
                return success;
            }
        } catch (Exception ex) {
            log.warn("Redis nonce check failed, using local fallback: {}", ex.getMessage());
        }
        Instant expiresAt = Instant.now().plusSeconds(properties.getNonceTtlSeconds());
        return localNonceCache.putIfAbsent(key, expiresAt) == null;
    }

    private void cleanupLocalCache() {
        Instant now = Instant.now();
        localNonceCache.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }
}
