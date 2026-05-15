package com.pricemanagement.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 缓存配置
 * 支持 Redis 和内存缓存两种模式
 *
 * 优先尝试 Redis，如果 Redis 不可用则自动降级为内存缓存
 */
@Configuration
@EnableCaching
@Slf4j
public class RedisConfig {

    /**
     * 缓存名称常量
     */
    public static final String CACHE_PRODUCTS = "products";
    public static final String CACHE_CATEGORIES = "categories";
    public static final String CACHE_ORIGINS = "origins";
    public static final String CACHE_CUSTOMERS = "customers";
    public static final String CACHE_DICT = "dict";
    public static final String CACHE_USERS = "users";
    public static final String CACHE_MENU = "menu";
    public static final String CACHE_STYLE = "style";

    /**
     * 基础过期时间（秒）
     */
    private static final long BASE_TTL_SECONDS = 3600; // 1小时

    /**
     * Redis 连接工厂（可选注入）
     */
    @Autowired(required = false)
    private RedisConnectionFactory redisConnectionFactory;

    /**
     * 缓存管理器
     * 优先使用 Redis，如果 Redis 不可用则降级为内存缓存
     */
    @Bean
    @Primary
    public CacheManager cacheManager() {
        // 尝试使用 Redis
        if (redisConnectionFactory != null) {
            try {
                // 测试 Redis 连接是否可用
                redisConnectionFactory.getConnection().close();
                log.info("Redis is available, using Redis cache manager");
                return createRedisCacheManager(redisConnectionFactory);
            } catch (Exception e) {
                log.warn("Redis connection failed, falling back to in-memory cache: {}", e.getMessage());
            }
        } else {
            log.warn("Redis connection factory not available, using in-memory cache");
        }

        // 降级为内存缓存
        return createFallbackCacheManager();
    }

    /**
     * 创建 Redis 缓存管理器
     */
    private CacheManager createRedisCacheManager(RedisConnectionFactory connectionFactory) {
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // 默认配置：使用随机过期时间防止缓存雪崩
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(getRandomTtl(BASE_TTL_SECONDS)))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(stringSerializer))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(stringSerializer))
                .disableCachingNullValues();

        // 为不同缓存设置不同的过期时间
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // 产品缓存：30分钟随机过期
        cacheConfigurations.put(CACHE_PRODUCTS, RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(getRandomTtl(1800)))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(stringSerializer))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(stringSerializer))
                .disableCachingNullValues());

        // 字典缓存：2小时随机过期（字典变化较少）
        cacheConfigurations.put(CACHE_DICT, RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(getRandomTtl(7200)))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(stringSerializer))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(stringSerializer))
                .disableCachingNullValues());

        // 用户缓存：15分钟随机过期
        cacheConfigurations.put(CACHE_USERS, RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(getRandomTtl(900)))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(stringSerializer))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(stringSerializer))
                .disableCachingNullValues());

        // 菜单缓存：1小时随机过期
        cacheConfigurations.put(CACHE_MENU, RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(getRandomTtl(3600)))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(stringSerializer))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(stringSerializer))
                .disableCachingNullValues());

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }

    /**
     * 创建内存缓存管理器（降级方案）
     */
    private CacheManager createFallbackCacheManager() {
        return new ConcurrentMapCacheManager(
                CACHE_PRODUCTS,
                CACHE_CATEGORIES,
                CACHE_ORIGINS,
                CACHE_CUSTOMERS,
                CACHE_DICT,
                CACHE_USERS,
                CACHE_MENU,
                CACHE_STYLE
        );
    }

    /**
     * 获取随机过期时间
     * 基础时间的 80%-120% 随机，防止缓存雪崩
     *
     * @param baseTtl 基础过期时间（秒）
     * @return 随机过期时间（秒）
     */
    private long getRandomTtl(long baseTtl) {
        // 80% - 120% 随机因子
        double randomFactor = 0.8 + ThreadLocalRandom.current().nextDouble() * 0.4;
        return (long) (baseTtl * randomFactor);
    }
}
