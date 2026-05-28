-- Normalize the daily price query menu so it is managed as one canonical menu item.

SET @product_menu_id := (
    SELECT id
    FROM menu_item
    WHERE parent_id IS NULL
      AND name = '产品管理'
    ORDER BY id
    LIMIT 1
);

SET @canonical_price_query_menu_id := (
    SELECT id
    FROM menu_item
    WHERE path = '/price-query'
    ORDER BY id
    LIMIT 1
);

INSERT INTO menu_item (parent_id, name, path, icon, sort_order, visible, roles, created_time, updated_time)
SELECT @product_menu_id, '价格查询', '/price-query', 'price', 3, TRUE, '["ADMIN","EDITOR","VIEWER"]', NOW(), NOW()
WHERE @product_menu_id IS NOT NULL
  AND @canonical_price_query_menu_id IS NULL;

SET @canonical_price_query_menu_id := (
    SELECT id
    FROM menu_item
    WHERE path = '/price-query'
    ORDER BY id
    LIMIT 1
);

UPDATE menu_item
SET parent_id = @product_menu_id,
    name = '价格查询',
    icon = 'price',
    sort_order = 3,
    visible = TRUE,
    roles = '["ADMIN","EDITOR","VIEWER"]',
    updated_time = NOW()
WHERE id = @canonical_price_query_menu_id
  AND @product_menu_id IS NOT NULL;

DELETE FROM menu_item
WHERE path = '/price-query'
  AND id <> @canonical_price_query_menu_id;
