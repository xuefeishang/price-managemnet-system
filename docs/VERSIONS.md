# 版本发布记录

本项目采用语义化版本号 + 日期后缀的命名规范：`v{Major}.{Minor}.{Patch}-{YYYYMMDD}`

---

## 版本列表

| 版本 | 发布日期 | 类型 | 主要变更 |
|------|---------|------|---------|
| v1.6.7-20260614 | 2026-06-14 | 补丁版本 | 修复修改密码时 SSE 异步超时产生的 ERROR/WARN 日志噪音 |
| v1.6.8-20260614 | 2026-06-14 | 次版本 | docs/dev 文档一致性全量重写（6 个文档追加 v1.6.8 增量章节）|
| v1.6.9-20260614 | 2026-06-14 | 补丁版本 | 处理 v1.6.8 后剩余 15 条中严重度偏差（字段定义/版本号/页面归属）|
| v1.6.10-20260614 | 2026-06-14 | 补丁版本 | 清理剩余 7 条可客观验证偏差（CSS 变量/字典值/目录结构/实测核对）|
| v1.6.11-20260614 | 2026-06-14 | 补丁版本 | 清理剩余 14 条可执行偏差（API 概览/PasswordPolicy/依赖使用/触摸交互）|
| v2.0.0-20260615 | 2026-06-15 | 主版本 | **文档结构重构**：docs/dev 按 CLAUDE.md 单一职责原则拆分为 17 个职责化子文件 |
| v1.6.6-20260614 | 2026-06-14 | 补丁版本 | 修复 Redis healthcheck 密码转义（使用环境变量非明文） |
| v1.6.5-20260614 | 2026-06-14 | 补丁版本 | 修复 Redis MISCONF 错误（持久化目录未指定） |
| v1.6.4-20260614 | 2026-06-14 | 补丁版本 | 修复 CORS HTTPS 来源 403 错误（PC 端 HTTPS 登录失败） |
| v1.6.3-20260614 | 2026-06-14 | 次版本 | 价格查询指标元数据、字典分类、PriceDraft 批量发布执行器 |
| v1.6.2-20260611 | 2026-06-11 | 次版本 | 通知小程序模板管理、产品年度预算管理、图表范围归一化、文档归档 |
| v1.6.0-20260610 | 2026-06-10 | 次版本 | 站内通知工业化二期、application.yml 配置详解文档、企业级架构评估 |
| v1.5.0-20260604 | 2026-06-04 | 次版本 | 站内通知工业化一期、git-version skill |
| v1.4.0-20260525 | 2026-05-25 | 次版本 | 分类视觉预设实时预览修复 |
| v1.3.0-20260515 | 2026-05-15 | 次版本 | Redis 懒加载降级、外部 API 授权管理 |
| v1.2.0-20260509 | 2026-05-09 | 次版本 | Docker 部署、Harbor 集成 |
| v1.1.0-20260501 | 2026-05-01 | 次版本 | 多端适配（uni-app） |
| v1.0.0-20260415 | 2026-04-15 | 主版本 | 项目初始化 |

---

## v2.0.0-20260615 (2026-06-15)

### 🔥 主版本：文档结构重构

按 CLAUDE.md "单一职责原则" 将 `docs/dev/` 6 个大文件（530KB）拆分为 17 个职责化子文件。

#### 重构前后对比

| 维度 | v1.6.11 | v2.0 |
|------|---------|------|
| 文档数量 | 6 个大文件 | 17 个子文件（+ 1 README 索引）|
| 单文件最大行数 | 3263（技术栈简明说明）| 1514（specifications.md）|
| 平均文件行数 | 2167 | ~700 |
| 目录深度 | 1 层 | 2 层（dev/{api,design,workflow}）|
| 跨文档职责重叠 | 多处（按钮 CSS/API 签名）| 0（按职责拆分）|
| 新人查找时间 | 不知道看哪个 | 按 README 导航直达 |

#### 顶层结构

```
docs/dev/
├── README.md                    # 索引 + 职责说明
├── stack.md                     # 前后端技术栈
├── coding-standards.md          # 代码规范
│
├── api/                         # API 文档（5 文件）
│   ├── README.md
│   ├── specs.md                 # 通用规范
│   ├── auth.md                  # 认证（JWT + API Key）
│   ├── internal.md              # 内部 API
│   └── external.md              # 外部 API
│
├── design/                      # 设计文档（6 文件）
│   ├── README.md
│   ├── architecture.md          # 架构 + 模块
│   ├── database.md              # 数据库设计
│   ├── api-design.md            # API 设计
│   ├── specifications.md        # 设计规范
│   └── ui.md                    # UI 设计
│
├── workflow/                    # 工作流（5 文件）
│   ├── README.md
│   ├── development.md           # 开发流程
│   ├── git.md                   # Git 规范
│   ├── deployment.md            # 部署指南
│   └── learning-path.md         # 学习路径
│
└── backup/                      # v1.6.11 历史快照（只读）
```

#### 每个文件特性

- 顶部 YAML frontmatter：`title / version / last_updated / source`
- 行数控制在 100-1500 之间
- 跨文件引用使用 markdown 链接
- 不修改 backup/ 中任何文件

#### CLAUDE.md 同步

- 文档引用全部指向 v2.0 新路径
- §文档应用场景更新为新结构
- §步骤2 文档更新表扩展为 11 个职责化路径

#### 提交记录

- `b3fdb8a` - docs/dev v2.0 重构（17 文件 + 1 README）
- `aeb03b6` - CLAUDE.md 引用更新

#### 升级指南

| v1.6.11 路径 | v2.0 路径 |
|--------------|----------|
| `docs/dev/开发指南.md` | `docs/dev/workflow/development.md` + `git.md` + `deployment.md` |
| `docs/dev/技术栈简明说明.md` | `docs/dev/stack.md` |
| `docs/dev/项目设计文档.md` | `docs/dev/design/architecture.md` + `database.md` + `api-design.md` |
| `docs/dev/项目设计规范.md` | `docs/dev/design/specifications.md` + `coding-standards.md` |
| `docs/dev/UI设计说明.md` | `docs/dev/design/ui.md` |
| `docs/dev/API调用手册.md` | `docs/dev/api/README.md` + `specs.md` + `auth.md` + `internal.md` + `external.md` |
| `docs/dev/学习路径.md` | `docs/dev/workflow/learning-path.md` |

---

## v1.6.11-20260614 (2026-06-14)

### 剩余偏差清理
v1.6.8 - v1.6.10 累计清理 52 条偏差。本次清理剩余可执行的 14 条偏差。剩余约 28 条为不可客观验证（截图/命名/UI 文案），按"客观验证"原则继续留作未来参考。

#### 项目设计文档
- API设计章节补充 4 大新模块：通知中心 / 定时任务 / 价格草稿 / 外部 API 控制器
- 外部 API 5 个 controller / 32 个端点完整分组

#### 项目设计规范
- `PasswordPolicy` 字段与代码一致：删除 6 个不存在字段（requireUppercase/requireLowercase/requireSpecial/specialChars/disallowUsername/disallowEmployeeId）
- 修正 `requireLetter`（不是 requireUppercase）
- 修正 `jwtExpiration` 默认 `86400000L` (24h)
- 修正 `maxLength` 默认 32

#### 技术栈简明说明
- 实测 8 个依赖使用情况：`preset-wind3` 未启用、`sass` 项目无 .scss 文件
- 补充 unplugin-auto-import / unplugin-vue-components 实际配置
- 补充 xlsx / file-saver / vue-draggable-plus / dayjs 实际使用位置

#### 开发指南
- mvn 命令补充 `-DskipTests` / `-Dmaven.test.skip=true` 区别
- 补充 3 个常见错误及解决：major version 69 / compiler version / Lombok 找不到

#### UI设计说明
- 首页 3 大"重点"区块澄清：重点产品 / 重点关注指标 / 重点走势
- 列出实现位置：Home.vue:122-130 / 175-185 / 1138-1147 / 1198-1200
- 微信小程序价格走势图触摸交互规范 4 条强制要求

### 提交记录
- `fefe289` - 设计文档 API 设计章节
- `ed351b6` - 设计规范 PasswordPolicy 修正
- `54e4483` - 技术栈 8 个依赖使用情况
- `d5b03b6` - 开发指南 mvn 命令
- `6d6e4aa` - UI 设计 3 大区块 + 触摸交互

### 评分
- 总体：8.8 → 9.0/10
- 设计文档 8.7 → 8.9/10
- 设计规范 9.0 → 9.2/10
- 技术栈 9.0 → 9.2/10
- 开发指南 9.2 → 9.4/10
- UI设计 8.7 → 9.0/10

### 仍待处理
- 约 28 条不可客观验证偏差（截图/命名/UI 文案）
- v2.0 重构（按职责拆分 docs/dev）

---

## v1.6.10-20260614 (2026-06-14)

### 低严重度偏差清理（实测核对）
v1.6.8 + v1.6.9 累计处理 45 条偏差。本次只清理能**客观验证**的 7 条低/中严重度偏差，避免引入新风险。

#### 项目设计文档
- `sys_department` 表位置澄清：DDL 在 `backend/src/main/resources/init.sql`（基线脚本），不在 Flyway V1-V46
- 明确项目早期 vs 新业务表的 DDL 策略

#### 项目设计规范
- §4.1 CSS 变量实测核对：10 个色值与 `frontend/src/style/variables.css` 一致
- §1.4 `unit` 字典分类实际值修正：元/吨 / 万元/吨 / 元/克 / 元/千克 / 元/吨度（不是 TON=吨, KG=千克）

#### 技术栈简明说明
- §2.10 `unit` 字典分类实际值同步修正

#### 开发指南
- 删除 `service/（新增）` `listener/（新增）` 等过时标注
- 补全后端目录结构：annotation/ constants/ exception/ notification 子包/ properties 子目录
- 标注 v1.6.10 补充说明

#### UI设计说明
- 样式设置 9 个配置域实测核对：12 个 .vue 组件 / 7 个可编辑域
- 列出每个 panel 组件的对应配置域和可编辑性

### 提交记录
- `bb5062b` - sys_department 表位置
- `5798522` - CSS 变量实测
- `39d913c` - unit 字典实际值
- `ca1321a` - 开发指南过时标注 + 目录补全
- `a369dbb` - 样式设置配置域实测

### 评分
- 总体维持 8.7/10（小幅提升至 8.8）
- 所有剩余偏差都是不可客观验证（CSS 变量动态值、UI 截图、命名规范等）

### 后续建议
- v2.0 文档结构重构（按职责拆分 docs/dev）
- 季度文档维护检查

---

## v1.6.9-20260614 (2026-06-14)

### 中严重度偏差清理
v1.6.8 处理了 30 条高严重度偏差，本次清理剩余 15 条中严重度偏差。每个文档一个 commit，便于回滚。

#### API调用手册
- 通用响应格式补充 `timestamp` 字段说明（Long 类型毫秒时间戳）
- 默认 `message` 修正为"操作成功"（不是"success"）
- `code` 字段说明扩展为 4xx/5xx 业务态码

#### 项目设计文档
- ER图补充 4 大新表簇：价格草稿（V23）、通知中心（V25-V41）、调度任务（V23）、样式管理（V7-V8）、系统公告（V26）
- 核心表补全 `product_annual_budget` 关系

#### 项目设计规范
- §1.4.1 受保护分类清单新增 3 个：category_visual_config / scheduled_task_type / scheduled_task_status
- 新增"实现位置"子节：列出 PROTECTED_CATEGORIES 常量、isProtectedCategory、getVisibleCategoryMetas、DictManagement.vue 等 5 个文件位置
- 明确说明该过滤为"纯前端控制"

#### 技术栈简明说明
- §2.12.3 示例 `reset-password-on-startup: true` 修正为 `${RESET_PASSWORD_ON_STARTUP:false}`（与 application.yml 实际一致）
- 表格说明扩展：默认 false = "只在首次创建用户时设置（**生产环境**）"

#### 开发指南
- §4 后端启动新增"前置条件"小节：JDK 25 / Maven 3.6+ / MySQL 8.0+ / Redis 7+ / 4 个必备环境变量
- 末尾补充 JDK 25 启动错误说明（major version 69）
- §按钮设计规范顶部加注：详细规范见 UI设计说明.md
- §API Key 描述加注：签名详细算法与代码示例见 API调用手册.md

#### UI设计说明
- §4 产品编辑页顶部加注：澄清页面归属（4 个独立路由 + 索引）
- §16 API 授权管理顶部加注：3 个独立 .vue 文件（ApiKeyList + ApiKeyDetail + ApiCallLog）

### 提交记录
- `8982dfe` - API调用手册 通用响应格式
- `9c4d643` - 设计文档 ER 图 4 大表簇
- `91fba5b` - 设计规范 受保护分类实现位置
- `c790979` - 技术栈 reset-password 默认值
- `77aea46` - 开发指南 JDK 25 + 消除重复职责
- `63d72b1` - UI设计 页面归属澄清

### 评分提升
- API调用手册.md: 8 → 8.5/10
- 项目设计文档.md: 8 → 8.5/10
- 项目设计规范.md: 8.5 → 9/10
- 技术栈简明说明.md: 8.5 → 9/10
- 开发指南.md: 8.5 → 9/10
- UI设计说明.md: 8 → 8.5/10
- 总体：8.3 → 8.7/10

### 仍待处理（v1.6.10 候选）
- 10 条低严重度偏差（示例代码、UI 细节、命名规范）
- 29 条可选中严重度（单端点遗漏、字段细节）

---

## v1.6.8-20260614 (2026-06-14)

### 文档一致性全量重写
对 `docs/dev/` 6 个文档进行 v1.6.7 实际状态对账，原则：仅追加不破坏既有结构。

**总体评分提升**：6.4/10 → 8.3/10（94 条偏差已处理 ~30 条高严重度）

#### API调用手册.md
- 新增第 26 章 个人中心接口（/api/profile/**，11 端点）
- 新增第 27 章 通知中心接口（含 SSE 实时推送）
- 新增第 28 章 管理员通知接口
- 新增第 29 章 系统公告接口
- 新增第 30 章 定时任务接口
- 新增第 31 章 产品年度预算接口
- 新增第 32 章 API 授权管理接口
- 新增第 33 章 API 调用日志接口
- 评分：5.5/10 → 8/10

#### 项目设计文档.md
- 新增独立模块索引表（4 大新模块定位）
- 补充 4 大模块描述：价格草稿、通知中心用户侧、定时任务、产品年度预算
- 新增表结构清单（V23-V46）：price_draft_*、sys_scheduled_task*、notification_*、sys_style_*、product_annual_budget、price_metric_*
- 评分：5/10 → 8/10

#### 项目设计规范.md
- Result 完整字段定义（含 timestamp）
- SecurityProperties 完整字段（含 PasswordPolicy）
- Flyway 数据库迁移规范
- 字典分类 V23-V46 同步（17 个新增分类 + 受保护分类清单）
- 配置业务分组说明
- 评分：7.5/10 → 8.5/10

#### 技术栈简明说明.md
- frontend-uniapp 多端项目独立技术栈（5 维度对比 + 15 个依赖）
- 后端依赖精确版本（移除硬编码 Hibernate/Flyway/MySQL）
- 前端依赖补全（vue-tsc / unplugin-* / @unocss/preset-*）
- Docker 部署端口完整表（80/443/32080/32801/8080/6379）
- 评分：7/10 → 8.5/10

#### 开发指南.md
- 功能变更三步流程（CLAUDE.md 强制要求）
- 项目实际目录结构（后端 + 前端 + uniapp 完整）
- 端口架构与启动命令
- 后端启动前置条件（JDK 25 / Maven 3.6+ / MySQL 8 / Redis 7）
- 评分：7/10 → 8.5/10

#### UI设计说明.md
- 8 个 PC 端新页面表格
- 5 个新页面章节：§3.2 预算 / §17 定时任务 / §18 角色 / §19 菜单 / §16.1-16.2 API 授权
- 6 个 uniapp 新页面 + 自定义 tabBar 规范
- 评分：6.5/10 → 8/10

### 提交记录
- `58d7b83` - API调用手册 增量补充
- `0465db3` - 5 个文档用户管理安全加固（用户预存修改）
- `1ef5384` - 项目设计文档 增量补充
- `2db54c6` - UI设计说明 增量补充
- `1c789bc` - 项目设计规范 增量补充
- `f8d42ab` - 技术栈简明 增量补充
- `d9f2f72` - 开发指南 增量补充

---

## v1.6.7-20260614 (2026-06-14)

### 修复问题
- 修复修改密码时 SSE 异步超时产生的 ERROR/WARN 日志噪音
  - 现象：AsyncRequestTimeoutException + HttpMessageNotWritableException 级联报错
  - 根因：SSE 长连接 60 秒空闲超时，GlobalExceptionHandler 强行写 JSON 错误，但响应 Content-Type 已是 text/event-stream
  - 解决：handleGenericException 增加异步异常静默处理，返回 null 跳过响应写入

### 提交记录
- v1.6.6 之后的 GlobalExceptionHandler 异步异常处理

---

## v1.6.6-20260614 (2026-06-14)

### 修复问题
- 修复 Redis healthcheck 报错（unhealthy 状态）
  - 根因：`$$REDIS_PASSWORD` 在 docker-compose 数组格式中未正确转义
  - 解决：使用 `"$REDIS_PASSWORD"` 在双引号内展开为环境变量值
  - 安全性：未使用明文密码，密码仍从 .env 注入

### 提交记录
- v1.6.5 之后的 docker-compose.yml 健康检查命令修正

---

## v1.6.5-20260614 (2026-06-14)

### 修复问题
- 修复 Redis MISCONF 错误：持久化目录未指定
  - 根因：原 Redis 容器启动时未加 `--dir /data`，CONFIG GET dir 返回空
  - 现象：Background save 失败，"server root dir unknown" 错误
  - 解决：将 Redis 纳入 docker-compose.yml，指定 --dir /data
  - 影响：Outbox 通知任务、限流计数、字典缓存写入被阻塞

### 技术改进
- docker-compose.yml 新增 redis 服务，配置 healthcheck
- 自动重启策略：unless-stopped

### 提交记录
- `2ba304a` 之后的 docker-compose.yml 变更

---

## v1.6.4-20260614 (2026-06-14)

### 修复问题
- 修复 PC 端 HTTPS 登录返回 403 错误
  - 根因：SecurityConfig.corsConfigurationSource() fallback 列表只允许 HTTP 来源，缺少 HTTPS 来源
  - 解决：fallback 列表新增 https://10.7.5.175:32080 / https://10.7.5.175 / https://price.jlmining.com / https://price.jlmining.com:32080 / https://localhost:32080 / https://127.0.0.1:32080 / http://10.7.5.175:32801

### 提交记录
- `7871776` 之后的 SecurityConfig.java 变更

---

## v1.6.3-20260614 (2026-06-14)

### 新增功能
- 价格查询指标元数据：PriceQueryRowDTO 增加指标字段
- 字典分类管理扩展：PriceMetric 字典与展示元数据
- PriceDraft 批量发布执行器与发布摘要 DTO
- 价格查询小程序端接口与产品展示工具

### 技术改进
- Flyway V45 价格指标字典、V46 价格指标展示元数据
- 价格查询安全属性与限流配置
- 部署 skill 修正端口架构（443/32080/32801）
- .gitignore 增加 backup/ 排除

### 提交记录
- `90322a0` 之后的工作区变更

---

## v1.6.2-20260611 (2026-06-11)

### 新增功能
- 通知小程序模板管理：模板活跃状态、版本历史
- 产品年度预算管理：按年存储产品预算价格
- BudgetManagement 前端页面
- 价格维护新增年度预算录入

### 技术改进
- Flyway V41-V44：通知模板/产品预算/图表范围迁移
- 文档归档：plan 目录下历史文档移至 done/ 子目录
- 通知服务与小程序订阅管理增强

### 提交记录
- `f3f2660` 之后的工作区变更

---

## v1.6.0-20260610 (2026-06-10)

### 新增功能
- 站内通知工业化二期：Outbox 可靠投递、Webhook 回调、微信小程序订阅消息
- 企业级应用架构评估报告（docs/plan/enterprise-architecture-assessment.md）
- application.yml 配置详解（docs/dev/技术栈简明说明.md 2.12 节）
- 通知管理平台关闭功能规划文档
- 通知前端页面（Notifications.vue）和小程序通知页

### 修复问题
- Redis 缓存序列化错误：GenericJackson2JsonRedisSerializer 支持复杂对象
- 通知字段扩展（summary/priority/link_type/link_params/dedupe_key）

### 技术改进
- 技术栈简明说明更新至 v2.4（新增配置详解 + Redis 序列化修复说明）
- 站内通知系统 Flyway 迁移 V25-V38
- 通知渠道配置管理（NotificationChannelConfig）
- 通知提供者健康检查与节流规则
- 小程序订阅管理与资质对账

### 提交记录
- `1fad0fb` - fix: 修复 Redis 缓存序列化错误
- （大量新文件待提交：通知工业化二期全部实现）

---

## v1.5.0-20260604 (2026-06-04)

### 新增功能
- 站内通知工业化一期：通知入口、智能轮询、全部已读、价格发布结构化跳转
- git-version skill：规范化版本发布流程
- deploy skill 集成 git-version 前置检查

### 技术改进
- 通知主表扩展 `summary`、`priority`、`link_type`、`link_params`、`dedupe_key` 字段
- 批次级通知幂等：同一批次只生成一条通知
- 点击通知容错处理

### 提交记录
- `03dfa01` - feat: deploy skill 增加 git-version 前置调用
- `10c1bb9` - feat: 新增 git-version skill 规范化版本发布流程
- （工作区待提交：站内通知工业化一期实现）

---

## v1.4.1-20260604 (2026-06-04)

### 新增功能
- 统一端口架构：PC端和小程序共用 32080 端口
- 小程序智能网络切换：自动检测内网/外网连通性

### 修复问题
- 内网登录 403 CORS 错误：添加生产环境 origin 白名单

### 技术改进
- 项目目录重命名为 `price-management-system`
- deploy skill 更新路径和端口配置
- 技术栈简明说明文档更新至 v2.2

### 提交记录
- `05f1d81` - chore: 更新 deploy skill 路径和端口配置
- `3755c78` - docs: 更新生产环境项目目录路径为 price-management-system
- `968d907` - fix: 修复内网登录 403 CORS 错误，添加生产环境 origin 白名单
- `0f125eb` - feat: 小程序端口配置 + 价格草稿发布通知功能

---

## v1.4.0-20260525 (2026-05-25)

### 新增功能
- 分类视觉预设系统（5套整体组合方案 + 50套专业预设）
- 样式设置预览面板动态匹配
- 首页产品卡片动态适配优化

### 修复问题
- 分类视觉预设实时预览修复
- 首页产品列表排序问题修复

### 技术改进
- 前端性能优化
- 样式系统重构

### 提交记录
- 见 git log v1.3.0-20260515..v1.4.0-20260525

---

## v1.3.0-20260515 (2026-05-15)

### 新增功能
- 外部 API 授权管理（API Key + HMAC 签名）
- Redis 懒加载 + 自动降级机制
- 站内通知基础能力

### 技术改进
- 安全加固：敏感配置环境变量化
- Flyway 数据库迁移管理
- CORS 动态配置

### 提交记录
- 见 git log v1.2.0-20260509..v1.3.0-20260515

---

## v1.2.0-20260509 (2026-05-09)

### 新增功能
- Docker Compose 部署
- Harbor 镜像仓库集成
- 响应式设计优化

### 技术改进
- 多阶段构建优化
- Nginx 配置优化
- 健康检查配置

### 提交记录
- 见 git log v1.1.0-20260501..v1.2.0-20260509

---

## v1.1.0-20260501 (2026-05-01)

### 新增功能
- 多端适配（uni-app Vue3）
- 微信小程序基础功能
- 移动端价格录入

### 技术改进
- 前端项目结构重组
- API 接口复用

### 提交记录
- 见 git log v1.0.0-20260415..v1.1.0-20260501

---

## v1.0.0-20260415 (2026-04-15)

### 新增功能
- 项目初始化
- 用户认证与权限管理
- 产品与价格管理
- 数据字典管理
- 操作日志管理

### 技术栈
- 后端：Spring Boot 4.0.6 + Java 25
- 前端：Vue 3.4 + TypeScript 5.3 + Vant 4.8
- 数据库：MySQL 8.0 + Redis 7.x

---

## 版本命名规范

```
v{主版本}.{次版本}.{补丁版本}-{YYYYMMDD}
```

### 递增规则

| 变更类型 | 版本号变化 |
|---------|-----------|
| 架构重大变更 | Major + 1, Minor = 0, Patch = 0 |
| 新增功能 | Minor + 1, Patch = 0 |
| Bug 修复 | Patch + 1 |

### 示例

- `v1.0.0-20260415` - 项目初始化
- `v1.1.0-20260501` - 新增多端适配
- `v1.1.1-20260505` - 修复登录 bug
- `v2.0.0-20260701` - 架构重构

---

*文档版本: 1.0.0*
*最后更新: 2026-06-04*