# 后台 + 前端技术学习教材

> 面向 **Java / Spring Boot 初学者**，结合本项目（矿产品价格管理系统）的真实代码讲解后台 + 前端开发。

---

## 这是什么？

这是一套从 **零基础 → 能独立给项目加功能** 的渐进式教材。
每章都遵循同一个原则：**先讲"是什么 / 解决什么问题"，再讲"怎么用"，最后落到本项目的一个真实例子**。

## 👋 你是哪种读者？

| 画像 | 建议 |
|------|------|
| 🅰️ **会 Java，没碰过 Spring Boot** | 跳过 00x 前置篇，从 01 开始 |
| 🅱️ **完全零基础**（不会 Java、不会 SQL、不会前端） | 从 00 开始，按顺序读 |
| 🅲️ **只想搞清楚某个具体技术** | 看下面目录选章节 |
| 🅳️ **要部署项目到生产环境** | 看 12 Docker 部署 |
| 🅴️ **要写测试 / 提高代码质量** | 看 11 单元测试 |
| 🅵️ **只会后台，想学前端** | 看 13 → 14 → 15 |
| 🅶️ **要排查慢问题** | 看 16 性能调优 |

## 学习目标

读完这套教材，你应该能够：

1. 说出本项目后台 + 前端用到的 **所有核心技术** 及其作用
2. 读懂任意一个 Controller / Service / Repository / Vue 组件
3. 理解为什么项目要分这么多层
4. 跟着教程 **独立给系统加一个小功能**
5. 用 Docker 把项目部署到生产环境
6. 写单元测试、查日志、排查问题、调优性能
7. 知道接下来该深入哪个方向（微服务、安全、分布式…）

## 阅读顺序

### 📚 前置篇（零基础必看）

| 编号 | 标题 | 你会学到 |
|------|------|---------|
| [00](00-prepare.md) | 环境搭建 | JDK、Maven、IDEA、MySQL、Redis、Node 安装 |
| [00b](00b-mysql-basics.md) | MySQL 基础 | 30 分钟速成 SQL |
| [00c](00c-java-syntax.md) | Java 语法入门 | 零基础学 Java |
| [00d](00d-maven-git.md) | Maven 与 Git 入门 | pom.xml 在干嘛、Git 怎么用 |

### 🚀 后台核心篇（必读）

| 编号 | 标题 | 你会学到 |
|------|------|---------|
| [01](01-architecture-overview.md) | 宏观架构概览 | 整个系统长什么样 |
| [02](02-java-advanced.md) | Java 高级特性 | 注解、泛型、Lombok、Stream API |
| [03](03-spring-boot.md) | Spring Boot 与 IoC 核心 | Bean、自动配置、Starter |
| [04](04-layered-architecture.md) | 项目分层架构 | controller / service / repository |
| [05](05-jpa-persistence.md) | JPA 与数据持久化 | Entity、Repository、事务、Flyway |
| [06](06-security-jwt.md) | Spring Security 与 JWT | 登录流程、Token、权限 |
| [07](07-redis-cache.md) | Redis 缓存与性能 | 缓存策略、懒加载降级 |
| [08](08-aop-operation-log.md) | AOP 切面与操作日志 | `@OperationLog` 自动记录 |
| [09](09-exception-handling.md) | 全局异常处理 | `@RestControllerAdvice` |

### 🔧 后台进阶篇

| 编号 | 标题 | 你会学到 |
|------|------|---------|
| [10](10-logging.md) | 日志框架 | Logback 怎么打日志、查日志 |
| [11](11-testing.md) | 单元测试 | JUnit + Mockito + AssertJ |
| [12](12-docker-deploy.md) | Docker 部署 | Dockerfile、Compose、Harbor |

### 🌐 前端篇

| 编号 | 标题 | 你会学到 |
|------|------|---------|
| [13](13-frontend-basics.md) | 前端基础 | HTML / CSS / JavaScript 30 分钟速成 |
| [14](14-vue3-essentials.md) | Vue3 核心 | 组合式 API、组件、响应式、Pinia、Router |
| [15](15-this-project-frontend.md) | 本项目前端结构 | 双前端（H5 + uni-app）+ 字典服务 |

### 📈 性能与进阶

| 编号 | 标题 | 你会学到 |
|------|------|---------|
| [16](16-performance-tuning.md) | 性能调优入门 | SQL 优化、缓存、JVM、Tomcat |

### 🗺️ 学习规划

| 编号 | 标题 | 你会学到 |
|------|------|---------|
| [99](99-learning-path.md) | 学习路线图 | 4 周/8 周学习计划、实战任务 |

## 阅读建议

- **不要追求一遍看懂**：先通读建立印象，再回头精读
- **一定要动手**：每章末尾都有"动手试试"环节
- **遇到不懂的术语**先跳过去，不要卡住
- **结合本项目代码**：教材里的所有例子都来自本项目

## 本项目技术全景

**后台**：Spring Boot 4 单体应用 + JPA 持久化 + Spring Security 安全 + Redis 缓存 + Flyway 迁移。

**前端**：Vue 3 + TypeScript + Vite（PC 端）+ uni-app + Vue 3（多端）。

### 后台技术栈

| 技术 | 版本 | 章节 |
|------|------|------|
| Java | 25 | 00c / 02 |
| Maven | - | 00 / 00d |
| Git | - | 00d |
| MySQL | 8 | 00b / 05 |
| Redis | 7+ | 07 |
| Spring Boot | 4.0.6 | 03 |
| Spring Data JPA | (随 Boot) | 05 |
| Hibernate | (随 JPA) | 05 |
| Spring Security | 7.0.5 | 06 |
| JJWT | 0.12.6 | 06 |
| Spring Data Redis | (随 Boot) | 07 |
| Spring Cache | (随 Boot) | 07 |
| Flyway | (随 Boot) | 05 |
| Lombok | 1.18.46 | 02 |
| AOP | (随 Spring) | 08 |
| SLF4J + Logback | 1.5.25 | 10 |
| JUnit 5 | (随 Boot) | 11 |
| Mockito | (随 Boot) | 11 |
| AssertJ | 3.27.7 | 11 |
| H2 | (test) | 11 |
| Docker | - | 12 |
| EasyExcel | 4.0.3 | 实战补充 |

### 前端技术栈

| 技术 | 用途 | 章节 |
|------|------|------|
| HTML / CSS / JavaScript | 前端三件套 | 13 |
| TypeScript | JS 超集 | 14 |
| Vue 3 | 主框架 | 14 |
| Vite | 构建工具 | 14 |
| Vue Router | 路由 | 14 |
| Pinia | 状态管理 | 14 |
| Element Plus | UI 组件库 | 14 / 15 |
| ECharts | 图表 | 实战补充 |
| Axios | HTTP 请求 | 14 / 15 |
| uni-app | 多端框架 | 15 |

## 学习方法

1. **场景驱动**：带着"我要做个功能"的目标去学
2. **对照阅读**：教材和 IDE 同时打开
3. **改代码 > 抄代码**：抄十遍不如改一遍
4. **记录问题**：建 `my-questions.md` 记录卡住的地方
5. **跑起来 > 看懂**：先把项目跑起来

## 🎯 速成路线

### 4 周速成（"能改能写"，纯后台）

```
Day 1-3    前置篇 4 个文件
Day 4-6    01 → 02 → 03        全局观 + Spring Boot
Day 7-9    04 → 05             分层 + JPA
Day 10-12  06 → 07 → 08        安全 + 缓存 + AOP
Day 13-14  09 → 10             异常 + 日志
Day 15-16  11 → 12             测试 + Docker
Day 17+    99 实操任务
```

### 8 周完整（"前后端都能写"）

```
Week 1   前置篇 + 01 宏观架构
Week 2   02-03 Java 高级 + Spring Boot
Week 3   04-05 分层 + JPA
Week 4   06-08 安全 + 缓存 + AOP
Week 5   09-10 异常 + 日志
Week 6   11-12 测试 + Docker
Week 7   13-15 前端三件套 + Vue3 + 本项目前端
Week 8   16 性能调优 + 99 实操
```

### 按目标学

| 你的目标 | 看这些 |
|---------|--------|
| **只学后台** | 00-12（4 周） |
| **只学前端** | 13-15 + 复习 06（2 周） |
| **改现有功能** | 01 → 02 → 03 → 04 → 05 |
| **加新功能** | 04 → 05 → 09 → 11 |
| **部署上线** | 00 → 12 |
| **写测试** | 11 |
| **排查问题** | 09 → 10 → 16 |
| **团队协作** | 00d |
| **学前端** | 13 → 14 → 15 |

---

准备好了吗？

- **完全零基础** → [00 环境搭建](00-prepare.md)
- **会 Java** → [01 宏观架构](01-architecture-overview.md)
- **只会后台** → [13 前端基础](13-frontend-basics.md)
- **只会前端** → [03 Spring Boot](03-spring-boot.md)