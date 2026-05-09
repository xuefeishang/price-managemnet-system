package com.pricemanagement.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 安全配置属性类
 * 集中管理所有敏感配置，包括数据库、Redis、JWT和默认用户密码
 */
@Data
@Component
@ConfigurationProperties(prefix = "security")
public class SecurityProperties {

    /**
     * JWT密钥（从环境变量 JWT_SECRET 读取）
     */
    private String jwtSecret;

    /**
     * JWT过期时间（毫秒，默认24小时）
     */
    private Long jwtExpiration = 86400000L;

    /**
     * 默认用户密码
     * 从环境变量 DEFAULT_USER_PASSWORD 读取
     * 注意：生产环境必须修改此密码
     */
    private String defaultUserPassword;

    /**
     * 是否在启动时重置默认用户密码
     * 建议生产环境设为 false，避免每次启动都重置密码
     */
    private boolean resetPasswordOnStartup = true;
}