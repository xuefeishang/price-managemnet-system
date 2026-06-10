CREATE TABLE IF NOT EXISTS notification_outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    payload_json TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_time DATETIME NULL,
    locked_by VARCHAR(100) NULL,
    lock_until DATETIME NULL,
    last_error_code VARCHAR(100) NULL,
    last_error_message VARCHAR(500) NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_notification_outbox_aggregate UNIQUE (aggregate_type, aggregate_id),
    INDEX idx_notification_outbox_status_retry (status, next_retry_time),
    INDEX idx_notification_outbox_lock (locked_by, lock_until)
);

CREATE TABLE IF NOT EXISTS notification_preference (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    quiet_start_time TIME NULL,
    quiet_end_time TIME NULL,
    created_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_notification_preference_user_type_channel UNIQUE (user_id, notification_type, channel),
    INDEX idx_notification_preference_user (user_id),
    INDEX idx_notification_preference_type_channel (notification_type, channel),
    CONSTRAINT fk_notification_preference_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
);

INSERT INTO sys_dict (category, dict_key, dict_value, extra_value, sort_order, status, remark, created_time, updated_time)
SELECT * FROM (
    SELECT 'notification_outbox_status' AS category, 'PENDING' AS dict_key, '待处理' AS dict_value, '#64748B' AS extra_value, 1 AS sort_order, 'ACTIVE' AS status, 'Outbox状态' AS remark, NOW() AS created_time, NOW() AS updated_time
    UNION ALL SELECT 'notification_outbox_status', 'PROCESSING', '处理中', '#3B82F6', 2, 'ACTIVE', 'Outbox状态', NOW(), NOW()
    UNION ALL SELECT 'notification_outbox_status', 'SUCCESS', '成功', '#10B981', 3, 'ACTIVE', 'Outbox状态', NOW(), NOW()
    UNION ALL SELECT 'notification_outbox_status', 'FAILED', '失败', '#EF4444', 4, 'ACTIVE', 'Outbox状态', NOW(), NOW()
    UNION ALL SELECT 'notification_channel', 'WEBHOOK', 'Webhook', NULL, 4, 'ACTIVE', '通知渠道', NOW(), NOW()
    UNION ALL SELECT 'notification_channel', 'WECHAT_WORK', '企业微信', NULL, 5, 'ACTIVE', '通知渠道', NOW(), NOW()
) AS new_dicts
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict d
    WHERE d.category = new_dicts.category AND d.dict_key = new_dicts.dict_key
);
