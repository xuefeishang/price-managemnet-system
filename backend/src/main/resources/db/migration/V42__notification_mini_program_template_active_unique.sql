UPDATE notification_mini_program_template template
JOIN (
    SELECT id
    FROM (
        SELECT
            id,
            ROW_NUMBER() OVER (
                PARTITION BY notification_type
                ORDER BY COALESCE(published_time, updated_time, created_time) DESC, id DESC
            ) AS active_rank
        FROM notification_mini_program_template
        WHERE status = 'ACTIVE'
    ) ranked_active
    WHERE active_rank > 1
) duplicate_active
    ON duplicate_active.id = template.id
SET template.status = 'DISABLED',
    template.updated_time = NOW();

ALTER TABLE notification_mini_program_template
    ADD COLUMN active_notification_type VARCHAR(50)
        GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN notification_type ELSE NULL END) STORED
        COMMENT '生效模板唯一约束辅助列';

CREATE UNIQUE INDEX uk_notification_mini_template_active_type
    ON notification_mini_program_template (active_notification_type);
