CREATE TABLE IF NOT EXISTS notification_mini_program_eligibility (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '小程序订阅用户资格快照ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    row_status VARCHAR(20) NOT NULL COMMENT '聚合行状态',
    openid_bound BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否绑定小程序openid',
    configured_template_count INT NOT NULL DEFAULT 0 COMMENT '已配置模板数',
    authorized_template_count INT NOT NULL DEFAULT 0 COMMENT '当前可用授权模板数',
    available_total INT NOT NULL DEFAULT 0 COMMENT '当前可用授权总次数',
    last_authorized_time DATETIME COMMENT '最近授权时间',
    config_fingerprint VARCHAR(64) NOT NULL COMMENT '模板配置指纹',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT uk_notification_mini_eligibility_user UNIQUE (user_id),
    INDEX idx_notification_mini_eligibility_status_user (row_status, user_id),
    INDEX idx_notification_mini_eligibility_authorized (last_authorized_time),
    CONSTRAINT fk_notification_mini_eligibility_user FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小程序订阅用户资格查询快照';
