# 个人中心与账号运维功能规划

## Context

当前系统已经具备个人中心的基础能力：

- 后端 `AuthController` 已提供 `GET /api/auth/profile`、`PUT /api/auth/profile`、`PUT /api/auth/password`。
- 前端已有 `frontend/src/views/Profile.vue` 和路由 `/profile`，路由标题为“个人管理”。
- `sys_user` 已包含 `nickname`、`email`、`phone`、`department`、`dept_id`、`last_login_time`、`last_login_ip`、`login_count`、`password_updated_time`、`is_locked` 等字段。
- 系统已有 `operation_log` 操作日志表和 `/api/logs` 管理查询能力。
- 系统已有 refresh token 表和 `RefreshTokenService`，具备撤销用户 refresh token 的基础能力。

但现有个人中心仍偏“基础资料页”，缺少面向每个登录用户的账号运维能力：

- 个人资料、密码、安全信息、会话管理、我的操作记录没有形成统一页面信息架构。
- 修改个人资料和修改密码逻辑直接放在 `AuthController`，职责和认证接口混在一起。
- 修改密码后未明确是否撤销旧 refresh token、是否需要重新登录。
- 个人操作记录只能由管理员在操作日志页查看，普通用户缺少自助审计入口。
- 安全信息如最近登录 IP、密码更新时间、登录次数、账号锁定状态没有完整展示。
- 缺少“退出其他设备”“我的登录历史”“个人偏好”等运维型能力。

本方案目标是把现有“个人管理”升级为统一的 **个人中心与账号运维页面**，所有登录用户可自助维护个人资料、修改密码、查看安全状态和审计自己的操作记录，同时不破坏管理员的用户管理能力。

## Goals

1. 所有登录用户都能访问 `/profile`，不区分 ADMIN / EDITOR / VIEWER。
2. 用户只能查看和修改自己的资料，不能通过个人中心修改角色、部门、状态、权限等管理字段。
3. 用户可自助修改密码，修改成功后按安全策略撤销旧 refresh token。
4. 用户可查看账号安全信息：最近登录时间、最近登录 IP、登录次数、密码更新时间、账号状态等。
5. 用户可查看自己的最近操作记录和登录/登出记录。
6. 用户可退出其他设备或撤销自己的其他会话。
7. 页面显示的角色、状态、操作类型等编码值必须走字典或已有权限/角色数据，禁止硬编码中文映射。
8. 保持现有 `/api/auth/profile` 兼容，避免现有 `userStore.fetchProfile()` 和路由守卫回归。
9. 用户可查看登录历史和当前有效会话，支持单设备撤销、退出其他设备、退出全部设备。
10. 个人偏好暂缓上线，避免影响或误导系统级样式、默认首页和分页设置；后续需先完成全局偏好策略再恢复。
11. 密码策略在后端统一校验，前端只做即时提示，不作为安全依据。
12. 所有个人中心接口都必须从认证上下文识别当前用户，不接受前端传入 `userId` 作为授权依据。

## 9.5+ 达成原则

本方案按一次完整交付设计，目标评分 9.5+。关键安全能力全部纳入本次交付；个人偏好因涉及系统整体设置语义，当前版本暂缓展示。

| 维度 | 达成方式 |
|------|----------|
| 安全性 | 修改密码强制撤销 refresh token、会话可撤销、操作日志脱敏、个人日志按当前用户隔离 |
| 可执行性 | 明确接口、DTO、迁移、服务分层、前端组件和验收标准 |
| 项目一致性 | 兼容 `/api/auth/profile`，新增 `/api/profile/**`，复用现有 `sys_user`、`operation_log`、`refresh_token` |
| 运维能力 | 安全信息、登录历史、会话管理和我的操作记录统一收敛到个人中心 |
| 可维护性 | `AuthController` 只保留认证职责，个人中心逻辑迁移到 `ProfileService` |
| 用户体验 | PC / 移动端统一入口，危险操作二次确认，修改资料后顶部昵称同步刷新 |

## Non-Goals

- 不允许用户自助修改用户名、员工编号、角色、部门、状态、权限。
- 本次不做头像上传、短信验证、邮箱验证或双因素认证。
- 不替代管理员的用户管理页面。
- 不开放普通用户查看他人操作日志。
- 本次不新增复杂通知偏好和深度个性化首页配置。

## 功能范围

### 一次完整交付范围

目标：把已有个人资料和修改密码能力升级为稳定、可审计、可自助运维的完整个人中心。

本次交付包含：

- 基本资料查看和编辑。
- 修改密码。
- 安全信息只读展示。
- 我的操作记录。
- 登录历史。
- 当前会话与其他设备列表。
- 单设备撤销、退出其他设备、退出全部设备。
- 个人偏好设置暂缓上线；后端预留能力可保留，前端不展示入口。
- 顶部用户菜单入口优化。
- 后端服务分层优化，个人中心逻辑从 `AuthController` 拆到 `ProfileService`。
- 操作日志记录：资料更新、密码修改、撤销会话、查看个人操作记录。
- 安全事件记录：登录成功、登录失败、修改密码、撤销会话。

本次交付需要新增迁移，用于补齐 refresh token 设备信息、登录历史和个人偏好。由于外部 API 阶段二规划已预留 `V21__external_api_auth_phase2.sql`，本功能如在其后实施，建议使用：

```text
backend/src/main/resources/db/migration/V22__personal_profile_management.sql
```

### 后续扩展

后续可选增强不影响本方案 9.5+ 达标：

- 头像上传。
- 邮箱验证。
- 手机号短信验证。
- 双因素认证。
- 历史密码限制。
- 更细粒度的个人通知偏好。

## 页面设计

### 参考图诊断与升级方向

参考图 `docs/UI/user_home.png` 展示的是更成熟的“用户中心”形态，核心不是单个资料表单，而是 **账号摘要 + 资料工作区 + 运维看板** 的组合。现有方案需要在内容架构和视觉语言上进一步升级：

| 维度 | 参考图表现 | 本方案升级要求 |
|------|------------|----------------|
| 首屏层级 | 左侧系统菜单稳定，右侧用户中心首屏聚焦账号信息 | `/profile` 首屏必须直接呈现用户身份、安全状态和可操作资料区 |
| 内容组织 | 上方为资料 Tab，底部为安全、操作、会话三块看板 | 保留 Tab 工作区，同时增加首屏运维概览区，减少用户来回切 Tab |
| 色彩语言 | 大面积白底 + 浅灰边框 + 深青绿色主操作色 | 使用项目主色青绿作为个人中心主色，辅以蓝色、橙色表达设备/风险，不做单一绿色页面 |
| 信息密度 | 表单和卡片间距克制，适合后台系统长期使用 | 避免营销式大卡片，使用紧凑、可扫描的运维面板 |
| 可达性 | 头像、Tab、顶部返回工作台构成清晰入口 | 全局头像点击进入个人中心，个人中心提供返回工作台与刷新入口 |

升级后页面目标：

- 让用户一进入页面即可知道“我是谁、账号是否安全、最近发生了什么、有哪些设备在线”。
- 将“编辑资料”保持为主工作区，但把安全评分、登录设备、最近操作前置到首屏。
- 视觉上延续项目后台系统的白底、浅边框、低阴影风格，用青绿色承载主行动与当前状态。
- 所有卡片、Tab、按钮、表格都应能适配 PC 和移动端，不出现文字拥挤或横向溢出。

### 路由与入口

保留现有路由：

```text
/profile
```

入口：

```text
全局头像
└── 点击进入 /profile

用户菜单或快捷入口
├── 个人中心
├── 修改密码（可定位到账号安全 Tab）
└── 退出登录
```

菜单结构中如已有“个人中心 /profile”，应避免在左侧主菜单重复占用过多空间；推荐主要通过全局头像进入，左侧菜单可按当前产品习惯保留或隐藏。

入口行为要求：

- PC 端左侧栏底部头像点击进入 `/profile`。
- 移动端抽屉内头像点击进入 `/profile` 并关闭抽屉。
- 如果当前已在 `/profile`，点击头像不重复刷新页面，只保持当前页面。
- 头像按钮必须有 `title="进入个人中心"`，并使用 button 语义支持键盘访问。

### 页面信息架构

页面采用后台管理系统风格，避免营销式大卡片布局。升级后采用三层架构：

```text
个人中心
├── 顶部标题栏
│   ├── 标题：用户中心
│   ├── 问候语：{nickname}，您好！管理你的账户信息与安全设置。
│   ├── 刷新按钮
│   └── 返回工作台按钮
├── 首屏主体
│   ├── 左侧账号摘要
│   └── 右侧资料 Tab 工作区
└── 运维概览区
    ├── 账号安全概览
    ├── 最近操作
    └── 登录设备 / 会话
```

PC 端首屏布局：

```text
用户中心 header
┌───────────────┬─────────────────────────────────────────┐
│ 账号摘要 320px │ 基本资料 / 账号安全 / 操作记录 / ... Tabs │
└───────────────┴─────────────────────────────────────────┘
┌───────────────┬───────────────────┬─────────────────────┐
│ 账号安全概览   │ 最近操作           │ 登录设备 / 会话       │
└───────────────┴───────────────────┴─────────────────────┘
```

移动端布局：

```text
用户中心 header
账号摘要
基本资料 / 账号安全 / 操作记录 / 登录历史 / 会话管理 Tabs
账号安全概览
最近操作
登录设备 / 会话
```

### 首屏模块内容

#### 1. 顶部标题栏

内容：

- 左侧：`用户中心` 标题。
- 副标题：`{nickname}，您好！管理你的账户信息与安全设置。`
- 右侧：刷新按钮、返回工作台按钮。

交互：

- 刷新按钮重新拉取 `profile/security/logs/sessions/preferences`。
- 返回工作台优先跳转个人偏好中的 `defaultHomePath`；如果无权限或为空，回退 `/home`。
- 移动端按钮可收敛为图标按钮，避免挤压标题。

#### 2. 账号摘要卡

内容：

```text
头像首字母
昵称
用户名
角色标签
状态 / 部门 / 员工编号 / 最近登录
安全评分 / 登录设备数 / 操作记录数
```

设计要求：

- 头像使用青绿色径向或线性渐变，保留白色首字母。
- 角色标签使用浅青绿底，不写死角色中文，通过角色字典或角色数据展示。
- 状态使用字典显示；启用状态用绿色圆点，锁定/停用状态用橙色或红色提醒。
- 底部三项统计使用小型指标卡：安全评分、登录设备、操作记录。

#### 3. Tab 工作区

Tab 顺序按用户使用频率和参考图一致；个人偏好暂缓展示：

```text
基本资料 → 账号安全 → 操作记录 → 登录历史 → 会话管理
```

要求：

- 当前 Tab 使用青绿色文字和底部 3px 指示线。
- Tab 容器顶部保留 1px 浅灰分隔线。
- PC 端 Tab 内容区与账号摘要卡等高时优先展示完整表单；高度不足时内容区内部滚动。
- 移动端 Tab 可横向滚动，但不得隐藏当前选中态。

#### 4. 运维概览区

首屏底部新增三块概览，不替代完整 Tab，只提供高频摘要：

| 模块 | 内容 | 点击行为 |
|------|------|----------|
| 账号安全概览 | 密码强度、最近修改密码、登录密码、安全邮箱、账号状态 | 点击进入“账号安全” Tab |
| 最近操作 | 最近 3 条操作记录，展示模块、描述、时间 | 点击“查看全部”进入“操作记录” Tab |
| 登录设备 / 会话 | 当前在线数、本月登录数、常用设备占比、最近 3 个设备 | 点击“管理设备”进入“会话管理” Tab |

概览区数据不足时：

- 最近操作为空：显示“暂无操作记录”。
- 登录设备为空：显示“暂无有效会话”。
- 安全信息缺失：显示 `-`，不显示假数据。

### 颜色与视觉规范

个人中心应延续参考图的“轻量后台 + 青绿色主色”方向，但避免整页单一青绿色。建议使用以下页面级 token，实际可映射到项目已有 CSS 变量：

| 用途 | 建议颜色 | 使用位置 |
|------|----------|----------|
| 页面背景 | `#F7F9FB` | profile 页面外层背景 |
| 卡片背景 | `#FFFFFF` | 摘要卡、Tab 容器、概览卡 |
| 主色 | `#0D6E6E` | 主按钮、当前 Tab、头像渐变起点 |
| 主色深色 | `#075E5E` | 主按钮 hover、头像渐变终点 |
| 成功色 | `#10B981` | 启用状态、安全通过、在线点 |
| 信息蓝 | `#2563EB` | 登录设备、浏览器/终端图标 |
| 风险橙 | `#F97316` | 异常登录、常用设备占比提醒 |
| 危险红 | `#DC2626` | 退出全部设备、锁定/失败提醒 |
| 主文本 | `#0F172A` | 标题、表单主要文本 |
| 次文本 | `#64748B` | 副标题、辅助说明 |
| 边框 | `#E2E8F0` | 卡片、输入框、分隔线 |

视觉细节：

- 页面外层背景使用浅灰，不使用大面积渐变和装饰光斑。
- 卡片圆角控制在 8px，符合项目 UI 规范。
- 卡片阴影使用极轻阴影：`0 1px 2px rgba(15, 23, 42, 0.04)`，避免浮夸。
- 主按钮使用青绿色实底，危险按钮使用红色描边或红色文本，避免误触。
- 指标数字可使用青绿、蓝、橙三类颜色区分安全、设备、操作，不全部使用同一色相。
- 表单输入框高度建议 40px，按钮高度建议 40px，保证后台系统密度。

### 组件架构升级

现有 `Profile.vue` 不应继续承载全部模板与业务逻辑，建议按参考图拆成页面容器 + 模块组件：

```text
frontend/src/views/Profile.vue
frontend/src/components/profile/ProfileHeader.vue
frontend/src/components/profile/ProfileSummaryCard.vue
frontend/src/components/profile/ProfileTabs.vue
frontend/src/components/profile/ProfileBasicForm.vue
frontend/src/components/profile/ProfileSecurityPanel.vue
frontend/src/components/profile/ProfileOperationLogs.vue
frontend/src/components/profile/ProfileLoginHistory.vue
frontend/src/components/profile/ProfileSessions.vue
frontend/src/components/profile/ProfilePreferences.vue
frontend/src/components/profile/ProfileSecurityOverview.vue
frontend/src/components/profile/ProfileRecentOperations.vue
frontend/src/components/profile/ProfileSessionOverview.vue
frontend/src/composables/useProfileCenter.ts
```

职责划分：

| 文件 | 职责 |
|------|------|
| `Profile.vue` | 页面布局、Tab 状态、统一加载态、错误态 |
| `useProfileCenter.ts` | 聚合 profile/security/logs/sessions/preferences 数据加载和刷新 |
| `ProfileHeader.vue` | 标题、问候语、刷新、返回工作台 |
| `ProfileSummaryCard.vue` | 左侧账号摘要与三项指标 |
| `ProfileTabs.vue` | Tab 切换、移动端横向滚动 |
| `ProfileBasicForm.vue` | 基本资料表单和保存 |
| `ProfileSecurityPanel.vue` | 账号安全详情和修改密码 |
| `ProfileOperationLogs.vue` | 我的操作记录完整列表 |
| `ProfileLoginHistory.vue` | 登录历史完整列表 |
| `ProfileSessions.vue` | 会话管理完整列表和危险操作 |
| `ProfilePreferences.vue` | 个人偏好表单 |
| `ProfileSecurityOverview.vue` | 底部安全概览卡 |
| `ProfileRecentOperations.vue` | 底部最近操作摘要 |
| `ProfileSessionOverview.vue` | 底部设备/会话摘要 |

组件边界规则：

- API 调用集中在 `useProfileCenter.ts` 或页面容器中，展示组件通过 props 和 emits 通信。
- 危险操作二次确认可在容器统一处理，也可由对应模块 emits 出语义事件。
- 字典读取可由容器统一注入常用 label 方法，或组件内部使用 `useDict`，但不得在模板写中文枚举映射。
- 列表组件必须保留空状态、加载态、错误态。

### 参考图落地差异说明

参考图中的“安全评分”“本月登录”“常用设备占比”可以分两步实现：

| 能力 | 本次可执行实现 | 后续增强 |
|------|----------------|----------|
| 安全评分 | 用规则计算静态分：密码已设置、邮箱已绑定、账号正常、最近登录正常、无失败登录 | 引入风险模型、异常 IP、异地登录 |
| 本月登录 | 从 `sys_login_history` 按当前用户和当前月统计成功登录次数 | 增加趋势图 |
| 常用设备占比 | 用最近 90 天登录历史按 `deviceName` 聚合，展示最高占比 | 增加设备指纹稳定识别 |
| 部门/职位 | 当前 `department` 可展示，职位字段如暂无则显示角色名 | 新增职位字段或接入组织架构 |
| 安全邮箱 | 当前使用 `email` 是否为空判断绑定状态 | 邮箱验证后展示已验证/未验证 |

本次不要求新增复杂风险模型，但 UI 结构必须预留这些数据位，避免后续大改。

### 页面信息架构（旧版基线）

以下为旧版基线结构，作为升级前后对照。升级实施时应以“三层架构”和“首屏模块内容”为准。

```text
个人中心
├── 账号摘要
│   ├── 昵称 / 用户名
│   ├── 主角色
│   ├── 所属部门
│   └── 最近登录
└── 工作区 Tabs
    ├── 基本资料
    ├── 账号安全
    ├── 我的操作记录
    └── 会话管理
    └── 个人偏好（暂缓）
```

移动端可改为纵向分区：

```text
账号摘要
基本资料
账号安全
我的操作记录
会话管理
个人偏好（暂缓）
```

### 基本资料

字段：

| 字段 | 可编辑 | 来源 | 说明 |
|------|--------|------|------|
| 用户名 | 否 | `sys_user.username` | 登录账号，不允许自助修改 |
| 员工编号 | 否 | `sys_user.employee_id` | 管理字段 |
| 昵称 | 是 | `sys_user.nickname` | 页面展示名，必填 |
| 邮箱 | 是 | `sys_user.email` | 可选，校验邮箱格式 |
| 手机号 | 是 | `sys_user.phone` | 可选，校验手机号格式 |
| 部门 | 否 | `department` / `deptId` | 管理字段 |
| 角色 | 否 | `roles` / `role` | 使用角色数据展示 |
| 账号状态 | 否 | `status` | 使用字典显示 |
| 创建时间 | 否 | `createdTime` | 只读 |
| 更新时间 | 否 | `updatedTime` | 只读 |

保存规则：

- `nickname` 必填，去除首尾空白。
- `email` 为空时允许清空；非空必须符合邮箱格式。
- `phone` 为空时允许清空；非空必须符合手机号格式。
- 返回数据不得包含 `password`。

### 账号安全

展示项：

| 内容 | 来源 | 说明 |
|------|------|------|
| 最近登录时间 | `sys_user.last_login_time` | 登录成功后更新 |
| 最近登录 IP | `sys_user.last_login_ip` | 登录成功后更新 |
| 登录次数 | `sys_user.login_count` | 登录成功后累加 |
| 密码更新时间 | `sys_user.password_updated_time` | 修改密码后更新 |
| 登录方式 | `sys_user.login_type` | PASSWORD / WECHAT 等 |
| 账号锁定状态 | `sys_user.is_locked` | 只读展示 |

修改密码：

- 当前密码。
- 新密码。
- 确认新密码。
- 密码强度提示。

密码策略：

- 后端统一校验，前端只做即时提示。
- 长度 8-32 位。
- 必须同时包含字母和数字。
- 不能包含空白字符。
- 不能与旧密码相同。
- 不能与 `username`、`nickname`、`phone` 完全相同。
- 不在日志、异常、响应中返回任何密码原文。
- 密码策略通过 `SecurityProperties` 扩展配置项管理，默认值即上述规则。

修改成功策略：

- 更新 `password_updated_time`。
- 撤销当前用户全部 refresh token。
- 前端清理 access token 和 refresh token。
- 跳转登录页，提示“密码已修改，请重新登录”。
- 该策略固定写入 `docs/dev/API调用手册.md`，不再保留“继续当前会话”的分支。

### 我的操作记录

展示当前用户自己的操作日志，普通用户不可切换查看他人。

筛选项：

- 操作类型。
- 操作模块。
- 时间范围。
- 关键字。

列表字段：

| 字段 | 说明 |
|------|------|
| 操作时间 | `created_time` |
| 操作模块 | 使用 `operation_module` 字典显示 |
| 操作类型 | 使用操作日志类型显示 |
| 操作描述 | 日志描述 |
| IP 地址 | 操作 IP |
| 状态 | 成功 / 失败 |

默认展示最近 30 天，分页参数沿用 `page`、`size`。

过滤依据：

- 优先按 `operation_log.user_id = 当前用户 ID` 查询。
- 对历史日志中 `user_id` 为空但 `username` 有值的数据，兼容追加 `operation_log.username = 当前用户名`。
- 接口不接受 `userId` 参数。
- 返回字段沿用现有操作日志 DTO，敏感字段如密码、token、Secret 必须脱敏或不返回。

### 会话管理

能力：

- 展示当前有效会话列表。
- 标识当前会话。
- 支持撤销单个其他设备会话。
- 支持退出其他设备。
- 支持退出全部设备并重新登录。
- 修改密码后自动退出全部设备并重新登录。

会话字段：

| 字段 | 说明 |
|------|------|
| id | refresh token 记录 ID |
| current | 是否当前会话 |
| deviceName | 设备展示名 |
| ipAddress | 登录或最近刷新 IP |
| userAgent | 浏览器 User-Agent，列表展示时截断 |
| createdTime | 会话创建时间 |
| lastUsedTime | 最近使用时间 |
| expiryDate | 过期时间 |
| revoked | 是否已撤销 |

当前会话识别：

- 前端调用会话接口时携带当前 refresh token 的 SHA-256 指纹，或后端从请求体接收当前 refresh token 原文后只做内存比对，不记录日志。
- 后端响应只返回是否当前会话，不返回 token 原文或 token 指纹。

### 登录历史

展示最近登录记录和失败记录，帮助用户发现异常登录。

列表字段：

| 字段 | 说明 |
|------|------|
| loginTime | 登录时间 |
| ipAddress | 登录 IP |
| userAgent | 登录设备 |
| result | SUCCESS / FAILED |
| failureReason | 失败原因摘要 |

默认展示最近 90 天，分页参数沿用 `page`、`size`。

### 个人偏好（暂缓）

当前版本个人偏好不在用户中心页面展示。原因是表格密度、默认首页、主题模式和默认分页大小都会影响系统整体体验，如果只在用户中心保存而未被全局统一消费，会造成“设置了但不生效”或“个人设置覆盖系统设置”的误解。

暂缓策略：

- 前端 `/profile` 不展示“个人偏好” Tab。
- 前端不调用 `/api/profile/preferences`。
- “返回工作台”固定回 `/home`，不读取 `defaultHomePath`。
- 后端 `sys_user_preference` 和 `/api/profile/preferences` 可作为预留能力保留，暂不作为当前上线功能验收项。
- 后续如恢复，需要先设计全局偏好策略，包括登录跳转、主题、分页、表格密度在全系统的应用边界。

偏好项：

| 字段 | 可选值 | 默认值 | 说明 |
|------|--------|--------|------|
| tableDensity | COMPACT / DEFAULT / COMFORTABLE | DEFAULT | 表格密度 |
| defaultHomePath | `/home` 或用户有权限的路由 | `/home` | 登录后默认页 |
| themeMode | SYSTEM / LIGHT / DARK | SYSTEM | 主题跟随方式 |
| pageSize | 10 / 20 / 50 / 100 | 20 | 默认分页大小 |

规则：

- `defaultHomePath` 必须是当前用户有权限访问的路由。
- 偏好只影响当前用户，不写入全局样式配置。
- 字段值使用字典或前端常量选项，页面显示名称不得硬编码在业务逻辑中。

## 后端设计

### 分层调整

建议新增：

```text
backend/src/main/java/com/pricemanagement/controller/ProfileController.java
backend/src/main/java/com/pricemanagement/service/ProfileService.java
backend/src/main/java/com/pricemanagement/dto/ProfileDTO.java
backend/src/main/java/com/pricemanagement/dto/ProfileUpdateRequest.java
backend/src/main/java/com/pricemanagement/dto/ProfileSecurityDTO.java
backend/src/main/java/com/pricemanagement/dto/ProfilePasswordChangeRequest.java
backend/src/main/java/com/pricemanagement/dto/ProfileSessionDTO.java
backend/src/main/java/com/pricemanagement/dto/ProfileLoginHistoryDTO.java
backend/src/main/java/com/pricemanagement/dto/ProfilePreferenceDTO.java
```

兼容策略：

- 保留现有 `/api/auth/profile`、`/api/auth/password`。
- 新增 `/api/profile/**` 作为个人中心语义化接口。
- 旧接口委托 `ProfileService`，避免逻辑重复。
- 前端可逐步从 `src/api/auth.ts` 迁移到 `src/api/profile.ts`；迁移完成后仍保留旧接口兼容。

### API 设计

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/profile` | 获取个人中心资料 |
| PUT | `/api/profile` | 更新个人资料 |
| GET | `/api/profile/security` | 获取账号安全信息 |
| PUT | `/api/profile/password` | 修改密码 |
| GET | `/api/profile/operation-logs` | 查询我的操作记录 |
| GET | `/api/profile/sessions` | 查询我的会话概要 |
| DELETE | `/api/profile/sessions/{id}` | 撤销指定其他设备会话 |
| DELETE | `/api/profile/sessions/others` | 退出其他设备 |
| DELETE | `/api/profile/sessions/all` | 退出全部设备并重新登录 |
| GET | `/api/profile/login-history` | 查询我的登录历史 |
| GET | `/api/profile/preferences` | 获取个人偏好 |
| PUT | `/api/profile/preferences` | 更新个人偏好 |

兼容接口：

| 方法 | 路径 | 兼容方式 |
|------|------|----------|
| GET | `/api/auth/profile` | 委托 `ProfileService.getCurrentProfile()` |
| PUT | `/api/auth/profile` | 委托 `ProfileService.updateCurrentProfile()` |
| PUT | `/api/auth/password` | 委托 `ProfileService.changePassword()` |

### DTO 结构

`ProfileDTO`：

```json
{
  "id": 1,
  "username": "admin",
  "employeeId": "000001",
  "nickname": "管理员",
  "email": "admin@example.com",
  "phone": "13800000000",
  "department": "管理部",
  "deptId": 1,
  "role": "ADMIN",
  "roles": ["ADMIN"],
  "permissions": ["user:read"],
  "status": "ACTIVE",
  "loginType": "PASSWORD",
  "lastLoginTime": "2026-06-02 10:00:00",
  "lastLoginIp": "127.0.0.1",
  "loginCount": 10,
  "passwordUpdatedTime": "2026-06-02 10:00:00",
  "createdTime": "2026-05-01 10:00:00",
  "updatedTime": "2026-06-02 10:00:00"
}
```

`ProfileUpdateRequest`：

```json
{
  "nickname": "管理员",
  "email": "admin@example.com",
  "phone": "13800000000"
}
```

`ProfilePasswordChangeRequest`：

```json
{
  "oldPassword": "old-password",
  "newPassword": "new-password",
  "confirmPassword": "new-password"
}
```

`ProfileSessionDTO`：

```json
{
  "id": 1,
  "current": true,
  "deviceName": "Chrome / Windows",
  "ipAddress": "127.0.0.1",
  "userAgent": "Mozilla/5.0 ...",
  "createdTime": "2026-06-02 10:00:00",
  "lastUsedTime": "2026-06-02 10:30:00",
  "expiryDate": "2026-06-09 10:00:00",
  "revoked": false
}
```

`ProfilePreferenceDTO`：

```json
{
  "tableDensity": "DEFAULT",
  "defaultHomePath": "/home",
  "themeMode": "SYSTEM",
  "pageSize": 20
}
```

### 操作日志

以下操作必须记录：

| 操作 | module | type | description |
|------|--------|------|-------------|
| 更新个人资料 | 个人中心 | UPDATE | 更新个人资料 |
| 修改密码 | 个人中心 | UPDATE | 修改登录密码 |
| 退出其他设备 | 个人中心 | UPDATE | 退出其他设备 |
| 撤销指定会话 | 个人中心 | UPDATE | 撤销设备会话 |
| 退出全部设备 | 个人中心 | UPDATE | 退出全部设备 |
| 更新个人偏好 | 个人中心 | UPDATE | 更新个人偏好 |
| 查看个人操作记录 | 个人中心 | VIEW | 查看我的操作记录 |

注意：

- 修改密码日志不得记录旧密码、新密码、确认密码。
- 更新资料日志只记录字段名和脱敏后的变更摘要。
- 个人操作记录接口只返回当前用户自己的日志。

### 会话与 Token

固定策略：

- 修改密码后默认撤销所有 refresh token，前端清理本地 token 并跳转登录页。
- `DELETE /api/profile/sessions/others` 撤销除当前 refresh token 之外的所有有效 token。
- `DELETE /api/profile/sessions/{id}` 只能撤销当前用户自己的非当前会话。
- `DELETE /api/profile/sessions/all` 撤销当前用户全部 refresh token，响应成功后前端跳转登录页。
- 所有撤销接口必须写操作日志。

`RefreshTokenService` 增加：

```java
List<ProfileSessionDTO> getUserSessions(Long userId, String currentRefreshToken);
void revokeSession(Long userId, Long sessionId, String currentRefreshToken);
void revokeOtherSessions(Long userId, String currentRefreshToken);
void revokeAllSessions(Long userId);
```

## 数据库设计

新增迁移文件：

```text
backend/src/main/resources/db/migration/V22__personal_profile_management.sql
```

如果实际实施时 `V22` 已被占用，使用下一个可用版本号。

### refresh_token 扩展字段

| 字段 | 类型 | 说明 |
|------|------|------|
| device_name | VARCHAR(100) | 设备展示名，如 Chrome / Windows |
| ip_address | VARCHAR(50) | 登录或最近刷新 IP |
| user_agent | VARCHAR(500) | 浏览器 User-Agent |
| last_used_time | DATETIME | 最近使用时间 |

索引：

- `idx_refresh_token_user_revoked(user_id, revoked)`
- `idx_refresh_token_expiry(expiry_date)`

ORM 检查：

- `RefreshToken.deviceName` -> `device_name`
- `RefreshToken.ipAddress` -> `ip_address`
- `RefreshToken.userAgent` -> `user_agent`
- `RefreshToken.lastUsedTime` -> `last_used_time`

### sys_login_history

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 用户 ID，失败且无法识别用户时为空 |
| username | VARCHAR(100) | 登录名 |
| login_time | DATETIME | 登录时间 |
| ip_address | VARCHAR(50) | 登录 IP |
| user_agent | VARCHAR(500) | 登录设备 |
| result | VARCHAR(20) | SUCCESS / FAILED |
| failure_reason | VARCHAR(500) | 失败原因摘要 |
| created_time | DATETIME | 创建时间 |

索引：

- `idx_login_history_user_time(user_id, login_time)`
- `idx_login_history_username_time(username, login_time)`
- `idx_login_history_result(result)`

### sys_user_preference

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| user_id | BIGINT | 用户 ID，唯一 |
| table_density | VARCHAR(20) | COMPACT / DEFAULT / COMFORTABLE |
| default_home_path | VARCHAR(200) | 默认首页路径 |
| theme_mode | VARCHAR(20) | SYSTEM / LIGHT / DARK |
| page_size | INT | 默认分页大小 |
| created_time | DATETIME | 创建时间 |
| updated_time | DATETIME | 更新时间 |
| version | BIGINT | 乐观锁 |

索引：

- `uk_user_preference_user(user_id)`

### 字典设计

新增或确认字典分类：

| 分类 | 说明 |
|------|------|
| profile_table_density | 个人表格密度 |
| profile_theme_mode | 个人主题模式 |
| login_result | 登录结果 |

字典项：

| 分类 | 字典键 | 显示值 |
|------|--------|--------|
| profile_table_density | COMPACT | 紧凑 |
| profile_table_density | DEFAULT | 默认 |
| profile_table_density | COMFORTABLE | 宽松 |
| profile_theme_mode | SYSTEM | 跟随系统 |
| profile_theme_mode | LIGHT | 浅色 |
| profile_theme_mode | DARK | 深色 |
| login_result | SUCCESS | 成功 |
| login_result | FAILED | 失败 |

## 前端设计

### 文件结构

建议新增或调整：

```text
frontend/src/api/profile.ts
frontend/src/types/profile.ts
frontend/src/views/Profile.vue
frontend/src/components/profile/ProfileSummary.vue
frontend/src/components/profile/ProfileBasicForm.vue
frontend/src/components/profile/ProfileSecurityPanel.vue
frontend/src/components/profile/ProfileOperationLogs.vue
frontend/src/components/profile/ProfileSessions.vue
frontend/src/components/profile/ProfileLoginHistory.vue
frontend/src/components/profile/ProfilePreferences.vue
frontend/src/components/profile/ProfileSecurityOverview.vue
frontend/src/components/profile/ProfileRecentOperations.vue
frontend/src/components/profile/ProfileSessionOverview.vue
frontend/src/composables/useProfileCenter.ts
```

现有 `frontend/src/views/Profile.vue` 可以保留并拆分组件，避免继续膨胀。

### 页面布局 CSS 执行细节

页面外层建议使用以下结构，保证和参考图一致且可响应：

```text
.profile-page
├── .profile-header
├── .profile-primary-grid
│   ├── .profile-summary-card
│   └── .profile-tab-card
└── .profile-overview-grid
    ├── .security-overview-card
    ├── .recent-operations-card
    └── .session-overview-card
```

PC 端布局建议：

```css
.profile-page {
  min-height: 100%;
  background: #F7F9FB;
  padding: 24px;
}

.profile-primary-grid {
  display: grid;
  grid-template-columns: minmax(280px, 320px) minmax(0, 1fr);
  gap: 16px;
}

.profile-overview-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.05fr) minmax(0, 1.05fr);
  gap: 16px;
  margin-top: 16px;
}
```

移动端布局建议：

```css
@media (max-width: 768px) {
  .profile-page {
    padding: 12px;
  }

  .profile-primary-grid,
  .profile-overview-grid {
    grid-template-columns: 1fr;
  }

  .profile-tab-list {
    overflow-x: auto;
    white-space: nowrap;
  }
}
```

执行要求：

- 不使用固定页面高度制造版式，列表区域遵循项目自适应表格规范。
- 账号摘要卡在 PC 端宽度稳定，右侧工作区使用 `minmax(0, 1fr)` 防止表格撑破布局。
- 所有卡片使用统一 `.profile-card` 基础样式：白底、8px 圆角、1px 浅边框、轻阴影。
- 表单采用两列布局，移动端降为一列；输入框文字不得溢出。
- 概览卡内部列表最多展示 3 条，超出通过“查看全部 / 管理设备”进入完整 Tab。

### 概览数据计算规则

为实现参考图中的首屏指标，前端可在现有接口数据基础上计算轻量摘要，不必新增专门聚合接口；如后续性能需要，再新增 `/api/profile/overview`。

| 指标 | 数据来源 | 计算方式 |
|------|----------|----------|
| 安全评分 | `security`、`profile`、`loginHistory` | 初始 100 分；邮箱为空 -10；密码更新时间为空 -20；账号锁定 -40；最近 7 天失败登录每条 -5，最低 0 |
| 登录设备数 | `sessions` | `sessions.filter(item => !item.revoked).length` |
| 操作记录数 | `operationLogs.totalElements` | 使用后端分页 total |
| 本月登录 | `loginHistory` | 当前月 `result === SUCCESS` 的记录数；若分页数据不足，展示当前页统计并标注为“近期” |
| 常用设备占比 | `loginHistory` 或 `sessions` | 按 `deviceName` 聚合最高占比；无数据时显示 `-` |
| 最近操作 | `operationLogs.content` | 取最近 3 条 |
| 最近设备 | `sessions` | 按 `lastUsedTime` 倒序取最近 3 条 |

如果要保证“本月登录”和“常用设备占比”严格准确，后续可新增后端 DTO：

```text
GET /api/profile/overview
```

返回：

```json
{
  "securityScore": 92,
  "activeSessionCount": 3,
  "operationCount": 126,
  "monthlyLoginCount": 5,
  "topDeviceRatio": 98
}
```

### 交互设计

- PC 端：左侧账号摘要固定宽度，右侧 Tab 内容区自适应高度。
- 移动端：纵向卡片式分区，操作按钮固定在区块底部。
- 顶部刷新按钮必须有加载态，刷新期间禁用重复点击。
- 返回工作台按钮优先使用个人偏好 `defaultHomePath`，无权限时回退 `/home`。
- 点击账号摘要的安全评分、底部安全概览卡，切换到“账号安全” Tab。
- 点击最近操作卡“查看全部”，切换到“操作记录” Tab。
- 点击登录设备卡“管理设备”，切换到“会话管理” Tab。
- 修改密码使用独立表单，不和个人资料共用保存按钮。
- “退出其他设备”必须二次确认。
- “退出全部设备”“修改密码”成功后必须清理本地 token 并跳转登录页。
- 会话列表必须清楚标识当前会话，当前会话不能被单独撤销。
- 操作日志表格遵循项目自适应表格规范，分页按钮不得静默隐藏页码。
- 登录历史默认展示最近 90 天，失败原因只展示摘要。

### 字典与硬编码

- 账号状态使用 `getStatusLabel()` 或 `getDictValue()`。
- 操作类型使用操作日志类型字典。
- 操作模块使用 `operation_module` 字典。
- 角色显示优先复用登录返回的 `roles` 和角色管理接口，不在页面写死 `ADMIN -> 管理员`。
- 表格密度、主题模式、登录结果使用字典或集中常量，不在模板中散落中文分支。

### 视觉实现优先级

| 优先级 | 内容 | 说明 |
|--------|------|------|
| P0 | 头像入口、三层页面骨架、Tab 顺序、基础资料表单 | 影响用户能否按参考图进入和使用 |
| P0 | 账号摘要卡、安全状态、最近登录、登录设备数 | 首屏识别用户身份和安全状态 |
| P1 | 底部三块运维概览卡 | 提升参考图一致性和运维效率 |
| P1 | 颜色 token、按钮状态、Tab 指示线、统一卡片样式 | 提升视觉完成度 |
| P1 | 组件拆分和 `useProfileCenter` | 控制 `Profile.vue` 复杂度 |
| P2 | 安全评分、本月登录、常用设备占比准确统计接口 | 可先前端轻量计算，后续后端聚合 |

## 数据库与一致性

本功能涉及数据库变更，必须新增迁移并同步数据字典。迁移目标：

| 表 | 用途 |
|------|------|
| `sys_user` | 个人资料、安全信息 |
| `refresh_token` | 会话与设备信息 |
| `operation_log` | 我的操作记录 |
| `sys_login_history` | 登录历史 |
| `sys_user_preference` | 个人偏好 |

需要确认的 ORM 一致性：

- `User.passwordUpdatedTime` 对应 `sys_user.password_updated_time`。
- `User.lastLoginTime` 对应 `sys_user.last_login_time`。
- `User.lastLoginIp` 对应 `sys_user.last_login_ip`。
- `User.loginCount` 对应 `sys_user.login_count`。
- `User.isLocked` 对应 `sys_user.is_locked`。
- `RefreshToken` 字段与 `refresh_token` 表一致。
- `OperationLog` 字段与 `operation_log` 表一致。
- `LoginHistory` 字段与 `sys_login_history` 表一致。
- `UserPreference` 字段与 `sys_user_preference` 表一致。

需要同步更新：

- `V22__personal_profile_management.sql` 或实际下一个迁移版本。
- `RefreshToken` Entity。
- 新增 `LoginHistory` Entity。
- 新增 `UserPreference` Entity。
- Repository 查询方法。
- 数据字典。
- 项目设计文档。
- API 调用手册。

## 实施步骤

### 参考图升级执行阶段

为了把参考图能力落地到当前系统，建议在原有一次完整交付步骤基础上增加 UI 架构升级阶段：

#### 阶段 A：入口与页面骨架

1. 确认 `frontend/src/components/Layout.vue` 中 PC 侧边栏头像和移动端抽屉头像均可点击进入 `/profile`。
2. 如果仍保留 `frontend/src/components/NavBar.vue`，同步补齐头像点击进入 `/profile`，避免备用布局行为不一致。
3. 在 `Profile.vue` 中建立三层页面骨架：`ProfileHeader`、`profile-primary-grid`、`profile-overview-grid`。
4. 顶部标题栏实现“用户中心”、问候语、刷新按钮、返回工作台按钮。
5. 返回工作台优先读取 `preferences.defaultHomePath`，无权限或为空回退 `/home`。

#### 阶段 B：视觉 token 与基础样式

1. 在 `Profile.vue` 或 profile 专用样式中定义页面级 CSS 变量：背景、卡片、主色、信息蓝、风险橙、危险红、边框和文本色。
2. 建立 `.profile-card` 通用样式，统一白底、8px 圆角、浅边框、轻阴影。
3. 将主按钮、Tab 选中态、头像渐变统一为青绿色主色。
4. 将设备类指标使用蓝色，风险/失败类指标使用橙色或红色，避免页面只剩单一青绿色。
5. 检查所有按钮、Tab、输入框在 1366px、1440px、移动端 375px 宽度下文字不溢出。

#### 阶段 C：内容架构升级

1. 将左侧账号摘要卡升级为参考图结构：头像、昵称、用户名、角色标签、状态、部门、员工编号、最近登录。
2. 在摘要卡底部增加三项小指标：安全评分、登录设备数、操作记录数。
3. 调整 Tab 顺序为：基本资料、账号安全、操作记录、登录历史、会话管理、个人偏好。
4. 在首屏底部新增三块概览卡：账号安全概览、最近操作、登录设备 / 会话。
5. 概览卡只展示摘要和最多 3 条列表，完整内容仍在对应 Tab 中处理。

#### 阶段 D：组件拆分

1. 新增 `frontend/src/components/profile/` 目录。
2. 拆分 `ProfileHeader.vue`、`ProfileSummaryCard.vue`、`ProfileTabs.vue`、`ProfileSecurityOverview.vue`、`ProfileRecentOperations.vue`、`ProfileSessionOverview.vue`。
3. 将基本资料、账号安全、操作记录、登录历史、会话管理、个人偏好拆成独立组件。
4. 新增 `useProfileCenter.ts` 聚合数据加载、刷新和概览计算。
5. `Profile.vue` 仅保留布局、当前 Tab、统一加载态和事件协调。

#### 阶段 E：数据与交互联动

1. 刷新按钮同时刷新 profile、security、operation logs、login history、sessions、preferences。
2. 安全评分点击后切换到“账号安全” Tab。
3. 最近操作“查看全部”点击后切换到“操作记录” Tab。
4. 登录设备“管理设备”点击后切换到“会话管理” Tab。
5. 修改资料成功后刷新 profile 并同步顶部用户昵称。
6. 修改密码、退出全部设备成功后清理 token 并跳转登录页。
7. 所有危险操作保留二次确认。

#### 阶段 F：响应式与验收

1. PC 端验证 1366x768、1440x900、1920x1080 三档视口。
2. 移动端验证 375x667、390x844、414x896 三档视口。
3. 验证 Tab 横向滚动、表单一列化、概览卡纵向堆叠。
4. 验证无文字重叠、无无意义横向滚动条、按钮文字不溢出。
5. 运行 `npm run build`，如涉及接口或 DTO 调整，同时运行 `mvn test`。

### 一次完整交付步骤

1. 创建 `V22__personal_profile_management.sql` 或实际下一个迁移版本，扩展 `refresh_token` 并新增 `sys_login_history`、`sys_user_preference`、阶段字典。
2. 更新 `RefreshToken` Entity，新增 `LoginHistory`、`UserPreference` Entity，并完成 ORM 注解与数据库字段一致性检查。
3. 新增 `LoginHistoryRepository`、`UserPreferenceRepository`，扩展 `RefreshTokenRepository` 的用户会话查询和撤销方法。
4. 扩展 `SecurityProperties`，增加密码策略配置项：最小长度、最大长度、是否要求字母、是否要求数字、是否禁止空白。
5. 新增 `ProfileService`，承接个人资料、账号安全、修改密码、我的日志、登录历史、会话管理和个人偏好逻辑。
6. 新增 `ProfileController`，提供 `/api/profile/**` 语义化接口。
7. 保留 `AuthController` 现有接口，并委托 `ProfileService`，保证 `/api/auth/profile` 和 `/api/auth/password` 兼容。
8. 修改登录流程：登录成功写入 `last_login_time`、`last_login_ip`、`login_count`、`sys_login_history`，创建 refresh token 时记录设备信息。
9. 修改登录失败流程：记录 `sys_login_history` 失败事件，失败原因只写摘要。
10. 修改刷新 token 流程：更新 `refresh_token.last_used_time`、`ip_address`、`user_agent`。
11. 实现修改密码：后端统一密码策略校验，更新 `password_updated_time`，撤销全部 refresh token，写操作日志。
12. 实现 `/api/profile/operation-logs`，按当前用户 `userId` 查询，兼容历史 `username` 日志，不接受 `userId` 参数。
13. 实现 `/api/profile/sessions`、`/sessions/{id}`、`/sessions/others`、`/sessions/all`，确保只能操作当前用户自己的会话。
14. 实现 `/api/profile/login-history`，默认查询最近 90 天，支持分页。
15. 实现 `/api/profile/preferences` 获取和更新，校验默认首页必须是当前用户可访问路由。
16. 新增 `frontend/src/api/profile.ts` 和 `frontend/src/types/profile.ts`，前端逐步从 `auth.ts` 迁移个人中心调用。
17. 重构 `Profile.vue`，拆分账号摘要、基本资料、账号安全、我的操作记录、会话管理、登录历史、个人偏好组件。
18. 按参考图新增顶部标题栏、刷新按钮、返回工作台按钮、底部三块运维概览卡。
19. 顶部用户菜单或全局头像增加“个人中心”入口，确认 PC / 移动端入口可用。
20. 实现危险操作二次确认：修改密码、撤销单会话、退出其他设备、退出全部设备。
21. 修改密码和退出全部设备成功后，前端清理本地 token 并跳转登录页。
22. 全面检查角色、状态、操作类型、操作模块、表格密度、主题模式、登录结果显示，禁止硬编码中文映射。
23. 按参考图视觉规范检查颜色、卡片、Tab、按钮、表单、概览卡和移动端布局。
24. 补充后端单元测试和集成测试。
25. 运行后端测试和前端构建。
26. 更新 README、开发指南、IDEA 部署指南、项目设计文档、API 调用手册、UI 说明、完成总结和数据字典。

## Verification

### 后端验证

- [ ] 未登录访问 `/api/profile` 返回 401。
- [ ] 登录用户只能获取自己的个人资料。
- [ ] 更新个人资料不能修改用户名、角色、部门、状态、权限。
- [ ] 更新个人资料后 `/api/auth/profile` 和 `/api/profile` 返回一致。
- [ ] 密码策略由后端统一校验，长度、字母、数字、空白字符、与旧密码相同均按规则拦截。
- [ ] 修改密码时旧密码错误返回 400。
- [ ] 新密码与确认密码不一致返回 400。
- [ ] 新密码与旧密码相同返回 400。
- [ ] 修改密码成功后 `password_updated_time` 更新。
- [ ] 修改密码成功后当前用户全部 refresh token 被撤销。
- [ ] 登录成功写入 `sys_login_history` 成功记录。
- [ ] 登录失败写入 `sys_login_history` 失败记录，失败原因不包含密码。
- [ ] refresh token 创建和刷新时写入或更新设备信息。
- [ ] 我的操作记录只返回当前用户日志，接口不接受 `userId` 参数。
- [ ] 单设备撤销不能撤销当前会话，不能撤销他人会话。
- [ ] 退出其他设备只撤销当前会话之外的 token。
- [ ] 退出全部设备撤销当前用户全部 token。
- [ ] 个人偏好默认首页必须校验当前用户路由权限。
- [ ] 个人资料更新、修改密码、撤销会话、更新偏好写入操作日志。
- [ ] `/api/auth/profile`、`/api/auth/password` 兼容旧前端调用。

### 前端验证

- [ ] `/profile` 对 ADMIN / EDITOR / VIEWER 均可访问。
- [ ] 全局头像在 PC 侧边栏、移动端抽屉和备用导航中均可进入个人中心。
- [ ] 个人中心顶部标题栏包含“用户中心”、问候语、刷新按钮和返回工作台按钮。
- [ ] 返回工作台优先跳转个人偏好 `defaultHomePath`，无权限时回退 `/home`。
- [ ] 首屏采用左侧账号摘要 + 右侧 Tab 工作区 + 底部三块运维概览卡结构。
- [ ] 账号摘要展示头像、昵称、用户名、角色标签、状态、部门、员工编号、最近登录和三项指标。
- [ ] Tab 顺序为基本资料、账号安全、操作记录、登录历史、会话管理、个人偏好。
- [ ] 当前 Tab 使用青绿色文字和底部指示线，移动端 Tab 可横向滚动且选中态可见。
- [ ] 基本资料保存后页面和顶部用户昵称同步刷新。
- [ ] 安全评分或安全概览点击后切换到账号安全 Tab。
- [ ] 最近操作“查看全部”点击后切换到操作记录 Tab。
- [ ] 登录设备“管理设备”点击后切换到会话管理 Tab。
- [ ] 修改密码表单有旧密码、新密码、确认密码校验。
- [ ] 修改密码成功后清理本地 token 并跳转登录页。
- [ ] 我的操作记录分页、筛选、空状态正常。
- [ ] 登录历史分页、筛选、空状态正常。
- [ ] 会话列表能标识当前会话。
- [ ] 撤销会话、退出其他设备、退出全部设备都有二次确认。
- [ ] 退出全部设备后清理本地 token 并跳转登录页。
- [ ] 个人偏好保存后表格密度、默认页、主题模式按预期生效。
- [ ] 移动端和 PC 端布局无文字重叠、无无意义横向滚动条。
- [ ] 状态、角色、操作类型、表格密度、主题模式、登录结果显示不使用硬编码中文映射。

### 视觉与架构验收

- [ ] 页面背景为浅灰，卡片为白底浅边框，不使用大面积渐变背景或装饰光斑。
- [ ] 主按钮、头像渐变、Tab 选中态统一使用青绿色主色。
- [ ] 设备类指标使用蓝色，风险/失败类信息使用橙色或红色，页面不是单一绿色主题。
- [ ] 卡片圆角不超过 8px，阴影轻量，符合后台系统气质。
- [ ] PC 端 1366x768 下首屏能同时看到账号摘要、资料工作区和至少一部分运维概览区。
- [ ] PC 端 1440x900 或以上能完整看到三块运维概览卡。
- [ ] 移动端 375px 宽度下表单自动单列，按钮文字不溢出。
- [ ] 概览卡最多展示 3 条摘要数据，超出通过“查看全部 / 管理设备”进入对应 Tab。
- [ ] 刷新按钮有加载态，刷新期间禁止重复点击。
- [ ] 空数据状态明确，不显示伪造统计。
- [ ] `Profile.vue` 不再承载全部业务和模板，核心模块已拆分到 `components/profile/`。
- [ ] 数据加载与刷新逻辑集中到 `useProfileCenter.ts` 或同等组合式函数。

建议验收截图：

```text
docs/UI/profile-upgrade-desktop-1440.png
docs/UI/profile-upgrade-mobile-390.png
```

### 构建与测试

```bash
cd backend
mvn test
```

```bash
cd frontend
npm run build
```

建议补充测试：

- `ProfileService` 单元测试。
- `ProfileController` 权限隔离测试。
- 修改密码撤销 token 测试。
- 我的操作记录只能查当前用户的集成测试。
- 会话撤销不能操作他人 token 的集成测试。
- 登录历史成功/失败记录测试。
- 个人偏好默认首页权限校验测试。

## 风险与控制

| 风险 | 控制措施 |
|------|----------|
| 个人中心和用户管理职责混淆 | 个人中心只允许修改 `nickname/email/phone/password`，管理字段只读 |
| 修改密码后旧 token 仍可用 | 修改密码后撤销 refresh token，并明确是否要求重新登录 |
| 普通用户越权查看他人日志 | `/api/profile/operation-logs` 从认证上下文取当前用户，不接受 `userId` 参数 |
| 普通用户撤销他人会话 | 所有会话操作都按当前 `userId` 限定，撤销前校验 token 归属 |
| 操作日志泄露敏感信息 | 密码字段不入日志，资料变更只记录脱敏摘要 |
| 旧接口和新接口数据不一致 | `/api/auth/profile` 委托 `ProfileService`，避免双份逻辑 |
| 字典硬编码回归 | 前端统一使用字典服务或角色接口展示编码名称 |
| 会话管理误导用户 | 扩展 refresh token 设备字段并标识当前会话；当前会话不允许单独撤销 |
| 登录历史表增长过快 | 默认保留 180 天，可后续接入归档清理任务 |
| 默认首页越权 | 保存偏好时校验路由权限，权限变化后回退 `/home` |

## 通过标准

- 普通用户不需要管理员协助即可修改个人资料和密码。
- 用户可以通过全局头像直接进入个人中心。
- 用户进入个人中心首屏即可看到身份摘要、资料工作区、安全概览、最近操作和登录设备摘要。
- 用户能在个人中心查看自己的账号安全状态和最近操作。
- 用户能查看登录历史、当前会话和其他设备，并能撤销自己的会话。
- 用户能维护个人偏好，且默认首页不会越权。
- 修改密码、退出设备等高风险动作有清晰提示、二次确认和操作日志。
- 修改密码后旧 refresh token 全部失效，用户必须重新登录。
- 管理员用户管理页面仍负责角色、部门、状态、重置密码等管理能力。
- `/api/auth/profile` 兼容现有前端，新增 `/api/profile/**` 为后续个人中心演进提供清晰边界。
- 页面视觉达到参考图方向：轻量后台、青绿色主色、白底浅边框、三块运维概览、PC/移动端均无布局破损。
- 个人中心复杂度通过组件拆分控制，后续可继续扩展头像上传、邮箱验证、双因素认证等能力。
- 后端测试和前端构建通过，个人中心权限隔离和 token 撤销有测试覆盖。

---

*方案评分目标：9.7+*
*执行方式：一次完整交付 + 参考图视觉架构升级*
*最后更新：2026-06-02*
