-- Personal profile management: session devices, login history and user preferences.

SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'refresh_token'
      AND column_name = 'device_name'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE refresh_token ADD COLUMN device_name VARCHAR(100) AFTER username',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'refresh_token'
      AND column_name = 'ip_address'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE refresh_token ADD COLUMN ip_address VARCHAR(50) AFTER device_name',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'refresh_token'
      AND column_name = 'user_agent'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE refresh_token ADD COLUMN user_agent VARCHAR(500) AFTER ip_address',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @column_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'refresh_token'
      AND column_name = 'last_used_time'
);
SET @ddl := IF(@column_exists = 0,
    'ALTER TABLE refresh_token ADD COLUMN last_used_time DATETIME AFTER created_time',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'refresh_token'
      AND index_name = 'idx_refresh_token_user_revoked'
);
SET @ddl := IF(@index_exists = 0,
    'CREATE INDEX idx_refresh_token_user_revoked ON refresh_token(user_id, revoked)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @index_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'refresh_token'
      AND index_name = 'idx_refresh_token_expiry'
);
SET @ddl := IF(@index_exists = 0,
    'CREATE INDEX idx_refresh_token_expiry ON refresh_token(expiry_date)',
    'SELECT 1'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS sys_login_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    username VARCHAR(100),
    login_time DATETIME NOT NULL,
    ip_address VARCHAR(50),
    user_agent VARCHAR(500),
    result VARCHAR(20) NOT NULL,
    failure_reason VARCHAR(500),
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    INDEX idx_login_history_user_time (user_id, login_time),
    INDEX idx_login_history_username_time (username, login_time),
    INDEX idx_login_history_result (result)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sys_user_preference (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    table_density VARCHAR(20) NOT NULL DEFAULT 'DEFAULT',
    default_home_path VARCHAR(200) NOT NULL DEFAULT '/home',
    theme_mode VARCHAR(20) NOT NULL DEFAULT 'SYSTEM',
    page_size INT NOT NULL DEFAULT 20,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_user_preference_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sys_dict (category, dict_key, dict_value, sort_order, status, remark, created_time, updated_time)
SELECT category, dict_key, dict_value, sort_order, 'ACTIVE', remark, NOW(), NOW()
FROM (
    SELECT 'profile_table_density' category, 'COMPACT' dict_key, '紧凑' dict_value, 1 sort_order, '个人表格密度' remark
    UNION ALL SELECT 'profile_table_density', 'DEFAULT', '默认', 2, '个人表格密度'
    UNION ALL SELECT 'profile_table_density', 'COMFORTABLE', '宽松', 3, '个人表格密度'
    UNION ALL SELECT 'profile_theme_mode', 'SYSTEM', '跟随系统', 1, '个人主题模式'
    UNION ALL SELECT 'profile_theme_mode', 'LIGHT', '浅色', 2, '个人主题模式'
    UNION ALL SELECT 'profile_theme_mode', 'DARK', '深色', 3, '个人主题模式'
    UNION ALL SELECT 'login_result', 'SUCCESS', '成功', 1, '登录结果'
    UNION ALL SELECT 'login_result', 'FAILED', '失败', 2, '登录结果'
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM sys_dict d
    WHERE d.category = seed.category AND d.dict_key = seed.dict_key
);
