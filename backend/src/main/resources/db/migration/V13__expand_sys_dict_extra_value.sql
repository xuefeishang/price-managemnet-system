-- 扩展 sys_dict.extra_value，支持分类视觉等 JSON 配置
-- 背景：category_visual_config 保存 preset 引用与必要视觉 token，VARCHAR(500) 容量不足会导致保存时报 409/Data truncation。

ALTER TABLE sys_dict
    MODIFY COLUMN extra_value TEXT COMMENT '扩展值（如货币符号、图标名、JSON配置等）';
