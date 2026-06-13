# 矿产品价格管理系统

企业级前后端分离的矿产品价格展示与管理系统，面向企业内部员工使用。

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端（H5） | Vue 3.4 + TypeScript 5.3 + Vant 4.8 + UnoCSS + Pinia 2.1 + ECharts 6.0 |
| 前端（多端） | uni-app Vue3 + TypeScript + Pinia（支持 H5/APP/小程序） |
| 后端 | Spring Boot 4.0.6 + Java 25 + Spring Security 7.0 |
| 数据库 | MySQL 8.0/8.4 + Redis 7.x（支持懒加载，Redis 不可用时自动降级为内存缓存） |
| 认证 | JWT (Access Token + Refresh Token) + 外部 API Key HMAC 签名 |
| 部署 | Docker + Nginx |

## 主要功能

- **用户认证与权限管理**
  - JWT Token 认证（双 Token 机制：Access Token 24h + Refresh Token 7d）
  - 三种角色：管理员（ADMIN）、编辑者（EDITOR）、查看者（VIEWER）
  - 动态权限系统（36个权限码，登录时获取用户权限列表，刷新个人资料时同步权限缓存）
  - 管理员用户管理：新增、编辑资料、锁定/解锁、删除、导入导出、独立多角色分配；编辑用户时可设置新密码，留空则不修改
  - 个人中心与账号运维：资料维护、密码修改、账号安全、登录历史、会话管理、个人操作记录和个人偏好
  - API 限流保护（登录 5次/分钟/IP）

- **外部 API 授权管理（仅管理员）**
  - 独立 `/api/external/v1/**` 外部接口面，与内部 JWT 接口物理隔离
  - API Key + HMAC-SHA256 签名认证，支持 Timestamp、Nonce 防重放、IP 白名单、分钟限流和日限额，限额填 `0` 表示不限制
  - Secret 仅创建时展示一次，数据库使用 AES-GCM 加密存储
  - 管理端支持密钥创建、编辑、启用、停用、吊销、权限分配和调用日志查询
  - 支持“部署级开关 + 页面运行时开关”双层控制，可在 API 授权管理页即时暂停/恢复外部 API 服务
  - 新增密钥时提供接口参数结构与 Node.js / Java 25 / Postman / PowerShell / curl 可复制调用示例，创建成功后可生成带真实 App ID / Secret 的一次性可运行示例
  - 功能默认关闭，配置 `API_KEY_ENABLED=true` 后仅影响 `/api/external/**`

- **部门管理**
  - 树状组织架构（总部/子公司/部门三级）
  - 拖拽调整部门层级
  - 部门用户统计
  - 部门负责人设置

- **产品与价格管理**
  - 首页驾驶舱布局（产品总数/今日更新/覆盖品类/价格异动摘要、重点产品价格、主价格曲线、分页产品表格、价格预警，区块显隐/排序跟随样式设置）
  - 首页日期选择，查看历史价格
  - 产品分类管理
  - 产品与价格 CRUD
  - 产品管理升级为响应式工作台：PC 端使用自适应分页产品列表联动资料/价格侧栏，移动端使用单列产品卡片；支持名称、编码、规格搜索及分类、状态、排序筛选，管理入口按权限展示
  - 产品详情升级为响应式价格决策页：产品身份、近期价格、年度预算、趋势统计及同比环比合并为统一概览区，概览区和实际价格曲线动态读取产品分类在样式设置中的配色并保证文字对比度；页头支持按日期查看截至该日有效的正式价格，并提供价格查询入口；实际价格走势按正式价格生效区间连续返回并以平滑曲线展示，支持 30天/180天/12个月及自然年查看
  - 预算管理独立为产品管理下的年度预算工作台，按产品 + 年份维护年度预算价；年份输入支持直接定位到 1900-2999 任意年份，年度预算作为预算信息唯一来源，自动用于该年价格查询和走势预算线
  - 价格维护页对齐首页产品列表工作台，支持搜索、产品类别过滤、分页、拖拽调整产品顺序并同步首页排序，同时录入当日售价并保留昨日售价、价格变化、月均价对照
  - 价格维护支持“保存草稿 / 发布”双阶段：保存只写入草稿，发布后才写入正式价格、价格历史并向用户生成站内通知；发布重试跳过已发布明细，通知按草稿批次去重；PC 左侧用户卡片更多菜单提供消息入口，未读时三点按钮切换为红色数字角标，通知抽屉支持未读筛选、全部已读、归档和价格查询结构化跳转；预留 `PRICE_PUBLISH` 审批扩展点但默认不启用
  - 价格查询页面向普通用户开放，只读查询价格，支持按日期、关键字、分类分页筛选；列表展示产品名称（内含产地胶囊与规格）、近期价格、预算价格和“较预算”，其中近期价格为该产品最后一次正式价格维护的价格，行选中联动右侧 30天/180天/12个月趋势和价格摘要，并可按自然年查看历史变化
  - 价格历史记录 + ECharts 折线图可视化
  - 首页图表在浏览器最小化或页面隐藏时暂停自动 resize，避免窗口尺寸异常导致图表错位或浏览器窗口被恢复
  - 产品列表/价格维护新增产地列；首页产品卡片在存在产地时默认展示“产地 + 字典名称”信息胶囊
  - 小程序产品详情隐藏内部产品编码，并通过字典展示产地与报价适用客户中文名称
  - 小程序产品详情价格走势支持每日售价/预算双线、日期刻度、触摸查看具体日期金额及按自然年查看历史变化
  - 首页产品列表升级为分页工作台，支持表格/卡片/自动模式、搜索、分类筛选、分页、排序和行选中联动主价格曲线，并按浏览器宽度自适应避免页面横向滚动
  - 首页重点产品价格曲线和重点走势卡片同时展示正式价格实线与年度预算虚线；产品列表迷你走势图提示层挂载到页面顶层，避免被表格单元格裁剪
  - 分类视觉预设系统（支持整套组合方案、1组自定义组合、50套分组预设、分类级 icon/主色/浅底/边框/趋势图色）

- **Excel 导入导出**
  - 产品数据批量导入
  - 产品数据导出
  - 价格查询按当前筛选条件导出全部匹配数据，VIEWER 也可使用 `price:export`

- **响应式设计**
  - PC端布局（≥1024px）
  - 移动端布局（<1024px）

- **多端支持（uni-app）**
  - H5 端
  - 微信小程序端
  - APP 端（iOS/Android）
  - 微信一键登录
  - 小程序角色化导航：VIEWER 展示“首页 / 历史 / 我的”，ADMIN/EDITOR 增加“录入”
  - 移动端提供历史价格只读查询和轻量价格录入/补录
  - 产品、分类、产地、客户、审批、字典、用户等完整运维保留在 PC 端

- **操作日志管理（仅管理员）**
  - 日志列表查询与筛选
  - 统计分析（趋势图、饼图、柱状图）
  - 月度报表、年度报表 + 用户排行
  - 通用定时任务管理：支持价格自动发布任务配置、启停、手动执行和执行日志查看；默认任务初始化为停用，需管理员确认后启用
  - 通知管理平台：价格发布、审批待办、定时任务失败、外部 API 告警、导入导出完成和系统公告均通过统一 `NotificationCreateCommand` 接入；PC 端通过 SSE 轻事件 + 带抖动和失败退避的轮询降级获取未读数，用户卡片更多按钮以红色数字提示未读消息；外部渠道通过 `notification_outbox` 异步投递，Webhook Provider 支持幂等键、超时和失败落库；ADMIN 可在“系统管理 / 通知管理”查询消息、收件人、投递日志、失败重试、指标看板、Provider 健康状态和聚合频控规则，并创建、发布、撤回系统公告；PC `/notifications` 支持选择 `MINI_PROGRAM` 作为小程序订阅消息渠道，渠道配置页维护小程序 AppID、启用状态、默认跳转页和 AppSecret 密钥托管状态，“小程序模板”独立页签维护模板版本、字段映射、测试校验、发布、停用、回滚和重新授权影响评估，并提供本地诊断、远程 token 校验、受控测试投递与最近失败跳转；订阅授权详情抽屉展示模板、最近投递、失败原因、用户偏好和异常处理记录，站内通知始终兜底，uni-app 通知页提供订阅授权主入口、分批授权、未读摘要、全部已读、归档、价格查询跳转和订阅消息 `messageId` 点击参数承接，我的页保留消息入口、未读角标和订阅授权快捷入口

- **审批流程管理**
  - 审批工作流配置（创建、编辑、删除、激活/停用）
  - 审批节点配置（节点类型、审批角色、顺序）
  - 审批请求管理（待审批、我的申请）

- **字典管理**
  - 数据字典 CRUD（按领域分组导航管理分类，启用/停用）
  - 前端字典服务（useDict composable，全局缓存）
  - 所有页面硬编码标签动态化
  - 受保护分类隔离（样式配置分类默认隐藏，仅可查看）
  - "显示系统配置"开关（ADMIN可查看受保护分类）
  - 分类使用帮助（用途、使用页面、字段规则、风险提示）
  - 分类效果展示（下拉选项、状态色、图标、JSON、只读配置预览）
  - extraValue智能渲染（颜色色块、JSON格式化+复制、图标预览）

- **全局样式管理（仅管理员）**
  - 响应式配置工作台（PC顶部配置域Tab + 配置/预览两栏，移动端横滑导航）
  - 三层状态模型（serverConfig/draftConfig/appliedConfig）
  - 所有变更先进入草稿，仅通过顶部“保存配置”统一生效
  - 系统名称与登录页副标题自定义
  - Logo 上传管理（Base64 存数据库，跨平台兼容）
  - 浏览器页签图标动态使用样式设置中的 Logo，并生成 64×64 放大图标，避免默认图标或小图标显示不清
  - 色彩方案切换（预设名称由接口返回，前端不硬编码显示名）
  - 布局方案切换（预设名称由接口返回，支持后端扩展）
  - 字号预设切换（紧凑/标准/大字体/特大字体采用可读性重平衡刻度，预设名称由接口返回，支持后端扩展）
  - 字体配置（标题/正文/数字字体）
  - CSS 变量动态应用（导航背景、页面背景、卡片圆角等）
  - 首页体验配置（产品卡片列数、重点产品数量、产品列表模式/每页条数）
  - 首页排序配置（首页组件显隐与顺序、产品列表分类顺序、分类内产品顺序；重点关注指标与重点走势跟随同源排序，预览同步对齐首页模块结构和产品产地信息）
  - 分类视觉配置（5套整体组合方案 + 1组“我的组合” + 5组50套专业预设，支持管理员微调助手）
  - 版本快照与回滚（保存自动生成版本，支持手动回滚历史配置，版本对比覆盖登录页副标题等文本差异）

- **安全加固**
  - 敏感配置环境变量化
  - CORS 动态配置
  - 数据库迁移管理（Flyway）
  - Redis 懒加载 + 自动降级（Redis 不可用时不影响应用启动）

- **告警系统**
  - 钉钉/企业微信告警
  - 健康检查监听（内存、CPU 阈值）

- **AI 智能助手**（详见 [docs/plan/ai-agent-design.md](docs/plan/ai-agent-design.md)）

## 快速启动

### 1. 初始化数据库

```sql
CREATE DATABASE IF NOT EXISTS price_management DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Flyway 会在应用启动时自动执行迁移脚本。
历史库首次接入 Flyway 时会 baseline 到 V12，再执行 V13 及之后的迁移；空库会从 V1 开始完整迁移。

### 2. 启动后端

```bash
cd backend
mvnw spring-boot:run
```

或使用 IDE 运行 `PriceManagementApplication.java`

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

### 4. 访问应用

- 前端：http://localhost:5173
- 后端：http://localhost:8080

## 默认用户

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | 【敏感-已移除】 | 管理员 |
| editor | 【敏感-已移除】 | 编辑者 |
| viewer | 【敏感-已移除】 | 查看者 |

## 数据库脚本

| 文件 | 说明 |
|------|------|
| `backend/src/main/resources/db/migration/V1__*.sql` | Flyway 基线迁移 |
| `backend/src/main/resources/db/migration/V2__*.sql` | Refresh Token 表 |
| `backend/src/main/resources/db/migration/V13__*.sql` - `V16__*.sql` | 历史库首次接入 Flyway 后自动补齐的结构和菜单迁移 |
| `backend/src/main/resources/db/migration/V17__external_api_auth_phase1.sql` | 外部 API 授权管理一期表、字典、菜单和端点权限 |
| `backend/src/main/resources/db/migration/V19__external_api_endpoint_code_examples.sql` | 外部 API 端点结构化示例、参数 schema 和可复制代码元数据 |
| `backend/src/main/resources/db/migration/V22__personal_profile_management.sql` | 个人中心会话设备、登录历史和个人偏好 |
| `backend/src/main/resources/db/migration/V25__notification_outbox_and_preferences.sql` | 通知 Outbox、用户通知偏好和外部渠道字典 |
| `backend/src/main/resources/db/migration/V26__notification_management_phase2.sql` | 通知管理二期：系统公告表、Webhook/公告字典、通知管理菜单和权限 |
| `backend/src/main/resources/db/migration/V27__system_notice_field_capacity_alignment.sql` | 系统公告字段容量与数据字典/设计文档对齐 |
| `backend/src/main/resources/db/migration/V28__notification_phase3_frequency_rules.sql` | 通知三期聚合频控默认规则字典 |
| `backend/src/main/resources/db/migration/V30__notification_aggregate_event_count.sql` | 通知聚合消息真实事件计数字段 |
| `backend/src/main/resources/db/migration/V29__notification_provider_health_status_dict.sql` | Provider 健康状态字典 |
| `backend/src/main/resources/db/migration/V31__notification_mini_program_subscription.sql` | 小程序订阅授权表和授权状态字典 |
| `backend/src/main/resources/db/migration/V35__notification_mini_program_resolution.sql` | 小程序订阅异常处理用户级记录表 |
| `backend/src/main/resources/db/migration/V36__notification_operations_hardening.sql` | 测试投递隔离、订阅处理乐观锁、细粒度权限与历史敏感操作参数清理 |
| `backend/src/main/resources/db/migration/V37__notification_mini_resolution_status_dict.sql` | 小程序订阅异常处理状态字典 |
| `backend/src/main/resources/db/migration/V38__notification_mini_program_eligibility.sql` | 小程序订阅用户资格查询快照与聚合行状态数据库分页索引 |
| `backend/src/main/resources/db/migration/V32__notification_channel_config.sql` | 通知渠道运行配置表，支持小程序订阅消息 PC 运维配置和密钥托管 |
| `backend/src/main/resources/db/migration/V39__system_setting_permission_backfill.sql` | 回填并启用 `system:setting` 权限，确保 ADMIN 可保存通知渠道运行配置 |
| `backend/src/main/resources/db/migration/V41__notification_mini_program_template_window.sql` / `V42__notification_mini_program_template_active_unique.sql` | 小程序订阅模板版本与运维历史表；升级时清理重复 ACTIVE，并通过生成列唯一索引保证每种通知类型只有一个生效模板 |
| `backend/src/main/resources/db/migration/V40__notification_mini_program_page_dict.sql` | 小程序通知跳转页字典，统一默认页和模板页的可选路径 |

## 项目文档

### 开发文档 (`docs/dev/`)
| 文档 | 说明 |
|------|------|
| [开发指南.md](docs/dev/开发指南.md) | 开发流程、代码规范、API文档 |
| [项目设计文档.md](docs/dev/项目设计文档.md) | 技术设计文档 |
| [API调用手册.md](docs/dev/API调用手册.md) | 内部 JWT API 与外部 API Key 签名调用说明 |
| [项目设计规范.md](docs/dev/项目设计规范.md) | 设计规范与约束 |
| [UI设计说明.md](docs/dev/UI设计说明.md) | UI设计说明 |
| [技术栈简明说明.md](docs/dev/技术栈简明说明.md) | 技术原理详解 |

### 运维文档 (`docs/ops/`)
| 文档 | 说明 |
|------|------|
| [操作手册.md](docs/ops/操作手册.md) | 操作指南 |
| [IDEA部署指南.md](docs/ops/IDEA部署指南.md) | 部署教程（本地+生产） |
| [外部API生产部署检查清单.md](docs/ops/外部API生产部署检查清单.md) | 外部 API 授权管理上线前配置、密钥和验证清单 |

### 归档文档 (`docs/archive/`)
| 文档 | 说明 |
|------|------|
| [技术架构评估报告.md](docs/archive/技术架构评估报告.md) | 架构评估与改进建议 |
| [技术架构优化实施方案.md](docs/archive/技术架构优化实施方案.md) | 架构优化方案 |
| [项目完成总结.md](docs/archive/项目完成总结.md) | 完成情况汇总 |

### 其他文档
| 文档 | 说明 |
|------|------|
| [docs/plan/notification-management-platform-closure-feature.md](docs/plan/notification-management-platform-closure-feature.md) | 通知管理平台剩余闭环工作实施计划 |
| [docs/plan/通知管理平台当前实现状态.md](docs/plan/通知管理平台当前实现状态.md) | 通知管理平台当前代码事实、验收状态和剩余缺口的唯一事实源 |
| [CLAUDE.md](CLAUDE.md) | 项目规范（AI助手指导） |
| [docs/plan/multi-platform-adaptation.md](docs/plan/multi-platform-adaptation.md) | 多端适配方案 |
| [frontend-uniapp/README.md](frontend-uniapp/README.md) | 多端前端项目说明 |

## Docker 部署

生产微信小程序公网通过 `https://price.jlmining.com:32080` 访问 API，内网正式入口为 `http://10.7.5.175:32801`。两个入口由价格管理系统自己的 `price-management-frontend` Nginx 容器分别监听，避免同端口 HTTP/HTTPS 冲突；证书和私钥部署到生产项目的 `certs/` 目录并只读挂载，不得提交到仓库。

```bash
# 构建并启动
docker-compose up -d --build

# 查看日志
docker-compose logs -f

# 停止
docker-compose down
```

### Harbor 镜像

生产环境镜像已推送到 Harbor：

```bash
# 拉取镜像
docker pull jlmining.com/pricemanage/price-management-backend:v1.4.0
docker pull jlmining.com/pricemanage/price-management-frontend:v1.4.0
```

## 环境变量配置

生产部署时必须配置以下环境变量：

| 变量 | 说明 |
|------|------|
| `DB_PASSWORD` | 数据库密码 |
| `REDIS_PASSWORD` | Redis 密码 |
| `JWT_SECRET` | JWT 密钥（建议 256 位随机字符串） |
| `DEFAULT_USER_PASSWORD` | 默认用户密码 |
| `API_KEY_ENABLED` | 是否启用外部 API Key 认证，默认 `false` |
| `API_KEY_ENCRYPTION_KEY` | 外部 API Secret AES-GCM 主密钥，Base64 编码 32 字节；创建 API Key 时必须配置 |
| `API_KEY_ENCRYPTION_KEY_VERSION` | 外部 API Secret 主密钥版本，默认 `v1` |
| `API_KEY_TIMESTAMP_WINDOW_SECONDS` | 外部请求时间戳允许窗口，默认 `300` |
| `API_KEY_NONCE_TTL_SECONDS` | 外部请求 Nonce 防重放 TTL，默认 `600` |
| `API_KEY_CACHE_TTL_SECONDS` | 外部授权缓存 TTL 预留配置，第一阶段授权元数据实时查库 |
| `API_KEY_LOG_RETENTION_DAYS` | 外部 API 调用日志保留天数，默认 `180` |
| `PASSWORD_MIN_LENGTH` | 个人中心修改密码最小长度，默认 `8` |
| `PASSWORD_MAX_LENGTH` | 个人中心修改密码最大长度，默认 `32` |
| `PASSWORD_REQUIRE_LETTER` | 修改密码是否必须包含字母，默认 `true` |
| `PASSWORD_REQUIRE_DIGIT` | 修改密码是否必须包含数字，默认 `true` |

开发环境默认使用 `application-dev.yml` 中的开发兜底 key，允许本地直接创建 API Key；如需模拟生产密钥，可在 IDEA Run Configuration 中覆盖 `API_KEY_ENCRYPTION_KEY`。生产 Docker 默认使用 `prod` profile，必须由 `.env` 经 `docker-compose.yml` 传入独立随机 `API_KEY_ENCRYPTION_KEY`。

---

*版本：v1.16.0*
*最后更新：2026-06-02 — 个人中心与账号运维升级*
