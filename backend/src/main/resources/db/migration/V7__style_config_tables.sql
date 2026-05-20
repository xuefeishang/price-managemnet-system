-- V7: 样式配置专用表
-- 创建 sys_style_config 和 sys_style_preset 表，实现样式设置与字典管理的分离

-- 样式配置主表（当前生效配置）
CREATE TABLE IF NOT EXISTS sys_style_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '配置ID',
    config_key VARCHAR(100) NOT NULL UNIQUE COMMENT '配置键',
    config_value TEXT COMMENT '配置值',
    config_type VARCHAR(50) DEFAULT 'string' COMMENT '类型：string/json/color/font',
    description VARCHAR(500) COMMENT '说明',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_style_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='样式配置表';

-- 样式预设表（色彩方案、布局方案等）
CREATE TABLE IF NOT EXISTS sys_style_preset (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '预设ID',
    preset_type VARCHAR(50) NOT NULL COMMENT '预设类型：color_scheme/layout_style/font_preset',
    preset_key VARCHAR(100) NOT NULL COMMENT '预设键',
    preset_name VARCHAR(200) NOT NULL COMMENT '预设名称',
    preset_description VARCHAR(500) COMMENT '预设说明',
    config_json TEXT NOT NULL COMMENT '配置 JSON',
    is_default BIT DEFAULT 0 COMMENT '是否默认',
    sort_order INT DEFAULT 0 COMMENT '排序',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_preset_type_key (preset_type, preset_key),
    INDEX idx_preset_type (preset_type),
    INDEX idx_preset_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='样式预设表';

-- 基础样式配置（幂等插入）
INSERT INTO sys_style_config (config_key, config_value, config_type, description)
VALUES
    ('system_name', '价格管理系统', 'string', '系统显示名称'),
    ('logo_url', '', 'string', 'Logo URL'),
    ('logo_size', 'medium', 'string', 'Logo 尺寸'),
    ('heading_font', 'Newsreader', 'font', '标题字体'),
    ('body_font', 'Inter', 'font', '正文字体'),
    ('number_font', 'JetBrains Mono', 'font', '数字字体'),
    ('font_size_xs', '0.75rem', 'string', '辅助信息字号'),
    ('font_size_sm', '0.875rem', 'string', '表格内容字号'),
    ('font_size_base', '1rem', 'string', '正文表头字号'),
    ('font_size_lg', '1.125rem', 'string', '小节标题字号'),
    ('font_size_xl', '1.25rem', 'string', '页面副标题字号'),
    ('font_size_2xl', '1.5rem', 'string', '页面主标题字号'),
    ('font_size_3xl', '1.875rem', 'string', '特大标题字号'),
    ('active_color_scheme', 'scheme_teal_classic', 'string', '当前色彩方案'),
    ('active_layout_style', 'layout_top_nav', 'string', '当前布局方案'),
    ('active_theme', 'theme_red_green', 'string', '兼容旧主题')
ON DUPLICATE KEY UPDATE
    config_value = VALUES(config_value),
    config_type = VALUES(config_type),
    description = VALUES(description);

-- 色彩方案预设（幂等插入）
INSERT INTO sys_style_preset (preset_type, preset_key, preset_name, preset_description, config_json, is_default, sort_order, status)
VALUES
    ('color_scheme', 'scheme_teal_classic', '青绿经典（默认）', '当前系统默认配色，青绿主色，专业稳重', '{"priceRiseColor":"#EF4444","priceFallColor":"#10B981","priceFlatColor":"#9CA3AF","chartPrimaryColor":"#0D6E6E","chartBudgetColor":"#F59E0B","chartColors":["#0D6E6E","#10B981","#F59E0B","#EF4444","#8B5CF6","#EC4899","#6366F1","#14B8A6","#64748B"]}', 1, 1, 'ACTIVE'),
    ('color_scheme', 'scheme_classic', '经典红绿', '传统配色，涨价红色，跌价绿色', '{"priceRiseColor":"#EF4444","priceFallColor":"#10B981","priceFlatColor":"#9CA3AF","chartPrimaryColor":"#0D6E6E","chartBudgetColor":"#F59E0B","chartColors":["#0D6E6E","#10B981","#F59E0B","#EF4444","#8B5CF6","#EC4899","#6366F1","#14B8A6","#64748B"]}', 0, 2, 'ACTIVE'),
    ('color_scheme', 'scheme_us_stock', '美股绿红', '美股风格，涨价绿色，跌价红色', '{"priceRiseColor":"#10B981","priceFallColor":"#EF4444","priceFlatColor":"#9CA3AF","chartPrimaryColor":"#0D6E6E","chartBudgetColor":"#F59E0B","chartColors":["#0D6E6E","#10B981","#F59E0B","#EF4444","#8B5CF6","#EC4899","#6366F1","#14B8A6","#64748B"]}', 0, 3, 'ACTIVE'),
    ('color_scheme', 'scheme_business', '商务蓝橙', '商务风格配色', '{"priceRiseColor":"#3B82F6","priceFallColor":"#F97316","priceFlatColor":"#9CA3AF","chartPrimaryColor":"#3B82F6","chartBudgetColor":"#F59E0B","chartColors":["#3B82F6","#F97316","#0D6E6E","#8B5CF6","#EC4899","#6366F1","#14B8A6","#64748B","#10B981"]}', 0, 4, 'ACTIVE'),
    ('color_scheme', 'scheme_noble', '高贵紫金', '高贵风格配色', '{"priceRiseColor":"#8B5CF6","priceFallColor":"#EAB308","priceFlatColor":"#9CA3AF","chartPrimaryColor":"#8B5CF6","chartBudgetColor":"#F59E0B","chartColors":["#8B5CF6","#EAB308","#0D6E6E","#EC4899","#6366F1","#14B8A6","#64748B","#10B981","#F59E0B"]}', 0, 5, 'ACTIVE'),
    ('color_scheme', 'scheme_deep_blue', '深矿蓝', '参考图配色，专业科技风格', '{"priceRiseColor":"#EF4444","priceFallColor":"#10B981","priceFlatColor":"#9CA3AF","chartPrimaryColor":"#165DFF","chartBudgetColor":"#F59E0B","chartColors":["#165DFF","#10B981","#F59E0B","#EF4444","#8B5CF6","#EC4899","#6366F1","#14B8A6","#64748B"]}', 0, 6, 'ACTIVE'),
    ('color_scheme', 'scheme_warm', '暖色系', '温暖活力配色', '{"priceRiseColor":"#F97316","priceFallColor":"#06B6D4","priceFlatColor":"#9CA3AF","chartPrimaryColor":"#F97316","chartBudgetColor":"#F59E0B","chartColors":["#F97316","#06B6D4","#F59E0B","#EF4444","#8B5CF6","#EC4899","#6366F1","#14B8A6","#64748B"]}', 0, 7, 'ACTIVE')
ON DUPLICATE KEY UPDATE
    preset_name = VALUES(preset_name),
    preset_description = VALUES(preset_description),
    config_json = VALUES(config_json),
    is_default = VALUES(is_default),
    sort_order = VALUES(sort_order),
    status = VALUES(status);

-- 布局方案预设（幂等插入）
INSERT INTO sys_style_preset (preset_type, preset_key, preset_name, preset_description, config_json, is_default, sort_order, status)
VALUES
    ('layout_style', 'layout_top_nav', '经典顶部导航', '传统后台管理布局', '{"navPosition":"top","navBgColor":"#FFFFFF","navTextColor":"#1A1A1A","pageBgColor":"#FAFAFA","cardBgColor":"#FFFFFF","cardShadow":"0 1px 3px rgba(0,0,0,0.1)","borderRadius":"12px"}', 1, 1, 'ACTIVE'),
    ('layout_style', 'layout_left_nav', '左侧导航', '功能较多的系统布局', '{"navPosition":"left","navBgColor":"#FFFFFF","navTextColor":"#1A1A1A","pageBgColor":"#FAFAFA","cardBgColor":"#FFFFFF","cardShadow":"0 1px 3px rgba(0,0,0,0.1)","borderRadius":"12px"}', 0, 2, 'ACTIVE'),
    ('layout_style', 'layout_dashboard', '深矿蓝仪表盘', '参考图布局，专业数据展示', '{"navPosition":"left","navBgColor":"#1E3A5F","navTextColor":"#FFFFFF","pageBgColor":"#F5F5F5","cardBgColor":"#FFFFFF","cardShadow":"0 1px 3px rgba(0,0,0,0.1)","borderRadius":"8px","showTitleBar":true,"showMiniChart":true,"gradientChart":true}', 0, 3, 'ACTIVE'),
    ('layout_style', 'layout_minimal', '极简卡片式', '简洁现代布局', '{"navPosition":"top-minimal","navBgColor":"transparent","navTextColor":"#1A1A1A","pageBgColor":"#FAFAFA","cardBgColor":"#FFFFFF","cardShadow":"0 4px 6px rgba(0,0,0,0.1)","borderRadius":"16px"}', 0, 4, 'ACTIVE')
ON DUPLICATE KEY UPDATE
    preset_name = VALUES(preset_name),
    preset_description = VALUES(preset_description),
    config_json = VALUES(config_json),
    is_default = VALUES(is_default),
    sort_order = VALUES(sort_order),
    status = VALUES(status);

-- 字号预设（幂等插入）
INSERT INTO sys_style_preset (preset_type, preset_key, preset_name, preset_description, config_json, is_default, sort_order, status)
VALUES
    ('font_preset', 'compact', '紧凑', '数据密集型后台', '{"fontSizeXs":"0.625rem","fontSizeSm":"0.75rem","fontSizeBase":"0.875rem","fontSizeLg":"1rem","fontSizeXl":"1.125rem","fontSize2xl":"1.25rem","fontSize3xl":"1.5rem"}', 0, 1, 'ACTIVE'),
    ('font_preset', 'standard', '标准', '通用场景', '{"fontSizeXs":"0.75rem","fontSizeSm":"0.875rem","fontSizeBase":"1rem","fontSizeLg":"1.125rem","fontSizeXl":"1.25rem","fontSize2xl":"1.5rem","fontSize3xl":"1.875rem"}', 1, 2, 'ACTIVE'),
    ('font_preset', 'large', '大字体', '比标准略大', '{"fontSizeXs":"0.8125rem","fontSizeSm":"0.9375rem","fontSizeBase":"1.0625rem","fontSizeLg":"1.1875rem","fontSizeXl":"1.375rem","fontSize2xl":"1.625rem","fontSize3xl":"1.9375rem"}', 0, 3, 'ACTIVE'),
    ('font_preset', 'xlarge', '特大字体', '演示/投影/无障碍', '{"fontSizeXs":"0.875rem","fontSizeSm":"1rem","fontSizeBase":"1.125rem","fontSizeLg":"1.25rem","fontSizeXl":"1.5rem","fontSize2xl":"1.75rem","fontSize3xl":"2rem"}', 0, 4, 'ACTIVE')
ON DUPLICATE KEY UPDATE
    preset_name = VALUES(preset_name),
    preset_description = VALUES(preset_description),
    config_json = VALUES(config_json),
    is_default = VALUES(is_default),
    sort_order = VALUES(sort_order),
    status = VALUES(status);