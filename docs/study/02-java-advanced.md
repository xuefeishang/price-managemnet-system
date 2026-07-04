# 02. Java 高级特性：注解、泛型、Lombok、Stream

> 已经看完 [00c Java 语法入门](00c-java-syntax.md)，这一章讲**本项目真正用到的"高级语法糖"**——让你能看懂 `Product`、`@Data`、`stream()` 这些代码。

---

## 一、版本与背景

本项目用 **Java 25**（截至 2026 年的最新 LTS）。

| 版本 | 年份 | 备注 |
|------|------|------|
| Java 8 | 2014 | 经典版本 |
| Java 11 | 2018 | LTS |
| Java 17 | 2021 | LTS |
| Java 21 | 2023 | LTS |
| **Java 25** | 2025 | **本项目使用** |

LTS = Long Term Support，企业首选。

如果你完全没学过 Java，请先看 [00c Java 语法入门](00c-java-syntax.md)。

## 二、注解（Annotation）

### 2.1 什么是注解？

**注解 = 给代码贴的"标签"**。

```java
@Override                                      // 标签 1：表示重写父类方法
@Entity                                         // 标签 2：表示这是一个 JPA 实体
@Data                                           // 标签 3：Lombok 自动生成代码
public class Product { ... }
```

**注解本身不干活**，它只是标记。真正干活的是读到这些标记的框架代码（Spring、JPA、Lombok…）。

### 2.2 内置注解

| 注解 | 作用 |
|------|------|
| `@Override` | 标记重写父类方法 |
| `@Deprecated` | 标记已过时（编译器会警告） |
| `@SuppressWarnings("xxx")` | 抑制编译器警告 |
| `@FunctionalInterface` | 标记函数式接口 |

### 2.3 元注解（修饰注解的注解）

```java
@Target(ElementType.METHOD)                     // 这个注解贴在方法上
@Retention(RetentionPolicy.RUNTIME)             // 运行时保留（反射能读到）
public @interface OperationLog {
    String type();
}
```

| 元注解 | 含义 |
|--------|------|
| `@Target` | 这个注解能贴哪里（方法？类？字段？） |
| `@Retention` | 注解保留到什么时候（源码？字节码？运行时？） |

### 2.4 本项目用到的注解分两类

**框架注解**（框架读取）：

```java
@Entity                                          // JPA：这是数据库表
@Table(name = "product")                         // JPA：对应表名
@Id                                              // JPA：主键
@Column(name = "product_name")                   // JPA：列名
@RestController                                  // Spring：REST 控制器
@RequestMapping("/api/products")                 // Spring：URL 前缀
@GetMapping                                      // Spring：GET 请求
@Autowired                                       // Spring：注入依赖
@Service                                         // Spring：业务层
@Repository                                      // Spring：数据层
@Configuration                                   // Spring：配置类
@Transactional                                   // Spring：事务管理
@Cacheable                                       // Spring Cache：缓存
@PreAuthorize("hasRole('ADMIN')")                // Spring Security：权限
```

**Lombok 注解**（编译时生成代码）：

```java
@Data                                            // getter/setter/toString 等
@Getter / @Setter                                // 单个
@Builder                                         // 建造者模式
@Slf4j                                           // 日志对象 log
@RequiredArgsConstructor                         // final 字段构造注入
@NoArgsConstructor                              // 无参构造
@AllArgsConstructor                              // 全参构造
```

### 2.5 自定义注解

本项目最经典的例子：`@OperationLog`。

```java
@Target(ElementType.METHOD)                      // 贴在方法上
@Retention(RetentionPolicy.RUNTIME)              // 运行时可读
public @interface OperationLog {
    String type();                                // 必填：操作类型
    String module() default "";                  // 可选：模块名
    String description() default "";             // 可选：描述
}
```

用法：

```java
@OperationLog(type = "CREATE", module = "产品", description = "新增")
public ProductDTO create(...) { ... }
```

切面读到后自动记录日志，详见 [08 AOP 切面](08-aop-operation-log.md)。

## 三、泛型（Generics）

### 3.1 什么是泛型？

**泛型 = "类型参数化"**——让一个类/方法支持多种类型。

```java
// 没有泛型
List list = new ArrayList();
list.add("hello");
list.add(123);
String s = (String) list.get(1);   // 运行时 ClassCastException！

// 有泛型
List<String> list = new ArrayList<>();
list.add("hello");
// list.add(123);   // 编译报错！
String s = list.get(0);            // 自动是 String
```

### 3.2 本项目的泛型用法

```java
// 泛型类
public class Result<T> {            // T 是类型参数，调用时指定
    private T data;
    private String message;

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.data = data;
        return r;
    }
}

// 使用
Result<ProductDTO> r1 = Result.success(productDTO);     // T = ProductDTO
Result<List<ProductDTO>> r2 = Result.success(list);    // T = List<ProductDTO>
Result<Page<ProductDTO>> r3 = Result.success(page);    // T = Page<ProductDTO>
```

### 3.3 常见泛型写法

| 写法 | 含义 |
|------|------|
| `<T>` | 任意类型 |
| `<T extends Number>` | T 必须是 Number 或子类 |
| `<?>` | 任意类型（接收） |
| `<? extends T>` | T 或其子类 |
| `<? super T>` | T 或其父类 |

### 3.4 看懂本项目的 Repository

```java
public interface ProductRepository extends JpaRepository<Product, Long> {
    //                                ^^^^^^  ^^^^^
    //                                实体类   主键类型
}
```

`JpaRepository<T, ID>` 是泛型接口，要指定两个类型参数：
- `T` = 实体类（这里是 Product）
- `ID` = 主键类型（这里是 Long）

## 四、Lombok：本项目的"代码生成器"

Lombok 是一个**编译期代码生成器**，通过注解帮你写 Java 样板代码。

### 4.1 为什么需要 Lombok？

```java
// 没有 Lombok：一个有 10 个字段的类，要写 20 个 getter/setter
public class Product {
    private Long id;
    private String name;
    // ...
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    // ... 20 个方法
}

// 有 Lombok：一个注解搞定
@Data
public class Product {
    private Long id;
    private String name;
}
```

### 4.2 本项目用到的 Lombok 注解

| 注解 | 生成什么 | 什么时候用 |
|------|---------|-----------|
| `@Data` | getter/setter/toString/equals/hashCode | POJO 实体、DTO |
| `@Getter` / `@Setter` | 单个 getter/setter | 想细粒度控制 |
| `@Builder` | 建造者模式 | 复杂对象构造 |
| `@Slf4j` | `Logger log` 字段 | 需要打日志的类 |
| `@RequiredArgsConstructor` | 为 final 字段生成构造方法 | 注入 Service |
| `@AllArgsConstructor` | 全参构造 | 特殊场景 |
| `@NoArgsConstructor` | 无参构造 | JPA 要求 |
| `@ToString` / `@EqualsAndHashCode` | 单个 | 单独生成 |

### 4.3 真实用法

**本项目 Controller**：

```java
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor                         // 生成 public ProductController(ProductService s)
public class ProductController {
    private final ProductService productService; // final + 构造注入
    // 没显式构造方法，Lombok 自动生成
}
```

**本项目 Service**：

```java
@Service
@RequiredArgsConstructor                         // 生成构造方法
@Slf4j                                           // 生成 private static final Logger log
public class ProductService {
    private final ProductRepository productRepository;

    public Page<ProductDTO> list(int page, int size) {
        log.info("查询产品 page={}", page);         // 直接用 log
        return productRepository.findAll(...)
                .map(this::toDTO);
    }
}
```

**本项目 DTO**：

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDTO {
    private Long id;
    private String name;

    // 这些方法都不用写，Lombok 自动生成：
    //   getId(), setId(), getName(), setName(), toString(), equals(), hashCode()
    //   builder(): ProductDTO.builder().id(1L).name("铜").build()
}
```

### 4.4 IDEA 配置 Lombok

Lombok 是**编译期插件**，IDEA 需要：
1. **装插件**：`Settings → Plugins → 搜索 Lombok → Install`
2. **启用注解处理**：`Settings → Build → Compiler → Annotation Processors → 勾选 Enable`

否则会看到一堆 `getName()` 找不到的红字。

## 五、Stream API

Stream API 是 Java 8 引入的**处理集合的优雅写法**。

### 5.1 三步走：获取流 → 处理 → 收集

```java
List<Product> products = ...;

// 传统写法：循环 + 判断 + 加到新集合
List<String> names = new ArrayList<>();
for (Product p : products) {
    if (p.getPrice().compareTo(new BigDecimal("5000")) > 0) {
        names.add(p.getName());
    }
}

// Stream 写法
List<String> names = products.stream()
    .filter(p -> p.getPrice().compareTo(new BigDecimal("5000")) > 0)
    .map(Product::getName)
    .toList();
```

### 5.2 常用操作

| 操作 | 作用 | 例子 |
|------|------|------|
| `.filter(条件)` | 过滤 | `.filter(p -> p.isActive())` |
| `.map(转换)` | 一对一转换 | `.map(Product::getName)` |
| `.distinct()` | 去重 | `.distinct()` |
| `.sorted(比较器)` | 排序 | `.sorted(Comparator.comparing(Product::getPrice))` |
| `.limit(n)` | 取前 n 个 | `.limit(10)` |
| `.skip(n)` | 跳过 n 个 | `.skip(20)` |
| `.forEach(动作)` | 遍历 | `.forEach(System.out::println)` |
| `.collect(收集器)` | 收成集合 | `.collect(Collectors.toList())` |
| `.toList()` | 直接收成不可变 List | `.toList()` |
| `.count()` | 计数 | `.count()` |
| `.anyMatch(条件)` | 任一匹配 | `.anyMatch(p -> p.getPrice() > 1000)` |
| `.allMatch(条件)` | 全部匹配 | `.allMatch(Product::isActive)` |
| `.noneMatch(条件)` | 全不匹配 | `.noneMatch(p -> p.isDeleted())` |
| `.findFirst()` | 第一个 | `.findFirst()` |
| `.reduce(聚合)` | 聚合 | `.reduce(BigDecimal.ZERO, BigDecimal::add)` |

### 5.3 本项目典型用法

```java
// 1. 转 DTO
List<ProductDTO> dtos = productRepository.findAll().stream()
    .map(this::toDTO)
    .toList();

// 2. 过滤 + 排序
List<Product> actives = products.stream()
    .filter(p -> p.getStatus() == ProductStatus.ACTIVE)
    .sorted(Comparator.comparing(Product::getCreatedTime).reversed())
    .toList();

// 3. 转换成 Map
Map<Long, String> idToName = products.stream()
    .collect(Collectors.toMap(Product::getId, Product::getName));

// 4. 按字段分组
Map<String, List<Product>> byCategory = products.stream()
    .collect(Collectors.groupingBy(Product::getCategoryName));

// 5. 统计
long activeCount = products.stream()
    .filter(p -> p.getStatus() == ProductStatus.ACTIVE)
    .count();
```

### 5.4 方法引用

`::` 是方法引用，等价于 lambda：

| 写法 | 等价于 |
|------|--------|
| `Product::getName` | `p -> p.getName()` |
| `Product::isActive` | `p -> p.isActive()` |
| `System.out::println` | `x -> System.out.println(x)` |
| `this::toDTO` | `entity -> this.toDTO(entity)` |

### 5.5 注意事项

```java
// Stream 不能复用
Stream<Product> stream = products.stream();
stream.filter(...).toList();
stream.filter(...).toList();    // ❌ IllegalStateException

// 必须重新获取
products.stream().filter(...).toList();
products.stream().filter(...).toList();  // ✅

// toList() 返回不可变 List（JDK 16+）
List<String> names = products.stream().map(Product::getName).toList();
names.add("x");   // ❌ UnsupportedOperationException
// 想可变用：.collect(Collectors.toList())
```

## 六、Optional：防空指针

### 6.1 NullPointerException 之痛

```java
// 传统写法：一连串 null 判断
String name = null;
if (product != null) {
    if (product.getCategory() != null) {
        name = product.getCategory().getName();
    }
}
```

### 6.2 Optional 写法

```java
String name = Optional.ofNullable(product)              // 包装可能为 null 的值
    .map(Product::getCategory)                          // 转换
    .map(Category::getName)                             // 再转换
    .orElse("未分类");                                    // 默认值
```

### 6.3 常用方法

| 方法 | 作用 |
|------|------|
| `Optional.of(x)` | 包装一个值（x 不能 null） |
| `Optional.ofNullable(x)` | 包装可能为 null 的值 |
| `Optional.empty()` | 创建一个空的 Optional |
| `.isPresent()` | 是否有值 |
| `.get()` | 取值（空时抛异常） |
| `.orElse(default)` | 没值时用默认值 |
| `.orElseThrow()` | 没值时抛异常 |
| `.map(f)` | 转换（自动解包） |
| `.flatMap(f)` | 转换（f 必须返回 Optional） |
| `.filter(p)` | 过滤 |

### 6.4 本项目典型用法

```java
// 1. Repository 返回 Optional<T>
Optional<Product> product = productRepository.findById(id);

// 2. Service 处理
ProductDTO dto = productRepository.findById(id)
    .map(this::toDTO)
    .orElseThrow(() -> new BusinessException("产品不存在"));

// 3. 链式调用
String categoryName = Optional.ofNullable(product)
    .map(Product::getCategory)
    .map(Category::getName)
    .orElse("未分类");
```

## 七、Java 25 的新特性（选读）

本项目用了 Java 25 的部分新特性。看到不慌：

| 特性 | 例子 | 备注 |
|------|------|------|
| `var` 局部类型推断 | `var list = new ArrayList<String>();` | 局部变量可用 |
| `record` 数据类 | `public record Point(int x, int y) {}` | 不可变 POJO |
| `sealed` 密封类 | `sealed class Shape permits Circle, Square` | 限制继承 |
| `text block` 文本块 | `"""多行"""` | SQL/JSON 友好 |
| `switch` 表达式 | `var s = switch(x) { case 1 -> "一"; ... };` | 不用 break |
| `instanceof` 模式 | `if (obj instanceof String s) { s.length(); }` | 自动转型 |
| `Stream.toList()` | `.toList()` | JDK 16+，替代 collect |
| `Pattern Matching for switch` | `switch (obj) { case String s -> ...; }` | JDK 21+ |

## 八、动手试试

### 实验 1：读懂一个 Lombok 类

打开 `entity/Product.java`，数一下：

1. 类上贴了几个 Lombok 注解？
2. 字段全是 `private` 吗？都是包装类型吗？
3. 为什么用 `Long` 而不是 `long`？—— 因为可能为 null
4. 想象一下没 Lombok 要写多少行

### 实验 2：写一个 Stream

```java
import lombok.Data;
import java.util.List;
import java.util.stream.Collectors;

@Data
class User {
    private String name;
    private Integer age;
    private String city;
}

public class StreamDemo {
    public static void main(String[] args) {
        List<User> users = List.of(
            new User("张三", 25, "北京"),
            new User("李四", 30, "上海"),
            new User("王五", 25, "北京"),
            new User("赵六", 35, "广州")
        );

        // 1. 找年龄大于 25 的
        List<String> names = users.stream()
            .filter(u -> u.getAge() > 25)
            .map(User::getName)
            .toList();
        System.out.println(names);  // [李四, 赵六]

        // 2. 按城市分组
        var byCity = users.stream()
            .collect(Collectors.groupingBy(User::getCity));
        System.out.println(byCity);
        // {北京=[张三,王五], 上海=[李四], 广州=[赵六]}

        // 3. 平均年龄
        double avg = users.stream()
            .mapToInt(User::getAge)
            .average()
            .orElse(0);
        System.out.println("平均年龄：" + avg);
    }
}
```

### 实验 3：自定义注解 + 反射读取

```java
import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MyAnno {
    String value();
}

class Demo {
    @MyAnno("hello")
    public void foo() {}

    public static void main(String[] args) throws Exception {
        MyAnno anno = Demo.class.getMethod("foo").getAnnotation(MyAnno.class);
        System.out.println(anno.value());  // hello
    }
}
```

### 实验 4：Optional 链式

```java
import java.util.Optional;

public class OptionalDemo {
    public static void main(String[] args) {
        String input = "hello";

        String result = Optional.ofNullable(input)
            .filter(s -> s.length() > 3)
            .map(String::toUpperCase)
            .orElse("默认值");

        System.out.println(result);  // HELLO
    }
}
```

## 九、常见疑问

**Q：为什么字段用 `Long` 而不是 `long`？**
A：`Long` 是包装类型（对象），可以为 null；`long` 是基本类型，不能为 null。数据库字段允许为 NULL 时必须用包装类型。

**Q：`final` 有什么用？**
A：`final` 变量不能重新赋值；`final` 方法不能被重写；`final` 类不能被继承。本项目大量用 `private final` 字段做"构造注入"。

**Q：`@Autowired` 和构造注入哪个好？**
A：**构造注入**更好。final + 不可变、易测试（不依赖 Spring 容器）、IDEA 友好。

**Q：Lombok 会被 IDEA 编译错吗？**
A：装好 Lombok 插件就没事。如果看到一堆红字：`Settings → Plugins → Lombok → Install`。

**Q：`@Data` 为什么不能用在有关系的 Entity 上？**
A：`@Data` 包含 `@EqualsAndHashCode`，会让 Hibernate 懒加载代理抛 NPE。Entity 用 `@Getter @Setter @ToString` 即可。

**Q：Stream 性能比 for 循环差吗？**
A：差不多。Stream 内部也是循环，还有少量对象分配。**可读性 > 微小性能差异**。

---

下一步：[03 Spring Boot 与 IoC 核心](03-spring-boot.md) →

回头补课：[00c Java 语法入门](00c-java-syntax.md)