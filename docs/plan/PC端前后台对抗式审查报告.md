# PC端前后台对抗式审查报告

审查日期：2026-06-30  
审查范围：`backend/src/main/java/com/pricemanagement`、`backend/src/main/resources`、`frontend/src`、`frontend/package.json`  
审查方式：以“接口会被错误调用、权限会被绕过、数据会被异常输入污染、页面会在真实 PC 视口反复使用”为假设进行对抗式静态审查，并补充构建/测试验证。

## 验证结果

- 后端：在 `backend` 目录执行 `mvn -q test`，测试通过。
- PC 前端：在 `frontend` 目录执行 `npm run build`，构建通过。
- 前端构建告警：`installCanvasRenderer` 产物约 521 kB，Vite/Rolldown 提示部分 chunk 超过 500 kB；UnoCSS 与自动导入插件耗时占比较高，属于性能优化信号，不是阻断缺陷。

## 高优先级问题

### P0-1 基础资料 status 参数被解析但未生效

定位：

- `backend/src/main/java/com/pricemanagement/controller/ProductCategoryController.java:28-35`
- `backend/src/main/java/com/pricemanagement/controller/OriginController.java:24-30`
- `backend/src/main/java/com/pricemanagement/controller/CustomerController.java:24-30`
- 前端调用：`frontend/src/api/categories.ts`、`frontend/src/api/origins.ts`、`frontend/src/api/customers.ts`

问题：

PC 端 API 支持 `status` 参数，但后端只要 `status` 是合法枚举，就统一调用 `getActiveCategories()` / `getActiveOrigins()` / `getActiveCustomers()`。因此 `status=INACTIVE` 也会返回启用数据，`status` 参数语义失真。

风险：

- 管理员以为自己在查看/维护停用数据，实际拿到的是启用数据。
- 前端页面、下拉选项、统计口径可能出现“看起来成功、结果错误”的静默错误。
- 外部 API 的基础资料控制器也存在同类模式，需一并核对。

建议：

- Service 层补充 `getCategoriesByStatus(CommonStatus status)`、`getOriginsByStatus(...)`、`getCustomersByStatus(...)`，或直接复用 Repository 的 `findByStatusOrderBySortOrderAsc(status)`。
- Controller 中使用解析后的 `categoryStatus` / `originStatus` / `customerStatus`，非法 status 返回 400，不要吞掉后回退到全量。
- 增加 Controller 或 Service 单测，覆盖 `ACTIVE`、`INACTIVE`、非法 status 三类输入。

### P0-2 后端权限边界依赖前端路由，部分接口仅要求登录

定位：

- `backend/src/main/java/com/pricemanagement/controller/PermissionController.java:14` 下所有 GET 接口无 `@PreAuthorize`
- `backend/src/main/java/com/pricemanagement/controller/DepartmentController.java:27`、`:36`、`:45` 读接口无 `@PreAuthorize`
- `backend/src/main/java/com/pricemanagement/controller/HomeController.java:31`、`:43`、`:55`、`:67` 无方法级角色约束
- 全局配置：`SecurityConfig` 只保证非公开接口 authenticated，方法级权限由注解决定

问题：

PC 路由中部门、权限、通知等页面有 `adminOnly` / `editorOnly` 限制，但后端部分接口没有等价注解。攻击者不需要打开页面，只要有任意有效登录态即可直接请求这些接口。

风险：

- VIEWER 可枚举权限树、部门详情等内部结构。
- 前端菜单隐藏不等于后端授权，后续新增页面或脚本调用时容易扩大越权面。

建议：

- 权限树接口建议至少 `hasRole('ADMIN')`；如运行期按钮权限需要普通用户读取，应新增 `/api/auth/profile` 权限摘要字段或只返回当前用户权限，不返回完整权限树。
- 部门读接口按业务定级：若仅用户管理需要，设为 ADMIN；若全员资料展示需要，返回脱敏树并明确文档契约。
- 首页接口补齐 `hasAnyRole('ADMIN','EDITOR','VIEWER')`，保持与 `/api/home/product-order` 一致。
- 增加 MockMvc 权限回归测试：ADMIN、EDITOR、VIEWER 分别访问关键接口，断言 200/403。

### P0-3 变更类接口操作日志覆盖不完整

定位示例：

- `CustomerController.java:46`、`:57`、`:69`
- `OriginController.java:46`、`:57`、`:69`
- `ProductCategoryController.java:51`、`:62`、`:74`
- `MenuController.java:48`、`:54`、`:60`、`:67`、`:74`
- `ApprovalController.java` 多个工作流与审批变更接口
- `ProfileController.java:27`、`:37`、`:62`、`:68`、`:74`、`:97`
- `SysDictController.java:56`、`:67`、`:78`、`:90`

问题：

项目规范要求所有数据变更操作记录操作日志，但当前控制器存在三种实现状态：`@OperationLog`、手动 `OperationLogHelper`、完全无记录。基础资料、菜单、审批流、字典、个人资料等变更接口覆盖不一致。

风险：

- 高风险变更无法追责，例如菜单权限调整、字典项删除、审批流变更。
- 审计页面数据不完整，管理端“已记录所有操作”的认知不可信。

建议：

- 先补齐高风险接口：菜单、字典、审批流、基础资料删除/状态切换。
- 统一采用 `@OperationLog`，只有需要细粒度失败原因时再用 `OperationLogHelper`。
- 对敏感参数接口设置 `logParams=false` 或脱敏，避免密码、token、密钥、微信配置进入日志。

## 中优先级问题

### P1-1 Token 存储在 localStorage，XSS 后可长期接管账号

定位：

- `frontend/src/store/useUserStore.ts:31`、`:78-79`、`:138-139`
- `frontend/src/api/profile.ts:15`

问题：

Access Token 与 Refresh Token 均存储在 `localStorage`。一旦页面存在 XSS、第三方依赖污染或浏览器插件读取，本地长期刷新令牌会被直接窃取。

建议：

- 中短期：缩短 access token 有效期，refresh token 旋转并记录设备指纹/异常 IP；对退出、改密、锁定用户做强制失效。
- 中期：改为 HttpOnly + Secure + SameSite Cookie 承载 refresh token，access token 仅内存保存。
- 配套：在前端引入 CSP、安全响应头与依赖审计，降低 XSS 成功率。

### P1-2 生产配置默认值混入环境绑定

定位：

- `backend/src/main/java/com/pricemanagement/config/SecurityConfig.java:136-160`
- `backend/src/main/resources/application.yml:48`

问题：

当 `CORS_ALLOWED_ORIGINS` 未配置时，代码内置了多组生产/公网来源；Redis 默认主机为 `10.7.5.175`。这会让新环境在“未显式配置”的情况下继承历史生产/内网假设。

风险：

- 部署到新环境时跨域策略过宽或指向错误基础设施。
- 配置漂移难以审计，安全边界分散在代码与配置文件中。

建议：

- CORS 默认仅允许 localhost，本地以 profile 或 `.env` 扩展；生产必须显式配置 `CORS_ALLOWED_ORIGINS`。
- Redis 默认改为 `localhost` 或无默认并在启动校验中提示。
- 把生产域名、IP 从代码迁移到部署配置和运维文档。

### P1-3 前端权限与数据来源存在历史残留

定位：

- `frontend/src/views/UserManagement.vue:517`
- `frontend/src/views/Approval.vue:377`

问题：

用户管理页在组件内使用 `window.location.href = '/#/home'` 做二次跳转，但项目路由是 `createWebHistory`，hash 路径残留不一致。审批页读取 `localStorage.getItem('user')`，而当前 Pinia store 并未持久化 `user`。

风险：

- 权限跳转路径在 history 模式下不可预测。
- 审批申请人等上下文可能拿不到当前用户，导致功能行为依赖陈旧代码。

建议：

- 所有页面内跳转统一使用 `router.push` / `router.replace`。
- 当前用户信息统一来自 `useUserStore()`，禁止页面直接读取历史 localStorage user。

### P1-4 PC 表格自适应规范覆盖不均

定位：

- `frontend/src/views/UserManagement.vue:50`、`:53`、`:743`
- 对照较好实现：`frontend/src/views/Products.vue:291-316`

问题：

产品列表已实现基于容器高度的自适应 page size，但用户管理仍使用固定 `20/30/50/100/150` 条/页。项目规范要求 PC 表格默认随浏览器高度自适应，并同步影响分页请求。

风险：

- 大屏浪费空间，小屏出现页面滚动与表格滚动叠加。
- 不同管理页交互模型不一致，用户需要重新适应。

建议：

- 抽取 `useAdaptivePageSize(tableShellRef, options)` composable，在用户、日志、API 调用日志等分页表格复用。
- 保留手动 page size，但增加“自适应”模式并作为 PC 默认值。

### P1-5 大体积图表依赖进入独立大 chunk

定位：

- `npm run build` 输出：`installCanvasRenderer-*.js` 约 521 kB，触发 chunk 告警。

问题：

PC 首页、价格查询、预算、详情等页面都依赖图表，当前构建可用，但首访或弱网环境可能被大图表 chunk 拖慢。

建议：

- 对图表页面继续保持路由级懒加载，并进一步拆分 ECharts renderer/chart 类型注册。
- 非首屏图表使用可见区懒加载。
- 设定 bundle budget：单 chunk 超过 500 kB 需要说明或拆分。

## 低优先级与规范问题

### P2-1 字典服务规范仍有绕过点

定位示例：

- `frontend/src/views/ScheduledTasks.vue:187`
- `frontend/src/views/Notifications.vue:1532`、`:1549`、`:1899`
- `frontend/src/views/Approval.vue:185-193` 状态样式仍为本地 map

问题：

多数页面已调用 `loadAllDicts()` 并使用 `getDictValue()`，但仍有若干状态展示、按钮文案、提示语直接写死“启用/停用/已启用/未启用”。其中按钮命令文案可以硬编码，但状态值展示应优先走字典。

建议：

- 状态展示统一改为 `getDictValue(category, key)`。
- 状态样式可以继续本地映射，但颜色优先来自字典 `extraValue` 或统一 token。

### P2-2 原生 confirm 与桌面交互风格不一致

定位：

- `frontend/src/views/Notifications.vue:546`、`:563`、`:929`

问题：

通知管理页面使用 `window.confirm` 承载发布、回滚、真实测试投递等高风险操作。原生弹窗不可统一样式、不可扩展风险详情、不可做二次输入确认。

建议：

- 使用项目内统一确认弹窗组件或 Vant Dialog。
- 高风险操作展示影响范围，例如“会停用旧模板”“会消耗一次订阅授权”。

### P2-3 前端错误日志在生产环境输出较多

定位：

- 多个页面存在 `console.error`，`frontend/src/utils/http.ts` 对慢请求使用 `console.debug`

问题：

多数错误日志便于开发，但生产环境若无统一日志策略，会造成控制台噪声，也可能输出异常响应细节。

建议：

- 增加 `logger` 工具，按 `import.meta.env.DEV` 与采样策略控制输出。
- 用户可感知错误交给 toast/dialog，诊断日志交给监控或开发环境。

## 建议整改路线

1. 先修 `status` 参数失效与后端漏权限注解，并补单测；这是最容易造成错误数据和越权读取的部分。
2. 补齐操作日志覆盖，尤其是菜单、字典、审批流、基础资料删除/状态变更。
3. 清理前端历史残留：`/#/home`、`localStorage.user`、状态展示硬编码。
4. 抽取 PC 表格自适应分页能力，先落到用户管理、操作日志、API 调用日志。
5. 做生产配置收口：CORS、Redis 默认值、Token 存储与安全响应头。
6. 做构建性能专项：ECharts chunk 拆分、图表可见区懒加载、bundle budget。

## 建议新增验证清单

- `GET /api/categories?status=INACTIVE` 返回停用分类，`status=BAD` 返回 400。
- VIEWER 请求 `/api/permissions/tree`、`/api/departments/tree` 的结果符合预期权限边界。
- 菜单、字典、审批流、客户/产地/分类变更后，`operation_log` 能查到对应记录。
- 用户管理页在 1366x768、1920x1080、2560x1440 三档视口下自动调整分页 size。
- `npm run build` 保持通过；单 chunk 超过 500 kB 时输出说明或拆分计划。
- `mvn test` 保持通过，并新增权限/状态筛选回归测试。

