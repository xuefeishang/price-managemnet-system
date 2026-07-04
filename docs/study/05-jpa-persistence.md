# 05. JPA 与数据持久化

> 数据怎么存进数据库、怎么取出来，这一章讲透。

---

## 一、什么是"持久化"？

**持久化 = 把内存里的数据存到能长期保存的地方**（如硬盘、数据库）。

Java 后台开发最常用的两种方式：

| 方式 | 特点 | 本项目用吗 |
|------|------|-----------|
| **JDBC** | 写 SQL，手动管理连接 | ❌ 太啰嗦 |
| **MyBatis** | 写 SQL（XML 或注解） | ❌ |
| **JPA / Hibernate** | 用对象操作数据库 | ✅ 本项目用的 |

**JPA 是什么？**

JPA = Java Persistence API，**Java 官方定义的"用对象操作数据库"的规范**。它本身只是一套接口，**真正干活的是 Hibernate**（JPA 最流行的实现）。

```
JPA（接口/规范）       ← Java EE 标准
  └─ Hibernate（实现） ← JBoss 出品，最成熟
       └─ Spring Data JPA ← Spring 在 Hibernate 上又包了一层
            └─ 本项目用的就是 Spring Data JPA
```

## 二、JPA 的核心思想：ORM

**ORM = Object-Relational Mapping = 对象关系映射**。

把数据库表 → Java 类、字段 → 列、行 → 对象：

```
┌────────────────────┐       ┌─────────────────┐
│ 数据库表 product    │       │ Java 类 Product  │
├────────────────────┤       ├─────────────────┤
│ id (BIGINT)         │ ←──→  │ Long id          │
│ product_name        │ ←──→  │ String name      │
│ price (DECIMAL)     │ ←──→  │ BigDecimal price │
│ created_time        │ ←──→  │ LocalDateTime    │
└────────────────────┘       └─────────────────┘
```

开发者**面向对象编程**，不用关心 SQL 怎么写。

## 三、Entity 注解详解

```java
@Entity                                          // 标记为 JPA 实体
@Table(name = "product", indexes = {             // 对应表名、索引
    @Index(name = "idx_category", columnList = "category_id")
})
@Data
@NoArgsConstructor
public class Product {
    @Id                                            // 主键
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // 数据库自增
    private Long id;

    @Column(name = "product_name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)                   // 枚举存字符串
    @Column(name = "status", length = 20)
    private ProductStatus status;

    @Column(name = "price", precision = 18, scale = 4)
    private BigDecimal price;

    @CreationTimestamp                             // 自动填充创建时间
    @Column(name = "created_time", updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp                               // 自动填充更新时间
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    @Version                                       // 乐观锁
    @Column(name = "version")
    private Long version;

    // 关联关系
    @ManyToOne(fetch = FetchType.LAZY)             // 懒加载
    @JoinColumn(name = "category_id")
    private Category category;
}
```

### 3.1 主键策略

| 策略 | 说明 | 适用 |
|------|------|------|
| `IDENTITY` | 数据库自增（MySQL AUTO_INCREMENT） | MySQL 单库 |
| `SEQUENCE` | 数据库序列 | Oracle / PostgreSQL |
| `TABLE` | 单独一张序列表 | 跨数据库 |
| `UUID` | UUID 字符串 | 分布式系统 |
| `AUTO` | JPA 自己选 | 不推荐 |

本项目用 `IDENTITY`，简单直接。

### 3.2 字段映射细节

```java
@Column(
    name = "product_name",      // 数据库列名
    nullable = false,           // 不能为 NULL
    length = 100,               // 字符串长度
    unique = true,              // 唯一
    precision = 18,             // BigDecimal 总位数
    scale = 4                   // BigDecimal 小数位数
)
```

### 3.3 枚举的坑

```java
// ❌ 错误：ORDINAL 存的是 0, 1, 2...
@Enumerated(EnumType.ORDINAL)
private Status status;
// 如果将来在枚举中间加一个值，所有旧数据的"含义"都会错位！

// ✅ 正确：STRING 存的是 "ACTIVE", "DISABLED"
@Enumerated(EnumType.STRING)
private Status status;
```

**本项目所有枚举都是 STRING**。

### 3.4 时间字段

```java
@CreationTimestamp    // INSERT 时自动写入
@UpdateTimestamp      // INSERT 和 UPDATE 时自动更新
```

本项目还要求所有表加 `created_time` / `updated_time` 两个字段（详见 CLAUDE.md）。

### 3.5 乐观锁 @Version

```java
@Version
private Long version;
```

并发场景：A 和 B 同时读 v=1，A 修改后 UPDATE ... WHERE version=1，B 也 UPDATE ... WHERE version=1 → B 影响 0 行，会抛 `OptimisticLockException`。

### 3.6 关联关系（本项目少用）

```java
// 多对一：多个产品属于一个分类
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "category_id")
private Category category;

// 一对多：一个分类有多个产品
@OneToMany(mappedBy = "category")
private List<Product> products;
```

**fetch = LAZY（懒加载）**：用到时才查数据库。

⚠️ **本项目的一个特殊约定**：
> 关联字段只在 Entity 里声明，**返回前端时全部转成 DTO**，避免 Hibernate 懒加载代理序列化失败（项目踩过这个坑，已沉淀为 DTO 模式）。

## 四、Repository 详解

### 4.1 继承 JpaRepository

```java
public interface ProductRepository extends JpaRepository<Product, Long> {
    // Long = 主键类型
}
```

**继承来的免费方法**：

| 方法 | SQL |
|------|-----|
| `save(entity)` | INSERT 或 UPDATE |
| `saveAll(entities)` | 批量保存 |
| `findById(id)` | SELECT WHERE id = ? |
| `findAll()` | SELECT * |
| `findAll(Pageable)` | SELECT ... LIMIT ? OFFSET ? |
| `findAll(Sort)` | SELECT ... ORDER BY ... |
| `count()` | SELECT COUNT(*) |
| `delete(entity)` | DELETE |
| `deleteById(id)` | DELETE WHERE id = ? |
| `existsById(id)` | SELECT EXISTS ... |

### 4.2 方法名查询（最常用）

```java
public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByName(String name);
    List<Product> findByStatus(ProductStatus status);
    List<Product> findByCategoryId(Long categoryId);
    List<Product> findByNameLike(String pattern);
    List<Product> findByPriceGreaterThan(BigDecimal price);
    Page<Product> findByStatus(ProductStatus status, Pageable pageable);
    List<Product> findTop10ByOrderByCreatedTimeDesc();
    long countByStatus(ProductStatus status);
    boolean existsByName(String name);
}
```

**支持的关键词**：`find / read / get / count / exists / delete` + `By + 属性 + 条件`

| 条件关键字 | 例子 |
|------------|------|
| `And` / `Or` | `findByNameAndStatus` |
| `Like` / `Containing` / `StartingWith` | `findByNameLike("%铜%")` |
| `GreaterThan` / `LessThan` / `Between` | `findByPriceGreaterThan` |
| `IsNull` / `IsNotNull` | `findByRemarkIsNull` |
| `In` / `NotIn` | `findByStatusIn(List)` |
| `OrderBy` | `findByStatusOrderByCreatedTimeDesc` |
| `Top` / `First` | `findTop10By...` |
| `Distinct` | `findDistinctByName` |

### 4.3 @Query 自定义查询

```java
@Query("SELECT p FROM Product p WHERE p.status = :status AND p.price > :minPrice")
List<Product> findActiveAndExpensive(@Param("status") ProductStatus status,
                                      @Param("minPrice") BigDecimal minPrice);

@Query("SELECT new com.pricemanagement.dto.ProductSummary(p.id, p.name) " +
       "FROM Product p WHERE p.categoryId = :cid")
List<ProductSummary> findSummaryByCategory(@Param("cid") Long cid);
```

`p.price > :minPrice` 里的 `p` 是 JPQL，**用类名/字段名而不是表名/列名**。

### 4.4 原生 SQL

```java
@Query(value = "SELECT * FROM product WHERE DATE(created_time) = CURDATE()",
       nativeQuery = true)
List<Product> findTodayProducts();
```

`nativeQuery = true` 时写的就是 MySQL SQL，**但失去了跨数据库能力**。

### 4.5 分页和排序

```java
// Service 里
PageRequest pageable = PageRequest.of(0, 20, Sort.by("createdTime").descending());
Page<Product> page = productRepository.findAll(pageable);

// page.getContent()       → 当前页数据
// page.getTotalElements() → 总条数
// page.getTotalPages()    → 总页数
// page.getNumber()        → 当前页（从 0 开始）
// page.getSize()          → 每页大小
```

## 五、Entity 生命周期

Entity 有 4 种状态：

```
         new
          │
          ▼
   ┌────────────┐
   │  Transient │  ← new 出来，没保存过
   └────────────┘
          │ save()
          ▼
   ┌────────────┐
   │  Managed   │  ← 被 EntityManager 管理
   └────────────┘
          │ detach() / clear() / close()
          ▼
   ┌────────────┐
   │ Detached   │  ← 修改不会自动同步到数据库
   └────────────┘
          │ remove()
          ▼
   ┌────────────┐
   │  Removed   │  ← 标记删除，事务提交时真正删除
   └────────────┘
```

**实务中理解 3 件事就够了**：

1. `save()` 新对象 → 走 INSERT
2. `save()` 已存在对象（带 id） → 走 UPDATE（merge）
3. 事务内改 Managed 对象的字段 → 自动 UPDATE（脏检查）

## 六、事务管理

```java
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;

    @Transactional   // 这个方法跑在一个事务里
    public void placeOrder(OrderDTO dto) {
        // 1. 扣库存
        inventoryRepository.decrement(dto.getProductId(), dto.getQty());
        // 2. 下订单
        orderRepository.save(dto.toEntity());
        // 任何一步抛异常，前面全部回滚
    }

    @Transactional(readOnly = true)   // 只读事务，性能更好
    public Order getById(Long id) {
        return orderRepository.findById(id).orElseThrow();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)  // 新开事务，不受外层影响
    public void logOperation() { ... }
}
```

**传播行为（Propagation）速查**：

| 值 | 含义 |
|----|------|
| `REQUIRED`（默认） | 有就用，没有就开 |
| `REQUIRES_NEW` | 一定要新的，挂起外层 |
| `NESTED` | 嵌套，外层回滚它也回滚 |
| `SUPPORTS` | 有就用，没有不开 |
| `NOT_SUPPORTED` | 不用事务 |

**事务失效的常见原因**：

1. ❌ 注解加在 `private` 方法上（不生效）
2. ❌ 方法内部 `this.method()`（绕过了代理）
3. ❌ 异常被 `try-catch` 吞了（不会触发回滚）
4. ❌ 抛的异常不是 RuntimeException（默认只回滚 RuntimeException）
5. ❌ 数据库引擎不支持事务（如 MyISAM）

## 七、Flyway：数据库版本管理

**问题**：项目上线后改了表结构，多套环境（开发、测试、生产）怎么保持一致？同事 A 改了 schema，同事 B 怎么同步？

**Flyway 解决**：把数据库变更写进 SQL 文件，用 Git 管理，自动执行。

```
backend/src/main/resources/db/migration/
├── V1__init.sql              ← 第 1 个版本
├── V2__add_product_table.sql ← 第 2 个版本
├── V3__add_status_index.sql  ← 第 3 个版本
└── ...
```

**命名规则**：`V{版本号}__{描述}.sql`，双下划线分隔。

**执行过程**：

```
启动应用
  → Flyway 检查数据库
  → 发现有 V1, V2, V3 三个文件没执行
  → 按顺序执行
  → 写入 flyway_schema_history 表（记录已执行的版本）
  → 下次启动只执行新文件
```

**本项目配置**（`application.yml`）：

```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    baseline-version: 12
    locations: classpath:db/migration
```

**好处**：

1. 数据库结构跟代码一起进 Git
2. 全新环境一条命令初始化好
3. 谁、什么时候、改了什么，一目了然

## 八、本项目特殊约定：DTO 模式

**为什么返回 DTO 而不是 Entity？**

Hibernate 实体有"懒加载代理"——`Product.category` 可能是个代理对象，只有真的访问它时才查数据库。

如果直接把 Entity 转成 JSON 返回：

```java
@GetMapping("/{id}")
public Product get(@PathVariable Long id) {
    return productRepository.findById(id).orElseThrow();
    // 序列化时访问 category，触发 SQL
    // 但事务已结束，LazyInitializationException
}
```

**解决方案**：全程用 DTO。

```java
@GetMapping("/{id}")
public ProductDTO get(@PathVariable Long id) {
    Product p = productRepository.findById(id).orElseThrow();
    return toDTO(p);    // 在事务内完成转换，关闭 EntityManager
}

private ProductDTO toDTO(Product p) {
    ProductDTO dto = new ProductDTO();
    dto.setId(p.getId());
    dto.setName(p.getName());
    if (p.getCategory() != null) {   // 在事务内访问，安全
        dto.setCategoryName(p.getCategory().getName());
    }
    return dto;
}
```

**转换的几种方式**：

| 方式 | 例子 |
|------|------|
| 手写 | `ProductDTO dto = new ProductDTO(); dto.setId(p.getId()); ...` |
| BeanUtils | `BeanUtils.copyProperties(dto, entity)` |
| MapStruct | 编译期生成映射代码（推荐） |
| Lombok builder | `ProductDTO.builder().id(p.getId()).name(p.getName()).build()` |

## 九、动手试试

### 实验 1：读懂一个 Entity

打开 `entity/Product.java`，对照数据库表：

```sql
-- backend/src/main/resources/db/migration/V*.sql
-- 找到 product 表的定义
```

对比字段名、类型、长度、是否非空。

### 实验 2：写一个复杂查询

在 `ProductRepository` 加：

```java
@Query("SELECT p FROM Product p " +
       "WHERE (:name IS NULL OR p.name LIKE %:name%) " +
       "AND (:status IS NULL OR p.status = :status) " +
       "ORDER BY p.createdTime DESC")
Page<Product> search(@Param("name") String name,
                     @Param("status") ProductStatus status,
                     Pageable pageable);
```

在 Service 里调用，看看分页效果。

### 实验 3：写一个 Flyway 迁移

新增 `V999__add_product_remark.sql`：

```sql
ALTER TABLE product ADD COLUMN remark VARCHAR(500);
```

启动应用，观察 Flyway 执行日志，看 `flyway_schema_history` 表多了一行。

---

## 十、常见错误与解决

| 错误 | 原因 | 解决 |
|------|------|------|
| `LazyInitializationException` | 事务外访问懒加载字段 | 转 DTO 或 @EntityGraph 预加载 |
| `Detached entity passed to persist` | id 已存在又 save | 用 `merge()` 或 `save()`（已处理） |
| `ObjectOptimisticLockingFailureException` | 乐观锁冲突 | 重试或业务提示用户 |
| `could not execute statement` | 字段超长、唯一约束冲突 | 检查数据 |
| `JpaSystemException` | 多种可能 | 看完整堆栈 |

---

下一章：[06 Spring Security 与 JWT](06-security-jwt.md) →

回头补课：[04 项目分层架构](04-layered-architecture.md)