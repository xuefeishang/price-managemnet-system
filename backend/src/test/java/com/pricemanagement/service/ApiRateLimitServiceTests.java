package com.pricemanagement.service;

import com.pricemanagement.entity.ApiKey;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ApiRateLimitServiceTests {

    @Test
    void allowReturnsTrueWithoutCountingWhenLimitsAreZero() {
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        ApiRateLimitService service = new ApiRateLimitService(redisTemplate);
        ApiKey apiKey = new ApiKey();
        apiKey.setAppId("app_test");
        apiKey.setRateLimitPerMinute(0);
        apiKey.setDailyLimit(0);

        assertTrue(service.allow(apiKey));
        verifyNoInteractions(redisTemplate);
    }
}
