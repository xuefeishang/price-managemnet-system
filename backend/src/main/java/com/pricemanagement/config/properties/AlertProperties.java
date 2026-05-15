package com.pricemanagement.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 告警配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "alert")
public class AlertProperties {

    /**
     * 是否启用告警
     */
    private boolean enabled = false;

    /**
     * 钉钉告警配置
     */
    private DingTalk dingTalk = new DingTalk();

    /**
     * 企业微信告警配置
     */
    private WeChat weChat = new WeChat();

    /**
     * 告警阈值配置
     */
    private Threshold threshold = new Threshold();

    @Data
    public static class DingTalk {
        /**
         * Webhook URL
         */
        private String webhook;

        /**
         * 签名密钥
         */
        private String secret;

        /**
         * 是否启用
         */
        private boolean enabled = false;
    }

    @Data
    public static class WeChat {
        /**
         * Webhook URL
         */
        private String webhook;

        /**
         * 是否启用
         */
        private boolean enabled = false;
    }

    @Data
    public static class Threshold {
        /**
         * 内存使用率告警阈值（百分比）
         */
        private double memoryUsage = 90.0;

        /**
         * CPU 使用率告警阈值（百分比）
         */
        private double cpuUsage = 80.0;

        /**
         * 数据库连接池使用率告警阈值（百分比）
         */
        private double dbPoolUsage = 80.0;

        /**
         * 错误率告警阈值（百分比）
         */
        private double errorRate = 5.0;
    }
}
