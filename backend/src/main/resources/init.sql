-- =====================================================
-- 价格管理系统 - 数据初始化脚本
-- 数据库: price_management
-- 说明: 包含表结构创建和初始数据插入
-- =====================================================

USE price_management;

-- =====================================================
-- 1. 创建表结构
-- =====================================================

-- 1.0 验证码表
CREATE TABLE IF NOT EXISTS sys_captcha (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '验证码ID',
    captcha_key VARCHAR(100) NOT NULL UNIQUE COMMENT '验证码Key（UUID）',
    captcha_code VARCHAR(4) NOT NULL COMMENT '验证码（4位数字）',
    captcha_image TEXT COMMENT '验证码图片Base64',
    ip_address VARCHAR(50) COMMENT '请求IP',
    expire_time DATETIME NOT NULL COMMENT '过期时间',
    used BOOLEAN DEFAULT FALSE COMMENT '是否已使用',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    INDEX idx_captcha_key (captcha_key),
    INDEX idx_captcha_expire (expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='验证码表';

-- 1.1 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    employee_id VARCHAR(6) UNIQUE COMMENT '工号（6位数字）',
    password VARCHAR(200) NOT NULL COMMENT '密码（BCrypt加密）',
    role VARCHAR(20) NOT NULL COMMENT '角色：ADMIN/EDITOR/VIEWER',
    dept_id BIGINT COMMENT '部门ID',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/INACTIVE',
    nickname VARCHAR(50) COMMENT '昵称',
    email VARCHAR(100) COMMENT '邮箱',
    phone VARCHAR(20) COMMENT '电话',
    department VARCHAR(100) COMMENT '部门（旧字段，保留兼容）',
    login_type VARCHAR(20) DEFAULT 'PASSWORD' COMMENT '登录方式：PASSWORD密码, WECHAT微信, BOTH双方式',
    wechat_openid VARCHAR(100) UNIQUE COMMENT '微信OpenID',
    wechat_unionid VARCHAR(100) COMMENT '微信UnionID',
    wechat_nickname VARCHAR(100) COMMENT '微信昵称',
    wechat_avatar VARCHAR(500) COMMENT '微信头像URL',
    last_login_time DATETIME COMMENT '最后登录时间',
    last_login_ip VARCHAR(50) COMMENT '最后登录IP',
    login_count INT DEFAULT 0 COMMENT '登录次数',
    password_updated_time DATETIME COMMENT '密码更新时间',
    is_locked BOOLEAN DEFAULT FALSE COMMENT '是否锁定',
    locked_time DATETIME COMMENT '锁定时间',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_username (username),
    INDEX idx_user_employee_id (employee_id),
    INDEX idx_user_status (status),
    INDEX idx_user_role (role),
    INDEX idx_user_dept (dept_id),
    INDEX idx_user_wechat_openid (wechat_openid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 1.2 产品分类表
CREATE TABLE IF NOT EXISTS product_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '分类ID',
    name VARCHAR(100) NOT NULL COMMENT '分类名称',
    code VARCHAR(50) UNIQUE COMMENT '分类编码',
    sort_order INT DEFAULT 0 COMMENT '排序顺序',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/INACTIVE',
    remark TEXT COMMENT '备注',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_category_code (code),
    INDEX idx_category_status (status),
    INDEX idx_category_sort (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品分类表';

-- 1.3 产品表
CREATE TABLE IF NOT EXISTS product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '产品ID',
    name VARCHAR(200) NOT NULL COMMENT '产品名称',
    code VARCHAR(100) COMMENT '产品编码',
    selling_price DECIMAL(15, 4) COMMENT '售价',
    budget_price DECIMAL(15, 4) COMMENT '预算价格',
    category_id BIGINT COMMENT '分类ID',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/INACTIVE',
    description TEXT COMMENT '产品描述',
    specs TEXT COMMENT '规格参数',
    image_url VARCHAR(500) COMMENT '图片URL',
    origin_ids VARCHAR(500) COMMENT '产地ID列表(JSON数组)',
    customer_ids VARCHAR(500) COMMENT '客户ID列表(JSON数组)',
    remark TEXT COMMENT '备注',
    unit VARCHAR(50) COMMENT '计量单位：元/吨、万元/吨、元/克、元/千克',
    sort_order INT DEFAULT 0 COMMENT '排序顺序',
    show_on_home BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否在首页展示',
    currency VARCHAR(20) DEFAULT 'CNY' COMMENT '计价币种：CNY-人民币、USD-美元、EUR-欧元',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (category_id) REFERENCES product_category(id) ON DELETE SET NULL ON UPDATE CASCADE,
    INDEX idx_product_category (category_id),
    INDEX idx_product_status (status),
    INDEX idx_product_name (name),
    INDEX idx_product_code (code),
    INDEX idx_product_sort (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品表';

-- 1.3.1 产地表
CREATE TABLE IF NOT EXISTS origin (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '产地ID',
    name VARCHAR(100) NOT NULL COMMENT '产地名称',
    code VARCHAR(50) UNIQUE NOT NULL COMMENT '产地编码',
    sort_order INT DEFAULT 0 COMMENT '排序顺序',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/INACTIVE',
    remark TEXT COMMENT '备注',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_origin_code (code),
    INDEX idx_origin_status (status),
    INDEX idx_origin_sort (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产地表';

-- 1.3.2 客户表
CREATE TABLE IF NOT EXISTS customer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '客户ID',
    name VARCHAR(100) NOT NULL COMMENT '客户名称',
    code VARCHAR(50) UNIQUE NOT NULL COMMENT '客户编码',
    contact VARCHAR(100) COMMENT '联系人',
    phone VARCHAR(20) COMMENT '联系电话',
    address VARCHAR(500) COMMENT '地址',
    sort_order INT DEFAULT 0 COMMENT '排序顺序',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/INACTIVE',
    remark TEXT COMMENT '备注',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_customer_code (code),
    INDEX idx_customer_status (status),
    INDEX idx_customer_sort (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户表';

-- 1.4 价格表
CREATE TABLE IF NOT EXISTS price (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '价格ID',
    product_id BIGINT NOT NULL COMMENT '产品ID',
    original_price DECIMAL(15, 4) COMMENT '原价',
    current_price DECIMAL(15, 4) NOT NULL COMMENT '现价',
    cost_price DECIMAL(15, 4) COMMENT '成本价',
    budget_price DECIMAL(15, 4) COMMENT '预算价格',
    effective_date DATE COMMENT '生效日期',
    expiry_date DATE COMMENT '失效日期',
    unit VARCHAR(50) COMMENT '单位：元/吨、元/克等',
    price_spec VARCHAR(200) COMMENT '价格规格',
    created_by BIGINT COMMENT '创建人',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_price_product (product_id),
    INDEX idx_price_effective (effective_date, expiry_date),
    INDEX idx_price_created (created_time),
    UNIQUE KEY uk_product_effective_date (product_id, effective_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='价格表';

-- 1.5 价格历史表
CREATE TABLE IF NOT EXISTS price_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '历史记录ID',
    price_id BIGINT COMMENT '价格ID',
    product_id BIGINT NOT NULL COMMENT '产品ID',
    old_price DECIMAL(15, 4) COMMENT '旧价格',
    new_price DECIMAL(15, 4) NOT NULL COMMENT '新价格',
    change_type VARCHAR(20) NOT NULL COMMENT '变动类型：CREATE/UPDATE/DELETE',
    changed_by BIGINT COMMENT '变更操作人ID',
    remark TEXT COMMENT '备注',
    changed_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '变动时间',
    FOREIGN KEY (price_id) REFERENCES price(id) ON DELETE SET NULL ON UPDATE CASCADE,
    FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_history_product (product_id),
    INDEX idx_history_price (price_id),
    INDEX idx_history_time (changed_time),
    INDEX idx_history_type (change_type),
    INDEX idx_history_changed_by (changed_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='价格历史表';

-- 1.6 数据同步日志表
CREATE TABLE IF NOT EXISTS sync_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID',
    sync_type VARCHAR(50) NOT NULL COMMENT '同步类型',
    sync_source VARCHAR(100) COMMENT '同步源',
    sync_status VARCHAR(20) NOT NULL COMMENT '同步状态：SUCCESS/FAILED',
    sync_count INT DEFAULT 0 COMMENT '同步数量',
    success_count INT DEFAULT 0 COMMENT '成功数量',
    failed_count INT DEFAULT 0 COMMENT '失败数量',
    error_message TEXT COMMENT '错误信息',
    started_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '开始时间',
    completed_time DATETIME COMMENT '完成时间',
    INDEX idx_sync_type (sync_type),
    INDEX idx_sync_status (sync_status),
    INDEX idx_sync_time (started_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据同步日志表';

-- =====================================================
-- 1.7 操作日志表
-- =====================================================
CREATE TABLE IF NOT EXISTS operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID',
    user_id BIGINT COMMENT '用户ID',
    username VARCHAR(100) COMMENT '用户名',
    operation_type VARCHAR(50) COMMENT '操作类型',
    operation_module VARCHAR(100) COMMENT '操作模块',
    operation_desc VARCHAR(500) COMMENT '操作描述',
    request_method VARCHAR(10) COMMENT '请求方法',
    request_url VARCHAR(500) COMMENT '请求URL',
    request_params TEXT COMMENT '请求参数',
    response_code VARCHAR(10) COMMENT '响应码',
    response_data TEXT COMMENT '响应数据',
    ip_address VARCHAR(50) COMMENT 'IP地址',
    user_agent VARCHAR(500) COMMENT '用户代理',
    operation_time DATETIME NOT NULL COMMENT '操作时间',
    execution_time BIGINT COMMENT '执行时间(毫秒)',
    error_message TEXT COMMENT '错误信息',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    INDEX idx_operation_user (user_id),
    INDEX idx_operation_type (operation_type),
    INDEX idx_operation_time (operation_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- =====================================================
-- 2. 初始化用户数据
-- =====================================================
-- 密码均为: admin123 (BCrypt加密)

SET @has_user = 0;
SELECT COUNT(*) INTO @has_user FROM sys_user;

INSERT INTO sys_user (username, password, role, status, nickname, email, phone, created_time, updated_time)
SELECT * FROM (
    SELECT 'admin' AS username, '$2a$10$N9qo8uLOickgx2ZMRZoMy.MrqhK5QrE5MVFBJBZQRb7qyqR/qZ5KC' AS password, 'ADMIN' AS role, 'ACTIVE' AS status, '管理员' AS nickname, 'admin@pricemanagement.com' AS email, '13800138000' AS phone, NOW() AS created_time, NOW() AS updated_time
    UNION ALL SELECT 'editor', '$2a$10$N9qo8uLOickgx2ZMRZoMy.MrqhK5QrE5MVFBJBZQRb7qyqR/qZ5KC', 'EDITOR', 'ACTIVE', '编辑者', 'editor@pricemanagement.com', '13800138001', NOW(), NOW()
    UNION ALL SELECT 'viewer', '$2a$10$N9qo8uLOickgx2ZMRZoMy.MrqhK5QrE5MVFBJBZQRb7qyqR/qZ5KC', 'VIEWER', 'ACTIVE', '查看者', 'viewer@pricemanagement.com', '13800138002', NOW(), NOW()
) AS tmp
WHERE @has_user = 0;

SELECT CONCAT('用户数据: ', IF(@has_user > 0, '已存在，跳过', '初始化完成（3个用户）')) AS status;

-- =====================================================
-- 3. 初始化产品分类
-- =====================================================

SET @has_category = 0;
SELECT COUNT(*) INTO @has_category FROM product_category;

INSERT INTO product_category (name, code, sort_order, status, remark, created_time, updated_time)
SELECT * FROM (
    SELECT '黑色金属' AS name, 'BLACK_METAL' AS code, 1 AS sort_order, 'ACTIVE' AS status, '黑色金属产品分类' AS remark, NOW() AS created_time, NOW() AS updated_time
    UNION ALL SELECT '有色金属', 'NON_FERROUS_METAL', 2, 'ACTIVE', '有色金属产品分类', NOW(), NOW()
    UNION ALL SELECT '贵金属', 'PRECIOUS_METAL', 3, 'ACTIVE', '贵金属产品分类', NOW(), NOW()
    UNION ALL SELECT '化工产品', 'CHEMICAL', 4, 'ACTIVE', '化工产品分类', NOW(), NOW()
    UNION ALL SELECT '煤炭及焦炭', 'COAL', 5, 'ACTIVE', '煤炭及焦炭产品分类', NOW(), NOW()
) AS tmp
WHERE @has_category = 0;

SELECT CONCAT('分类数据: ', IF(@has_category > 0, '已存在，跳过', '初始化完成（5个分类）')) AS status;

-- =====================================================
-- 4. 初始化产品信息
-- =====================================================

SET @has_product = 0;
SELECT COUNT(*) INTO @has_product FROM product;

INSERT INTO product (name, category_id, status, specs, remark, created_time, updated_time)
SELECT * FROM (
    SELECT '硫精砂' AS name, 4 AS category_id, 'ACTIVE' AS status, '出厂承兑，As < 0.1%' AS specs, '硫精砂产品' AS remark, NOW() AS created_time, NOW() AS updated_time
    UNION ALL SELECT '硫酸', 4, 'ACTIVE', '93% 出厂承兑', '硫酸产品', NOW(), NOW()
    UNION ALL SELECT '钼精矿', 2, 'ACTIVE', '45-50（元/吨度）；50以上（元/吨度）', '钼精矿产品', NOW(), NOW()
    UNION ALL SELECT '电铜', 2, 'ACTIVE', 'A级（元/吨）', '电铜产品', NOW(), NOW()
    UNION ALL SELECT '金', 3, 'ACTIVE', '2#（元/克）', '金产品', NOW(), NOW()
    UNION ALL SELECT '银', 3, 'ACTIVE', '3#（元/千克）', '银产品', NOW(), NOW()
    UNION ALL SELECT '钢坯', 1, 'ACTIVE', '方坯 Q235', '钢坯产品', NOW(), NOW()
    UNION ALL SELECT '废钢（河北纵横）', 1, 'ACTIVE', '厚6mm，<18000mm，宽<1200mm；常规重废', '废钢产品', NOW(), NOW()
    UNION ALL SELECT '五氧化二钒', 2, 'ACTIVE', '98% 片状，承兑（万元/吨）', '五氧化二钒产品', NOW(), NOW()
    UNION ALL SELECT '镁锭', 2, 'ACTIVE', '99990（闻喜）元/吨', '镁锭产品', NOW(), NOW()
    UNION ALL SELECT '铅锭', 2, 'ACTIVE', '1#（元/吨）', '铅锭产品', NOW(), NOW()
    UNION ALL SELECT '锌锭', 2, 'ACTIVE', '0#（元/吨）', '锌锭产品', NOW(), NOW()
    UNION ALL SELECT '钯金', 3, 'ACTIVE', '99.95（元/克）', '钯金产品', NOW(), NOW()
    UNION ALL SELECT '铂金', 3, 'ACTIVE', '99.95（元/克）', '铂金产品', NOW(), NOW()
    UNION ALL SELECT '硫酸钴', 4, 'ACTIVE', '≥20.5% 国产（万元/吨）', '硫酸钴产品', NOW(), NOW()
    UNION ALL SELECT '碳酸锂', 4, 'ACTIVE', '电池级 99.5% 国产（万元/吨）', '碳酸锂产品', NOW(), NOW()
    UNION ALL SELECT '钛精矿', 2, 'ACTIVE', '48% 不含税（元/吨）', '钛精矿产品', NOW(), NOW()
    UNION ALL SELECT '无烟煤（一级冶金焦）', 5, 'ACTIVE', 'C>85%，A<12.5%，S<0.7%，V<1.9%，HGI>50，M25<7%，M10>65%', '无烟煤产品', NOW(), NOW()
    UNION ALL SELECT '萤石湿粉', 4, 'ACTIVE', '97%', '萤石湿粉产品', NOW(), NOW()
    UNION ALL SELECT '铁精粉', 1, 'ACTIVE', '66%', '铁精粉产品', NOW(), NOW()
) AS tmp
WHERE @has_product = 0;

SELECT CONCAT('产品数据: ', IF(@has_product > 0, '已存在，跳过', '初始化完成（20个产品）')) AS status;

-- =====================================================
-- 5. 初始化价格数据
-- =====================================================

SET @has_price = 0;
SELECT COUNT(*) INTO @has_price FROM price;

INSERT INTO price (product_id, original_price, current_price, cost_price, unit, price_spec, effective_date, created_time)
SELECT p.id, 0 AS original_price, 0 AS current_price, 0 AS cost_price, '元/吨' AS unit, p.specs AS price_spec, CURDATE() AS effective_date, NOW() AS created_time
FROM product p
WHERE @has_price = 0
AND NOT EXISTS (SELECT 1 FROM price WHERE product_id = p.id);

SELECT CONCAT('价格数据: ', IF(@has_price > 0, '已存在，跳过', '初始化完成')) AS status;

-- =====================================================
-- 6. 菜单项表初始化（如不存在）
-- =====================================================
CREATE TABLE IF NOT EXISTS menu_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '菜单ID',
    parent_id BIGINT COMMENT '父级菜单ID',
    name VARCHAR(100) NOT NULL COMMENT '菜单名称',
    path VARCHAR(200) COMMENT '菜单路径',
    icon VARCHAR(50) COMMENT '菜单图标',
    sort_order INT DEFAULT 0 COMMENT '排序顺序',
    visible BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否可见',
    roles VARCHAR(500) COMMENT '可见角色(JSON数组)',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_menu_parent (parent_id),
    INDEX idx_menu_sort (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单项表';

-- =====================================================
-- 7. 初始化菜单数据
-- =====================================================

SET @has_menu = 0;
SELECT COUNT(*) INTO @has_menu FROM menu_item;

-- 一级菜单
INSERT INTO menu_item (id, parent_id, name, path, icon, sort_order, visible, roles, created_time, updated_time)
SELECT * FROM (
    SELECT 1 AS id, NULL AS parent_id, '首页' AS name, '/home' AS path, 'home' AS icon, 1 AS sort_order, TRUE AS visible, NULL AS roles, NOW() AS created_time, NOW() AS updated_time
    UNION ALL SELECT 2, NULL, '产品管理', NULL, 'product', 2, TRUE, NULL, NOW(), NOW()
    UNION ALL SELECT 3, NULL, '基础运维', NULL, 'category', 3, TRUE, '["ADMIN","EDITOR"]', NOW(), NOW()
    UNION ALL SELECT 4, NULL, '系统管理', NULL, 'settings', 4, TRUE, '["ADMIN"]', NOW(), NOW()
) AS tmp
WHERE @has_menu = 0;

-- 二级菜单
INSERT INTO menu_item (id, parent_id, name, path, icon, sort_order, visible, roles, created_time, updated_time)
SELECT * FROM (
    SELECT 10 AS id, 2 AS parent_id, '产品列表' AS name, '/products' AS path, NULL AS icon, 1 AS sort_order, TRUE AS visible, NULL AS roles, NOW() AS created_time, NOW() AS updated_time
    UNION ALL SELECT 11, 2, '价格维护', '/price-maintenance', 'price', 2, TRUE, '["ADMIN","EDITOR"]', NOW(), NOW()
    UNION ALL SELECT 12, 2, '价格查询', '/price-query', 'price', 3, TRUE, '["ADMIN","EDITOR","VIEWER"]', NOW(), NOW()
    UNION ALL SELECT 20, 3, '产品维护', '/product-edit', NULL, 1, TRUE, '["ADMIN","EDITOR"]', NOW(), NOW()
    UNION ALL SELECT 21, 3, '分类管理', '/categories', NULL, 2, TRUE, '["ADMIN","EDITOR"]', NOW(), NOW()
    UNION ALL SELECT 22, 3, '导入导出', '/import', NULL, 3, TRUE, '["ADMIN","EDITOR"]', NOW(), NOW()
    UNION ALL SELECT 23, 3, '审批管理', '/approval', 'check-circle', 4, TRUE, '["ADMIN","EDITOR"]', NOW(), NOW()
    UNION ALL SELECT 24, 3, '字典管理', NULL, 'dict', 5, TRUE, '["ADMIN","EDITOR"]', NOW(), NOW()
    UNION ALL SELECT 30, 4, '用户管理', '/users', 'users', 1, TRUE, '["ADMIN"]', NOW(), NOW()
    UNION ALL SELECT 34, 4, '部门管理', '/departments', 'building', 2, TRUE, '["ADMIN"]', NOW(), NOW()
    UNION ALL SELECT 31, 4, '菜单配置', '/menu-config', 'menu', 3, TRUE, '["ADMIN"]', NOW(), NOW()
    UNION ALL SELECT 32, 4, '日志管理', '/operation-log', 'log', 4, TRUE, '["ADMIN"]', NOW(), NOW()
    UNION ALL SELECT 33, 4, '审批流配置', '/approval-config', 'workflow', 5, TRUE, '["ADMIN"]', NOW(), NOW()
    UNION ALL SELECT 36, 4, '通知管理', '/notifications', 'bell', 6, TRUE, '["ADMIN"]', NOW(), NOW()
    UNION ALL SELECT 35, 4, '样式设置', '/style-settings', 'palette', 7, TRUE, '["ADMIN"]', NOW(), NOW()
    UNION ALL SELECT 40, 24, '产地管理', '/origins', NULL, 1, TRUE, '["ADMIN","EDITOR"]', NOW(), NOW()
    UNION ALL SELECT 41, 24, '客户管理', '/customers', NULL, 2, TRUE, '["ADMIN","EDITOR"]', NOW(), NOW()
    UNION ALL SELECT 42, 24, '数据字典', '/dict-management', NULL, 3, TRUE, '["ADMIN"]', NOW(), NOW()
) AS tmp
WHERE @has_menu = 0;

SELECT CONCAT('菜单数据: ', IF(@has_menu > 0, '已存在，跳过', '初始化完成')) AS status;

-- =====================================================
-- 7.1 初始化产地数据
-- =====================================================

SET @has_origin = 0;
SELECT COUNT(*) INTO @has_origin FROM origin;

INSERT INTO origin (name, code, sort_order, status, remark, created_time, updated_time)
SELECT * FROM (
    SELECT '国内' AS name, 'DOMESTIC' AS code, 1 AS sort_order, 'ACTIVE' AS status, '国内产地' AS remark, NOW() AS created_time, NOW() AS updated_time
    UNION ALL SELECT '进口', 'IMPORTED', 2, 'ACTIVE', '进口产地', NOW(), NOW()
) AS tmp
WHERE @has_origin = 0;

SELECT CONCAT('产地数据: ', IF(@has_origin > 0, '已存在，跳过', '初始化完成（2个产地）')) AS status;

-- =====================================================
-- 7.2 初始化客户数据
-- =====================================================

SET @has_customer = 0;
SELECT COUNT(*) INTO @has_customer FROM customer;

INSERT INTO customer (name, code, sort_order, status, remark, created_time, updated_time)
SELECT * FROM (
    SELECT '终端用户' AS name, 'END_USER' AS code, 1 AS sort_order, 'ACTIVE' AS status, '终端用户' AS remark, NOW() AS created_time, NOW() AS updated_time
    UNION ALL SELECT '贸易商', 'TRADER', 2, 'ACTIVE', '贸易商客户', NOW(), NOW()
) AS tmp
WHERE @has_customer = 0;

SELECT CONCAT('客户数据: ', IF(@has_customer > 0, '已存在，跳过', '初始化完成（2个客户）')) AS status;

-- =====================================================
-- 7.3 数据字典表
-- =====================================================
CREATE TABLE IF NOT EXISTS sys_dict (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '字典ID',
    category VARCHAR(50) NOT NULL COMMENT '分类标识',
    dict_key VARCHAR(100) NOT NULL COMMENT '字典键',
    dict_value VARCHAR(200) NOT NULL COMMENT '显示值',
    extra_value TEXT COMMENT '扩展值（如货币符号、图标名、JSON配置等）',
    sort_order INT DEFAULT 0 COMMENT '排序顺序',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/INACTIVE',
    remark TEXT COMMENT '备注',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_category_key (category, dict_key),
    INDEX idx_dict_category (category),
    INDEX idx_dict_status (status),
    INDEX idx_dict_sort (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据字典表';

-- =====================================================
-- 7.4 初始化字典数据
-- =====================================================

SET @has_dict = 0;
SELECT COUNT(*) INTO @has_dict FROM sys_dict;

-- 币种字典
INSERT INTO sys_dict (category, dict_key, dict_value, extra_value, sort_order, status, remark, created_time, updated_time)
SELECT * FROM (
    SELECT 'currency' AS category, 'CNY' AS dict_key, '人民币' AS dict_value, '¥' AS extra_value, 1 AS sort_order, 'ACTIVE' AS status, '中国人民币' AS remark, NOW() AS created_time, NOW() AS updated_time
    UNION ALL SELECT 'currency', 'USD', '美元', '$', 2, 'ACTIVE', '美国美元', NOW(), NOW()
    UNION ALL SELECT 'currency', 'EUR', '欧元', '€', 3, 'ACTIVE', '欧元', NOW(), NOW()
) AS tmp
WHERE @has_dict = 0;

-- 通用状态字典
INSERT INTO sys_dict (category, dict_key, dict_value, extra_value, sort_order, status, remark, created_time, updated_time)
SELECT * FROM (
    SELECT 'common_status' AS category, 'ACTIVE' AS dict_key, '启用' AS dict_value, '#52c41a' AS extra_value, 1 AS sort_order, 'ACTIVE' AS status, '启用状态' AS remark, NOW() AS created_time, NOW() AS updated_time
    UNION ALL SELECT 'common_status', 'INACTIVE', '停用', '#ff4d4f', 2, 'ACTIVE', '停用状态', NOW(), NOW()
) AS tmp
WHERE @has_dict = 0;

-- 用户角色字典
INSERT INTO sys_dict (category, dict_key, dict_value, extra_value, sort_order, status, remark, created_time, updated_time)
SELECT * FROM (
    SELECT 'user_role' AS category, 'ADMIN' AS dict_key, '管理员' AS dict_value, 'shield' AS extra_value, 1 AS sort_order, 'ACTIVE' AS status, '系统管理员' AS remark, NOW() AS created_time, NOW() AS updated_time
    UNION ALL SELECT 'user_role', 'EDITOR', '编辑者', 'edit', 2, 'ACTIVE', '内容编辑者', NOW(), NOW()
    UNION ALL SELECT 'user_role', 'VIEWER', '查看者', 'eye', 3, 'ACTIVE', '只读查看者', NOW(), NOW()
) AS tmp
WHERE @has_dict = 0;

-- 审批状态字典
INSERT INTO sys_dict (category, dict_key, dict_value, extra_value, sort_order, status, remark, created_time, updated_time)
SELECT * FROM (
    SELECT 'approval_status' AS category, 'PENDING' AS dict_key, '待审批' AS dict_value, '#faad14' AS extra_value, 1 AS sort_order, 'ACTIVE' AS status, '等待审批' AS remark, NOW() AS created_time, NOW() AS updated_time
    UNION ALL SELECT 'approval_status', 'APPROVED', '已通过', '#52c41a', 2, 'ACTIVE', '审批通过', NOW(), NOW()
    UNION ALL SELECT 'approval_status', 'REJECTED', '已拒绝', '#ff4d4f', 3, 'ACTIVE', '审批拒绝', NOW(), NOW()
    UNION ALL SELECT 'approval_status', 'CANCELLED', '已撤回', '#999999', 4, 'ACTIVE', '审批撤回', NOW(), NOW()
) AS tmp
WHERE @has_dict = 0;

-- 工作流类型字典
INSERT INTO sys_dict (category, dict_key, dict_value, extra_value, sort_order, status, remark, created_time, updated_time)
SELECT * FROM (
    SELECT 'workflow_type' AS category, 'PRICE_CHANGE' AS dict_key, '价格变更' AS dict_value, NULL AS extra_value, 1 AS sort_order, 'ACTIVE' AS status, '价格变更审批' AS remark, NOW() AS created_time, NOW() AS updated_time
    UNION ALL SELECT 'workflow_type', 'PRODUCT_CREATE', '产品创建', NULL, 2, 'ACTIVE', '产品创建审批', NOW(), NOW()
) AS tmp
WHERE @has_dict = 0;

-- 审批节点类型字典
INSERT INTO sys_dict (category, dict_key, dict_value, extra_value, sort_order, status, remark, created_time, updated_time)
SELECT * FROM (
    SELECT 'node_type' AS category, 'APPROVER' AS dict_key, '审批' AS dict_value, NULL AS extra_value, 1 AS sort_order, 'ACTIVE' AS status, '审批节点' AS remark, NOW() AS created_time, NOW() AS updated_time
    UNION ALL SELECT 'node_type', 'NOTIFIER', '知会', NULL, 2, 'ACTIVE', '知会节点', NOW(), NOW()
) AS tmp
WHERE @has_dict = 0;

-- 业务类型字典
INSERT INTO sys_dict (category, dict_key, dict_value, extra_value, sort_order, status, remark, created_time, updated_time)
SELECT * FROM (
    SELECT 'business_type' AS category, 'PRICE' AS dict_key, '价格' AS dict_value, NULL AS extra_value, 1 AS sort_order, 'ACTIVE' AS status, '价格业务' AS remark, NOW() AS created_time, NOW() AS updated_time
    UNION ALL SELECT 'business_type', 'PRODUCT', '产品', NULL, 2, 'ACTIVE', '产品业务', NOW(), NOW()
) AS tmp
WHERE @has_dict = 0;

-- 审批操作字典
INSERT INTO sys_dict (category, dict_key, dict_value, extra_value, sort_order, status, remark, created_time, updated_time)
SELECT * FROM (
    SELECT 'approval_action' AS category, 'APPROVE' AS dict_key, '通过' AS dict_value, NULL AS extra_value, 1 AS sort_order, 'ACTIVE' AS status, '审批通过' AS remark, NOW() AS created_time, NOW() AS updated_time
    UNION ALL SELECT 'approval_action', 'REJECT', '拒绝', NULL, 2, 'ACTIVE', '审批拒绝', NOW(), NOW()
) AS tmp
WHERE @has_dict = 0;

-- 价格变更类型字典
INSERT INTO sys_dict (category, dict_key, dict_value, extra_value, sort_order, status, remark, created_time, updated_time)
SELECT * FROM (
    SELECT 'change_type' AS category, 'CREATE' AS dict_key, '新建' AS dict_value, NULL AS extra_value, 1 AS sort_order, 'ACTIVE' AS status, '新建记录' AS remark, NOW() AS created_time, NOW() AS updated_time
    UNION ALL SELECT 'change_type', 'UPDATE', '更新', NULL, 2, 'ACTIVE', '更新记录', NOW(), NOW()
    UNION ALL SELECT 'change_type', 'DELETE', '删除', NULL, 3, 'ACTIVE', '删除记录', NOW(), NOW()
) AS tmp
WHERE @has_dict = 0;

-- 计量单位字典
INSERT INTO sys_dict (category, dict_key, dict_value, extra_value, sort_order, status, remark, created_time, updated_time)
SELECT * FROM (
    SELECT 'unit' AS category, '元/吨' AS dict_key, '元/吨' AS dict_value, NULL AS extra_value, 1 AS sort_order, 'ACTIVE' AS status, '每吨价格（元）' AS remark, NOW() AS created_time, NOW() AS updated_time
    UNION ALL SELECT 'unit', '万元/吨', '万元/吨', NULL, 2, 'ACTIVE', '每吨价格（万元）', NOW(), NOW()
    UNION ALL SELECT 'unit', '元/克', '元/克', NULL, 3, 'ACTIVE', '每克价格（元）', NOW(), NOW()
    UNION ALL SELECT 'unit', '元/千克', '元/千克', NULL, 4, 'ACTIVE', '每千克价格（元）', NOW(), NOW()
    UNION ALL SELECT 'unit', '元/吨度', '元/吨度', NULL, 5, 'ACTIVE', '每吨度价格（元）', NOW(), NOW()
) AS tmp
WHERE @has_dict = 0;

-- 部门类型字典
INSERT INTO sys_dict (category, dict_key, dict_value, extra_value, sort_order, status, remark, created_time, updated_time)
SELECT * FROM (
    SELECT 'dept_type' AS category, 'HEADQUARTERS' AS dict_key, '总部' AS dict_value, '#6366f1' AS extra_value, 1 AS sort_order, 'ACTIVE' AS status, '总部/集团' AS remark, NOW() AS created_time, NOW() AS updated_time
    UNION ALL SELECT 'dept_type', 'COMPANY', '子公司', '#f59e0b', 2, 'ACTIVE', '子公司/分公司', NOW(), NOW()
    UNION ALL SELECT 'dept_type', 'DEPARTMENT', '部门', '#10b981', 3, 'ACTIVE', '普通部门', NOW(), NOW()
) AS tmp
WHERE @has_dict = 0;

-- 字体大小字典（style 分类）- 独立条件检查
SET @has_font_size_dict = 0;
SELECT COUNT(*) INTO @has_font_size_dict FROM sys_dict WHERE category='style' AND dict_key='font_size_xs';

INSERT INTO sys_dict (category, dict_key, dict_value, extra_value, sort_order, status, remark, created_time, updated_time)
SELECT * FROM (
    SELECT 'style' AS category, 'font_size_xs' AS dict_key, 'auxiliary' AS dict_value, '0.75rem' AS extra_value, 20 AS sort_order, 'ACTIVE' AS status, 'caption, badge' AS remark, NOW() AS created_time, NOW() AS updated_time
    UNION ALL SELECT 'style', 'font_size_sm', 'table-cell', '0.875rem', 21, 'ACTIVE', 'table-cell', NOW(), NOW()
    UNION ALL SELECT 'style', 'font_size_base', 'body-header', '1rem', 22, 'ACTIVE', 'body, header', NOW(), NOW()
    UNION ALL SELECT 'style', 'font_size_lg', 'subtitle', '1.125rem', 23, 'ACTIVE', 'subtitle', NOW(), NOW()
    UNION ALL SELECT 'style', 'font_size_xl', 'section-title', '1.25rem', 24, 'ACTIVE', 'section-title', NOW(), NOW()
    UNION ALL SELECT 'style', 'font_size_2xl', 'page-title', '1.5rem', 25, 'ACTIVE', 'page-title', NOW(), NOW()
    UNION ALL SELECT 'style', 'font_size_3xl', 'hero-title', '1.875rem', 26, 'ACTIVE', 'hero-title', NOW(), NOW()
) AS tmp
WHERE @has_font_size_dict = 0;

-- 分类视觉配置字典
SET @has_category_visual_dict = 0;
SELECT COUNT(*) INTO @has_category_visual_dict FROM sys_dict WHERE category='category_visual_config';

INSERT INTO sys_dict (category, dict_key, dict_value, extra_value, sort_order, status, remark, created_time, updated_time)
SELECT * FROM (
    -- 黑色金属：钢铁灰色调
    SELECT 'category_visual_config' AS category, 'BLACK_METAL' AS dict_key, '黑色金属' AS dict_value,
    '{"categoryCode":"BLACK_METAL","primaryColor":"#4A5568","secondaryColor":"#718096","textColor":"#2D3748","borderColor":"#4A5568","glowColor":"rgba(74,85,104,0.2)","icon":"iron_ore","iconType":"builtin","darkMode":{"primaryColor":"#718096","textColor":"#E2E8F0","borderColor":"#4A5568","glowColor":"rgba(113,128,150,0.25)"}}' AS extra_value,
    1 AS sort_order, 'ACTIVE' AS status, '钢铁类产品视觉配置' AS remark, NOW() AS created_time, NOW() AS updated_time
    -- 有色金属：金铜色调
    UNION ALL SELECT 'category_visual_config', 'NON_FERROUS_METAL', '有色金属',
    '{"categoryCode":"NON_FERROUS_METAL","primaryColor":"#B87333","secondaryColor":"#D4A574","textColor":"#8B4513","borderColor":"#B87333","glowColor":"rgba(184,115,51,0.2)","icon":"copper_coil","iconType":"builtin","darkMode":{"primaryColor":"#D4A574","textColor":"#F5F7FA","borderColor":"#B87333","glowColor":"rgba(212,165,116,0.25)"}}',
    2, 'ACTIVE', '铜铝等有色金属视觉配置', NOW(), NOW()
    -- 贵金属：奢华金色
    UNION ALL SELECT 'category_visual_config', 'PRECIOUS_METAL', '贵金属',
    '{"categoryCode":"PRECIOUS_METAL","primaryColor":"#D4AF37","secondaryColor":"#FFD700","textColor":"#8B6914","borderColor":"#D4AF37","glowColor":"rgba(212,175,55,0.25)","icon":"gold_ingot","iconType":"builtin","darkMode":{"primaryColor":"#FFD700","textColor":"#FFF8DC","borderColor":"#D4AF37","glowColor":"rgba(255,215,0,0.3)"}}',
    3, 'ACTIVE', '金银铂贵金属视觉配置', NOW(), NOW()
    -- 化工产品：科技紫色调
    UNION ALL SELECT 'category_visual_config', 'CHEMICAL', '化工产品',
    '{"categoryCode":"CHEMICAL","primaryColor":"#8B5CF6","secondaryColor":"#A78BFA","textColor":"#6D28D9","borderColor":"#8B5CF6","glowColor":"rgba(139,92,246,0.2)","icon":"rare_element","iconType":"builtin","darkMode":{"primaryColor":"#A78BFA","textColor":"#EDE9FE","borderColor":"#8B5CF6","glowColor":"rgba(167,139,250,0.25)"}}',
    4, 'ACTIVE', '化工原料产品视觉配置', NOW(), NOW()
    -- 煤炭及焦炭：深黑色调
    UNION ALL SELECT 'category_visual_config', 'COAL', '煤炭及焦炭',
    '{"categoryCode":"COAL","primaryColor":"#1F2937","secondaryColor":"#374151","textColor":"#111827","borderColor":"#1F2937","glowColor":"rgba(31,41,55,0.3)","icon":"iron_ore","iconType":"builtin","darkMode":{"primaryColor":"#374151","textColor":"#F9FAFB","borderColor":"#1F2937","glowColor":"rgba(55,65,81,0.35)"}}',
    5, 'ACTIVE', '煤炭焦炭产品视觉配置', NOW(), NOW()
) AS tmp
WHERE @has_category_visual_dict = 0;

SELECT CONCAT('字典数据: ', IF(@has_dict > 0, '已存在，跳过', '初始化完成（11个分类，含分类视觉配置）')) AS status;

-- =====================================================
-- 8. 审批流程定义表
-- =====================================================
CREATE TABLE IF NOT EXISTS approval_workflow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '流程ID',
    workflow_name VARCHAR(100) NOT NULL COMMENT '流程名称',
    workflow_type VARCHAR(50) NOT NULL COMMENT '流程类型：PRICE_CHANGE, PRODUCT_CREATE',
    approval_level INT NOT NULL DEFAULT 1 COMMENT '审批级别（1-3级）',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_workflow_type (workflow_type),
    INDEX idx_workflow_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流程定义表';

-- =====================================================
-- 9. 审批节点表
-- =====================================================
CREATE TABLE IF NOT EXISTS approval_node (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '节点ID',
    workflow_id BIGINT NOT NULL COMMENT '流程ID',
    node_order INT NOT NULL COMMENT '节点顺序',
    node_type VARCHAR(20) NOT NULL COMMENT '节点类型：APPROVER审批, NOTIFIER知会',
    approver_role VARCHAR(20) COMMENT '审批角色：ADMIN, EDITOR',
    is_required BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否必须审批',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    FOREIGN KEY (workflow_id) REFERENCES approval_workflow(id) ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_node_workflow (workflow_id),
    INDEX idx_node_order (node_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批节点表';

-- =====================================================
-- 10. 审批请求表
-- =====================================================
CREATE TABLE IF NOT EXISTS approval_request (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '请求ID',
    workflow_id BIGINT NOT NULL COMMENT '流程ID',
    business_type VARCHAR(50) NOT NULL COMMENT '业务类型：PRICE, PRODUCT',
    business_id BIGINT NOT NULL COMMENT '业务数据ID（价格时为productId，产品时为产品ID）',
    current_node_id BIGINT COMMENT '当前节点ID',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING待审批, APPROVED已通过, REJECTED已拒绝, CANCELLED已撤回',
    applicant_id BIGINT NOT NULL COMMENT '申请人ID',
    request_data TEXT COMMENT '变更数据（JSON格式存储待审批的变更内容）',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (workflow_id) REFERENCES approval_workflow(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    FOREIGN KEY (current_node_id) REFERENCES approval_node(id) ON DELETE SET NULL ON UPDATE CASCADE,
    INDEX idx_request_business (business_type, business_id),
    INDEX idx_request_status (status),
    INDEX idx_request_applicant (applicant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批请求表';

-- =====================================================
-- 11. 审批记录表
-- =====================================================
CREATE TABLE IF NOT EXISTS approval_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
    request_id BIGINT NOT NULL COMMENT '请求ID',
    node_id BIGINT NOT NULL COMMENT '节点ID',
    approver_id BIGINT COMMENT '审批人ID',
    action VARCHAR(20) NOT NULL COMMENT '操作：APPROVE通过, REJECT拒绝',
    comment TEXT COMMENT '审批意见',
    old_value TEXT COMMENT '变更前值',
    new_value TEXT COMMENT '变更后值',
    action_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '操作时间',
    FOREIGN KEY (request_id) REFERENCES approval_request(id) ON DELETE CASCADE ON UPDATE CASCADE,
    FOREIGN KEY (node_id) REFERENCES approval_node(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_record_request (request_id),
    INDEX idx_record_approver (approver_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批记录表';

-- =====================================================
-- 9. 初始化审批工作流数据
-- =====================================================

SET @has_workflow = 0;
SELECT COUNT(*) INTO @has_workflow FROM approval_workflow;

INSERT INTO approval_workflow (id, workflow_name, workflow_type, approval_level, is_active, created_time, updated_time)
SELECT * FROM (
    SELECT 1 AS id, '价格变更审批' AS workflow_name, 'PRICE_CHANGE' AS workflow_type, 2 AS approval_level, FALSE AS is_active, NOW() AS created_time, NOW() AS updated_time
    UNION ALL SELECT 2, '产品创建审批', 'PRODUCT_CREATE', 1, FALSE, NOW(), NOW()
) AS tmp
WHERE @has_workflow = 0;

SELECT CONCAT('审批工作流数据: ', IF(@has_workflow > 0, '已存在，跳过', '初始化完成（2个工作流，默认停用）')) AS status;

-- =====================================================
-- 10. 初始化审批节点数据
-- =====================================================

SET @has_node = 0;
SELECT COUNT(*) INTO @has_node FROM approval_node;

INSERT INTO approval_node (id, workflow_id, node_order, node_type, approver_role, is_required, created_time)
SELECT * FROM (
    SELECT 1 AS id, 1 AS workflow_id, 1 AS node_order, 'APPROVER' AS node_type, 'EDITOR' AS approver_role, TRUE AS is_required, NOW() AS created_time
    UNION ALL SELECT 2, 1, 2, 'APPROVER', 'ADMIN', TRUE, NOW()
    UNION ALL SELECT 3, 2, 1, 'APPROVER', 'ADMIN', TRUE, NOW()
) AS tmp
WHERE @has_node = 0;

SELECT CONCAT('审批节点数据: ', IF(@has_node > 0, '已存在，跳过', '初始化完成（3个节点）')) AS status;

-- =====================================================
-- 11. 部门组织表
-- =====================================================
CREATE TABLE IF NOT EXISTS sys_department (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '部门ID',
    parent_id BIGINT COMMENT '父部门ID（NULL表示顶级）',
    dept_code VARCHAR(50) NOT NULL UNIQUE COMMENT '部门编码',
    dept_name VARCHAR(100) NOT NULL COMMENT '部门名称',
    dept_type VARCHAR(20) NOT NULL DEFAULT 'DEPARTMENT' COMMENT '类型：HEADQUARTERS总部/COMPANY公司/DEPARTMENT部门',
    leader_id BIGINT COMMENT '部门负责人ID',
    sort_order INT DEFAULT 0 COMMENT '排序',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/INACTIVE',
    path VARCHAR(500) COMMENT '层级路径（如：1/2/3）',
    level INT DEFAULT 1 COMMENT '层级深度',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_dept_parent (parent_id),
    INDEX idx_dept_code (dept_code),
    INDEX idx_dept_path (path),
    INDEX idx_dept_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门组织表';

-- =====================================================
-- 12. 系统角色表
-- =====================================================
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '角色ID',
    role_code VARCHAR(50) NOT NULL UNIQUE COMMENT '角色编码',
    role_name VARCHAR(100) NOT NULL COMMENT '角色名称',
    description VARCHAR(500) COMMENT '角色描述',
    dept_id BIGINT COMMENT '所属部门（NULL表示全局角色）',
    sort_order INT DEFAULT 0 COMMENT '排序',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/INACTIVE',
    is_system BOOLEAN DEFAULT FALSE COMMENT '是否系统内置角色',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_role_code (role_code),
    INDEX idx_role_status (status),
    INDEX idx_role_dept (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色表';

-- =====================================================
-- 13. 系统权限表
-- =====================================================
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

-- =====================================================
-- 14. 用户角色关联表
-- =====================================================
CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '关联ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    UNIQUE KEY uk_user_role (user_id, role_id),
    INDEX idx_user_role_user (user_id),
    INDEX idx_user_role_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- =====================================================
-- 15. 角色权限关联表
-- =====================================================
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '关联ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    UNIQUE KEY uk_role_permission (role_id, permission_id),
    INDEX idx_role_permission_role (role_id),
    INDEX idx_role_permission_permission (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

-- =====================================================
-- 16. 初始化部门数据
-- =====================================================
SET @has_dept = 0;
SELECT COUNT(*) INTO @has_dept FROM sys_department;

INSERT INTO sys_department (id, parent_id, dept_code, dept_name, dept_type, sort_order, status, path, level, created_time, updated_time)
SELECT * FROM (
    SELECT 1 AS id, NULL AS parent_id, 'HQ' AS dept_code, '总部' AS dept_name, 'HEADQUARTERS' AS dept_type, 1 AS sort_order, 'ACTIVE' AS status, '1' AS path, 1 AS level, NOW() AS created_time, NOW() AS updated_time
) AS tmp
WHERE @has_dept = 0;

SELECT CONCAT('部门数据: ', IF(@has_dept > 0, '已存在，跳过', '初始化完成（1个总部）')) AS status;

-- =====================================================
-- 17. 初始化角色数据
-- =====================================================
SET @has_sys_role = 0;
SELECT COUNT(*) INTO @has_sys_role FROM sys_role;

INSERT INTO sys_role (id, role_code, role_name, description, sort_order, status, is_system, created_time, updated_time)
SELECT * FROM (
    SELECT 1 AS id, 'ADMIN' AS role_code, '系统管理员' AS role_name, '拥有所有权限' AS description, 1 AS sort_order, 'ACTIVE' AS status, TRUE AS is_system, NOW() AS created_time, NOW() AS updated_time
    UNION ALL SELECT 2, 'EDITOR', '编辑用户', '可编辑产品价格数据', 2, 'ACTIVE', TRUE, NOW(), NOW()
    UNION ALL SELECT 3, 'VIEWER', '普通用户', '仅可查看数据', 3, 'ACTIVE', TRUE, NOW(), NOW()
) AS tmp
WHERE @has_sys_role = 0;

SELECT CONCAT('角色数据: ', IF(@has_sys_role > 0, '已存在，跳过', '初始化完成（3个角色）')) AS status;

-- =====================================================
-- 18. 初始化权限数据
-- =====================================================
SET @has_sys_permission = 0;
SELECT COUNT(*) INTO @has_sys_permission FROM sys_permission;

INSERT INTO sys_permission (id, permission_code, permission_name, permission_type, parent_id, resource_url, sort_order, status, created_time, updated_time)
SELECT * FROM (
    SELECT 1 AS id, 'home:view' AS permission_code, '首页查看' AS permission_name, 'MENU' AS permission_type, NULL AS parent_id, '/home' AS resource_url, 1 AS sort_order, 'ACTIVE' AS status, NOW() AS created_time, NOW() AS updated_time
    UNION ALL SELECT 2, 'product:view', '产品列表查看', 'MENU', NULL, '/products', 10, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 3, 'product:create', '产品创建', 'BUTTON', 2, NULL, 11, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 4, 'product:edit', '产品编辑', 'BUTTON', 2, NULL, 12, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 5, 'product:delete', '产品删除', 'BUTTON', 2, NULL, 13, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 6, 'product:import', '产品导入', 'BUTTON', 2, NULL, 14, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 7, 'product:export', '产品导出', 'BUTTON', 2, NULL, 15, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 8, 'price:view', '价格查看', 'MENU', NULL, '/price-maintenance', 20, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 9, 'price:edit', '价格编辑', 'BUTTON', 8, NULL, 21, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 10, 'price:approve', '价格审批', 'BUTTON', 8, NULL, 22, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 32, 'price:export', '价格导出', 'BUTTON', 8, NULL, 23, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 11, 'category:view', '分类管理查看', 'MENU', NULL, '/categories', 30, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 12, 'category:edit', '分类编辑', 'BUTTON', 11, NULL, 31, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 13, 'origin:view', '产地管理查看', 'MENU', NULL, '/origins', 40, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 14, 'origin:edit', '产地编辑', 'BUTTON', 13, NULL, 41, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 15, 'customer:view', '客户管理查看', 'MENU', NULL, '/customers', 50, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 16, 'customer:edit', '客户编辑', 'BUTTON', 15, NULL, 51, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 17, 'approval:view', '审批查看', 'MENU', NULL, '/approval', 60, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 18, 'approval:create', '审批创建', 'BUTTON', 17, NULL, 61, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 19, 'approval:process', '审批处理', 'BUTTON', 17, NULL, 62, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 20, 'user:view', '用户管理查看', 'MENU', NULL, '/users', 100, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 21, 'user:create', '用户创建', 'BUTTON', 20, NULL, 101, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 22, 'user:edit', '用户编辑', 'BUTTON', 20, NULL, 102, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 23, 'user:delete', '用户删除', 'BUTTON', 20, NULL, 103, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 24, 'user:password:reset', '用户密码重置', 'BUTTON', 20, NULL, 104, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 25, 'role:view', '角色管理查看', 'MENU', NULL, '/roles', 110, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 26, 'role:edit', '角色编辑', 'BUTTON', 25, NULL, 111, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 27, 'dept:view', '部门管理查看', 'MENU', NULL, '/departments', 120, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 28, 'dept:edit', '部门编辑', 'BUTTON', 27, NULL, 121, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 29, 'log:view', '日志查看', 'MENU', NULL, '/operation-log', 130, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 30, 'log:export', '日志导出', 'BUTTON', 29, NULL, 131, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 31, 'system:setting', '系统设置', 'MENU', NULL, '/system-settings', 140, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 33, 'notification:view', '通知管理查看', 'MENU', NULL, '/notifications', 150, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 34, 'notification:retry', '通知投递重试', 'BUTTON', 33, NULL, 151, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 35, 'system-notice:create', '系统公告创建', 'BUTTON', 33, NULL, 152, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 36, 'system-notice:cancel', '系统公告撤回', 'BUTTON', 33, NULL, 153, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 37, 'notification:subscription:view', '订阅授权查看', 'BUTTON', 33, NULL, 154, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 38, 'notification:subscription:guide', '订阅授权引导', 'BUTTON', 33, NULL, 155, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 39, 'notification:subscription:resolve', '订阅异常处理', 'BUTTON', 33, NULL, 156, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 40, 'notification:test-token', '通知渠道远程校验', 'BUTTON', 33, NULL, 157, 'ACTIVE', NOW(), NOW()
    UNION ALL SELECT 41, 'notification:test-delivery', '通知渠道测试投递', 'BUTTON', 33, NULL, 158, 'ACTIVE', NOW(), NOW()
) AS tmp
WHERE @has_sys_permission = 0;

SELECT CONCAT('权限数据: ', IF(@has_sys_permission > 0, '已存在，跳过', '初始化完成（41个权限）')) AS status;

-- =====================================================
-- 19. 初始化角色权限关联
-- =====================================================
SET @has_role_permission = 0;
SELECT COUNT(*) INTO @has_role_permission FROM sys_role_permission;

-- ADMIN拥有所有权限
INSERT INTO sys_role_permission (role_id, permission_id, created_time)
SELECT 1 AS role_id, p.id AS permission_id, NOW() AS created_time
FROM sys_permission p
WHERE @has_role_permission = 0;

-- EDITOR拥有产品、价格、分类、产地、客户、审批相关权限
INSERT INTO sys_role_permission (role_id, permission_id, created_time)
SELECT 2 AS role_id, p.id AS permission_id, NOW() AS created_time
FROM sys_permission p
WHERE p.permission_code IN ('home:view', 'product:view', 'product:create', 'product:edit', 'product:import', 'product:export', 'price:view', 'price:edit', 'price:export', 'category:view', 'category:edit', 'origin:view', 'origin:edit', 'customer:view', 'customer:edit', 'approval:view', 'approval:create', 'approval:process', 'log:view')
AND @has_role_permission = 0;

-- VIEWER仅拥有查看权限
INSERT INTO sys_role_permission (role_id, permission_id, created_time)
SELECT 3 AS role_id, p.id AS permission_id, NOW() AS created_time
FROM sys_permission p
WHERE (p.permission_code LIKE '%:view' OR p.permission_code = 'price:export')
AND @has_role_permission = 0;

SELECT CONCAT('角色权限关联: ', IF(@has_role_permission > 0, '已存在，跳过', '初始化完成')) AS status;

-- =====================================================
-- 20. 初始化用户角色关联
-- =====================================================
SET @has_user_role = 0;
SELECT COUNT(*) INTO @has_user_role FROM sys_user_role;

-- 为默认用户分配角色（user_id: 1=admin, 2=editor, 3=viewer; role_id: 1=ADMIN, 2=EDITOR, 3=VIEWER）
INSERT INTO sys_user_role (user_id, role_id, created_time)
SELECT * FROM (
    SELECT 1 AS user_id, 1 AS role_id, NOW() AS created_time
    UNION ALL SELECT 2, 2, NOW()
    UNION ALL SELECT 3, 3, NOW()
) AS tmp
WHERE @has_user_role = 0;

SELECT CONCAT('用户角色关联: ', IF(@has_user_role > 0, '已存在，跳过', '初始化完成（3个关联）')) AS status;

-- =====================================================
-- 初始化完成提示
-- =====================================================

SELECT '========================================' AS '';
SELECT '  数据初始化完成！' AS message;
SELECT '========================================' AS '';
SELECT '' AS '';
SELECT '默认用户:' AS '';
SELECT '  admin   / admin123   (管理员)' AS '';
SELECT '  editor  / admin123   (编辑者)' AS '';
SELECT '  viewer  / admin123   (查看者)' AS '';
SELECT '' AS '';
SELECT '初始化数据统计:' AS '';
SELECT CONCAT('  - 产品分类: 5 个') AS '';
SELECT CONCAT('  - 产品: 20 个') AS '';
SELECT CONCAT('  - 用户: 3 个') AS '';
SELECT CONCAT('  - 审批工作流: 2 个（默认停用）') AS '';
SELECT CONCAT('  - 审批节点: 3 个') AS '';

-- =====================================================
-- 21. 样式配置专用表
-- =====================================================

-- 样式配置主表（当前生效配置）
CREATE TABLE IF NOT EXISTS sys_style_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '配置ID',
    config_key VARCHAR(100) NOT NULL UNIQUE COMMENT '配置键',
    config_value TEXT COMMENT '配置值',
    config_type VARCHAR(50) DEFAULT 'string' COMMENT '类型：string/json/color/font',
    description VARCHAR(500) COMMENT '说明',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_style_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='样式配置表';

-- 样式预设表（色彩方案、布局方案等）
CREATE TABLE IF NOT EXISTS sys_style_preset (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '预设ID',
    preset_type VARCHAR(50) NOT NULL COMMENT '预设类型：color_scheme/layout_style/font_preset',
    preset_key VARCHAR(100) NOT NULL COMMENT '预设键',
    preset_name VARCHAR(200) NOT NULL COMMENT '预设名称',
    preset_description VARCHAR(500) COMMENT '预设说明',
    config_json TEXT NOT NULL COMMENT '配置 JSON',
    is_default TINYINT DEFAULT 0 COMMENT '是否默认',
    sort_order INT DEFAULT 0 COMMENT '排序',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_preset_type_key (preset_type, preset_key),
    INDEX idx_preset_type (preset_type),
    INDEX idx_preset_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='样式预设表';

-- =====================================================
-- 22. 初始化样式配置数据
-- =====================================================

SET @has_style_config = 0;
SELECT COUNT(*) INTO @has_style_config FROM sys_style_config;

-- 基础样式配置
INSERT INTO sys_style_config (config_key, config_value, config_type, description, created_time, updated_time)
SELECT * FROM (
    SELECT 'system_name' AS config_key, '价格管理系统' AS config_value, 'string' AS config_type, '系统显示名称' AS description, NOW() AS created_time, NOW() AS updated_time
    UNION ALL SELECT 'logo_url', '', 'string', 'Logo URL', NOW(), NOW()
    UNION ALL SELECT 'logo_size', 'medium', 'string', 'Logo 尺寸：small/medium/large/xlarge', NOW(), NOW()
    UNION ALL SELECT 'heading_font', 'Newsreader', 'font', '标题字体', NOW(), NOW()
    UNION ALL SELECT 'body_font', 'Inter', 'font', '正文字体', NOW(), NOW()
    UNION ALL SELECT 'number_font', 'JetBrains Mono', 'font', '数字字体', NOW(), NOW()
    UNION ALL SELECT 'font_size_preset', 'standard', 'string', '字号预设', NOW(), NOW()
    UNION ALL SELECT 'font_size_xs', '0.8125rem', 'string', '辅助信息字号', NOW(), NOW()
    UNION ALL SELECT 'font_size_sm', '0.875rem', 'string', '表格内容字号', NOW(), NOW()
    UNION ALL SELECT 'font_size_base', '1rem', 'string', '正文表头字号', NOW(), NOW()
    UNION ALL SELECT 'font_size_lg', '1.125rem', 'string', '小节标题字号', NOW(), NOW()
    UNION ALL SELECT 'font_size_xl', '1.25rem', 'string', '页面副标题字号', NOW(), NOW()
    UNION ALL SELECT 'font_size_2xl', '1.5rem', 'string', '页面主标题字号', NOW(), NOW()
    UNION ALL SELECT 'font_size_3xl', '2rem', 'string', '特大标题字号', NOW(), NOW()
    UNION ALL SELECT 'active_color_scheme', 'scheme_teal_classic', 'string', '当前色彩方案', NOW(), NOW()
    UNION ALL SELECT 'active_layout_style', 'layout_top_nav', 'string', '当前布局方案', NOW(), NOW()
    UNION ALL SELECT 'subtitle_text', '价格展示与管理平台', 'string', '登录页副标题文案', NOW(), NOW()
    UNION ALL SELECT 'subtitle_font', 'body', 'string', '登录页副标题字体', NOW(), NOW()
    UNION ALL SELECT 'subtitle_font_weight', '400', 'string', '登录页副标题字重', NOW(), NOW()
    UNION ALL SELECT 'subtitle_color', 'rgba(255, 255, 255, 0.75)', 'string', '登录页副标题颜色', NOW(), NOW()
    UNION ALL SELECT 'active_theme', 'theme_red_green', 'string', '兼容旧主题', NOW(), NOW()
) AS tmp
WHERE @has_style_config = 0;

SELECT CONCAT('样式配置数据: ', IF(@has_style_config > 0, '已存在，跳过', '初始化完成（20项配置）')) AS status;

-- =====================================================
-- 23. 初始化色彩方案预设
-- =====================================================

SET @has_color_scheme = 0;
SELECT COUNT(*) INTO @has_color_scheme FROM sys_style_preset WHERE preset_type='color_scheme';

INSERT INTO sys_style_preset (preset_type, preset_key, preset_name, preset_description, config_json, is_default, sort_order, status, created_time, updated_time)
SELECT * FROM (
    -- 方案一：青绿经典（默认）
    SELECT 'color_scheme' AS preset_type, 'scheme_teal_classic' AS preset_key, '青绿经典（默认）' AS preset_name, '当前系统默认配色，青绿主色，专业稳重' AS preset_description,
    '{"priceRise":"#EF4444","priceFall":"#10B981","priceFlat":"#9CA3AF","chartPrimary":"#0D6E6E","chartColors":["#0D6E6E","#10B981","#F59E0B","#EF4444","#8B5CF6","#EC4899","#6366F1","#14B8A6","#64748B"]}' AS config_json,
    1 AS is_default, 1 AS sort_order, 'ACTIVE' AS status, NOW() AS created_time, NOW() AS updated_time
    -- 方案二：美股绿红
    UNION ALL SELECT 'color_scheme', 'scheme_us_stock', '美股绿红', '美股风格，涨价绿色，跌价红色',
    '{"priceRise":"#10B981","priceFall":"#EF4444","priceFlat":"#9CA3AF","chartPrimary":"#0D6E6E","chartColors":["#0D6E6E","#10B981","#F59E0B","#EF4444","#8B5CF6","#EC4899","#6366F1","#14B8A6","#64748B"]}',
    0, 2, 'ACTIVE', NOW(), NOW()
    -- 方案三：商务蓝橙
    UNION ALL SELECT 'color_scheme', 'scheme_business', '商务蓝橙', '商务风格配色',
    '{"priceRise":"#3B82F6","priceFall":"#F97316","priceFlat":"#9CA3AF","chartPrimary":"#3B82F6","chartColors":["#3B82F6","#F97316","#0D6E6E","#8B5CF6","#EC4899","#6366F1","#14B8A6","#64748B","#10B981"]}',
    0, 3, 'ACTIVE', NOW(), NOW()
    -- 方案四：高贵紫金
    UNION ALL SELECT 'color_scheme', 'scheme_noble', '高贵紫金', '高贵风格配色',
    '{"priceRise":"#8B5CF6","priceFall":"#EAB308","priceFlat":"#9CA3AF","chartPrimary":"#8B5CF6","chartColors":["#8B5CF6","#EAB308","#0D6E6E","#EC4899","#6366F1","#14B8A6","#64748B","#10B981","#F59E0B"]}',
    0, 4, 'ACTIVE', NOW(), NOW()
    -- 方案五：深矿蓝
    UNION ALL SELECT 'color_scheme', 'scheme_deep_blue', '深矿蓝', '参考图配色，专业科技风格',
    '{"priceRise":"#EF4444","priceFall":"#10B981","priceFlat":"#9CA3AF","chartPrimary":"#165DFF","chartColors":["#165DFF","#10B981","#F59E0B","#EF4444","#8B5CF6","#EC4899","#6366F1","#14B8A6","#64748B"]}',
    0, 5, 'ACTIVE', NOW(), NOW()
    -- 方案六：暖色系
    UNION ALL SELECT 'color_scheme', 'scheme_warm', '暖色系', '温暖活力配色',
    '{"priceRise":"#F97316","priceFall":"#06B6D4","priceFlat":"#9CA3AF","chartPrimary":"#F97316","chartColors":["#F97316","#06B6D4","#F59E0B","#EF4444","#8B5CF6","#EC4899","#6366F1","#14B8A6","#64748B"]}',
    0, 6, 'ACTIVE', NOW(), NOW()
) AS tmp
WHERE @has_color_scheme = 0;

SELECT CONCAT('色彩方案预设: ', IF(@has_color_scheme > 0, '已存在，跳过', '初始化完成（6套方案）')) AS status;

-- =====================================================
-- 24. 初始化布局方案预设
-- =====================================================

SET @has_layout_style = 0;
SELECT COUNT(*) INTO @has_layout_style FROM sys_style_preset WHERE preset_type='layout_style';

INSERT INTO sys_style_preset (preset_type, preset_key, preset_name, preset_description, config_json, is_default, sort_order, status, created_time, updated_time)
SELECT * FROM (
    -- 布局一：经典顶部导航（默认）
    SELECT 'layout_style' AS preset_type, 'layout_top_nav' AS preset_key, '经典顶部导航' AS preset_name, '传统后台管理布局' AS preset_description,
    '{"navPosition":"top","navBgColor":"#FFFFFF","navTextColor":"#1A1A1A","pageBgColor":"#FAFAFA","cardBgColor":"#FFFFFF","cardShadow":"0 1px 3px rgba(0,0,0,0.1)","borderRadius":"12px"}' AS config_json,
    1 AS is_default, 1 AS sort_order, 'ACTIVE' AS status, NOW() AS created_time, NOW() AS updated_time
    -- 布局二：左侧导航
    UNION ALL SELECT 'layout_style', 'layout_left_nav', '左侧导航', '功能较多的系统布局',
    '{"navPosition":"left","navBgColor":"#FFFFFF","navTextColor":"#1A1A1A","pageBgColor":"#FAFAFA","cardBgColor":"#FFFFFF","cardShadow":"0 1px 3px rgba(0,0,0,0.1)","borderRadius":"12px"}',
    0, 2, 'ACTIVE', NOW(), NOW()
    -- 布局三：深矿蓝仪表盘
    UNION ALL SELECT 'layout_style', 'layout_dashboard', '深矿蓝仪表盘', '参考图布局，专业数据展示',
    '{"navPosition":"left","navBgColor":"#1E3A5F","navTextColor":"#FFFFFF","pageBgColor":"#F5F5F5","cardBgColor":"#FFFFFF","cardShadow":"0 1px 3px rgba(0,0,0,0.1)","borderRadius":"8px","showTitleBar":true,"showMiniChart":true,"gradientChart":true}',
    0, 3, 'ACTIVE', NOW(), NOW()
    -- 布局四：极简卡片式
    UNION ALL SELECT 'layout_style', 'layout_minimal', '极简卡片式', '简洁现代布局',
    '{"navPosition":"top-minimal","navBgColor":"transparent","navTextColor":"#1A1A1A","pageBgColor":"#FAFAFA","cardBgColor":"#FFFFFF","cardShadow":"0 4px 6px rgba(0,0,0,0.1)","borderRadius":"16px"}',
    0, 4, 'ACTIVE', NOW(), NOW()
) AS tmp
WHERE @has_layout_style = 0;

SELECT CONCAT('布局方案预设: ', IF(@has_layout_style > 0, '已存在，跳过', '初始化完成（4套方案）')) AS status;

-- =====================================================
-- 25. 初始化字号预设
-- =====================================================

SET @has_font_preset = 0;
SELECT COUNT(*) INTO @has_font_preset FROM sys_style_preset WHERE preset_type='font_preset';

INSERT INTO sys_style_preset (preset_type, preset_key, preset_name, preset_description, config_json, is_default, sort_order, status, created_time, updated_time)
SELECT * FROM (
    -- 紧凑
    SELECT 'font_preset' AS preset_type, 'compact' AS preset_key, '紧凑' AS preset_name, '高密度但保持可读' AS preset_description,
    '{"xs":"0.75rem","sm":"0.8125rem","base":"0.9375rem","lg":"1rem","xl":"1.125rem","2xl":"1.375rem","3xl":"1.75rem"}' AS config_json,
    0 AS is_default, 1 AS sort_order, 'ACTIVE' AS status, NOW() AS created_time, NOW() AS updated_time
    -- 标准（默认）
    UNION ALL SELECT 'font_preset', 'standard', '标准', '通用场景',
    '{"xs":"0.8125rem","sm":"0.875rem","base":"1rem","lg":"1.125rem","xl":"1.25rem","2xl":"1.5rem","3xl":"2rem"}',
    1, 2, 'ACTIVE', NOW(), NOW()
    -- 大字体
    UNION ALL SELECT 'font_preset', 'large', '大字体', '阅读友好',
    '{"xs":"0.875rem","sm":"1rem","base":"1.125rem","lg":"1.25rem","xl":"1.5rem","2xl":"1.875rem","3xl":"2.375rem"}',
    0, 3, 'ACTIVE', NOW(), NOW()
    -- 特大字体（无障碍）
    UNION ALL SELECT 'font_preset', 'xlarge', '特大字体', '演示/投影/无障碍',
    '{"xs":"1rem","sm":"1.125rem","base":"1.25rem","lg":"1.5rem","xl":"1.75rem","2xl":"2.25rem","3xl":"2.75rem"}',
    0, 4, 'ACTIVE', NOW(), NOW()
) AS tmp
WHERE @has_font_preset = 0;

SELECT CONCAT('字号预设: ', IF(@has_font_preset > 0, '已存在，跳过', '初始化完成（4套预设）')) AS status;

-- =====================================================
-- 26. 价格草稿发布与通知表
-- =====================================================

CREATE TABLE IF NOT EXISTS price_draft_batch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '草稿批次ID',
    version BIGINT DEFAULT 0 COMMENT '乐观锁版本',
    effective_date DATE NOT NULL COMMENT '生效日期',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '草稿状态',
    source_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL' COMMENT '来源类型',
    product_scope_snapshot TEXT COMMENT '产品范围快照',
    item_count INT DEFAULT 0 COMMENT '产品总数',
    saved_item_count INT DEFAULT 0 COMMENT '已保存明细数',
    last_modified_by BIGINT COMMENT '最后修改人',
    published_time DATETIME COMMENT '发布时间',
    published_by BIGINT COMMENT '发布人',
    created_by BIGINT COMMENT '创建人',
    remark VARCHAR(500) COMMENT '备注',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_price_draft_batch_date_status (effective_date, status),
    INDEX idx_price_draft_batch_created_by (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='价格草稿批次表';

CREATE TABLE IF NOT EXISTS price_draft_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '草稿明细ID',
    batch_id BIGINT NOT NULL COMMENT '草稿批次ID',
    product_id BIGINT NOT NULL COMMENT '产品ID',
    base_price_id BIGINT COMMENT '基准价格ID',
    base_price_version BIGINT COMMENT '基准价格版本',
    original_price DECIMAL(15,4) COMMENT '原价格',
    current_price DECIMAL(15,4) NOT NULL COMMENT '草稿价格',
    cost_price DECIMAL(15,4) COMMENT '成本价',
    budget_price DECIMAL(15,4) COMMENT '预算价',
    effective_date DATE COMMENT '生效日期',
    expiry_date DATE COMMENT '失效日期',
    unit VARCHAR(50) COMMENT '计量单位',
    price_spec VARCHAR(200) COMMENT '价格规格',
    item_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '明细状态',
    last_modified_by BIGINT COMMENT '最后修改人',
    published_price_id BIGINT COMMENT '发布后价格ID',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT uk_price_draft_item_batch_product UNIQUE (batch_id, product_id),
    INDEX idx_price_draft_item_batch (batch_id),
    INDEX idx_price_draft_item_product (product_id),
    CONSTRAINT fk_price_draft_item_batch FOREIGN KEY (batch_id) REFERENCES price_draft_batch(id),
    CONSTRAINT fk_price_draft_item_product FOREIGN KEY (product_id) REFERENCES product(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='价格草稿明细表';

CREATE TABLE IF NOT EXISTS price_publish_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '发布日志ID',
    batch_id BIGINT NOT NULL COMMENT '草稿批次ID',
    effective_date DATE NOT NULL COMMENT '生效日期',
    publish_type VARCHAR(20) NOT NULL DEFAULT 'MANUAL' COMMENT '发布类型',
    status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS' COMMENT '发布结果',
    total_count INT DEFAULT 0 COMMENT '总数',
    success_count INT DEFAULT 0 COMMENT '成功数',
    fail_count INT DEFAULT 0 COMMENT '失败数',
    message TEXT COMMENT '结果消息',
    created_by BIGINT COMMENT '发布人',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    INDEX idx_price_publish_batch (batch_id),
    INDEX idx_price_publish_date (effective_date),
    CONSTRAINT fk_price_publish_batch FOREIGN KEY (batch_id) REFERENCES price_draft_batch(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='价格发布日志表';

CREATE TABLE IF NOT EXISTS notification_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '通知消息ID',
    type VARCHAR(50) NOT NULL COMMENT '通知类型',
    title VARCHAR(100) NOT NULL COMMENT '标题',
    summary VARCHAR(300) COMMENT '通知摘要',
    content TEXT COMMENT '内容',
    business_type VARCHAR(50) COMMENT '业务类型',
    business_id BIGINT COMMENT '业务ID',
    channels VARCHAR(200) COMMENT '通知渠道',
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '通知优先级',
    link_type VARCHAR(50) COMMENT '跳转类型',
    link_params TEXT COMMENT '跳转参数JSON',
    dedupe_key VARCHAR(150) COMMENT '通知幂等键',
    expire_time DATETIME COMMENT '过期时间',
    event_count BIGINT NOT NULL DEFAULT 1 COMMENT '聚合消息包含的事件数量',
    created_by BIGINT COMMENT '创建人',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    CONSTRAINT uk_notification_dedupe_key UNIQUE (dedupe_key),
    INDEX idx_notification_type (type),
    INDEX idx_notification_business (business_type, business_id),
    INDEX idx_notification_type_created (type, created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知消息表';

CREATE TABLE IF NOT EXISTS notification_recipient (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '通知接收ID',
    message_id BIGINT NOT NULL COMMENT '消息ID',
    user_id BIGINT NOT NULL COMMENT '接收用户ID',
    read_status VARCHAR(20) NOT NULL DEFAULT 'UNREAD' COMMENT '阅读状态',
    read_time DATETIME COMMENT '阅读时间',
    archived BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否归档',
    archived_time DATETIME COMMENT '归档时间',
    first_seen_time DATETIME COMMENT '首次触达时间',
    CONSTRAINT uk_notification_message_user UNIQUE (message_id, user_id),
    INDEX idx_notification_recipient_user (user_id, read_status),
    INDEX idx_notification_recipient_message (message_id),
    INDEX idx_notification_recipient_user_time (user_id, id),
    CONSTRAINT fk_notification_recipient_message FOREIGN KEY (message_id) REFERENCES notification_message(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知接收人表';

CREATE TABLE IF NOT EXISTS notification_delivery_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '投递日志ID',
    message_id BIGINT NOT NULL COMMENT '消息ID',
    recipient_id BIGINT NOT NULL COMMENT '接收记录ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    channel VARCHAR(50) NOT NULL COMMENT '渠道',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '投递状态',
    provider VARCHAR(50) COMMENT '服务商',
    provider_message_id VARCHAR(100) COMMENT '服务商消息ID',
    retry_count INT DEFAULT 0 COMMENT '重试次数',
    delivered_time DATETIME COMMENT '投递时间',
    error_code VARCHAR(100) COMMENT '错误编码',
    error_message VARCHAR(500) COMMENT '错误信息',
    is_test BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否测试投递',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_notification_delivery_message (message_id),
    INDEX idx_notification_delivery_user (user_id),
    INDEX idx_notification_delivery_status (status),
    INDEX idx_notification_delivery_test (is_test),
    CONSTRAINT fk_notification_delivery_message FOREIGN KEY (message_id) REFERENCES notification_message(id),
    CONSTRAINT fk_notification_delivery_recipient FOREIGN KEY (recipient_id) REFERENCES notification_recipient(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知渠道投递日志表';

CREATE TABLE IF NOT EXISTS notification_outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '通知Outbox ID',
    event_type VARCHAR(50) NOT NULL COMMENT '事件类型',
    aggregate_type VARCHAR(50) NOT NULL COMMENT '聚合类型',
    aggregate_id BIGINT NOT NULL COMMENT '聚合ID',
    payload_json TEXT COMMENT '事件快照JSON',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'Outbox状态',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    next_retry_time DATETIME COMMENT '下次重试时间',
    locked_by VARCHAR(100) COMMENT '锁定实例',
    lock_until DATETIME COMMENT '锁定到期时间',
    last_error_code VARCHAR(100) COMMENT '最近错误编码',
    last_error_message VARCHAR(500) COMMENT '最近错误信息',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT uk_notification_outbox_aggregate UNIQUE (aggregate_type, aggregate_id),
    INDEX idx_notification_outbox_status_retry (status, next_retry_time),
    INDEX idx_notification_outbox_lock (locked_by, lock_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知可靠投递Outbox表';

CREATE TABLE IF NOT EXISTS notification_preference (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '通知偏好ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    notification_type VARCHAR(50) NOT NULL COMMENT '通知类型',
    channel VARCHAR(50) NOT NULL COMMENT '渠道',
    enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否启用',
    quiet_start_time TIME COMMENT '免打扰开始时间',
    quiet_end_time TIME COMMENT '免打扰结束时间',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    CONSTRAINT uk_notification_preference_user_type_channel UNIQUE (user_id, notification_type, channel),
    INDEX idx_notification_preference_user (user_id),
    INDEX idx_notification_preference_type_channel (notification_type, channel),
    CONSTRAINT fk_notification_preference_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户通知偏好表';

CREATE TABLE IF NOT EXISTS notification_mini_program_subscription (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '小程序订阅授权ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    openid VARCHAR(100) NOT NULL COMMENT '微信小程序openid',
    notification_type VARCHAR(50) NOT NULL COMMENT '通知类型',
    template_id VARCHAR(100) NOT NULL COMMENT '订阅消息模板ID',
    status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN' COMMENT '授权状态',
    available_count INT NOT NULL DEFAULT 0 COMMENT '可用授权次数',
    last_authorized_time DATETIME COMMENT '最近授权时间',
    source VARCHAR(50) COMMENT '授权来源',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT uk_notification_mini_sub_user_type_template UNIQUE (user_id, notification_type, template_id),
    INDEX idx_notification_mini_sub_user (user_id),
    INDEX idx_notification_mini_sub_template (template_id),
    INDEX idx_notification_mini_sub_type (notification_type),
    CONSTRAINT fk_notification_mini_sub_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小程序订阅消息授权表';

CREATE TABLE IF NOT EXISTS notification_mini_program_eligibility (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '小程序订阅用户资格快照ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    row_status VARCHAR(20) NOT NULL COMMENT '聚合行状态',
    openid_bound BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否绑定小程序openid',
    configured_template_count INT NOT NULL DEFAULT 0 COMMENT '已配置模板数',
    authorized_template_count INT NOT NULL DEFAULT 0 COMMENT '当前可用授权模板数',
    available_total INT NOT NULL DEFAULT 0 COMMENT '当前可用授权总次数',
    last_authorized_time DATETIME COMMENT '最近授权时间',
    config_fingerprint VARCHAR(64) NOT NULL COMMENT '模板配置指纹',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT uk_notification_mini_eligibility_user UNIQUE (user_id),
    INDEX idx_notification_mini_eligibility_status_user (row_status, user_id),
    INDEX idx_notification_mini_eligibility_authorized (last_authorized_time),
    CONSTRAINT fk_notification_mini_eligibility_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小程序订阅用户资格查询快照';

CREATE TABLE IF NOT EXISTS notification_mini_program_resolution (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '订阅异常处理ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    resolve_status VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT '处理状态',
    resolve_remark VARCHAR(500) COMMENT '处理备注',
    remind_after DATETIME COMMENT '暂不提醒截止时间',
    follow_up_required BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否需要跟进',
    resolved_by BIGINT COMMENT '处理人',
    resolved_time DATETIME COMMENT '处理时间',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT uk_notification_mini_resolution_user UNIQUE (user_id),
    INDEX idx_notification_mini_resolution_status (resolve_status),
    CONSTRAINT fk_notification_mini_resolution_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小程序订阅异常处理记录';

CREATE TABLE IF NOT EXISTS notification_channel_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '通知渠道配置ID',
    channel VARCHAR(50) NOT NULL COMMENT '通知渠道',
    enabled BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否启用',
    app_id VARCHAR(100) COMMENT '非敏感应用标识',
    endpoint_url VARCHAR(500) COMMENT '接口地址',
    secret_cipher TEXT COMMENT '敏感密钥密文',
    secret_key_version VARCHAR(20) COMMENT '密钥加密版本',
    secret_fingerprint VARCHAR(64) COMMENT '密钥指纹',
    timeout_ms INT COMMENT '接口超时时间毫秒',
    default_page VARCHAR(200) COMMENT '默认跳转页',
    config_json TEXT COMMENT '渠道扩展配置JSON',
    created_by BIGINT COMMENT '创建人',
    updated_by BIGINT COMMENT '更新人',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    CONSTRAINT uk_notification_channel_config_channel UNIQUE (channel),
    INDEX idx_notification_channel_config_channel (channel),
    INDEX idx_notification_channel_config_status (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知渠道运行配置表';

CREATE TABLE IF NOT EXISTS system_notice (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '系统公告ID',
    title VARCHAR(200) NOT NULL COMMENT '标题',
    summary VARCHAR(500) COMMENT '摘要',
    content TEXT NOT NULL COMMENT '内容',
    target_roles TEXT NOT NULL COMMENT '目标角色JSON',
    channels TEXT NOT NULL COMMENT '通知渠道JSON',
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

CREATE TABLE IF NOT EXISTS sys_scheduled_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '定时任务ID',
    version BIGINT DEFAULT 0 COMMENT '乐观锁版本',
    task_code VARCHAR(80) NOT NULL COMMENT '任务编码',
    task_name VARCHAR(100) NOT NULL COMMENT '任务名称',
    task_type VARCHAR(50) NOT NULL COMMENT '任务类型',
    cron_expression VARCHAR(100) NOT NULL COMMENT 'Cron表达式',
    timezone VARCHAR(50) NOT NULL DEFAULT 'Asia/Shanghai' COMMENT '时区',
    enabled BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否启用',
    config_json TEXT COMMENT '任务参数JSON',
    lock_until DATETIME COMMENT '锁定到期时间',
    locked_by VARCHAR(100) COMMENT '锁定实例',
    last_scheduled_time DATETIME COMMENT '最近计划执行时间',
    last_run_time DATETIME COMMENT '最近执行时间',
    next_run_time DATETIME COMMENT '下次执行时间',
    last_run_status VARCHAR(20) COMMENT '最近执行状态',
    created_by BIGINT COMMENT '创建人',
    remark VARCHAR(500) COMMENT '备注',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT uk_scheduled_task_code UNIQUE (task_code),
    INDEX idx_scheduled_task_enabled (enabled),
    INDEX idx_scheduled_task_next_run (next_run_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用定时任务表';

CREATE TABLE IF NOT EXISTS sys_scheduled_task_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '任务日志ID',
    task_id BIGINT NOT NULL COMMENT '任务ID',
    task_code VARCHAR(80) NOT NULL COMMENT '任务编码',
    scheduled_time DATETIME COMMENT '计划执行时间',
    trigger_type VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED' COMMENT '触发方式',
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING' COMMENT '执行状态',
    started_time DATETIME COMMENT '开始时间',
    finished_time DATETIME COMMENT '完成时间',
    duration_ms BIGINT COMMENT '耗时毫秒',
    business_type VARCHAR(50) COMMENT '业务类型',
    business_id BIGINT COMMENT '业务ID',
    message TEXT COMMENT '执行摘要',
    error_stack TEXT COMMENT '异常堆栈',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    CONSTRAINT uk_task_scheduled_time UNIQUE (task_id, scheduled_time, trigger_type),
    INDEX idx_scheduled_task_log_task (task_id),
    INDEX idx_scheduled_task_log_status (status),
    CONSTRAINT fk_scheduled_task_log_task FOREIGN KEY (task_id) REFERENCES sys_scheduled_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用定时任务执行日志表';

-- =====================================================
-- 27. 价格发布、通知与定时任务字典
-- =====================================================

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
    UNION ALL SELECT 'notification_type', 'APPROVAL_PENDING', '审批待处理', NULL, 2, 'ACTIVE', '通知类型', NOW(), NOW()
    UNION ALL SELECT 'notification_type', 'APPROVAL_FINISHED', '审批完成', NULL, 3, 'ACTIVE', '通知类型', NOW(), NOW()
    UNION ALL SELECT 'notification_type', 'TASK_FAILED', '任务失败', NULL, 4, 'ACTIVE', '通知类型', NOW(), NOW()
    UNION ALL SELECT 'notification_type', 'API_LIMIT_WARNING', 'API告警', NULL, 5, 'ACTIVE', '通知类型', NOW(), NOW()
    UNION ALL SELECT 'notification_type', 'IMPORT_EXPORT_FINISHED', '导入导出完成', NULL, 6, 'ACTIVE', '通知类型', NOW(), NOW()
    UNION ALL SELECT 'notification_type', 'SYSTEM_NOTICE', '系统公告', NULL, 7, 'ACTIVE', '通知类型', NOW(), NOW()
    UNION ALL SELECT 'notification_channel', 'IN_APP', '站内通知', NULL, 1, 'ACTIVE', '通知渠道', NOW(), NOW()
    UNION ALL SELECT 'notification_channel', 'APP_PUSH', 'App推送', NULL, 2, 'ACTIVE', '通知渠道', NOW(), NOW()
    UNION ALL SELECT 'notification_channel', 'MINI_PROGRAM', '小程序订阅消息', NULL, 3, 'ACTIVE', '通知渠道', NOW(), NOW()
    UNION ALL SELECT 'notification_channel', 'WEBHOOK', 'Webhook', NULL, 4, 'ACTIVE', '通知渠道', NOW(), NOW()
    UNION ALL SELECT 'notification_channel', 'WECHAT_WORK', '企业微信', NULL, 5, 'ACTIVE', '通知渠道', NOW(), NOW()
    UNION ALL SELECT 'notification_mini_program_page', 'pages/notifications/index', '消息通知', NULL, 1, 'ACTIVE', '小程序通知跳转页', NOW(), NOW()
    UNION ALL SELECT 'notification_mini_program_page', 'pages/home/index', '首页', NULL, 2, 'ACTIVE', '小程序通知跳转页', NOW(), NOW()
    UNION ALL SELECT 'notification_mini_program_page', 'pages/history/index', '历史价格', NULL, 3, 'ACTIVE', '小程序通知跳转页', NOW(), NOW()
    UNION ALL SELECT 'notification_mini_program_page', 'pages/products/list', '产品列表', NULL, 4, 'ACTIVE', '小程序通知跳转页', NOW(), NOW()
    UNION ALL SELECT 'notification_mini_program_page', 'pages/profile/index', '个人中心', NULL, 5, 'ACTIVE', '小程序通知跳转页', NOW(), NOW()
    UNION ALL SELECT 'notification_read_status', 'UNREAD', '未读', '#F59E0B', 1, 'ACTIVE', '阅读状态', NOW(), NOW()
    UNION ALL SELECT 'notification_read_status', 'READ', '已读', '#10B981', 2, 'ACTIVE', '阅读状态', NOW(), NOW()
    UNION ALL SELECT 'notification_priority', 'LOW', '低', '#64748B', 1, 'ACTIVE', '通知优先级', NOW(), NOW()
    UNION ALL SELECT 'notification_priority', 'NORMAL', '普通', '#0D6E6E', 2, 'ACTIVE', '通知优先级', NOW(), NOW()
    UNION ALL SELECT 'notification_priority', 'HIGH', '高', '#F59E0B', 3, 'ACTIVE', '通知优先级', NOW(), NOW()
    UNION ALL SELECT 'notification_priority', 'URGENT', '紧急', '#EF4444', 4, 'ACTIVE', '通知优先级', NOW(), NOW()
    UNION ALL SELECT 'notification_link_type', 'PRICE_QUERY', '价格查询', NULL, 1, 'ACTIVE', '通知跳转类型', NOW(), NOW()
    UNION ALL SELECT 'notification_link_type', 'APPROVAL_DETAIL', '审批详情', NULL, 2, 'ACTIVE', '通知跳转类型', NOW(), NOW()
    UNION ALL SELECT 'notification_link_type', 'TASK_LOG', '任务日志', NULL, 3, 'ACTIVE', '通知跳转类型', NOW(), NOW()
    UNION ALL SELECT 'notification_link_type', 'SYSTEM_NOTICE', '系统通知', NULL, 4, 'ACTIVE', '通知跳转类型', NOW(), NOW()
    UNION ALL SELECT 'notification_business_type', 'PRICE', '价格', NULL, 1, 'ACTIVE', '通知业务类型', NOW(), NOW()
    UNION ALL SELECT 'notification_business_type', 'APPROVAL', '审批', NULL, 2, 'ACTIVE', '通知业务类型', NOW(), NOW()
    UNION ALL SELECT 'notification_business_type', 'TASK', '任务', NULL, 3, 'ACTIVE', '通知业务类型', NOW(), NOW()
    UNION ALL SELECT 'notification_business_type', 'SYSTEM', '系统', NULL, 4, 'ACTIVE', '通知业务类型', NOW(), NOW()
    UNION ALL SELECT 'notification_business_type', 'SECURITY', '安全', NULL, 5, 'ACTIVE', '通知业务类型', NOW(), NOW()
    UNION ALL SELECT 'notification_delivery_status', 'PENDING', '待投递', '#64748B', 1, 'ACTIVE', '投递状态', NOW(), NOW()
    UNION ALL SELECT 'notification_delivery_status', 'SUCCESS', '成功', '#10B981', 2, 'ACTIVE', '投递状态', NOW(), NOW()
    UNION ALL SELECT 'notification_delivery_status', 'FAILED', '失败', '#EF4444', 3, 'ACTIVE', '投递状态', NOW(), NOW()
    UNION ALL SELECT 'notification_delivery_status', 'SKIPPED', '已跳过', '#9CA3AF', 4, 'ACTIVE', '投递状态', NOW(), NOW()
    UNION ALL SELECT 'notification_outbox_status', 'PENDING', '待处理', '#64748B', 1, 'ACTIVE', 'Outbox状态', NOW(), NOW()
    UNION ALL SELECT 'notification_outbox_status', 'PROCESSING', '处理中', '#3B82F6', 2, 'ACTIVE', 'Outbox状态', NOW(), NOW()
    UNION ALL SELECT 'notification_outbox_status', 'SUCCESS', '成功', '#10B981', 3, 'ACTIVE', 'Outbox状态', NOW(), NOW()
    UNION ALL SELECT 'notification_outbox_status', 'FAILED', '失败', '#EF4444', 4, 'ACTIVE', 'Outbox状态', NOW(), NOW()
    UNION ALL SELECT 'notification_provider_health_status', 'OK', '正常', '#10B981', 1, 'ACTIVE', 'Provider健康状态', NOW(), NOW()
    UNION ALL SELECT 'notification_provider_health_status', 'DEGRADED', '降级', '#F59E0B', 2, 'ACTIVE', 'Provider健康状态', NOW(), NOW()
    UNION ALL SELECT 'notification_provider_health_status', 'DOWN', '异常', '#EF4444', 3, 'ACTIVE', 'Provider健康状态', NOW(), NOW()
    UNION ALL SELECT 'notification_provider_health_status', 'NOT_CONFIGURED', '未配置', '#9CA3AF', 4, 'ACTIVE', 'Provider健康状态', NOW(), NOW()
    UNION ALL SELECT 'notification_mini_subscription_status', 'UNKNOWN', '未知', '#9CA3AF', 1, 'ACTIVE', '小程序订阅授权状态', NOW(), NOW()
    UNION ALL SELECT 'notification_mini_subscription_status', 'ACCEPT', '已授权', '#10B981', 2, 'ACTIVE', '小程序订阅授权状态', NOW(), NOW()
    UNION ALL SELECT 'notification_mini_subscription_status', 'REJECT', '已拒绝', '#EF4444', 3, 'ACTIVE', '小程序订阅授权状态', NOW(), NOW()
    UNION ALL SELECT 'notification_mini_subscription_status', 'BAN', '已禁用', '#64748B', 4, 'ACTIVE', '小程序订阅授权状态', NOW(), NOW()
    UNION ALL SELECT 'notification_mini_subscription_row_status', 'NORMAL', '正常', '#10B981', 1, 'ACTIVE', '小程序订阅用户行状态', NOW(), NOW()
    UNION ALL SELECT 'notification_mini_subscription_row_status', 'LOW_BALANCE', '低余量', '#F59E0B', 2, 'ACTIVE', '小程序订阅用户行状态', NOW(), NOW()
    UNION ALL SELECT 'notification_mini_subscription_row_status', 'UNBOUND', '未绑定', '#9CA3AF', 3, 'ACTIVE', '小程序订阅用户行状态', NOW(), NOW()
    UNION ALL SELECT 'notification_mini_subscription_row_status', 'REJECTED', '拒绝/禁用', '#EF4444', 4, 'ACTIVE', '小程序订阅用户行状态', NOW(), NOW()
    UNION ALL SELECT 'notification_mini_resolution_status', 'OPEN', '待处理', '#F59E0B', 1, 'ACTIVE', '小程序订阅异常处理状态', NOW(), NOW()
    UNION ALL SELECT 'notification_mini_resolution_status', 'RESOLVED', '已处理', '#10B981', 2, 'ACTIVE', '小程序订阅异常处理状态', NOW(), NOW()
    UNION ALL SELECT 'notification_mini_resolution_status', 'SNOOZED', '暂不提醒', '#64748B', 3, 'ACTIVE', '小程序订阅异常处理状态', NOW(), NOW()
    UNION ALL SELECT 'notification_mini_resolution_status', 'FOLLOW_UP', '跟进标记', '#3B82F6', 4, 'ACTIVE', '小程序订阅异常处理状态', NOW(), NOW()
    UNION ALL SELECT 'notification_frequency_rule', 'TASK_FAILED', '任务失败聚合频控', '{"enabled":true,"windowMinutes":30,"maxCount":5}', 1, 'ACTIVE', '任务失败消息在时间窗内超过阈值后聚合', NOW(), NOW()
    UNION ALL SELECT 'notification_frequency_rule', 'API_LIMIT_WARNING', 'API告警聚合频控', '{"enabled":true,"windowMinutes":30,"maxCount":5}', 2, 'ACTIVE', 'API告警消息在时间窗内超过阈值后聚合', NOW(), NOW()
    UNION ALL SELECT 'notification_frequency_rule', 'IMPORT_EXPORT_FINISHED', '导入导出完成聚合频控', '{"enabled":true,"windowMinutes":60,"maxCount":10}', 3, 'ACTIVE', '导入导出完成消息在时间窗内超过阈值后聚合', NOW(), NOW()
    UNION ALL SELECT 'system_notice_status', 'DRAFT', '草稿', '#64748B', 1, 'ACTIVE', '系统公告状态', NOW(), NOW()
    UNION ALL SELECT 'system_notice_status', 'SCHEDULED', '待发布', '#3B82F6', 2, 'ACTIVE', '系统公告状态', NOW(), NOW()
    UNION ALL SELECT 'system_notice_status', 'PUBLISHED', '已发布', '#10B981', 3, 'ACTIVE', '系统公告状态', NOW(), NOW()
    UNION ALL SELECT 'system_notice_status', 'CANCELLED', '已撤回', '#9CA3AF', 4, 'ACTIVE', '系统公告状态', NOW(), NOW()
    UNION ALL SELECT 'system_notice_status', 'EXPIRED', '已过期', '#F59E0B', 5, 'ACTIVE', '系统公告状态', NOW(), NOW()
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

-- =====================================================
-- 初始化完成提示（更新）
-- =====================================================

SELECT '========================================' AS '';
SELECT '  数据初始化完成！' AS message;
SELECT '========================================' AS '';
SELECT '' AS '';
SELECT '默认用户:' AS '';
SELECT '  admin   / admin123   (管理员)' AS '';
SELECT '  editor  / admin123   (编辑者)' AS '';
SELECT '  viewer  / admin123   (查看者)' AS '';
SELECT '' AS '';
SELECT '样式系统:' AS '';
SELECT '  - 色彩方案: 6 套（青绿经典为默认）' AS '';
SELECT '  - 布局方案: 4 套（经典顶部导航为默认）' AS '';
SELECT '  - 字号预设: 4 套（标准为默认）' AS '';
SELECT '' AS '';
SELECT '价格发布能力:' AS '';
SELECT '  - 价格草稿保存/发布表: 已创建' AS '';
SELECT '  - 通知消息与投递日志表: 已创建' AS '';
SELECT '  - 通用定时任务: 已创建（价格自动发布默认停用）' AS '';
