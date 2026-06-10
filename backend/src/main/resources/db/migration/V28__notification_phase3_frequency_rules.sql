INSERT INTO sys_dict (category, dict_key, dict_value, extra_value, sort_order, status, remark, created_time, updated_time)
SELECT * FROM (
    SELECT 'notification_frequency_rule' AS category, 'TASK_FAILED' AS dict_key, '任务失败聚合频控' AS dict_value,
           '{"enabled":true,"windowMinutes":30,"maxCount":5}' AS extra_value,
           1 AS sort_order, 'ACTIVE' AS status, '任务失败消息在时间窗内超过阈值后聚合' AS remark, NOW() AS created_time, NOW() AS updated_time
    UNION ALL SELECT 'notification_frequency_rule', 'API_LIMIT_WARNING', 'API告警聚合频控',
           '{"enabled":true,"windowMinutes":30,"maxCount":5}',
           2, 'ACTIVE', 'API告警消息在时间窗内超过阈值后聚合', NOW(), NOW()
    UNION ALL SELECT 'notification_frequency_rule', 'IMPORT_EXPORT_FINISHED', '导入导出完成聚合频控',
           '{"enabled":true,"windowMinutes":60,"maxCount":10}',
           3, 'ACTIVE', '导入导出完成消息在时间窗内超过阈值后聚合', NOW(), NOW()
) AS tmp
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict d WHERE d.category = tmp.category AND d.dict_key = tmp.dict_key
);
