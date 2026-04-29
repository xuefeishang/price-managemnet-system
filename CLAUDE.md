# 矿产品价格管理系统 - 项目规范

## 功能变更流程

每次功能变化（包括新增、修改、删除）必须执行以下步骤：

### 步骤1：检查前后端与数据库一致性

**前后端一致性检查：**

- 控制器接口路径 (`/api/xxx`) 与前端 API 调用路径是否一致
- 请求/响应数据结构是否匹配（字段名、类型、嵌套结构）
- 实体类属性与 TypeScript 接口类型是否一致
- 分页/排序参数是否一致

**后端与数据库一致性检查：**

- Entity 字段与数据库表结构是否一致（字段名、类型、约束）
- Repository 查询方法与数据库 SQL 是否匹配
- 新增功能需要检查 init.sql 是否需要更新

**数据字典与数据库一致性检查：**

- 数据字典文档中的表结构与实际数据库表是否一致
- 字段说明、类型、备注是否准确

### 步骤2：更新项目文档

**必须更新的文档：**

| 文档 | 更新内容 |
|------|---------|
| `README.md` | 功能列表、新增功能说明 |
| `开发指南.md` | 开发流程、代码规范、API文档变更 |
| `IDEA部署指南.md` | 部署方式、环境配置变更 |
| `项目设计文档.md` | 数据库表结构、API接口、功能模块设计 |
| `项目完成总结.md` | 功能完成情况表格、更新状态 |
| `UI设计说明.md` | 界面/交互变更 |

**文档更新原则：**

- API 变更：必须更新 `项目设计文档.md` 中的 API 部分
- 数据库变更：必须更新 `项目设计文档.md` 中的表结构部分
- 新增功能：所有文档中功能列表部分必须同步更新
- 界面/交互变更：更新对应的说明文档

### 步骤3：更新数据字典

如果功能涉及数据库变更，必须同步更新数据字典文档，记录：

- 表名、中文说明
- 字段详情（名称、类型、约束、说明）
- 索引信息
- 表关系说明

---

## 项目技术规范

### 核心原则：禁止硬编码

**所有编码值的显示名称必须从字典服务动态获取，严禁在前端代码中硬编码中文标签。**

违反示例与正确做法：

```vue
<!-- ❌ 禁止 -->
<span>{{ product.status === 'ACTIVE' ? '启用' : '停用' }}</span>
<option value="ADMIN">管理员</option>
const roleMap = { ADMIN: '管理员', EDITOR: '编辑者' }

<!-- ✅ 正确 -->
<span>{{ getStatusLabel(product.status) }}</span>
<option v-for="opt in roleOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
const roleName = getRoleLabel(user.role)
```

**允许硬编码的场景：**
- API 请求/响应中的枚举值（如 `status: 'ACTIVE'`）— 这是数据协议
- 后端 Entity/Enum 中的常量定义
- CSS 类名绑定（如 `:class="status?.toLowerCase()"`）

### 后端规范

**Java 代码规范：**

- 包名：`com.pricemanagement`
- 分层：controller / service / repository / entity / dto / config / util / annotation
- 使用 Lombok 简化代码
- 使用 `@OperationLog` 注解记录操作日志

**RESTful API 规范：**

- 路径：`/api/{resource}`
- 分页参数：`page`, `size`
- 时间格式：`yyyy-MM-dd HH:mm:ss`
- 统一响应：`Result<T>` 包装类

**操作日志类型：**

| 类型 | 说明 |
|------|------|
| LOGIN | 用户登录 |
| LOGOUT | 用户登出 |
| CREATE | 新增数据 |
| UPDATE | 更新数据 |
| DELETE | 删除数据 |
| VIEW | 查看数据 |
| EXPORT | 导出数据 |
| IMPORT | 导入数据 |

### 前端规范

**Vue 3 + TypeScript 规范：**

- 使用 Composition API (`<script setup>`)
- 类型定义放在 `src/types/` 目录
- API 接口放在 `src/api/` 目录
- 视图组件放在 `src/views/` 目录

**字典服务使用规范：**

- 所有编码值的显示名称必须通过 `useDict` composable 获取，禁止硬编码
- 页面 `onMounted` 中必须调用 `loadAllDicts()` 加载字典缓存
- 状态显示：使用 `getStatusLabel(key)` 而非 `status === 'ACTIVE' ? '启用' : '停用'`
- 角色显示：使用 `getRoleLabel(key)` 而非硬编码映射
- 货币符号：使用 `getCurrencySymbol(key)` 获取
- 通用字典值：使用 `getDictValue(category, key)` 获取
- 下拉选项：使用 `getDictOptions(category)` 获取启用项列表
- 字典分类参见 `useDict.ts` 中的 `CATEGORY_LABELS`

**ECharts 使用规范：**

- 使用 `vue-echarts` 组件库
- 主题色统一使用项目配色
- 图表响应式处理

### 数据库规范

**表命名：** 使用下划线命名法，如 `operation_log`, `price_history`

**字段规范：**

- 主键：`id BIGINT AUTO_INCREMENT`
- 时间字段：`created_time DATETIME`, `updated_time DATETIME`
- 状态字段：`status VARCHAR(20)`
- 外键：`xxx_id BIGINT`

**必须包含的审计字段：**

- `created_time` - 创建时间
- `updated_time` - 更新时间

---

## 项目结构

```
price-management-system/
├── backend/
│   └── src/main/java/com/pricemanagement/
│       ├── controller/     # 控制器层
│       ├── service/        # 服务层
│       ├── repository/     # 数据访问层
│       ├── entity/         # 实体类
│       ├── dto/            # 数据传输对象
│       ├── config/         # 配置类
│       ├── util/           # 工具类
│       └── annotation/     # 自定义注解
├── frontend/
│   └── src/
│       ├── api/           # API接口
│       ├── components/    # 公共组件
│       ├── composables/   # 组合式函数（useDict等）
│       ├── views/         # 页面视图
│       ├── router/        # 路由配置
│       ├── store/         # 状态管理
│       └── types/         # TypeScript类型
├── plan/                   # Plan 文件夹（功能规划文档）
├── README.md              # 项目入口文档
├── 开发指南.md            # 开发流程与规范
├── IDEA部署指南.md        # 部署教程（本地+生产）
├── 项目设计文档.md        # 技术设计文档
├── 项目完成总结.md        # 项目完成情况
├── UI设计说明.md          # UI设计说明
└── CLAUDE.md              # 本文件（项目规范）
```

---

## 默认用户

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | ADMIN |
| editor | editor123 | EDITOR |
| viewer | viewer123 | VIEWER |

---

## 菜单结构

系统启动时自动初始化以下菜单：

```
首页
  └── 首页

产品管理
  ├── 产品列表
  └── 价格维护

基础运维
  ├── 分类管理
  ├── 字典管理
  │   ├── 产地管理
  │   └── 客户管理
  └── 导入导出

系统管理
  ├── 用户管理（仅ADMIN）
  ├── 菜单配置（仅ADMIN）
  └── 日志管理（仅ADMIN）
```

---

## 操作日志记录规范

所有涉及数据变更的操作都必须记录操作日志：

- 使用 `@OperationLog` 注解自动记录
- 日志内容应包含：操作人、操作模块、操作类型、操作描述、IP地址、状态、错误信息
- 关键操作（删除、高风险）必须记录详细信息

---

---

## 项目文档规范

### 文档职责划分

| 文档 | 职责 | 受众 | 更新时机 |
|------|------|------|---------|
| `CLAUDE.md` | 项目规范、AI助手指导原则 | Claude Code | 规范变更时 |
| `README.md` | 项目入口文档：简介、功能列表、快速启动 | 所有访问者 | 功能变更时 |
| `开发指南.md` | 开发流程、代码规范、Git规范、API文档、常用命令 | 开发者 | 开发流程变更时 |
| `IDEA部署指南.md` | 本地开发环境部署 + 生产环境部署 | 开发者 | 部署方式变更时 |
| `项目设计文档.md` | 技术选型、数据库设计、API设计、功能模块设计 | 设计师/架构师 | 技术变更时 |
| `项目完成总结.md` | 项目概述、完成情况汇总、技术栈 | 管理者/汇报 | 里程碑完成时 |
| `UI设计说明.md` | UI设计说明、配色方案、页面清单 | 设计师 | UI变更时 |

### 文档应用场景

```
新成员加入
  └─ README.md → 开发指南.md → IDEA部署指南.md

开发新功能
  └─ CLAUDE.md → 项目设计文档.md → 开发指南.md

部署生产环境
  └─ IDEA部署指南.md → 生产部署章节

汇报/总结
  └─ 项目完成总结.md

UI设计变更
  └─ UI设计说明.md
```

### 文档更新原则

1. **变更驱动更新**：只有当文档描述的内容发生变更时，才更新对应文档
2. **单一职责**：每个文档只描述其职责范围内的内容，避免重复描述
3. **引用替代重复**：多个文档需要描述同一内容时，采用"参考 XXX.md"方式引用，而非重复描述
4. **删除旧文档**：文档合并时，删除被整合的旧文档，仅保留最新版本

### 文档引用规范

在文档中引用其他文档时，使用以下格式：
```markdown
详见 [文档名](文档名.md)
```

### 历史文档处理

以下文档属于历史文档，已完成其使命，**不应再更新**：
- `价格管理系统-头脑风暴评估报告.md` - 评估阶段文档，已过时

---

## Plan 文件规范

### Plan 文件存放位置

所有 Plan 文件必须放在项目根目录的 `plan/` 文件夹下：

```
price-management-system/
└── plan/
    └── xxx-feature.md    # 功能规划文档
```

### Plan 文件命名规范

- 使用 kebab-case 命名：`{功能名}-feature.md`
- 示例：`dict-feature.md`、`approval-workflow-feature.md`

### Plan 文件内容要求

每个 Plan 文件应包含以下章节：

1. **Context**：需求背景、要解决的问题
2. **实现方案**：数据库设计、后端/前端实现要点
3. **关键参考文件**：复用现有代码的文件路径
4. **实现步骤**：具体的实施顺序
5. **Verification**：验证方式

### Plan 与 CLAUDE.md 的关系

- CLAUDE.md 是项目的永久规范，定义长期遵循的原则
- Plan 文件是临时文档，功能完成后可删除或归档
- 如果 Plan 中的实践被验证为有效，应将其中的规范补充到 CLAUDE.md

---

## 注意事项

1. **数据字典是源代码级别的文档**，必须与数据库表结构保持完全一致
2. **文档更新**应在功能开发完成后、提交代码前完成
3. **API 变更**需要同时通知前端对接人员
4. **数据库迁移**需要记录 SQL 变更脚本
5. **文档归一原则**：每个场景只保留 1 份文档，避免重复。合并时删除旧文件，仅保留最新最完整的版本（如 init.sql 替代 schema.sql + approval_workflow_init.sql 等）
