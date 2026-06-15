---
title: 开发流程
version: v2.0.0
last_updated: 2026-06-15
source: docs/dev/backup/开发指南.md
---

# 开发流程

本文件覆盖开发环境准备、快速开始、开发流程规范、后端启动前置条件、mvn 命令详解与常见错误。

> 完整项目规范见 [CLAUDE.md](../../../CLAUDE.md)，技术选型与架构见 [design/architecture.md](../design/architecture.md)。

## 目录

- [1. 开发环境准备](#1-开发环境准备)
- [2. 快速开始](#2-快速开始)
- [3. 开发流程规范](#3-开发流程规范)
- [4. 后端启动前置条件](#4-后端启动前置条件)
- [5. mvn 命令详解](#5-mvn-命令详解)
- [6. 常见错误与解决](#6-常见错误与解决)
- [7. 增量更新记录](#7-增量更新记录)

---

## 1. 开发环境准备

### 1.1 硬件要求

| 项目 | 最低配置 | 推荐配置 |
|------|---------|---------|
| 内存 | 8GB | 16GB |
| 磁盘空间 | 20GB 可用 | 50GB 可用 |
| CPU | Intel i5 | Intel i7 / Apple M1+ |

### 1.2 后端开发环境

| 软件 | 版本要求 | 下载地址 |
|------|---------|---------|
| Java JDK | **JDK 25+** | https://adoptium.net/ |
| Maven | 3.6+ | https://maven.apache.org/ |
| MySQL | 8.0+ | https://www.mysql.com/ |
| Redis（可选） | 7+（开发可降级使用 6.x） | https://redis.io/ |

> Redis 支持懒加载 + 自动降级机制：Redis 不可用时自动使用 `ConcurrentMapCacheManager` 内存缓存，不影响应用启动。

```bash
# 快速启动 Redis（Docker）
docker run -d --name redis -p 6379:6379 redis:latest
```

### 1.3 前端开发环境

| 软件 | 版本要求 | 下载地址 |
|------|---------|---------|
| Node.js | 16+（推荐 20+） | https://nodejs.org/ |
| Git | 任意 | https://git-scm.com/ |

> 项目使用 UnoCSS（原子化 CSS 引擎）与 Vant 共存；前端依赖完整版本见 [deployment.md](deployment.md) 或 `frontend/package.json`。

### 1.4 IDE 推荐

| 端 | 推荐 | 备选 |
|----|------|------|
| 后端 | IntelliJ IDEA（开启 Lombok 插件） | Eclipse |
| 前端 | VS Code | WebStorm |
| uni-app | HBuilderX | VS Code + uni-app 插件 |

---

## 2. 快速开始

### 2.1 克隆项目

```bash
cd E:\ClaudeCodeProject\price-management-system
# 或 Linux/macOS
cd ~/projects/price-management-system
```

### 2.2 数据库初始化

1. 启动 MySQL 服务
2. 创建数据库：

```sql
CREATE DATABASE IF NOT EXISTS price_management DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE price_management;
```

3. **可选**：导入数据初始化脚本（产品分类和产品数据）：

```bash
# MySQL 命令行
source /path/to/backend/src/main/resources/init.sql;
```

> **注意**：用户数据由 Spring Boot 应用启动时的 `DataInitializer` 自动初始化，无需手动导入。
>
> **生产环境**必须使用 Flyway 迁移脚本（`backend/src/main/resources/db/migration/`），不要直接执行 `init.sql`。

### 2.3 后端项目配置

打开 `backend/src/main/resources/application.yml`，修改数据库连接配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/price_management?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: ${DB_PASSWORD:dev_password}  # 开发环境使用默认值，生产必须通过环境变量注入
    driver-class-name: com.mysql.cj.jdbc.Driver
```

#### Redis 配置（支持懒加载与自动降级）

系统使用 Redis 进行缓存，支持懒加载机制：

- **Redis 可用时**：使用 Redis 分布式缓存
- **Redis 不可用时**：自动降级为内存缓存，不影响应用启动

```yaml
spring:
  data:
    redis:
      host: 10.7.5.175
      port: 6379
      timeout: 5000ms
  cache:
    type: redis
    redis:
      time-to-live: 3600000
```

- 缓存服务：`SysDictService`（字典）、`StyleConfigService`（样式配置）
- TTL：1小时 + 随机偏移（80%-120%）防雪崩
- 降级策略：Redis 连接失败时自动使用 `ConcurrentMapCacheManager`

### 2.4 后端项目启动

#### 方式一：使用 Maven 命令

```bash
cd backend
mvn clean install          # 编译 + 打包到 target/（包含运行测试）
mvn spring-boot:run        # 启动 Spring Boot 应用
```

> **v1.6.11 补充**：`mvn clean install` 会执行测试用例。**开发期间**如需快速启动可使用 `mvn spring-boot:run` 单独命令，跳过 `install`。
>
> **如跳过测试**：`mvn clean install -DskipTests` 或 `mvn clean install -Dmaven.test.skip=true`（后者同时跳过测试编译）。

#### 方式二：使用 IDE

1. 在 IDE 中导入 Maven 项目
2. 找到 `com.pricemanagement.PriceManagementApplication` 类
3. 右键运行

> **v1.6.9 补充**：JDK 25 启动若报 `Unsupported class file major version 69`，说明用了 JDK 17/21，请升级或使用 `JAVA_HOME` 切换。

### 2.5 前端项目启动

**H5 前端（Vue3 + Vite）：**

```bash
cd frontend
npm install
npm run dev
```

**多端前端（uni-app，支持 H5/小程序/APP）：**

```bash
cd frontend-uniapp
npm install
npm run dev:h5          # H5 开发
npm run dev:mp-weixin   # 微信小程序开发
npm run dev:app         # APP 开发
```

详见 [frontend-uniapp/README.md](../../../frontend-uniapp/README.md)

### 2.6 访问应用

| 应用 | 访问地址 |
|------|---------|
| H5 前端 | http://localhost:5173 |
| 后端 API | http://localhost:8080 |
| API 文档（Swagger） | http://localhost:8080/swagger-ui.html |
| 小程序端（uni-app H5 模式） | http://localhost:8080 |

---

## 3. 开发流程规范

### 3.1 功能变更三步流程（CLAUDE.md 强制要求）

> 补全 v1.6.8 §"开发流程" 缺失的 CLAUDE.md 工作流引用。每次功能变更（含新增/修改/删除）必须执行：

#### 步骤 1：检查前后端与数据库一致性

**前后端一致性检查：**

- 控制器接口路径 (`/api/xxx`) 与前端 API 调用路径是否一致
- 请求/响应数据结构是否匹配（字段名、类型、嵌套结构）
- 实体类属性与 TypeScript 接口类型是否一致
- 分页/排序参数是否一致

**后端与数据库一致性检查：**

- Entity 字段与数据库表结构是否一致（字段名、类型、约束）
- Repository 查询方法与数据库 SQL 是否匹配
- 新增功能需要检查 `init.sql` / Flyway 迁移是否需要更新

**ORM 注解一致性检查（必须）：**

| 注解类型 | 检查项 |
|---------|--------|
| `@Table(name="xxx")` | 表名是否存在 |
| `@Column(name="xxx")` | 列名是否存在，类型是否匹配 |
| `@JoinColumn(name="xxx")` | 外键列是否存在 |
| `@ManyToOne` / `@OneToMany` | 外键关系是否正确 |
| `@JoinTable` | 中间表是否存在 |
| `@Version` | 乐观锁列是否存在 |
| `@Transient` | 不参与数据库映射的字段是否有此注解 |

**不一致会导致：** JPA 启动时报错或列数据无法正确读写。

**辅助工具：** 启动应用时设置 `jpa.show-sql: true` 观察 Hibernate 输出的 DDL 语句。

**数据字典与数据库一致性检查：**

- 数据字典文档中的表结构与实际数据库表是否一致
- 字段说明、类型、备注是否准确

#### 步骤 2：更新项目文档

v2.0 起，`docs/dev` 已按职责拆分为 `api/`、`design/`、`workflow/` 三类文档。旧版中文大文件只保留在 `docs/dev/backup/` 作为历史快照，不再作为维护入口。

| 文档 | 更新内容 |
|------|---------|
| `README.md` | 功能列表、新增功能说明 |
| `docs/dev/workflow/development.md` | 开发流程、代码规范变更 |
| `docs/dev/workflow/deployment.md` / `docs/ops/IDEA部署指南.md` | 部署方式、环境配置变更 |
| `docs/dev/design/architecture.md` | 功能模块、架构设计变更 |
| `docs/dev/design/database.md` | 数据库表结构、ER 图变更 |
| `docs/dev/design/api-design.md` | API 设计总览、模块接口变更 |
| `docs/dev/api/internal.md` / `docs/dev/api/external.md` | 内部/外部端点详细契约变更 |
| `docs/dev/coding-standards.md` / `docs/dev/design/specifications.md` | 长期代码规范、设计规范变更 |
| `docs/archive/项目完成总结.md` | 功能完成情况表格、更新状态 |
| `docs/dev/design/ui.md` | 界面/交互变更 |

**文档更新原则：**

- API 变更：必须更新 `docs/dev/api/internal.md` 或 `docs/dev/api/external.md`，并同步 `docs/dev/design/api-design.md`
- 数据库变更：必须更新 `docs/dev/design/database.md`
- 新增功能：所有文档中功能列表部分必须同步更新
- 界面/交互变更：更新 `docs/dev/design/ui.md`

#### 步骤 3：更新数据字典

如果功能涉及数据库变更，必须同步更新数据字典文档，记录：

- 表名、中文说明
- 字段详情（名称、类型、约束、说明）
- 索引信息
- 表关系说明

### 3.2 代码风格规范

#### 后端 Java 代码规范

- 使用 4 个空格缩进
- 变量和方法使用 camelCase
- 类名使用 PascalCase
- 常量使用大写蛇形命名 SNAKE_CASE
- 使用 Lombok 简化代码
- 使用 `@Slf4j` 进行日志记录

#### 前端代码规范

- 使用 2 个空格缩进
- 变量和方法使用 camelCase
- 组件名使用 PascalCase
- 文件名使用 kebab-case
- 使用 TypeScript 进行类型安全检查
- 使用 ESLint 进行代码检查

### 3.3 禁止硬编码规范

**核心原则：所有编码值的显示名称必须从字典服务动态获取，禁止在代码中硬编码。**

#### 禁止的硬编码模式

| 禁止写法 | 正确写法 | 说明 |
|---------|---------|------|
| `status === 'ACTIVE' ? '启用' : '停用'` | `getStatusLabel(status)` | 状态标签 |
| `<option value="ADMIN">管理员</option>` | `v-for="opt in roleOptions"` | 下拉选项 |
| `{ ADMIN: '管理员', EDITOR: '编辑者' }` | `getRoleLabel(role)` | 角色映射 |
| `currency === 'CNY' ? '¥' : '$'` | `getCurrencySymbol(currency)` | 货币符号 |
| `{ CREATE: '创建', UPDATE: '更新' }` | `getDictValue('change_type', key)` | 变更类型等 |

#### 规则详解

1. **状态值**：`ACTIVE/INACTIVE` 等编码的中文显示名称，必须通过 `getStatusLabel()` 获取
2. **角色值**：`ADMIN/EDITOR/VIEWER` 等角色编码，必须通过 `getRoleLabel()` 获取
3. **下拉选项**：表单中的 `<select>` 选项必须通过 `getDictOptions(category)` 动态渲染
4. **货币符号**：必须通过 `getCurrencySymbol()` 获取
5. **通用字典值**：任何编码到中文的映射必须通过 `getDictValue(category, key)` 获取
6. **字典缓存**：每个页面 `onMounted` 中必须调用 `loadAllDicts()` 确保缓存已加载

#### 例外情况

- API 请求参数中的枚举值（如 `status: 'ACTIVE'`）允许硬编码，这是数据协议
- 后端 Entity 中定义的枚举常量允许硬编码
- CSS 类名中的状态值（如 `:class="status?.toLowerCase()"`）允许硬编码

### 3.4 PC 端与移动端适配规范

#### 响应式断点

- **PC端**：`window.innerWidth >= 1024px`
- **移动端**：`window.innerWidth < 1024px`
- `useLayout` 在窗口最小化、页面隐藏或浏览器报告极小 `outerWidth/outerHeight` 时不更新布局宽度，避免最小化过程误触发 PC/移动端布局切换

#### 页面布局要求

**1. 每个页面必须同时支持 PC 端和移动端布局**

每个页面组件（`views` 目录下的 `.vue` 文件）必须包含两套布局：

```vue
<template>
  <!-- PC端布局：屏幕宽度 >= 1024px -->
  <template v-if="isPCLayout">
    <div class="pc-page-layout">
      <!-- PC端专用布局和样式 -->
    </div>
  </template>

  <!-- 移动端布局：屏幕宽度 < 1024px -->
  <template v-else>
    <div class="mobile-page-layout">
      <!-- 移动端专用布局和样式 -->
    </div>
  </template>
</template>

<script setup lang="ts">
// 判断是否为PC布局
const isPCLayout = computed(() => {
  if (typeof window !== 'undefined') {
    return window.innerWidth >= 1024
  }
  return false
})
</script>

<style scoped>
/* PC端样式 */
@media (min-width: 1024px) {
  .pc-page-layout {
    /* PC端专属样式 */
  }
}

/* 移动端样式 */
@media (max-width: 1023px) {
  .mobile-page-layout {
    /* 移动端专属样式 */
  }
}
</style>
```

**2. PC 端设计原则**

- 使用更大的间距和字体
- 表单采用网格布局（grid）而非堆叠布局
- 侧边栏或顶部导航
- 数据表格展示（如产品列表）
- 支持鼠标悬停效果
- 按钮和交互元素适合鼠标点击

**3. 移动端设计原则**

- 单列布局为主
- 顶部导航栏 + 底部操作区
- 触摸友好的大按钮（至少 44px 高度）
- 表单项垂直堆叠
- 使用 Vant 或原生移动端组件
- 卡片式布局展示数据

**4. 不得将移动端样式简单放大作为 PC 端样式**

#### 浏览器最小化兼容规范

- 首页及其他 ECharts 图表必须使用 `useSafeChartAutoresize()` 返回的 `chartAutoresize` 绑定 `vue-echarts` 的 `:autoresize`，页面隐藏或窗口最小化时暂停自动 resize
- 路由 history 必须通过 `router/index.ts` 的 `createAppHistory()` 创建，不直接在业务代码中调用 `createWebHistory()`；该封装会跳过 Vue Router 在 `document.visibilitychange` 中保存滚动位置的监听，避免部分 Edge/Chrome 环境最小化后立即恢复窗口
- 禁止在页面隐藏、窗口 blur、resize 或 visibilitychange 回调中调用 `window.focus()`、`history.replaceState()`、`history.pushState()` 等可能重新激活窗口的操作；如必须处理，应先判断 `document.visibilityState !== 'hidden'`

#### 页面铺满规范

页面内容必须铺满 Layout 提供的右侧展示区域，不得留有明显的空白边距。

**核心规则：**

1. **禁止使用 `max-width` 限制宽度** — PC 端页面容器不得设置 `max-width` 和 `margin: 0 auto`，必须横向铺满可用空间
2. **禁止页面级 padding** — Layout 的 `.pc-main` 已提供 24px 统一 padding，页面内部 PC 容器不得再设置 padding
3. **禁止 `min-height: 100vh`** — 页面渲染在 Layout 的 flex 容器内，使用 `100vh` 会导致内容超出可视区域
4. **使用 flex 布局组织内容** — PC 端容器使用 `display: flex; flex-direction: column; gap: 24px;` 替代子元素的 `margin-bottom`

**正确示例：**

```css
/* 页面根元素 */
.xxx-page {
  background-color: #F5F5F5;
  /* 不要设置 min-height: 100vh */
}

/* PC端容器 */
.pc-xxx {
  display: flex;
  flex-direction: column;
  gap: 24px;
  /* 不要设置 padding、max-width、margin: 0 auto */
}
```

**错误示例：**

```css
/* 以下写法均禁止 */
.pc-xxx {
  padding: 32px;          /* 错误：padding 由 Layout 统一提供 */
  max-width: 1100px;      /* 错误：限制了页面宽度，无法铺满 */
  margin: 0 auto;         /* 错误：居中缩小了可用空间 */
}

.xxx-page {
  min-height: 100vh;      /* 错误：在 Layout flex 容器内会导致溢出 */
}
```

**布局结构说明：**

```
┌──────────┬──────────────────────────────────┐
│          │  Layout .pc-main (padding: 24px) │
│  侧边栏   │ ┌──────────────────────────────┐ │
│  240px   │ │  页面 .pc-xxx (无padding)     │ │
│          │ │  ┌──────────────────────────┐ │ │
│          │ │  │  内容区域（铺满）         │ │ │
│          │ │  └──────────────────────────┘ │ │
│          │ └──────────────────────────────┘ │
└──────────┴──────────────────────────────────┘
```

**新建页面检查清单（PC 端铺满）：**

- [ ] PC 端容器未设置 `max-width`、`margin: 0 auto`、`padding`
- [ ] 页面根元素未使用 `min-height: 100vh`
- [ ] 子元素间距通过父容器 `gap` 控制，而非子元素 `margin-bottom`
- [ ] 页面在宽屏（1920px+）下仍能铺满整个右侧展示区域

#### 组件复用

- PC 端和移动端公用的 UI 组件应抽离到 `components` 目录
- 如需针对不同端做不同展示，使用条件渲染而非覆盖样式

#### 常见布局模式参考

**PC端参考布局：**

```
┌─────────────────────────────────────┐
│  Logo    导航菜单                    │
├─────────────────────────────────────┤
│  ┌─────────┐  ┌──────────────────┐  │
│  │ 侧边栏  │  │                  │  │
│  │ 菜单    │  │    主内容区      │  │
│  │         │  │                  │  │
│  │         │  │                  │  │
│  └─────────┘  └──────────────────┘  │
└─────────────────────────────────────┘
```

**移动端参考布局：**

```
┌─────────────────────┐
│ ☰  Logo      操作  │  ← 顶部导航
├─────────────────────┤
│                     │
│     主内容区         │
│                     │
│                     │
├─────────────────────┤
│  🏠  📦  ⚙️  👤   │  ← 底部导航（可选）
└─────────────────────┘
```

#### 新建页面检查清单

- [ ] PC 端和移动端两套布局都已实现
- [ ] 响应式断点正确（1024px）
- [ ] PC 端采用适合桌面端的网格/侧边栏布局
- [ ] 移动端采用适合触屏的单列/卡片布局
- [ ] 表单在 PC 端使用 grid 布局，在移动端使用单列堆叠
- [ ] 按钮在移动端有足够的触摸区域（至少 44px）
- [ ] 已在不同屏幕宽度下测试

### 3.5 按钮设计规范

> 详细设计规范请参阅 [design/ui.md](../design/ui.md)。

#### 按钮类型与尺寸

**PC 端按钮：**

| 按钮类型 | 高度 | 内边距 | 字体大小 | 圆角 | 用途 |
|---------|------|--------|---------|------|------|
| 主要按钮（btn-primary） | 40px | 12px 24px | 14px | 8px | 保存、提交、确认 |
| 次要按钮（btn-secondary） | 36px | 8px 16px | 13px | 6px | 取消、返回 |
| 图标按钮（btn-icon） | 32px × 32px | - | - | 6px | 编辑、删除、添加子菜单 |
| 小型按钮（btn-small） | 28px | 4px 12px | 12px | 4px | 表格内操作 |

**移动端按钮：**

| 按钮类型 | 最小高度 | 内边距 | 字体大小 | 圆角 | 用途 |
|---------|---------|--------|---------|------|------|
| 主要按钮（btn-primary） | 44px | 12px 24px | 14px | 8px | 保存、提交、确认 |
| 次要按钮（btn-secondary） | 40px | 10px 20px | 14px | 8px | 取消、返回 |
| 图标按钮（btn-icon） | 36px × 36px | - | - | 8px | 编辑、删除 |
| 小型按钮（btn-small） | 36px | 8px 12px | 13px | 6px | 表格内操作 |

#### 按钮样式代码规范

**主要按钮：**

```css
.btn-primary {
  padding: 12px 24px;
  min-height: 40px;
  background: #0D6E6E;
  color: #FFFFFF;
  border: none;
  border-radius: 8px;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 150ms;
}

.btn-primary:hover {
  background: #0D8A8A;
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
```

**次要按钮：**

```css
.btn-secondary {
  padding: 10px 20px;
  min-height: 36px;
  background: #F5F5F5;
  color: #666666;
  border: none;
  border-radius: 6px;
  font-family: 'Inter', sans-serif;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 150ms;
}
```

**图标按钮：**

```css
.btn-icon {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: #666666;
  cursor: pointer;
  border-radius: 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: all 150ms;
}

.btn-icon:hover {
  background: #E5E5E5;
  color: #1A1A1A;
}

.btn-icon.danger:hover {
  background: rgba(239, 68, 68, 0.1);
  color: #EF4444;
}
```

#### 按钮命名规范

- `btn-primary` - 主要操作按钮（保存、确认）
- `btn-secondary` - 次要操作按钮（取消、返回）
- `btn-icon` - 图标按钮（编辑、删除）
- `btn-icon danger` - 危险操作图标按钮（删除）
- `btn-small` - 小型操作按钮
- `btn-save` - 保存按钮
- `btn-cancel` - 取消按钮
- `btn-confirm` - 确认按钮

#### 按钮颜色规范

| 用途 | 色值 | 说明 |
|------|------|------|
| 主色调 | `#0D6E6E`（深青色） | 主要按钮背景 |
| 悬停色 | `#0D8A8A`（浅青色） | 主要按钮悬停 |
| 次要色 | `#F5F5F5`（浅灰） | 次要按钮背景 |
| 危险色 | `#EF4444`（红色） | 删除等危险操作 |
| 文字色 | `#666666`（灰色） | 次要按钮文字 |

#### 一致性要求

1. 同一页面内同类按钮必须使用相同的类名
2. 不同页面相同功能的按钮必须使用相同的样式
3. 移动端按钮触摸区域不得小于 44px × 44px
4. 按钮的圆角、字体、颜色必须遵循上述规范

### 3.6 菜单规范

#### 系统菜单结构（完整树 + 顶部上下文导航）

```
一级菜单（左侧完整菜单树）
  └── 二级菜单（左侧完整菜单树）
        └── 三级菜单（页面顶部上下文 Tab）
              └── 四级菜单（三级 Tab 点击下拉）
```

**示例：**

```
基础运维
  └── 字典管理（父级菜单，无跳转）
        ├── 产地管理 → /origins
        └── 客户管理 → /customers
```

产品管理默认包含：产品列表 `/products`、价格维护 `/price-maintenance`、预算管理 `/budget-management`、价格查询 `/price-query`。预算管理仅 ADMIN/EDITOR 在菜单中可见。

**交互规则：**

- 左侧菜单必须展示当前用户可见的完整菜单树，支持三级及更深层级
- 带 `path` 的菜单项点击必须优先执行路由跳转；仅当菜单项无 `path` 且有子菜单时，点击才用于展开/收起
- 无 `path` 菜单只作为分组，点击只展开/收起，不自动跳转到第一个子菜单
- 页面顶部不再重复展示一级菜单下的二级入口
- 当前路由位于三级分组时，顶部展示同组三级 Tab
- 四级及以后菜单通过点击三级 Tab 后下拉展示；更深层级仍可通过左侧完整树访问
- 编辑页、详情页等非菜单路由应通过 `route.meta.activeMenu` 回落到所属菜单高亮
- 左侧菜单点击后必须立即更新选中反馈；菜单数据按当前角色缓存，菜单配置变更时再强制刷新，避免路由切换时重复请求 `/api/menus/visible`
- 菜单树选中态应由 Layout 预计算 active id 集合传入，子菜单组件不得在每个菜单项渲染时递归扫描整棵子树

#### 菜单创建流程

**1. 后端 - 数据库 `menu_item` 表**

在 `init.sql` 中插入菜单数据：

```sql
-- 一级菜单（父级）
INSERT INTO menu_item (id, parent_id, name, path, icon, sort_order, visible, roles, created_time, updated_time)
SELECT 24, 3, '字典管理', NULL, 'dict', 5, TRUE, '["ADMIN","EDITOR"]', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM menu_item WHERE id = 24);

-- 二级菜单（字典管理的子菜单）
INSERT INTO menu_item (id, parent_id, name, path, icon, sort_order, visible, roles, created_time, updated_time)
SELECT 40, 24, '产地管理', '/origins', NULL, 1, TRUE, '["ADMIN","EDITOR"]', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM menu_item WHERE id = 40);

INSERT INTO menu_item (id, parent_id, name, path, icon, sort_order, visible, roles, created_time, updated_time)
SELECT 41, 24, '客户管理', '/customers', NULL, 2, TRUE, '["ADMIN","EDITOR"]', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM menu_item WHERE id = 41);
```

**字段说明：**

| 字段 | 说明 |
|------|------|
| `id` | 唯一标识，不能与现有菜单冲突 |
| `parent_id` | 父级菜单ID，顶级菜单为 NULL |
| `name` | 菜单显示名称 |
| `path` | 路由路径，无跳转时设为 NULL |
| `icon` | 图标名称（需在 Layout.vue 中添加对应的 SVG） |
| `sort_order` | 排序序号，数字越小越靠前 |
| `visible` | 是否显示 |
| `roles` | 允许访问的角色数组，JSON 格式 |

**2. 前端 - 路由配置**

在 `router/index.ts` 中添加路由：

```typescript
{
  path: '/origins',
  name: 'Origins',
  component: () => import('../views/Origins.vue'),
  meta: { title: '产地管理', requiresAuth: true }
}
```

**3. 前端 - Layout.vue（仅当添加新图标时）**

如果菜单使用了新的图标名称，需在 Layout.vue 的图标渲染逻辑中添加对应的 SVG：

```vue
<svg v-else-if="menu.icon === 'your-icon'" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
  <!-- SVG path here -->
</svg>
```

#### 菜单权限说明

- `roles` 字段控制哪些角色可以访问该菜单
- 格式为 JSON 数组：`'["ADMIN","EDITOR"]'`
- 常用权限组合：
  - `["ADMIN"]` - 仅管理员
  - `["ADMIN","EDITOR"]` - 管理员和编辑
  - `["ADMIN","EDITOR","VIEWER"]` - 所有用户

---

## 4. 后端启动前置条件

> **v1.6.10 补全**：本节补充 §"快速开始" 中缺失的启动前置条件。

### 4.1 JDK 与构建工具

| 依赖 | 版本要求 | 说明 |
|------|---------|------|
| **JDK** | **JDK 25**（不是 JDK 17/21） | `pom.xml` 显式声明 `java.version: 25` |
| **Maven** | 3.6+（支持 Spring Boot 4.0.6） | 推荐 3.9+ |
| **MySQL** | 8.0+ | 生产使用 8.0 |
| **Redis** | 7+ | 开发可降级使用 6.x |

### 4.2 环境变量（生产环境必填）

```bash
# 数据库
DB_PASSWORD              # 数据库密码（生产环境必须设置）

# Redis
REDIS_PASSWORD           # Redis 密码（生产环境必须设置）

# JWT
JWT_SECRET               # JWT 签名密钥（生产环境必须设置）

# 默认用户
DEFAULT_USER_PASSWORD    # 默认用户密码（生产环境必须设置）
```

> 详细环境变量列表见 [deployment.md](deployment.md) §环境变量配置。

### 4.3 启动前自检清单

- [ ] JDK 版本为 25（`java -version`）
- [ ] Maven 版本 ≥ 3.6（`mvn -version`）
- [ ] MySQL 服务运行中（`mysql -u root -p`）
- [ ] 数据库 `price_management` 已创建
- [ ] Redis 服务运行中或可降级（`redis-cli ping` 或不启动 Redis 测试降级）
- [ ] `application.yml` 中数据库连接配置正确
- [ ] 环境变量已设置（生产环境）

---

## 5. mvn 命令详解

### 5.1 核心命令

| 命令 | 作用 | 是否执行测试 |
|------|------|------------|
| `mvn compile` | 编译源代码 | 否 |
| `mvn test` | 运行测试 | 仅测试 |
| `mvn package` | 打包（生成 JAR/WAR） | 是 |
| `mvn install` | 打包并安装到本地 Maven 仓库 | 是 |
| `mvn clean` | 清理 `target/` 目录 | 否 |
| `mvn spring-boot:run` | 启动 Spring Boot 应用 | 否 |
| `mvn dependency:tree` | 查看依赖树 | 否 |
| `mvn dependency:analyze` | 分析依赖使用情况 | 否 |

### 5.2 常用组合命令

```bash
# 开发期快速启动（不打包）
mvn spring-boot:run

# 完整编译 + 打包
mvn clean package

# 完整编译 + 打包 + 安装到本地仓库
mvn clean install

# 跳过测试的快速编译
mvn clean install -DskipTests

# 跳过测试编译（更激进）
mvn clean install -Dmaven.test.skip=true

# 仅清理 + 编译（不打包）
mvn clean compile

# 生成源码包（用于 IDE 关联源码）
mvn source:jar install

# 清理 IDE 缓存
mvn clean -U
```

### 5.3 `-DskipTests` vs `-Dmaven.test.skip=true`

| 参数 | 跳过测试执行 | 跳过测试编译 | 速度 | 适用场景 |
|------|-------------|-------------|------|---------|
| `-DskipTests` | ✅ | ❌（仍编译测试类） | 中 | 跳过慢测试但保留测试代码 |
| `-Dmaven.test.skip=true` | ✅ | ✅（不编译测试类） | 快 | 完全跳过测试，节省时间 |

**推荐用法：**

```bash
# 日常开发推荐：跳过测试执行（保留测试代码）
mvn clean install -DskipTests

# 紧急修复推荐：完全跳过测试（最快）
mvn clean install -Dmaven.test.skip=true

# CI/CD 推荐：不跳过测试，确保质量
mvn clean install
```

### 5.4 启动后端应用

```bash
# 方式一：使用 spring-boot:run 插件
cd backend
mvn spring-boot:run

# 方式二：使用编译后的 JAR
mvn clean package -DskipTests
java -jar target/price-management-backend-1.0.0.jar

# 方式三：使用 IDE
# 在 IDE 中右键 PriceManagementApplication.java → Run

# 方式四：使用 Docker
docker compose up -d backend
```

### 5.5 查看依赖与冲突

```bash
# 导出依赖树到文件
mvn dependency:tree > dependency_tree.txt

# 检查版本冲突
mvn dependency:analyze

# 排除特定依赖
mvn dependency:tree -Dverbose -Dincludes=com.fasterxml.jackson.core
```

---

## 6. 常见错误与解决

### 错误 1：`Unsupported class file major version 69`

**原因：** JDK 版本过低（使用了 JDK 17/21，项目需要 JDK 25）。

**解决：**

```bash
# 1. 检查当前 JDK 版本
java -version

# 2. 安装 JDK 25（下载地址：https://adoptium.net/）

# 3. 设置 JAVA_HOME 指向 JDK 25
# Windows:
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25
set PATH=%JAVA_HOME%\bin;%PATH%

# Linux/macOS:
export JAVA_HOME=/path/to/jdk-25
export PATH=$JAVA_HOME/bin:$PATH

# 4. 验证
java -version
mvn -version
```

### 错误 2：`Java compiler version does not match`

**原因：** Maven 使用的 JDK 与项目要求不一致。

**解决：**

```bash
# 设置 JAVA_HOME 指向 JDK 25
# Windows (PowerShell):
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# Linux/macOS:
export JAVA_HOME=/path/to/jdk-25
export PATH=$JAVA_HOME/bin:$PATH

# IDE 中：File → Project Structure → SDKs → 选择 JDK 25
# IDE 中：File → Settings → Build → Compiler → Java Compiler → Project bytecode version → 25
```

### 错误 3：`Cannot find symbol` Lombok 生成方法

**原因：** IDEA 未启用 Lombok 插件或 Lombok 编译失败。

**解决：**

1. **IDEA 安装 Lombok 插件：**
   - File → Settings → Plugins → Marketplace → 搜索 "Lombok" → Install
   - 重启 IDEA

2. **启用注解处理：**
   - File → Settings → Build → Compiler → Annotation Processors
   - 勾选 "Enable annotation processing"

3. **检查 Lombok 依赖（pom.xml）：**

```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>1.18.46</version>
    <scope>provided</scope>
</dependency>
```

4. **清理并重新编译：**

```bash
mvn clean install -U
# IDEA 中：Build → Rebuild Project
```

### 错误 4：Flyway 迁移校验失败

**错误信息：** `Migration checksum mismatch`

**原因：** 某个已执行过的 Flyway 迁移文件被修改。

**解决：**

- **开发环境：** 确认历史文件已恢复后执行 `mvn flyway:repair`
- **生产环境：**
  1. 立即停止部署
  2. 确认迁移文件来源（Git 历史 / 备份）
  3. 恢复迁移文件或新增 `Vxx__fix_migration.sql` 补偿脚本
  4. **生产环境禁止执行 `repair`**

> 历史迁移文件执行后应保持不变，后续数据库结构或初始化数据调整必须新增 `Vxx__*.sql` 迁移文件。

### 错误 5：本地 Tomcat 临时目录权限失败

**错误信息：** `Existing directory ... Temp ... is not owned by ...`

**解决：**

```yaml
# application-dev.yml
server:
  tomcat:
    basedir: ./target/tomcat
```

或设置环境变量 `TOMCAT_BASEDIR`：

```bash
# Windows
set TOMCAT_BASEDIR=E:\tmp\price-management-tomcat

# Linux/macOS
export TOMCAT_BASEDIR=/tmp/price-management-tomcat
```

### 错误 6：端口被占用

如果 8080 或 5173 端口被占用：

**后端端口修改（`application.yml`）：**

```yaml
server:
  port: 8081
```

**前端端口修改（`vite.config.ts`）：**

```typescript
server: {
  port: 5174
}
```

**查找占用端口的进程：**

```bash
# Windows
netstat -ano | findstr :8080
taskkill /PID <PID> /F

# Linux/macOS
lsof -i :8080
kill -9 <PID>
```

### 错误 7：数据库连接失败

检查以下几点：

- MySQL 服务是否正在运行
- 用户名和密码是否正确
- 数据库名称是否存在
- 防火墙是否允许连接
- `application.yml` 中 `url` 是否正确（注意 `useSSL`、`serverTimezone` 参数）

### 错误 8：前端无法访问后端 API

检查：

- 后端是否正在运行
- 后端端口是否正确（默认 8080）
- CORS 配置是否正确（开发环境默认允许 localhost）
- 前端 `vite.config.ts` 的 `proxy` 配置是否正确

### 错误 9：Redis 连接失败（启动报错）

**症状：** 启动报 `Unable to connect to Redis`

**原因：** Redis 服务不可用。

**解决：**

- **默认行为：** 系统已实现 Redis 懒加载 + 自动降级机制，Redis 不可用时会自动切换为内存缓存，**不影响应用启动**
- **确认降级生效：** 检查应用启动日志，看到 `CacheManager: ConcurrentMapCacheManager` 表示已降级
- **真要使用 Redis：** 检查 Redis 服务是否运行（`redis-cli ping`），防火墙是否允许 6379 端口

---

## 7. 增量更新记录

### 7.1 v1.6.8 一致性更新（2026-06-14）

本节对账文档与 v1.6.7 实际状态，原则：仅追加不破坏既有内容。

#### 补全内容

- §3.1 功能变更三步流程（CLAUDE.md 强制要求）
- §3.4 项目实际目录结构
- §4 后端启动前置条件
- §8 端口架构与启动命令

#### 项目实际目录结构（v1.6.10 补全）

> 修正既有 §"项目结构说明" 中不完整的目录树。

**后端目录结构（实际）：**

```
backend/src/main/java/com/pricemanagement/
├── annotation/          # 自定义注解（如 @OperationLog）
├── config/              # 配置类
│   ├── properties/      # 配置属性类（SecurityProperties 等）
│   └── ...（其它配置类）
├── constants/           # 常量类
├── controller/          # 控制器层
│   └── external/        # 外部 API 控制器（API Key 认证）
├── dto/                 # 数据传输对象
├── entity/              # 数据实体（JPA）
├── exception/           # 自定义异常类
├── listener/            # 事件监听器（Excel 导入）
├── repository/          # 数据访问层
├── service/             # 服务层
│   └── notification/    # 通知服务子包（消息/Outbox/小程序 Provider 等）
└── util/                # 工具类
```

**包统计（2026-06-14）：** 32 个 Controller、36+ 个 Service、55 个 Entity、76+ 个 DTO、15 个 Config、30+ 个 Repository。

**前端 H5 目录结构（实际）：**

```
frontend/src/
├── api/                 # API 接口定义（按业务模块拆分）
├── assets/              # 静态资源
├── components/          # 通用组件
│   ├── dict/            # 字典相关组件（DictCategoryHelpPanel 等）
│   ├── home/            # 首页相关组件
│   ├── icons/           # 图标组件
│   ├── layout/          # 布局相关组件（SidebarMenuTree 等）
│   └── style-settings/  # 样式设置组件 + preview/ 子目录
├── composables/         # 组合式函数（useDict/useStyleSettingsWorkbench 等）
├── constants/           # 前端常量
├── router/              # 路由配置
├── store/               # 状态管理（Pinia）
├── style/               # 全局样式与 CSS 变量
├── types/               # TypeScript 类型定义
├── utils/               # 工具函数
└── views/               # 页面组件（30 个）
```

**前端 uni-app 目录结构（实际）：**

```
frontend-uniapp/src/
├── api/                 # API 接口（与 H5 共用接口签名）
├── components/          # 通用组件
│   ├── home/            # 首页相关（RiskAlertsPanel/SummarySection）
│   ├── mini-trend-chart/# 趋势图
│   ├── mp-echarts/      # 微信小程序 ECharts 适配
│   └── price-trend-chart/# 价格趋势图（含触摸交互优化）
├── composables/         # 组合式函数
├── custom-tab-bar/      # 自定义底栏
├── pages/               # 主包页面（7 个目录）
│   ├── home/            # 首页
│   ├── history/         # 历史价格
│   ├── login/           # 登录
│   ├── notifications/   # 通知
│   ├── price-maintenance/ # 价格维护
│   ├── products/        # 产品列表/详情/编辑
│   └── profile/         # 我的
├── pages-sub/           # 分包页面
│   ├── approval/        # 审批入口
│   └── basic/           # 基础数据浏览
├── store/               # 状态管理
├── types/               # TypeScript 类型
└── utils/               # 工具函数
```

### 7.2 v1.6.9 一致性更新（2026-06-14）

#### JDK 25 启动错误

- **v1.6.9 补充：** JDK 25 启动若报 `Unsupported class file major version 69`，说明用了 JDK 17/21，请升级或使用 `JAVA_HOME` 切换
- 见 [§6 错误 1](#错误-1unsupported-class-file-major-version-69)

#### 按钮设计规范

- 详细设计规范请参阅 [design/ui.md](../design/ui.md)
- 见 [§3.5 按钮设计规范](#35-按钮设计规范)

### 7.3 v1.6.10 一致性更新（2026-06-14）

#### 后端目录结构补全

- 原目录树缺失 `annotation/ constants/ exception/ notification/ properties/` 等子包
- `config/` 实际还有 `properties/` 子目录
- `controller/` 实际还有 `external/` 子目录
- 见 [§7.1](#71-v168-一致性更新2026-06-14)

#### 低严重度偏差清理

- 文档与代码轻微不一致已修复
- 后端启动前置条件补全

### 7.4 v1.6.11 一致性更新（2026-06-14）

#### mvn clean install 行为变更

- **`mvn clean install` 会执行测试用例**：开发期间如需快速启动可使用 `mvn spring-boot:run` 单独命令，跳过 `install`
- **如跳过测试：** `mvn clean install -DskipTests` 或 `mvn clean install -Dmaven.test.skip=true`（后者同时跳过测试编译）
- 见 [§2.4 后端项目启动](#24-后端项目启动) 与 [§5 mvn 命令详解](#5-mvn-命令详解)

#### 端口架构统一

- PC 端和小程序共用 32080 端口（公网 HTTPS）
- 内网 HTTP 入口：32801
- 详细见 [deployment.md](deployment.md) §端口架构

---

## 相关文档

- [CLAUDE.md](../../../CLAUDE.md) — 项目规范（永久文档）
- [README.md](README.md) — 工作流总览
- [git.md](git.md) — Git 规范
- [deployment.md](deployment.md) — 部署指南
- [learning-path.md](learning-path.md) — 学习路径
- [design/architecture.md](../design/architecture.md) — 技术选型与架构设计
- [design/database.md](../design/database.md) — 数据库设计
- [design/api-design.md](../design/api-design.md) — API 设计总览
- [design/specifications.md](../design/specifications.md) — 项目设计规范
- [coding-standards.md](../coding-standards.md) — 代码规范
- [docs/ops/IDEA部署指南.md](../../ops/IDEA部署指南.md) — 本地/生产部署详细步骤
