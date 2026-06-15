---
name: db-migration
description: 创建 Flyway 数据库迁移脚本
disable-model-invocation: true
---

# 数据库迁移脚本生成

创建符合 Flyway 规范的数据库迁移脚本。

## 使用方式

```
/db-migration <变更描述>
```

示例：
```
/db-migration 添加用户头像字段
/db-migration 新增订单表
```

## 执行步骤

1. 查询现有迁移脚本版本号（`backend/src/main/resources/db/migration/`）
2. 生成下一个版本号（V{N+1}__{description}.sql）
3. 根据变更描述生成 SQL 脚本
4. 包含幂等性处理（IF NOT EXISTS 等）

## 命名规范

- 版本号：V9__add_user_avatar.sql
- 格式：V{版本号}__{下划线分隔的描述}.sql
- 版本号必须连续递增

## 脚本模板

```sql
-- V{N}: {变更描述}
-- 创建/修改表说明

CREATE TABLE IF NOT EXISTS table_name (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    -- 字段定义
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='表说明';

-- 幂等插入
INSERT INTO table_name (columns) VALUES (values)
ON DUPLICATE KEY UPDATE column = VALUES(column);
```

## 参考文件

- `backend/src/main/resources/db/migration/` — 现有迁移脚本
- `backend/src/main/resources/init.sql` — 初始化脚本
- `docs/dev/design/database.md` — 数据库设计（v2.0）
