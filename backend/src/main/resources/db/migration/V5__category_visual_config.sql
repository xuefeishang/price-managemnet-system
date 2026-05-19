-- =====================================================
-- V5: 分类视觉配置字典数据初始化
-- 创建日期: 2026-05-19
-- 说明: 为产品分类添加视觉配置（颜色、图标、光晕等）
-- =====================================================

-- 分类视觉配置字典数据
-- 边框统一使用深矿蓝(#165DFF)，分类色用于内部填充和图标
-- 光晕透明度降低至0.15-0.2，符合"克制"原则
INSERT INTO sys_dict (category, dict_key, dict_value, extra_value, sort_order, status, remark) VALUES
-- 黄金类
('category_visual_config', 'CAT_GOLD', '黄金类', '{"categoryId":1,"primaryColor":"#D4A574","secondaryColor":"#C4956A","textColor":"#8B5A2B","borderColor":"#165DFF","glowColor":"rgba(212,165,116,0.15)","icon":"gold_ingot","iconType":"builtin","darkMode":{"primaryColor":"#E8C89E","textColor":"#F5E6D3","borderColor":"#165DFF","glowColor":"rgba(232,200,158,0.2)"}}', 1, 'ACTIVE', '黄金类产品视觉配置'),

-- 白银类
('category_visual_config', 'CAT_SILVER', '白银类', '{"categoryId":2,"primaryColor":"#A8B5C4","secondaryColor":"#9AA8B7","textColor":"#6B7B8A","borderColor":"#165DFF","glowColor":"rgba(168,181,196,0.15)","icon":"silver_bar","iconType":"builtin","darkMode":{"primaryColor":"#C4D1DE","textColor":"#E8F0F8","borderColor":"#165DFF","glowColor":"rgba(196,209,222,0.2)"}}', 2, 'ACTIVE', '白银类产品视觉配置'),

-- 铜类
('category_visual_config', 'CAT_COPPER', '铜类', '{"categoryId":3,"primaryColor":"#B87333","secondaryColor":"#A66628","textColor":"#8B4513","borderColor":"#165DFF","glowColor":"rgba(184,115,51,0.15)","icon":"copper_coil","iconType":"builtin","darkMode":{"primaryColor":"#D4916A","textColor":"#F5E0D0","borderColor":"#165DFF","glowColor":"rgba(212,145,106,0.2)"}}', 3, 'ACTIVE', '铜类产品视觉配置'),

-- 铁矿石
('category_visual_config', 'CAT_IRON', '铁矿石', '{"categoryId":4,"primaryColor":"#8B4513","secondaryColor":"#7A3D11","textColor":"#5C3317","borderColor":"#165DFF","glowColor":"rgba(139,69,19,0.15)","icon":"iron_ore","iconType":"builtin","darkMode":{"primaryColor":"#B87852","textColor":"#F5E8D8","borderColor":"#165DFF","glowColor":"rgba(184,120,82,0.2)"}}', 4, 'ACTIVE', '铁矿石产品视觉配置'),

-- 铝类
('category_visual_config', 'CAT_ALUMINUM', '铝类', '{"categoryId":5,"primaryColor":"#C0C0C0","secondaryColor":"#B0B0B0","textColor":"#808080","borderColor":"#165DFF","glowColor":"rgba(192,192,192,0.12)","icon":"aluminum_block","iconType":"builtin","darkMode":{"primaryColor":"#E0E0E0","textColor":"#F5F5F5","borderColor":"#165DFF","glowColor":"rgba(224,224,224,0.15)"}}', 5, 'ACTIVE', '铝类产品视觉配置'),

-- 稀土类
('category_visual_config', 'CAT_RARE', '稀土类', '{"categoryId":6,"primaryColor":"#8B5CF6","secondaryColor":"#7C3AED","textColor":"#6D28D9","borderColor":"#165DFF","glowColor":"rgba(139,92,246,0.15)","icon":"rare_element","iconType":"builtin","darkMode":{"primaryColor":"#A78BFA","textColor":"#EDE9FE","borderColor":"#165DFF","glowColor":"rgba(167,139,250,0.2)"}}', 6, 'ACTIVE', '稀土类产品视觉配置');

-- 默认分类视觉配置（用于未配置的分类）
INSERT INTO sys_dict (category, dict_key, dict_value, extra_value, sort_order, status, remark) VALUES
('category_visual_config', 'CAT_DEFAULT', '默认', '{"categoryId":0,"primaryColor":"#165DFF","secondaryColor":"#3C7EFF","textColor":"#1D2129","borderColor":"#165DFF","glowColor":"rgba(22,93,255,0.15)","icon":"default","iconType":"builtin","darkMode":{"primaryColor":"#3C7EFF","textColor":"#F5F7FA","borderColor":"#165DFF","glowColor":"rgba(60,126,255,0.2)"}}', 0, 'ACTIVE', '默认分类视觉配置');