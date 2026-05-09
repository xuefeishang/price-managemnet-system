-- =====================================================
-- 价格管理系统 - 数据库列修复脚本
-- 用于修复 Entity 与数据库表结构的列不一致问题
-- 运行前请备份数据库！
-- =====================================================

USE price_management;

-- =====================================================
-- 1. 为 product 表添加缺失列
-- =====================================================

-- 添加 code 列（如果不存在）
ALTER TABLE product ADD COLUMN IF NOT EXISTS code VARCHAR(100) COMMENT '产品编码';
ALTER TABLE product ADD INDEX IF NOT EXISTS idx_product_code (code);

-- 添加 show_on_home 列（如果不存在）
ALTER TABLE product ADD COLUMN IF NOT EXISTS show_on_home BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否在首页展示';

-- 添加 currency 列（如果不存在）
ALTER TABLE product ADD COLUMN IF NOT EXISTS currency VARCHAR(20) DEFAULT 'CNY' COMMENT '计价币种：CNY-人民币、USD-美元、EUR-欧元';

-- 添加 version 列（如果不存在，用于乐观锁）
ALTER TABLE product ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 COMMENT '乐观锁版本号';

-- =====================================================
-- 2. 为 price 表添加缺失列
-- =====================================================

-- 添加 version 列（如果不存在，用于乐观锁）
ALTER TABLE price ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0 COMMENT '乐观锁版本号';

-- =====================================================
-- 3. 为 price_history 表添加缺失列
-- =====================================================

-- 添加 changed_by 列（如果不存在）
ALTER TABLE price_history ADD COLUMN IF NOT EXISTS changed_by BIGINT COMMENT '变更操作人ID';
ALTER TABLE price_history ADD INDEX IF NOT EXISTS idx_history_changed_by (changed_by);

-- =====================================================
-- 验证修复结果
-- =====================================================

SELECT '修复完成！' AS message;

-- 验证 product 表结构
SELECT 'product 表结构:' AS '';
DESCRIBE product;

-- 验证 price 表结构
SELECT 'price 表结构:' AS '';
DESCRIBE price;

-- 验证 price_history 表结构
SELECT 'price_history 表结构:' AS '';
DESCRIBE price_history;
