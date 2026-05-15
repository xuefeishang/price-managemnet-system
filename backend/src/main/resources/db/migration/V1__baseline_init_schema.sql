-- =====================================================
-- V1: 基线 - 标记已有数据库的初始状态
-- 此脚本仅在首次运行 Flyway 时创建基线标记
-- 已有数据库通过 baseline-on-migrate: true 自动跳过
-- =====================================================

-- 基线迁移：对应 init.sql 中的完整表结构
-- Flyway 在已有数据库上会自动标记 V1 为已执行（baseline）
-- 新数据库需要执行此脚本创建所有表

-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(200) NOT NULL,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    nickname VARCHAR(50),
    email VARCHAR(100),
    phone VARCHAR(20),
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_username (username),
    INDEX idx_user_status (status),
    INDEX idx_user_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 产品分类表
CREATE TABLE IF NOT EXISTS product_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) UNIQUE,
    sort_order INT DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    remark TEXT,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_category_code (code),
    INDEX idx_category_status (status),
    INDEX idx_category_sort (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 产品表
CREATE TABLE IF NOT EXISTS product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    code VARCHAR(100),
    selling_price DECIMAL(15, 4),
    budget_price DECIMAL(15, 4),
    category_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    description TEXT,
    specs TEXT,
    image_url VARCHAR(500),
    origin_ids VARCHAR(500),
    customer_ids VARCHAR(500),
    remark TEXT,
    unit VARCHAR(50),
    sort_order INT DEFAULT 0,
    show_on_home BOOLEAN NOT NULL DEFAULT FALSE,
    currency VARCHAR(20) DEFAULT 'CNY',
    version BIGINT NOT NULL DEFAULT 0,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (category_id) REFERENCES product_category(id) ON DELETE SET NULL ON UPDATE CASCADE,
    INDEX idx_product_category (category_id),
    INDEX idx_product_status (status),
    INDEX idx_product_name (name),
    INDEX idx_product_code (code),
    INDEX idx_product_sort (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 产地表
CREATE TABLE IF NOT EXISTS origin (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) UNIQUE NOT NULL,
    sort_order INT DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    remark TEXT,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_origin_code (code),
    INDEX idx_origin_status (status),
    INDEX idx_origin_sort (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 客户表
CREATE TABLE IF NOT EXISTS customer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) UNIQUE NOT NULL,
    contact VARCHAR(100),
    phone VARCHAR(20),
    address VARCHAR(500),
    sort_order INT DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    remark TEXT,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_customer_code (code),
    INDEX idx_customer_status (status),
    INDEX idx_customer_sort (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 价格表
CREATE TABLE IF NOT EXISTS price (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    original_price DECIMAL(15, 4),
    current_price DECIMAL(15, 4) NOT NULL,
    cost_price DECIMAL(15, 4),
    budget_price DECIMAL(15, 4),
    effective_date DATE,
    expiry_date DATE,
    unit VARCHAR(50),
    price_spec VARCHAR(200),
    created_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_price_product (product_id),
    INDEX idx_price_effective (effective_date, expiry_date),
    INDEX idx_price_created (created_time),
    UNIQUE KEY uk_product_effective_date (product_id, effective_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 价格历史表
CREATE TABLE IF NOT EXISTS price_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    price_id BIGINT,
    product_id BIGINT NOT NULL,
    old_price DECIMAL(15, 4),
    new_price DECIMAL(15, 4) NOT NULL,
    change_type VARCHAR(20) NOT NULL,
    changed_by BIGINT,
    remark TEXT,
    changed_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    FOREIGN KEY (price_id) REFERENCES price(id) ON DELETE SET NULL ON UPDATE CASCADE,
    FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_history_product (product_id),
    INDEX idx_history_price (price_id),
    INDEX idx_history_time (changed_time),
    INDEX idx_history_type (change_type),
    INDEX idx_history_changed_by (changed_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 同步日志表
CREATE TABLE IF NOT EXISTS sync_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sync_type VARCHAR(50) NOT NULL,
    sync_source VARCHAR(100),
    sync_status VARCHAR(20) NOT NULL,
    sync_count INT DEFAULT 0,
    success_count INT DEFAULT 0,
    failed_count INT DEFAULT 0,
    error_message TEXT,
    started_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    completed_time DATETIME,
    INDEX idx_sync_type (sync_type),
    INDEX idx_sync_status (sync_status),
    INDEX idx_sync_time (started_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 操作日志表
CREATE TABLE IF NOT EXISTS operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    username VARCHAR(100),
    operation_type VARCHAR(50),
    operation_module VARCHAR(100),
    operation_desc VARCHAR(500),
    request_method VARCHAR(10),
    request_url VARCHAR(500),
    request_params TEXT,
    response_code VARCHAR(10),
    response_data TEXT,
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    operation_time DATETIME NOT NULL,
    execution_time BIGINT,
    error_message TEXT,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    INDEX idx_operation_user (user_id),
    INDEX idx_operation_type (operation_type),
    INDEX idx_operation_time (operation_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 菜单项表
CREATE TABLE IF NOT EXISTS menu_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id BIGINT,
    name VARCHAR(100) NOT NULL,
    path VARCHAR(200),
    icon VARCHAR(50),
    sort_order INT DEFAULT 0,
    visible BOOLEAN NOT NULL DEFAULT TRUE,
    roles VARCHAR(500),
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_menu_parent (parent_id),
    INDEX idx_menu_sort (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 数据字典表
CREATE TABLE IF NOT EXISTS sys_dict (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category VARCHAR(50) NOT NULL,
    dict_key VARCHAR(100) NOT NULL,
    dict_value VARCHAR(200) NOT NULL,
    extra_value VARCHAR(500),
    sort_order INT DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    remark TEXT,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_category_key (category, dict_key),
    INDEX idx_dict_category (category),
    INDEX idx_dict_status (status),
    INDEX idx_dict_sort (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 审批流程定义表
CREATE TABLE IF NOT EXISTS approval_workflow (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_name VARCHAR(100) NOT NULL,
    workflow_type VARCHAR(50) NOT NULL,
    approval_level INT NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_workflow_type (workflow_type),
    INDEX idx_workflow_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 审批节点表
CREATE TABLE IF NOT EXISTS approval_node (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id BIGINT NOT NULL,
    node_order INT NOT NULL,
    node_type VARCHAR(20) NOT NULL,
    approver_role VARCHAR(20),
    is_required BOOLEAN NOT NULL DEFAULT TRUE,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    FOREIGN KEY (workflow_id) REFERENCES approval_workflow(id) ON DELETE CASCADE ON UPDATE CASCADE,
    INDEX idx_node_workflow (workflow_id),
    INDEX idx_node_order (node_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 审批请求表
CREATE TABLE IF NOT EXISTS approval_request (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workflow_id BIGINT NOT NULL,
    business_type VARCHAR(50) NOT NULL,
    business_id BIGINT NOT NULL,
    current_node_id BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    applicant_id BIGINT NOT NULL,
    request_data TEXT,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (workflow_id) REFERENCES approval_workflow(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    FOREIGN KEY (current_node_id) REFERENCES approval_node(id) ON DELETE SET NULL ON UPDATE CASCADE,
    INDEX idx_request_business (business_type, business_id),
    INDEX idx_request_status (status),
    INDEX idx_request_applicant (applicant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 审批记录表
CREATE TABLE IF NOT EXISTS approval_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    approver_id BIGINT,
    action VARCHAR(20) NOT NULL,
    comment TEXT,
    old_value TEXT,
    new_value TEXT,
    action_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    FOREIGN KEY (request_id) REFERENCES approval_request(id) ON DELETE CASCADE ON UPDATE CASCADE,
    FOREIGN KEY (node_id) REFERENCES approval_node(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX idx_record_request (request_id),
    INDEX idx_record_approver (approver_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
