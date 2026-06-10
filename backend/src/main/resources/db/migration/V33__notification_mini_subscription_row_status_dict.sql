INSERT INTO sys_dict (category, dict_key, dict_value, extra_value, sort_order, status, remark, created_time, updated_time)
SELECT seed.category, seed.dict_key, seed.dict_value, seed.extra_value, seed.sort_order, seed.status, seed.remark, seed.created_time, seed.updated_time
FROM (
    SELECT 'notification_mini_subscription_row_status' AS category, 'NORMAL' AS dict_key, '正常' AS dict_value, '#10B981' AS extra_value, 1 AS sort_order, 'ACTIVE' AS status, '小程序订阅用户行状态' AS remark, NOW() AS created_time, NOW() AS updated_time
    UNION ALL SELECT 'notification_mini_subscription_row_status', 'LOW_BALANCE', '低余量', '#F59E0B', 2, 'ACTIVE', '小程序订阅用户行状态', NOW(), NOW()
    UNION ALL SELECT 'notification_mini_subscription_row_status', 'UNBOUND', '未绑定', '#9CA3AF', 3, 'ACTIVE', '小程序订阅用户行状态', NOW(), NOW()
    UNION ALL SELECT 'notification_mini_subscription_row_status', 'REJECTED', '拒绝/禁用', '#EF4444', 4, 'ACTIVE', '小程序订阅用户行状态', NOW(), NOW()
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict d WHERE d.category = seed.category AND d.dict_key = seed.dict_key
);
