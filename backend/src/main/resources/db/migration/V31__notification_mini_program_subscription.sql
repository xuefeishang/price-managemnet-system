CREATE TABLE IF NOT EXISTS notification_mini_program_subscription (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '小程序订阅授权ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    openid VARCHAR(100) NOT NULL COMMENT '微信小程序openid',
    notification_type VARCHAR(50) NOT NULL COMMENT '通知类型',
    template_id VARCHAR(100) NOT NULL COMMENT '订阅消息模板ID',
    status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN' COMMENT '授权状态',
    available_count INT NOT NULL DEFAULT 0 COMMENT '可用授权次数',
    last_authorized_time DATETIME COMMENT '最近授权时间',
    source VARCHAR(50) COMMENT '授权来源',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT uk_notification_mini_sub_user_type_template UNIQUE (user_id, notification_type, template_id),
    INDEX idx_notification_mini_sub_user (user_id),
    INDEX idx_notification_mini_sub_template (template_id),
    INDEX idx_notification_mini_sub_type (notification_type),
    CONSTRAINT fk_notification_mini_sub_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小程序订阅消息授权表';

INSERT INTO sys_dict (category, dict_key, dict_value, extra_value, sort_order, status, remark, created_time, updated_time)
SELECT category, dict_key, dict_value, extra_value, sort_order, status, remark, created_time, updated_time
FROM (
    SELECT 'notification_mini_subscription_status' AS category, 'UNKNOWN' AS dict_key, '未知' AS dict_value, '#9CA3AF' AS extra_value, 1 AS sort_order, 'ACTIVE' AS status, '小程序订阅授权状态' AS remark, NOW() AS created_time, NOW() AS updated_time
    UNION ALL SELECT 'notification_mini_subscription_status', 'ACCEPT', '已授权', '#10B981', 2, 'ACTIVE', '小程序订阅授权状态', NOW(), NOW()
    UNION ALL SELECT 'notification_mini_subscription_status', 'REJECT', '已拒绝', '#EF4444', 3, 'ACTIVE', '小程序订阅授权状态', NOW(), NOW()
    UNION ALL SELECT 'notification_mini_subscription_status', 'BAN', '已禁用', '#64748B', 4, 'ACTIVE', '小程序订阅授权状态', NOW(), NOW()
) defaults
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict d
    WHERE d.category = defaults.category AND d.dict_key = defaults.dict_key
);
