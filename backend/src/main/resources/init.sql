-- =====================================================
-- 价格管理系统 - 数据初始化脚本
-- 数据库: price_management
-- 说明: 包含表结构创建和初始数据插入
-- =====================================================

USE price_management;

-- =====================================================
-- 1. 创建表结构
-- =====================================================

-- 1.1 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password VARCHAR(200) NOT NULL COMMENT '密码（BCrypt加密）',
    role VARCHAR(20) NOT NULL COMMENT '角色：ADMIN/EDITOR/VIEWER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/INACTIVE',
    nickname VARCHAR(50) COMMENT '昵称',
    email VARCHAR(100) COMMENT '邮箱',
    phone VARCHAR(20) COMMENT '电话',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_username (username),
    INDEX idx_user_status (status),
    INDEX idx_user_role (role)
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
    code VARCHAR(100) NOT NULL UNIQUE COMMENT '产品编码',
    category_id BIGINT COMMENT '分类ID',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/INACTIVE',
    description TEXT COMMENT '产品描述',
    specs TEXT COMMENT '规格参数',
    image_url VARCHAR(500) COMMENT '图片URL',
    origin_ids VARCHAR(500) COMMENT '产地ID列表(JSON数组)',
    customer_ids VARCHAR(500) COMMENT '客户ID列表(JSON数组)',
    remark TEXT COMMENT '备注',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (category_id) REFERENCES product_category(id) ON DELETE SET NULL ON UPDATE CASCADE,
    INDEX idx_product_code (code),
    INDEX idx_product_category (category_id),
    INDEX idx_product_status (status),
    INDEX idx_product_name (name)
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
    effective_date DATE COMMENT '生效日期',
    expiry_date DATE COMMENT '失效日期',
    unit VARCHAR(50) COMMENT '单位：元/吨、元/克等',
    price_spec VARCHAR(200) COMMENT '价格规格',
    created_by BIGINT COMMENT '创建人',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_price_product (product_id),
    INDEX idx_price_effective (effective_date, expiry_date),
    INDEX idx_price_created (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='价格表';

-- 1.5 价格历史表
CREATE TABLE IF NOT EXISTS price_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '历史记录ID',
    price_id BIGINT COMMENT '价格ID',
    product_id BIGINT NOT NULL COMMENT '产品ID',
    old_price DECIMAL(15, 4) COMMENT '旧价格',
    new_price DECIMAL(15, 4) NOT NULL COMMENT '新价格',
    change_type VARCHAR(20) NOT NULL COMMENT '变动类型：CREATE/UPDATE/DELETE',
    remark TEXT COMMENT '备注',
    changed_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '变动时间',
    FOREIGN KEY (price_id) REFERENCES price(id) ON DELETE SET NULL ON UPDATE CASCADE,
    FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_history_product (product_id),
    INDEX idx_history_price (price_id),
    INDEX idx_history_time (changed_time),
    INDEX idx_history_type (change_type)
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

INSERT INTO product (name, code, category_id, status, specs, remark, created_time, updated_time)
SELECT * FROM (
    SELECT '硫精砂' AS name, 'SULFUR_CONCENTRATE' AS code, 4 AS category_id, 'ACTIVE' AS status, '出厂承兑，As < 0.1%' AS specs, '硫精砂产品' AS remark, NOW() AS created_time, NOW() AS updated_time
    UNION ALL SELECT '硫酸', 'SULFURIC_ACID', 4, 'ACTIVE', '93% 出厂承兑', '硫酸产品', NOW(), NOW()
    UNION ALL SELECT '钼精矿', 'MOLYBDENUM_CONCENTRATE', 2, 'ACTIVE', '45-50（元/吨度）；50以上（元/吨度）', '钼精矿产品', NOW(), NOW()
    UNION ALL SELECT '电铜', 'ELECTRIC_COPPER', 2, 'ACTIVE', 'A级（元/吨）', '电铜产品', NOW(), NOW()
    UNION ALL SELECT '金', 'GOLD', 3, 'ACTIVE', '2#（元/克）', '金产品', NOW(), NOW()
    UNION ALL SELECT '银', 'SILVER', 3, 'ACTIVE', '3#（元/千克）', '银产品', NOW(), NOW()
    UNION ALL SELECT '钢坯', 'STEEL_BILLET', 1, 'ACTIVE', '方坯 Q235', '钢坯产品', NOW(), NOW()
    UNION ALL SELECT '废钢（河北纵横）', 'SCRAP_STEEL', 1, 'ACTIVE', '厚6mm，<18000mm，宽<1200mm；常规重废', '废钢产品', NOW(), NOW()
    UNION ALL SELECT '五氧化二钒', 'VANADIUM_PENTOXIDE', 2, 'ACTIVE', '98% 片状，承兑（万元/吨）', '五氧化二钒产品', NOW(), NOW()
    UNION ALL SELECT '镁锭', 'MAGNESIUM_INGOT', 2, 'ACTIVE', '99990（闻喜）元/吨', '镁锭产品', NOW(), NOW()
    UNION ALL SELECT '铅锭', 'LEAD_INGOT', 2, 'ACTIVE', '1#（元/吨）', '铅锭产品', NOW(), NOW()
    UNION ALL SELECT '锌锭', 'ZINC_INGOT', 2, 'ACTIVE', '0#（元/吨）', '锌锭产品', NOW(), NOW()
    UNION ALL SELECT '钯金', 'PALLADIUM', 3, 'ACTIVE', '99.95（元/克）', '钯金产品', NOW(), NOW()
    UNION ALL SELECT '铂金', 'PLATINUM', 3, 'ACTIVE', '99.95（元/克）', '铂金产品', NOW(), NOW()
    UNION ALL SELECT '硫酸钴', 'COBALT_SULFATE', 4, 'ACTIVE', '≥20.5% 国产（万元/吨）', '硫酸钴产品', NOW(), NOW()
    UNION ALL SELECT '碳酸锂', 'LITHIUM_CARBONATE', 4, 'ACTIVE', '电池级 99.5% 国产（万元/吨）', '碳酸锂产品', NOW(), NOW()
    UNION ALL SELECT '钛精矿', 'TITANIUM_CONCENTRATE', 2, 'ACTIVE', '48% 不含税（元/吨）', '钛精矿产品', NOW(), NOW()
    UNION ALL SELECT '无烟煤（一级冶金焦）', 'ANTHRACITE', 5, 'ACTIVE', 'C>85%，A<12.5%，S<0.7%，V<1.9%，HGI>50，M25<7%，M10>65%', '无烟煤产品', NOW(), NOW()
    UNION ALL SELECT '萤石湿粉', 'FLUORITE_POWDER', 4, 'ACTIVE', '97%', '萤石湿粉产品', NOW(), NOW()
    UNION ALL SELECT '铁精粉', 'IRON_CONCENTRATE', 1, 'ACTIVE', '66%', '铁精粉产品', NOW(), NOW()
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
    UNION ALL SELECT 20, 3, '产品维护', '/product-edit', NULL, 1, TRUE, '["ADMIN","EDITOR"]', NOW(), NOW()
    UNION ALL SELECT 21, 3, '分类管理', '/categories', NULL, 2, TRUE, '["ADMIN","EDITOR"]', NOW(), NOW()
    UNION ALL SELECT 22, 3, '导入导出', '/import', NULL, 3, TRUE, '["ADMIN","EDITOR"]', NOW(), NOW()
    UNION ALL SELECT 23, 3, '审批管理', '/approval', 'check-circle', 4, TRUE, '["ADMIN","EDITOR"]', NOW(), NOW()
    UNION ALL SELECT 24, 3, '字典管理', NULL, 'dict', 5, TRUE, '["ADMIN","EDITOR"]', NOW(), NOW()
    UNION ALL SELECT 30, 4, '用户管理', '/users', 'users', 1, TRUE, '["ADMIN"]', NOW(), NOW()
    UNION ALL SELECT 31, 4, '菜单配置', '/menu-config', 'menu', 2, TRUE, '["ADMIN"]', NOW(), NOW()
    UNION ALL SELECT 32, 4, '日志管理', '/operation-log', 'log', 3, TRUE, '["ADMIN"]', NOW(), NOW()
    UNION ALL SELECT 33, 4, '审批流配置', '/approval-config', 'workflow', 4, TRUE, '["ADMIN"]', NOW(), NOW()
    UNION ALL SELECT 40, 24, '产地管理', '/origins', NULL, 1, TRUE, '["ADMIN","EDITOR"]', NOW(), NOW()
    UNION ALL SELECT 41, 24, '客户管理', '/customers', NULL, 2, TRUE, '["ADMIN","EDITOR"]', NOW(), NOW()
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
