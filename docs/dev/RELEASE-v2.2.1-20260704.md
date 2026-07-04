# Release v2.2.1-20260704

> **发布日期**：2026-07-04
> **类型**：补丁版本（Patch）
> **Git Tag**：`v2.2.1-20260704`
> **上一版本**：`v2.2.0-20260628`

---

## 📌 一句话总结

本次发布**最大亮点**是新增 **21 份 / 10,960 行 / 约 35 万字** 的 `docs/study` 学习教材，从零基础到能改本项目代码，给团队和新人一份完整的学习路径。同时配套了 5 个单元测试、若干业务代码优化与方案文档归档。

---

## 🌟 亮点

### 📚 docs/study 学习教材（核心亮点）

针对 **Java/Spring Boot 初学者** 编写的渐进式教材，结合本项目真实代码讲解后台与前端开发，覆盖 6 大篇目：

| 篇目 | 内容 | 章节数 |
|------|------|--------|
| 📚 前置篇 | 环境搭建、SQL、Java 语法、Maven+Git | 4 |
| 🚀 后台核心篇 | 架构、Spring Boot、分层、JPA、安全、Redis、AOP、异常 | 9 |
| 🔧 后台进阶篇 | 日志、单元测试、Docker 部署 | 3 |
| 🌐 前端篇 | HTML/CSS/JS、Vue3、本项目双前端 | 3 |
| 📈 性能篇 | SQL 优化、缓存、JVM 调优 | 1 |
| 🗺️ 学习规划 | 4 周/8 周学习路径、实战任务 | 1 |
| 📍 总入口 | README 含按画像分流、读者画像 6 类 | — |

**总计 21 个 md 文件，10,960 行，约 35 万字。**

每个章节遵循三段式：
1. 先讲"是什么 / 解决什么问题"
2. 再讲"怎么用"
3. 最后落到本项目的真实代码示例

读者画像分流：
- 🅰️ 会 Java，没碰过 Spring Boot → 跳过前置篇
- 🅱️ 完全零基础 → 从 00 开始按顺序读
- 🅲️ 只想看某个技术 → 按目录选章节
- 🅳️ 要部署 → 看 12 Docker 部署
- 🅴️ 要写测试 → 看 11 单元测试
- 🅵️ 只会后台想学前端 → 看 13-15
- 🅶️ 要排查慢问题 → 看 16 性能调优

入口文件：[docs/study/README.md](../study/README.md)

---

## 📦 完整变更清单

### 🆕 新增（25 个文件）

#### 文档

- `docs/study/` — 整个目录（21 个 md 文件）
  - `README.md`
  - `00-prepare.md` / `00b-mysql-basics.md` / `00c-java-syntax.md` / `00d-maven-git.md`
  - `01-architecture-overview.md` / `02-java-advanced.md` / `03-spring-boot.md` / `04-layered-architecture.md` / `05-jpa-persistence.md` / `06-security-jwt.md` / `07-redis-cache.md` / `08-aop-operation-log.md` / `09-exception-handling.md`
  - `10-logging.md` / `11-testing.md` / `12-docker-deploy.md`
  - `13-frontend-basics.md` / `14-vue3-essentials.md` / `15-this-project-frontend.md`
  - `16-performance-tuning.md`
  - `99-learning-path.md`

#### 单元测试

- `backend/src/test/java/com/pricemanagement/config/GlobalExceptionHandlerTests.java`
- `backend/src/test/java/com/pricemanagement/config/NotificationSseExceptionResolverTests.java`
- `backend/src/test/java/com/pricemanagement/config/SecurityBoundaryContractTests.java`
- `backend/src/test/java/com/pricemanagement/controller/BasicDataStatusContractTests.java`
- `backend/src/test/java/com/pricemanagement/service/NotificationRealtimeServiceTests.java`

#### 方案文档

- `docs/plan/PC端前后台对抗式审查报告.md`
- `docs/plan/PC端前后台第一性原理整改完善方案.md`
- `docs/plan/sse-client-disconnect-error-fix.md`

### ✏️ 修改（25 个文件）

#### 后端业务代码

- **Controller（13 个）**：
  - `ApprovalController` / `CustomerController` / `DepartmentController`
  - `HomeController` / `MenuController` / `NotificationController`
  - `OriginController` / `PermissionController` / `ProductCategoryController`
  - `SysDictController` / `controller/external/ExternalBasicDataController`
- **Service（4 个）**：
  - `CustomerService` / `NotificationRealtimeService`
  - `OriginService` / `ProductCategoryService`
- **Config（1 个）**：`config/GlobalExceptionHandler`
- **Constants（1 个）**：`constants/SystemConstants`

#### 前端（3 个）

- `frontend/src/views/Approval.vue`
- `frontend/src/views/Notifications.vue`
- `frontend/src/views/UserManagement.vue`

#### 文档（4 个）

- `docs/VERSIONS.md` — 补充 v2.2.1 发布说明
- `docs/dev/api/external.md` / `docs/dev/api/internal.md` — API 端点同步
- `docs/dev/design/api-design.md` / `docs/dev/design/architecture.md` — 设计文档更新

---

## 🔄 升级指南

### 无破坏性变更 ✅

本次发布**不涉及**数据库 schema 变更、API 端点签名变更、配置项重命名。可以平滑升级。

### 升级步骤

```bash
# 1. 拉取最新代码
git pull origin master
git checkout v2.2.1-20260704    # 或保持 master

# 2. 编译（如果有改动需要重新编译）
cd backend && mvn clean compile

# 3. 重启服务
docker compose restart backend
# 或直接重启 Spring Boot 进程

# 4. 验证
curl http://localhost:8080/actuator/health
# 期望: {"status":"UP"}
```

### 注意事项

- **新增学习教材不影响运行时**，纯文档变更
- **新增 5 个测试** 在 `mvn test` 时会执行，建议 CI 流水线已包含此步骤
- **业务代码改动**：建议先在测试环境验证，再升级生产

---

## 🧪 测试覆盖

| 测试类 | 覆盖范围 |
|--------|---------|
| `GlobalExceptionHandlerTests` | 全局异常处理契约 |
| `NotificationSseExceptionResolverTests` | SSE 异常处理 |
| `SecurityBoundaryContractTests` | 安全边界契约 |
| `BasicDataStatusContractTests` | 基础数据状态契约 |
| `NotificationRealtimeServiceTests` | 通知实时服务 |

运行测试：

```bash
cd backend
mvn test
```

---

## 👥 致谢

- 主开发：radishfly（xuefeishang@163.com）
- AI 辅助开发：Claude（Anthropic）
- 教学教材编写：Claude + radishfly 协作

---

## 📚 推荐阅读

如果你刚加入项目，建议按以下顺序阅读：

1. [docs/study/00-prepare.md](../study/00-prepare.md) — 把项目跑起来
2. [docs/study/01-architecture-overview.md](../study/01-architecture-overview.md) — 理解架构
3. [docs/study/README.md](../study/README.md) — 看完整学习路径

---

## 🔗 链接

- **GitHub Release**：https://github.com/xuefeishang/price-managemnet-system/releases/tag/v2.2.1-20260704
- **学习教材入口**：[docs/study/README.md](../study/README.md)
- **版本列表**：[docs/VERSIONS.md](../../VERSIONS.md)

---

**Co-Authored-By**: Claude <noreply@anthropic.com>