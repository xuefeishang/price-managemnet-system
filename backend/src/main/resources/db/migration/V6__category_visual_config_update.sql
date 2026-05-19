-- =====================================================
-- 分类视觉配置 - 按实际产品分类配置
-- 创建日期: 2026-05-19
-- 说明: 根据product_category表的实际分类配置视觉样式
-- =====================================================

-- 先删除旧的分类视觉配置（如果存在）
DELETE FROM sys_dict WHERE category = 'category_visual_config';

-- 重新插入分类视觉配置
-- 边框统一使用深矿蓝(#165DFF)，分类色用于内部填充和图标
-- 光晕透明度0.15-0.2，符合"克制"原则

-- 黑色金属（铁矿、钢铁等）- 深灰褐色
INSERT INTO sys_dict (category, dict_key, dict_value, extra_value, sort_order, status, remark, created_time, updated_time)
VALUES ('category_visual_config', 'CAT_BLACK_METAL', '黑色金属',
'{"categoryCode":"BLACK_METAL","primaryColor":"#5C4033","secondaryColor":"#4A3628","textColor":"#3D2B1F","borderColor":"#165DFF","glowColor":"rgba(92,64,51,0.15)","icon":"iron_ore","iconType":"builtin","darkMode":{"primaryColor":"#8B7355","textColor":"#E8DED5","borderColor":"#165DFF","glowColor":"rgba(139,115,85,0.2)"}}',
1, 'ACTIVE', '黑色金属产品视觉配置（铁矿、钢铁）', NOW(), NOW());

-- 有色金属（铜、铝、锌等）- 青铜色
INSERT INTO sys_dict (category, dict_key, dict_value, extra_value, sort_order, status, remark, created_time, updated_time)
VALUES ('category_visual_config', 'CAT_NON_FERROUS', '有色金属',
'{"categoryCode":"NON_FERROUS_METAL","primaryColor":"#CD7F32","secondaryColor":"#B87333","textColor":"#8B5A2B","borderColor":"#165DFF","glowColor":"rgba(205,127,50,0.15)","icon":"copper_coil","iconType":"builtin","darkMode":{"primaryColor":"#E8A862","textColor":"#F5E6D3","borderColor":"#165DFF","glowColor":"rgba(232,168,98,0.2)"}}',
2, 'ACTIVE', '有色金属产品视觉配置（铜、铝、锌）', NOW(), NOW());

-- 贵金属（金、银、铂等）- 金色
INSERT INTO sys_dict (category, dict_key, dict_value, extra_value, sort_order, status, remark, created_time, updated_time)
VALUES ('category_visual_config', 'CAT_PRECIOUS', '贵金属',
'{"categoryCode":"PRECIOUS_METAL","primaryColor":"#D4A574","secondaryColor":"#C4956A","textColor":"#8B5A2B","borderColor":"#165DFF","glowColor":"rgba(212,165,116,0.15)","icon":"gold_ingot","iconType":"builtin","darkMode":{"primaryColor":"#E8C89E","textColor":"#F5E6D3","borderColor":"#165DFF","glowColor":"rgba(232,200,158,0.2)"}}',
3, 'ACTIVE', '贵金属产品视觉配置（金、银、铂）', NOW(), NOW());

-- 化工产品 - 蓝绿色
INSERT INTO sys_dict (category, dict_key, dict_value, extra_value, sort_order, status, remark, created_time, updated_time)
VALUES ('category_visual_config', 'CAT_CHEMICAL', '化工产品',
'{"categoryCode":"CHEMICAL","primaryColor":"#0891B2","secondaryColor":"#0E7490","textColor":"#164E63","borderColor":"#165DFF","glowColor":"rgba(8,145,178,0.15)","icon":"aluminum_block","iconType":"builtin","darkMode":{"primaryColor":"#22D3EE","textColor":"#E0F7FA","borderColor":"#165DFF","glowColor":"rgba(34,211,238,0.2)"}}',
4, 'ACTIVE', '化工产品视觉配置', NOW(), NOW());

-- 煤炭及焦炭 - 炭黑色
INSERT INTO sys_dict (category, dict_key, dict_value, extra_value, sort_order, status, remark, created_time, updated_time)
VALUES ('category_visual_config', 'CAT_COAL', '煤炭及焦炭',
'{"categoryCode":"COAL","primaryColor":"#374151","secondaryColor":"#1F2937","textColor":"#111827","borderColor":"#165DFF","glowColor":"rgba(55,65,81,0.15)","icon":"iron_ore","iconType":"builtin","darkMode":{"primaryColor":"#6B7280","textColor":"#F3F4F6","borderColor":"#165DFF","glowColor":"rgba(107,114,128,0.2)"}}',
5, 'ACTIVE', '煤炭及焦炭产品视觉配置', NOW(), NOW());

-- 默认分类视觉配置（用于未配置的分类）
INSERT INTO sys_dict (category, dict_key, dict_value, extra_value, sort_order, status, remark, created_time, updated_time)
VALUES ('category_visual_config', 'CAT_DEFAULT', '默认',
'{"categoryCode":"DEFAULT","primaryColor":"#165DFF","secondaryColor":"#3C7EFF","textColor":"#1D2129","borderColor":"#165DFF","glowColor":"rgba(22,93,255,0.15)","icon":"default","iconType":"builtin","darkMode":{"primaryColor":"#3C7EFF","textColor":"#F5F7FA","borderColor":"#165DFF","glowColor":"rgba(60,126,255,0.2)"}}',
0, 'ACTIVE', '默认分类视觉配置', NOW(), NOW());

-- 验证插入结果
SELECT dict_key, dict_value,
       JSON_EXTRACT(extra_value, '$.primaryColor') AS primary_color,
       JSON_EXTRACT(extra_value, '$.icon') AS icon
FROM sys_dict
WHERE category = 'category_visual_config'
ORDER BY sort_order;
