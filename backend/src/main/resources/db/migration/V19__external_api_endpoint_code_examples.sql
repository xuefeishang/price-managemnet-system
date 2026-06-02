-- Add structured examples and lightweight schemas for copyable external API code snippets.

SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_external_api_endpoint'
      AND column_name = 'query_example'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE sys_external_api_endpoint ADD COLUMN query_example TEXT AFTER usage_notes',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_external_api_endpoint'
      AND column_name = 'body_example'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE sys_external_api_endpoint ADD COLUMN body_example TEXT AFTER query_example',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_external_api_endpoint'
      AND column_name = 'path_params_example'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE sys_external_api_endpoint ADD COLUMN path_params_example TEXT AFTER body_example',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_external_api_endpoint'
      AND column_name = 'query_schema'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE sys_external_api_endpoint ADD COLUMN query_schema TEXT AFTER path_params_example',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_external_api_endpoint'
      AND column_name = 'body_schema'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE sys_external_api_endpoint ADD COLUMN body_schema TEXT AFTER query_schema',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_external_api_endpoint'
      AND column_name = 'path_params_schema'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE sys_external_api_endpoint ADD COLUMN path_params_schema TEXT AFTER body_schema',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_external_api_endpoint'
      AND column_name = 'success_example'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE sys_external_api_endpoint ADD COLUMN success_example TEXT AFTER path_params_schema',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_external_api_endpoint'
      AND column_name = 'failure_example'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE sys_external_api_endpoint ADD COLUMN failure_example TEXT AFTER success_example',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_external_api_endpoint'
      AND column_name = 'code_notes'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE sys_external_api_endpoint ADD COLUMN code_notes TEXT AFTER failure_example',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @page_query_example := '{"page":0,"size":20}';
SET @page_query_schema := '[{"name":"page","type":"number","required":false,"defaultValue":0,"description":"页码，从 0 开始"},{"name":"size","type":"number","required":false,"defaultValue":20,"description":"每页条数"}]';
SET @id_path_example := '{"id":1}';
SET @id_path_schema := '[{"name":"id","type":"number","required":true,"description":"资源 ID"}]';
SET @product_path_example := '{"productId":1}';
SET @product_path_schema := '[{"name":"productId","type":"number","required":true,"description":"产品 ID"}]';
SET @empty_schema := '[]';

UPDATE sys_external_api_endpoint
SET
    query_example = CASE
        WHEN path_pattern IN ('/api/external/v1/products', '/api/external/v1/categories', '/api/external/v1/origins', '/api/external/v1/customers') THEN @page_query_example
        WHEN path_pattern = '/api/external/v1/price-query' THEN '{"page":0,"size":20,"keyword":"","date":"2026-06-01"}'
        WHEN path_pattern = '/api/external/v1/price-query/export' THEN '{"date":"2026-06-01"}'
        WHEN path_pattern LIKE '%price-history' THEN '{"page":0,"size":20}'
        WHEN path_pattern LIKE '%price-by-date' THEN '{"date":"2026-06-01"}'
        WHEN path_pattern LIKE '%price-trend' THEN '{"startDate":"2026-05-01","endDate":"2026-06-01"}'
        WHEN path_pattern IN ('/api/external/v1/prices/by-date', '/api/external/v1/prices/by-date-with-stats') THEN '{"date":"2026-06-01"}'
        WHEN path_pattern = '/api/external/v1/dict' THEN '{"category":"api_permission"}'
        ELSE '{}'
    END,
    body_example = CASE
        WHEN method = 'POST' AND path_pattern = '/api/external/v1/products' THEN '{"name":"示例产品","categoryId":1,"status":"ACTIVE","unit":"吨","remark":"外部 API 创建"}'
        WHEN method = 'PUT' AND path_pattern = '/api/external/v1/products/*' THEN '{"name":"更新后的示例产品","categoryId":1,"status":"ACTIVE","unit":"吨","remark":"外部 API 更新"}'
        WHEN method = 'POST' AND path_pattern = '/api/external/v1/products/*/prices' THEN '{"currentPrice":1000.00,"effectiveDate":"2026-06-01","unit":"吨","remark":"外部 API 价格"}'
        WHEN method = 'PUT' AND path_pattern = '/api/external/v1/prices/*' THEN '{"currentPrice":1088.00,"effectiveDate":"2026-06-01","unit":"吨","remark":"外部 API 调价"}'
        ELSE ''
    END,
    path_params_example = CASE
        WHEN path_pattern LIKE '%products/*/price%' OR path_pattern LIKE '%products/*/current-price' THEN @product_path_example
        WHEN path_pattern LIKE '%products/*/prices' THEN @product_path_example
        WHEN path_pattern LIKE '%/home/**' THEN '{"wildcard":"dashboard"}'
        WHEN path_pattern LIKE '%/dict/**' THEN '{"wildcard":"active"}'
        WHEN path_pattern LIKE '%/*' THEN @id_path_example
        ELSE '{}'
    END,
    query_schema = CASE
        WHEN path_pattern IN ('/api/external/v1/products', '/api/external/v1/categories', '/api/external/v1/origins', '/api/external/v1/customers') THEN @page_query_schema
        WHEN path_pattern = '/api/external/v1/price-query' THEN '[{"name":"page","type":"number","required":false,"defaultValue":0,"description":"页码，从 0 开始"},{"name":"size","type":"number","required":false,"defaultValue":20,"description":"每页条数"},{"name":"keyword","type":"string","required":false,"description":"产品或分类关键词"},{"name":"date","type":"string","required":false,"description":"查询日期，格式 yyyy-MM-dd"}]'
        WHEN path_pattern = '/api/external/v1/price-query/export' THEN '[{"name":"date","type":"string","required":false,"description":"导出日期，格式 yyyy-MM-dd"}]'
        WHEN path_pattern LIKE '%price-history' THEN @page_query_schema
        WHEN path_pattern LIKE '%price-by-date' OR path_pattern IN ('/api/external/v1/prices/by-date', '/api/external/v1/prices/by-date-with-stats') THEN '[{"name":"date","type":"string","required":true,"description":"查询日期，格式 yyyy-MM-dd"}]'
        WHEN path_pattern LIKE '%price-trend' THEN '[{"name":"startDate","type":"string","required":true,"description":"开始日期，格式 yyyy-MM-dd"},{"name":"endDate","type":"string","required":true,"description":"结束日期，格式 yyyy-MM-dd"}]'
        WHEN path_pattern = '/api/external/v1/dict' THEN '[{"name":"category","type":"string","required":false,"description":"字典分类编码，例如 api_permission"}]'
        ELSE @empty_schema
    END,
    body_schema = CASE
        WHEN method IN ('POST', 'PUT') AND path_pattern LIKE '/api/external/v1/products%' AND path_pattern NOT LIKE '%/prices' THEN '[{"name":"name","type":"string","required":true,"description":"产品名称"},{"name":"categoryId","type":"number","required":true,"description":"分类 ID"},{"name":"status","type":"string","required":false,"defaultValue":"ACTIVE","description":"状态编码"},{"name":"unit","type":"string","required":false,"description":"计量单位"},{"name":"remark","type":"string","required":false,"description":"备注"}]'
        WHEN method IN ('POST', 'PUT') AND (path_pattern LIKE '%/prices' OR path_pattern LIKE '/api/external/v1/prices/%') THEN '[{"name":"currentPrice","type":"number","required":true,"description":"当前价格"},{"name":"effectiveDate","type":"string","required":true,"description":"生效日期，格式 yyyy-MM-dd"},{"name":"unit","type":"string","required":false,"description":"计量单位"},{"name":"remark","type":"string","required":false,"description":"备注"}]'
        ELSE @empty_schema
    END,
    path_params_schema = CASE
        WHEN path_pattern LIKE '%products/*/price%' OR path_pattern LIKE '%products/*/current-price' THEN @product_path_schema
        WHEN path_pattern LIKE '%products/*/prices' THEN @product_path_schema
        WHEN path_pattern LIKE '%/home/**' OR path_pattern LIKE '%/dict/**' THEN '[{"name":"wildcard","type":"string","required":true,"description":"通配路径片段"}]'
        WHEN path_pattern LIKE '%/*' THEN @id_path_schema
        ELSE @empty_schema
    END,
    success_example = CASE
        WHEN path_pattern LIKE '%export%' THEN '文件流响应，保存为 xlsx 文件'
        WHEN method = 'DELETE' THEN '{"code":200,"message":"删除成功","data":true}'
        WHEN method IN ('POST', 'PUT') THEN '{"code":200,"message":"操作成功","data":{"id":1}}'
        ELSE '{"code":200,"message":"操作成功","data":{}}'
    END,
    failure_example = CASE
        WHEN method IN ('POST', 'PUT') THEN '{"code":400,"message":"参数错误或业务校验失败"}'
        WHEN method = 'DELETE' THEN '{"code":409,"message":"资源存在关联数据，不能删除"}'
        ELSE '{"code":401,"message":"API 签名验证失败"}'
    END,
    code_notes = CASE
        WHEN method = 'DELETE' THEN '删除接口风险较高，复制运行前请确认资源 ID 和授权范围。'
        WHEN path_pattern LIKE '%export%' THEN '导出接口返回文件流，PowerShell 示例会写入本地文件。'
        WHEN method IN ('POST', 'PUT') THEN '请求体会参与 SHA-256 和 HMAC 签名，发送 body 必须与签名时的字符串完全一致。'
        ELSE '读取接口适合用于首次联调，建议先复制此类接口验证签名配置。'
    END
WHERE query_example IS NULL
   OR body_example IS NULL
   OR path_params_example IS NULL
   OR query_schema IS NULL
   OR body_schema IS NULL
   OR path_params_schema IS NULL
   OR success_example IS NULL
   OR failure_example IS NULL
   OR code_notes IS NULL;
