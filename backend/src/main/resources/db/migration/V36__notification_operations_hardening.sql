ALTER TABLE notification_delivery_log
    ADD COLUMN is_test BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否测试投递';

CREATE INDEX idx_notification_delivery_test
    ON notification_delivery_log (is_test);

ALTER TABLE notification_mini_program_subscription
    DROP INDEX idx_notification_mini_sub_resolve_status,
    DROP COLUMN resolve_status,
    DROP COLUMN resolve_remark,
    DROP COLUMN resolved_by,
    DROP COLUMN resolved_time;

ALTER TABLE notification_mini_program_resolution
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本';

UPDATE operation_log
SET request_params = '[REDACTED: historical sensitive request parameters removed]'
WHERE LOWER(request_params) REGEXP '(secret|password|access_token|token)[[:space:]]*[=:]';

INSERT INTO sys_permission (
    permission_code, permission_name, permission_type, parent_id, resource_url,
    sort_order, status, created_time, updated_time
)
SELECT * FROM (
    SELECT 'notification:subscription:view', '订阅授权查看', 'BUTTON', (SELECT id FROM sys_permission WHERE permission_code = 'notification:view' LIMIT 1), NULL, 154, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 'notification:subscription:guide', '订阅授权引导', 'BUTTON', (SELECT id FROM sys_permission WHERE permission_code = 'notification:view' LIMIT 1), NULL, 155, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 'notification:subscription:resolve', '订阅异常处理', 'BUTTON', (SELECT id FROM sys_permission WHERE permission_code = 'notification:view' LIMIT 1), NULL, 156, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 'notification:test-token', '通知渠道远程校验', 'BUTTON', (SELECT id FROM sys_permission WHERE permission_code = 'notification:view' LIMIT 1), NULL, 157, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 'notification:test-delivery', '通知渠道测试投递', 'BUTTON', (SELECT id FROM sys_permission WHERE permission_code = 'notification:view' LIMIT 1), NULL, 158, 'ACTIVE', NOW(), NOW()
) AS tmp
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission p WHERE p.permission_code = tmp.permission_code
);

INSERT INTO sys_role_permission (role_id, permission_id, created_time)
SELECT r.id, p.id, NOW()
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN (
    'notification:subscription:view',
    'notification:subscription:guide',
    'notification:subscription:resolve',
    'notification:test-token',
    'notification:test-delivery'
)
WHERE r.role_code = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
