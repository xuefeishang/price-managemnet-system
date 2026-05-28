# 矿产品价格管理系统

企业级前后端分离的矿产品价格展示与管理系统，面向企业内部员工使用。

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端（H5） | Vue 3.4 + TypeScript 5.3 + Vant 4.8 + UnoCSS + Pinia 2.1 + ECharts 6.0 |
| 前端（多端） | uni-app Vue3 + TypeScript + Pinia（支持 H5/APP/小程序） |
| 后端 | Spring Boot 4.0.6 + Java 25 + Spring Security 7.0 |
| 数据库 | MySQL 8.0/8.4 + Redis 7.x（支持懒加载，Redis 不可用时自动降级为内存缓存） |
| 认证 | JWT (Access Token + Refresh Token) |
| 部署 | Docker + Nginx |

## 主要功能

- **用户认证与权限管理**
  - JWT Token 认证（双 Token 机制：Access Token 24h + Refresh Token 7d）
  - 三种角色：管理员（ADMIN）、编辑者（EDITOR）、查看者（VIEWER）
  - 动态权限系统（32个权限码，登录时获取用户权限列表，刷新个人资料时同步权限缓存）
  - API 限流保护（登录 5次/分钟/IP）

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
  - 价格维护页对齐首页产品列表工作台，支持搜索、产品类别过滤、分页、拖拽调整产品顺序并同步首页排序，同时录入当日售价并保留昨日售价、价格变化、月均价对照
  - 价格查询页面向普通用户开放，只读查询每日价格，支持按日期、关键字、分类分页筛选；列表展示产品名称（内含产地胶囊与规格）、当日/昨日售价和“较昨日”，行选中联动右侧 7日/30日/90日/年度趋势和价格摘要
  - 价格历史记录 + ECharts 折线图可视化
  - 首页图表在浏览器最小化或页面隐藏时暂停自动 resize，避免窗口尺寸异常导致图表错位或浏览器窗口被恢复
  - 产品列表/价格维护新增产地列；首页产品卡片在存在产地时默认展示“产地 + 字典名称”信息胶囊
  - 首页产品列表升级为分页工作台，支持表格/卡片/自动模式、搜索、分类筛选、分页、排序和行选中联动主价格曲线，并按浏览器宽度自适应避免页面横向滚动
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

- **操作日志管理（仅管理员）**
  - 日志列表查询与筛选
  - 统计分析（趋势图、饼图、柱状图）
  - 月度报表、年度报表 + 用户排行

- **审批流程管理**
  - 审批工作流配置（创建、编辑、删除、激活/停用）
  - 审批节点配置（节点类型、审批角色、顺序）
  - 审批请求管理（待审批、我的申请）

- **字典管理**
  - 数据字典 CRUD（按分类管理，启用/停用）
  - 前端字典服务（useDict composable，全局缓存）
  - 所有页面硬编码标签动态化
  - 受保护分类隔离（样式配置分类默认隐藏，仅可查看）
  - "显示系统配置"开关（ADMIN可查看受保护分类）
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

## 项目文档

### 开发文档 (`docs/dev/`)
| 文档 | 说明 |
|------|------|
| [开发指南.md](docs/dev/开发指南.md) | 开发流程、代码规范、API文档 |
| [项目设计文档.md](docs/dev/项目设计文档.md) | 技术设计文档 |
| [项目设计规范.md](docs/dev/项目设计规范.md) | 设计规范与约束 |
| [UI设计说明.md](docs/dev/UI设计说明.md) | UI设计说明 |
| [技术栈简明说明.md](docs/dev/技术栈简明说明.md) | 技术原理详解 |

### 运维文档 (`docs/ops/`)
| 文档 | 说明 |
|------|------|
| [操作手册.md](docs/ops/操作手册.md) | 操作指南 |
| [IDEA部署指南.md](docs/ops/IDEA部署指南.md) | 部署教程（本地+生产） |

### 归档文档 (`docs/archive/`)
| 文档 | 说明 |
|------|------|
| [技术架构评估报告.md](docs/archive/技术架构评估报告.md) | 架构评估与改进建议 |
| [技术架构优化实施方案.md](docs/archive/技术架构优化实施方案.md) | 架构优化方案 |
| [项目完成总结.md](docs/archive/项目完成总结.md) | 完成情况汇总 |

### 其他文档
| 文档 | 说明 |
|------|------|
| [CLAUDE.md](CLAUDE.md) | 项目规范（AI助手指导） |
| [docs/plan/multi-platform-adaptation.md](docs/plan/multi-platform-adaptation.md) | 多端适配方案 |
| [frontend-uniapp/README.md](frontend-uniapp/README.md) | 多端前端项目说明 |

## Docker 部署

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

---

*版本：v1.15.0*
*最后更新：2026-05-28 — 新增普通用户日常价格查询与导出页面*
