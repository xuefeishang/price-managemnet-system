-- 为默认用户分配角色
-- 先检查是否存在，不存在则插入
INSERT INTO sys_user_role (user_id, role_id, created_time)
SELECT u.id, r.id, NOW()
FROM sys_user u
JOIN sys_role r ON r.role_code = u.role
WHERE NOT EXISTS (
    SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id
);

-- 验证结果
SELECT u.username, u.role, r.role_code, ur.user_id, ur.role_id
FROM sys_user u
LEFT JOIN sys_user_role ur ON ur.user_id = u.id
LEFT JOIN sys_role r ON r.id = ur.role_id;
