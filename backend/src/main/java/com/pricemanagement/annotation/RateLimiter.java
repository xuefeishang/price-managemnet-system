package com.pricemanagement.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * API 限流注解
 * 基于 Redis 实现滑动窗口限流
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimiter {

    /**
     * 限流 key 前缀
     */
    String key() default "";

    /**
     * 时间窗口（秒）
     */
    int time() default 60;

    /**
     * 时间窗口内允许的最大请求数
     */
    int count() default 100;

    /**
     * 限流策略
     */
    LimitType limitType() default LimitType.DEFAULT;

    /**
     * 提示信息
     */
    String message() default "访问过于频繁，请稍后再试";

    enum LimitType {
        DEFAULT,
        IP,
        USER
    }
}
