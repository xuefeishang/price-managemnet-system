CREATE TABLE IF NOT EXISTS product_annual_budget (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    budget_year INT NOT NULL,
    budget_price DECIMAL(15, 4),
    created_by BIGINT,
    updated_by BIGINT,
    remark VARCHAR(500),
    version BIGINT NOT NULL DEFAULT 0,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_product_annual_budget_product FOREIGN KEY (product_id) REFERENCES product(id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT uk_product_budget_year UNIQUE (product_id, budget_year),
    INDEX idx_product_annual_budget_year (budget_year),
    INDEX idx_product_annual_budget_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品年度预算表';

INSERT INTO product_annual_budget (
    product_id,
    budget_year,
    budget_price,
    created_time,
    updated_time
)
SELECT p.id,
       YEAR(CURDATE()),
       p.budget_price,
       NOW(),
       NOW()
FROM product p
WHERE p.budget_price IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM product_annual_budget b
      WHERE b.product_id = p.id
        AND b.budget_year = YEAR(CURDATE())
  );

INSERT INTO menu_item (parent_id, name, path, icon, sort_order, visible, roles, created_time, updated_time)
SELECT product_root.id,
       '预算管理',
       '/budget-management',
       'price',
       3,
       TRUE,
       '["ADMIN","EDITOR"]',
       NOW(),
       NOW()
FROM (
    SELECT id FROM menu_item WHERE parent_id IS NULL AND name = '产品管理' ORDER BY id LIMIT 1
) product_root
WHERE NOT EXISTS (
    SELECT 1 FROM menu_item WHERE path = '/budget-management'
);

UPDATE menu_item
SET sort_order = 4
WHERE path = '/price-query'
  AND sort_order < 4;
