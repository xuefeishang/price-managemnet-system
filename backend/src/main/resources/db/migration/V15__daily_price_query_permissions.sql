-- Daily price query page menu and export permission.

INSERT INTO menu_item (parent_id, name, path, icon, sort_order, visible, roles, created_time, updated_time)
SELECT product_menu.id, '价格查询', '/price-query', 'price', 3, TRUE, '["ADMIN","EDITOR","VIEWER"]', NOW(), NOW()
FROM menu_item product_menu
WHERE product_menu.parent_id IS NULL
  AND product_menu.name = '产品管理'
  AND NOT EXISTS (SELECT 1 FROM menu_item existing WHERE existing.path = '/price-query');

UPDATE menu_item
SET visible = TRUE,
    roles = '["ADMIN","EDITOR","VIEWER"]',
    updated_time = NOW()
WHERE path = '/price-query';

INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, resource_url, sort_order, status, created_time, updated_time)
SELECT 'price:export', '价格导出', 'BUTTON', p.id, NULL, 23, 'ACTIVE', NOW(), NOW()
FROM sys_permission p
WHERE p.permission_code = 'price:view'
  AND NOT EXISTS (SELECT 1 FROM sys_permission existing WHERE existing.permission_code = 'price:export');

UPDATE sys_permission
SET permission_name = '价格导出',
    permission_type = 'BUTTON',
    parent_id = (SELECT id FROM (SELECT id FROM sys_permission WHERE permission_code = 'price:view') AS price_view),
    sort_order = 23,
    status = 'ACTIVE',
    updated_time = NOW()
WHERE permission_code = 'price:export';

INSERT INTO sys_role_permission (role_id, permission_id, created_time)
SELECT r.id, p.id, NOW()
FROM sys_role r
JOIN sys_permission p ON p.permission_code = 'price:export'
WHERE r.role_code IN ('ADMIN', 'EDITOR', 'VIEWER')
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_permission rp
      WHERE rp.role_id = r.id
        AND rp.permission_id = p.id
  );
