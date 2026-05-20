-- 分类视觉配置数据插入脚本
-- 用于已有数据库，init.sql 条件判断不会执行

-- 检查是否已存在
SELECT COUNT(*) AS existing_count FROM sys_dict WHERE category='category_visual_config';

-- 如果已存在，先删除旧数据（可选）
-- DELETE FROM sys_dict WHERE category='category_visual_config';

-- 插入分类视觉配置
INSERT INTO sys_dict (category, dict_key, dict_value, extra_value, sort_order, status, remark, created_time, updated_time)
SELECT * FROM (
    -- 黑色金属：钢铁灰色调
    SELECT 'category_visual_config' AS category, 'BLACK_METAL' AS dict_key, '黑色金属' AS dict_value,
    '{"categoryCode":"BLACK_METAL","primaryColor":"#4A5568","secondaryColor":"#718096","textColor":"#2D3748","borderColor":"#4A5568","glowColor":"rgba(74,85,104,0.2)","icon":"iron_ore","iconType":"builtin","darkMode":{"primaryColor":"#718096","textColor":"#E2E8F0","borderColor":"#4A5568","glowColor":"rgba(113,128,150,0.25)"}}' AS extra_value,
    1 AS sort_order, 'ACTIVE' AS status, '钢铁类产品视觉配置' AS remark, NOW() AS created_time, NOW() AS updated_time
    -- 有色金属：金铜色调
    UNION ALL SELECT 'category_visual_config', 'NON_FERROUS_METAL', '有色金属',
    '{"categoryCode":"NON_FERROUS_METAL","primaryColor":"#B87333","secondaryColor":"#D4A574","textColor":"#8B4513","borderColor":"#B87333","glowColor":"rgba(184,115,51,0.2)","icon":"copper_coil","iconType":"builtin","darkMode":{"primaryColor":"#D4A574","textColor":"#F5F7FA","borderColor":"#B87333","glowColor":"rgba(212,165,116,0.25)"}}',
    2, 'ACTIVE', '铜铝等有色金属视觉配置', NOW(), NOW()
    -- 贵金属：奢华金色
    UNION ALL SELECT 'category_visual_config', 'PRECIOUS_METAL', '贵金属',
    '{"categoryCode":"PRECIOUS_METAL","primaryColor":"#D4AF37","secondaryColor":"#FFD700","textColor":"#8B6914","borderColor":"#D4AF37","glowColor":"rgba(212,175,55,0.25)","icon":"gold_ingot","iconType":"builtin","darkMode":{"primaryColor":"#FFD700","textColor":"#FFF8DC","borderColor":"#D4AF37","glowColor":"rgba(255,215,0,0.3)"}}',
    3, 'ACTIVE', '金银铂贵金属视觉配置', NOW(), NOW()
    -- 化工产品：科技紫色调
    UNION ALL SELECT 'category_visual_config', 'CHEMICAL', '化工产品',
    '{"categoryCode":"CHEMICAL","primaryColor":"#8B5CF6","secondaryColor":"#A78BFA","textColor":"#6D28D9","borderColor":"#8B5CF6","glowColor":"rgba(139,92,246,0.2)","icon":"rare_element","iconType":"builtin","darkMode":{"primaryColor":"#A78BFA","textColor":"#EDE9FE","borderColor":"#8B5CF6","glowColor":"rgba(167,139,250,0.25)"}}',
    4, 'ACTIVE', '化工原料产品视觉配置', NOW(), NOW()
    -- 煤炭及焦炭：深黑色调
    UNION ALL SELECT 'category_visual_config', 'COAL', '煤炭及焦炭',
    '{"categoryCode":"COAL","primaryColor":"#1F2937","secondaryColor":"#374151","textColor":"#111827","borderColor":"#1F2937","glowColor":"rgba(31,41,55,0.3)","icon":"iron_ore","iconType":"builtin","darkMode":{"primaryColor":"#374151","textColor":"#F9FAFB","borderColor":"#1F2937","glowColor":"rgba(55,65,81,0.35)"}}',
    5, 'ACTIVE', '煤炭焦炭产品视觉配置', NOW(), NOW()
) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE category='category_visual_config' AND dict_key='BLACK_METAL');

-- 验证插入结果
SELECT id, category, dict_key, dict_value, LEFT(extra_value, 50) AS extra_value_preview
FROM sys_dict
WHERE category='category_visual_config';