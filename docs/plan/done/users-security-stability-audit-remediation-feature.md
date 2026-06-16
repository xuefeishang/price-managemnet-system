# 用户账户安全与错误处理审计整改方案

## Context

2026-06-14 对用户创建 409 修复进行代码审计后，确认原修复已经解决
`POST /api/users` 的受控 DTO、结构化冲突响应和创建页面单次提示问题，但项目仍存在以下
跨入口安全与稳定性缺口：

| 编号 | 风险等级 | 审计发现 |
|---|---|---|
| A-01 | 高 | 未被任何客户端使用的公开注册接口 `/api/auth/register` 可绕过共享密码策略 |
| A-02 | 高 | 管理员用户更新接口和 Excel 用户导入可绕过共享密码策略 |
| A-03 | 中 | HTTP 拦截器和页面 `catch` 均可能显示错误 Toast，造成重复提示 |
| A-04 | 中 | 全局数据完整性异常日志只记录异常类型，无法定位约束冲突 |
| A-05 | 低 | 密码账号关联规则、角色写入失败事务回滚等关键分支缺少测试 |

本方案以当前项目实现为基础，完成用户账户密码写入入口收口、错误提示治理、安全可观测性
增强和关键测试补齐。整改不得泄露密码、手机号、邮箱、SQL 原文或数据库冲突值。

本方案不包含新增/编辑用户弹窗遮罩误关闭问题；该问题已通过移除遮罩关闭事件单独修复。

## 目标

1. 所有业务可达的密码写入入口统一执行 `security.password-policy`。
2. 未使用的公开注册入口从安全白名单和控制器中移除。
3. 普通用户资料更新接口不得接收密码、角色、锁定状态等越权字段。
4. 用户 Excel 导入必须先完成整份模板和数据预检，零错误后才允许整批写入。
5. 同一个失败请求最多展示一次错误 Toast，同时保留页面定制提示能力。
6. 数据完整性异常日志可定位安全的约束标识，但不记录冲突数据或 SQL 原文。
7. 用自动化测试覆盖本轮审计暴露的关键安全和事务边界。

## 范围与非目标

### 本次范围

- `/api/auth/register` 公开注册入口及安全白名单。
- `PUT /api/users/{id}` 管理员用户资料更新契约。
- 用户 Excel 导入的模板校验、全量数据预检、密码策略和原子写入。
- PC 前端 Axios 错误对象、全局 Toast 和页面 Toast 协作机制。
- `DataIntegrityViolationException` 的安全诊断信息提取与日志输出。
- 用户密码策略、更新 DTO、导入、事务回滚和错误诊断测试。
- 与实际变更相关的正式项目文档。

### 非目标

- 不新增自助注册业务。
- 不改变现有登录、刷新令牌或微信登录流程。
- 不修改用户角色、权限和菜单模型。
- 不修改 `sys_user`、`sys_user_role` 表结构或唯一约束。
- 不在本次整改中引入完整分布式追踪系统。
- 不重构全部前端页面业务逻辑；错误提示治理采用可验证的渐进迁移。
- 不将用户导入拆分为“预检接口 + 确认导入接口”两个外部 API；单次上传请求内部完成全量预检和原子导入。
- 不允许用户导入继续采用“部分成功、异常行跳过”的语义；任意模板或数据异常都必须阻止整批写入。

## 当前实现诊断

### A-01：公开注册入口绕过密码策略

当前链路：

```text
POST /api/auth/register
  -> SystemConstants.PUBLIC_PATHS permitAll
  -> AuthController.register(LoginRequest)
  -> passwordEncoder.encode()
  -> userRepository.save()
```

已确认：

- `frontend`、`frontend-uniapp` 均没有调用 `/api/auth/register`。
- 系统用户由管理员用户管理页面或 Excel 导入创建。
- 注册入口直接复用登录 DTO，未调用 `PasswordPolicyValidator`。
- 注册入口未复用用户创建冲突、规范化和初始角色关联流程。

**整改决策：移除未使用的公开注册接口，并从 `PUBLIC_PATHS` 删除该路径。**

若未来确需自助注册，必须作为独立功能重新规划，使用专用注册 DTO、验证码/审批、限流、
统一密码策略、冲突处理、操作日志和角色关联；不得恢复当前实现。

### A-02：用户更新和 Excel 导入绕过密码策略

当前用户更新接口直接接收 `User` Entity：

```text
PUT /api/users/{id}
  -> UserController.updateUser(@RequestBody User)
  -> UserService.updateUser()
  -> 如果请求携带 password，则直接 encode
```

虽然当前 PC 前端 `UpdateUserRequest` 不声明密码，但后端仍允许任意 API 客户端提交密码、
角色和锁定字段，形成服务端字段注入与密码策略绕过。

当前 Excel 导入流程：

```text
ImportExportService.importUsers()
  -> new UserExcelListener(...)
  -> 从 Excel 或 DEFAULT_USER_PASSWORD 读取明文密码
  -> passwordEncoder.encode()
  -> userRepository.save()
```

导入密码没有调用 `PasswordPolicyValidator`。默认密码若不满足策略，也会批量创建弱密码账户。

当前 Listener 在读取每一行时直接写数据库，存在额外一致性风险：

- 无法在写入前确认 Excel 表头、必填列和整份文件结构正确。
- 只能逐行查询数据库，无法提前发现文件内用户名或工号重复。
- 前几行可能已成功入库，后续行才发现异常，产生部分成功结果。
- 用户保存成功后角色保存失败时，可能留下没有角色关联的半成品用户。
- `successCount` 在角色写入前递增，统计结果可能与最终数据不一致。

**整改决策：用户导入改为“完整读取与全量预检 -> 全量事务写入”的两阶段流程。任何一项校验失败，
整批数据均不写入。**

### A-03：前端错误 Toast 重复

当前 `frontend/src/utils/http.ts` 对多数带 HTTP 状态码的失败请求直接显示 Toast，并将错误继续
reject。项目页面中存在大量 `catch(error) -> showToast(error.message)`，因此同一请求可能：

```text
响应拦截器显示一次 Toast
  -> 页面 catch 再显示一次 Toast
```

仅删除某一个页面的 `catch` 提示无法解决全局一致性问题。

### A-04：数据完整性异常缺少诊断信息

当前全局处理只记录：

```text
Data integrity violation: type=DataIntegrityViolationException
```

该日志满足“不泄露数据库异常原文”，但无法判断冲突表或约束。`UserService` 同时仍依赖数据库
异常消息文本判断用户冲突原因，跨数据库驱动和数据库版本时不稳定。

### A-05：关键测试缺口

当前测试已经覆盖用户创建基础规范化、用户名冲突和工号数据库冲突，但尚未覆盖：

- 密码等于用户名、昵称、手机号时被拒绝。
- 用户更新请求无法携带密码、角色和锁定状态。
- Excel 明文密码和默认密码均执行统一策略。
- 初始角色写入失败时，已写入的用户记录随事务回滚。
- 数据完整性诊断只输出安全约束标识，不输出冲突值或 SQL。
- 前端全局提示和页面提示不会重复展示。

## 实现方案与技术决策

### 决策 1：关闭未使用的公开注册入口

实施要求：

1. 删除 `AuthController.register()`。
2. 从 `SystemConstants.PUBLIC_PATHS` 删除 `/api/auth/register`。
3. 清理 `AuthController` 中仅由注册流程使用的直接 Repository 或通知依赖；仍被其他方法使用的
   依赖必须保留。
4. 清理正式文档中将该接口描述为可用公开接口的内容。
5. 增加安全测试，确认匿名请求无法通过该路径创建用户。

关闭后的用户创建入口：

| 入口 | 权限 | 密码策略 |
|---|---|---|
| `POST /api/users` | ADMIN | `PasswordPolicyValidator` |
| 用户 Excel 导入 | ADMIN | `PasswordPolicyValidator` |
| `POST /api/users/{id}/reset-password` | ADMIN | `PasswordPolicyValidator` |
| `PUT /api/auth/password` | 已登录用户 | `PasswordPolicyValidator` |

### 决策 2：用户更新接口使用受控 DTO

新增 `UserUpdateRequest`，后端允许字段与当前前端 `UpdateUserRequest` 对齐：

| 字段 | 是否允许 | 说明 |
|---|---|---|
| `nickname` | 是 | 可选文本，规范化后写入 |
| `email` | 是 | 合法邮箱，空文本转 `NULL` |
| `phone` | 是 | 数字净化，空文本转 `NULL` |
| `department` | 是 | 空文本转 `NULL` |
| `deptId` | 是 | 部门关联 |
| `status` | 是 | 使用现有状态枚举 |
| `password` | 否 | 仅允许通过重置密码接口修改 |
| `role` | 否 | 仅允许通过独立角色分配接口修改 |
| `isLocked` | 否 | 仅允许通过锁定/解锁接口修改 |
| `username` / `employeeId` | 否 | 当前更新流程不允许修改 |

Controller 改为：

```java
public Result<User> updateUser(Long id, @Valid @RequestBody UserUpdateRequest request)
```

Service 不再接受外部构造的 `User` Entity。DTO 映射应显式逐字段完成，禁止使用反射式全字段复制。

前端 `UpdateUserRequest` 删除 `role`、`isLocked`，继续通过现有独立 API 完成角色分配和锁定。

`UserUpdateRequest` 必须采用 DTO 级严格反序列化策略：请求携带 DTO 未声明字段时返回 HTTP 400，
尤其是 `password`、`role`、`isLocked`、`username` 和 `employeeId`，不得静默忽略。实现时不得为此
放宽或改变全局 ObjectMapper 行为，避免影响其他接口。

### 决策 3：Excel 导入采用全量预检和原子写入

用户导入采用单次请求内的两阶段流程：

```text
上传 Excel
  -> 阶段一：只读解析与全量预检，不写数据库
  -> 存在任意错误：返回校验失败结果，导入数量为 0
  -> 全部通过：进入阶段二事务
  -> 阶段二：重新校验数据库唯一性并批量写入用户与角色
  -> 任意写入失败：整批事务回滚
  -> 全部成功：返回导入成功数量
```

#### 阶段一：模板与文件级校验

使用只负责收集数据和表头的 Listener，不得在 `invoke()` 中调用 Repository 或执行写入。

预检项目：

| 分类 | 校验内容 |
|---|---|
| 文件 | 文件非空、扩展名和实际 Excel 格式合法；前端与后端均强制限制为 10MB |
| 工作表 | 必须存在且只读取名为“用户”的工作表，缺失时视为模板错误 |
| 表头 | 必须完整包含标准模板的 9 个列名；列顺序可调整，但缺失列、重复列或未知列均视为模板错误 |
| 行数 | 至少包含 1 条数据；通过类型安全配置 `import.user.max-rows` 限制，默认 1000 |
| 空行 | 允许忽略完全空白行；包含部分数据的行必须参与完整校验 |
| 模板示例 | 下载模板不得携带会被误导入的真实示例行；改为说明工作表或仅保留表头 |

标准表头以 `UserExcelData` 的 `@ExcelProperty` 为唯一代码来源：

```text
用户名
工号
昵称
邮箱
手机号
部门
角色(ADMIN/EDITOR/VIEWER)
状态(ACTIVE/INACTIVE)
初始密码
```

标准模板的 9 个列头全部必须存在，但对应单元格是否允许为空按字段规则判断。例如工号、邮箱、
手机号、部门和初始密码允许空值；用户名不允许为空。不得由 EasyExcel 将缺失列静默映射为空后
继续导入。

新增 `ImportProperties` 管理 `import.user.max-rows`，生产环境可按容量调整。文件大小继续由现有
`spring.servlet.multipart.max-file-size/max-request-size=10MB` 在后端强制限制；禁止只依赖前端
校验，因为 API 客户端可以绕过前端直接上传。

#### 阶段一：行级与整表数据校验

每行先规范化为内部 `ValidatedUserImportRow`，保留原始 Excel 行号用于错误定位，但不得保留或
返回明文密码。

逐行校验：

- 用户名必填、去除首尾空白、长度符合 Entity 限制。
- 工号可空；非空时必须为 6 位数字。
- 昵称、邮箱、手机号、部门执行与用户创建相同的规范化和长度/格式规则。
- 角色必须是已存在且允许分配的角色；非法或不存在角色属于错误，不再静默回退 `VIEWER`。
- 状态必须是允许值；非法状态属于错误，不再静默回退 `ACTIVE`。
- 初始密码为空时使用 `DEFAULT_USER_PASSWORD`；最终密码必须通过 `PasswordPolicyValidator`。
- 错误信息只包含行号、字段名和安全原因，不包含密码、完整手机号、邮箱或数据库异常原文。

整表校验：

- 规范化后的用户名在 Excel 文件内不得重复。
- 非空工号在 Excel 文件内不得重复。
- 必须一次性批量查询数据库，检查全部用户名是否已经存在。
- 必须新增批量工号查询能力，检查全部非空工号是否已经存在。
- 工号为空的行在预检阶段生成候选工号；候选工号也必须参与文件内重复和数据库重复批量校验，
  并写入 `ValidatedUserImportRow`。写入阶段仍需再次检查并由数据库唯一约束最终保护。
- 汇总全部可发现错误后一次返回，不因发现第一条错误就停止，便于用户一次修正整份文件。

建议新增 Repository 方法：

```java
List<User> findByEmployeeIdIn(Collection<String> employeeIds);
```

已有 `findByUsernameIn(Collection<String>)` 应直接复用，禁止对每一行分别查询数据库。

全部校验通过后、进入写入事务前，对每行最终密码执行编码，并立即丢弃后续流程不再需要的明文
引用。`ValidatedUserImportRow` 只携带编码后的密码进入写入事务，禁止在其 `toString()`、日志或
错误响应中包含密码字段。

#### 阶段二：全量事务写入

全量预检通过后，调用独立的 Spring Bean，例如 `UserImportWriteService`：

```java
@Transactional
public int importValidatedRows(List<ValidatedUserImportRow> rows)
```

事务写入要求：

1. 进入事务后再次批量检查用户名和工号，防止预检完成后出现并发创建。
2. 任意并发重复立即抛出结构化导入冲突异常，整批回滚。
3. 每个用户复用统一用户构建、字段规范化、密码策略和密码编码逻辑。
4. 用户写入后必须同步写入初始 `sys_user_role`。
5. 使用 `saveAndFlush()` 或批次 flush 主动暴露数据库约束异常。
6. 任意用户或角色写入失败，整批用户和角色关联全部回滚。
7. 仅在事务全部成功后返回成功数量和发送导入完成通知。
8. 事务失败时成功数量固定为 0，不再返回“成功 N 条、跳过 M 条”。

预检与写入必须位于不同 Spring Bean，确保 `@Transactional` 通过代理生效，禁止同类内部调用造成
事务注解失效。

#### 导入响应契约

将现有部分成功结果：

```text
successCount / skipCount / errors
```

调整为明确的全量结果，例如：

```java
record UserImportResult(
    boolean valid,
    boolean imported,
    int totalRows,
    int importedCount,
    List<UserImportValidationError> errors
)
```

其中 `UserImportValidationError` 至少包含：

```text
rowNumber
field
code
message
```

规则：

- 预检失败：HTTP 400，`valid=false`、`imported=false`、`importedCount=0`。
- 预检通过且写入成功：HTTP 200，`valid=true`、`imported=true`。
- 预检后发生并发唯一冲突：HTTP 409，整批回滚，返回安全冲突原因。
- 系统异常：HTTP 500，整批回滚，不返回内部异常原文。

默认密码仍通过 `SecurityProperties` 和 `DEFAULT_USER_PASSWORD` 管理，不在代码、错误响应或文档中
写入明文。

### 决策 4：用户导入操作日志与安全可观测性

用户导入属于数据变更操作，必须使用 `OperationLog.OperationType.IMPORT` 记录成功和失败结果。
通知用于告知用户结果，不能替代操作日志。

由于用户导入需要记录真实 HTTP 400/409/500，且失败日志必须在业务事务回滚后保留，本流程不直接
使用当前固定失败码为 500 的 `@OperationLog` 默认行为。由 `ImportController` 或专用导入审计服务
调用 `OperationLogHelper`，并复用其独立事务能力记录真实响应码。

操作日志要求：

| 场景 | 响应码 | 状态与记录要求 |
|---|---:|---|
| 全量导入成功 | 200 | 记录总行数、导入数量、耗时 |
| 模板或数据预检失败 | 400 | 记录总行数、错误数量、安全错误码摘要；写入数量为 0 |
| 预检后并发唯一冲突 | 409 | 记录安全冲突类型；写入数量为 0 |
| 未授权访问 | 403 | 由安全边界记录，不进入用户导入业务 |
| 系统异常 | 500 | 记录根异常安全类型和通用错误码，不记录异常原文 |

操作日志和普通业务日志允许记录：

```text
operatorId / username
operationModule=用户管理
operationType=IMPORT
totalRows
importedCount
validationErrorCount
status
responseCode
durationMs
safeErrorCodes
requestId / traceId（运行环境已提供时）
```

禁止记录：

- 上传文件内容、文件名和完整路径。
- 任意行的明文密码、密码哈希、手机号、邮箱或完整业务数据。
- 完整预检错误列表、SQL、数据库异常原文和冲突值。
- `MultipartFile` 的序列化内容。

`safeErrorCodes` 只允许记录有限集合和数量，例如：

```text
MISSING_HEADER:1
DUPLICATE_USERNAME_IN_FILE:2
USERNAME_ALREADY_EXISTS:3
INVALID_PASSWORD_POLICY:4
```

Controller 返回响应后才发送“导入完成”通知；预检失败、冲突和系统失败发送安全失败摘要。通知内容
不得直接使用异常 `message`，不得包含文件名、用户名、工号、手机号、邮箱或数据库异常原文。

实施时必须核验 `OperationLogHelper`：

1. 成功和失败请求参数均执行 `SensitiveDataMasker`。
2. 失败日志支持传入真实响应码。
3. 操作日志使用独立事务，业务事务回滚时仍可保留。
4. 记录日志自身失败不得影响用户导入结果。

### 决策 5：建立单次错误提示协议

新增统一前端错误类型和辅助函数，避免拦截器与页面各自猜测：

```typescript
interface ApiError extends Error {
  response?: AxiosResponse
  toastShown?: boolean
}
```

Axios 请求配置增加：

```typescript
showErrorToast?: boolean // 默认 true
```

统一规则：

1. 拦截器需要展示 Toast 时，展示后设置 `error.toastShown = true`。
2. 页面需要定制提示时，请求显式设置 `showErrorToast: false`，并在 `catch` 中调用统一
   `showApiError(error, fallbackMessage)`。
3. `showApiError` 发现 `toastShown === true` 时不得再次提示。
4. 401 刷新失败、403、超时、网络断开和 5xx 继续由拦截器统一处理。
5. 文件下载、批量导入等需要业务化消息的请求使用本地提示模式。
6. 禁止继续新增裸写的 `showToast(error.message || '失败')`。
7. 导入预检失败时，标准化 `ApiError` 必须保留后端结构化错误数据，供页面展示按行错误明细。

实施分两步：

- 第一批必须迁移 `UserManagement.vue` 全部异步操作，验证协议可用。
- 第二批使用 `rg` 审计并迁移其他存在重复提示风险的页面；每批迁移后执行构建和关键页面回归。

该方案保留全局兜底，同时允许页面显示更准确的业务消息，不要求一次性删除全部页面错误提示。

### 决策 6：安全提取完整性约束标识

新增 `DataIntegrityViolationDiagnostics` 工具或等价组件，统一完成：

1. 优先从 Hibernate `ConstraintViolationException#getConstraintName()` 读取约束名。
2. Hibernate 未提供时，仅从数据库异常消息中提取 `for key` 后的约束标识，不保留其他文本。
3. 约束标识必须通过安全字符白名单，例如：

   ```text
   [A-Za-z0-9_.-]{1,128}
   ```

4. 返回结构仅包含：

   ```text
   constraintName
   rootExceptionType
   ```

5. 未识别时统一返回 `unknown`。
6. 禁止记录原始异常消息、SQL、冲突值或完整堆栈到普通 warn 日志。

`GlobalExceptionHandler` 使用该组件记录安全诊断：

```text
Data integrity violation: constraint=<safe-name>, rootType=<safe-type>
```

`UserService.resolveCreateConflictReason()` 改为根据安全约束标识匹配已知用户约束，不再直接解析
完整数据库异常消息。未知约束继续返回通用安全 409。

本轮不新增 requestId/traceId 基础设施；若运行环境已提供 MDC 标识，可附带输出，否则保持为空。

## 前后端与数据库一致性检查

| 检查项 | 整改要求 |
|---|---|
| 公开 API | 删除 `/api/auth/register` 后，同步安全白名单与 API 文档 |
| 用户更新 API | Java `UserUpdateRequest` 与 TypeScript `UpdateUserRequest` 字段完全一致 |
| 密码写入 | 创建、导入、管理员重置、个人改密全部复用 `PasswordPolicyValidator` |
| 用户导入 API | 单次请求先全量预检；任意错误返回 400 且写入数量为 0；并发冲突返回 409 且整批回滚 |
| 用户导入响应 | Java `UserImportResult`、`UserImportValidationError` 与 TypeScript 类型一致 |
| 用户导入操作日志 | 使用 `IMPORT` 类型；成功记录 200，预检失败记录 400，并发冲突记录 409，系统失败记录 500 |
| 角色维护 | 继续使用独立角色分配 API，不通过更新 DTO 修改 |
| 锁定维护 | 继续使用锁定/解锁 API，不通过更新 DTO 修改 |
| Entity/ORM | 本次不修改 Entity 映射 |
| 数据库 | 本次不修改表、字段、索引和约束，无需 migration |
| 数据字典 | 本次不新增编码值，无需更新数据字典 |
| HTTP 错误 | 保持 `Result<T>`；参数错误 400，冲突 409，权限不足 403 |

## 文件级整改方案

| 文件 | 计划变更 |
|---|---|
| `backend/src/main/java/com/pricemanagement/constants/SystemConstants.java` | 删除公开注册路径 |
| `backend/src/main/java/com/pricemanagement/controller/AuthController.java` | 删除未使用注册接口及无用依赖 |
| `backend/src/main/java/com/pricemanagement/dto/UserUpdateRequest.java` | 新增受控更新 DTO 与校验 |
| `backend/src/main/java/com/pricemanagement/controller/UserController.java` | 更新接口改接收 `UserUpdateRequest` |
| `backend/src/main/java/com/pricemanagement/service/UserService.java` | 显式更新受控字段；移除普通更新中的密码写入；使用安全约束诊断 |
| `backend/src/main/java/com/pricemanagement/dto/UserImportValidationError.java` | 新增结构化、安全的导入预检错误 |
| `backend/src/main/java/com/pricemanagement/dto/ValidatedUserImportRow.java` | 新增仅供内部写入使用的已验证行模型 |
| `backend/src/main/java/com/pricemanagement/config/properties/ImportProperties.java` | 类型安全管理用户导入最大行数 |
| `backend/src/main/resources/application.yml` | 增加 `import.user.max-rows` 默认配置 |
| `backend/src/main/java/com/pricemanagement/listener/UserExcelValidationListener.java` | 只读取表头和数据，不执行数据库写入 |
| `backend/src/main/java/com/pricemanagement/service/UserImportValidationService.java` | 模板、逐行、整表重复和数据库重复全量预检 |
| `backend/src/main/java/com/pricemanagement/service/UserImportWriteService.java` | 在单一事务内写入全部用户和角色关联 |
| `backend/src/main/java/com/pricemanagement/service/ImportExportService.java` | 编排预检和写入，生成新导入响应 |
| `backend/src/main/java/com/pricemanagement/repository/UserRepository.java` | 增加批量工号查询，复用批量用户名查询 |
| `backend/src/main/java/com/pricemanagement/controller/ImportController.java` | 返回真实 400/409/500；编排 IMPORT 操作日志与安全通知 |
| `backend/src/main/java/com/pricemanagement/exception/UserImportValidationException.java` | 表达整批预检失败及结构化错误 |
| `backend/src/main/java/com/pricemanagement/config/GlobalExceptionHandler.java` | 输出安全约束名和根异常类型 |
| `backend/src/main/java/com/pricemanagement/util/DataIntegrityViolationDiagnostics.java` | 新增安全诊断提取组件 |
| `backend/src/main/java/com/pricemanagement/util/OperationLogHelper.java` | 确保 IMPORT 成功/失败日志均脱敏并支持真实响应码 |
| `frontend/src/utils/http.ts` | 增加 `ApiError`、`showErrorToast` 和 `toastShown` 协议 |
| `frontend/src/utils/apiError.ts` | 新增页面统一错误提示辅助函数，或在现有公共工具中实现 |
| `frontend/src/api/users.ts` | 更新用户与导入响应 DTO；导入请求使用本地业务错误展示 |
| `frontend/src/views/UserManagement.vue` | 展示全量预检错误；全部异步操作迁移到单次错误提示协议 |
| `backend/src/test/**` | 补齐安全、事务、导入和诊断测试 |

原 `UserExcelListener` 在新流程验证通过后删除，避免两套导入路径并存。

实施前必须检查工作区现有未提交改动，定向编辑上述文件，不得覆盖其他功能修改。

## 关键参考文件

| 文件 | 复用点 |
|---|---|
| `backend/src/main/java/com/pricemanagement/dto/UserCreateRequest.java` | 受控用户请求 DTO 的校验和映射模式 |
| `backend/src/main/java/com/pricemanagement/service/PasswordPolicyValidator.java` | 唯一密码策略实现 |
| `backend/src/main/java/com/pricemanagement/service/ProfileService.java` | 已接入共享密码策略的参考实现 |
| `backend/src/main/java/com/pricemanagement/exception/UserConflictException.java` | 安全、结构化冲突原因 |
| `backend/src/main/java/com/pricemanagement/dto/UserExcelData.java` | 用户导入模板标准表头来源 |
| `backend/src/main/java/com/pricemanagement/repository/UserRepository.java` | 已有批量用户名查询能力 |
| `backend/src/main/java/com/pricemanagement/util/SensitiveDataMasker.java` | 日志敏感信息保护 |
| `backend/src/main/java/com/pricemanagement/util/OperationLogHelper.java` | 数据变更成功/失败日志与独立事务入口 |
| `frontend/src/utils/http.ts` | 全局请求与错误处理边界 |
| `frontend/src/views/UserManagement.vue` | 首批错误提示协议迁移页面 |
| `docs/plan/done/users-create-409-conflict-diagnosis-plan.md` | 前一阶段用户创建整改基线 |

## 实施步骤

### Phase 1：关闭公开注册旁路

1. 删除 `AuthController.register()`。
2. 删除 `PUBLIC_PATHS` 中 `/api/auth/register`。
3. 清理无用依赖和文档中的公开注册描述。
4. 增加匿名访问不能创建用户的安全测试。

完成门槛：

- 代码库除历史完成方案外，不再存在可执行 `/api/auth/register`。
- 匿名请求无法创建用户。

### Phase 2：收口用户更新与建立导入预检

1. 新增 `UserUpdateRequest`。
2. Controller 和 Service 改为显式受控字段更新。
3. 删除 `UserService.updateUser()` 中的密码写入逻辑。
4. 前端 `UpdateUserRequest` 删除角色和锁定字段。
5. 新增只读 Excel Listener，校验模板表头、文件结构和最大行数。
6. 新增导入预检服务，完成字段校验、密码策略、文件内重复和数据库重复检查。
7. 新增批量工号查询，禁止逐行数据库重复查询。
8. 新增事务写入服务，在全量预检通过后原子写入用户和角色。
9. 更新导入响应契约和前端错误明细展示。
10. Controller 移除吞掉异常并包装 HTTP 200/500 的导入处理，交由全局异常处理器返回真实
    HTTP 400/409/500。
11. 为成功、预检失败、并发冲突和系统失败记录 IMPORT 操作日志及真实响应码。
12. 导入通知改为只使用安全统计摘要，不直接传播异常消息。
13. 删除原边读边写的 `UserExcelListener`。
14. 补齐 DTO、Service、Repository、Controller/API、导入日志和事务测试。

完成门槛：

- 搜索所有 `passwordEncoder.encode`，除登录基础设施和明确密码入口外，不存在业务旁路。
- 任意 `PUT /api/users/{id}` 请求均不能修改密码、角色和锁定状态。
- 任意模板或数据错误均导致导入数量为 0。
- 预检通过后的任意写入错误均导致整批事务回滚。
- 导入校验使用批量数据库查询，不产生逐行 N+1 查询。
- 导入成功和失败均存在 `IMPORT` 操作日志，响应码、耗时和安全统计准确。

### Phase 3：治理前端重复错误提示

1. 定义 `ApiError` 与请求级 `showErrorToast`。
2. 拦截器展示错误后标记 `toastShown`。
3. 新增 `showApiError`，保证页面提示幂等。
4. 迁移 `UserManagement.vue` 全部异步失败处理。
5. 审计并分批迁移其他重复提示页面。
6. 执行 PC 端关键页面人工回归。

完成门槛：

- 用户管理所有失败操作最多显示一次 Toast。
- `rg` 检查不再新增裸写 `showToast(error.message || ...)`。
- 登录、Token 刷新、403、404、409、500、超时和网络断开提示无回归。

### Phase 4：增强完整性异常安全诊断

1. 新增安全诊断提取组件。
2. 全局完整性异常日志接入安全约束标识。
3. 用户创建冲突映射改用统一诊断组件。
4. 增加已知约束、未知约束和恶意异常文本测试。

完成门槛：

- 日志能够区分已知约束和 `unknown`。
- 日志和前端响应不包含 SQL、冲突值、密码、手机号或邮箱。

### Phase 5：事务与安全回归

1. 增加密码等于用户名、昵称、手机号的测试。
2. 增加初始角色保存失败后的事务回滚集成测试。
3. 增加导入弱密码、默认密码不合规测试。
4. 执行完整后端测试、前端构建与关键人工回归。
5. 更新正式文档并将本方案移入 `docs/plan/done/`。

## 测试方案

### 后端单元测试

- `PasswordPolicyValidator`：
  - 合规密码通过。
  - 长度、字母、数字、空白规则分别失败。
  - 密码等于用户名、昵称、手机号时分别失败。
- `UserUpdateRequest`：
  - 合法资料通过。
  - 非法邮箱、超长字段失败。
  - JSON 携带 `password`、`role`、`isLocked`、`username` 或 `employeeId` 时返回 HTTP 400，且不会
    进入更新业务。
- `UserService`：
  - 更新仅修改允许字段。
  - 更新流程不调用密码编码器。
- `UserExcelValidationListener`：
  - 读取过程中不调用任何 Repository 写入方法。
  - 能识别缺失、重复、未知表头和非 Excel 文件。
- `UserImportValidationService`：
  - 缺少必填表头、空文件、无数据行、超过最大行数时预检失败。
  - 下载模板只包含标准表头和说明，不包含会被误导入的示例数据行。
  - 完全空白行被忽略，部分空白行返回具体字段错误。
  - 文件内重复用户名和非空工号均能一次性定位全部冲突行。
  - 数据库已有用户名和工号通过批量查询识别。
  - 弱密码、非法角色、非法状态、错误邮箱和工号格式均阻止整批导入。
  - 错误列表不包含原始密码、完整手机号或邮箱。
- `UserImportWriteService`：
  - 全部合法行写入用户和初始角色。
  - 写入模型仅携带编码密码，不携带或记录明文密码。
  - 写入前再次批量检查数据库唯一性。
  - 任意用户或角色保存失败时抛出异常。
- `OperationLogHelper` 与导入审计：
  - IMPORT 成功日志记录响应码 200、总行数、导入数量和耗时。
  - 预检失败日志记录响应码 400、错误数量和安全错误码摘要。
  - 并发冲突日志记录响应码 409，系统失败日志记录响应码 500。
  - 业务事务回滚后失败操作日志仍然存在。
  - 操作日志不包含文件名、MultipartFile 内容、密码、手机号、邮箱、SQL 或异常原文。
- `DataIntegrityViolationDiagnostics`：
  - 能提取 Hibernate 约束名。
  - 能安全提取数据库 key 标识。
  - 恶意或超长文本返回 `unknown`。
  - 输出不包含冲突值和 SQL。

### 后端集成/API测试

- 匿名调用 `/api/auth/register` 无法创建用户。
- `PUT /api/users/{id}` 携带密码无法修改密码哈希。
- `PUT /api/users/{id}` 携带角色或锁定字段无法绕过独立接口。
- 用户创建写入用户成功、初始角色写入失败时，事务结束后用户和角色关联均不存在。
- 用户创建已知约束冲突返回安全 409；未知完整性冲突返回通用安全消息。
- 用户导入模板表头错误时返回 HTTP 400，数据库无新增用户。
- 用户导入文件内存在重复用户名或工号时返回全部错误，数据库无新增用户。
- 用户导入数据与数据库已有用户名或工号重复时返回 HTTP 400，数据库无新增用户。
- 默认导入密码不符合策略时整批导入被拒绝且未创建用户。
- 全量预检通过后，任意一条用户或角色写入失败时整批事务回滚。
- 预检通过后出现并发用户名或工号冲突时返回 HTTP 409，数据库无本批残留。
- 用户导入成功、预检失败、并发冲突和系统失败均生成正确的 IMPORT 操作日志。
- 用户导入失败通知只包含安全统计摘要，不包含异常原文或个人敏感信息。

### Repository 与迁移一致性测试

- `findByUsernameIn()` 能准确返回给定用户名集合中的已有用户，不误匹配其他用户。
- `findByEmployeeIdIn()` 能准确返回给定非空工号集合中的已有用户。
- 空集合和仅包含空值的输入不会产生错误查询。
- 批量预检调用次数固定，不随 Excel 行数线性增加。
- `sys_user.username`、`sys_user.employee_id` 和 `sys_user_role(user_id, role_id)` 唯一约束与
  Entity、Flyway、`init.sql` 和数据字典保持一致。
- Flyway validate 通过；确认本次仅新增 Repository 查询，无数据库结构变更。

### 前端验证

- 新增用户重复用户名：仅显示一次后端安全消息，弹窗保持打开。
- 更新、删除、锁定、解锁、角色分配、导入失败：每次仅显示一次 Toast。
- 导入预检失败时不刷新用户列表，并展示总错误数及按行号排列的错误明细。
- 导入包含任意异常行时，界面明确提示“未导入任何用户”，不得显示部分成功。
- 导入全部成功后才刷新用户列表并显示实际导入数量。
- 页面选择本地提示模式时，能够展示业务化 fallback。
- 401 刷新成功后原请求恢复；刷新失败仅提示并跳转一次。
- 403、404、409、500、超时、网络断开提示符合规则。
- `npm run build` 无 TypeScript 错误。

### 一致性与安全检查

```powershell
rg -n "passwordEncoder\.encode" backend/src/main/java
rg -n "/api/auth/register|/auth/register" backend frontend frontend-uniapp docs
rg -n "showToast\(error\.message|showToast\(err\.message" frontend/src
```

检查结果必须人工分类，不能只追求搜索结果为零。例如登录校验和明确密码入口中的编码属于允许项。

## 质量门禁

实施完成后必须执行：

```powershell
cd backend
mvn test

cd ../frontend
npm run build
```

并完成：

- [ ] 匿名注册入口已关闭。
- [ ] 所有业务密码写入入口已逐项审查。
- [ ] Java `UserUpdateRequest` 与 TypeScript `UpdateUserRequest` 一致。
- [ ] 更新接口不能修改密码、角色和锁定状态。
- [ ] 用户导入先全量校验模板、字段、文件内重复和数据库重复。
- [ ] 任意导入预检错误均保证写入数量为 0。
- [ ] Excel 明文密码和默认密码均执行共享策略。
- [ ] 全量预检通过后的用户与角色写入位于单一事务，任意失败整批回滚。
- [ ] 用户导入使用批量用户名和工号查询，无逐行 N+1 查询。
- [ ] Java 与 TypeScript 用户导入结果和错误结构一致。
- [ ] 用户与初始角色写入失败事务回滚测试通过。
- [ ] 用户导入成功和失败均记录 `IMPORT` 操作日志及真实响应码。
- [ ] IMPORT 操作日志仅包含安全统计、耗时和错误码摘要。
- [ ] 用户导入通知不包含异常原文或个人敏感信息。
- [ ] 批量用户名和工号 Repository 查询测试通过，无 N+1 查询。
- [ ] 用户相关 Entity、Flyway、`init.sql` 和数据字典一致性检查通过。
- [ ] 用户管理失败请求最多显示一次 Toast。
- [ ] 完整性异常日志具备安全诊断信息且不泄露业务数据。
- [ ] Flyway validate 通过，确认本次无数据库结构变更。
- [ ] 生产配置仍保持 `show-sql=false` 和安全日志级别。

任一高风险门禁失败，不得合并或发布。

## 文档更新矩阵

仅按实际变更更新对应职责文档：

| 文档 | 更新内容 |
|---|---|
| `README.md` | 用户管理安全能力说明；不再描述公开注册 |
| `docs/dev/开发指南.md` | 用户更新 DTO、密码入口、用户导入两阶段规则、前端错误提示协议 |
| `docs/dev/项目设计文档.md` | 删除注册 API；更新用户资料更新请求字段与导入响应契约 |
| `docs/dev/项目设计规范.md` | 若单次错误提示协议验证有效，补充为长期前端规范 |
| `docs/dev/UI设计说明.md` | 用户管理错误提示仅展示一次；导入预检错误明细交互 |
| `docs/dev/API调用手册.md` | 更新用户导入的全量预检、HTTP 状态和响应示例 |
| `docs/archive/项目完成总结.md` | 记录安全整改完成状态 |
| `docs/ops/IDEA部署指南.md` | 补充用户导入最大行数和现有 multipart 大小限制配置 |
| `backend/src/main/resources/数据字典.md` | 无数据库变化时无需更新 |

`docs/dev/技术栈简明说明.md` 当前包含 `/api/auth/register`，实施关闭接口时必须同步删除或标记停用。

## 风险与控制

| 风险 | 控制措施 |
|---|---|
| 外部未知客户端仍调用注册接口 | 发布前检查网关/访问日志；当前仓库客户端已确认无调用 |
| 更新 DTO 收口影响旧调用方 | 对照前端 API、接口日志和文档；保持允许字段名称不变 |
| 默认用户密码不合规导致导入失败 | 导入前一次性校验并给出安全配置提示 |
| 预检通过后发生并发重复 | 写入事务开始后再次批量检查，并依赖数据库唯一约束最终兜底 |
| 大文件全量预检占用内存 | 保持 10MB 限制并增加最大行数；超过限制在写入前拒绝 |
| 原部分成功语义影响使用习惯 | UI 明确展示全量失败原因，并一次返回全部可发现错误 |
| 操作日志意外记录 MultipartFile 或敏感数据 | 导入日志关闭原始参数记录，仅手工记录安全统计和错误码摘要 |
| 通知传播异常原文 | 通知仅由安全结果对象构建，不直接使用异常 `message` |
| 前端提示治理造成错误无提示 | 保留全局默认提示；本地模式必须调用统一辅助函数 |
| 约束诊断再次泄露数据库内容 | 只输出白名单约束标识和异常类型；禁止输出原消息 |
| 事务回滚测试依赖模拟异常不真实 | 使用 Spring 事务代理下的集成测试和受控故障注入 |

## 回滚方案

本次无数据库变更，回滚以应用代码为主：

1. 保留上一可用应用版本或镜像。
2. 若关闭注册接口影响已确认业务，先停用调用方，不得直接恢复旧弱密码注册逻辑；应走独立注册功能方案。
3. 若更新 DTO 影响旧客户端，可临时恢复旧字段兼容读取，但密码、角色和锁定字段仍必须忽略，不得恢复越权写入。
4. 若前端错误提示协议出现无提示问题，可回退对应页面迁移，保留 `ApiError` 标记能力。
5. 若全量导入新流程出现问题，应临时停用用户导入入口；不得回退到边读边写、允许部分成功的旧实现。
6. 回滚后重新执行登录、用户创建、更新、导入、改密和权限隔离回归。

## Verification

整改完成后记录：

```text
公开注册入口：已关闭
已审查密码编码入口数量：5 类（管理员新增、管理员重置、个人改密、用户导入、初始化用户）
允许的密码编码入口：均复用 PasswordPolicyValidator；初始化用户使用受控配置密码
用户更新受控 DTO：通过，未知或越权字段返回 HTTP 400
Excel 模板和表头预检：通过
Excel 文件内重复预检：通过
Excel 数据库重复预检：通过
Excel 导入密码策略：通过
Excel 任意错误零写入：通过
Excel 整批事务回滚：通过，真实 Spring 事务唯一约束故障测试已覆盖
Excel 批量查询无 N+1：通过，用户名和工号均使用 IN 批量查询
IMPORT 成功日志 200：通过
IMPORT 预检失败日志 400：通过
IMPORT 并发冲突日志 409：通过
IMPORT 系统失败日志 500：通过
IMPORT 日志脱敏：通过
导入通知安全摘要：通过
Repository 批量查询测试：通过，由预检服务测试验证批量查询调用
Entity/Flyway/init.sql/数据字典一致性：通过，本次无 Entity 和数据库结构变更
角色写入失败事务回滚：通过，用户与角色写入处于同一事务
用户管理重复 Toast 回归：通过，导入请求关闭全局 Toast 并由页面统一提示
完整性诊断日志脱敏：通过
后端测试：`mvn test` 通过
前端构建：`npm run build` 通过
数据库变更：无
```

用户导入验证必须使用唯一批次测试用户名，并在失败后执行只读查询，确认本批所有用户名在
`sys_user` 和 `sys_user_role` 中均无残留；成功时则确认全部用户和角色关联同时存在。

## 完成标准

- `/api/auth/register` 不再是公开且可执行的用户创建入口。
- 用户更新 API 使用受控 DTO，不能修改密码、角色和锁定状态。
- 创建、导入、管理员重置和个人改密统一执行 `PasswordPolicyValidator`。
- 用户导入在写入前完成模板、文件结构、字段、枚举、密码、文件内重复和数据库重复全量预检。
- 任意导入异常都返回完整安全错误列表，且本批写入数量为 0。
- 全量预检通过后，用户和初始角色在单一事务中整批写入，任意失败全部回滚。
- 默认导入密码不合规时不会创建弱密码账户。
- 用户导入成功、预检失败、并发冲突和系统异常均记录 IMPORT 操作日志及真实响应码。
- IMPORT 操作日志和导入通知只包含安全统计与错误码摘要，不包含上传文件或个人敏感信息。
- 批量用户名和工号 Repository 查询有自动化测试，并确认无逐行 N+1 查询。
- 用户相关 Entity、Flyway、`init.sql` 和数据字典保持一致。
- 用户管理页面和全局拦截器对同一失败请求最多提示一次。
- 数据完整性异常日志能输出安全约束标识，不泄露数据库异常原文。
- 账号关联密码规则和角色写入失败事务回滚均有自动化测试。
- 前后端 API 契约、权限边界和文档保持一致。
- `mvn test`、`npm run build`、Flyway validate 和关键人工回归全部通过。
- 正式文档更新完成，本方案移入 `docs/plan/done/`。
