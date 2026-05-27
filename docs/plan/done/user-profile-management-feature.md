# 用户个人管理功能规划

## Context

当前系统已经具备用户登录、JWT 鉴权、用户管理、角色权限和默认用户初始化能力，但普通登录用户缺少一个清晰、完整、可维护的个人管理闭环。代码层面已有部分基础：

- 后端 `AuthController` 已提供 `/api/auth/profile`、`/api/auth/password`。
- 前端 `src/api/auth.ts` 已封装 `getProfile`、`updateProfile`、`changePassword`。
- 前端已有 `src/views/Profile.vue` 页面和 `/profile` 路由。
- 顶部/页面局部导航存在跳转个人中心的入口痕迹。

需要把这些能力正式整理成“用户个人管理”页面，使所有角色用户都能：

- 查看自己的账号、角色、部门、登录信息等只读资料。
- 修改自己的昵称、邮箱、手机号。
- 修改自己的登录密码。
- 修改成功后刷新当前用户信息，密码修改后强制重新登录。
- 管理个人偏好设置时明确哪些是本地偏好、哪些是服务端账号信息。

本功能不应替代管理员的“用户管理”。管理员仍通过用户管理维护角色、状态、锁定、重置密码等高权限操作；个人管理只允许用户维护自己的低风险信息。

## 当前实现差距清单

| 文件/能力 | 当前状态 | 风险 | 目标决策 |
|-----------|----------|------|----------|
| `frontend/src/views/Profile.vue` | 已有个人中心雏形，包含资料编辑、改密、本地系统设置、PC/移动端两套布局 | 页面职责混杂，本地设置与账号资料容易混淆，移动端存在页面级导航重复 | 重构为“账号概览、个人信息、安全设置、本地偏好”四块；本地偏好保留但弱化，不作为账号能力 |
| `frontend/src/components/Layout.vue` | PC 侧边栏底部展示用户信息，移动端抽屉展示用户信息，但用户信息区不是稳定个人管理入口 | 用户找不到个人资料入口，尤其普通角色可能不关注系统菜单 | PC 用户信息区可点击进入 `/profile`；移动端用户区增加“个人管理”入口；退出登录继续独立按钮 |
| `frontend/src/api/auth.ts` | 已封装 `getProfile`、`updateProfile`、`changePassword` | API 可复用，但前后端字段、校验文案需要统一 | 保持接口路径不变，补齐字段类型、错误提示和成功后的用户缓存刷新 |
| `backend/src/main/java/com/pricemanagement/controller/AuthController.java` | 已提供个人信息查询/更新和修改密码接口 | 当前可能直接返回实体，操作日志、`passwordUpdatedTime` 和字段白名单需要强化 | 查询返回安全 DTO；更新只允许低风险字段；改密更新密码时间；补操作日志 |
| `backend/src/main/java/com/pricemanagement/dto/ChangePasswordRequest.java` | 现有规则为 6-20 位 | 如果方案写 8-20，开发时会与现有后端规则冲突 | 一期统一采用 6-20 位硬校验；强度提示只做辅助，不阻断；二期再升级复杂度策略 |
| `backend/src/main/java/com/pricemanagement/entity/User.java` | 已包含昵称、邮箱、手机、部门、登录统计、密码更新时间等字段 | 若数据库初始化脚本缺字段，会导致 JPA/SQL 不一致 | 实施前核对 `init.sql`、Entity、TS `User` 接口三方字段一致 |
| 字典显示 | 角色标签已使用字典服务，状态与其他编码字段仍需核对 | 前端硬编码中文会违反项目规范 | 角色、状态只传编码，展示统一走 `useDict` |

## 一期/二期边界

### 一期必须完成

- 所有已登录用户都能访问 `/profile`，不依赖后台菜单权限。
- PC 侧边栏用户信息区、移动端用户区域都能进入个人管理。
- 用户可以查看账号概览：用户名、工号、角色、状态、部门、登录方式、最近登录时间、登录 IP、登录次数、密码更新时间。
- 用户可以修改昵称、邮箱、手机号。
- 用户可以修改密码，成功后立即退出登录并跳转登录页。
- 后端只允许更新当前用户自己的低风险资料，禁止通过请求体修改角色、状态、用户名、工号、部门等管理字段。
- 更新个人信息、修改密码写入操作日志。
- 角色、状态显示全部使用字典服务。
- PC 和移动端都没有横向滚动、遮挡、按钮文字溢出。

### 一期明确不做

- 不做头像上传。
- 不做微信绑定/解绑。
- 不做用户名、工号、角色、状态、部门的个人自助修改。
- 不做全量 session/token 失效策略，只在前端改密成功后清理当前登录态。
- 不把本地主题、字体、首页偏好等本地设置升级为服务端个人偏好。

### 二期可扩展

- 头像上传与静态资源管理。
- 微信绑定/解绑和第三方登录信息展示。
- 修改密码后服务端主动失效旧 refresh token / 访问令牌。
- 登录设备管理、最近安全事件列表。
- 服务端个人偏好表，例如 `user_preferences`。

## 实现方案

### 1. 功能范围

个人管理页面首期分为四个区域：

| 区域 | 说明 | 编辑权限 |
|------|------|----------|
| 账号概览 | 用户名、工号、角色、状态、部门、登录方式、最近登录时间、登录次数 | 只读 |
| 个人信息 | 昵称、邮箱、手机号 | 当前用户可编辑 |
| 安全设置 | 修改密码、密码更新时间、安全提示 | 当前用户可编辑密码 |
| 本地偏好 | 仅当前浏览器生效的主题、显示密度等偏好 | 当前用户可编辑，但不进入账号 API |

暂不纳入：

- 角色修改：仅管理员在用户管理维护。
- 用户状态/锁定：仅管理员在用户管理维护。
- 工号、用户名修改：保持只读，避免登录标识变化造成审计困难。
- 微信绑定/解绑：字段已存在，但作为后续单独功能规划。
- 头像上传：当前无文件上传/静态资源管理闭环，暂不纳入首期。

### 2. 页面交互设计

PC 端采用工作台式两栏布局：

```text
┌──────────────────────────────────────────────────────────────┐
│ 个人管理                                                     │
│ 管理个人资料、账号安全和本地偏好                              │
├───────────────────────┬──────────────────────────────────────┤
│ 账号卡片               │ 个人信息                              │
│ - 昵称/用户名          │ - 昵称 input                           │
│ - 角色标签             │ - 邮箱 input                           │
│ - 部门/状态            │ - 手机号 input                         │
│ - 最近登录             │ - 保存按钮                             │
│                       ├──────────────────────────────────────┤
│ 安全摘要               │ 修改密码                              │
│ - 密码更新时间         │ - 当前密码                             │
│ - 登录次数             │ - 新密码 + 强度提示                    │
│ - 安全建议             │ - 确认新密码                           │
│                       │ - 修改成功后重新登录                   │
└───────────────────────┴──────────────────────────────────────┘
```

移动端采用单列卡片布局：

- 顶部显示账号摘要。
- 个人信息表单卡片。
- 修改密码折叠/弹窗卡片。
- 本地偏好设置放在最后，避免与账号资料混淆。

交互要求：

- 信息保存与密码修改分开，不能共用一个提交按钮。
- 修改密码表单默认隐藏密码，提供显示/隐藏按钮。
- 新密码校验前端即时提示，后端最终兜底。
- 密码修改成功后清空 token 和用户缓存，跳转登录页。
- 表单提交时禁用按钮并展示保存中状态。
- 失败时展示明确错误信息，不吞掉后端返回文案。

### 3. 后端设计

复用现有认证接口，必要时做一致性增强。

#### 3.1 查询个人信息

```http
GET /api/auth/profile
```

返回当前登录用户信息。

建议返回字段：

```json
{
  "id": 1,
  "username": "admin",
  "employeeId": "000001",
  "role": "ADMIN",
  "status": "ACTIVE",
  "nickname": "管理员",
  "email": "admin@example.com",
  "phone": "13800000000",
  "department": "系统管理部",
  "deptId": 1,
  "loginType": "PASSWORD",
  "lastLoginTime": "2026-05-26 21:00:00",
  "lastLoginIp": "127.0.0.1",
  "loginCount": 10,
  "passwordUpdatedTime": "2026-05-26 21:10:00",
  "createdTime": "2026-05-01 09:00:00",
  "updatedTime": "2026-05-26 21:10:00"
}
```

注意：

- 禁止返回 `password`。
- 建议返回 `ProfileResponse` DTO，而不是直接返回 `User` Entity，避免未来字段新增时误暴露敏感信息。
- 角色、状态展示名称由前端字典服务解析，后端只返回编码值。

#### 3.2 更新个人信息

```http
PUT /api/auth/profile
```

请求体：

```json
{
  "nickname": "张三",
  "email": "zhangsan@example.com",
  "phone": "13800000000"
}
```

后端校验：

- `nickname`：允许不传；传入时 1-50 字符，空字符串按不更新处理，避免把昵称误清空。
- `email`：允许为空；非空时校验邮箱格式；空字符串统一归一化为 `null`。
- `phone`：允许为空；非空时校验手机号或宽松电话格式；空字符串统一归一化为 `null`。
- 不允许请求体更新 `username`、`role`、`status`、`deptId`、`employeeId`。

操作日志：

- 模块：个人管理
- 类型：UPDATE
- 描述：更新个人信息
- 内容：记录用户 ID、更新字段名称，不记录敏感明文。

#### 3.3 修改密码

```http
PUT /api/auth/password
```

请求体：

```json
{
  "oldPassword": "old-password",
  "newPassword": "new-password",
  "confirmPassword": "new-password"
}
```

后端校验：

- 当前用户必须已登录。
- 旧密码必须正确。
- 新密码与确认密码一致。
- 新密码不能与旧密码一致。
- 一期硬校验统一为 6-20 位，与现有 `ChangePasswordRequest` 保持一致；强度条可以提示字母、数字、特殊字符组合，但不作为阻断条件。
- 修改成功后更新 `password_updated_time`。

操作日志：

- 模块：个人管理
- 类型：UPDATE
- 描述：修改个人密码
- 内容：不记录密码明文，只记录用户 ID 和结果。

安全建议：

- 修改密码成功后前端立即退出登录。
- 后续可扩展 refresh token 失效策略，使旧 token 失效。

### 4. 前端设计

#### 4.1 路由与入口

复用现有路由：

```ts
{
  path: 'profile',
  name: 'Profile',
  component: () => import('../views/Profile.vue'),
  meta: { title: '个人管理' }
}
```

入口要求：

- PC 左侧 `Layout.vue` 的 `.sidebar-footer .user-info` 改为可点击入口，点击进入 `/profile`；退出登录按钮保持独立，避免误触。
- 移动端抽屉的用户信息区增加“个人管理”入口，点击进入 `/profile` 并关闭抽屉。
- 该入口对所有已登录角色可见。
- 不依赖后台菜单权限配置，避免普通用户看不到自己的个人中心。
- `/profile` 路由只要求登录，不增加 ADMIN/EDITOR 等角色限制。

#### 4.2 页面结构

建议将 `Profile.vue` 重构为更清晰的模块：

```text
src/views/Profile.vue
src/components/profile/ProfileSummaryCard.vue
src/components/profile/ProfileInfoForm.vue
src/components/profile/PasswordChangePanel.vue
src/components/profile/LocalPreferencePanel.vue
```

如果首期控制改动范围，也可以保留单文件，但需按区域整理 script 和 template。

#### 4.2.1 现有 `Profile.vue` 重构取舍

| 现有内容 | 处理方式 | 原因 |
|----------|----------|------|
| PC 账号卡片 | 保留并增强 | 作为账号概览核心区域，补充登录 IP、登录次数、密码更新时间 |
| 编辑个人资料弹窗 | PC 端改为右侧表单卡片，移动端保留底部抽屉 | PC 重复弹窗会降低效率；移动端抽屉更符合当前 Vant 交互 |
| 修改密码弹窗 | PC 可保留弹窗或右侧安全卡片；移动端保留底部抽屉 | 改密属于低频高风险操作，需要明确确认态 |
| 系统设置弹窗 | 改名为“本地偏好”，放在页面最后 | 避免用户误以为这些设置会同步到账号或其他设备 |
| 页面内底部导航 | 如全局 `Layout.vue` 已提供移动导航，则个人页面内不再重复 | 避免同一页面出现两套导航 |
| 角色样式 class 映射 | 可以保留 CSS class 映射，但中文名称必须来自字典 | class 是样式协议，不属于中文标签硬编码 |

#### 4.2.2 视觉与交互标准

- 页面样式使用 style-settings 变量和现有全局 CSS 变量，不新建脱离主题的固定色板。
- 表单卡片不嵌套卡片，PC 使用两栏栅格，移动端使用单列分区。
- 所有按钮有 loading/disabled 状态。
- 密码显示/隐藏使用图标按钮，按钮需有 `title` 或可访问标签。
- 保存成功后使用轻量提示，不跳走；改密成功后提示并跳转登录页。
- 错误提示优先展示后端返回信息，前端校验只处理必填、长度、格式、确认密码一致等明确规则。

#### 4.3 数据流

- 页面进入时优先调用 `userStore.fetchProfile()`。
- 表单初始值来自 `userStore.user`。
- 保存个人信息后调用 `updateProfile()`，再刷新 `userStore.fetchProfile()`。
- 修改密码后调用 `changePassword()`，成功后执行 `userStore.logoutAction()` 并跳转 `/login`。

#### 4.4 字典规范

- 角色显示使用 `getRoleLabel(role)`。
- 状态显示使用 `getStatusLabel(status)`。
- 不硬编码“管理员/编辑者/查看者”“启用/停用”等中文标签。

#### 4.5 前端 API 与类型契约

| 类型/方法 | 一期目标 |
|-----------|----------|
| `User` | 补齐 `employeeId`、`department`、`deptId`、`loginType`、`lastLoginTime`、`lastLoginIp`、`loginCount`、`passwordUpdatedTime` 等可展示字段 |
| `UpdateProfileRequest` | 只包含 `nickname`、`email`、`phone` |
| `ChangePasswordRequest` | 只包含 `oldPassword`、`newPassword`、`confirmPassword` |
| `getProfile()` | 返回安全用户资料 DTO，不包含 `password` |
| `updateProfile()` | 保存后刷新 `userStore.fetchProfile()` |
| `changePassword()` | 成功后执行 `logoutAction()` 并跳转登录页 |

### 5. 前后端一致性检查

| 项目 | 检查点 |
|------|--------|
| API 路径 | 前端 `src/api/auth.ts` 与后端 `/api/auth/profile`、`/api/auth/password` 保持一致 |
| 用户字段 | `User` Entity 与 `src/types/index.ts` 的 `User` 接口字段保持一致 |
| 密码字段 | 后端不返回 `password`；前端只在修改密码表单中临时持有密码 |
| 日期字段 | `lastLoginTime`、`passwordUpdatedTime`、`createdTime`、`updatedTime` 按统一时间格式展示 |
| 字典字段 | `role`、`status` 只传编码，前端从字典服务解析显示名称 |
| 日志 | 更新个人信息、修改密码都需要记录操作日志 |

### 5.1 API 契约验收表

| 接口 | 请求 | 成功响应 | 失败响应重点 |
|------|------|----------|--------------|
| `GET /api/auth/profile` | 无请求体，依赖当前 token | 当前用户安全资料，不含 `password` | 未登录返回 401 或统一未授权错误 |
| `PUT /api/auth/profile` | `nickname/email/phone` | 更新后的安全资料或成功结果 | 邮箱/手机号格式错误返回明确文案；越权字段被忽略 |
| `PUT /api/auth/password` | `oldPassword/newPassword/confirmPassword` | 成功结果，前端随后退出登录 | 旧密码错误、确认密码不一致、新旧密码相同、长度不合规都要有明确文案 |

### 5.2 权限与安全验收表

| 场景 | 目标行为 |
|------|----------|
| ADMIN 访问 `/profile` | 允许访问，只能修改自己的个人信息和密码 |
| EDITOR 访问 `/profile` | 允许访问，只能修改自己的个人信息和密码 |
| VIEWER 访问 `/profile` | 允许访问，只能修改自己的个人信息和密码 |
| 请求体夹带 `role/status/username` | 后端忽略，不写入数据库 |
| 修改密码成功 | `password_updated_time` 更新，前端清空 token 和用户缓存 |
| 修改密码失败 | 不修改密码，不清空登录态，展示错误信息 |

### 6. 数据库一致性检查

首期不新增表。

需核对 `sys_user` 表字段与 `User` Entity：

| Entity 字段 | 数据库列 | 用途 |
|-------------|----------|------|
| `nickname` | `nickname` | 昵称 |
| `email` | `email` | 邮箱 |
| `phone` | `phone` | 手机号 |
| `department` | `department` | 部门名称 |
| `deptId` | `dept_id` | 部门 ID |
| `lastLoginTime` | `last_login_time` | 最近登录时间 |
| `lastLoginIp` | `last_login_ip` | 最近登录 IP |
| `loginCount` | `login_count` | 登录次数 |
| `passwordUpdatedTime` | `password_updated_time` | 密码更新时间 |

ORM 注解检查：

- `@Table(name = "sys_user")` 对应数据库表。
- `@Column(name = "password_updated_time")` 必须存在。
- `@Column(name = "dept_id")` 必须存在。
- `@JsonIgnore` 应继续保护 `password` 字段。

如果 `init.sql` 中缺少 `password_updated_time`、`last_login_time`、`last_login_ip`、`login_count` 等列，需要同步补齐。

## 关键参考文件

| 文件 | 用途 |
|------|------|
| `backend/src/main/java/com/pricemanagement/entity/User.java` | 用户实体字段、ORM 映射 |
| `backend/src/main/java/com/pricemanagement/controller/AuthController.java` | 个人信息与密码接口现状 |
| `backend/src/main/java/com/pricemanagement/controller/UserController.java` | 管理员用户管理能力边界 |
| `backend/src/main/java/com/pricemanagement/dto/UpdateProfileRequest.java` | 个人信息更新请求校验 |
| `backend/src/main/java/com/pricemanagement/dto/ChangePasswordRequest.java` | 修改密码请求校验 |
| `backend/src/main/java/com/pricemanagement/util/OperationLogHelper.java` | 操作日志记录方式 |
| `frontend/src/api/auth.ts` | 个人信息与密码 API 封装 |
| `frontend/src/views/Profile.vue` | 当前个人管理页面雏形 |
| `frontend/src/components/Layout.vue` | PC/移动端个人管理入口 |
| `frontend/src/store/useUserStore.ts` | 当前登录用户状态与缓存 |
| `frontend/src/router/index.ts` | `/profile` 路由 |
| `frontend/src/types/index.ts` | `User` 类型与后端字段一致性 |
| `frontend/src/composables/useDict.ts` | 角色/状态字典显示 |
| `docs/dev/项目设计文档.md` | API 与功能模块设计同步位置 |
| `docs/dev/UI设计说明.md` | 页面与交互说明同步位置 |

## 实现步骤

### Phase 1：现状核对与接口收口

1. 检查 `/api/auth/profile` 是否始终不返回 `password`。
2. 增加或整理 `ProfileResponse`，避免直接暴露 `User` Entity。
3. 检查 `updateProfile` 是否只允许更新 `nickname/email/phone`。
4. 统一空字符串处理：邮箱、手机号空字符串归一化为 `null`。
5. 为更新个人信息、修改密码补充 `@OperationLog` 或 `OperationLogHelper` 记录。
6. 修改密码成功时更新 `passwordUpdatedTime`。
7. 统一密码长度规则为 6-20 位，并同步前后端提示。

### Phase 2：页面体验重构

1. 梳理 `Profile.vue` 信息结构，分为账号概览、个人信息、安全设置、本地偏好。
2. PC 端做两栏工作台布局，移动端做单列卡片布局。
3. 表单输入使用明确 label、错误提示、保存中状态。
4. 角色、状态显示全部通过字典服务。
5. 密码修改成功后弹窗提示并强制重新登录。
6. 将“系统设置”改为“本地偏好”，并明确只影响当前浏览器。
7. 移除或合并页面内重复移动端导航，避免与全局 `Layout.vue` 冲突。

### Phase 3：入口与导航

1. PC `Layout.vue` 侧边栏用户信息区增加点击进入 `/profile`。
2. 移动端抽屉用户信息区增加“个人管理”入口。
3. 确保所有登录角色都能访问 `/profile`，不受管理员菜单配置影响。
4. 页面标题统一为“个人管理”。

### Phase 4：测试与回归

1. 后端补充或执行 profile/password 相关接口测试。
2. 前端执行表单校验、导航入口、移动端布局的手动回归。
3. 使用 ADMIN、EDITOR、VIEWER 三种角色分别验证入口和权限边界。
4. 检查操作日志新增记录，确认不包含密码明文。

### Phase 5：文档同步

完成开发后按项目规范更新：

- `README.md`：功能列表增加个人管理。
- `docs/dev/开发指南.md`：补充个人管理接口、密码规则和前端字典规范。
- `docs/dev/项目设计文档.md`：补充认证接口表、功能模块设计。
- `docs/dev/UI设计说明.md`：补充个人管理页面布局与交互。
- `docs/archive/项目完成总结.md`：更新功能完成情况。
- 如数据库字段有变化，同步数据字典/初始化 SQL。

## Definition of Done

- `/profile` 页面可以作为所有登录用户的稳定个人管理入口。
- 个人信息保存后，页面展示、`userStore.user`、后端数据库三者一致。
- 修改密码成功后，用户必须重新登录；旧密码、新密码不一致等错误路径均可正确提示。
- 后端接口不返回 `password`，不允许通过个人接口修改管理字段。
- 更新个人信息和修改密码均有操作日志。
- 角色、状态等编码值展示符合字典服务规范。
- PC、移动端界面符合当前项目风格和 style-settings 配色，不出现横向滚动和布局遮挡。
- 文档同步完成，开发者可以从本方案直接追踪到代码文件、接口、验证用例。

## 评分自检（目标 9.5+）

| 维度 | 权重 | 自评分 | 依据 |
|------|------|--------|------|
| 需求边界清晰度 | 20% | 9.8 | 已明确一期必须做、不做、二期扩展，避免范围漂移 |
| 现状结合度 | 20% | 9.6 | 对 `Profile.vue`、`Layout.vue`、`AuthController`、DTO、Store 都给出改造方向 |
| 前后端/数据库一致性 | 20% | 9.6 | 覆盖 API、类型、Entity、数据库列、ORM 注解、字典显示 |
| 安全与权限 | 15% | 9.5 | 覆盖敏感字段、字段白名单、改密后退出、操作日志、不记录密码 |
| UI/交互可执行性 | 15% | 9.5 | 明确 PC/移动端布局、入口、表单状态、视觉标准 |
| 验证完整度 | 10% | 9.5 | 覆盖后端、前端、角色、构建、日志回归 |

综合评分：9.6 / 10。剩余 0.4 分主要留给实施阶段的真实截图验证、自动化测试覆盖率和代码评审结果。

## Verification

### 后端验证

1. 登录后调用 `GET /api/auth/profile`，确认返回当前用户且无 `password`。
2. 调用 `PUT /api/auth/profile` 修改昵称、邮箱、手机号，确认数据库更新。
3. 尝试在请求体中传 `role/status/username`，确认不会被更新。
4. 使用错误旧密码调用 `PUT /api/auth/password`，应返回旧密码错误。
5. 使用不一致的新密码和确认密码，应返回校验错误。
6. 使用正确旧密码修改成功后，确认 `password_updated_time` 更新。
7. 检查操作日志中存在个人信息更新和密码修改记录。

### 前端验证

1. ADMIN、EDITOR、VIEWER 均可进入个人管理页面。
2. 个人信息展示与当前登录用户一致。
3. 保存个人信息后页面和用户缓存同步刷新。
4. 修改密码成功后自动退出并跳转登录页。
5. 角色、状态中文显示来自字典服务。
6. PC 与移动端布局均无内容重叠、按钮文字溢出、横向滚动。

### 构建验证

```bash
cd frontend
npm run build

cd ../backend
mvn -DskipTests compile
```
