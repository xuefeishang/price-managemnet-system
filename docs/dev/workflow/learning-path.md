---
title: 学习路径
version: v2.0.0
last_updated: 2026-06-15
source: docs/dev/backup/学习路径.md
---

# 学习路径

> 本文档为新成员上手学习路径建议，完整复制自 `docs/dev/backup/学习路径.md`。

## 本项目 Spring Boot 技术栈详解

### 一、项目依赖结构

本项目基于 Spring Boot 4.0.6，使用了以下核心模块：

| 模块 | 作用 |
|------|------|
| spring-boot-starter-web | Web 开发（REST API、控制器） |
| spring-boot-starter-data-jpa | 数据持久化 |
| spring-boot-starter-security | 认证授权（JWT + BCrypt） |
| spring-boot-starter-validation | 参数校验（@Valid） |
| spring-boot-starter-aspectj | AOP 切面（操作日志、限流） |
| spring-boot-starter-data-redis | Redis 缓存 |
| spring-boot-starter-cache | 缓存抽象层 |
| flyway-core | 数据库版本迁移 |

### 二、分层架构（核心概念）

```
Controller → Service → Repository → Entity
   ↓           ↓           ↓         ↓
 接口层      业务层      数据层      实体层
```

典型代码示例：`ProductController` → `ProductService` → `ProductRepository` → `Product`

### 三、核心注解详解

#### 1. 启动类注解

```java
@SpringBootApplication  // 组合注解 = @Configuration + @EnableAutoConfiguration + @ComponentScan
@EnableCaching          // 启用缓存功能
public class PriceManagementApplication { ... }
```

#### 2. Controller 层注解

```java
@RestController          // = @Controller + @ResponseBody（返回JSON）
@RequestMapping("/api/products")  // 路径前缀
@RequiredArgsConstructor  // Lombok：自动生成构造器注入
@Validated               // 启用方法参数校验
@Slf4j                   // Lombok：自动生成 log 日志对象

// 方法级注解
@GetMapping("/{id}")     // HTTP GET 方法
@PostMapping             // HTTP POST 方法
@PreAuthorize("hasRole('ADMIN')")  // Spring Security 权限控制
```

#### 3. Service 层注解

```java
@Service                 // 标记为服务层组件
@Transactional           // 声明事务（自动提交/回滚）
@Transactional(readOnly = true)  // 只读事务（优化查询性能）
```

#### 4. Repository 层注解

```java
@Repository              // 标记为数据访问组件
public interface ProductRepository extends
    JpaRepository<Product, Long>,           // 基础CRUD
    JpaSpecificationExecutor<Product> {     // 动态查询

    @EntityGraph(attributePaths = {"category"})  // 解决懒加载N+1问题
    List<Product> findByStatus(CommonStatus status);  // 方法名自动生成SQL
}
```

#### 5. Entity 层注解

```java
@Entity                  // 标记为数据库实体
@Table(name = "product") // 指定表名
@Data                    // Lombok：getter/setter/toString/equals/hashCode

@Id                      // 主键
@GeneratedValue(strategy = GenerationType.IDENTITY)  // 自增

@Version                 // 乐观锁字段
@Column(nullable = false, length = 200)  // 字段约束
@ManyToOne(fetch = FetchType.EAGER)      // 关联关系
@JoinColumn(name = "category_id")        // 外键列
@Transient               // 不映射数据库（临时字段）
@CreationTimestamp       // 创建时间自动填充
@UpdateTimestamp         // 更新时间自动填充
```

### 四、关键技术点详解

#### 1. Spring Security 配置

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // 启用 @PreAuthorize
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
            .csrf(AbstractHttpConfigurer::disable)  // 禁用CSRF（前后端分离）
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))  // 无状态
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()  // 公开路径
                .anyRequest().authenticated())  // 其他需认证
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

#### 2. 依赖注入方式

本项目使用 **构造器注入**（推荐方式）：

```java
@RequiredArgsConstructor  // Lombok 自动生成
public class ProductController {
    private final ProductService productService;  // final → 构造器注入
}
```

#### 3. 分页查询

```java
// Pageable 接口封装分页参数
Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
Page<Product> products = repository.findAll(spec, pageable);

// 返回结果包含：内容、总页数、总条数、当前页等
```

#### 4. 动态查询

```java
Specification<Product> spec = (root, query, cb) -> {
    Predicate p = cb.conjunction();
    if (keyword != null) {
        p = cb.and(p, cb.like(root.get("name"), "%" + keyword + "%"));
    }
    if (categoryId != null) {
        p = cb.and(p, cb.equal(root.get("category").get("id"), categoryId));
    }
    return p;
};
```

#### 5. AOP 切面（操作日志）

```java
@Aspect
@Component
public class OperationLogAspect {

    @Around("@annotation(operationLog)")  // 拦截带注解的方法
    public Object around(ProceedingJoinPoint pjp, OperationLog operationLog) {
        // 前置：记录操作开始
        Object result = pjp.proceed();  // 执行原方法
        // 后置：记录操作成功
        return result;
    }
}
```

### 五、配置文件关键项

```yaml
spring:
  jpa:
    hibernate.ddl-auto: validate  # 只验证表结构，不自动创建
    show-sql: true                # 打印SQL（开发调试）

  flyway:
    enabled: true                 # 数据库迁移

  cache:
    type: redis                   # 缓存类型
```

### 六、学习路径建议

| 阶段 | 重点学习内容 | 本项目示例文件 |
|------|------------|--------------|
| 入门 | 注解基础、分层架构 | `ProductController.java` |
| 进阶 | JPA 关联映射、分页 | `Product.java`、`ProductRepository.java` |
| 高级 | Spring Security、AOP | `SecurityConfig.java`、`RateLimiterAspect.java` |
| 实战 | 事务管理、缓存 | `ProductService.java`（`@Transactional`） |

**建议学习顺序：**

1. 先看 `ProductController` 理解 REST API 结构
2. 看 `Product.java` 理解 JPA 注解映射
3. 看 `ProductRepository` 理解 Spring Data JPA 查询方法
4. 看 `SecurityConfig` 理解认证授权流程
5. 看 `RateLimiterAspect` 理解 AOP 切面编程

## 前端进阶学习路径（参考）

| 阶段 | 重点学习内容 | 本项目示例文件 |
|------|------------|--------------|
| 入门 | Vue 3 Composition API + `<script setup>` | `frontend/src/views/Home.vue` |
| 进阶 | Pinia Store、Vue Router 守卫 | `frontend/src/store/useUserStore.ts` |
| 高级 | 自研 composable（useDict/useLayout/useSafeChartAutoresize） | `frontend/src/composables/useDict.ts` |
| 实战 | uni-app 多端适配、微信小程序 ECharts | `frontend-uniapp/src/pages/home/index.vue` |

## 部署进阶学习路径（参考）

| 阶段 | 重点学习内容 | 本项目示例文件 |
|------|------------|--------------|
| 入门 | docker-compose.yml 结构 | 项目根目录 `docker-compose.yml` |
| 进阶 | Nginx 反向代理 + SPA 路由回退 | `frontend/nginx.conf` |
| 高级 | Harbor 镜像备份 + 环境变量注入 | `CLAUDE.md §Harbor 镜像备份规范` |
| 实战 | 多阶段构建 + 生产优化 | `Dockerfile.backend.prod` |

## 相关文档

- [CLAUDE.md](../../../CLAUDE.md) — 项目规范
- [README.md](README.md) — 工作流总览
- [docs/dev/项目设计文档.md](../项目设计文档.md) — 技术选型
- [docs/ops/IDEA部署指南.md](../../ops/IDEA部署指南.md) — 本地部署
