# 04. 项目分层架构

> 本章把 `com.pricemanagement` 包下的 8 个子目录**逐个讲清楚**，让你看到任何文件都知道该读哪一层。

---

## 一、为什么要分层？

不分的写法：

```java
// 一切写在一个类里（反面教材）
public class ProductController {
    public List<Product> list() {
        Connection conn = DriverManager.getConnection("jdbc:mysql://...");
        PreparedStatement ps = conn.prepareStatement("SELECT * FROM product");
        // ... 解析、转换、权限校验、缓存、日志 ...
    }
}
```

这种代码活不过一周——改个 SQL 要找半天，测不了，复用不了。

**分层之后**，每一层只关心自己那一段事：

```
Controller  →  "有请求来了，我调谁？"
Service     →  "业务上要做什么？查库？算钱？"
Repository  →  "怎么把对象存到数据库 / 取出来？"
Entity      →  "这条数据长什么样？"
DTO         →  "对外暴露什么字段？"
Config      →  "Bean 怎么装配？拦截器怎么配？"
Util        →  "字符串处理、时间格式化等通用工具"
Annotation  →  "自定义注解"
```

**好处**：改 SQL 不影响业务逻辑、加缓存不改 Controller、换数据库不用改 Service…

## 二、本项目的 8 层全景

```
src/main/java/com/pricemanagement/
├── PriceManagementApplication.java  ← 启动类
│
├── controller/      ① Controller 层：HTTP 接口
│   └── external/      - 对外 API（API Key 鉴权）
│
├── service/         ② Service 层：业务逻辑
│   └── impl/           - 接口实现（部分 Service 用接口分离）
│
├── repository/      ③ Repository 层：数据库操作
│
├── entity/          ④ Entity：数据库表对应的类
│
├── dto/             ⑤ DTO：数据传输对象（入参/出参）
│
├── config/          ⑥ Config：配置类
│   └── properties/    - 类型安全的 @ConfigurationProperties
│
├── util/            ⑦ Util：通用工具
│
├── annotation/      ⑧ Annotation：自定义注解
│
├── constants/       常量类
├── exception/       自定义异常
└── listener/        事件监听器
```

下面**逐层**讲，附真实代码片段。

## 三、Controller 层：HTTP 接口

**职责**：接 HTTP 请求 → 调 Service → 返回 JSON。

**本项目典型 Controller**：

```java
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public Result<Page<ProductDTO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(productService.list(page, size));
    }

    @GetMapping("/{id}")
    public Result<ProductDTO> get(@PathVariable Long id) {
        return Result.success(productService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<ProductDTO> create(@Valid @RequestBody ProductDTO dto) {
        return Result.success(productService.create(dto));
    }
}
```

**注解速查**：

| 注解 | 作用 |
|------|------|
| `@RestController` | 返回值直接序列化成 JSON |
| `@RequestMapping("/api/products")` | 类级别的 URL 前缀 |
| `@GetMapping` / `@PostMapping` / `@PutMapping` / `@DeleteMapping` | HTTP 方法 |
| `@RequestParam` | URL 查询参数 `?page=1` |
| `@PathVariable` | URL 路径变量 `/products/{id}` |
| `@RequestBody` | 请求体 JSON → 对象 |
| `@Valid` | 触发参数校验（配合字段上的 `@NotBlank` 等） |
| `@PreAuthorize("hasRole('ADMIN')")` | 权限控制，必须 ADMIN 才能调 |

**Controller 三不要**：

1. ❌ 不要写业务逻辑（只调 Service）
2. ❌ 不要写 SQL（交给 Repository）
3. ❌ 不要返回 Entity（返回 DTO，防止字段暴露/懒加载异常）

## 四、Service 层：业务逻辑

**职责**：处理业务规则、组合多个 Repository、事务管理、缓存策略。

**本项目典型 Service**：

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final RedisCacheService redisCacheService;

    @Transactional(readOnly = true)
    public Page<ProductDTO> list(int page, int size) {
        log.info("查询产品列表, page={}, size={}", page, size);
        return productRepository.findAll(PageRequest.of(page - 1, size))
                .map(this::toDTO);    // Entity → DTO
    }

    @Transactional
    public ProductDTO create(ProductDTO dto) {
        Product entity = new Product();
        entity.setName(dto.getName());
        // ... 业务校验
        Product saved = productRepository.save(entity);
        log.info("创建产品成功, id={}", saved.getId());
        redisCacheService.evict("products");    // 清缓存
        return toDTO(saved);
    }

    private ProductDTO toDTO(Product entity) {
        // Entity → DTO 的转换方法
        ProductDTO dto = new ProductDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        return dto;
    }
}
```

**@Transactional 是什么？**

事务 = "要么全成功、要么全失败"的原子操作。

```java
@Transactional   // 这个方法跑在一个事务里
public void transfer(Long fromId, Long toId, BigDecimal amount) {
    accountRepository.debit(fromId, amount);    // 扣钱
    accountRepository.credit(toId, amount);    // 加钱
    // 如果任何一步抛异常，前面已经执行的 SQL 自动回滚
}
```

`@Transactional(readOnly = true)` 表示只读事务，JPA 可以做性能优化。

**Service 三不要**：

1. ❌ 不要处理 HTTP 相关的东西（不知道 HttpServletRequest 是什么）
2. ❌ 不要直接 new Repository（用注入的）
3. ❌ 业务校验放这里，不要放 Controller

## 五、Repository 层：数据库操作

**职责**：纯 CRUD，对数据库的读写都封装在这里。

**本项目典型 Repository**：

```java
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    // JPA 自动生成实现，你只声明接口
    // 1. 按方法名自动生成 SQL
    Optional<Product> findByName(String name);

    // 2. JPQL（面向对象的 SQL）
    @Query("SELECT p FROM Product p WHERE p.status = :status AND p.categoryId = :cid")
    List<Product> findByStatusAndCategory(@Param("status") String status,
                                          @Param("cid") Long categoryId);

    // 3. 原生 SQL（必要时）
    @Query(value = "SELECT * FROM product WHERE created_time > :since", nativeQuery = true)
    List<Product> findRecent(@Param("since") LocalDateTime since);
}
```

**JpaRepository 自带的方法**（不用写就能用）：

| 方法 | 作用 |
|------|------|
| `save(entity)` | 插入或更新 |
| `saveAll(entities)` | 批量保存 |
| `findById(id)` | 按主键查 |
| `findAll()` | 查所有 |
| `findAll(PageRequest)` | 分页查 |
| `count()` | 计数 |
| `deleteById(id)` | 按主键删 |
| `existsById(id)` | 判断是否存在 |
| `flush()` | 强制刷盘 |

**命名约定（findByXxx）**：

```java
findByName              → WHERE name = ?
findByNameAndAge        → WHERE name = ? AND age = ?
findByNameLike          → WHERE name LIKE ?
findByAgeGreaterThan    → WHERE age > ?
findByOrderByCreatedTimeDesc  → 按 createdTime DESC 排序
```

**Repository 三不要**：

1. ❌ 不要写业务逻辑（只做数据库读写）
2. ❌ 不要处理缓存
3. ❌ 不要抛业务异常（让它冒泡到 Service 层处理）

## 六、Entity 层：数据库表的镜像

**职责**：和数据库表一一对应，每行数据 = 一个 Entity 对象。

**本项目典型 Entity**：

```java
@Entity
@Table(name = "product")
@Data
@NoArgsConstructor
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_name", nullable = false, length = 100)
    private String name;

    @Column(name = "category_id")
    private Long categoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private ProductStatus status;       // 枚举类型

    @Column(name = "price", precision = 18, scale = 4)
    private BigDecimal price;

    @Column(name = "created_time", updatable = false)
    @CreationTimestamp                   // 自动填充创建时间
    private LocalDateTime createdTime;

    @UpdateTimestamp                     // 自动填充更新时间
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    // 关联关系（懒加载）
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", insertable = false, updatable = false)
    private Category category;
}
```

**关键注解**：

| 注解 | 作用 |
|------|------|
| `@Entity` | 标记这是 JPA 实体 |
| `@Table(name = "x")` | 指定表名 |
| `@Id` | 主键 |
| `@GeneratedValue` | 主键生成策略（IDENTITY = 数据库自增） |
| `@Column` | 字段映射配置 |
| `@Enumerated(EnumType.STRING)` | 枚举存为字符串（不要用 ORDINAL，坑！） |
| `@CreationTimestamp` / `@UpdateTimestamp` | 自动维护时间 |
| `@ManyToOne` / `@OneToMany` / `@OneToOne` / `@ManyToMany` | 关联关系 |
| `@JoinColumn` | 外键列 |
| `@Version` | 乐观锁字段 |

**关系字段的命名规范**：

```java
// 单向多对一：产品 → 分类
@ManyToOne
@JoinColumn(name = "category_id")
private Category category;

// 单向一对多：分类 → 产品列表
@OneToMany(mappedBy = "category")
private List<Product> products;
```

`mappedBy` 指明"关系的维护方在对方"，要写在非维护方。

**Entity 五不要**：

1. ❌ 不要返回给前端（暴露内部字段、可能触发懒加载异常）
2. ❌ 不要在 Entity 里加业务方法（保持纯数据）
3. ❌ 不要用基本类型 `long`（用 `Long`，允许 null）
4. ❌ 不要用 `@Data` 在有关系的 Entity 上（会产生 NPE，改用 `@Getter @Setter @ToString`）
5. ❌ 不要循环依赖（A → B → A）

## 七、DTO 层：数据传输对象

**为什么要 DTO？**

| 场景 | Entity | DTO |
|------|--------|-----|
| 返回前端 | 暴露内部字段（如密码哈希） | 只暴露需要的 |
| 多表关联 | 触发懒加载、可能 N+1 | 一次查询组装好 |
| 入参校验 | — | 字段加 `@NotBlank` 等 |
| API 文档 | — | DTO 上的注解能生成 Swagger |
| 版本兼容 | 改 Entity 影响数据库 | 改 DTO 不影响 |

**本项目典型 DTO**：

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDTO {
    private Long id;

    @NotBlank(message = "产品名称不能为空")
    @Length(max = 100)
    private String name;

    @NotNull(message = "分类不能为空")
    private Long categoryId;

    private String categoryName;     // 用于展示，数据库里没有

    @DecimalMin(value = "0", message = "价格不能小于 0")
    private BigDecimal price;

    private ProductStatus status;
}
```

**DTO 三不要**：

1. ❌ 不要继承 Entity
2. ❌ 不要加 `@Entity` 注解（它不是表）
3. ❌ 不要在 DTO 里写业务逻辑

## 八、Config 层：Spring 配置

**职责**：注册 Bean、配置拦截器、配置过滤器、加载自定义属性。

本项目有几类 Config：

### 8.1 主配置类

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    // 配置过滤器链、密码编码器、AuthenticationProvider ...
}
```

### 8.2 配置属性类（Properties）

把 `application.yml` 里的自定义配置封装成强类型对象：

```java
@Data
@Component
@ConfigurationProperties(prefix = "security")
public class SecurityProperties {
    private String jwtSecret;
    private long jwtExpiration = 86400000;
    private PasswordPolicy passwordPolicy = new PasswordPolicy();

    @Data
    public static class PasswordPolicy {
        private int minLength = 8;
        private boolean requireDigit = true;
        // ...
    }
}
```

用的时候：

```java
@RequiredArgsConstructor
@Service
public class MyService {
    private final SecurityProperties securityProperties;

    public void foo() {
        String secret = securityProperties.getJwtSecret();
    }
}
```

### 8.3 拦截器 / 过滤器

```java
@Component
public class OperationLogInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        // 记录请求开始时间
        return true;
    }
}
```

## 九、Util 层：通用工具

**职责**：与业务无关的通用功能，如时间格式化、字符串处理、加密、IP 解析。

**本项目典型 Util**：

```java
public final class StringUtils {
    private StringUtils() {}    // 私有构造，禁止实例化

    public static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
```

**Util 设计原则**：
- 类是 `final`、构造方法 `private`
- 方法是 `public static`
- 不依赖 Spring Bean（纯静态工具）

> 💡 不过本项目有些 Util 类持有 Spring Bean（如 `ClientIpResolver`），这是"伪 Util"，更好的做法是单独写成 `@Component`。

## 十、Annotation 层：自定义注解

**职责**：声明业务语义的"标签"，由切面或框架读取后处理。

**本项目自定义注解**（最典型的一个）：

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {
    String type();             // 操作类型：CREATE/UPDATE/DELETE
    String module() default ""; // 模块名
    String description() default ""; // 描述
}
```

用在 Controller 上：

```java
@PostMapping
@OperationLog(type = "CREATE", module = "产品管理", description = "新增产品")
public Result<ProductDTO> create(@RequestBody ProductDTO dto) { ... }
```

切面读到后自动记录日志，详见 [08 AOP 与操作日志](08-aop-operation-log.md)。

## 十一、其他辅助包

| 包 | 作用 |
|----|------|
| `constants/` | 全局常量，如 `public static final String USER_CACHE = "user:cache:"` |
| `exception/` | 自定义异常类，如 `BusinessException`、`ResourceNotFoundException` |
| `listener/` | Spring 事件监听器，如启动后初始化菜单、用户 |

## 十二、动手试试

### 实验 1：找一个 Controller，顺藤摸瓜

打开 `controller/ProductController.java`，挑一个接口，按 F3（IDEA 跳转）一路追到 Service → Repository → Entity，记录路径。

### 实验 2：写一个 DTO

新建 `dto/ProductCreateRequest.java`：

```java
@Data
public class ProductCreateRequest {
    @NotBlank
    private String name;
    @NotNull
    private Long categoryId;
}
```

在 Controller 的方法参数上加上 `@Valid @RequestBody ProductCreateRequest req`，试试不传 `name` 看接口返回什么错误。

### 实验 3：给 Entity 加字段

给 `Product` 加一个 `String remark;` 字段，对应数据库加一列 `remark VARCHAR(500)`，重启应用（因为 `ddl-auto: validate` 会校验），观察启动日志。

---

## 十三、一张图总结各层职责

```
┌──────────────────────────────────────────────┐
│  Controller  接收 HTTP、参数校验、返回 JSON   │ ← 面向"前端/外部"
├──────────────────────────────────────────────┤
│  Service     业务逻辑、事务、缓存、日志        │ ← 面向"业务"
├──────────────────────────────────────────────┤
│  Repository  数据库 CRUD                      │ ← 面向"数据库"
├──────────────────────────────────────────────┤
│  Entity      数据模型（数据库表的映射）        │ ← 面向"持久化"
└──────────────────────────────────────────────┘

┌──────────────────────────────────────────────┐
│  DTO         跨层数据传输（API 输入输出）      │ ← 横切各层
└──────────────────────────────────────────────┘

┌──────────────────────────────────────────────┐
│  Config/Util/Annotation  横切关注点           │ ← 任何层都能用
└──────────────────────────────────────────────┘
```

---

下一章：[05 JPA 与数据持久化](05-jpa-persistence.md) →

回头补课：[01 宏观架构概览](01-architecture-overview.md)