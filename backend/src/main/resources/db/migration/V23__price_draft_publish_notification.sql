CREATE TABLE IF NOT EXISTS price_draft_batch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version BIGINT DEFAULT 0,
    effective_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    source_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    product_scope_snapshot TEXT,
    item_count INT DEFAULT 0,
    saved_item_count INT DEFAULT 0,
    last_modified_by BIGINT,
    published_time DATETIME,
    published_by BIGINT,
    created_by BIGINT,
    remark VARCHAR(500),
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_price_draft_batch_date_status (effective_date, status),
    INDEX idx_price_draft_batch_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='价格草稿批次表';

CREATE TABLE IF NOT EXISTS price_draft_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    base_price_id BIGINT,
    base_price_version BIGINT,
    original_price DECIMAL(15,4),
    current_price DECIMAL(15,4) NOT NULL,
    cost_price DECIMAL(15,4),
    budget_price DECIMAL(15,4),
    effective_date DATE,
    expiry_date DATE,
    unit VARCHAR(50),
    price_spec VARCHAR(200),
    item_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    last_modified_by BIGINT,
    published_price_id BIGINT,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_price_draft_item_batch_product UNIQUE (batch_id, product_id),
    INDEX idx_price_draft_item_batch (batch_id),
    INDEX idx_price_draft_item_product (product_id),
    CONSTRAINT fk_price_draft_item_batch FOREIGN KEY (batch_id) REFERENCES price_draft_batch(id),
    CONSTRAINT fk_price_draft_item_product FOREIGN KEY (product_id) REFERENCES product(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='价格草稿明细表';

CREATE TABLE IF NOT EXISTS price_publish_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    effective_date DATE NOT NULL,
    publish_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    total_count INT DEFAULT 0,
    success_count INT DEFAULT 0,
    fail_count INT DEFAULT 0,
    message TEXT,
    created_by BIGINT,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    INDEX idx_price_publish_batch (batch_id),
    INDEX idx_price_publish_date (effective_date),
    CONSTRAINT fk_price_publish_batch FOREIGN KEY (batch_id) REFERENCES price_draft_batch(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='价格发布日志表';

CREATE TABLE IF NOT EXISTS notification_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(50) NOT NULL,
    title VARCHAR(100) NOT NULL,
    content TEXT,
    business_type VARCHAR(50),
    business_id BIGINT,
    channels VARCHAR(200),
    created_by BIGINT,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    INDEX idx_notification_type (type),
    INDEX idx_notification_business (business_type, business_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知消息表';

CREATE TABLE IF NOT EXISTS notification_recipient (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    read_status VARCHAR(20) NOT NULL DEFAULT 'UNREAD',
    read_time DATETIME,
    CONSTRAINT uk_notification_message_user UNIQUE (message_id, user_id),
    INDEX idx_notification_recipient_user (user_id, read_status),
    INDEX idx_notification_recipient_message (message_id),
    CONSTRAINT fk_notification_recipient_message FOREIGN KEY (message_id) REFERENCES notification_message(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知接收人表';

CREATE TABLE IF NOT EXISTS notification_delivery_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id BIGINT NOT NULL,
    recipient_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    channel VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    provider VARCHAR(50),
    provider_message_id VARCHAR(100),
    retry_count INT DEFAULT 0,
    delivered_time DATETIME,
    error_code VARCHAR(100),
    error_message VARCHAR(500),
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_notification_delivery_message (message_id),
    INDEX idx_notification_delivery_user (user_id),
    INDEX idx_notification_delivery_status (status),
    CONSTRAINT fk_notification_delivery_message FOREIGN KEY (message_id) REFERENCES notification_message(id),
    CONSTRAINT fk_notification_delivery_recipient FOREIGN KEY (recipient_id) REFERENCES notification_recipient(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知渠道投递日志表';

CREATE TABLE IF NOT EXISTS sys_scheduled_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    version BIGINT DEFAULT 0,
    task_code VARCHAR(80) NOT NULL,
    task_name VARCHAR(100) NOT NULL,
    task_type VARCHAR(50) NOT NULL,
    cron_expression VARCHAR(100) NOT NULL,
    timezone VARCHAR(50) NOT NULL DEFAULT 'Asia/Shanghai',
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    config_json TEXT,
    lock_until DATETIME,
    locked_by VARCHAR(100),
    last_scheduled_time DATETIME,
    last_run_time DATETIME,
    next_run_time DATETIME,
    last_run_status VARCHAR(20),
    created_by BIGINT,
    remark VARCHAR(500),
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_scheduled_task_code UNIQUE (task_code),
    INDEX idx_scheduled_task_enabled (enabled),
    INDEX idx_scheduled_task_next_run (next_run_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用定时任务表';

CREATE TABLE IF NOT EXISTS sys_scheduled_task_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    task_code VARCHAR(80) NOT NULL,
    scheduled_time DATETIME,
    trigger_type VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    started_time DATETIME,
    finished_time DATETIME,
    duration_ms BIGINT,
    business_type VARCHAR(50),
    business_id BIGINT,
    message TEXT,
    error_stack TEXT,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uk_task_scheduled_time UNIQUE (task_id, scheduled_time, trigger_type),
    INDEX idx_scheduled_task_log_task (task_id),
    INDEX idx_scheduled_task_log_status (status),
    CONSTRAINT fk_scheduled_task_log_task FOREIGN KEY (task_id) REFERENCES sys_scheduled_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用定时任务执行日志表';

INSERT INTO sys_dict (category, dict_key, dict_value, extra_value, sort_order, status, remark, created_time, updated_time)
SELECT * FROM (
    SELECT 'price_draft_status' AS category,
           'DRAFT' AS dict_key,
           '草稿' AS dict_value,
           '#64748B' AS extra_value,
           1 AS sort_order,
           'ACTIVE' AS status,
           '价格草稿状态' AS remark,
           NOW() AS created_time,
           NOW() AS updated_time
    UNION ALL SELECT 'price_draft_status', 'PENDING_APPROVAL', '待审批', '#F59E0B', 2, 'ACTIVE', '未来审批预留状态', NOW(), NOW()
    UNION ALL SELECT 'price_draft_status', 'APPROVED', '已通过', '#10B981', 3, 'ACTIVE', '未来审批预留状态', NOW(), NOW()
    UNION ALL SELECT 'price_draft_status', 'REJECTED', '已拒绝', '#EF4444', 4, 'ACTIVE', '未来审批预留状态', NOW(), NOW()
    UNION ALL SELECT 'price_draft_status', 'PUBLISHING', '发布中', '#3B82F6', 5, 'ACTIVE', '价格草稿状态', NOW(), NOW()
    UNION ALL SELECT 'price_draft_status', 'PUBLISHED', '已发布', '#10B981', 6, 'ACTIVE', '价格草稿状态', NOW(), NOW()
    UNION ALL SELECT 'price_draft_status', 'CANCELLED', '已取消', '#9CA3AF', 7, 'ACTIVE', '价格草稿状态', NOW(), NOW()
    UNION ALL SELECT 'price_publish_type', 'MANUAL', '手动发布', NULL, 1, 'ACTIVE', '发布类型', NOW(), NOW()
    UNION ALL SELECT 'price_publish_type', 'SCHEDULED', '定时发布', NULL, 2, 'ACTIVE', '发布类型', NOW(), NOW()
    UNION ALL SELECT 'price_publish_status', 'SUCCESS', '成功', '#10B981', 1, 'ACTIVE', '发布结果', NOW(), NOW()
    UNION ALL SELECT 'price_publish_status', 'FAILED', '失败', '#EF4444', 2, 'ACTIVE', '发布结果', NOW(), NOW()
    UNION ALL SELECT 'price_publish_status', 'PARTIAL', '部分成功', '#F59E0B', 3, 'ACTIVE', '发布结果', NOW(), NOW()
    UNION ALL SELECT 'notification_type', 'PRICE_PUBLISHED', '价格已发布', NULL, 1, 'ACTIVE', '通知类型', NOW(), NOW()
    UNION ALL SELECT 'notification_channel', 'IN_APP', '站内通知', NULL, 1, 'ACTIVE', '通知渠道', NOW(), NOW()
    UNION ALL SELECT 'notification_channel', 'APP_PUSH', 'App推送', NULL, 2, 'ACTIVE', '通知渠道', NOW(), NOW()
    UNION ALL SELECT 'notification_channel', 'MINI_PROGRAM', '小程序订阅消息', NULL, 3, 'ACTIVE', '通知渠道', NOW(), NOW()
    UNION ALL SELECT 'notification_read_status', 'UNREAD', '未读', '#F59E0B', 1, 'ACTIVE', '阅读状态', NOW(), NOW()
    UNION ALL SELECT 'notification_read_status', 'READ', '已读', '#10B981', 2, 'ACTIVE', '阅读状态', NOW(), NOW()
    UNION ALL SELECT 'notification_delivery_status', 'PENDING', '待投递', '#64748B', 1, 'ACTIVE', '投递状态', NOW(), NOW()
    UNION ALL SELECT 'notification_delivery_status', 'SUCCESS', '成功', '#10B981', 2, 'ACTIVE', '投递状态', NOW(), NOW()
    UNION ALL SELECT 'notification_delivery_status', 'FAILED', '失败', '#EF4444', 3, 'ACTIVE', '投递状态', NOW(), NOW()
    UNION ALL SELECT 'notification_delivery_status', 'SKIPPED', '已跳过', '#9CA3AF', 4, 'ACTIVE', '投递状态', NOW(), NOW()
    UNION ALL SELECT 'scheduled_task_type', 'PRICE_PUBLISH', '价格自动发布', NULL, 1, 'ACTIVE', '定时任务类型', NOW(), NOW()
    UNION ALL SELECT 'scheduled_task_type', 'NOTIFICATION_RETRY', '通知重试', NULL, 2, 'ACTIVE', '定时任务类型', NOW(), NOW()
    UNION ALL SELECT 'scheduled_task_type', 'DATA_CLEANUP', '数据清理', NULL, 3, 'ACTIVE', '定时任务类型', NOW(), NOW()
    UNION ALL SELECT 'scheduled_task_trigger_type', 'SCHEDULED', '自动触发', NULL, 1, 'ACTIVE', '任务触发方式', NOW(), NOW()
    UNION ALL SELECT 'scheduled_task_trigger_type', 'MANUAL_TEST', '手动测试', NULL, 2, 'ACTIVE', '任务触发方式', NOW(), NOW()
    UNION ALL SELECT 'scheduled_task_trigger_type', 'MANUAL_RUN', '手动执行', NULL, 3, 'ACTIVE', '任务触发方式', NOW(), NOW()
    UNION ALL SELECT 'scheduled_task_run_status', 'RUNNING', '执行中', '#3B82F6', 1, 'ACTIVE', '任务执行状态', NOW(), NOW()
    UNION ALL SELECT 'scheduled_task_run_status', 'SUCCESS', '成功', '#10B981', 2, 'ACTIVE', '任务执行状态', NOW(), NOW()
    UNION ALL SELECT 'scheduled_task_run_status', 'FAILED', '失败', '#EF4444', 3, 'ACTIVE', '任务执行状态', NOW(), NOW()
    UNION ALL SELECT 'scheduled_task_run_status', 'SKIPPED', '已跳过', '#9CA3AF', 4, 'ACTIVE', '任务执行状态', NOW(), NOW()
    UNION ALL SELECT 'workflow_type', 'PRICE_PUBLISH', '价格发布审批', NULL, 3, 'ACTIVE', '未来价格发布审批预留', NOW(), NOW()
    UNION ALL SELECT 'operation_module', '价格维护', '价格维护', 'price', 7, 'ACTIVE', '价格草稿保存与发布', NOW(), NOW()
    UNION ALL SELECT 'operation_module', '定时任务', '定时任务', 'schedule', 8, 'ACTIVE', '通用定时任务配置', NOW(), NOW()
    UNION ALL SELECT 'operation_module', '通知中心', '通知中心', 'notification', 9, 'ACTIVE', '通知消息阅读与投递', NOW(), NOW()
) AS seed
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict d WHERE d.category = seed.category AND d.dict_key = seed.dict_key
);

INSERT INTO sys_scheduled_task (task_code, task_name, task_type, cron_expression, timezone, enabled, config_json, remark, created_time, updated_time)
SELECT 'PRICE_AUTO_PUBLISH',
       '价格自动发布',
       'PRICE_PUBLISH',
       '0 0 9 * * ?',
       'Asia/Shanghai',
       FALSE,
       '{"dateOffsetDays":-1,"publishOnlyCompleteDraft":false,"notifyChannels":["IN_APP"],"recipientRoles":["ADMIN","EDITOR","VIEWER"],"systemUserId":0,"skipIfNoDraft":true}',
       '默认停用，需管理员确认后启用',
       NOW(),
       NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_scheduled_task WHERE task_code = 'PRICE_AUTO_PUBLISH');
