INSERT INTO sys_dict (
    category, dict_key, dict_value, extra_value, sort_order, status, remark, created_time, updated_time
)
SELECT * FROM (
    SELECT 'notification_mini_resolution_status', 'OPEN', '待处理', '#F59E0B', 1, 'ACTIVE', '小程序订阅异常处理状态', NOW(), NOW()
    UNION ALL SELECT 'notification_mini_resolution_status', 'RESOLVED', '已处理', '#10B981', 2, 'ACTIVE', '小程序订阅异常处理状态', NOW(), NOW()
    UNION ALL SELECT 'notification_mini_resolution_status', 'SNOOZED', '暂不提醒', '#64748B', 3, 'ACTIVE', '小程序订阅异常处理状态', NOW(), NOW()
    UNION ALL SELECT 'notification_mini_resolution_status', 'FOLLOW_UP', '跟进标记', '#3B82F6', 4, 'ACTIVE', '小程序订阅异常处理状态', NOW(), NOW()
) AS tmp
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict d
    WHERE d.category = tmp.category AND d.dict_key = tmp.dict_key
);
