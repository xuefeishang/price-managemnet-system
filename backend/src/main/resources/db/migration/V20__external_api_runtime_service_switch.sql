-- Add runtime service switch for external API availability.

INSERT INTO sys_style_config (config_key, config_value, config_type, description, created_time, updated_time)
SELECT 'external_api_service_enabled', 'true', 'boolean', '外部 API 运行时服务开关', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_style_config WHERE config_key = 'external_api_service_enabled'
);

INSERT INTO sys_dict (category, dict_key, dict_value, sort_order, status, remark, created_time, updated_time)
SELECT 'api_auth_result', 'SERVICE_DISABLED', '服务暂停', 12, 'ACTIVE', '外部API认证结果', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict WHERE category = 'api_auth_result' AND dict_key = 'SERVICE_DISABLED'
);
