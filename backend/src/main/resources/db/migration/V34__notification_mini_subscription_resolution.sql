ALTER TABLE notification_mini_program_subscription
    ADD COLUMN resolve_status VARCHAR(20) DEFAULT 'OPEN' COMMENT '异常处理状态',
    ADD COLUMN resolve_remark VARCHAR(500) COMMENT '异常处理备注',
    ADD COLUMN resolved_by BIGINT COMMENT '处理人',
    ADD COLUMN resolved_time DATETIME COMMENT '处理时间';

CREATE INDEX idx_notification_mini_sub_resolve_status
    ON notification_mini_program_subscription (resolve_status);
