INSERT INTO sys_permission (
    permission_code, permission_name, permission_type, parent_id, resource_url,
    sort_order, status, created_time, updated_time
)
SELECT 'system:setting', '系统设置', 'MENU', NULL, '/system-settings',
       140, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission p WHERE p.permission_code = 'system:setting'
);

UPDATE sys_permission
SET permission_name = '系统设置',
    permission_type = 'MENU',
    resource_url = '/system-settings',
    status = 'ACTIVE',
    updated_time = NOW()
WHERE permission_code = 'system:setting';

INSERT INTO sys_role_permission (role_id, permission_id, created_time)
SELECT r.id, p.id, NOW()
FROM sys_role r
JOIN sys_permission p ON p.permission_code = 'system:setting'
WHERE r.role_code = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
