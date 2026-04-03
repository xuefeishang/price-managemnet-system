package com.pricemanagement.annotation;

import com.pricemanagement.entity.OperationLog.OperationType;

import java.lang.annotation.*;

/**
 * 操作日志注解
 * 标注在Controller方法上，自动记录操作日志
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {

    /**
     * 操作模块
     */
    String module() default "";

    /**
     * 操作类型
     */
    OperationType type() default OperationType.OTHER;

    /**
     * 操作描述，支持SpEL表达式
     */
    String description() default "";

    /**
     * 是否记录请求参数
     */
    boolean logParams() default true;

    /**
     * 是否记录响应数据
     */
    boolean logResponse() default false;

    /**
     * 是否记录IP地址
     */
    boolean logIp() default true;
}
