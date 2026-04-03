-- 插入默认用户 (密码: admin123, BCrypt加密)
USE price_management;

-- 先清空表
DELETE FROM sys_user WHERE username IN ('admin', 'editor', 'viewer');

-- 插入用户 (BCrypt hash for 'admin123')
INSERT INTO sys_user (username, password, role, status, nickname, email, phone, created_time, updated_time) VALUES
('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMy.MrqhK5QrE5MVFBJBZQRb7qyqR/qZ5KC', 'ADMIN', 'ACTIVE', 'Admin', 'admin@pricemanagement.com', '13800138000', NOW(), NOW()),
('editor', '$2a$10$N9qo8uLOickgx2ZMRZoMy.MrqhK5QrE5MVFBJBZQRb7qyqR/qZ5KC', 'EDITOR', 'ACTIVE', 'Editor', 'editor@pricemanagement.com', '13800138001', NOW(), NOW()),
('viewer', '$2a$10$N9qo8uLOickgx2ZMRZoMy.MrqhK5QrE5MVFBJBZQRb7qyqR/qZ5KC', 'VIEWER', 'ACTIVE', 'Viewer', 'viewer@pricemanagement.com', '13800138002', NOW(), NOW());

SELECT * FROM sys_user;