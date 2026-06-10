ALTER TABLE notification_message
    ADD COLUMN event_count BIGINT NOT NULL DEFAULT 1 COMMENT '聚合消息包含的事件数量' AFTER expire_time;
