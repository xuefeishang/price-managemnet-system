CREATE TABLE IF NOT EXISTS system_notice (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '系统公告ID',
    title VARCHAR(100) NOT NULL COMMENT '标题',
    summary VARCHAR(300) COMMENT '摘要',
    content TEXT NOT NULL COMMENT '内容',
    target_roles VARCHAR(200) NOT NULL COMMENT '目标角色JSON',
    channels VARCHAR(200) NOT NULL COMMENT '通知渠道JSON',
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '通知优先级',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '公告状态',
    scheduled_publish_time DATETIME COMMENT '计划发布时间',
    published_time DATETIME COMMENT '发布时间',
    cancelled_time DATETIME COMMENT '撤回时间',
    expire_time DATETIME COMMENT '过期时间',
    notification_message_id BIGINT COMMENT '发布后通知消息ID',
    created_by BIGINT COMMENT '创建人',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_system_notice_status_schedule (status, scheduled_publish_time),
    INDEX idx_system_notice_created (created_time),
    INDEX idx_system_notice_message (notification_message_id),
    CONSTRAINT fk_system_notice_message FOREIGN KEY (notification_message_id) REFERENCES notification_message(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统公告表';

INSERT INTO sys_dict (category, dict_key, dict_value, extra_value, sort_order, status, remark, created_time, updated_time)
SELECT * FROM (
    SELECT 'notification_type' AS category, 'APPROVAL_PENDING' AS dict_key, '审批待处理' AS dict_value, NULL AS extra_value, 2 AS sort_order, 'ACTIVE' AS status, '通知类型' AS remark, NOW() AS created_time, NOW() AS updated_time
    UNION ALL SELECT 'notification_type', 'APPROVAL_FINISHED', '审批完成', NULL, 3, 'ACTIVE', '通知类型', NOW(), NOW()
    UNION ALL SELECT 'notification_type', 'TASK_FAILED', '任务失败', NULL, 4, 'ACTIVE', '通知类型', NOW(), NOW()
    UNION ALL SELECT 'notification_type', 'API_LIMIT_WARNING', 'API告警', NULL, 5, 'ACTIVE', '通知类型', NOW(), NOW()
    UNION ALL SELECT 'notification_type', 'IMPORT_EXPORT_FINISHED', '导入导出完成', NULL, 6, 'ACTIVE', '通知类型', NOW(), NOW()
    UNION ALL SELECT 'notification_type', 'SYSTEM_NOTICE', '系统公告', NULL, 7, 'ACTIVE', '通知类型', NOW(), NOW()
    UNION ALL SELECT 'system_notice_status', 'DRAFT', '草稿', '#64748B', 1, 'ACTIVE', '系统公告状态', NOW(), NOW()
    UNION ALL SELECT 'system_notice_status', 'SCHEDULED', '待发布', '#3B82F6', 2, 'ACTIVE', '系统公告状态', NOW(), NOW()
    UNION ALL SELECT 'system_notice_status', 'PUBLISHED', '已发布', '#10B981', 3, 'ACTIVE', '系统公告状态', NOW(), NOW()
    UNION ALL SELECT 'system_notice_status', 'CANCELLED', '已撤回', '#9CA3AF', 4, 'ACTIVE', '系统公告状态', NOW(), NOW()
    UNION ALL SELECT 'system_notice_status', 'EXPIRED', '已过期', '#F59E0B', 5, 'ACTIVE', '系统公告状态', NOW(), NOW()
) AS tmp
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict d WHERE d.category = tmp.category AND d.dict_key = tmp.dict_key
);

INSERT INTO menu_item (parent_id, name, path, icon, sort_order, visible, roles, created_time, updated_time)
SELECT system_menu.id, '通知管理', '/notifications', 'bell', 6, TRUE, '["ADMIN"]', NOW(), NOW()
FROM menu_item system_menu
WHERE system_menu.parent_id IS NULL
  AND system_menu.name = '系统管理'
  AND NOT EXISTS (SELECT 1 FROM menu_item m WHERE m.path = '/notifications');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, resource_url, sort_order, status, created_time, updated_time)
SELECT * FROM (
    SELECT 'notification:view' AS permission_code, '通知管理查看' AS permission_name, 'MENU' AS permission_type, NULL AS parent_id, '/notifications' AS resource_url, 150 AS sort_order, 'ACTIVE' AS status, NOW() AS created_time, NOW() AS updated_time
    UNION ALL SELECT 'notification:retry', '通知投递重试', 'BUTTON', NULL, NULL, 151, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 'system-notice:create', '系统公告创建', 'BUTTON', NULL, NULL, 152, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 'system-notice:cancel', '系统公告撤回', 'BUTTON', NULL, NULL, 153, 'ACTIVE', NOW(), NOW()
) AS tmp
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission p WHERE p.permission_code = tmp.permission_code
);

INSERT INTO sys_role_permission (role_id, permission_id, created_time)
SELECT r.id, p.id, NOW()
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN ('notification:view', 'notification:retry', 'system-notice:create', 'system-notice:cancel')
WHERE r.role_code = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
