# SSE 长连接修复与架构升级方案

## 背景

价格系统目前使用 SSE 长连接推送通知。每天价格更新一次，业务上不需要真正的实时性，但 SSE 改造为实时通知中心后，可承载未来业务扩展（库存预警、订单状态、生产监控、紧急审批推送等）。

**当前问题**：
- 修改密码后 60 秒，SSE 连接空闲超时
- `AsyncRequestTimeoutException` 被 `GlobalExceptionHandler` 拦截
- 试图返回 JSON Result，但 SSE 响应 `Content-Type: text/event-stream` 没有对应 converter
- 二次抛 `HttpMessageNotWritableException`，日志噪音大

**目标**：
- 修复当前报错
- 抽离实时推送网关，承载未来多业务模块
- 提供完整、统一、可扩展的架构

---

## 架构设计

### 总体架构

```
┌────────────────────────────────────────────────────────────┐
│  业务模块层（当前与未来）                                     │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐         │
│  │ 价格模块 │ │ 库存模块 │ │ 订单模块 │ │ 审批模块 │  未来  │
│  └─────┬────┘ └─────┬────┘ └─────┬────┘ └─────┬────┘         │
│        │ 领域事件    │            │            │             │
└────────┼────────────┼────────────┼────────────┼─────────────┘
         │            │            │            │
         ▼            ▼            ▼            ▼
┌────────────────────────────────────────────────────────────┐
│  事件总线 (Spring ApplicationEvent)                          │
│  - 业务事件发布                                              │
│  - 事件订阅分发                                              │
└────────────────────────┬───────────────────────────────────┘
                         │
                         ▼
┌────────────────────────────────────────────────────────────┐
│  实时推送网关 (RealtimeGateway)                              │
│  - 统一管理 SseEmitter 连接池                                │
│  - 事件路由：根据 topic 分发给订阅者                          │
│  - 心跳保活：30 秒一次                                       │
│  - 异常处理：异步异常静默                                    │
│  - 优雅降级：连接断开自动清理                                │
│  - 鉴权集成：从 SecurityContext 提取 userId                   │
└────────────────────────┬───────────────────────────────────┘
                         │
                         ▼
┌────────────────────────────────────────────────────────────┐
│  浏览器 SSE 连接                                             │
│  - 自动重连机制                                              │
│  - 离线时轮询补偿                                            │
└────────────────────────────────────────────────────────────┘
```

### 核心组件

| 组件 | 职责 | 现有/新建 |
|------|------|----------|
| **RealtimeGateway** | 统一管理 SSE 连接与事件分发 | 新建 |
| **SseConnection** | 单个 SSE 连接封装（重连、异常、清理） | 新建 |
| **SseTopic** | 事件主题枚举 | 新建 |
| **SseEvent** | 事件统一格式 | 新建 |
| **RealtimeEventListener** | 监听 Spring 事件并推送 | 新建 |
| **NotificationRealtimeService** | 现有通知 SSE 业务 | 重构为网关客户端 |
| **GlobalExceptionHandler** | 全局异常处理 | 增强异步异常支持 |
| **Spring Security Config** | 安全配置 | 屏蔽 DevMgmt 等异常路径 |

### 关键设计点

**1. 事件统一格式**

```java
public record SseEvent(
    String topic,           // 主题（如 "price.changed", "inventory.alert"）
    String eventType,       // 事件类型（如 "created", "updated", "alert"）
    Object data,            // 业务数据
    Long userId,            // 目标用户
    LocalDateTime timestamp
) {}
```

**2. Topic 设计**

```java
public class SseTopics {
    // 价格模块
    public static final String PRICE_CHANGED = "price.changed";
    public static final String PRICE_PUBLISHED = "price.published";
    
    // 通知模块
    public static final String NOTIFICATION_NEW = "notification.new";
    public static final String NOTIFICATION_UNREAD = "notification.unread";
    
    // 未来扩展（预留）
    // public static final String INVENTORY_ALERT = "inventory.alert";
    // public static final String ORDER_STATUS = "order.status";
    // public static final String APPROVAL_URGENT = "approval.urgent";
}
```

**3. 网关核心实现**

```java
@Component
public class RealtimeGateway {
    
    // 用户连接池
    private final Map<Long, Set<SseConnection>> connections = new ConcurrentHashMap<>();
    
    // 事件监听
    @EventListener
    public void onDomainEvent(SseEvent event) {
        dispatch(event);
    }
    
    // 业务方法：发布事件
    public void publish(SseEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
    
    // 订阅连接
    public SseConnection subscribe(Long userId, Set<String> topics) {
        SseConnection conn = new SseConnection(userId, topics, this);
        connections.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(conn);
        conn.start();
        return conn;
    }
    
    // 分发事件
    private void dispatch(SseEvent event) {
        Set<SseConnection> targets = connections.get(event.userId());
        if (targets == null) return;
        
        for (SseConnection conn : targets) {
            if (conn.isSubscribedTo(event.topic())) {
                conn.send(event);
            }
        }
    }
}
```

**4. 连接封装（含心跳）**

```java
public class SseConnection {
    private final SseEmitter emitter;
    private final Set<String> topics;
    private final Runnable onCleanup;
    
    public void start() {
        // 30 秒心跳
        ScheduledFuture<?> heartbeat = scheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event()
                    .name("heartbeat")
                    .data(System.currentTimeMillis()));
            } catch (Exception e) {
                cleanup();
            }
        }, 30, 30, TimeUnit.SECONDS);
        
        emitter.onCompletion(this::cleanup);
        emitter.onTimeout(this::cleanup);
        emitter.onError(e -> cleanup());
    }
    
    public void send(SseEvent event) {
        try {
            emitter.send(SseEmitter.event()
                .name(event.topic())
                .data(objectMapper.writeValueAsString(event)));
        } catch (Exception e) {
            cleanup();
        }
    }
    
    public boolean isSubscribedTo(String topic) {
        return topics.contains(topic) || topics.contains("*");
    }
    
    private void cleanup() {
        // 清理 emitter、取消心跳任务、从连接池移除
    }
}
```

**5. 异步异常静默处理**

```java
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AsyncExceptionHandler {
    
    @ExceptionHandler({
        AsyncRequestTimeoutException.class,
        AsyncRequestNotUsableException.class
    })
    public void handleAsyncTimeout(HttpServletRequest request, Exception ex) {
        // SSE 异步超时是正常现象，仅记录 debug
        log.debug("SSE 异步超时: {} - {}", 
                  request.getRequestURI(), ex.getMessage());
        // 不返回任何内容，响应已提交
    }
}
```

**6. 鉴权集成**

```java
@GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseConnection subscribe(@AuthenticationPrincipal UserDetails user) {
    Set<String> topics = Set.of(
        SseTopics.PRICE_CHANGED,
        SseTopics.NOTIFICATION_NEW,
        SseTopics.NOTIFICATION_UNREAD
    );
    return realtimeGateway.subscribe(user.getId(), topics);
}
```

**7. 安全加固**

```java
http
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/DevMgmt/**", "/META-INF/**", "/WEB-INF/**").denyAll()
        .anyRequest().authenticated()
    )
```

---

## 实施步骤

### 阶段 0：临时止血（5 分钟）

**目的**：在架构重构前立即停止错误日志噪音

**文件**：`backend/src/main/java/com/pricemanagement/config/GlobalExceptionHandler.java`

```java
@ExceptionHandler(Exception.class)
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public Result<Void> handleGenericException(Exception ex, HttpServletRequest request) {
    // SSE 异步请求超时是正常现象，静默处理
    if (ex instanceof AsyncRequestTimeoutException) {
        log.debug("SSE 异步超时: {}", request.getRequestURI());
        return null;
    }
    if (ex instanceof HttpMessageNotWritableException) {
        log.debug("响应已提交，无法写入错误: {}", ex.getMessage());
        return null;
    }
    log.error("Unexpected error occurred", ex);
    return Result.error(500, "服务器内部错误，请稍后重试");
}
```

**验证**：重启后端 → 复现 60s 超时 → 日志仅 `Async timeout (normal)` debug

---

### 阶段 1：异步异常处理基础设施（0.5h）

**文件**：`backend/src/main/java/com/pricemanagement/handler/AsyncExceptionHandler.java`（新建）

```java
package com.pricemanagement.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import jakarta.servlet.http.HttpServletRequest;

@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AsyncExceptionHandler {
    
    @ExceptionHandler({
        AsyncRequestTimeoutException.class,
        AsyncRequestNotUsableException.class
    })
    public void handleAsyncTimeout(HttpServletRequest request, Exception ex) {
        log.debug("SSE 异步超时: {} - {}", 
                  request.getRequestURI(), ex.getMessage());
    }
}
```

**理由**：独立类比修改 GlobalExceptionHandler 更安全，未来所有异步异常统一处理。

---

### 阶段 2：安全配置加固（0.5h）

**文件**：`backend/src/main/java/com/pricemanagement/config/SecurityConfig.java`

**变更**：
- 在 `securityFilterChain` 链 `authorizeHttpRequests` 中添加：
  ```java
  .requestMatchers("/DevMgmt/**", "/META-INF/**", "/WEB-INF/**").denyAll()
  .requestMatchers("/actuator/**").hasRole("ADMIN")  // 限制 actuator
  ```

**目的**：
- 屏蔽外部恶意扫描请求（WinRM 等）
- 限制 actuator 访问

---

### 阶段 3：SseEvent 与 SseTopics 基础（1h）

**新建文件**：
- `backend/src/main/java/com/pricemanagement/realtime/SseEvent.java`
- `backend/src/main/java/com/pricemanagement/realtime/SseTopics.java`

**SseEvent.java**：
```java
package com.pricemanagement.realtime;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SseEvent implements Serializable {
    private String topic;            // 主题
    private String eventType;        // 事件类型
    private Object data;             // 业务数据
    private Long userId;             // 目标用户
    private LocalDateTime timestamp; // 事件时间
    private String traceId;          // 链路追踪 ID
}
```

**SseTopics.java**：
```java
package com.pricemanagement.realtime;

public final class SseTopics {
    private SseTopics() {}
    
    // 价格模块
    public static final String PRICE_CHANGED = "price.changed";
    public static final String PRICE_PUBLISHED = "price.published";
    
    // 通知模块
    public static final String NOTIFICATION_NEW = "notification.new";
    public static final String NOTIFICATION_UNREAD = "notification.unread";
    public static final String SYSTEM_NOTICE = "system.notice";
}
```

---

### 阶段 4：SseConnection 封装（1.5h）

**新建文件**：`backend/src/main/java/com/pricemanagement/realtime/SseConnection.java`

```java
package com.pricemanagement.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
public class SseConnection {
    
    private static final long HEARTBEAT_INTERVAL_SECONDS = 30;
    private static final long SSE_TIMEOUT_MILLIS = 30 * 60 * 1000L; // 30 分钟
    
    private final Long userId;
    private final Set<String> topics;
    private final ObjectMapper objectMapper;
    private final Runnable onCleanup;
    private final SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
    private final ScheduledExecutorService scheduler = 
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sse-heartbeat-" + userId);
            t.setDaemon(true);
            return t;
        });
    private ScheduledFuture<?> heartbeatTask;
    
    public SseEmitter getEmitter() {
        return emitter;
    }
    
    public void start() {
        // 注册生命周期回调
        emitter.onCompletion(this::cleanup);
        emitter.onTimeout(this::cleanup);
        emitter.onError(t -> cleanup());
        
        // 启动心跳
        heartbeatTask = scheduler.scheduleAtFixedRate(
            this::sendHeartbeat,
            HEARTBEAT_INTERVAL_SECONDS,
            HEARTBEAT_INTERVAL_SECONDS,
            TimeUnit.SECONDS
        );
        
        // 立即发送连接成功事件
        try {
            emitter.send(SseEmitter.event()
                .name("connected")
                .data("{\"userId\":" + userId + "}"));
        } catch (IOException e) {
            cleanup();
        }
    }
    
    public void send(SseEvent event) {
        if (!isSubscribedTo(event.getTopic())) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(event);
            emitter.send(SseEmitter.event()
                .name(event.getTopic())
                .data(json));
        } catch (Exception e) {
            log.debug("SSE 发送失败 userId={} topic={}", userId, event.getTopic());
            cleanup();
        }
    }
    
    public boolean isSubscribedTo(String topic) {
        return topics.contains("*") || topics.contains(topic);
    }
    
    private void sendHeartbeat() {
        try {
            emitter.send(SseEmitter.event()
                .name("heartbeat")
                .data(String.valueOf(System.currentTimeMillis())));
        } catch (Exception e) {
            log.debug("SSE 心跳失败 userId={}", userId);
            cleanup();
        }
    }
    
    private void cleanup() {
        try {
            if (heartbeatTask != null) {
                heartbeatTask.cancel(false);
            }
            scheduler.shutdown();
            emitter.complete();
        } catch (Exception ignored) {}
        onCleanup.run();
    }
}
```

---

### 阶段 5：RealtimeGateway 核心（2h）

**新建文件**：`backend/src/main/java/com/pricemanagement/realtime/RealtimeGateway.java`

```java
package com.pricemanagement.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeGateway {
    
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    
    // 用户连接池
    private final ConcurrentHashMap<Long, Set<SseConnection>> connections = 
        new ConcurrentHashMap<>();
    
    /**
     * 业务模块调用此方法发布事件
     */
    public void publish(SseEvent event) {
        eventPublisher.publishEvent(event);
    }
    
    /**
     * 监听 Spring 事件总线
     */
    @EventListener
    public void onSseEvent(SseEvent event) {
        Set<SseConnection> userConnections = connections.get(event.getUserId());
        if (userConnections == null || userConnections.isEmpty()) {
            return;
        }
        
        for (SseConnection conn : userConnections) {
            conn.send(event);
        }
    }
    
    /**
     * 用户订阅 SSE
     */
    public SseConnection subscribe(Long userId, Set<String> topics) {
        SseConnection conn = new SseConnection(
            userId,
            topics,
            objectMapper,
            () -> removeConnection(userId, conn)
        );
        connections.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
                   .add(conn);
        conn.start();
        log.info("SSE 订阅建立 userId={} topics={}", userId, topics);
        return conn;
    }
    
    private void removeConnection(Long userId, SseConnection conn) {
        Set<SseConnection> set = connections.get(userId);
        if (set != null) {
            set.remove(conn);
            if (set.isEmpty()) {
                connections.remove(userId);
            }
        }
        log.info("SSE 连接清理 userId={}", userId);
    }
    
    /**
     * 获取在线用户数（监控用）
     */
    public int getOnlineUserCount() {
        return connections.size();
    }
    
    /**
     * 获取连接数（监控用）
     */
    public int getConnectionCount() {
        return connections.values().stream()
            .mapToInt(Set::size)
            .sum();
    }
}
```

---

### 阶段 6：Controller 端点（1h）

**新建文件**：`backend/src/main/java/com/pricemanagement/controller/RealtimeController.java`

```java
package com.pricemanagement.controller;

import com.pricemanagement.realtime.RealtimeGateway;
import com.pricemanagement.realtime.SseConnection;
import com.pricemanagement.realtime.SseTopics;
import com.pricemanagement.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Set;

@RestController
@RequestMapping("/api/realtime")
@RequiredArgsConstructor
public class RealtimeController {
    
    private final RealtimeGateway gateway;
    
    /**
     * 统一 SSE 订阅端点
     * 客户端建立连接后自动接收订阅话题的事件
     */
    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseConnection subscribe() {
        Long userId = SecurityUtils.getCurrentUserId();
        
        // 默认订阅所有话题
        Set<String> topics = Set.of(
            SseTopics.PRICE_CHANGED,
            SseTopics.PRICE_PUBLISHED,
            SseTopics.NOTIFICATION_NEW,
            SseTopics.NOTIFICATION_UNREAD,
            SseTopics.SYSTEM_NOTICE
        );
        
        return gateway.subscribe(userId, topics);
    }
}
```

---

### 阶段 7：业务模块改造（2h）

#### 7.1 通知模块改造

**改造**：`NotificationRealtimeService.java`

**变更**：
- 移除直接的 `SseEmitter` 管理
- 改为通过 `RealtimeGateway` 发布事件
- 业务方法签名保持向后兼容

```java
@Service
@RequiredArgsConstructor
public class NotificationRealtimeService {
    
    private final RealtimeGateway gateway;
    
    public void publishUnreadChanged(Long userId, long unreadCount) {
        SseEvent event = new SseEvent(
            SseTopics.NOTIFICATION_UNREAD,
            "unreadChanged",
            Map.of("unreadCount", unreadCount),
            userId,
            LocalDateTime.now(),
            null
        );
        gateway.publish(event);
    }
    
    public void publishNewNotification(Long userId, Long messageId, 
                                      String notificationType, long unreadCount) {
        SseEvent event = new SseEvent(
            SseTopics.NOTIFICATION_NEW,
            "new",
            Map.of("messageId", messageId, "type", notificationType, 
                   "unreadCount", unreadCount),
            userId,
            LocalDateTime.now(),
            null
        );
        gateway.publish(event);
    }
}
```

#### 7.2 价格模块改造

**新增**：`PriceRealtimePublisher.java`

```java
@Component
@RequiredArgsConstructor
public class PriceRealtimePublisher {
    
    private final RealtimeGateway gateway;
    
    @EventListener
    public void onPriceChanged(PriceChangedEvent event) {
        SseEvent sseEvent = new SseEvent(
            SseTopics.PRICE_CHANGED,
            "changed",
            Map.of("productId", event.getProductId(),
                   "oldPrice", event.getOldPrice(),
                   "newPrice", event.getNewPrice()),
            event.getUserId(),
            LocalDateTime.now(),
            null
        );
        gateway.publish(sseEvent);
    }
    
    @EventListener
    public void onPricePublished(PricePublishedEvent event) {
        SseEvent sseEvent = new SseEvent(
            SseTopics.PRICE_PUBLISHED,
            "published",
            Map.of("batchId", event.getBatchId(),
                   "count", event.getCount()),
            event.getUserId(),
            LocalDateTime.now(),
            null
        );
        gateway.publish(sseEvent);
    }
}
```

---

### 阶段 8：监控端点（1h）

**新建文件**：`backend/src/main/java/com/pricemanagement/controller/RealtimeMonitorController.java`

```java
@RestController
@RequestMapping("/api/admin/realtime")
@RequiredArgsConstructor
public class RealtimeMonitorController {
    
    private final RealtimeGateway gateway;
    
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<RealtimeStats> stats() {
        return Result.success(new RealtimeStats(
            gateway.getOnlineUserCount(),
            gateway.getConnectionCount()
        ));
    }
    
    public record RealtimeStats(int onlineUsers, int totalConnections) {}
}
```

---

### 阶段 9：删除旧 SSE 服务（0.5h）

**删除/重构**：
- `NotificationRealtimeService.java` 中的 SseEmitter 管理代码
- `NotificationController.java` 中的 `/sse` 端点
- `NotificationRealtimeService.subscribe()` 方法

**理由**：功能已被 `RealtimeController` 统一接管

---

### 阶段 10：前端适配（2h）

**改造**：`frontend/src/composables/useNotificationIndicator.ts`

```typescript
// 旧：调用 /api/notifications/sse
// 新：调用 /api/realtime/sse（统一网关）

import { useUserStore } from '@/store/useUserStore'

export function useNotificationIndicator() {
  const userStore = useUserStore()
  const unreadCount = ref(0)
  let eventSource: EventSource | null = null
  let reconnectTimer: number | null = null
  
  const connect = () => {
    if (!userStore.token) return
    
    // 统一网关 SSE
    eventSource = new EventSource(
      '/api/realtime/sse',
      { withCredentials: true }
    )
    
    eventSource.addEventListener('connected', (e) => {
      console.log('SSE connected:', e.data)
    })
    
    eventSource.addEventListener('heartbeat', () => {
      // 心跳，无需处理
    })
    
    eventSource.addEventListener('notification.unread', (e) => {
      const data = JSON.parse(e.data)
      unreadCount.value = data.data.unreadCount
    })
    
    eventSource.addEventListener('notification.new', (e) => {
      // 刷新未读数
      loadUnreadCount()
      showNotification(JSON.parse(e.data))
    })
    
    eventSource.addEventListener('price.changed', (e) => {
      // 价格变更提示（未来扩展）
    })
    
    eventSource.onerror = () => {
      // 自动重连
      eventSource?.close()
      eventSource = null
      reconnectTimer = window.setTimeout(connect, 5000)
    }
  }
  
  const disconnect = () => {
    if (reconnectTimer) clearTimeout(reconnectTimer)
    if (eventSource) {
      eventSource.close()
      eventSource = null
    }
  }
  
  // 登录后连接
  watch(() => userStore.token, (token) => {
    if (token) connect()
    else disconnect()
  })
  
  // 改密时主动断开
  userStore.$onAction(({ name, after }) => {
    if (name === 'changePasswordAction') {
      after(() => disconnect())
    }
  })
  
  onMounted(() => {
    if (userStore.token) connect()
  })
  
  onUnmounted(() => disconnect())
  
  return { unreadCount, connect, disconnect }
}
```

---

## 实施路径

| 阶段 | 任务 | 交付物 | 风险 |
|------|------|--------|------|
| **0** | 临时止血：GlobalExceptionHandler 加 if | 错误日志消失 | 无 |
| **1** | 异步异常独立处理器 | 长期可维护 | 无 |
| **2** | 安全配置加固 | 屏蔽恶意扫描 | 无 |
| **3** | SseEvent + SseTopics 基础类 | 统一事件格式 | 无 |
| **4** | SseConnection 封装（含心跳） | 长连接稳定保活 | 低 |
| **5** | RealtimeGateway 网关核心 | 统一事件分发 | 中 |
| **6** | RealtimeController 端点 | 统一订阅入口 | 低 |
| **7** | 业务模块改造（通知+价格） | 业务接入 | 中 |
| **8** | 监控端点 | 运维可见 | 无 |
| **9** | 删除旧 SSE 业务 | 代码清理 | 低 |
| **10** | 前端适配 | 统一接入 | 低 |

---

## 验收标准

### 功能验收

- [ ] 后端启动后 `/api/realtime/sse` 端点可订阅
- [ ] 用户改密后 60 秒，后端日志仅出现 debug 级别"异步超时"，无 ERROR
- [ ] 不再出现 `HttpMessageNotWritableException`
- [ ] 通知发布后用户 SSE 连接 1 秒内收到
- [ ] 心跳正常（30 秒一次）

### 性能验收

- [ ] 单用户 SSE 连接内存占用 < 1MB
- [ ] 心跳包大小 < 100 字节
- [ ] 1000 并发用户无明显性能下降

### 架构验收

- [ ] 业务模块通过 `RealtimeGateway.publish()` 推送事件
- [ ] 未来新业务模块只需新增 `SseTopics` 常量 + EventListener
- [ ] 无业务模块直接管理 SseEmitter
- [ ] GlobalExceptionHandler 无需任何异步相关特例处理

### 安全验收

- [ ] `/DevMgmt/**` 返回 403
- [ ] `/actuator/**` 仅 ADMIN 访问
- [ ] SSE 连接携带 JWT 鉴权

---

## 未来扩展指引

新增业务模块实时推送的步骤：

1. **在 SseTopics 添加 topic 常量**
   ```java
   public static final String INVENTORY_ALERT = "inventory.alert";
   ```

2. **新增 EventListener**
   ```java
   @EventListener
   public void onInventoryAlert(InventoryAlertEvent event) {
       gateway.publish(new SseEvent(
           SseTopics.INVENTORY_ALERT,
           "alert",
           event.getData(),
           event.getUserId(),
           LocalDateTime.now(),
           null
       ));
   }
   ```

3. **前端订阅**
   ```typescript
   eventSource.addEventListener('inventory.alert', handler)
   ```

**零侵入、零修改 RealtimeGateway 即可接入新业务**。

---

## 风险与回滚

| 风险 | 等级 | 回滚方案 |
|------|------|----------|
| 阶段 0-2 失败 | 无 | Git revert 即可 |
| 阶段 4-6 网关核心失败 | 中 | 保留旧 NotificationRealtimeService，关闭 RealtimeController |
| 阶段 7 业务改造失败 | 中 | 业务可同时调用旧/新，任一可用即可 |
| 阶段 9 删除旧服务 | 低 | 失败时暂不删除，旧/新并行 |

**渐进式发布**：每个阶段独立可部署、可验证、可回滚。

---

## 总结

**核心收益**：
- 修复当前 SSE 错误（5 分钟止血 + 完整架构）
- 抽离实时推送网关，承载未来扩展
- 30 秒心跳保活，长连接稳定
- 异步异常静默处理，日志清洁
- 安全加固，屏蔽恶意扫描

**架构目标**：
业务模块 → 领域事件 → 实时推送网关 → 浏览器 SSE

**未来可扩展场景**（无需重构）：
- 库存预警推送
- 订单状态变更
- 生产设备异常
- 紧急审批通知
- 系统公告实时推送
