INSERT INTO sys_dict (category, dict_key, dict_value, extra_value, sort_order, status, remark, created_time, updated_time)
SELECT * FROM (
    SELECT 'notification_provider_health_status' AS category, 'OK' AS dict_key, '正常' AS dict_value,
           '#10B981' AS extra_value, 1 AS sort_order, 'ACTIVE' AS status, 'Provider健康状态' AS remark,
           NOW() AS created_time, NOW() AS updated_time
    UNION ALL SELECT 'notification_provider_health_status', 'DEGRADED', '降级',
           '#F59E0B', 2, 'ACTIVE', 'Provider健康状态', NOW(), NOW()
    UNION ALL SELECT 'notification_provider_health_status', 'DOWN', '异常',
           '#EF4444', 3, 'ACTIVE', 'Provider健康状态', NOW(), NOW()
    UNION ALL SELECT 'notification_provider_health_status', 'NOT_CONFIGURED', '未配置',
           '#9CA3AF', 4, 'ACTIVE', 'Provider健康状态', NOW(), NOW()
) AS tmp
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict d WHERE d.category = tmp.category AND d.dict_key = tmp.dict_key
);
