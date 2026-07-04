# 03. Spring Boot 与 IoC 核心

> Spring 是 Java 后台绕不开的话题。这一章帮你建立**两个核心心智模型**——IoC 和 AOP。

---

## 一、Spring 到底是什么？

**一句话：Spring 是一个"帮你管理对象、组装对象"的框架。**

你写了一个 `ProductService`，里面需要用到 `ProductRepository`。在没有 Spring 的年代，你得这样：

```java
public class ProductService {
    private ProductRepository productRepository = new ProductRepositoryImpl();
    // 写死了 new 哪个实现，将来换不了、测不了
}
```

有了 Spring：

```java
@Service
public class ProductService {
    private final ProductRepository productRepository;
    // Spring 自动把 ProductRepository 的实现"塞进来"
    // 我不关心它怎么 new 的，也不用关心是哪个实现
}
```

这就是 Spring 解决的核心问题：**解耦**——把"对象的创建"和"对象的使用"分开。

## 二、IoC：控制反转

**IoC = Inversion of Control = 控制反转**。

听起来吓人，其实意思很简单：

```
传统：对象 A 用到对象 B → A 自己 new B（"我控制"）
IoC：对象 A 用到对象 B → 容器（Spring）帮我 new B 塞给我（"框架控制"）
```

控制权从"我自己"反转到"框架"，所以叫"控制反转"。

**IoC 的实现方式有两种**：

| 方式 | 写法 | 优缺点 |
|------|------|--------|
| 字段注入 | `@Autowired private Foo foo;` | 简洁，但难测试、不推荐 |
| 构造注入（推荐） | `private final Foo foo;` + `@RequiredArgsConstructor` | 不可变、易测试、IDE 友好 |

本项目**全部用构造注入**，所以你看到的所有 `Service` 类都长这样：

```java
@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;
    private final RedisCache redisCache;          // 想加就加，构造方法不用改
    // Lombok 自动生成构造方法，Spring 调用它注入
}
```

## 三、DI：依赖注入

DI = Dependency Injection = 依赖注入。**IoC 的具体实现方式**。

```java
@Service
public class ProductService {
    // ProductRepository 是 ProductService 的"依赖"
    // Spring 把 ProductRepository 的实现注入进来
    private final ProductRepository productRepository;
}
```

**为什么注入的是接口而不是实现类？**

```java
public interface ProductRepository extends JpaRepository<Product, Long> {
    // ...
}
// Spring 会自动生成一个实现：SimpleJpaRepository
// 你也可以写自定义实现：ProductRepositoryImpl
// 注入时用接口，具体实现由 Spring 决定
```

好处：**换实现不用改业务代码**。比如将来要换成读写分离的 Repository，只改 Spring 配置即可。

## 四、Bean：Spring 管理的对象

**Bean = 被 Spring 容器管理的对象**。

### 怎么让一个类变成 Bean？

**方式 1：注解**（最常用）

| 注解 | 用在哪 | 含义 |
|------|--------|------|
| `@Component` | 通用类 | "我是 Spring 管理的对象" |
| `@Service` | 业务层 | 同上，但语义化是"业务服务" |
| `@Repository` | 数据访问层 | 同上，且 JPA 会自动翻译异常 |
| `@Controller` | MVC 控制器 | 处理 HTTP 请求 |
| `@RestController` | 同上 | 直接返回 JSON（@Controller + @ResponseBody） |
| `@Configuration` | 配置类 | 里面用 @Bean 定义对象 |

**方式 2：@Bean 方法**（用于第三方库的类）

```java
@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        // 自己 new 一个 RedisTemplate，交给 Spring 管理
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);
        return template;
    }
}
```

### Bean 的生命周期

```
new 对象 → 注入依赖 → @PostConstruct → 被使用 → @PreDestroy → 销毁
```

本项目里偶尔能看到 `@PostConstruct`，就是在 Bean 创建后跑一段初始化代码。

## 五、Spring Boot 怎么"开箱即用"？

**传统 Spring**：要写一堆 XML 或 Java Config 才能启动项目。
**Spring Boot**：引一个 `starter`，零配置就能用。

### 原理：自动配置（Auto-Configuration）

Spring Boot 的 `spring-boot-autoconfigure` 包里有一堆 `XxxAutoConfiguration` 类，每个都看：
- classpath 里有没有某个类？
- application.yml 里有没有某个配置？

两个都满足，就自动把这个 Bean 装配进去。

**举例**：`spring-boot-starter-data-jpa` 的作用：

```
1. 看到 classpath 里有 Hibernate → 自动配置 EntityManager
2. 看到 application.yml 里有 spring.datasource.* → 自动配置 DataSource
3. 看到 application.yml 里有 spring.jpa.* → 自动配置 JPA
4. 扫描到 @Repository 接口 → 自动生成实现 Bean
```

**你什么都不用配，引个 starter 就完事。** 这就是"约定大于配置"。

## 六、Starter 机制

**Starter = 一组依赖 + 自动配置的合集**。

本项目 `pom.xml` 里这些就是 Starter：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>  <!-- Web 开发 -->
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>  <!-- JPA -->
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>  <!-- 安全 -->
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>  <!-- Redis -->
</dependency>
```

**命名规则**：`spring-boot-starter-{功能}`。记住这个模式，将来需要新功能直接搜 "spring boot starter xxx" 即可。

## 七、本项目启动类的解读

`PriceManagementApplication.java`：

```java
@SpringBootApplication     // 标记这是个 Spring Boot 应用
@EnableCaching            // 开启 Spring Cache 注解支持（@Cacheable 等）
@EnableScheduling         // 开启定时任务支持（@Scheduled）
public class PriceManagementApplication {
    public static void main(String[] args) {
        SpringApplication.run(PriceManagementApplication.class, args);
    }
}
```

`@SpringBootApplication` 是三个注解的组合：

```java
@SpringBootConfiguration   // 等价于 @Configuration（这是个配置类）
@EnableAutoConfiguration   // 开启自动配置
@ComponentScan            // 扫描本类所在包及其子包下的所有 @Component
```

**关键认知**：因为类放在 `com.pricemanagement` 包下，Spring 会自动扫描 `com.pricemanagement.**` 下所有带注解的类，这就是为什么 controller / service / repository 不需要额外注册。

## 八、常见疑问

### Q：@Autowired 和 @Resource 有什么区别？

`@Autowired` 是 Spring 的，按类型注入；`@Resource` 是 Java 标准的，先按名称再按类型。本项目全部用构造注入，所以很少看到这两个注解。

### Q：循环依赖怎么办？

A 类依赖 B，B 依赖 A，启动会报错。**解决办法**：
1. 重新设计，消除循环（推荐）
2. 用 `@Lazy` 延迟注入
3. 用 setter 注入代替构造注入（不推荐）

### Q：Bean 是单例的吗？

默认是 **Singleton（单例）**。整个应用一个实例，所有人共享。
其他 scope：`prototype`（每次注入都 new 一个）、`request`（每个 HTTP 请求一个）、`session`（每个用户会话一个）。

### Q：Spring 启动太慢怎么办？

可以：
1. 启用 `@Lazy` 延迟加载非关键 Bean
2. 用 `spring-context-indexer` 加速扫描
3. 升级到 Spring 6+ / Spring Boot 3+ / 4（本项目已用）

## 九、动手试试

### 实验 1：跟踪一个 Bean 的创建过程

1. 在 IDE 里打开 `ProductService.java`，在构造方法打断点（加 `System.out.println("ProductService 创建了")`）
2. 启动应用，观察控制台什么时候打印
3. 看到"先创建 Repository、再创建 Service"的顺序

### 实验 2：读懂 application.yml

打开 `backend/src/main/resources/application.yml`，找到 `spring.datasource` 节点：

```yaml
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:price_management}
```

这行配置的意思：
- 数据库 URL = `jdbc:mysql://主机:端口/数据库名`
- `${DB_HOST:localhost}` = 环境变量 `DB_HOST` 的值，如果没设则用 `localhost`
- Spring Boot 看到这个配置，自动用 `HikariDataSource`（默认连接池）

### 实验 3：手动写一个 Bean

新建 `com.pricemanagement.config.MyBean.java`：

```java
@Configuration
public class MyBean {
    @Bean
    public String myAppName() {
        return "price-management-system";
    }
}
```

然后在任意 Controller 里：

```java
@RestController
public class HomeController {
    private final String appName;

    public HomeController(String myAppName) {
        this.appName = myAppName;  // Spring 会把上面定义的 String Bean 注入
    }
}
```

访问接口，看到返回的 `appName` 了吗？

---

## 十、面试常见题

- Spring IoC 和 DI 的关系？— IoC 是思想，DI 是实现
- @Autowired 和 @Resource 区别？— 见上
- Bean 的生命周期？— 实例化 → 属性注入 → 初始化前 → 初始化 → 初始化后 → 使用 → 销毁
- Spring Boot 怎么做到开箱即用？— starter + 自动配置
- @SpringBootApplication 包含了什么？— @SpringBootConfiguration + @EnableAutoConfiguration + @ComponentScan

---

下一章：[04 项目分层架构](04-layered-architecture.md) →

回头补课：[01 宏观架构概览](01-architecture-overview.md)