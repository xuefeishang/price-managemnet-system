-- 添加 sys_role 表的 dept_id 列（所属部门）
-- 用于支持部门级角色

-- 添加 dept_id 列
ALTER TABLE sys_role ADD COLUMN dept_id BIGINT COMMENT '所属部门（NULL表示全局角色）' AFTER description;

-- 添加索引
ALTER TABLE sys_role ADD INDEX idx_role_dept (dept_id);