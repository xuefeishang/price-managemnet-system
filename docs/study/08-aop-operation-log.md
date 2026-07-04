# 08. AOP 切面与操作日志

> `@OperationLog` 注解一贴，自动记录"谁在什么时候做了什么"——这是怎么实现的？

---

## 一、什么是 AOP？

**AOP = Aspect-Oriented Programming = 面向切面编程**。

传统的 OOP（面向对象）把代码按"对象"组织：

```
ProductController    →  处理 HTTP 请求
ProductService       →  处理业务
ProductRepository    →  操作数据库
```

但有些功能横跨多个对象：

```
┌─────────────────────────────────────────┐
│ "记录操作日志" 这个需求                   │
│   - ProductController 要记               │
│   - UserController 要记                 │
│   - OrderController 要记                │
│   - ……所有 Controller 都要记            │
└─────────────────────────────────────────┘
```

如果每个 Controller 都写一份日志代码，那就重复了。**AOP 解决**：把这种"横切关注点"单独抽出来，统一处理。

**类比**：

| OOP | AOP |
|-----|-----|
| 纵向：每个类负责自己的业务 | 横向：切面拦截一批类的某些方法，统一做一件事 |
| 一个类是一个"圆柱体" | 切面是一把"刀"，横切所有圆柱体 |

## 二、AOP 核心术语

```
                切面（Aspect）
                     │
   ┌─────────────────┼─────────────────┐
   │                 │                 │
切入点        通知（Advice）       连接点
(Pointcut)    (何时做什么)         (Join Point)
"拦截谁"       "拦截后做什么"        "可拦截的位置"
```

| 术语 | 英文 | 含义 |
|------|------|------|
| **切面** | Aspect | 切面类本身（一个普通类 + `@Aspect`） |
| **连接点** | Join Point | 程序执行的某个点（如方法调用、异常抛出） |
| **切入点** | Pointcut | 一组连接点的表达式（"拦截哪些方法"） |
| **通知** | Advice | 在切入点做什么（"拦截后干啥"） |
| **目标对象** | Target | 被代理的对象 |
| **代理** | Proxy | Spring 帮我们生成的对象 |
| **织入** | Weaving | 把切面"缝"到目标对象的过程 |

## 三、五种通知类型

```java
@Aspect
@Component
public class MyAspect {

    @Before("execution(* com.pricemanagement.service.*.*(..))")
    public void before(JoinPoint jp) {
        // 1. 前置通知：方法执行前
        System.out.println("准备执行：" + jp.getSignature());
    }

    @After("execution(* com.pricemanagement.service.*.*(..))")
    public void after(JoinPoint jp) {
        // 2. 后置通知：方法执行后（无论成功失败）
        System.out.println("执行完毕：" + jp.getSignature());
    }

    @AfterReturning(value = "execution(* com.pricemanagement.service.*.*(..))",
                    returning = "result")
    public void afterReturning(JoinPoint jp, Object result) {
        // 3. 返回通知：方法正常返回
        System.out.println("返回结果：" + result);
    }

    @AfterThrowing(value = "execution(* com.pricemanagement.service.*.*(..))",
                   throwing = "ex")
    public void afterThrowing(JoinPoint jp, Exception ex) {
        // 4. 异常通知：方法抛异常
        System.out.println("抛异常：" + ex.getMessage());
    }

    @Around("execution(* com.pricemanagement.service.*.*(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        // 5. 环绕通知：最强大，能完全控制方法是否执行
        long start = System.currentTimeMillis();
        Object result = pjp.proceed();    // 调用原方法
        long cost = System.currentTimeMillis() - start;
        System.out.println("耗时：" + cost + "ms");
        return result;
    }
}
```

**记忆口诀**：前（before）后（after）返（returning）异（throwing）环（around）。

## 四、切入点表达式

**语法**：`execution(修饰符? 返回类型 包名.类名.方法名(参数) 抛出异常?)`

```java
// 1. 拦截所有 public 方法
execution(public * *(..))

// 2. 拦截 service 包下所有方法
execution(* com.pricemanagement.service..*.*(..))

// 3. 拦截 ProductController 下所有方法
execution(* com.pricemanagement.controller.ProductController.*(..))

// 4. 拦截带 @OperationLog 注解的方法
@annotation(com.pricemanagement.annotation.OperationLog)

// 5. 组合
execution(* com.pricemanagement.service..*.*(..)) && @annotation(OperationLog)
```

`..` 表示"任意"，`*` 表示"任意一段"。

## 五、本项目的 `@OperationLog` 实战

### 5.1 定义注解

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {
    /** 操作类型：CREATE/UPDATE/DELETE/LOGIN/... */
    String type();

    /** 模块名 */
    String module() default "";

    /** 操作描述 */
    String description() default "";
}
```

### 5.2 使用

```java
@PostMapping
@OperationLog(type = "CREATE", module = "产品管理", description = "新增产品")
public Result<ProductDTO> create(@RequestBody ProductDTO dto) {
    return Result.success(productService.create(dto));
}
```

### 5.3 写切面

```java
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class OperationLogAspect {

    private final OperationLogService operationLogService;
    private final SecurityContextHolder securityContextHolder;
    private final ClientIpResolver clientIpResolver;

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint pjp, OperationLog operationLog) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = null;
        Throwable error = null;

        try {
            result = pjp.proceed();
            return result;
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            // 不管成功失败，都记录日志
            saveLog(pjp, operationLog, result, error, System.currentTimeMillis() - start);
        }
    }

    private void saveLog(ProceedingJoinPoint pjp,
                         OperationLog annotation,
                         Object result,
                         Throwable error,
                         long costMs) {
        try {
            OperationLogEntity log = new OperationLogEntity();
            log.setType(annotation.type());
            log.setModule(annotation.module());
            log.setDescription(annotation.description());
            log.setMethod(pjp.getSignature().toShortString());
            log.setRequestParams(toJson(pjp.getArgs()));
            log.setResponse(result != null ? toJson(result).substring(0,
                                  Math.min(2000, toJson(result).length())) : null);
            log.setStatus(error == null ? "SUCCESS" : "FAILED");
            log.setErrorMessage(error != null ? error.getMessage() : null);
            log.setCostMs(costMs);

            // 当前用户
            String username = currentUsername();
            log.setOperator(username);

            // 客户端 IP
            log.setClientIp(clientIpResolver.getCurrentIp());

            // 异步保存（不阻塞业务）
            operationLogService.asyncSave(log);
        } catch (Exception e) {
            // 日志记录失败不能影响主业务
            log.error("记录操作日志失败", e);
        }
    }
}
```

**关键设计**：

1. **环绕通知**：拿到执行结果、异常、耗时
2. **try-finally**：无论成功失败都记录
3. **异常兜底**：日志失败不能影响主业务
4. **敏感字段过滤**：请求参数里有密码时脱敏

## 六、AOP 是怎么"无侵入"实现的？

**核心：动态代理**。

Spring 在启动时，对所有匹配的 Bean 生成**代理对象**，注入到调用方。

```
外部调用 productController.create()
       │
       ▼
代理对象 (OperationLogAspect.create)  ← Spring 生成的
       │ 1. before
       │ 2. 调用真正的 productController.create()
       │ 3. after
       │ 4. 返回结果
       ▼
真实对象 (ProductController.create)
```

调用方拿到的"controller"其实是代理，对它调用任何方法都会被切面拦截。

**两种代理方式**：

| 方式 | 适用 | 原理 |
|------|------|------|
| JDK 动态代理 | 目标类**实现了接口** | 基于接口生成新类 |
| CGLIB 动态代理 | 目标类**没实现接口** | 生成子类继承目标类 |

Spring Boot 默认用 CGLIB（更通用）。

## 七、AOP 失效场景

**新手常踩的坑**：

### 7.1 自调用失效

```java
@Service
public class UserService {
    public void outer() {
        this.inner();    // ❌ 自调用，切面不生效
    }

    @OperationLog(type = "LOGIN")
    public void inner() { ... }
}
```

**原因**：`this.inner()` 直接调用真实方法，没经过代理。

**解决**：

```java
// 方案 1：拆成两个 Bean
@Service
public class UserService {
    private final UserService self;   // 注入自己

    public UserService(@Lazy UserService self) {
        this.self = self;
    }

    public void outer() {
        self.inner();   // ✅ 通过代理调用
    }
}

// 方案 2：用 AopContext
((UserService) AopContext.currentProxy()).inner();
```

### 7.2 私有方法失效

```java
@OperationLog
private void foo() { ... }   // ❌ AOP 不能代理 private 方法
```

### 7.3 final 方法失效

```java
@OperationLog
public final void foo() { ... }   // ❌ CGLIB 不能代理 final 方法（不能重写）
```

### 7.4 Bean 没被 Spring 管理

自己 `new` 出来的对象，没经过 Spring 容器，自然不会有代理。

## 八、AOP 的典型应用场景

| 场景 | 用途 |
|------|------|
| **日志** | 操作日志、调用日志 |
| **权限** | `@PreAuthorize` 本质就是 AOP |
| **事务** | `@Transactional` 本质也是 AOP |
| **缓存** | `@Cacheable` 同样是 AOP |
| **限流** | 接口级流控 |
| **重试** | `@Retryable` |
| **监控** | 方法耗时统计 |
| **审计** | 记录谁改了什么 |
| **统一异常处理** | `@ControllerAdvice` 是 AOP 的变体 |

**结论**：AOP 是 Spring 的"瑞士军刀"，几乎所有横切关注点都靠它。

## 九、动手试试

### 实验 1：跟踪一次操作日志

1. 调用 `POST /api/products`（创建产品）
2. 查数据库 `operation_log` 表，应该有一条新记录
3. 字段应该有：操作人、操作类型、模块名、请求参数、耗时、IP、状态

### 实验 2：写一个自定义注解 + 切面

需求：自动打印每个 Service 方法的耗时（超过 500ms 告警）。

**第一步**：定义注解

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PerfLog {
}
```

**第二步**：写切面

```java
@Aspect
@Component
@Slf4j
public class PerfLogAspect {

    @Around("@annotation(com.pricemanagement.annotation.PerfLog)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return pjp.proceed();
        } finally {
            long cost = System.currentTimeMillis() - start;
            if (cost > 500) {
                log.warn("[慢方法] {} 耗时 {}ms", pjp.getSignature(), cost);
            }
        }
    }
}
```

**第三步**：用一下

```java
@PerfLog
public Page<ProductDTO> list(int page, int size) { ... }
```

### 实验 3：复现 AOP 失效

```java
@Service
public class DemoService {
    public void outer() {
        inner();    // 自调用
    }

    @PerfLog
    public void inner() {
        // 应该会打印耗时，但 outer 调用时不会
    }
}
```

测一下，理解为什么。

---

## 十、AOP 底层原理（选读）

Spring 用**动态字节码技术**生成代理类：

```
1. 启动时扫描所有 @Aspect
2. 对每个匹配 Pointcut 的 Bean 生成代理类
3. 代理类里包一层 Advice 调用逻辑
4. 把代理类注入到调用方
```

源码级别：

- `AnnotationAwareAspectJAutoProxyCreator`：自动创建代理的 BeanPostProcessor
- `ReflectiveMethodInvocation`：拦截器链的调用实现

如果想深入研究，可以看 Spring AOP 源码（org.springframework.aop 包）。

---

## 十一、面试常见题

- AOP 是什么？解决了什么问题？— 横切关注点
- Spring AOP 和 AspectJ 区别？— Spring AOP 运行时织入、AspectJ 编译期织入
- JDK 动态代理和 CGLIB 区别？— 见上
- Spring 事务是怎么实现的？— AOP（@Transactional 本质是环绕通知）
- @Transactional 失效场景？— 自调用、private、final、异常被吞、传播行为不对

---

下一章：[99 学习路线图与实战建议](99-learning-path.md) →

回头补课：[04 项目分层架构](04-layered-architecture.md)