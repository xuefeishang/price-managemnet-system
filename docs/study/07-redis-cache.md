# 07. Redis 缓存与性能

> 为什么需要缓存？Redis 是什么？怎么用？本项目怎么"缓存挂了也能跑"？

---

## 一、为什么需要缓存？

一个真实的性能问题：

```
用户：  "我打开产品列表要 5 秒钟！"
开发：  "SQL 跑了 3 秒，返回给前端 2 秒。"
```

每次都查数据库，数据库压力大、响应慢。**缓存**的思路：

> 把"经常查、很少变"的数据临时放在内存里，下次直接拿，不查数据库。

```
没有缓存：浏览器 → 后端 → MySQL（每次都查）→ 5 秒
有了缓存：浏览器 → 后端 → Redis（命中）→ 50 毫秒
                          → MySQL（未命中）→ 5 秒，但只这一次
```

**收益**：响应快 100 倍，数据库压力降为 1/N。

## 二、Redis 是什么？

**Redis = Remote Dictionary Server**，一个**内存数据库**。

特点：
- 数据存在内存里 → 极快（10 万+ QPS）
- 支持多种数据结构：字符串、哈希、列表、集合、有序集合
- 可以持久化到磁盘（但本项目用作纯缓存，不持久化）
- 单线程（Redis 6 之后 IO 多线程）

**对比 MySQL**：

| 特性 | MySQL | Redis |
|------|-------|-------|
| 存储位置 | 硬盘 | 内存 |
| 速度 | 慢（毫秒级） | 极快（微秒级） |
| 数据量 | TB 级 | GB 级 |
| 适合场景 | 主数据 | 临时数据 |

## 三、Spring Boot 集成 Redis

### 3.1 引入依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
```

### 3.2 配置连接

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:10.7.5.175}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      timeout: 5000ms
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0
```

### 3.3 核心类

| 类 | 作用 |
|----|------|
| `RedisTemplate<String, Object>` | 操作 Redis 的通用类 |
| `StringRedisTemplate` | 只存字符串 |
| `RedisConnectionFactory` | 连接工厂（Lettuce） |
| `@Cacheable` / `@CacheEvict` | Spring Cache 注解 |

## 四、两种使用方式

### 方式 1：Spring Cache 注解（声明式，最简单）

**3 个注解**：

```java
@Cacheable(value = "products", key = "#id")     // 先查缓存，没有就执行方法，结果放入缓存
public Product getById(Long id) { ... }

@CachePut(value = "products", key = "#product.id")   // 总是执行方法，结果放入缓存
public Product update(Product product) { ... }

@CacheEvict(value = "products", key = "#id")     // 删除缓存项
public void delete(Long id) { ... }

@CacheEvict(value = "products", allEntries = true)  // 清空整个缓存
public void clearAll() { ... }
```

**开启**：

```java
@SpringBootApplication
@EnableCaching    // ← 关键
public class PriceManagementApplication { ... }
```

**示例**：

```java
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Cacheable(value = "products", key = "#id", unless = "#result == null")
    public ProductDTO getById(Long id) {
        // 第一次进来：执行方法，查数据库，结果放入 Redis
        // 第二次进来：直接返回 Redis 里的，不进方法
        return productRepository.findById(id).map(this::toDTO).orElse(null);
    }

    @CacheEvict(value = "products", key = "#id")
    public void delete(Long id) {
        productRepository.deleteById(id);
    }
}
```

**优点**：侵入性低，一个注解搞定。
**缺点**：灵活性差，复杂场景写不了。

### 方式 2：RedisTemplate（命令式，最灵活）

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    public void set(String key, Object value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }

    public <T> T get(String key, Class<T> type) {
        return type.cast(redisTemplate.opsForValue().get(key));
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }

    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public Long increment(String key) {
        return redisTemplate.opsForValue().increment(key);
    }
}
```

**使用**：

```java
public List<ProductDTO> listActive() {
    String key = "products:active";
    List<ProductDTO> cached = redisCache.get(key, List.class);
    if (cached != null) {
        return cached;    // 命中缓存
    }

    List<ProductDTO> result = productRepository.findByStatus(ProductStatus.ACTIVE)
        .stream().map(this::toDTO).toList();

    redisCache.set(key, result, Duration.ofMinutes(10));   // 10 分钟过期
    return result;
}
```

**本项目主要用方式 2**，因为业务复杂（限流、计数器、分布式锁…）。

## 五、Redis 数据结构与典型用法

| 类型 | 用途 | 示例 |
|------|------|------|
| String | 存单个值、计数器 | 验证码：`"captcha:abc123" → "8d4f"` |
| Hash | 存对象的多个字段 | 用户信息：`"user:1" → {name, age, role}` |
| List | 队列、栈、最新列表 | 通知列表：`"notify:1" → [msg1, msg2]` |
| Set | 去重、标签、共同好友 | 已读消息 ID |
| Sorted Set | 排行榜、按时间排序 | 价格历史：score = 时间戳 |
| HyperLogLog | 基数统计（UV） | 访问用户数 |

**本项目的典型场景**：

```
# 验证码（String，TTL 5 分钟）
SET captcha:session123 ABCD EX 300

# 限流计数器（String + INCR）
INCR ratelimit:login:192.168.1.1
EXPIRE ratelimit:login:192.168.1.1 60

# 字典缓存（Hash）
HSET dict:product_status "ACTIVE" "启用" "DISABLED" "停用"

# 分布式锁（SET NX EX）
SET lock:order:12345 "uuid" NX EX 30

# 缓存用户 Session 信息（Hash）
HSET session:token123 "userId" "1" "role" "ADMIN"
```

## 六、缓存的三大问题

### 6.1 缓存穿透

**问题**：查一个**根本不存在**的数据，缓存永远不命中，每次都打到数据库。

**解决**：
- 缓存空值：`null` 也存进 Redis，TTL 短一些
- 布隆过滤器：提前判断 key 是否可能存在

### 6.2 缓存击穿

**问题**：某个**热点 key 过期**的瞬间，大量请求同时打到数据库。

**解决**：
- 永不过期（后台异步更新）
- 互斥锁：`SETNX` 只让一个线程去查数据库，其他等
- 逻辑过期：value 里存过期时间，应用层判断

### 6.3 缓存雪崩

**问题**：大量 key **同时过期**，或者 **Redis 挂了**，所有请求都打数据库。

**解决**：
- 过期时间加随机值，避免同时过期
- 多级缓存（本地缓存 + Redis）
- Redis 集群（主从 + 哨兵）
- **熔断降级**：Redis 挂了直接走数据库（**本项目的做法**）

## 七、本项目的亮点：缓存懒加载 + 降级

**问题**：Redis 是外部依赖，万一挂了，应用启动不了？

**默认行为**（坑）：

```
应用启动 → 创建 Redis 连接 → 连不上 → 启动失败
```

**本项目的设计**：

```
应用启动 → 尝试连接 Redis
       ├─ 成功 → 正常用
       └─ 失败 → 记录日志，降级为"无缓存"模式，应用照常启动
            ├─ 读：直接查数据库
            ├─ 写：直接写数据库
            └─ 后台线程重试连接，恢复后自动启用缓存
```

**实现要点**：

```java
// application.yml
management:
  health:
    redis:
      enabled: false   # 关闭 Redis 健康检查，不让它阻塞启动

// RedisConnectionFactory 配置里，捕获连接异常
// 不让它抛到 Spring 启动流程中
```

**为什么这样做？**

对于一个生产系统，**应用可用性 > 缓存可用性**。

| 场景 | 没降级 | 有降级 |
|------|--------|--------|
| Redis 启动慢 30 秒 | 应用启动卡 30 秒 | 应用照常启动，Redis 上来后再用 |
| Redis 半夜挂了 | 应用 OOM、全挂 | 应用继续跑，慢一点但不挂 |
| Redis 集群切换 | 应用重启 | 应用无感知 |

这是非常重要的**生产级**思维。

## 八、Spring Cache vs RedisTemplate

| 维度 | Spring Cache | RedisTemplate |
|------|--------------|---------------|
| 复杂度 | 简单（注解） | 中等（写代码） |
| 灵活性 | 低 | 高 |
| 分布式 | 支持 | 支持 |
| 监控 | 不直观 | 自己埋点 |
| 适用场景 | 简单缓存 | 复杂业务 |

**本项目的实践**：两者结合。

- 简单的列表缓存用 `@Cacheable`
- 复杂的业务（限流、分布式锁）用 `RedisTemplate`

## 九、动手试试

### 实验 1：观察 Redis 缓存

1. 启动应用
2. 用 Redis 客户端连接：`redis-cli -h 10.7.5.175 -a yourpass`
3. 调用 `GET /api/products/1` 接口
4. 查 Redis：`KEYS *product*` 看到缓存键
5. 再调一次接口，发现响应快了很多

### 实验 2：写一个带缓存的 Service

```java
@Service
@RequiredArgsConstructor
public class DictService {
    private final DictRepository dictRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CACHE_KEY = "dict:status";
    private static final Duration TTL = Duration.ofMinutes(30);

    public Map<String, String> getAllStatus() {
        // 1. 查缓存
        Object cached = redisTemplate.opsForValue().get(CACHE_KEY);
        if (cached instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().toString(),
                                          e -> e.getValue().toString()));
        }

        // 2. 查数据库
        Map<String, String> result = dictRepository.findAllByCategory("status")
            .stream()
            .collect(Collectors.toMap(Dict::getKey, Dict::getLabel));

        // 3. 写回缓存
        redisTemplate.opsForValue().set(CACHE_KEY, result, TTL);
        return result;
    }
}
```

### 实验 3：模拟 Redis 挂掉

1. 启动应用
2. 在配置里把 Redis 端口改成 9999（不存在的）
3. 观察应用能否正常启动，能否正常访问不依赖缓存的接口

---

## 十、Redis 选型与部署

| 场景 | 推荐 |
|------|------|
| 单点测试 | 单实例 Redis |
| 生产小规模 | Redis 主从 + 哨兵 |
| 生产大规模 | Redis Cluster（至少 3 主 3 从） |
| 不想运维 | 云厂商托管（阿里云 Redis、AWS ElastiCache） |

本项目用的是单实例 Redis（部署在内网 10.7.5.175）。

---

## 十一、关键学习要点

1. **Redis 不是数据库**，不要把重要数据只放 Redis
2. **缓存要设过期时间**，否则内存会撑爆
3. **更新数据库时记得清缓存**，否则会有脏数据
4. **Redis 挂了不能让应用挂**，要有降级方案
5. **不要缓存大对象**（如整个商品列表），按业务粒度切分
6. **缓存不是越多越好**，要看命中率

---

下一章：[08 AOP 切面与操作日志](08-aop-operation-log.md) →

回头补课：[05 JPA 与数据持久化](05-jpa-persistence.md)