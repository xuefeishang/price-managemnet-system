# 16. 性能调优入门：让系统从"能跑"到"跑得快"

> 系统上线后总会有"好慢啊"的反馈。这一章讲**怎么定位瓶颈、怎么优化**。

---

## 一、性能调优的总体思路

```
发现慢 → 定位瓶颈 → 优化 → 验证 → 上线

瓶颈通常出现在：
1. 数据库（80% 的性能问题）
2. 缓存设计
3. 应用代码（循环、N+1、锁）
4. JVM 参数
5. 网络（HTTP、序列化）
```

**铁律**：

> **先定位，再优化。不要瞎调。**

## 二、定位瓶颈：看哪个慢

### 2.1 慢在哪一层？

用链路追踪（traceId）：

```
用户请求
  ├─ Nginx       10ms
  ├─ 应用接收     5ms
  ├─ 业务处理     50ms
  │   ├─ 查 Redis   2ms
  │   ├─ 查 MySQL  200ms ← 慢在这！
  │   └─ 计算       10ms
  ├─ 序列化返回    5ms
  └─ 网络         30ms
```

**80% 的慢都在数据库**。

### 2.2 工具

| 工具 | 用途 |
|------|------|
| **Arthas** | 在线诊断，不用重启 |
| **JProfiler** | JVM 性能分析 |
| **VisualVM** | 监控 JVM 状态 |
| **MySQL Slow Query Log** | 慢 SQL 日志 |
| **EXPLAIN** | 分析 SQL 执行计划 |
| **Prometheus + Grafana** | 监控大盘 |
| **SkyWalking** | 分布式链路追踪 |

## 三、数据库调优（最常遇到）

### 3.1 找出慢 SQL

**开启慢查询日志**（`my.cnf`）：

```ini
slow_query_log = ON
slow_query_log_file = /var/log/mysql/slow.log
long_query_time = 1          # 超过 1 秒算慢
log_queries_not_using_indexes = ON   # 没用索引的也记
```

**查看慢 SQL**：

```bash
mysqldumpslow -s t -t 10 /var/log/mysql/slow.log
# -s t 按时间排序
# -t 10 看前 10 条
```

### 3.2 EXPLAIN 分析 SQL

```sql
EXPLAIN SELECT * FROM product WHERE category_id = 100 AND status = 'ACTIVE';
```

**关键看**：

| 列 | 关注点 |
|----|--------|
| `type` | 访问类型：system > const > eq_ref > ref > range > index > **ALL**（全表扫描，慢） |
| `key` | 实际用到的索引 |
| `rows` | 扫描行数（越小越好） |
| `Extra` | 额外信息：`Using filesort`、`Using temporary` 都是坏味道 |

**示例输出**：

```
type: ref           ← 用到了索引，good
key: idx_category   ← 用了 idx_category
rows: 50            ← 扫描 50 行
Extra: Using where  ← 没用 filesort，good
```

### 3.3 索引优化

**最常见的优化手段**。

#### 索引原则

```sql
-- ✅ 命中索引
SELECT * FROM product WHERE id = 1;                   -- 主键
SELECT * FROM product WHERE category_id = 100;        -- 单列索引
SELECT * FROM product WHERE category_id = 100 AND status = 'ACTIVE';  -- 联合索引

-- ❌ 不命中索引
SELECT * FROM product WHERE name LIKE '%铜%';         -- 前导通配符
SELECT * FROM product WHERE YEAR(created_time) = 2026; -- 函数
SELECT * FROM product WHERE price + 1 = 5000;         -- 表达式
```

#### 联合索引最左前缀

```sql
-- 创建联合索引
CREATE INDEX idx_cat_status ON product(category_id, status);

-- ✅ 命中
WHERE category_id = 100 AND status = 'ACTIVE'
WHERE category_id = 100

-- ❌ 不命中（跳过了 category_id）
WHERE status = 'ACTIVE'

-- ✅ 部分命中
WHERE category_id > 100 AND status = 'ACTIVE'   -- 只用 category_id
```

#### 覆盖索引

```sql
-- 只查索引列，不用回表
SELECT id FROM product WHERE category_id = 100;   -- 覆盖索引
```

### 3.4 避免 SELECT *

```sql
-- ❌ 慢：返回所有列
SELECT * FROM product WHERE category_id = 100;

-- ✅ 快：只查需要的列
SELECT id, name, price FROM product WHERE category_id = 100;
```

### 3.5 分页优化

**深分页慢**：`LIMIT 1000000, 20` 会扫 100 万行。

```sql
-- ❌ 慢：偏移越大越慢
SELECT * FROM product ORDER BY id LIMIT 1000000, 20;

-- ✅ 快：用 id 范围
SELECT * FROM product WHERE id > 1000000 ORDER BY id LIMIT 20;
```

### 3.6 避免 N+1 查询

**问题代码**：

```java
// 1 次查产品列表
List<Product> products = productRepository.findAll();

// N 次查分类
for (Product p : products) {
    Category c = categoryRepository.findById(p.getCategoryId()).get();
    p.setCategoryName(c.getName());
}
// 总共 N+1 次查询
```

**优化**：

```java
// 1 次查所有
@Query("SELECT p, c FROM Product p LEFT JOIN Category c ON p.categoryId = c.id")
List<Object[]> rows = ...;

// 或用 JOIN FETCH
@Query("SELECT p FROM Product p JOIN FETCH p.category")
List<Product> products = productRepository.findAllWithCategory();
// 1 次查询
```

### 3.7 批量操作

```java
// ❌ 慢：N 次 INSERT
for (Product p : products) {
    productRepository.save(p);  // 每次都执行 SQL
}

// ✅ 快：1 次批量
@Modifying
@Query(value = "INSERT INTO product (name, price) VALUES (:name, :price)",
       nativeQuery = true)
void batchInsert(@Param("name") String name, @Param("price") BigDecimal price);
// 或用 saveAll()
productRepository.saveAll(products);
```

### 3.8 本项目 JPA 配置

`application.yml`：

```yaml
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: 50   # 批量抓取大小
        jdbc:
          batch_size: 50               # 批量更新大小
        order_inserts: true             # 优化 INSERT 顺序
        order_updates: true            # 优化 UPDATE 顺序
```

## 四、缓存优化

### 4.1 缓存策略

| 策略 | 适用 |
|------|------|
| **Cache-Aside**（最常用） | 读多写少 |
| **Write-Through** | 强一致 |
| **Write-Behind** | 写多读少 |
| **Refresh-Ahead** | 热点数据 |

### 4.2 Cache-Aside 模式

```
读：
  1. 查缓存
  2. 命中 → 返回
  3. 未命中 → 查 DB
  4. 写缓存（设 TTL）
  5. 返回

写：
  1. 更新 DB
  2. 删缓存（不是更新！）
```

**为什么删而不是更新？**

```
更新缓存：A = 5
并发：A = 5 → A = 7 → A = 6
最后缓存可能是 7，但 DB 是 6，不一致
```

**懒加载双删**：

```java
public void update(Long id, Product p) {
    redis.delete("product:" + id);    // 1. 先删
    productRepository.save(p);        // 2. 更新 DB
    sleep(500ms);                     // 3. 等待可能存在的并发读完成
    redis.delete("product:" + id);    // 4. 再删一次
}
```

### 4.3 缓存粒度

```java
// ❌ 不好：粒度过大
@Cacheable("products")
public List<Product> findAll() { ... }

// ✅ 好：按 ID 粒度
@Cacheable(value = "product", key = "#id")
public Product findById(Long id) { ... }
```

### 4.4 缓存预热

```java
@Component
public class CacheWarmer implements ApplicationRunner {
    @Override
    public void run(ApplicationArguments args) {
        log.info("开始预热缓存");
        List<Product> all = productRepository.findAll();
        all.forEach(p -> redis.set("product:" + p.getId(), p));
        log.info("预热完成，共 {} 条", all.size());
    }
}
```

## 五、Tomcat 调优

`application.yml`：

```yaml
server:
  tomcat:
    threads:
      max: 200              # 最大线程数
      min-spare: 10         # 最小空闲
    accept-count: 100       # 等待队列长度
    max-connections: 8192
    connection-timeout: 5000
```

**线程数计算**：

```
线程数 = (线程 IO 时间 + 线程 CPU 时间) / 线程 CPU 时间 × CPU 数

例如：IO 100ms + CPU 50ms，8 核
线程数 = 150 / 50 × 8 = 24
```

## 六、JVM 调优

### 6.1 内存参数

```bash
java -Xms2g -Xmx2g \
     -Xmn1g \
     -XX:MetaspaceSize=256m \
     -XX:+UseG1GC \
     -XX:MaxGCPauseMillis=200 \
     -jar app.jar
```

| 参数 | 含义 | 推荐 |
|------|------|------|
| `-Xms` | 初始堆 | 等于 -Xmx（避免动态扩容） |
| `-Xmx` | 最大堆 | 物理内存的 1/4 ~ 1/2 |
| `-Xmn` | 年轻代 | 堆的 1/3 ~ 1/2 |
| `-XX:+UseG1GC` | 用 G1 垃圾回收器 | JDK 9+ 默认 |
| `-XX:MaxGCPauseMillis` | 最大 GC 停顿 | 200ms |

### 6.2 容器环境特别配置

Docker / K8s 中要识别容器内存限制：

```bash
-XX:+UseContainerSupport
-XX:MaxRAMPercentage=75.0   # 使用容器内存的 75%
```

### 6.3 用 Arthas 诊断

```bash
# 下载
curl -O https://arthas.aliyun.com/arthas-boot.jar

# 启动（会列出所有 Java 进程）
java -jar arthas-boot.jar

# 常用命令
dashboard                  # 看总览
thread                     # 看线程
jvm                        # 看 JVM 参数
memory                     # 看内存
trace com.pricemanagement.service.ProductService list '#cost>50'
                           # 跟踪方法，看耗时 > 50ms 的
watch com.pricemanagement.service.ProductService list '{params, returnObj, throwExp, cost}'
                           # 监控方法的入参、返回值、异常、耗时
```

## 七、SQL 调优案例

### 案例 1：深分页慢

**问题**：`SELECT * FROM price ORDER BY created_time LIMIT 100000, 20` 慢

**解决**：

```sql
-- 用主键游标分页
SELECT * FROM price
WHERE id > 上一页最后一条的id
ORDER BY id
LIMIT 20;
```

### 案例 2：N+1

**问题**：列表接口 1+50 次查询

**解决**：

```java
@Query("SELECT p FROM Product p JOIN FETCH p.category WHERE p.status = 'ACTIVE'")
List<Product> findActiveWithCategory();
```

### 案例 3：全表扫描

**问题**：`WHERE name LIKE '%铜%'` 全表扫

**解决**：

```sql
-- MySQL 5.7+
-- 全文索引
ALTER TABLE product ADD FULLTEXT INDEX ft_name (product_name);
SELECT * FROM product WHERE MATCH(product_name) AGAINST('铜' IN NATURAL LANGUAGE MODE);

-- 或用专门的搜索：Elasticsearch
```

### 案例 4：锁等待

**问题**：行锁等待，超时

**解决**：

```sql
-- 看锁等待
SELECT * FROM information_schema.innodb_trx;
SELECT * FROM information_schema.innodb_locks;

-- 优化：缩短事务时间、用乐观锁、加索引（避免锁表）
```

## 八、应用代码优化

### 8.1 异步处理

```java
@Async
public CompletableFuture<List<Product>> loadAsync() {
    return CompletableFuture.completedFuture(productRepository.findAll());
}
```

### 8.2 避免循环查数据库

```java
// ❌
for (Long id : ids) {
    productRepository.findById(id);  // N 次查询
}

// ✅
List<Product> products = productRepository.findAllById(ids);  // 1 次
```

### 8.3 字符串拼接

```java
// ❌ 慢：循环里 String +
String s = "";
for (int i = 0; i < 1000; i++) {
    s += i;   // 每次 new String
}

// ✅ 快：StringBuilder
StringBuilder sb = new StringBuilder();
for (int i = 0; i < 1000; i++) {
    sb.append(i);
}
```

### 8.4 大集合处理

```java
// ❌ 内存爆炸
List<Product> all = productRepository.findAll();  // 100 万条

// ✅ 流式 + 分页
PageRequest pageable = PageRequest.of(0, 1000);
Page<Product> page;
do {
    page = productRepository.findAll(pageable);
    process(page.getContent());
    pageable = pageable.next();
} while (page.hasNext());
```

### 8.5 减少反射

```java
// ❌ 频繁反射
for (Product p : products) {
    BeanUtils.copyProperties(dto, p);  // 每次都反射
}

// ✅ 用 MapStruct 编译期生成
```

## 九、HTTP 接口优化

### 9.1 启用 GZIP

```yaml
server:
  compression:
    enabled: true
    mime-types: application/json,text/html
```

### 9.2 减少 HTTP 请求

```typescript
// ❌ 一个表格 10 次请求
products.forEach(p => fetch(`/api/category/${p.categoryId}`));

// ✅ 后端一次性返回带分类名
GET /api/products?with=category
```

### 9.3 分页 + 懒加载

前端不要一次拉全部数据，用分页 + 滚动加载。

### 9.4 WebSocket / SSE

实时数据（价格变动、通知）用 WebSocket，不要前端轮询。

## 十、监控与告警

### 10.1 关键指标

| 指标 | 正常范围 | 监控 |
|------|---------|------|
| **QPS** | 业务相关 | Grafana |
| **RT**（响应时间） | < 200ms | Grafana |
| **错误率** | < 0.1% | Grafana |
| **JVM 堆使用** | < 80% | Actuator + Prometheus |
| **DB 连接池** | < 80% | Druid 监控 |
| **缓存命中率** | > 80% | Redis INFO |
| **GC 次数** | 偶尔 | GC log |

### 10.2 Actuator

`application.yml`：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, info, metrics, prometheus
  endpoint:
    health:
      show-details: always
```

访问 `http://localhost:8080/actuator/metrics` 看指标。

### 10.3 告警

本项目配置了钉钉/企微告警（详见 [10 日志](10-logging.md)）：

```yaml
alert:
  enabled: true
  ding-talk:
    enabled: true
    webhook: https://oapi.dingtalk.com/robot/send?access_token=xxx
  threshold:
    memory-usage: 90.0
    cpu-usage: 80.0
```

## 十一、性能优化 checklist

上线前检查：

```
数据库
  □ 慢查询日志开了吗？
  □ 关键 SQL 跑了 EXPLAIN？
  □ 索引覆盖主要查询吗？
  □ N+1 问题排查了吗？
  □ 分页 SQL 优化了吗？

缓存
  □ 热点数据缓存了吗？
  □ 缓存命中率多少？
  □ 缓存更新策略对吗？（先 DB 后缓存）

应用
  □ 循环里没有 IO？
  □ 事务尽量短？
  □ 异步能异步的？
  □ 大对象能分页吗？

JVM
  □ 堆内存设置合理？
  □ GC 策略选对了吗？
  □ 内存泄漏排查了吗？

监控
  □ 关键指标有监控吗？
  □ 异常有告警吗？
  □ 日志能查问题吗？
```

## 十二、动手试试

### 实验 1：跑 EXPLAIN

```sql
EXPLAIN SELECT * FROM product WHERE category_id = 100;
```

看 type 和 key。

### 实验 2：用 Arthas trace

```bash
java -jar arthas-boot.jar
trace com.pricemanagement.service.ProductService list -n 5
# 跑 5 次方法，看每步耗时
```

### 实验 3：测压

用 **JMeter** 或 **wrk** 测接口：

```bash
# wrk：100 并发 30 秒
wrk -t100 -c100 -d30s http://localhost:8080/api/products

# 输出：QPS、平均延迟、99%延迟
```

### 实验 4：看 JVM

```bash
jps            # Java 进程
jstat -gc <pid> 1000    # 每秒打印 GC 情况
jmap -heap <pid>        # 看堆内存分布
jstack <pid> | head -30 # 看线程栈
```

## 十三、关键认知

1. **80% 的性能问题在数据库**
2. **先定位再优化**：用 EXPLAIN、Arthas、监控
3. **缓存不是越多越好**：命中率比数量重要
4. **JVM 默认参数能用**：出问题再调
5. **监控先行**：出问题能第一时间发现
6. **性能是设计出来的，不是优化出来的**：表结构、SQL 设计时要考虑

## 十四、推荐资源

| 资源 | 类型 |
|------|------|
| **《高性能 MySQL》** | 书，必读 |
| **《Java 性能权威指南》** | 书 |
| **Arthas 官方文档** | 在线，必备 |
| **MySQL EXPLAIN 文档** | 文档 |
| **JVM 调优实战** | 课程 |

---

下一步：[99 学习路径](99-learning-path.md) →

回头补课：
- [05 JPA 与数据持久化](05-jpa-persistence.md)
- [07 Redis 缓存与性能](07-redis-cache.md)
- [10 日志框架](10-logging.md)