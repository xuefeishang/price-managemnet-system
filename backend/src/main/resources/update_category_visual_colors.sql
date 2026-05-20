-- 更新分类视觉配置为更鲜明的配色方案
-- 让颜色差异更加明显，一眼就能区分

UPDATE sys_dict SET extra_value = '{"categoryCode":"BLACK_METAL","primaryColor":"#DC2626","secondaryColor":"#EF4444","textColor":"#7F1D1D","borderColor":"#DC2626","glowColor":"rgba(220,38,38,0.25)","icon":"iron_ore","iconType":"builtin","darkMode":{"primaryColor":"#EF4444","textColor":"#FEE2E2","borderColor":"#DC2626","glowColor":"rgba(239,68,68,0.3)"}}'
WHERE category='category_visual_config' AND dict_key='CAT_BLACK_METAL';

UPDATE sys_dict SET extra_value = '{"categoryCode":"NON_FERROUS_METAL","primaryColor":"#EA580C","secondaryColor":"#F97316","textColor":"#7C2D12","borderColor":"#EA580C","glowColor":"rgba(234,88,12,0.25)","icon":"copper_coil","iconType":"builtin","darkMode":{"primaryColor":"#F97316","textColor":"#FFEDD5","borderColor":"#EA580C","glowColor":"rgba(249,115,22,0.3)"}}'
WHERE category='category_visual_config' AND dict_key='CAT_NON_FERROUS';

UPDATE sys_dict SET extra_value = '{"categoryCode":"PRECIOUS_METAL","primaryColor":"#CA8A04","secondaryColor":"#EAB308","textColor":"#713F12","borderColor":"#CA8A04","glowColor":"rgba(202,138,4,0.25)","icon":"gold_ingot","iconType":"builtin","darkMode":{"primaryColor":"#EAB308","textColor":"#FEF9C3","borderColor":"#CA8A04","glowColor":"rgba(234,179,8,0.3)"}}'
WHERE category='category_visual_config' AND dict_key='CAT_PRECIOUS';

UPDATE sys_dict SET extra_value = '{"categoryCode":"CHEMICAL","primaryColor":"#7C3AED","secondaryColor":"#8B5CF6","textColor":"#4C1D95","borderColor":"#7C3AED","glowColor":"rgba(124,58,237,0.25)","icon":"rare_element","iconType":"builtin","darkMode":{"primaryColor":"#8B5CF6","textColor":"#EDE9FE","borderColor":"#7C3AED","glowColor":"rgba(139,92,246,0.3)"}}'
WHERE category='category_visual_config' AND dict_key='CAT_CHEMICAL';

UPDATE sys_dict SET extra_value = '{"categoryCode":"COAL","primaryColor":"#1E3A5F","secondaryColor":"#2563EB","textColor":"#0F172A","borderColor":"#1E3A5F","glowColor":"rgba(30,58,95,0.25)","icon":"iron_ore","iconType":"builtin","darkMode":{"primaryColor":"#3B82F6","textColor":"#DBEAFE","borderColor":"#1E3A5F","glowColor":"rgba(59,130,246,0.3)"}}'
WHERE category='category_visual_config' AND dict_key='CAT_COAL';

-- 验证更新结果
SELECT dict_key,
       JSON_EXTRACT(extra_value, '$.primaryColor') AS primaryColor,
       JSON_EXTRACT(extra_value, '$.secondaryColor') AS secondaryColor
FROM sys_dict
WHERE category='category_visual_config'
ORDER BY dict_key;