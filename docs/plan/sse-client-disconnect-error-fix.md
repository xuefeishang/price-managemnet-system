# SSE 客户端断连后台报错修复方案

制定日期：2026-07-04  
关联历史方案：[async-timeout-sse-error-fix.md](async-timeout-sse-error-fix.md)  
关联接口：`GET /api/notifications/events`

> 存放说明：AGENTS.md 的历史规范写明 Plan 文件放在根目录 `plan/`，但当前仓库实际规划文档已迁移并集中维护在 `docs/plan/`，且本次用户明确要求落在 `/docs/plan`。本文件按当前仓库事实与用户要求放置，后续如进行文档规范归一，应同步修订 AGENTS.md 中的 Plan 存放位置说明。

## Context

本次后台日志显示，PC 端通知 SSE 在客户端断开后被后端记录为 ERROR，并触发二次异常：

- 主异常：`AsyncRequestNotUsableException: Servlet container error notification for disconnected client`
- 根因：`java.io.IOException: 你的主机中的软件中止了一个已建立的连接。`
- 触发点：`NotificationRealtimeService.send()` 在 `subscribe()` 中发送 `connected` 事件
- 二次异常：`HttpMessageNotWritableException: No converter for Result with preset Content-Type 'text/event-stream'`

第一性原理判断：

1. SSE 是长连接，客户端刷新、关闭页面、切换隐藏状态或前端主动 `AbortController.abort()` 都属于正常生命周期。
2. `text/event-stream` 一旦建立，服务端不能再尝试写入 `Result<T>` JSON 错误体。
3. 单个 SSE 连接发送失败只应清理该连接，不能污染全局 500 日志，也不能影响通知轮询兜底。
4. 通知未读数的事实源仍是 REST 查询和数据库，SSE 只是轻事件加速通道。

已有 `async-timeout-sse-error-fix.md` 主要解决 60 秒空闲超时产生的 `AsyncRequestTimeoutException` 日志噪声；当前问题是 Spring 7 / Tomcat 11 在客户端主动断开时抛出的 `AsyncRequestNotUsableException`，属于同一类异步响应生命周期问题的缺口补丁。

## 实现方案

### 1. 后端异常处理收口

在 `GlobalExceptionHandler` 中补齐异步响应生命周期异常处理：

- 增加专门的 `@ExceptionHandler` 方法覆盖：
  - `AsyncRequestTimeoutException`
  - `AsyncRequestNotUsableException`
- 方法返回 `void`，仅记录 `debug` 日志，不返回 `Result`，不标记业务 500。
- 保留现有 `HttpMessageNotWritableException` 兜底静默逻辑，用于响应已提交后的二次写入保护。
- 普通业务异常仍走现有 `Result.error(...)` 处理，不扩大静默范围。
- 验收口径以日志级别和异常链路为准：允许 debug 记录异常类名，禁止 ERROR/WARN 级别记录正常断连，禁止触发 JSON converter 二次异常。

推荐行为：

```java
@ExceptionHandler({
        AsyncRequestTimeoutException.class,
        AsyncRequestNotUsableException.class
})
public void handleAsyncRequestLifecycleException(Exception ex, HttpServletRequest request) {
    log.debug("异步请求已结束或客户端已断开: uri={}, error={}",
            request.getRequestURI(), ex.getClass().getSimpleName());
}
```

### 2. SSE 连接发送边界

调整 `NotificationRealtimeService` 的连接发送策略：

- `subscribe()` 创建新 emitter 后，`connected` 事件只发送给当前新连接。
- 不通过用户连接池广播 `connected`，避免新订阅时顺手扫描并触发旧断连。
- 提取单连接发送 helper，发送失败时只清理对应 emitter。
- 捕获范围限定为 `IOException | IllegalStateException`：
  - `AsyncRequestNotUsableException` 继承自 `IOException`，会被覆盖。
  - 不吞掉未知 `RuntimeException`，避免隐藏序列化、编码或程序错误。

推荐结构：

```java
private boolean sendToEmitter(Long userId, SseEmitter emitter, NotificationSseEventDTO event) {
    try {
        emitter.send(SseEmitter.event()
                .name(event.getEventType())
                .data(event));
        return true;
    } catch (IOException | IllegalStateException ex) {
        log.debug("SSE 连接发送失败，清理连接: userId={}, eventType={}, error={}",
                userId, event.getEventType(), ex.getClass().getSimpleName());
        return false;
    }
}
```

### 3. 接口契约显式化

在 `NotificationController.events()` 上显式声明 SSE 响应类型：

```java
@GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
```

保持以下契约不变：

- 路径仍为 `/api/notifications/events`
- 鉴权仍要求已登录用户角色
- 事件名仍为 `connected`、`unreadCountChanged`、`newNotification`
- 前端断开后仍回退到现有轮询逻辑

### 4. 文档同步边界

本次不涉及数据库、Entity、Repository、Flyway、数据字典、外部 API 或前端交互改版。

实施代码后需要同步：

- `docs/VERSIONS.md`：新增一条客户端断开 SSE 后端日志噪声修复记录。
- `docs/dev/design/architecture.md`：将 SSE 生命周期说明补充为“空闲超时和客户端主动断开均按正常连接生命周期静默处理”。

无需更新：

- `docs/dev/api/internal.md`：接口路径、参数、事件结构均不变。
- `docs/dev/design/api-design.md`：API 设计总览无新增端点、参数或响应结构变更。
- `docs/dev/design/database.md`：无数据库变更。
- 数据字典文档：无字典变更。

## 关键参考文件

| 文件 | 用途 |
|------|------|
| `backend/src/main/java/com/pricemanagement/config/GlobalExceptionHandler.java` | 全局异常处理与 SSE 二次写入兜底 |
| `backend/src/main/java/com/pricemanagement/service/NotificationRealtimeService.java` | SSE emitter 连接池与事件发送 |
| `backend/src/main/java/com/pricemanagement/controller/NotificationController.java` | `/api/notifications/events` 端点 |
| `frontend/src/components/Layout.vue` | PC 端通知 SSE 连接、主动 abort 与轮询兜底 |
| `docs/plan/async-timeout-sse-error-fix.md` | 历史 SSE 空闲超时修复方案 |

## 实现步骤

### 步骤 1：补齐异步生命周期异常处理

1. 在 `GlobalExceptionHandler` 引入 `AsyncRequestNotUsableException`。
2. 新增独立 handler，覆盖 `AsyncRequestTimeoutException` 和 `AsyncRequestNotUsableException`。
3. handler 返回 `void`，只记录 debug。
4. 保留原 `handleGenericException` 中的 `HttpMessageNotWritableException` 兜底。
5. 移除或避免在通用异常分支中把上述异步生命周期异常记录为 ERROR。

### 步骤 2：收窄 SSE 发送失败影响范围

1. 在 `NotificationRealtimeService` 中新增 `sendToEmitter(...)`。
2. `subscribe()` 中新增 emitter 后，只对该 emitter 发送 `connected`。
3. `send(...)` 广播 `unreadCountChanged` / `newNotification` 时复用 helper。
4. helper 返回失败时调用 `remove(userId, emitter)`。
5. 不改变 `SSE_TIMEOUT_MS` 和连接池结构。

### 步骤 3：显式声明 SSE Content-Type

1. 在 `NotificationController.events()` 的 `@GetMapping` 增加 `produces = MediaType.TEXT_EVENT_STREAM_VALUE`。
2. 引入 `org.springframework.http.MediaType`。
3. 不改变权限注解和方法返回类型。

### 步骤 4：补测试

1. 新增或更新 `GlobalExceptionHandler` 单元测试：
   - `AsyncRequestTimeoutException` 不抛出、不返回 `Result`。
   - `AsyncRequestNotUsableException` 不抛出、不返回 `Result`。
2. 新增 MVC 异常解析验证：
   - 构造最小测试控制器或直接通过 `ExceptionHandlerExceptionResolver` 触发异步生命周期异常。
   - 断言异常命中专用 handler，不进入 `handleGenericException`。
   - 断言不会产生 `Result` JSON 写回，也不会触发 `HttpMessageNotWritableException`。
3. 新增或更新 `NotificationRealtimeService` 单元测试：
   - 订阅时 `connected` 只发送给当前连接。
   - 广播时单个失败连接被清理，不影响同一用户其它连接。
4. 若单元构造 `SseEmitter` 发送失败成本较高，可用继承测试桩或反射读取连接池作为最小验证手段。
5. 日志验收测试如使用 `ListAppender` 或等价方式，仅断言无 ERROR/WARN 级别正常断连日志；不禁止 debug 中出现异常类名。

### 步骤 5：文档与版本记录

1. 更新 `docs/VERSIONS.md`，记录本次补丁。
2. 更新 `docs/dev/design/architecture.md` 的 SSE 生命周期说明。
3. 不更新 API 与数据库文档，避免制造伪变更。

## Verification

### 自动化验证

运行后端定向测试：

```powershell
mvn test -Dtest=GlobalExceptionHandlerTests,NotificationRealtimeServiceTests,NotificationSseExceptionResolverTests
```

若测试类命名与现有项目习惯不同，以实际新增测试类名为准。

### 手工复现验证

1. 启动后端与 PC 前端。
2. 登录 PC 端，确保全局布局已连接 `/api/notifications/events`。
3. 执行以下操作：
   - 刷新浏览器页面。
   - 关闭当前标签页。
   - 切换浏览器标签导致页面隐藏。
   - 重新打开页面触发新的 SSE 连接。
4. 后端日志验收：
   - ERROR/WARN 级别不得出现 `Unexpected error occurred`。
   - ERROR/WARN 级别不得出现 `AsyncRequestNotUsableException` 或正常客户端断连堆栈。
   - 任意级别不得出现 `No converter for Result with preset Content-Type 'text/event-stream'`。
   - debug 级别允许记录一次“异步请求已结束或客户端已断开”类摘要日志，但不打印完整堆栈。
5. 通知功能仍需满足：
   - 未读数可正常加载。
   - 新通知可通过 SSE 刷新未读数。
   - SSE 断开后前端继续使用轮询兜底。

### 回归边界

- 产品、价格、字典、审批、通知管理页面不应出现 API 契约变化。
- `/api/notifications/unread-count`、`/api/notifications/my`、读/归档接口行为不变。
- 服务端 ERROR 日志仍应保留真正业务异常，不能因为本补丁静默所有异常。

## 验收标准

- 客户端主动断开 SSE 不再产生后台 ERROR/WARN 级联报错。
- `GlobalExceptionHandler` 不再尝试向 `text/event-stream` 响应写入 `Result<T>`。
- 单个断开的 emitter 被清理，不影响同一用户其它连接。
- SSE 失败不影响通知未读数 REST 查询和轮询兜底。
- 后端定向测试通过，手工复现验证通过，且 MVC 异常解析验证证明异步生命周期异常不会进入通用 500 分支。
