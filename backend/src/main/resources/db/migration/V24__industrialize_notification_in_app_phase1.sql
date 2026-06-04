ALTER TABLE notification_message
    ADD COLUMN summary VARCHAR(300) NULL COMMENT '通知摘要',
    ADD COLUMN priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '通知优先级',
    ADD COLUMN link_type VARCHAR(50) NULL COMMENT '跳转类型',
    ADD COLUMN link_params TEXT NULL COMMENT '跳转参数JSON',
    ADD COLUMN dedupe_key VARCHAR(150) NULL COMMENT '通知幂等键',
    ADD COLUMN expire_time DATETIME NULL COMMENT '过期时间';

ALTER TABLE notification_message
    ADD INDEX idx_notification_type_created (type, created_time),
    ADD UNIQUE INDEX uk_notification_dedupe_key (dedupe_key);

ALTER TABLE notification_recipient
    ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否归档',
    ADD COLUMN archived_time DATETIME NULL COMMENT '归档时间',
    ADD COLUMN first_seen_time DATETIME NULL COMMENT '首次触达时间';

ALTER TABLE notification_recipient
    ADD INDEX idx_notification_recipient_user_time (user_id, id);

INSERT INTO sys_dict (category, dict_key, dict_value, extra_value, sort_order, status, remark, created_time, updated_time)
SELECT category, dict_key, dict_value, extra_value, sort_order, status, remark, NOW(), NOW()
FROM (
    SELECT 'notification_priority' AS category, 'LOW' AS dict_key, '低' AS dict_value, '#64748B' AS extra_value, 1 AS sort_order, 'ACTIVE' AS status, '通知优先级' AS remark
    UNION ALL SELECT 'notification_priority', 'NORMAL', '普通', '#0D6E6E', 2, 'ACTIVE', '通知优先级'
    UNION ALL SELECT 'notification_priority', 'HIGH', '高', '#F59E0B', 3, 'ACTIVE', '通知优先级'
    UNION ALL SELECT 'notification_priority', 'URGENT', '紧急', '#EF4444', 4, 'ACTIVE', '通知优先级'
    UNION ALL SELECT 'notification_link_type', 'PRICE_QUERY', '价格查询', NULL, 1, 'ACTIVE', '通知跳转类型'
    UNION ALL SELECT 'notification_link_type', 'APPROVAL_DETAIL', '审批详情', NULL, 2, 'ACTIVE', '通知跳转类型'
    UNION ALL SELECT 'notification_link_type', 'TASK_LOG', '任务日志', NULL, 3, 'ACTIVE', '通知跳转类型'
    UNION ALL SELECT 'notification_link_type', 'SYSTEM_NOTICE', '系统通知', NULL, 4, 'ACTIVE', '通知跳转类型'
    UNION ALL SELECT 'notification_business_type', 'PRICE', '价格', NULL, 1, 'ACTIVE', '通知业务类型'
    UNION ALL SELECT 'notification_business_type', 'APPROVAL', '审批', NULL, 2, 'ACTIVE', '通知业务类型'
    UNION ALL SELECT 'notification_business_type', 'TASK', '任务', NULL, 3, 'ACTIVE', '通知业务类型'
    UNION ALL SELECT 'notification_business_type', 'SYSTEM', '系统', NULL, 4, 'ACTIVE', '通知业务类型'
    UNION ALL SELECT 'notification_business_type', 'SECURITY', '安全', NULL, 5, 'ACTIVE', '通知业务类型'
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict d WHERE d.category = seed.category AND d.dict_key = seed.dict_key
);
