-- V8: 样式版本历史表
-- 创建 sys_style_version 表，用于存储样式配置版本快照，支持回滚功能

CREATE TABLE IF NOT EXISTS sys_style_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '版本ID',
    version_no VARCHAR(50) NOT NULL UNIQUE COMMENT '版本号，格式 vyyyyMMdd_HHmmss',
    config_snapshot TEXT NOT NULL COMMENT '配置快照JSON（完整StyleConfigDTO）',
    change_summary VARCHAR(500) COMMENT '变更说明',
    changed_by BIGINT COMMENT '变更人用户ID',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    INDEX idx_style_version_created_time (created_time),
    INDEX idx_style_version_changed_by (changed_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='样式版本历史表';