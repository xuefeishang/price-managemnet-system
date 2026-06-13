DELETE FROM sys_dict
WHERE category = 'chart_range'
  AND dict_key IN ('7d', '90d');

INSERT INTO sys_dict (
    category, dict_key, dict_value, extra_value, sort_order, status, remark, created_time, updated_time
)
VALUES
    ('chart_range', '30d', '30天', '30', 1, 'ACTIVE', '30天趋势', NOW(), NOW()),
    ('chart_range', '180d', '180天', '180', 2, 'ACTIVE', '180天趋势', NOW(), NOW()),
    ('chart_range', '1y', '12个月', '365', 3, 'ACTIVE', '近12个月趋势', NOW(), NOW())
ON DUPLICATE KEY UPDATE
    dict_value = VALUES(dict_value),
    extra_value = VALUES(extra_value),
    sort_order = VALUES(sort_order),
    status = VALUES(status),
    remark = VALUES(remark),
    updated_time = NOW();
