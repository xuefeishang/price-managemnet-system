-- Add API documentation metadata for external endpoint permission details.

SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_external_api_endpoint'
      AND column_name = 'request_example'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE sys_external_api_endpoint ADD COLUMN request_example TEXT AFTER description',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_external_api_endpoint'
      AND column_name = 'response_example'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE sys_external_api_endpoint ADD COLUMN response_example TEXT AFTER request_example',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_external_api_endpoint'
      AND column_name = 'error_codes'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE sys_external_api_endpoint ADD COLUMN error_codes TEXT AFTER response_example',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_external_api_endpoint'
      AND column_name = 'usage_notes'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE sys_external_api_endpoint ADD COLUMN usage_notes TEXT AFTER error_codes',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE sys_external_api_endpoint
SET
    request_example = CASE
        WHEN method = 'GET' THEN CONCAT(method, ' ', path_pattern, CASE WHEN path_pattern LIKE '%by-date%' OR path_pattern LIKE '%price-query%' THEN '?date=2026-05-30' ELSE '' END)
        WHEN method = 'POST' THEN CONCAT(method, ' ', path_pattern, '\n{"name":"示例数据","status":"ACTIVE"}')
        WHEN method = 'PUT' THEN CONCAT(method, ' ', path_pattern, '\n{"name":"更新后的示例数据"}')
        WHEN method = 'DELETE' THEN CONCAT(method, ' ', path_pattern)
        ELSE CONCAT(method, ' ', path_pattern)
    END,
    response_example = CASE
        WHEN path_pattern LIKE '%export%' THEN '二进制 Excel 文件流'
        WHEN method = 'DELETE' THEN '{"code":200,"message":"删除成功"}'
        ELSE '{"code":200,"data":{}}'
    END,
    error_codes = CASE
        WHEN method IN ('POST', 'PUT') THEN '400 参数错误；401 签名失败；403 权限不足；409 数据冲突'
        WHEN method = 'DELETE' THEN '401 签名失败；403 权限不足；404 资源不存在；409 已有关联数据'
        ELSE '400 参数错误；401 签名失败；403 权限不足；429 触发限流'
    END,
    usage_notes = CASE permission_code
        WHEN 'product:delete' THEN '高风险权限，建议只授权给受控系统并配合 IP 白名单。'
        WHEN 'price-query:export' THEN '导出接口耗时更高，建议配置较低日限额并控制调用频率。'
        WHEN 'dict:read' THEN '读取字典时必须关注 category，显示名称仍以字典服务为准。'
        WHEN 'price:write' THEN '写入价格可能触发审批或价格历史记录，请保留调用日志便于审计。'
        ELSE '调用时必须携带 X-App-Id、X-Timestamp、X-Nonce、X-Signature，禁止传输 X-App-Secret。'
    END
WHERE request_example IS NULL
   OR response_example IS NULL
   OR error_codes IS NULL
   OR usage_notes IS NULL;
