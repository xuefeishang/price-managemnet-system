# 99. 学习路线图与实战建议

> 教材全在这里了，最后给你一份**带时间表的学习计划**和**上手实操的指引**。

---

## 一、整体规划

```
 0 周         2 周         4 周         6 周         8 周
  │            │            │            │            │
  ▼            ▼            ▼            ▼            ▼
启蒙        入门        能改        能写         能设计
```

| 阶段 | 时间 | 目标 | 完成标志 |
|------|------|------|---------|
| **启蒙** | 第 1 周 | 知道后台是什么、跑起来 | 能本地启动项目、看到界面 |
| **入门** | 第 2-3 周 | 读懂现有代码 | 能跟着 Debug 看懂一个请求的全流程 |
| **上手** | 第 4-5 周 | 能修改现有功能 | 独立修复一个简单 Bug |
| **实战** | 第 6-7 周 | 能写新功能 | 独立加一个增删改查接口 |
| **进阶** | 第 8 周+ | 能设计架构 | 能设计新模块的表结构、API、缓存方案 |

## 二、四周冲刺计划（每天 2 小时）

> 适合下班后或周末学习，目标是"能改能写"。

### 第 1 周：环境与跑通

| 天 | 任务 | 时长 |
|----|------|------|
| Day 1 | 安装 JDK 25、Maven、IDEA | 1h |
| Day 2 | 拉代码、配置 `application.yml`、启动后端 | 2h |
| Day 3 | 启动前端、调通登录、随便点点 | 1h |
| Day 4 | 读 [01 宏观架构](01-architecture-overview.md)，画一张系统图 | 1.5h |
| Day 5 | 读 [00c Java 语法入门](00c-java-syntax.md) + [02 Java 高级](02-java-advanced.md) | 2h |
| Day 6 | 在 IDE 里 Debug 一个 Controller 的入口方法 | 2h |
| Day 7 | 总结，写下 3 个"我已经懂的"和 3 个"我还不懂的" | 1h |

**周末验收**：能在 IDEA 里打断点，跟踪一个"查产品"请求走完 controller → service → repository → MySQL。

### 第 2 周：核心概念

| 天 | 任务 |
|----|------|
| Day 8-9 | 读 [03 Spring Boot](03-spring-boot.md)，跟着做实验 1-3 |
| Day 10-11 | 读 [04 分层架构](04-layered-architecture.md)，用一个下午把所有包过一遍 |
| Day 12-13 | 读 [05 JPA](05-jpa-persistence.md)，挑 3 个 Entity 写一个简单查询 |
| Day 14 | 写学习笔记：把所有注解抄下来，贴便签，贴屏幕 |

**周末验收**：能不查文档，说出"controller 拿到请求后调用谁、为什么这么调"。

### 第 3 周：安全与缓存

| 天 | 任务 |
|----|------|
| Day 15-16 | 读 [06 Security & JWT](06-security-jwt.md)，跟着流程画一张图 |
| Day 17 | 跟踪一次完整的登录过程，记录每个步骤 |
| Day 18-19 | 读 [07 Redis 缓存](07-redis-cache.md)，动手用 Redis 客户端看缓存 |
| Day 20-21 | 模仿 `@OperationLog` 写一个自己的注解 + 切面 |

**周末验收**：能向别人讲清楚"登录是怎么实现的、Token 怎么校验的"。

### 第 4 周：第一个实战

| 天 | 任务 |
|----|------|
| Day 22 | 选一个简单的需求（见下文） |
| Day 23 | 写 plan 文件（设计文档） |
| Day 24-25 | 写 Entity、Repository、Service、Controller |
| Day 26 | 写 Flyway 迁移脚本 |
| Day 27 | 自测 + Debug |
| Day 28 | 写文档、写测试 |

**周末验收**：你写的新功能能在浏览器里跑起来。

## 三、四周速成后的延伸方向

| 方向 | 学什么 | 难度 | 价值 |
|------|--------|------|------|
| **性能调优** | JVM 调优、SQL 慢查询分析、缓存策略 | ★★★ | 高 |
| **分布式** | 微服务（Spring Cloud / Dubbo）、分布式事务、消息队列 | ★★★★ | 高 |
| **DevOps** | Docker、K8s、CI/CD、Prometheus 监控 | ★★★ | 高 |
| **安全深入** | 渗透测试、OWASP Top 10、安全编码规范 | ★★★ | 高 |
| **领域驱动设计** | DDD、微服务拆分 | ★★★★ | 高 |
| **响应式** | WebFlux、R2DBC | ★★★ | 中 |
| **云原生** | Serverless、Service Mesh | ★★★★ | 中 |

## 四、本项目可以"动手练"的小任务

按难度从低到高排列：

### 任务 1：增加一个查询字段（⭐）

需求：在产品列表加一个"创建人姓名"字段。

步骤：
1. `ProductDTO` 加 `createdByName`
2. `ProductService` 查 Product 时关联查 User
3. `ProductController` 不变（自动返回）
4. 前端表格加列

预计 1-2 小时。

### 任务 2：加一个新接口（⭐⭐）

需求：新增"按产地统计产品数量"接口 `GET /api/products/stats/by-origin`。

步骤：
1. `ProductRepository` 加 `@Query`
2. `ProductStatsDTO` 新建
3. `ProductService` 加方法
4. `ProductController` 加 `@GetMapping("/stats/by-origin")`
5. 前端加菜单、页面、ECharts 图表

预计半天。

### 任务 3：加一个审批流（⭐⭐⭐）

需求：产品创建要走审批，审批通过后才生效。

涉及的知识点：
- 新建 `ApprovalWorkflow`、`ApprovalRecord` Entity
- 状态机（草稿、审批中、通过、驳回）
- `@OperationLog` 记录审批历史
- 通知给审批人

预计 1-2 天。

### 任务 4：把一个功能拆成微服务（⭐⭐⭐⭐）

需求：把价格服务拆出去，独立部署。

涉及的知识点：
- Spring Cloud / Dubbo
- 服务注册与发现（Nacos / Eureka）
- 配置中心（Nacos Config）
- API 网关（Spring Cloud Gateway）
- 分布式事务（Seata）

预计 1 周+。

## 五、推荐学习资源

### 5.1 入门必看

| 资源 | 类型 | 适用 |
|------|------|------|
| [廖雪峰 Java 教程](https://liaoxuefeng.com) | 在线教程 | 零基础入门 |
| 《Head First Java》 | 书 | 入门图解 |
| 《Java 核心技术》 | 书 | 系统学习 |

### 5.2 Spring 生态

| 资源 | 类型 | 适用 |
|------|------|------|
| [Spring 官方文档](https://spring.io/docs) | 文档 | 一切答案都在这 |
| [Spring Boot Reference](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/) | 文档 | 启动器、配置、自动装配 |
| 《Spring 实战（第 6 版）》 | 书 | 系统学习 |
| [Spring Academy](https://spring.academy/) | 在线课 | 免费 |

### 5.3 实战 & 进阶

| 资源 | 类型 | 适用 |
|------|------|------|
| [Baeldung](https://www.baeldung.com/) | 博客 | Spring 各种细节 |
| [Java Guides](https://www.javaguides.net/) | 博客 | 入门到中级 |
| [DZone](https://dzone.com/) | 社区 | 看趋势 |
| 慕课网 / 极客时间 | 课程 | 中文实战课 |

### 5.4 工具

| 工具 | 用途 |
|------|------|
| **IDEA** | 主力 IDE（社区版免费） |
| **Maven Helper** | IDEA 插件，看依赖冲突 |
| **Lombok** | IDEA 插件 |
| **MyBatis Log** | 格式化 SQL |
| **Postman / Apifox** | 接口调试 |
| **Redis Desktop Manager** | 看 Redis 数据 |
| **DBeaver** | 通用数据库客户端 |
| **Arthas** | 阿里开源的 Java 诊断工具 |

## 六、学习原则

### 6.1 跑起来 > 看懂

```diff
- ❌ "我看了 10 篇 Spring Boot 教程，但还是不会写"
+ ✅ "我跑起来一个 Hello World，然后改成查数据库"
```

### 6.2 改一行 > 抄十遍

```diff
- ❌ 抄 10 遍教程代码，感觉都会了
+ ✅ 改 1 行教程代码，看到运行结果不一样了
```

### 6.3 写出来 > 想明白

```diff
- ❌ "我懂了，但要我自己写就写不出来"
+ ✅ "我先写出来，跑一下，看哪里和我想的不一样"
```

### 6.4 教别人 > 学给自己

学完一个东西，**用最简单的语言讲给非技术人员听**。
讲得出来 = 真懂；讲不出来 = 还在懵。

### 6.5 项目驱动 > 知识点驱动

```diff
- ❌ "我要学完 Spring 全家桶再写项目"  → 3 个月还在看文档
+ ✅ "我要给系统加个功能，顺便学用到的技术"  → 1 周搞定
```

## 七、本项目专属学习技巧

### 7.1 用 Debug 学代码

1. 找一个 Controller 方法，左边打断点
2. 用前端调一次接口
3. IDEA 会停在断点
4. **F8 步过 / F7 步入**，一层一层往下走
5. 看每个变量、每个调用

**这是学 Java 后台最快的办法，没有之一。**

### 7.2 用 Git 学历史

```bash
git log --oneline -- backend/src/main/java/com/pricemanagement/controller/ProductController.java
```

看一个文件被改过几次、为什么改，**比看代码本身更深刻**。

### 7.3 用 AI 学代码（但要警惕）

可以问 AI：

- "这段代码在做什么业务？"
- "为什么这里用 @Transactional？"
- "Spring Boot 启动流程是什么？"

但**不能**让 AI 替你写代码——你会错过"卡住然后想通"的过程，那是真正学会的时刻。

### 7.4 写学习笔记

推荐用 Markdown，建一个 `my-study/` 文件夹：

```
my-study/
├── 2026-06-28-spring-ioc.md
├── 2026-06-30-jpa-query.md
├── questions.md     ← 卡住的地方
└── cheatsheet.md    ← 高频查阅的速查表
```

## 八、卡住了怎么办？

| 卡住的地方 | 怎么办 |
|-----------|--------|
| Java 语法 | [廖雪峰](https://liaoxuefeng.com) / StackOverflow / 问 AI |
| Spring 配置 | 查官方文档、问 AI 报错信息 |
| SQL 不对 | 打开 `application.yml` 设 `jpa.show-sql: true`，看输出 |
| 不知道接口在哪 | 全局搜索 `@RequestMapping("/api/...")` |
| 报错看不懂 | 把报错丢给 AI / Google |
| 业务看不懂 | 找前端页面反推、看 CLAUDE.md |
| 编译错 | 看 IDEA 红色提示，Alt+Enter 让 IDEA 帮你 |
| 启动不起来 | 看启动日志最后一段，正常都有错误信息 |

## 九、给你的鼓励

后台开发**不难**，但**繁**。它不像前端能立刻看到效果，需要耐住性子：

- 调通第一个接口：兴奋
- 调通第二个接口：还行
- 调到第五个：有点烦
- 调到第十个：哇我居然懂了

**到了"哇我懂了"的那天，你就入门了。**

---

## 十、回到教材目录

- [01 宏观架构概览](01-architecture-overview.md)
- [02 Java 高级特性](02-java-advanced.md)
- [03 Spring Boot 与 IoC 核心](03-spring-boot.md)
- [04 项目分层架构](04-layered-architecture.md)
- [05 JPA 与数据持久化](05-jpa-persistence.md)
- [06 Spring Security 与 JWT](06-security-jwt.md)
- [07 Redis 缓存与性能](07-redis-cache.md)
- [08 AOP 切面与操作日志](08-aop-operation-log.md)

**建议阅读顺序**：01 → 02 → 03 → 04 → 05 → 06 → 07 → 08 → 99，每章配合作业完成。

学完这套教材，你就能：

- ✅ 独立看懂这个项目 90% 的代码
- ✅ 给项目加一个简单功能
- ✅ 在简历上写"熟悉 Spring Boot 全家桶开发"
- ✅ 找到一份 Java 后台开发工作

加油！