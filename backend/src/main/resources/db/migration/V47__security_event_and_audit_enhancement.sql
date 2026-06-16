-- =====================================================
-- V47: Security event and audit enhancement
-- Adds security event storage and IP blacklist tables.
-- Existing business tables remain backward compatible.
-- =====================================================

CREATE TABLE IF NOT EXISTS security_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(50) NOT NULL COMMENT 'Event type: ATTACK_SIGNATURE_BLOCKED, AUTH_LOGIN_FAILED, RATE_LIMITED, PERMISSION_DENIED, SUSPICIOUS_REQUEST, SERVER_ERROR',
    severity VARCHAR(20) NOT NULL DEFAULT 'INFO' COMMENT 'Severity: INFO, WARN, ERROR, CRITICAL',
    source_ip VARCHAR(45) COMMENT 'Source IP, IPv4 or IPv6',
    user_agent VARCHAR(500) COMMENT 'User-Agent',
    request_method VARCHAR(10) COMMENT 'HTTP method',
    request_uri VARCHAR(500) COMMENT 'Request URI',
    request_params TEXT COMMENT 'Masked request parameters or summary',
    status_code INT COMMENT 'HTTP status code',
    description VARCHAR(1000) COMMENT 'Masked event description',
    user_id BIGINT COMMENT 'Related user ID when authenticated',
    username VARCHAR(100) COMMENT 'Related username when authenticated',
    action_taken VARCHAR(200) COMMENT 'Action already taken',
    event_count BIGINT NOT NULL DEFAULT 1 COMMENT 'Aggregated count for repeated events',
    resolved BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Whether the event has been handled',
    resolved_by BIGINT COMMENT 'Resolver user ID',
    resolved_at DATETIME COMMENT 'Resolution time',
    resolution_note VARCHAR(500) COMMENT 'Resolution note',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_security_event_type_time (event_type, created_time),
    INDEX idx_security_event_ip_time (source_ip, created_time),
    INDEX idx_security_event_severity_resolved (severity, resolved),
    INDEX idx_security_event_created_time (created_time),
    INDEX idx_security_event_resolved_time (resolved, created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Security event audit table';

CREATE TABLE IF NOT EXISTS ip_blacklist (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ip_address VARCHAR(45) NOT NULL COMMENT 'Blocked IP address',
    reason VARCHAR(200) NOT NULL COMMENT 'Block reason',
    banned_by VARCHAR(30) NOT NULL COMMENT 'Source: AUTO_FAIL2BAN, AUTO_NGINX, MANUAL_ADMIN',
    banned_at DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expires_at DATETIME COMMENT 'Expiration time, NULL means no scheduled expiry',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Whether the block is active',
    banned_by_user_id BIGINT COMMENT 'Admin user ID for manual block',
    unban_at DATETIME COMMENT 'Unblock time',
    unban_by_user_id BIGINT COMMENT 'Admin user ID for manual unblock',
    unban_reason VARCHAR(200) COMMENT 'Unblock reason',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ip_blacklist_active (ip_address, is_active),
    INDEX idx_ip_blacklist_active_expires (is_active, expires_at),
    INDEX idx_ip_blacklist_ip (ip_address),
    INDEX idx_ip_blacklist_banned_at (banned_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IP blacklist table';

ALTER TABLE operation_log
    ADD COLUMN risk_score INT NOT NULL DEFAULT 0 COMMENT 'Risk score from 0 to 100',
    ADD COLUMN security_event_id BIGINT COMMENT 'Related security event ID',
    ADD INDEX idx_operation_risk_score (risk_score),
    ADD INDEX idx_operation_security_event (security_event_id);
