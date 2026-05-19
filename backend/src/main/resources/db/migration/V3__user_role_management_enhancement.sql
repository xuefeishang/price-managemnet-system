-- 用户管理与角色管理功能改进迁移脚本
-- 包含：验证码表、用户表扩展字段、角色表、权限表、关联表

-- 1. 验证码表
CREATE TABLE IF NOT EXISTS sys_captcha (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '验证码ID',
    captcha_key VARCHAR(100) NOT NULL UNIQUE COMMENT '验证码Key（UUID）',
    captcha_code VARCHAR(4) NOT NULL COMMENT '验证码（4位数字）',
    captcha_image VARCHAR(500) COMMENT '验证码图片Base64',
    ip_address VARCHAR(50) COMMENT '请求IP',
    expire_time DATETIME NOT NULL COMMENT '过期时间',
    used BOOLEAN DEFAULT FALSE COMMENT '是否已使用',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    INDEX idx_captcha_key (captcha_key),
    INDEX idx_captcha_expire (expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='验证码表';

-- 2. 用户表扩展字段
ALTER TABLE sys_user ADD COLUMN employee_id VARCHAR(6) UNIQUE COMMENT '工号（6位数字）' AFTER username;
ALTER TABLE sys_user ADD COLUMN department VARCHAR(100) COMMENT '部门' AFTER phone;
ALTER TABLE sys_user ADD COLUMN login_type VARCHAR(20) DEFAULT 'PASSWORD' COMMENT '登录方式：PASSWORD密码, WECHAT微信, BOTH双方式' AFTER department;
ALTER TABLE sys_user ADD COLUMN wechat_openid VARCHAR(100) UNIQUE COMMENT '微信OpenID' AFTER login_type;
ALTER TABLE sys_user ADD COLUMN wechat_unionid VARCHAR(100) COMMENT '微信UnionID' AFTER wechat_openid;
ALTER TABLE sys_user ADD COLUMN wechat_nickname VARCHAR(100) COMMENT '微信昵称' AFTER wechat_unionid;
ALTER TABLE sys_user ADD COLUMN wechat_avatar VARCHAR(500) COMMENT '微信头像URL' AFTER wechat_nickname;
ALTER TABLE sys_user ADD COLUMN last_login_time DATETIME COMMENT '最后登录时间' AFTER wechat_avatar;
ALTER TABLE sys_user ADD COLUMN last_login_ip VARCHAR(50) COMMENT '最后登录IP' AFTER last_login_time;
ALTER TABLE sys_user ADD COLUMN login_count INT DEFAULT 0 COMMENT '登录次数' AFTER last_login_ip;
ALTER TABLE sys_user ADD COLUMN password_updated_time DATETIME COMMENT '密码更新时间' AFTER login_count;
ALTER TABLE sys_user ADD COLUMN is_locked BOOLEAN DEFAULT FALSE COMMENT '是否锁定' AFTER password_updated_time;
ALTER TABLE sys_user ADD COLUMN locked_time DATETIME COMMENT '锁定时间' AFTER is_locked;

-- 添加索引
ALTER TABLE sys_user ADD INDEX idx_user_employee_id (employee_id);
ALTER TABLE sys_user ADD INDEX idx_user_wechat_openid (wechat_openid);

-- 3. 系统角色表
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '角色ID',
    role_code VARCHAR(50) NOT NULL UNIQUE COMMENT '角色编码',
    role_name VARCHAR(100) NOT NULL COMMENT '角色名称',
    description VARCHAR(500) COMMENT '角色描述',
    sort_order INT DEFAULT 0 COMMENT '排序',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/INACTIVE',
    is_system BOOLEAN DEFAULT FALSE COMMENT '是否系统内置角色',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_role_code (role_code),
    INDEX idx_role_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色表';

-- 4. 系统权限表
CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '权限ID',
    permission_code VARCHAR(100) NOT NULL UNIQUE COMMENT '权限编码',
    permission_name VARCHAR(100) NOT NULL COMMENT '权限名称',
    permission_type VARCHAR(20) NOT NULL COMMENT '权限类型：MENU菜单, BUTTON按钮, API接口',
    parent_id BIGINT COMMENT '父权限ID',
    resource_url VARCHAR(200) COMMENT '资源路径',
    icon VARCHAR(50) COMMENT '图标',
    sort_order INT DEFAULT 0 COMMENT '排序',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_permission_code (permission_code),
    INDEX idx_permission_type (permission_type),
    INDEX idx_permission_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统权限表';

-- 5. 用户角色关联表
CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '关联ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    UNIQUE KEY uk_user_role (user_id, role_id),
    INDEX idx_user_role_user (user_id),
    INDEX idx_user_role_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- 6. 角色权限关联表
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '关联ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    UNIQUE KEY uk_role_permission (role_id, permission_id),
    INDEX idx_role_permission_role (role_id),
    INDEX idx_role_permission_permission (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

-- 7. 初始化角色数据
INSERT INTO sys_role (id, role_code, role_name, description, sort_order, status, is_system, created_time, updated_time) VALUES
(1, 'ADMIN', '系统管理员', '拥有所有权限', 1, 'ACTIVE', TRUE, NOW(), NOW()),
(2, 'EDITOR', '编辑用户', '可编辑产品价格数据', 2, 'ACTIVE', TRUE, NOW(), NOW()),
(3, 'VIEWER', '普通用户', '仅可查看数据', 3, 'ACTIVE', TRUE, NOW(), NOW())
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name);

-- 8. 初始化权限数据
INSERT INTO sys_permission (id, permission_code, permission_name, permission_type, parent_id, resource_url, sort_order, status, created_time, updated_time) VALUES
(1, 'home:view', '首页查看', 'MENU', NULL, '/home', 1, 'ACTIVE', NOW(), NOW()),
(2, 'product:view', '产品列表查看', 'MENU', NULL, '/products', 10, 'ACTIVE', NOW(), NOW()),
(3, 'product:create', '产品创建', 'BUTTON', 2, NULL, 11, 'ACTIVE', NOW(), NOW()),
(4, 'product:edit', '产品编辑', 'BUTTON', 2, NULL, 12, 'ACTIVE', NOW(), NOW()),
(5, 'product:delete', '产品删除', 'BUTTON', 2, NULL, 13, 'ACTIVE', NOW(), NOW()),
(6, 'price:view', '价格查看', 'MENU', NULL, '/price-maintenance', 20, 'ACTIVE', NOW(), NOW()),
(7, 'price:edit', '价格编辑', 'BUTTON', 6, NULL, 21, 'ACTIVE', NOW(), NOW()),
(8, 'category:view', '分类管理查看', 'MENU', NULL, '/categories', 30, 'ACTIVE', NOW(), NOW()),
(9, 'category:edit', '分类编辑', 'BUTTON', 8, NULL, 31, 'ACTIVE', NOW(), NOW()),
(10, 'origin:view', '产地管理查看', 'MENU', NULL, '/origins', 40, 'ACTIVE', NOW(), NOW()),
(11, 'origin:edit', '产地编辑', 'BUTTON', 10, NULL, 41, 'ACTIVE', NOW(), NOW()),
(12, 'customer:view', '客户管理查看', 'MENU', NULL, '/customers', 50, 'ACTIVE', NOW(), NOW()),
(13, 'customer:edit', '客户编辑', 'BUTTON', 12, NULL, 51, 'ACTIVE', NOW(), NOW()),
(14, 'user:view', '用户管理查看', 'MENU', NULL, '/users', 100, 'ACTIVE', NOW(), NOW()),
(15, 'user:create', '用户创建', 'BUTTON', 14, NULL, 101, 'ACTIVE', NOW(), NOW()),
(16, 'user:edit', '用户编辑', 'BUTTON', 14, NULL, 102, 'ACTIVE', NOW(), NOW()),
(17, 'user:delete', '用户删除', 'BUTTON', 14, NULL, 103, 'ACTIVE', NOW(), NOW()),
(18, 'role:view', '角色管理查看', 'MENU', NULL, '/roles', 110, 'ACTIVE', NOW(), NOW()),
(19, 'role:edit', '角色编辑', 'BUTTON', 18, NULL, 111, 'ACTIVE', NOW(), NOW()),
(20, 'log:view', '日志查看', 'MENU', NULL, '/operation-log', 120, 'ACTIVE', NOW(), NOW())
ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name);

-- 9. 初始化角色权限关联 - ADMIN拥有所有权限
INSERT INTO sys_role_permission (role_id, permission_id, created_time)
SELECT 1, id, NOW() FROM sys_permission
ON DUPLICATE KEY UPDATE created_time = created_time;

-- 10. 初始化角色权限关联 - EDITOR拥有产品、价格、分类、产地、客户相关权限
INSERT INTO sys_role_permission (role_id, permission_id, created_time)
SELECT 2, id, NOW() FROM sys_permission
WHERE permission_code IN ('home:view', 'product:view', 'product:create', 'product:edit', 'price:view', 'price:edit', 'category:view', 'category:edit', 'origin:view', 'origin:edit', 'customer:view', 'customer:edit')
ON DUPLICATE KEY UPDATE created_time = created_time;

-- 11. 初始化角色权限关联 - VIEWER仅拥有查看权限
INSERT INTO sys_role_permission (role_id, permission_id, created_time)
SELECT 3, id, NOW() FROM sys_permission
WHERE permission_code LIKE '%:view'
ON DUPLICATE KEY UPDATE created_time = created_time;

-- 12. 为现有用户生成工号
UPDATE sys_user SET employee_id = LPAD(id, 6, '0') WHERE employee_id IS NULL;
