# 10. 日志框架：出问题时唯一能救你的东西

> 生产环境出问题，没日志 = 抓瞎。这一章讲怎么用 Logback 打日志、查日志。

---

## 一、为什么日志这么重要？

**线上出问题时**，你最想要什么？

```
❌ "用户说产品列表打不开了，但我不知道发生了什么"
✅ "查日志，发现 14:32:15 有 NPE，原因是 Redis 连接超时"
```

**日志 = 系统的黑匣子**。

| 信息 | 没有日志 | 有日志 |
|------|---------|--------|
| 用户操作记录 | 不知道谁做了什么 | 完整记录 |
| 出错位置 | 只能猜 | 精确到行号 |
| 性能数据 | 不知道慢在哪 | 有耗时统计 |
| 攻击追溯 | 无从查起 | 一查就明 |

## 二、Java 日志框架的历史

```
JDK 1.4 (2002)        JUL (java.util.logging)        ← 难用，配置差
  │
Log4j (1999)          Apache 出品                     ← 经典，但停更
  │
SLF4J + Logback (2006)  Ceki 出品，Spring 默认        ← 本项目用的
  │
Log4j2 (2014)         Apache 重新设计                 ← 性能更强
```

**本项目用 SLF4J 接口 + Logback 实现**——这是 Spring Boot 的默认组合，pom 里没显式声明。

**两个名词**：

- **SLF4J（Simple Logging Facade for Java）**：日志的"接口"
- **Logback**：日志的"实现"

类比：

```
SLF4J  ≈  JDBC      ← 统一的调用接口
Logback ≈ MySQL 驱动 ← 具体实现
```

## 三、最简日志使用

```java
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Slf4j                            // Lombok 自动生成 log 字段
@Service
public class ProductService {

    public ProductDTO getById(Long id) {
        log.debug("查询产品 id={}", id);            // DEBUG 级别
        Product p = productRepository.findById(id)
            .orElseThrow(() -> new BusinessException("产品不存在: " + id));
        log.info("找到产品 name={}", p.getName());   // INFO 级别
        return toDTO(p);
    }
}
```

**手动写法**（不推荐）：

```java
public class ProductService {
    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    
    public void foo() {
        log.info("info 消息");
    }
}
```

## 四、日志级别

从低到高：

```
TRACE  → 最细，调试用
DEBUG  → 调试信息
INFO   → 普通信息（默认级别）
WARN   → 警告
ERROR  → 错误
```

**记忆**：**调试用的日志，生产环境关掉**。

| 级别 | 输出什么 | 生产环境 |
|------|---------|---------|
| TRACE | 变量值、循环细节 | ❌ 关 |
| DEBUG | SQL 语句、参数 | ⚠️ 重要接口开 |
| INFO | 业务流程关键节点 | ✅ 开 |
| WARN | 异常情况、可恢复 | ✅ 开 |
| ERROR | 严重错误、需介入 | ✅ 开 |

**输出规则**：设置为 INFO 后，DEBUG/TRACE 不会输出。

## 五、本项目日志配置

打开 `backend/src/main/resources/application.yml`：

```yaml
logging:
  level:
    com.pricemanagement: info        # 本项目代码 INFO 级
    org.springframework.security: warn  # Spring Security 只 WARN 级
    org.hibernate.SQL: debug        # SQL 语句 DEBUG
```

打开 `backend/src/main/resources/logback-spring.xml`（如果存在）：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- 控制台输出 -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- 文件输出（按天滚动） -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/application.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/application.%d{yyyy-MM-dd}.log.gz</fileNamePattern>
            <maxHistory>30</maxHistory>
            <totalSizeCap>10GB</totalSizeCap>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- 异步写日志（提高性能） -->
    <appender name="ASYNC_FILE" class="ch.qos.logback.classic.AsyncAppender">
        <queueSize>512</queueSize>
        <discardingThreshold>0</discardingThreshold>
        <neverBlock>true</neverBlock>
        <appender-ref ref="FILE"/>
    </appender>

    <!-- 日志级别 -->
    <logger name="com.pricemanagement" level="info"/>
    <logger name="org.springframework.security" level="warn"/>
    <logger name="org.hibernate.SQL" level="debug"/>

    <root level="info">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="ASYNC_FILE"/>
    </root>
</configuration>
```

**输出格式**：

```
2026-06-28 21:00:00.123 [http-nio-8080-exec-1] INFO  c.p.service.ProductService - 找到产品 name=铜精粉
│                              │                        │     │                       │
│                              │                        │     │                       └─ 日志消息
│                              │                        │     └─ 类名（缩写）
│                              │                        └─ 级别
│                              └─ 线程名
└─ 时间戳（精确到毫秒）
```

## 六、日志最佳实践

### 6.1 怎么打日志

```java
// ✅ 推荐：占位符
log.info("用户 {} 下单，订单号 {}，金额 {}", userId, orderId, amount);

// ❌ 不推荐：字符串拼接（每次都执行拼接）
log.info("用户 " + userId + " 下单，订单号 " + orderId);

// ⚠️ 容易踩坑：先判断再拼接
if (log.isDebugEnabled()) {
    log.debug("请求参数: " + expensiveToString(obj));   // 用占位符也行
}
```

**为什么用占位符？**

- 性能更好：级别不够时不拼接字符串
- 不会因为 toString 抛异常中断日志

### 6.2 日志内容规范

```java
// ✅ 推荐：包含关键信息
log.info("创建产品成功, id={}, name={}, operator={}", 
         product.getId(), product.getName(), currentUser());

// ❌ 不推荐：内容空洞
log.info("成功");
log.info("错误");
log.error("Exception occurred");  // 没传异常对象
```

**最佳实践**：

```java
// ✅ 关键事件必记
log.info("订单创建, orderId={}, userId={}, amount={}", orderId, userId, amount);
log.info("登录成功, username={}, ip={}", username, clientIp);
log.info("价格发布, productId={}, price={}, approver={}", productId, price, approver);

// ✅ 错误日志必带异常对象
try {
    doSomething();
} catch (Exception e) {
    log.error("处理失败, id={}", id, e);   // ← 第二个参数是异常对象
}

// ✅ 重要操作记耗时
long start = System.currentTimeMillis();
List<Product> list = productRepository.findAll();
log.info("查询产品耗时 {}ms, 共 {} 条", System.currentTimeMillis() - start, list.size());
```

### 6.3 敏感信息要过滤

```java
// ❌ 绝对不要打密码、密钥、身份证
log.info("用户登录, password={}", password);     // 严重违规！
log.info("JWT token: {}", token);                // 严重违规！
log.info("身份证号: {}", idCard);                 // 严重违规！

// ✅ 脱敏
log.info("用户登录, password=******");
log.info("身份证号: {}****{}", idCard.substring(0, 4), idCard.substring(14));
```

本项目**专门有 `@SensitiveData` 注解 + AOP 自动脱敏**（详见 `docs/plan/security-hardening-2026-q2.md`）。

### 6.4 避免日志风暴

```java
// ❌ 在循环里打日志
for (Product p : products) {
    log.info("处理产品: {}", p.getName());  // 一万条就刷屏
}

// ✅ 汇总打
log.info("开始批量处理 {} 个产品", products.size());
int success = 0, failed = 0;
for (Product p : products) {
    try {
        process(p);
        success++;
    } catch (Exception e) {
        failed++;
        log.warn("处理产品失败, id={}", p.getId(), e);
    }
}
log.info("批量处理完成, 总数={}, 成功={}, 失败={}", 
         products.size(), success, failed);
```

## 七、用 MDC 跟踪请求

**问题**：高并发场景下，日志交错，没法知道一条请求的所有日志。

**MDC（Mapped Diagnostic Context）**：在请求开始时塞一个 traceId，所有日志自动带上。

```java
public class TraceIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                     FilterChain chain) throws ServletException, IOException {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        MDC.put("traceId", traceId);
        try {
            chain.doFilter(req, res);
        } finally {
            MDC.remove("traceId");
        }
    }
}
```

日志配置加 MDC：

```xml
<pattern>%d{HH:mm:ss.SSS} [%thread] [%X{traceId}] %-5level %logger{36} - %msg%n</pattern>
```

输出：

```
21:00:00.123 [http-nio-8080-exec-1] [a1b2c3d4e5f6g7h8] INFO  c.p.s.ProductService - 查询产品 id=1
21:00:00.234 [http-nio-8080-exec-1] [a1b2c3d4e5f6g7h8] INFO  c.p.s.PriceService - 查询价格 id=1
21:00:00.345 [http-nio-8080-exec-1] [a1b2c3d4e5f6g7h8] INFO  c.p.s.ProductService - 返回结果
```

同一请求的所有日志都有同一个 traceId，一查就明。

## 八、查日志的常用技巧

### 8.1 Linux 下查日志

```bash
# 1. 看最新日志
tail -f logs/application.log

# 2. 找包含 "ERROR" 的行
grep "ERROR" logs/application.log

# 3. 找特定用户的所有操作
grep "userId=123" logs/application.log

# 4. 看某时间段的日志
sed -n '/2026-06-28 14:00:00/,/2026-06-28 15:00:00/p' logs/application.log

# 5. 统计错误数量
grep -c "ERROR" logs/application.log

# 6. 看堆栈（上下文 5 行）
grep -A 5 "NullPointerException" logs/application.log
```

### 8.2 工具

| 工具 | 用途 |
|------|------|
| `less` / `vim` | 看大文件 |
| `grep` | 按关键字搜 |
| `awk` | 按列提取 |
| ELK（Elasticsearch + Logstash + Kibana） | 日志聚合分析 |
| Loki + Grafana | 轻量级日志方案 |
| Sentry | 错误监控平台 |

## 九、本项目操作日志 vs 系统日志

**两类日志，作用不同**：

| 类型 | 用途 | 存储 | 谁看 |
|------|------|------|------|
| **系统日志**（Logback） | 排查问题、监控、调试 | 文件（logback 配置） | 开发/运维 |
| **操作日志**（`@OperationLog`） | 审计、追溯谁做了什么 | 数据库（operation_log 表） | 业务/审计 |

详见 [08 AOP 切面与操作日志](08-aop-operation-log.md)。

## 十、动手试试

### 实验 1：观察日志输出

启动应用，随便调几个接口，看控制台日志：

- 时间戳精确到毫秒了吗？
- 线程名是什么？
- 哪个类的哪个方法打的？

### 实验 2：开 DEBUG 看 SQL

修改 `application.yml`：

```yaml
logging:
  level:
    com.pricemanagement: debug
    org.hibernate.SQL: debug
    org.hibernate.orm.jdbc.bind: trace
```

重启应用，再调一个查询接口，看 Hibernate 打印的 SQL 和参数。

### 实验 3：故意制造一个异常

```java
@GetMapping("/boom")
public String boom() {
    String s = null;
    return s.toUpperCase();   // NPE
}
```

调一下，看日志里：
- 异常类型是什么？
- 堆栈显示在哪个文件的哪一行？

### 实验 4：打一个完整的业务日志

在 `ProductService.create()` 里加日志：

```java
@Transactional
public ProductDTO create(ProductDTO dto) {
    long start = System.currentTimeMillis();
    log.info("开始创建产品, name={}, operator={}", dto.getName(), currentUser());

    validate(dto);
    Product p = toEntity(dto);
    Product saved = productRepository.save(p);

    log.info("产品创建成功, id={}, cost={}ms", saved.getId(),
             System.currentTimeMillis() - start);
    return toDTO(saved);
}
```

观察 INFO 日志怎么输出。

## 十一、常见错误

| 错误 | 原因 | 解决 |
|------|------|------|
| 日志没输出 | 级别太低 | 调高级别 |
| 日志乱码 | 编码不一致 | 设 `<charset>UTF-8</charset>` |
| 日志文件超大 | 没设滚动 | 加 `<rollingPolicy>` |
| 日志打循环里 | 性能问题 | 汇总打 |
| 异步队列满 | 队列太小 | 调大 `<queueSize>` |
| 找不到 log 字段 | 没用 `@Slf4j` | 加注解或手动声明 |

## 十二、关键认知

1. **日志是给未来的自己看的**，现在偷懒将来吃苦
2. **INFO 记录关键事件**，DEBUG 记录细节，ERROR 必带异常对象
3. **不要打印敏感信息**（密码、身份证、Token）
4. **用占位符而不是字符串拼接**
5. **生产环境默认 INFO 级别**，临时排查可以开 DEBUG

---

下一步：[11 单元测试](11-testing.md) →

回头补课：
- [09 全局异常处理](09-exception-handling.md)
- [04 项目分层架构](04-layered-architecture.md)