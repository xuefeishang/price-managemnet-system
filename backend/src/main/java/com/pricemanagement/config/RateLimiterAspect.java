package com.pricemanagement.config;

import com.pricemanagement.annotation.RateLimiter;
import com.pricemanagement.exception.RateLimitException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 限流切面
 * 支持 Redis 和内存两种限流方式
 *
 * 优先使用 Redis，如果 Redis 不可用则自动降级为内存限流
 */
@Aspect
@Component
@Slf4j
public class RateLimiterAspect {

    /**
     * Redis 限流模板（可选注入）
     */
    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 内存限流存储（降级方案）
     * Key: 限流键, Value: [计数, 过期时间戳]
     */
    private final Map<String, RateLimitEntry> memoryRateLimitStore = new ConcurrentHashMap<>();

    @Before("@annotation(rateLimiter)")
    public void doBefore(JoinPoint joinPoint, RateLimiter rateLimiter) {
        String key = buildKey(joinPoint, rateLimiter);
        long time = rateLimiter.time();
        long count = rateLimiter.count();

        boolean allowed;

        // 优先使用 Redis 限流
        if (redisTemplate != null) {
            allowed = checkWithRedis(key, time, count);
        } else {
            // Redis 不可用，使用内存限流
            allowed = checkWithMemory(key, time, count);
        }

        if (!allowed) {
            log.warn("Rate limit exceeded: key={}, limit={}", key, count);
            throw new RateLimitException(rateLimiter.message());
        }
    }

    /**
     * 使用 Redis 进行限流检查
     */
    private boolean checkWithRedis(String key, long time, long count) {
        try {
            String redisKey = "rate_limit:" + key;

            Long currentCount = redisTemplate.opsForValue().increment(redisKey);

            if (currentCount != null && currentCount == 1) {
                redisTemplate.expire(redisKey, time, TimeUnit.SECONDS);
            }

            log.debug("Redis rate limit check: key={}, current={}, limit={}", key, currentCount, count);

            return currentCount == null || currentCount <= count;
        } catch (Exception e) {
            log.warn("Redis rate limit failed, falling back to memory: {}", e.getMessage());
            // Redis 操作失败，降级为内存限流
            return checkWithMemory(key, time, count);
        }
    }

    /**
     * 使用内存进行限流检查（降级方案）
     */
    private boolean checkWithMemory(String key, long time, long count) {
        long now = System.currentTimeMillis();
        long expireTime = now + time * 1000;

        // 清理过期的条目
        memoryRateLimitStore.entrySet().removeIf(entry -> entry.getValue().isExpired(now));

        // 获取或创建条目
        RateLimitEntry entry = memoryRateLimitStore.compute(key, (k, v) -> {
            if (v == null || v.isExpired(now)) {
                return new RateLimitEntry(expireTime);
            }
            return v;
        });

        int currentCount = entry.increment();

        log.debug("Memory rate limit check: key={}, current={}, limit={}", key, currentCount, count);

        return currentCount <= count;
    }

    /**
     * 构建限流键
     */
    private String buildKey(JoinPoint joinPoint, RateLimiter rateLimiter) {
        String className = joinPoint.getSignature().getDeclaringType().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        StringBuilder key = new StringBuilder(className).append(":").append(methodName);

        switch (rateLimiter.limitType()) {
            case IP -> key.append(":").append(getClientIp());
            case USER -> key.append(":").append(getCurrentUserId());
            default -> {
                if (!rateLimiter.key().isEmpty()) {
                    key.append(":").append(rateLimiter.key());
                }
            }
        }

        return key.toString();
    }

    /**
     * 获取客户端 IP
     */
    private String getClientIp() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }
        HttpServletRequest request = attributes.getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 获取当前用户 ID
     */
    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return "anonymous";
    }

    /**
     * 内存限流条目
     */
    private static class RateLimitEntry {
        private final long expireTime;
        private final AtomicInteger count = new AtomicInteger(0);

        public RateLimitEntry(long expireTime) {
            this.expireTime = expireTime;
        }

        public int increment() {
            return count.incrementAndGet();
        }

        public boolean isExpired(long now) {
            return now > expireTime;
        }
    }
}
