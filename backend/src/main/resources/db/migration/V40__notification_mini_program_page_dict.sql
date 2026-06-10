INSERT INTO sys_dict (
    category, dict_key, dict_value, extra_value, sort_order, status, remark, created_time, updated_time
)
SELECT seed.category, seed.dict_key, seed.dict_value, seed.extra_value, seed.sort_order,
       seed.status, seed.remark, seed.created_time, seed.updated_time
FROM (
    SELECT 'notification_mini_program_page' AS category,
           'pages/notifications/index' AS dict_key,
           '消息通知' AS dict_value,
           NULL AS extra_value,
           1 AS sort_order,
           'ACTIVE' AS status,
           '小程序通知跳转页' AS remark,
           NOW() AS created_time,
           NOW() AS updated_time
    UNION ALL SELECT 'notification_mini_program_page', 'pages/home/index', '首页', NULL, 2, 'ACTIVE', '小程序通知跳转页', NOW(), NOW()
    UNION ALL SELECT 'notification_mini_program_page', 'pages/history/index', '历史价格', NULL, 3, 'ACTIVE', '小程序通知跳转页', NOW(), NOW()
    UNION ALL SELECT 'notification_mini_program_page', 'pages/products/list', '产品列表', NULL, 4, 'ACTIVE', '小程序通知跳转页', NOW(), NOW()
    UNION ALL SELECT 'notification_mini_program_page', 'pages/profile/index', '个人中心', NULL, 5, 'ACTIVE', '小程序通知跳转页', NOW(), NOW()
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict d WHERE d.category = seed.category AND d.dict_key = seed.dict_key
);
