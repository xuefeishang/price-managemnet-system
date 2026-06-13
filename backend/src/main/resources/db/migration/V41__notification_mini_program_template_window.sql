CREATE TABLE IF NOT EXISTS notification_mini_program_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '小程序订阅模板版本ID',
    notification_type VARCHAR(50) NOT NULL COMMENT '通知类型',
    template_id VARCHAR(100) NOT NULL COMMENT '微信订阅消息模板ID',
    page VARCHAR(200) COMMENT '小程序跳转页',
    fields_json TEXT NOT NULL COMMENT '字段映射JSON',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '模板状态',
    last_test_status VARCHAR(20) COMMENT '最近测试状态',
    last_test_message VARCHAR(500) COMMENT '最近测试说明',
    last_test_delivery_id BIGINT COMMENT '最近测试投递ID',
    last_test_time DATETIME COMMENT '最近测试时间',
    published_by BIGINT COMMENT '发布人',
    published_time DATETIME COMMENT '发布时间',
    created_by BIGINT COMMENT '创建人',
    updated_by BIGINT COMMENT '更新人',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    INDEX idx_notification_mini_template_type_status (notification_type, status),
    INDEX idx_notification_mini_template_template_id (template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小程序订阅消息模板版本表';

CREATE TABLE IF NOT EXISTS notification_mini_program_template_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '小程序订阅模板运维历史ID',
    template_id_ref BIGINT NOT NULL COMMENT '模板版本ID',
    notification_type VARCHAR(50) NOT NULL COMMENT '通知类型',
    action VARCHAR(20) NOT NULL COMMENT '操作类型',
    operator_id BIGINT COMMENT '操作人',
    status_before VARCHAR(20) COMMENT '操作前状态',
    status_after VARCHAR(20) COMMENT '操作后状态',
    template_id_masked VARCHAR(120) COMMENT '脱敏模板ID',
    message VARCHAR(500) COMMENT '操作说明',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    INDEX idx_notification_mini_template_history_template (template_id_ref),
    INDEX idx_notification_mini_template_history_type (notification_type),
    CONSTRAINT fk_notification_mini_template_history_template FOREIGN KEY (template_id_ref)
        REFERENCES notification_mini_program_template(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小程序订阅消息模板运维历史表';

INSERT INTO sys_dict (category, dict_key, dict_value, extra_value, sort_order, status, created_time, updated_time)
SELECT 'notification_mini_template_status', 'DRAFT', '草稿', '#64748B', 1, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict WHERE category = 'notification_mini_template_status' AND dict_key = 'DRAFT'
);

INSERT INTO sys_dict (category, dict_key, dict_value, extra_value, sort_order, status, created_time, updated_time)
SELECT 'notification_mini_template_status', 'TESTING', '测试中', '#F59E0B', 2, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict WHERE category = 'notification_mini_template_status' AND dict_key = 'TESTING'
);

INSERT INTO sys_dict (category, dict_key, dict_value, extra_value, sort_order, status, created_time, updated_time)
SELECT 'notification_mini_template_status', 'ACTIVE', '已生效', '#10B981', 3, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict WHERE category = 'notification_mini_template_status' AND dict_key = 'ACTIVE'
);

INSERT INTO sys_dict (category, dict_key, dict_value, extra_value, sort_order, status, created_time, updated_time)
SELECT 'notification_mini_template_status', 'DISABLED', '已停用', '#9CA3AF', 4, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict WHERE category = 'notification_mini_template_status' AND dict_key = 'DISABLED'
);
