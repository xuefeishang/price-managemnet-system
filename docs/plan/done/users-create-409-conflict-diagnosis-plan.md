# Users 页面新增用户 409 冲突诊断与修复方案

## Context

用户在 `users` 页面保存新用户时，浏览器控制台出现：

```text
POST http://localhost:5173/api/users 409 (Conflict)
Response error: AxiosError: Request failed with status code 409
```

目标不是只隐藏 409，而是基于当前项目实现完成以下闭环：

1. 先确认触发冲突的真实数据库约束。
2. 修复创建流程中的输入边界、事务错误语义和操作日志。
3. 让前端只展示一次可操作的冲突原因。
4. 保持 Controller、DTO、TypeScript 类型、Entity、Flyway、`init.sql` 和数据字典一致。
5. 通过项目质量门禁和用户管理关键回归。

当前工作区已经存在一组尚未验证的候选修改。实施前必须逐项审查这些改动，不得直接视为最终实现。

## 范围与非目标

### 本次范围

- `POST /api/users` 新增用户流程。
- 用户创建请求 DTO 与前端创建请求类型。
- `sys_user`、初始 `sys_user_role` 写入时的冲突处理。
- 新增用户成功与失败操作日志。
- 前端 HTTP 409 消息展示与重复 Toast。
- 对应测试、正式文档和必要的数据结构一致性修复。

### 非目标

- 不重构整个用户管理模块的查询和更新接口。
- 不改变现有 `/api/users` 路径或成功响应结构。
- 不改变用户角色字典、角色分配业务或权限模型。
- 不因猜测而新增或删除数据库索引。
- 不在数据库 flush 失败后的同一事务内重试写入。

## 当前实现基线

### 请求链路

```text
UserManagement.vue
  -> frontend/src/api/users.ts createUser()
  -> POST /api/users
  -> UserController.createUser()
  -> UserService.createUser()
  -> sys_user
  -> sys_user_role
```

### 已确认事实

1. 前端和后端路径一致，均为 `POST /api/users`。
2. 浏览器收到的真实 HTTP 409 来自 `GlobalExceptionHandler` 对
   `DataIntegrityViolationException` 的处理。
3. 现有用户名和手工工号预检查抛出 `IllegalArgumentException`，由
   `UserController` 捕获后包装为业务响应，不会产生浏览器看到的真实 HTTP 409。
4. 创建事务写入 `sys_user` 和 `sys_user_role`；现有 `save()` 可能将约束异常延迟到
   Hibernate flush 或事务提交阶段。
5. 仓库声明的相关唯一约束为：

| 表 | 唯一约束 |
|---|---|
| `sys_user` | `username` |
| `sys_user` | `employee_id` |
| `sys_user` | `wechat_openid` |
| `sys_user_role` | `(user_id, role_id)`，约束名 `uk_user_role` |

6. `phone` 和 `email` 在 Entity、`init.sql`、Flyway 基线和数据字典中均未声明唯一约束。
7. 前端 HTTP 拦截器会将未单独处理的 409 显示为“网络错误”，页面层还可能再次显示
   Axios 通用错误。
8. 当前创建接口直接接收 `User` Entity，允许请求携带创建流程不应写入的字段。
9. 当前创建失败操作日志只覆盖 `IllegalArgumentException`；若冲突异常交给全局处理，
   必须确保失败日志仍然落库。

### 尚未确认的真实冲突键

仅凭浏览器 409 不能确认具体冲突键。可能来源包括：

1. 自动生成的 `employee_id` 在预检查后发生极低概率并发碰撞。
2. 用户名在预检查与最终写入之间被其他请求并发创建。
3. 实际数据库存在仓库未记录的历史唯一索引。
4. `sys_user_role` 初始角色关联发生组合唯一约束冲突。
5. 请求意外传入重复或空字符串形式的 `wechat_openid`。

空手机号保存为 `""` 是数据规范问题，但按仓库结构不能直接认定为本次 409 根因。

## 诊断门槛

未完成以下三项检查前，不进入定向数据库修复。

### 1. 获取失败请求对应的后端异常

在本地复现一次失败请求，记录：

- 请求时间与测试用户名。
- `Data integrity violation` 日志对应的约束名。
- 异常发生在 `sys_user` 还是 `sys_user_role` flush。

数据库原始异常仅用于本地诊断，不写入前端响应或普通操作日志。

### 2. 只读核验实际数据库结构

```sql
SHOW INDEX FROM sys_user;
SHOW INDEX FROM sys_user_role;
SHOW CREATE TABLE sys_user;
SHOW CREATE TABLE sys_user_role;
```

比对：

- `backend/src/main/java/com/pricemanagement/entity/User.java`
- `backend/src/main/java/com/pricemanagement/entity/UserRole.java`
- `backend/src/main/resources/db/migration/V3__user_role_management_enhancement.sql`
- `backend/src/main/resources/init.sql`
- `backend/src/main/resources/数据字典.md`

检查结果必须记录为：

| 检查项 | 结果 |
|---|---|
| 原始失败请求的真实冲突约束 | 缺少当时后端异常日志，无法追溯到单一约束 |
| 实际数据库是否存在额外唯一索引 | 否 |
| Entity、Flyway、init.sql、数据字典是否一致 | 是 |

### 3. 核验失败请求体

在浏览器 Network 面板确认失败请求中的：

- `username`
- `employeeId`
- `role`
- 所有空字符串字段
- 是否存在前端类型未声明但 Entity 接收的额外字段

不得记录或传播明文密码。

## 技术决策

### 决策 1：使用专用创建 DTO

新增 `UserCreateRequest`，Controller 改为：

```java
public Result<User> createUser(@Valid @RequestBody UserCreateRequest request)
```

允许字段仅包含：

| 字段 | 校验 |
|---|---|
| `username` | 必填，去除首尾空白，长度不超过 Entity 上限 |
| `password` | 必填，使用项目 `security.password-policy` 校验 |
| `employeeId` | 可选；非空时必须为 6 位数字 |
| `role` | 必填，只允许 `User.Role` |
| `nickname` | 可选，长度不超过 Entity 上限 |
| `email` | 可选，合法邮箱且长度不超过 Entity 上限 |
| `phone` | 可选，净化后长度不超过 Entity 上限 |
| `department` | 可选，长度不超过 Entity 上限 |
| `deptId` | 可选 |

不得允许前端在创建请求中写入：

- `id`
- `status`
- `isLocked`
- `loginType`
- `wechatOpenid`
- 登录次数、登录时间等审计字段

前端现有 `CreateUserRequest` 与 Java DTO 字段和可选性必须保持一致。成功响应暂时继续返回
现有 `User` 结构并清空密码，以控制本次修复范围。

### 决策 2：规范化由 Service 统一执行

DTO 负责格式校验，Service 负责最终规范化：

- 必填文本先 `trim`，空值拒绝。
- 可选文本先 `trim`，空字符串转为 `NULL`。
- 手机号净化后为空则保存 `NULL`。
- `employee_id` 未提供时继续复用现有 `EmployeeIdService`。
- 密码编码前必须通过当前项目密码策略。

当前密码策略校验实现在 `ProfileService` 私有方法中。实施时抽取共享
`PasswordPolicyValidator`，由个人中心改密、管理员创建用户和管理员重置密码共同复用，
避免出现多套密码规则。

### 决策 3：主动 flush，但不在失败事务中重试

创建流程保持一个事务：

1. 显式预检查用户名和手工工号，提供快速、明确反馈。
2. `sys_user` 使用 `saveAndFlush()`，尽早暴露用户唯一约束冲突。
3. `sys_user_role` 使用 `saveAndFlush()`，尽早暴露角色关联冲突。
4. 任一写入失败，整个创建事务回滚。

自动工号发生数据库级并发碰撞时，本次请求返回安全的 409，用户可重新提交。禁止捕获 flush
异常后在同一事务内继续重试，因为事务通常已被标记为 rollback-only。

如未来需要自动重试，应单独设计使用新事务的重试执行器，不纳入本次修复。

### 决策 4：使用安全、结构化的冲突异常

使用用户创建专用冲突异常，内部携带稳定原因类型，例如：

```text
USERNAME_EXISTS
EMPLOYEE_ID_EXISTS
WECHAT_ALREADY_BOUND
USER_ROLE_EXISTS
UNKNOWN_USER_CONFLICT
```

处理顺序：

1. 显式预检查直接产生明确冲突类型。
2. 并发导致的数据库异常优先读取 Hibernate 暴露的约束名。
3. 无法识别约束时返回通用安全消息。
4. 禁止将 SQL、`Duplicate entry` 原文或数据库结构返回前端。
5. 安全冲突消息不得拼接用户名、工号、手机号、邮箱或微信标识原值。

统一由 `GlobalExceptionHandler` 返回：

```json
{
  "code": 409,
  "message": "用户名已存在，请更换后重试",
  "data": null
}
```

`UserController.createUser()` 不再将参数异常捕获后包装为 HTTP 200 的 `Result.error(400, ...)`。
DTO 校验和业务参数异常统一交给全局异常处理器返回真实 HTTP 400；用户冲突返回真实 HTTP 409。

### 决策 5：保留并修正操作日志

新增用户属于数据变更，成功和失败都必须记录。

- 保留当前 `OperationLogHelper` 模式，避免扩大到全模块日志重构。
- `UserController.createUser()` 捕获用户冲突异常，使用安全消息记录失败日志后重新抛出，
  交给全局异常处理器返回 HTTP 409。
- 操作日志使用 `OperationLogService` 现有 `REQUIRES_NEW`，确保主事务回滚时失败日志仍可保存。
- 请求参数仅记录用户名或安全摘要，不记录密码、完整手机号、邮箱、微信标识或数据库异常原文。
- 为 `OperationLogHelper` 增加可传入响应码的失败日志重载，使本次冲突日志记录为 `409`，
  而不是当前固定的 `500`。

### 决策 6：前端只展示一次 409 消息

`frontend/src/utils/http.ts`：

- HTTP 409 使用后端 `response.data.message`。
- 409 被包装为带安全消息的 Error 后继续 reject。
- 保持 400、401、403、404、429、500 和无响应网络错误现有行为。

`UserManagement.vue`：

- 保存失败由统一拦截器展示 Toast。
- 页面 catch 只负责结束 loading 和必要调试，不重复 Toast。
- 生产控制台不得输出请求体、密码或数据库错误详情。

## 文件级实施方案

| 文件 | 变更 |
|---|---|
| `backend/src/main/java/com/pricemanagement/dto/UserCreateRequest.java` | 新增创建 DTO 与 Jakarta Validation |
| `backend/src/main/java/com/pricemanagement/controller/UserController.java` | 接收 DTO、映射创建、保留成功/失败操作日志、冲突重新抛出 |
| `backend/src/main/java/com/pricemanagement/service/UserService.java` | 规范化、预检查、主动 flush、安全冲突映射 |
| `backend/src/main/java/com/pricemanagement/service/PasswordPolicyValidator.java` | 从现有个人中心密码校验中抽取共享密码策略 |
| `backend/src/main/java/com/pricemanagement/service/ProfileService.java` | 改为复用共享密码策略，保持原有行为 |
| `backend/src/main/java/com/pricemanagement/exception/UserConflictException.java` | 定义稳定冲突类型和安全消息 |
| `backend/src/main/java/com/pricemanagement/config/GlobalExceptionHandler.java` | 将用户冲突映射为 HTTP 409 |
| `backend/src/main/java/com/pricemanagement/util/OperationLogHelper.java` | 支持失败响应码，避免将 409 记为 500 |
| `frontend/src/api/users.ts` | 与 Java `UserCreateRequest` 对齐 |
| `frontend/src/utils/http.ts` | 正确展示并传播 409 消息 |
| `frontend/src/views/UserManagement.vue` | 避免保存失败重复 Toast |
| `backend/src/test/**` | Service、Controller/API、Repository/迁移一致性测试 |

实施时先审查当前工作区已有候选修改：符合本方案的部分保留并完善，不符合本方案的部分定向修正；
不得覆盖或回退用户的其他未关联改动。

## 数据库分支方案

### 分支 A：实际数据库与仓库一致

- 不新增 Flyway migration。
- 不修改表结构。
- 通过 DTO、规范化、主动 flush 和安全冲突映射完成修复。

### 分支 B：实际数据库存在仓库未记录的历史唯一索引

先确认该索引是否符合业务唯一性。

若索引错误：

1. 新增下一版本 Flyway migration，禁止修改历史 migration。
2. migration 中仅删除确认错误的索引。
3. 同步 `init.sql`、Entity 注解和 `数据字典.md`。
4. 提供执行前验证 SQL、执行后验证 SQL 和补偿 migration 方案。

若索引合理：

1. 保留索引。
2. 将唯一性规则补入 Entity、`init.sql`、数据字典和 DTO 校验/冲突处理。
3. 增加对应测试。

任何数据库变更发布前必须备份数据库或确认可恢复点。

## 前后端与数据库一致性矩阵

| 检查项 | 要求 |
|---|---|
| API 路径 | 保持 `POST /api/users` |
| 请求结构 | TypeScript `CreateUserRequest` 与 Java `UserCreateRequest` 一致 |
| 响应结构 | 保持 `Result<User>`，密码不得返回 |
| HTTP 状态 | 成功 200；参数错误 400；冲突 409；权限不足 403 |
| 权限 | 保持 `@PreAuthorize("hasRole('ADMIN')")` |
| 操作日志 | 成功记录 200；冲突失败记录 409；日志脱敏 |
| ORM | `User`、`UserRole` 注解与真实表一致 |
| 数据库 | Flyway、`init.sql`、数据字典与真实表一致 |
| 字典 | 本次不新增编码显示值，无需新增字典项 |

## 关键参考文件与复用点

| 文件 | 复用原因 |
|---|---|
| `backend/src/main/java/com/pricemanagement/controller/UserController.java` | 保留现有路径、ADMIN 权限和操作日志入口 |
| `backend/src/main/java/com/pricemanagement/service/UserService.java` | 保留用户与初始角色同事务创建边界 |
| `backend/src/main/java/com/pricemanagement/service/EmployeeIdService.java` | 复用现有 6 位工号生成与格式规则 |
| `backend/src/main/java/com/pricemanagement/service/ProfileService.java` | 复用并抽取现有密码策略校验逻辑 |
| `backend/src/main/java/com/pricemanagement/util/OperationLogHelper.java` | 复用成功/失败操作日志和独立事务落库 |
| `backend/src/main/java/com/pricemanagement/util/SensitiveDataMasker.java` | 复用敏感参数脱敏能力 |
| `backend/src/main/java/com/pricemanagement/config/GlobalExceptionHandler.java` | 统一输出真实 HTTP 400/409 和 `Result<T>` |
| `frontend/src/api/users.ts` | 保持前端创建请求契约 |
| `frontend/src/utils/http.ts` | 统一展示 HTTP 错误消息 |
| `frontend/src/views/UserManagement.vue` | 保持新增用户交互与表单状态 |
| `backend/src/main/resources/db/migration/V3__user_role_management_enhancement.sql` | 核验用户和角色关联基线结构 |
| `backend/src/main/resources/init.sql`、`数据字典.md` | 核验初始化结构与数据字典一致性 |

## 测试方案

### Service 单元测试

- 正常创建用户并写入初始角色。
- 用户名、可选文本去除首尾空白。
- 空手机号、邮箱、部门按 `NULL` 保存。
- 未填写工号时调用 `EmployeeIdService`。
- 手工工号格式错误返回 400 类参数错误。
- 预检查发现用户名或工号重复时产生明确冲突。
- `sys_user` flush 并发冲突映射为安全 409 原因。
- `sys_user_role` flush 冲突映射为安全 409 原因。
- 未识别数据库冲突不泄露 SQL。
- 任一 flush 失败时不发布用户资格刷新事件。

### Controller/API 测试

- 非管理员访问返回 403。
- DTO 缺少用户名、密码、角色时返回 400。
- DTO 密码不符合 `security.password-policy` 时返回 400。
- DTO 携带未允许字段不会写入 Entity。
- 用户冲突返回真实 HTTP 409 和统一 `Result`。
- 响应中不包含密码。
- 创建成功记录成功操作日志。
- 创建冲突记录 409 失败操作日志，且日志不含密码。

### Repository/迁移测试

- `sys_user` 和 `sys_user_role` 的表名、列名和唯一索引与 Entity 一致。
- Flyway 校验通过。
- 如新增 migration，验证执行前后索引状态和幂等边界。

### 前端验证

- 正常创建后关闭弹窗并刷新列表。
- 重复用户名或工号时只展示一次后端冲突原因。
- 保存失败后弹窗保留，输入内容不丢失。
- 400、401、403、404、429、500 和网络断开提示无回归。
- `npm run build` 无 TypeScript 错误。

### 关键回归

- 登录、刷新令牌和退出登录。
- 用户列表、创建、编辑、锁定、解锁、删除。
- 独立多角色分配与权限隔离。
- 用户导入，确认未被创建 DTO 改动影响。
- 通知资格刷新仅在用户创建事务成功提交后执行。

## 质量门禁

实施完成后必须执行：

```powershell
cd backend
mvn test

cd ../frontend
npm run build
```

并人工确认：

- [ ] Flyway validate 通过。
- [ ] Controller DTO 与前端 API 类型一致。
- [ ] 创建、重置密码与个人中心改密使用同一密码策略。
- [ ] `@PreAuthorize("hasRole('ADMIN')")` 保持有效。
- [ ] 成功与失败操作日志均存在且已脱敏。
- [ ] 生产环境未开启 `show-sql` 或 debug 安全日志。
- [ ] 若存在数据库变更，已备份并准备补偿 migration。
- [ ] 用户管理和权限隔离关键回归通过。

任一必跑门禁失败，不得合并或发布。

## 文档更新矩阵

按实际变更内容更新，不制造无效文档改动：

| 文档 | 更新要求 |
|---|---|
| `README.md` | 若用户管理功能说明需要体现明确冲突提示，则更新；否则记录无需变更 |
| `docs/dev/开发指南.md` | 更新创建 DTO、409 错误语义、日志与验证要求 |
| `docs/dev/项目设计文档.md` | 更新 `POST /api/users` 请求字段和 409 响应 |
| `docs/dev/UI设计说明.md` | 更新新增用户冲突只显示一次 Toast 的交互说明 |
| `docs/archive/项目完成总结.md` | 修复完成后更新用户管理完成状态 |
| `docs/ops/IDEA部署指南.md` | 无部署配置变化时记录无需变更；有 migration 时补充校验步骤 |
| `backend/src/main/resources/数据字典.md` | 仅当真实数据库结构发生变化时更新 |

## 实施步骤

1. 完成诊断门槛，记录真实冲突约束和数据库一致性结果。
2. 审查当前工作区候选修改，与本方案逐项对齐。
3. 新增并接入 `UserCreateRequest`，同步前端 `CreateUserRequest`。
4. 完成 Service 规范化、主动 flush 和安全冲突映射；禁止同事务重试。
5. 补齐 HTTP 409 全局处理和 409 操作日志。
6. 修复前端 409 消息展示与重复 Toast。
7. 根据数据库诊断结果选择分支 A 或分支 B。
8. 补齐 Service、Controller/API、Repository/迁移测试。
9. 执行质量门禁和关键回归。
10. 更新正式文档；完成后将本方案移入 `docs/plan/done/`。

## 回滚与补偿

### 无数据库变更

- 回滚应用代码到上一可用版本。
- 回滚后验证用户创建、角色关联和操作日志。
- 不删除失败请求产生的操作日志。

### 有数据库变更

- 发布前备份数据库或确认恢复点。
- 禁止回改历史 migration。
- 使用补偿 migration 恢复索引或约束。
- 回滚后重新执行 `SHOW INDEX`、Flyway validate 和用户创建验证。

## Verification

诊断后记录：

```text
原始失败请求真实冲突约束：历史日志缺失，无法追溯
已核验冲突表：sys_user、sys_user_role
是否存在仓库未记录索引：否
采用数据库分支：A
```

数据库验证：

```sql
SELECT id, username, employee_id, wechat_openid
FROM sys_user
WHERE username = '<测试用户名>';

SELECT ur.user_id, ur.role_id
FROM sys_user_role ur
JOIN sys_user u ON u.id = ur.user_id
WHERE u.username = '<测试用户名>';
```

确认：

- 成功创建时，用户和初始角色关联同时存在。
- 冲突失败时，不残留半成品用户或角色关联。
- 操作日志记录正确 HTTP 结果且不含敏感数据。
- 前端只展示一次安全、明确的错误消息。

## 完成标准

- 已核验并记录全部真实唯一约束；原始失败请求因历史日志缺失无法追溯到单一约束。
- 创建接口使用受控 DTO，前后端请求类型一致。
- 参数错误返回 400，业务冲突返回真实 HTTP 409。
- 数据库冲突不会泄露 SQL、约束明细或敏感数据。
- 用户和角色关联保持同事务原子性，失败无残留。
- 成功与失败操作日志完整，409 不再错误记录为 500。
- 前端只展示一次后端安全冲突原因。
- Entity、Flyway、`init.sql`、数据字典和真实数据库一致。
- `mvn test`、`npm run build`、Flyway validate 和关键回归全部通过。
- 正式文档已按变更内容同步，方案已归档。

## 实施结果

实施日期：2026-06-14

- 实际数据库不存在仓库未记录的用户相关唯一索引，采用数据库分支 A，未新增 migration。
- 新增 `UserCreateRequest`，创建接口不再直接接收 `User` Entity。
- 新增共享 `PasswordPolicyValidator`，用户创建、管理员重置密码和个人中心改密复用同一策略。
- 用户与初始角色关联写入均主动 flush，数据库冲突映射为安全、结构化 HTTP 409。
- 参数校验和业务参数错误返回真实 HTTP 400。
- 创建冲突失败操作日志记录响应码 409，并脱敏密码等敏感参数。
- 前端统一展示后端 409 消息，用户管理页面不再重复 Toast。
- 已同步 README、开发指南、项目设计文档、UI 设计说明、项目完成总结和 IDEA 部署指南。

验证结果：

```text
定向后端测试：11 项通过
完整后端测试：109 项通过
前端 npm run build：通过
git diff --check：通过
生产配置检查：ddl-auto=validate、show-sql=false、业务日志=info、安全日志=warn
数据库索引检查：sys_user 与 sys_user_role 均与 Entity、Flyway、init.sql、数据字典一致
```
