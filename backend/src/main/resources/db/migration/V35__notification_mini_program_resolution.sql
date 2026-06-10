CREATE TABLE IF NOT EXISTS notification_mini_program_resolution (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '订阅异常处理ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    resolve_status VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT '处理状态',
    resolve_remark VARCHAR(500) COMMENT '处理备注',
    remind_after DATETIME COMMENT '暂不提醒截止时间',
    follow_up_required BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否需要跟进',
    resolved_by BIGINT COMMENT '处理人',
    resolved_time DATETIME COMMENT '处理时间',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    CONSTRAINT uk_notification_mini_resolution_user UNIQUE (user_id),
    INDEX idx_notification_mini_resolution_status (resolve_status),
    CONSTRAINT fk_notification_mini_resolution_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小程序订阅异常处理记录';
