-- 修复 sys_style_version.config_snapshot 列类型
-- 背景：历史版本可能包含完整 StyleConfigDTO（含 Logo base64），需要足够容量
-- 当前策略：StyleVersionService.createLightweightSnapshot() 创建轻量快照
--   - 排除 logoUrl/logoUrlLogin/logoUrlNav 原始 base64
--   - 仅保留 assetRefs 引用信息（是否有值、类型、大小估计）
--   - Logo 不随版本回滚（回滚只恢复颜色、字体、布局等配置）
-- 类型选择：LONGTEXT 兼容历史快照和未来复杂配置扩展

ALTER TABLE sys_style_version MODIFY COLUMN config_snapshot LONGTEXT NOT NULL COMMENT '配置快照JSON（轻量化，排除Logo base64）';
