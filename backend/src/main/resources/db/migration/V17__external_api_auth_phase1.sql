-- Phase 1 external API authorization isolation.

CREATE TABLE IF NOT EXISTS sys_api_key (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    app_id VARCHAR(64) NOT NULL,
    app_secret_cipher TEXT NOT NULL,
    app_secret_key_version VARCHAR(20) NOT NULL DEFAULT 'v1',
    app_secret_fingerprint VARCHAR(64) NOT NULL,
    description VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    environment VARCHAR(20) NOT NULL DEFAULT 'TESTING',
    expire_time DATETIME,
    ip_whitelist TEXT,
    rate_limit_per_minute INT NOT NULL DEFAULT 60,
    daily_limit INT NOT NULL DEFAULT 10000,
    created_by BIGINT,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_used_time DATETIME,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_api_key_app_id (app_id),
    INDEX idx_api_key_status (status),
    INDEX idx_api_key_environment (environment),
    INDEX idx_api_key_expire_time (expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_api_key_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    api_key_id BIGINT NOT NULL,
    permission_code VARCHAR(100) NOT NULL,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE KEY uk_api_key_permission (api_key_id, permission_code),
    INDEX idx_api_key_permission_key (api_key_id),
    CONSTRAINT fk_api_key_permission_key
        FOREIGN KEY (api_key_id) REFERENCES sys_api_key(id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_external_api_endpoint (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    permission_code VARCHAR(100) NOT NULL,
    method VARCHAR(10) NOT NULL,
    path_pattern VARCHAR(200) NOT NULL,
    description VARCHAR(200),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    sort_order INT NOT NULL DEFAULT 0,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_external_endpoint (method, path_pattern),
    INDEX idx_external_endpoint_permission (permission_code),
    INDEX idx_external_endpoint_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_api_call_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    api_key_id BIGINT,
    app_id VARCHAR(64),
    endpoint VARCHAR(200) NOT NULL,
    query_string VARCHAR(1000),
    method VARCHAR(10) NOT NULL,
    permission_code VARCHAR(100),
    status_code INT,
    response_time INT,
    ip_address VARCHAR(50),
    request_time DATETIME NOT NULL,
    request_body_hash VARCHAR(64),
    nonce VARCHAR(64),
    auth_result VARCHAR(30) NOT NULL,
    error_message VARCHAR(500),
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    INDEX idx_api_call_api_key_time (api_key_id, request_time),
    INDEX idx_api_call_app_time (app_id, request_time),
    INDEX idx_api_call_status (status_code),
    INDEX idx_api_call_auth_result (auth_result),
    INDEX idx_api_call_created_time (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_api_key_operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    api_key_id BIGINT NOT NULL,
    operation VARCHAR(50) NOT NULL,
    operator_id BIGINT,
    operator_ip VARCHAR(50),
    detail TEXT,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    INDEX idx_api_key_operation_key (api_key_id),
    INDEX idx_api_key_operation_time (created_time),
    CONSTRAINT fk_api_key_operation_key
        FOREIGN KEY (api_key_id) REFERENCES sys_api_key(id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_dict (category, dict_key, dict_value, sort_order, status, remark, created_time, updated_time)
SELECT category, dict_key, dict_value, sort_order, 'ACTIVE', remark, NOW(), NOW()
FROM (
    SELECT 'api_key_status' category, 'ACTIVE' dict_key, '生效中' dict_value, 1 sort_order, 'API Key状态' remark
    UNION ALL SELECT 'api_key_status', 'DISABLED', '已停用', 2, 'API Key状态'
    UNION ALL SELECT 'api_key_status', 'EXPIRED', '已过期', 3, 'API Key状态'
    UNION ALL SELECT 'api_key_status', 'REVOKED', '已吊销', 4, 'API Key状态'
    UNION ALL SELECT 'api_key_environment', 'PRODUCTION', '生产', 1, 'API Key环境'
    UNION ALL SELECT 'api_key_environment', 'TESTING', '测试', 2, 'API Key环境'
    UNION ALL SELECT 'api_key_environment', 'SANDBOX', '沙箱', 3, 'API Key环境'
    UNION ALL SELECT 'api_key_operation', 'CREATE', '创建', 1, 'API Key操作'
    UNION ALL SELECT 'api_key_operation', 'UPDATE', '更新', 2, 'API Key操作'
    UNION ALL SELECT 'api_key_operation', 'ENABLE', '启用', 3, 'API Key操作'
    UNION ALL SELECT 'api_key_operation', 'DISABLE', '停用', 4, 'API Key操作'
    UNION ALL SELECT 'api_key_operation', 'REVOKE', '吊销', 5, 'API Key操作'
    UNION ALL SELECT 'api_auth_result', 'SUCCESS', '成功', 1, '外部API认证结果'
    UNION ALL SELECT 'api_auth_result', 'MISSING_HEADER', '缺少认证头', 2, '外部API认证结果'
    UNION ALL SELECT 'api_auth_result', 'INVALID_APP_ID', '应用不存在', 3, '外部API认证结果'
    UNION ALL SELECT 'api_auth_result', 'INVALID_SIGNATURE', '签名错误', 4, '外部API认证结果'
    UNION ALL SELECT 'api_auth_result', 'EXPIRED_TIMESTAMP', '时间戳过期', 5, '外部API认证结果'
    UNION ALL SELECT 'api_auth_result', 'REPLAY', '重放请求', 6, '外部API认证结果'
    UNION ALL SELECT 'api_auth_result', 'IP_DENIED', 'IP受限', 7, '外部API认证结果'
    UNION ALL SELECT 'api_auth_result', 'RATE_LIMITED', '触发限流', 8, '外部API认证结果'
    UNION ALL SELECT 'api_auth_result', 'FORBIDDEN', '权限不足', 9, '外部API认证结果'
    UNION ALL SELECT 'api_auth_result', 'DISABLED', '功能未启用', 10, '外部API认证结果'
    UNION ALL SELECT 'api_auth_result', 'UNSUPPORTED_CONTENT_TYPE', '不支持的内容类型', 11, '外部API认证结果'
    UNION ALL SELECT 'api_permission', 'product:read', '产品读取', 1, '外部API权限'
    UNION ALL SELECT 'api_permission', 'product:write', '产品写入', 2, '外部API权限'
    UNION ALL SELECT 'api_permission', 'product:delete', '产品删除', 3, '外部API权限'
    UNION ALL SELECT 'api_permission', 'price:read', '价格读取', 4, '外部API权限'
    UNION ALL SELECT 'api_permission', 'price:write', '价格写入', 5, '外部API权限'
    UNION ALL SELECT 'api_permission', 'price-query:read', '价格查询', 6, '外部API权限'
    UNION ALL SELECT 'api_permission', 'price-query:export', '价格导出', 7, '外部API权限'
    UNION ALL SELECT 'api_permission', 'category:read', '分类读取', 8, '外部API权限'
    UNION ALL SELECT 'api_permission', 'origin:read', '产地读取', 9, '外部API权限'
    UNION ALL SELECT 'api_permission', 'customer:read', '客户读取', 10, '外部API权限'
    UNION ALL SELECT 'api_permission', 'dict:read', '字典读取', 11, '外部API权限'
    UNION ALL SELECT 'api_permission', 'home:read', '首页读取', 12, '外部API权限'
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict d
    WHERE d.category = seed.category AND d.dict_key = seed.dict_key
);

INSERT INTO sys_external_api_endpoint (permission_code, method, path_pattern, description, status, sort_order, created_time, updated_time)
SELECT permission_code, method, path_pattern, description, 'ACTIVE', sort_order, NOW(), NOW()
FROM (
    SELECT 'product:read' permission_code, 'GET' method, '/api/external/v1/products' path_pattern, '产品列表' description, 10 sort_order
    UNION ALL SELECT 'product:read', 'GET', '/api/external/v1/products/*', '产品详情', 11
    UNION ALL SELECT 'product:write', 'POST', '/api/external/v1/products', '新增产品', 20
    UNION ALL SELECT 'product:write', 'PUT', '/api/external/v1/products/*', '编辑产品', 21
    UNION ALL SELECT 'product:delete', 'DELETE', '/api/external/v1/products/*', '删除产品', 30
    UNION ALL SELECT 'price:read', 'GET', '/api/external/v1/products/*/price-history', '价格历史', 40
    UNION ALL SELECT 'price:read', 'GET', '/api/external/v1/products/*/current-price', '当前价格', 41
    UNION ALL SELECT 'price:read', 'GET', '/api/external/v1/products/*/price-by-date', '指定日期价格', 42
    UNION ALL SELECT 'price:read', 'GET', '/api/external/v1/products/*/price-trend', '价格走势', 43
    UNION ALL SELECT 'price:read', 'GET', '/api/external/v1/prices/by-date', '按日期价格', 44
    UNION ALL SELECT 'price:read', 'GET', '/api/external/v1/prices/by-date-with-stats', '按日期价格统计', 45
    UNION ALL SELECT 'price:write', 'POST', '/api/external/v1/products/*/prices', '新增产品价格', 50
    UNION ALL SELECT 'price:write', 'PUT', '/api/external/v1/prices/*', '编辑价格', 51
    UNION ALL SELECT 'price-query:export', 'GET', '/api/external/v1/price-query/export', '价格查询导出', 60
    UNION ALL SELECT 'price-query:read', 'GET', '/api/external/v1/price-query', '价格查询', 61
    UNION ALL SELECT 'category:read', 'GET', '/api/external/v1/categories', '分类列表', 70
    UNION ALL SELECT 'category:read', 'GET', '/api/external/v1/categories/*', '分类详情', 71
    UNION ALL SELECT 'origin:read', 'GET', '/api/external/v1/origins', '产地列表', 80
    UNION ALL SELECT 'origin:read', 'GET', '/api/external/v1/origins/*', '产地详情', 81
    UNION ALL SELECT 'customer:read', 'GET', '/api/external/v1/customers', '客户列表', 90
    UNION ALL SELECT 'customer:read', 'GET', '/api/external/v1/customers/*', '客户详情', 91
    UNION ALL SELECT 'dict:read', 'GET', '/api/external/v1/dict', '字典列表', 100
    UNION ALL SELECT 'dict:read', 'GET', '/api/external/v1/dict/**', '字典读取', 101
    UNION ALL SELECT 'home:read', 'GET', '/api/external/v1/home/**', '首页数据', 110
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM sys_external_api_endpoint e
    WHERE e.method = seed.method AND e.path_pattern = seed.path_pattern
);

SET @system_menu_id := (
    SELECT id FROM menu_item
    WHERE parent_id IS NULL AND name = '系统管理'
    ORDER BY id
    LIMIT 1
);

INSERT INTO menu_item (parent_id, name, path, icon, sort_order, visible, roles, created_time, updated_time)
SELECT @system_menu_id, 'API授权管理', NULL, 'key', 7, TRUE, '["ADMIN"]', NOW(), NOW()
WHERE @system_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM menu_item WHERE name = 'API授权管理' AND parent_id = @system_menu_id);

SET @api_auth_menu_id := (
    SELECT id FROM menu_item
    WHERE parent_id = @system_menu_id AND name = 'API授权管理'
    ORDER BY id
    LIMIT 1
);

INSERT INTO menu_item (parent_id, name, path, icon, sort_order, visible, roles, created_time, updated_time)
SELECT @api_auth_menu_id, '密钥管理', '/api-keys', 'key', 1, TRUE, '["ADMIN"]', NOW(), NOW()
WHERE @api_auth_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM menu_item WHERE path = '/api-keys');

INSERT INTO menu_item (parent_id, name, path, icon, sort_order, visible, roles, created_time, updated_time)
SELECT @api_auth_menu_id, '调用日志', '/api-call-logs', 'log', 2, TRUE, '["ADMIN"]', NOW(), NOW()
WHERE @api_auth_menu_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM menu_item WHERE path = '/api-call-logs');
