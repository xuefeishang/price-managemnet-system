# 00b. MySQL 基础：30 分钟速成 SQL

> 本项目用 JPA 帮你写 SQL，但你至少要**看懂 SQL**，否则 Flyway 迁移脚本无从下手。

---

## 一、数据库是什么？

**数据库 = 存数据的"高级 Excel 表"**。

| 类比 | Excel | MySQL |
|------|-------|-------|
| 一组数据 | 文件 (.xlsx) | 数据库 (price_management) |
| 一张表 | Sheet (产品表) | Table (product) |
| 一行 | Row | Row |
| 一列 | Column (产品名) | Column (product_name) |

**MySQL 是什么？**

MySQL 是一个**关系数据库**——数据按"行和列"组织，表和表之间能建立"关系"。

## 二、连接到 MySQL

用命令行的方式（装好 MySQL 后）：

```bash
mysql -u root -p
# 输入密码
```

看到 `mysql>` 提示符就算成功了。

**几个常用命令**：

```sql
SHOW DATABASES;              -- 看所有数据库
USE price_management;        -- 切换到本项目的数据库
SHOW TABLES;                 -- 看所有表
DESC product;                -- 看 product 表的结构
SELECT * FROM product LIMIT 5;  -- 看前 5 行数据
```

## 三、SQL 四大天王

SQL = Structured Query Language，结构化查询语言。分 4 类：

| 类型 | 全称 | 干啥 | 关键字 |
|------|------|------|--------|
| **DDL** | Data Definition Language | 定义表结构 | CREATE / ALTER / DROP |
| **DML** | Data Manipulation Language | 增删改数据 | INSERT / UPDATE / DELETE |
| **DQL** | Data Query Language | 查数据 | SELECT |
| **DCL** | Data Control Language | 权限控制 | GRANT / REVOKE |

小白阶段掌握 DDL + DML + DQL 就够。

## 四、创建表（DDL）

### 4.1 看一个真实的表

打开 `backend/src/main/resources/db/migration/`，找一个建表 SQL。比如 `V1__init.sql` 之类。

```sql
CREATE TABLE product (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_name    VARCHAR(100) NOT NULL,
    category_id     BIGINT,
    price           DECIMAL(18,4),
    status          VARCHAR(20),
    remark          VARCHAR(500),
    created_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_time    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_category (category_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品表';
```

### 4.2 字段类型速查

| 类型 | 用途 | 例子 |
|------|------|------|
| `BIGINT` | 长整数（ID） | 1, 2, 10000000000 |
| `INT` | 整数 | 1, 100, 1000 |
| `VARCHAR(n)` | 可变长字符串 | '铜精粉'（最大 n 个字符） |
| `TEXT` | 长文本 | 备注、描述 |
| `DECIMAL(p, s)` | 高精度小数 | DECIMAL(18, 4) 共 18 位、小数 4 位 |
| `DATETIME` | 日期时间 | '2026-06-28 21:00:00' |
| `DATE` | 日期 | '2026-06-28' |
| `BOOLEAN` / `TINYINT(1)` | 布尔 | true / false |

### 4.3 约束

```sql
CREATE TABLE user (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,    -- 主键：唯一标识一行
    username VARCHAR(50) NOT NULL UNIQUE,        -- 非空 + 唯一
    age     INT DEFAULT 0,                       -- 默认值 0
    email   VARCHAR(100),
    dept_id BIGINT,
    
    FOREIGN KEY (dept_id) REFERENCES department(id),  -- 外键：关联另一张表
    INDEX idx_username (username)                -- 索引：加快查询
);
```

**关键约束**：

| 约束 | 作用 | 例子 |
|------|------|------|
| `PRIMARY KEY` | 主键，唯一且非空 | id |
| `NOT NULL` | 不能为空 | 用户名 |
| `UNIQUE` | 不能重复 | 邮箱 |
| `DEFAULT` | 默认值 | 创建时间 |
| `FOREIGN KEY` | 外键，关联另一张表 | 部门 ID |
| `CHECK` | 自定义检查 | 年龄 >= 0 |
| `AUTO_INCREMENT` | 自增（MySQL 特有） | id |

**主键 vs 外键**：

```
本项目的 product 表
┌────────────────────────────────────┐
│ id  │ product_name │ category_id   │ ← category_id 是外键
│ 1   │ 铜精粉       │ 100           │    指向 category 表的 100
│ 2   │ 铅精粉       │ 101           │
└────────────────────────────────────┘

category 表
┌────────────────────────────────┐
│ id  │ category_name            │ ← 这里的 id 被 product 引用
│ 100 │ 有色金属                  │
│ 101 │ 黑色金属                  │
└────────────────────────────────┘
```

**外键的好处**：保证数据一致性（不能引用不存在的分类）。

**本项目实践**：早期用了外键约束，后期为性能考虑**部分表去掉了外键**，靠业务代码保证一致性。

### 4.4 索引

**索引 = 书的目录**，让查询变快。

```sql
-- 没索引：扫整张表找 name='铜精粉' 的行（慢）
-- 有索引：直接定位（快）

CREATE INDEX idx_product_name ON product(product_name);

-- 组合索引：适合多字段联合查询
CREATE INDEX idx_status_created ON product(status, created_time);
```

**本项目实践**：所有 `xxx_id` 外键字段、`status` 状态字段、`created_time` 时间字段都建了索引。

## 五、增删改查（DML + DQL）

### 5.1 INSERT：插入数据

```sql
-- 单行插入
INSERT INTO product (product_name, category_id, price, status)
VALUES ('铜精粉', 100, 5800.00, 'ACTIVE');

-- 多行插入
INSERT INTO product (product_name, price, status) VALUES
  ('铜精粉', 5800.00, 'ACTIVE'),
  ('铅精粉', 1580.00, 'ACTIVE'),
  ('锌精粉', 2380.00, 'DISABLED');

-- 本项目实践：实际开发中很少手写 INSERT，都是程序通过 JPA 插入
```

### 5.2 SELECT：查询数据（最重要）

**最简单的查询**：

```sql
SELECT * FROM product;                       -- 查所有列
SELECT id, product_name, price FROM product; -- 只查需要的列
```

**WHERE：条件过滤**：

```sql
-- 等于
SELECT * FROM product WHERE status = 'ACTIVE';

-- 不等于
SELECT * FROM product WHERE status != 'DISABLED';

-- 大于小于
SELECT * FROM product WHERE price > 5000;

-- 多个条件
SELECT * FROM product WHERE status = 'ACTIVE' AND price > 5000;

-- 模糊查询：name 含"铜"
SELECT * FROM product WHERE product_name LIKE '%铜%';
--   % 表示任意字符（0 个或多个）
--   _ 表示单个字符

-- IN：匹配列表
SELECT * FROM product WHERE category_id IN (100, 101, 102);

-- BETWEEN：区间
SELECT * FROM product WHERE price BETWEEN 1000 AND 10000;

-- IS NULL：判断空
SELECT * FROM product WHERE remark IS NULL;
```

**ORDER BY：排序**：

```sql
SELECT * FROM product ORDER BY price DESC;          -- 价格从高到低
SELECT * FROM product ORDER BY created_time DESC;   -- 最新的在前
SELECT * FROM product ORDER BY price ASC, created_time DESC;  -- 多字段排序
```

**LIMIT：限制条数（分页）**：

```sql
SELECT * FROM product LIMIT 10;                       -- 前 10 条
SELECT * FROM product LIMIT 10 OFFSET 20;             -- 跳过 20 条，取 10 条
-- 等价于：跳过 20 条，取第 21-30 条（第 3 页，每页 10 条）
```

**聚合函数**：

```sql
SELECT COUNT(*) FROM product;                         -- 总数
SELECT COUNT(*) FROM product WHERE status = 'ACTIVE'; -- 启用的产品数
SELECT AVG(price) FROM product;                       -- 平均价格
SELECT MAX(price), MIN(price) FROM product;           -- 最高/最低
SELECT SUM(price * stock) FROM product;               -- 总价值
```

**GROUP BY：分组**：

```sql
-- 按分类统计产品数量
SELECT category_id, COUNT(*) AS cnt, AVG(price) AS avg_price
FROM product
WHERE status = 'ACTIVE'
GROUP BY category_id
HAVING cnt > 5                          -- HAVING：分组后的过滤
ORDER BY cnt DESC;
```

**DISTINCT：去重**：

```sql
SELECT DISTINCT category_id FROM product;  -- 有哪些分类被产品用过
```

### 5.3 UPDATE：更新数据

```sql
-- ⚠️ 危险：没有 WHERE 子句会更新整张表！
UPDATE product SET price = 6000;                  -- ❌ 把所有产品都改成 6000
UPDATE product SET price = 6000 WHERE id = 1;     -- ✅ 只改 id=1 的

-- 多个字段
UPDATE product
SET price = 6000, status = 'DISABLED', remark = '已停产'
WHERE id = 1;

-- 配合其他字段
UPDATE product
SET price = price * 1.1     -- 所有启用产品价格涨 10%
WHERE status = 'ACTIVE';
```

### 5.4 DELETE：删除数据

```sql
-- ⚠️ 危险：没有 WHERE 子句会删整张表！
DELETE FROM product;                    -- ❌ 全部删光
DELETE FROM product WHERE id = 1;       -- ✅ 删 id=1 的

-- 软删除（本项目实践）：实际不删数据，只改状态
UPDATE product SET status = 'DELETED' WHERE id = 1;
-- 业务上查不到，但数据留着可追溯
```

## 六、表连接（JOIN）

**为什么需要 JOIN？**

数据存在多张表里，JOIN 把它们拼起来查。

```sql
-- 本项目常见场景：查产品时要看分类名
SELECT p.id, p.product_name, p.price, c.category_name
FROM product p
INNER JOIN category c ON p.category_id = c.id
WHERE p.status = 'ACTIVE';
```

### 6.1 JOIN 类型图解

```
A 表        B 表
┌───┐      ┌───┐
│ 1 │      │ 1 │
│ 2 │  ┌───┤ 2 │
│ 3 │  │ 3 │ 3 │
│ 4 │  │   │ 4 │
└───┘  │   └───┘
       │
INNER JOIN：返回交集（A ∩ B）       → 1, 2, 3
LEFT JOIN：返回 A 全部 + 交集        → 1, 2, 3, 4（4 对应 B 是 NULL）
RIGHT JOIN：返回 B 全部 + 交集       → 1, 2, 3, 4
FULL JOIN：返回并集（MySQL 不直接支持）
```

### 6.2 本项目典型 JOIN

```sql
-- 产品 + 分类 + 创建人
SELECT
    p.id, p.product_name, p.price,
    c.category_name,
    u.username AS created_by_name
FROM product p
LEFT JOIN category c ON p.category_id = c.id
LEFT JOIN user u ON p.created_by = u.id
WHERE p.status = 'ACTIVE';
```

## 七、本项目 Flyway 迁移文件示例

打开 `backend/src/main/resources/db/migration/V*.sql`，你大概率会看到三种 SQL：

### 7.1 初始化建表

```sql
-- V1__init.sql
CREATE TABLE product (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_name    VARCHAR(100) NOT NULL,
    -- ...
);

CREATE TABLE category (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_name   VARCHAR(50) NOT NULL,
    -- ...
);
```

### 7.2 加字段

```sql
-- V13__add_remark.sql
ALTER TABLE product ADD COLUMN remark VARCHAR(500);
```

### 7.3 加索引

```sql
-- V14__add_index.sql
CREATE INDEX idx_product_status ON product(status);
```

**注意命名**：V 后面的数字必须**单调递增**，不能跳号也不能重复。可以用日期或顺序号。

## 八、动手试试

### 实验 1：看本项目的表

```bash
mysql -u root -p
USE price_management;
SHOW TABLES;
```

数一数有多少张表。挑 3 张表 `DESC` 看结构。

### 实验 2：基本查询练习

```sql
-- 1. 查所有产品价格
SELECT * FROM product;

-- 2. 查启用的产品
SELECT * FROM product WHERE status = 'ACTIVE';

-- 3. 查价格 > 5000 的，按价格降序
SELECT * FROM product WHERE price > 5000 ORDER BY price DESC;

-- 4. 每个分类有多少产品
SELECT category_id, COUNT(*) FROM product GROUP BY category_id;

-- 5. 产品 + 分类名
SELECT p.product_name, c.category_name, p.price
FROM product p
LEFT JOIN category c ON p.category_id = c.id;
```

### 实验 3：读懂 Flyway 脚本

打开最新的 V\* 脚本，对照实体类 `Product.java`，找出：
- 哪些数据库字段对应 `Product` 的哪些属性？
- 索引在哪个字段上？
- 哪些字段是 `NOT NULL`？

## 九、推荐资源

| 资源 | 类型 | 链接 |
|------|------|------|
| **SQL Tutorial** | 在线教程 | https://www.w3schools.com/sql/ |
| **SQLZoo** | 互动练习 | https://sqlzoo.net/ |
| **MySQL 官方文档** | 文档 | https://dev.mysql.com/doc/ |
| **菜鸟教程 SQL** | 中文教程 | https://www.runoob.com/sql/sql-tutorial.html |

## 十、常见错误

| 错误 | 原因 |
|------|------|
| `You have an error in your SQL syntax` | SQL 语法错，检查引号、分号、关键字 |
| `Unknown column 'xxx'` | 字段名写错 |
| `Table 'xxx' doesn't exist` | 表名写错，或没选数据库 |
| `Duplicate entry 'xxx' for key 'PRIMARY'` | 主键冲突，重复插入 |
| `Cannot delete or update a parent row` | 外键约束阻止删除（先删子表） |
| `Data too long for column 'xxx'` | 字段长度不够 |

---

下一步：[00c Java 语法入门](00c-java-syntax.md) →

回头补课：[00 环境搭建](00-prepare.md)