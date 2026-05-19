# AI Agent 兼容能力设计方案

> 状态：**待讨论** | 版本：v2.1 | 日期：2026-04-24
>
> v2.1 变更：新增管理员 Chat 全局开关功能（全局/角色级控制 + SSE 实时同步）
>
> v2.0 变更：新增外部 Agent 注册配置、连通性测试、健康监控、异常告警功能

---

## 一、需求分析

### 1.1 核心需求

| 需求 | 说明 |
|------|------|
| AI Chat 窗口 | 用户通过自然语言对话，获取、加工数据库中的业务数据 |
| 角色授权 | AI 只能返回当前用户角色权限范围内的数据 |
| Agent 兼容 | 支持 Open Claw、Hermes 等 Agent 框架的标准协议接入 |
| 安全可控 | AI 查询需审计，敏感操作需确认 |
| **外部 Agent 配置** | 管理员可注册、配置外部 Agent（地址/协议/密钥），测试连通后启用 |
| **健康监控** | 持续监控已启用 Agent 的连通状态，异常时自动告警 |
| **异常告警** | Agent 通讯异常或功能异常时，实时通知管理员 |
| **Chat 全局开关** | 管理员可一键开启/关闭 AI Chat 功能，关闭后所有用户即时失去 Chat 入口和功能 |

### 1.2 兼容的 Agent 协议分析

| 协议/框架 | 说明 | 兼容方式 |
|-----------|------|----------|
| **MCP (Model Context Protocol)** | Anthropic 提出的 Agent 工具调用标准，定义了 Tool、Resource、Prompt 三类能力 | 实现 MCP Server，暴露系统 Tool 定义 |
| **OpenAI Function Calling** | 业界最广泛使用的 Function Calling 协议 | 后端 AI Service 兼容 OpenAI Chat Completions API 格式 |
| **Open Claw** | 开源 Agent 框架，支持 MCP 和 OpenAI 协议 | 通过 MCP Server 兼容 |
| **Hermes** | Agent 框架，支持 Tool Use 和 Streaming | 通过 SSE + Function Calling 兼容 |

**核心结论**：系统需同时提供 **MCP Server** 和 **OpenAI 兼容 API**，即可覆盖主流 Agent 框架。

---

## 二、整体架构

```
┌─────────────────────────────────────────────────────────┐
│                   外部 Agent 集群                        │
│  (Open Claw / Hermes / Claude Desktop / 自定义 Agent)    │
└───────────┬─────────────────────────┬───────────────────┘
            │ MCP Protocol            │ OpenAI API
            ▼                         ▼
┌───────────────────┐    ┌────────────────────────────┐
│   MCP Server      │    │   OpenAI-Compatible API     │
│  (Stdio/SSE)      │    │   /v1/chat/completions      │
└────────┬──────────┘    └──────────┬─────────────────┘
         │                          │
         └──────────┬───────────────┘
                    ▼
┌─────────────────────────────────────────────────────────┐
│                  Agent Gateway Layer                      │
│  ┌──────────────────┐  ┌──────────────────────────┐    │
│  │  Agent Registry  │  │  Health Monitor           │    │
│  │  (注册/配置/启停)  │  │  (心跳检测/状态追踪/告警)  │    │
│  └────────┬─────────┘  └────────────┬─────────────┘    │
│           │                         │                   │
│  ┌────────┴─────────────────────────┴──────────────┐   │
│  │           Agent Proxy (统一路由/负载/降级)        │   │
│  └──────────────────────┬──────────────────────────┘   │
└─────────────────────────┼───────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│                   AI Service Layer                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ LLM Router   │  │ Tool Engine  │  │ Auth Guard   │  │
│  │ (多模型切换)  │  │ (工具调度)    │  │ (角色鉴权)    │  │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  │
│         │                 │                  │          │
│  ┌──────┴─────────────────┴──────────────────┴───────┐  │
│  │              Chat Session Manager                  │  │
│  │         (会话管理 / 上下文窗口 / 审计日志)          │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────┬───────────────────────────────┘
                          │
         ┌────────────────┼────────────────┐
         ▼                ▼                ▼
┌─────────────┐  ┌──────────────┐  ┌─────────────┐
│  内置工具集   │  │  数据查询层   │  │  业务服务层   │
│ (Tools)     │  │ (Repository) │  │ (Service)   │
└─────────────┘  └──────────────┘  └─────────────┘

┌─────────────────────────────────────────────────────────┐
│                     前端 AI Chat                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ ChatPanel    │  │ MessageList  │  │ ToolCallCard │  │
│  │ (浮动面板)    │  │ (消息流)      │  │ (工具调用卡片) │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
│  ┌──────────────┐  ┌──────────────┐                    │
│  │ InputBar     │  │ SessionList  │                    │
│  │ (输入框)      │  │ (历史会话)    │                    │
│  └──────────────┘  └──────────────┘                    │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│              前端 Agent 配置管理（仅 ADMIN）              │
│  ┌──────────────────┐  ┌──────────────────────────┐    │
│  │ AgentConfigPage  │  │ AgentHealthDashboard     │    │
│  │ (注册/编辑/测试)  │  │ (状态面板/告警通知)        │    │
│  └──────────────────┘  └──────────────────────────┘    │
│  ┌──────────────────────────────────────────────────┐  │
│  │ ChatFeatureToggle  (Chat 全局开关，仅 ADMIN)      │  │
│  └──────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

---

## 三、后端设计

### 3.1 新增依赖

```xml
<!-- LLM 调用：Spring AI (统一多模型接入) -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- SSE 支持（Spring WebFlux，仅用其 SSE 能力） -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>

<!-- MCP Server SDK -->
<dependency>
    <groupId>io.modelcontextprotocol</groupId>
    <artifactId>mcp-spring-sdk</artifactId>
    <version>0.5.0</version>
</dependency>
```

> **说明**：Spring AI 是 Spring 官方 AI 框架，提供统一的 LLM 调用抽象，支持 OpenAI、Claude、Ollama（本地模型）等后端切换。MCP SDK 用于实现 MCP Server。

### 3.2 配置项

```yaml
# application.yml 新增
ai:
  # LLM 提供者：openai / claude / ollama（本地模型）
  provider: openai
  openai:
    api-key: ${OPENAI_API_KEY:sk-xxx}
    base-url: ${OPENAI_BASE_URL:https://api.openai.com}
    model: gpt-4o-mini
  claude:
    api-key: ${CLAUDE_API_KEY:}
    model: claude-sonnet-4-20250514
  ollama:
    base-url: ${OLLAMA_BASE_URL:http://localhost:11434}
    model: qwen2.5:7b
  # 功能开关
  chat:
    enabled: true                # 默认值，运行时由数据库 ai_feature_switch 控制
    max-history-turns: 20        # 最大历史对话轮数
    max-tokens: 4096             # 单次回复最大 token
    temperature: 0.3             # 低温度=更精确，适合数据查询
  # 安全
  security:
    audit-enabled: true          # 审计日志
    confirm-destructive: true    # 破坏性操作需确认
  # MCP Server
  mcp:
    enabled: true
    server-name: price-management-mcp
    server-version: 1.0.0
```

### 3.3 核心类设计

#### 3.3.1 包结构

```
com.pricemanagement
├── ai
│   ├── config/
│   │   ├── AiProperties.java          # 配置类
│   │   └── AiAutoConfiguration.java   # 自动配置
│   ├── controller/
│   │   ├── ChatController.java        # Chat API（SSE）
│   │   ├── McpController.java         # MCP SSE 端点
│   │   └── AgentConfigController.java # 外部 Agent 配置管理 API
│   ├── service/
│   │   ├── AiChatService.java         # 核心对话服务
│   │   ├── LlmRouterService.java      # 多模型路由
│   │   ├── ChatSessionService.java    # 会话管理
│   │   ├── ChatFeatureService.java    # Chat 功能开关管理（全局开关/角色开关）
│   │   ├── AgentRegistryService.java  # Agent 注册/配置/启停
│   │   ├── AgentHealthService.java    # Agent 健康监控
│   │   └── AgentAlertService.java     # Agent 异常告警
│   ├── tool/
│   │   ├── ToolDefinition.java        # 工具定义接口
│   │   ├── ToolRegistry.java          # 工具注册中心
│   │   ├── ToolExecutor.java          # 工具执行器（含鉴权）
│   │   └── impl/
│   │       ├── ProductQueryTool.java   # 产品查询工具
│   │       ├── PriceQueryTool.java     # 价格查询工具
│   │       ├── CustomerQueryTool.java  # 客户查询工具
│   │       ├── OriginQueryTool.java    # 产地查询工具
│   │       ├── CategoryQueryTool.java  # 分类查询工具
│   │       ├── DictQueryTool.java      # 字典查询工具
│   │       ├── ApprovalQueryTool.java  # 审批查询工具
│   │       ├── LogQueryTool.java       # 日志查询工具
│   │       ├── UserQueryTool.java      # 用户查询工具（仅ADMIN）
│   │       └── StatsQueryTool.java     # 统计分析工具
│   ├── gateway/
│   │   ├── AgentProxy.java            # Agent 统一代理（路由/降级）
│   │   ├── AgentClientFactory.java    # Agent 客户端工厂
│   │   ├── McpClient.java             # MCP 协议客户端
│   │   └── OpenAiClient.java          # OpenAI 协议客户端
│   ├── monitor/
│   │   ├── HealthChecker.java         # 健康检查器（定时任务）
│   │   ├── HealthStatus.java          # 健康状态模型
│   │   ├── AlertRule.java             # 告警规则定义
│   │   └── AlertNotifier.java         # 告警通知分发
│   ├── mcp/
│   │   ├── McpServerHandler.java       # MCP 协议处理器
│   │   └── McpToolProvider.java        # MCP 工具暴露
│   ├── guard/
│   │   ├── AiAuthGuard.java           # AI 角色鉴权守卫
│   │   └── DataScopeFilter.java       # 数据范围过滤
│   ├── dto/
│   │   ├── ChatRequest.java
│   │   ├── ChatMessage.java
│   │   ├── ChatSession.java
│   │   ├── ToolCallResult.java
│   │   ├── AiAuditLog.java
│   │   ├── ChatFeatureStatus.java    # Chat 功能状态（含全局开关+角色开关）
│   │   ├── AgentConfigDTO.java        # Agent 配置传输对象
│   │   ├── AgentTestResult.java       # 连通性测试结果
│   │   └── AgentHealthReport.java     # 健康报告
│   └── entity/
│       ├── AiChatSession.java         # 会话实体
│       ├── AiChatMessage.java         # 消息实体
│       ├── AiFeatureSwitch.java       # AI 功能开关实体（全局/角色级）
│       ├── ExternalAgent.java         # 外部 Agent 配置实体
│       └── AgentHealthLog.java        # Agent 健康日志实体
```

#### 3.3.2 工具定义规范

每个工具必须声明**所需权限**，执行时由 `AiAuthGuard` 校验：

```java
public interface ToolDefinition {
    /** 工具名称（全局唯一） */
    String name();
    
    /** 工具描述（给 LLM 看，决定 LLM 是否调用） */
    String description();
    
    /** JSON Schema 参数定义 */
    String parameterSchema();
    
    /** 所需权限（至少一个） */
    String[] requiredPermissions();
    
    /** 执行工具，返回结果 */
    ToolCallResult execute(Map<String, Object> params, Long userId, String role);
}
```

#### 3.3.3 工具清单与权限映射

| 工具名 | 说明 | 所需权限 | ADMIN | EDITOR | VIEWER |
|--------|------|----------|-------|--------|--------|
| `query_products` | 查询产品列表/详情 | `product:view` | ✅ | ✅ | ✅ |
| `query_prices` | 查询价格信息 | `price:view` | ✅ | ✅ | ✅ |
| `query_price_history` | 查询价格变更历史 | `price:view` | ✅ | ✅ | ✅ |
| `query_customers` | 查询客户信息 | `product:view` | ✅ | ✅ | ✅ |
| `query_origins` | 查询产地信息 | `product:view` | ✅ | ✅ | ✅ |
| `query_categories` | 查询分类信息 | `category:view` | ✅ | ✅ | ✅ |
| `query_dict` | 查询字典数据 | - | ✅ | ✅ | ✅ |
| `query_approvals` | 查询审批信息 | `approval:view` | ✅ | ✅ | ✅ |
| `query_logs` | 查询操作日志 | `log:view` | ✅ | ❌ | ❌ |
| `query_users` | 查询用户信息 | `user:view` | ✅ | ❌ | ❌ |
| `query_stats_summary` | 综合统计概览 | `product:view` | ✅ | ✅ | ✅ |
| `query_stats_price_trend` | 价格趋势分析 | `price:view` | ✅ | ✅ | ✅ |
| `query_stats_category_dist` | 分类分布统计 | `category:view` | ✅ | ✅ | ✅ |
| `create_product` | 创建产品 | `product:create` | ✅ | ✅ | ❌ |
| `update_product` | 更新产品 | `product:edit` | ✅ | ✅ | ❌ |
| `create_price` | 创建价格 | `price:edit` | ✅ | ✅ | ❌ |
| `update_price` | 更新价格 | `price:edit` | ✅ | ✅ | ❌ |
| `submit_approval` | 提交审批 | `approval:create` | ✅ | ✅ | ❌ |
| `process_approval` | 审批处理 | `approval:process` | ✅ | ✅ | ❌ |

**关键规则**：
- VIEWER 只能查询，不能创建/修改
- EDITOR 可查询+创建/修改，但不可查看日志和用户管理
- ADMIN 全部权限
- **数据范围过滤**：EDITOR/VIEWER 查询产品/价格时，自动注入用户可见范围

#### 3.3.4 示例：产品查询工具

```java
@Component
public class ProductQueryTool implements ToolDefinition {
    
    @Override
    public String name() { return "query_products"; }
    
    @Override
    public String description() {
        return "查询产品信息。可按名称、分类、产地、状态等条件筛选，"
             + "支持分页。返回产品名称、规格、产地、分类、状态等字段。";
    }
    
    @Override
    public String parameterSchema() {
        return """
        {
            "type": "object",
            "properties": {
                "keyword": {"type": "string", "description": "产品名称关键词"},
                "category_id": {"type": "integer", "description": "分类ID"},
                "origin_id": {"type": "integer", "description": "产地ID"},
                "status": {"type": "string", "enum": ["ACTIVE","INACTIVE"], "description": "状态"},
                "page": {"type": "integer", "default": 1},
                "size": {"type": "integer", "default": 20, "maximum": 100}
            }
        }
        """;
    }
    
    @Override
    public String[] requiredPermissions() {
        return new String[]{"product:view"};
    }
    
    @Override
    public ToolCallResult execute(Map<String, Object> params, Long userId, String role) {
        // 调用已有的 ProductService 查询
        // 返回结构化 JSON 结果
    }
}
```

#### 3.3.5 AI 鉴权守卫

```java
@Component
public class AiAuthGuard {
    
    // 角色 → 权限集合映射（与前端 usePermission 一致）
    private static final Map<String, Set<String>> ROLE_PERMISSIONS = Map.of(
        "ADMIN", Set.of(/* 全部23个权限 */),
        "EDITOR", Set.of(/* 14个权限 */),
        "VIEWER", Set.of(/* 5个权限 */)
    );
    
    /**
     * 检查用户是否有权限执行指定工具
     * @return 允许执行的工具列表（过滤掉无权限的）
     */
    public List<ToolDefinition> filterToolsByRole(List<ToolDefinition> allTools, String role) {
        Set<String> permissions = ROLE_PERMISSIONS.getOrDefault(role, Set.of());
        return allTools.stream()
            .filter(tool -> Arrays.stream(tool.requiredPermissions())
                .anyMatch(permissions::contains))
            .toList();
    }
    
    /**
     * 校验单个工具调用权限
     */
    public boolean hasPermission(ToolDefinition tool, String role) {
        Set<String> permissions = ROLE_PERMISSIONS.getOrDefault(role, Set.of());
        return Arrays.stream(tool.requiredPermissions())
            .anyMatch(permissions::contains);
    }
}
```

#### 3.3.6 Chat API 设计

```java
@RestController
@RequestMapping("/api/ai")
public class ChatController {
    
    /**
     * 获取 Chat 功能状态（所有已认证用户可调用）
     * GET /api/ai/feature-status
     * 返回全局开关状态 + 当前角色的开关状态
     */
    @GetMapping("/feature-status")
    public Result<ChatFeatureStatus> getFeatureStatus(Authentication auth) {
        // 1. 读取全局开关
        // 2. 读取当前角色的开关
        // 3. 返回综合状态
    }
    
    /**
     * 管理员切换 Chat 全局开关
     * PUT /api/ai/feature-toggle
     * 仅 ADMIN 可调用
     */
    @PutMapping("/feature-toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> toggleFeature(@RequestBody FeatureToggleRequest request) {
        // 1. 更新全局/角色开关
        // 2. 广播 SSE 事件通知所有在线用户
        // 3. 记录审计日志
    }
    
    /**
     * SSE 订阅：Chat 功能状态变更通知
     * GET /api/ai/feature-status/stream (SSE)
     * 所有已认证用户可订阅
     * 当管理员切换开关时，实时推送变更事件
     */
    @GetMapping(value = "/feature-status/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatFeatureStatus>> streamFeatureStatus(Authentication auth) {
        // 订阅功能状态变更事件流
    }
    
    /**
     * SSE 流式对话
     * POST /api/ai/chat
     * 前置校验：Chat 功能必须处于开启状态
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatChunk>> chat(
            @RequestBody ChatRequest request,
            @AuthenticationPrincipal Authentication auth) {
        // 0. 检查 Chat 功能是否开启（全局 + 当前角色）
        //    若未开启，返回 Result.error(503, "AI Chat 功能已关闭")
        // 1. 从 auth 获取 userId 和 role
        // 2. 根据角色过滤可用工具
        // 3. 构建 LLM Prompt（含系统提示 + 工具定义 + 历史消息）
        // 4. 调用 LLM，流式返回
        // 5. 如果 LLM 返回 tool_call，执行工具并继续对话
        // 6. 审计日志记录
    }
    
    /**
     * 获取当前用户可用的工具列表（供前端展示）
     * GET /api/ai/tools
     * 前置校验：Chat 功能必须处于开启状态
     */
    @GetMapping("/tools")
    public Result<List<ToolInfo>> getAvailableTools(Authentication auth) {
        // 检查功能开关 → 根据角色返回可用工具
    }
    
    /**
     * 获取会话列表
     * GET /api/ai/sessions
     */
    @GetMapping("/sessions")
    public Result<List<ChatSession>> getSessions(Authentication auth) { }
    
    /**
     * 获取会话历史消息
     * GET /api/ai/sessions/{sessionId}/messages
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<ChatMessage>> getMessages(
            @PathVariable Long sessionId, Authentication auth) { }
    
    /**
     * 删除会话
     * DELETE /api/ai/sessions/{sessionId}
     */
    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(
            @PathVariable Long sessionId, Authentication auth) { }
}
```

#### 3.3.7 SSE 事件格式

```json
// 1. 文本流
event: message
data: {"type": "text", "content": "根据查询结果，"}

// 2. 工具调用中
event: message
data: {"type": "tool_call", "tool": "query_products", "args": {"keyword": "螺纹钢"}}

// 3. 工具结果
event: message
data: {"type": "tool_result", "tool": "query_products", "result": {...}}

// 4. 完成信号
event: done
data: {"sessionId": 123, "messageId": 456}
```

### 3.4 MCP Server 设计

MCP Server 暴露两个端点供外部 Agent 接入：

| 端点 | 协议 | 说明 |
|------|------|------|
| `POST /mcp/sse` | MCP SSE Transport | MCP 客户端连接入口 |
| `POST /mcp/message` | MCP HTTP Transport | MCP 消息处理 |

**MCP Tool 定义示例（MCP 协议格式）**：

```json
{
    "name": "query_products",
    "description": "查询产品信息。可按名称、分类、产地、状态等条件筛选。",
    "inputSchema": {
        "type": "object",
        "properties": {
            "keyword": {"type": "string", "description": "产品名称关键词"},
            "category_id": {"type": "integer", "description": "分类ID"},
            "status": {"type": "string", "enum": ["ACTIVE", "INACTIVE"]}
        }
    }
}
```

**MCP 认证**：MCP 客户端连接时需在请求头中携带 `Authorization: Bearer {jwt_token}`，与现有 JWT 体系一致。

### 3.5 OpenAI 兼容 API 设计

提供 `/v1/chat/completions` 端点，兼容 OpenAI 格式：

```java
@RestController
@RequestMapping("/v1")
public class OpenAiCompatibleController {
    
    /**
     * OpenAI 兼容接口
     * POST /v1/chat/completions
     */
    @PostMapping(value = "/chat/completions", 
                 produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> completions(
            @RequestBody OpenAiChatRequest request,
            @RequestHeader("Authorization") String auth) {
        // 1. JWT 鉴权
        // 2. 转换为内部 ChatRequest
        // 3. 调用 AiChatService
        // 4. 转换为 OpenAI 格式返回
    }
    
    /**
     * 获取可用模型列表
     * GET /v1/models
     */
    @GetMapping("/models")
    public Result<List<ModelInfo>> models() { }
}
```

返回格式兼容 OpenAI：

```json
{
    "id": "chatcmpl-xxx",
    "object": "chat.completion.chunk",
    "choices": [{
        "index": 0,
        "delta": {"content": "根据查询"},
        "finish_reason": null
    }]
}
```

### 3.6 系统提示词设计

```java
private String buildSystemPrompt(String role, List<ToolDefinition> tools) {
    return """
    你是价格管理系统的智能助手。你可以帮助用户查询和分析产品、价格、客户等业务数据。
    
    ## 当前用户信息
    - 角色：%s
    - 可用工具：%s
    
    ## 规则
    1. 只使用你拥有的工具来获取数据，不要编造数据
    2. 如果用户请求超出你的权限范围，礼貌地告知权限不足
    3. 回答中涉及金额时，显示货币符号和金额
    4. 数据分析时，主动提供趋势和洞察
    5. 如果用户问题模糊，主动澄清再查询
    6. 所有状态、角色等编码值，使用中文显示名称
    7. 日期格式：yyyy-MM-dd
    """.formatted(role, tools.stream().map(ToolDefinition::name).toList());
}
```

### 3.7 数据库表设计

#### ai_chat_session（AI 会话表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 会话 ID |
| user_id | BIGINT FK | 用户 ID |
| title | VARCHAR(200) | 会话标题（自动生成或用户命名） |
| model | VARCHAR(50) | 使用的模型 |
| message_count | INT | 消息数 |
| last_message_time | DATETIME | 最后消息时间 |
| created_time | DATETIME | 创建时间 |
| updated_time | DATETIME | 更新时间 |

#### ai_chat_message（AI 消息表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 消息 ID |
| session_id | BIGINT FK | 会话 ID |
| role | VARCHAR(20) | 角色：user / assistant / tool |
| content | TEXT | 消息内容 |
| tool_name | VARCHAR(50) | 调用的工具名（role=tool 时） |
| tool_args | JSON | 工具调用参数 |
| tool_result | JSON | 工具返回结果 |
| token_count | INT | Token 消耗 |
| created_time | DATETIME | 创建时间 |

#### ai_audit_log（AI 审计日志表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 日志 ID |
| user_id | BIGINT | 用户 ID |
| session_id | BIGINT | 会话 ID |
| action | VARCHAR(50) | 操作类型：chat / tool_call |
| tool_name | VARCHAR(50) | 工具名 |
| tool_args | JSON | 调用参数 |
| result_summary | VARCHAR(500) | 结果摘要 |
| token_used | INT | Token 消耗 |
| ip_address | VARCHAR(50) | IP 地址 |
| created_time | DATETIME | 创建时间 |

#### ai_feature_switch（AI 功能开关表）

**说明**：管理员控制 AI Chat 功能的全局和角色级开关，支持即时生效。

| 字段 | 类型 | 约束 | 默认值 | 说明 |
|--------|----------|------|--------|------|
| id | BIGINT | PK, AUTO_INCREMENT | - | 开关 ID |
| feature_key | VARCHAR(50) | UNIQUE, NOT NULL | - | 功能标识：`CHAT_GLOBAL` / `CHAT_ADMIN` / `CHAT_EDITOR` / `CHAT_VIEWER` |
| enabled | BOOLEAN | NOT NULL | TRUE | 是否启用 |
| disabled_reason | VARCHAR(200) | - | NULL | 关闭原因（管理员填写，展示给用户） |
| last_toggled_by | BIGINT | - | NULL | 最后操作人（用户 ID） |
| last_toggled_time | DATETIME | - | NULL | 最后切换时间 |
| created_time | DATETIME | NOT NULL | CURRENT_TIMESTAMP | 创建时间 |
| updated_time | DATETIME | - | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |

**预置数据（DataInitializer 自动初始化）：**

| feature_key | enabled | 说明 |
|-------------|---------|------|
| `CHAT_GLOBAL` | TRUE | 全局总开关——关闭后所有角色的 Chat 功能均不可用 |
| `CHAT_ADMIN` | TRUE | ADMIN 角色 Chat 开关（受全局开关约束） |
| `CHAT_EDITOR` | TRUE | EDITOR 角色 Chat 开关（受全局开关约束） |
| `CHAT_VIEWER` | TRUE | VIEWER 角色 Chat 开关（受全局开关约束） |

**开关生效逻辑：**

```
用户能否使用 Chat = CHAT_GLOBAL.enabled ✅
                    AND CHAT_{用户角色}.enabled ✅
```

- 全局开关关闭 → 所有角色不可用（无论角色开关状态）
- 全局开关开启 → 各角色按自身开关独立控制

**索引：**
- `uk_feature_key` — feature_key 唯一索引

#### external_agent（外部 Agent 配置表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | Agent ID |
| name | VARCHAR(100) NOT NULL | Agent 名称（如"Open Claw - 生产环境"） |
| code | VARCHAR(50) UNIQUE NOT NULL | Agent 唯一编码（如 `open_claw_prod`） |
| protocol | VARCHAR(20) NOT NULL | 通讯协议：`MCP_SSE` / `MCP_STDIO` / `OPENAI` / `CUSTOM` |
| endpoint_url | VARCHAR(500) NOT NULL | 连接地址（如 `http://192.168.1.100:8080/mcp/sse`） |
| api_key | VARCHAR(500) | 认证密钥（AES 加密存储） |
| auth_type | VARCHAR(20) | 认证方式：`NONE` / `BEARER` / `API_KEY` / `BASIC` |
| auth_header_name | VARCHAR(50) | 自定义认证头名称（如 `X-API-Key`） |
| config_json | JSON | 扩展配置（模型名、超时时间、重试策略等） |
| status | VARCHAR(20) NOT NULL DEFAULT 'DISABLED' | 状态：`ENABLED` / `DISABLED` / `ERROR` |
| health_status | VARCHAR(20) DEFAULT 'UNKNOWN' | 健康状态：`HEALTHY` / `DEGRADED` / `UNHEALTHY` / `UNKNOWN` |
| last_health_check | DATETIME | 最后健康检查时间 |
| last_error_message | VARCHAR(500) | 最后错误信息 |
| consecutive_failures | INT DEFAULT 0 | 连续失败次数 |
| total_requests | BIGINT DEFAULT 0 | 累计请求次数 |
| total_failures | BIGINT DEFAULT 0 | 累计失败次数 |
| avg_response_ms | INT | 平均响应时间（毫秒） |
| enabled_by | BIGINT | 启用人（用户 ID） |
| enabled_time | DATETIME | 启用时间 |
| remark | TEXT | 备注 |
| created_by | BIGINT | 创建人 |
| created_time | DATETIME NOT NULL | 创建时间 |
| updated_time | DATETIME | 更新时间 |

**索引：**
- `idx_agent_code` — code 唯一索引
- `idx_agent_status` — status 索引
- `idx_agent_health` — health_status 索引

#### agent_health_log（Agent 健康日志表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 日志 ID |
| agent_id | BIGINT FK NOT NULL | Agent ID |
| check_type | VARCHAR(20) NOT NULL | 检查类型：`SCHEDULED` / `MANUAL` / `ON_CALL` |
| status | VARCHAR(20) NOT NULL | 检查结果：`HEALTHY` / `DEGRADED` / `UNHEALTHY` |
| response_time_ms | INT | 响应时间（毫秒），-1 表示超时/无法连接 |
| error_message | VARCHAR(500) | 错误信息 |
| capabilities | JSON | Agent 返回的能力列表（首次检测时缓存） |
| created_time | DATETIME NOT NULL | 检查时间 |

**索引：**
- `idx_health_agent_time` — (agent_id, created_time) 复合索引
- `idx_health_status` — status 索引

#### agent_alert（Agent 告警记录表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 告警 ID |
| agent_id | BIGINT FK NOT NULL | Agent ID |
| alert_type | VARCHAR(30) NOT NULL | 告警类型：`CONNECTION_LOST` / `HIGH_LATENCY` / `AUTH_FAILED` / `CAPABILITY_CHANGED` / `CONSECUTIVE_FAILURE` |
| severity | VARCHAR(10) NOT NULL | 严重程度：`CRITICAL` / `WARNING` / `INFO` |
| message | VARCHAR(500) NOT NULL | 告警消息 |
| is_resolved | BOOLEAN DEFAULT FALSE | 是否已解决 |
| resolved_by | BIGINT | 解决人 |
| resolved_time | DATETIME | 解决时间 |
| notify_method | VARCHAR(20) | 通知方式：`IN_APP` / `EMAIL` / `WEBHOOK` |
| notified_users | JSON | 已通知的用户 ID 列表 |
| created_time | DATETIME NOT NULL | 告警时间 |

**索引：**
- `idx_alert_agent` — agent_id 索引
- `idx_alert_resolved` — is_resolved 索引
- `idx_alert_time` — created_time 索引

---

### 3.8 外部 Agent 注册与配置管理

#### 3.8.1 Agent 注册流程

```
管理员填写配置 → 连通性测试 → 测试通过 → 启用 Agent → 加入健康监控
                         ↘ 测试失败 → 保存为草稿（DISABLED）→ 修改后重新测试
```

**Agent 配置项：**

| 配置项 | 必填 | 说明 |
|--------|------|------|
| 名称 | ✅ | Agent 显示名称 |
| 编码 | ✅ | 唯一标识，用于系统内部引用 |
| 协议 | ✅ | MCP_SSE / MCP_STDIO / OPENAI / CUSTOM |
| 连接地址 | ✅ | URL 或 stdio 命令 |
| 认证方式 | - | NONE / BEARER / API_KEY / BASIC |
| 认证密钥 | - | AES 加密存储，前端显示为掩码 |
| 扩展配置 | - | JSON 格式，含超时/重试/模型等 |
| 备注 | - | 说明文字 |

#### 3.8.2 连通性测试（Connectivity Test）

测试分**三个阶段**，逐步深入：

```
┌─────────────────────────────────────────────────┐
│ 阶段 1：TCP 连通测试                             │
│ - 尝试建立 TCP 连接到目标地址                     │
│ - 超时 5 秒                                     │
│ - 成功 → 进入阶段 2                              │
│ - 失败 → 返回 "无法连接，请检查地址和网络"        │
├─────────────────────────────────────────────────┤
│ 阶段 2：协议握手测试                             │
│ - MCP: 发送 initialize 请求，验证响应             │
│ - OpenAI: 调用 /v1/models，验证响应格式           │
│ - 超时 10 秒                                    │
│ - 成功 → 进入阶段 3                              │
│ - 失败 → 返回 "协议不兼容或认证失败"              │
├─────────────────────────────────────────────────┤
│ 阶段 3：能力探测测试                             │
│ - MCP: 调用 tools/list，获取 Agent 暴露的工具列表  │
│ - OpenAI: 验证 model 是否可用                    │
│ - 记录响应时间                                   │
│ - 成功 → 返回测试报告（含能力列表）               │
│ - 失败 → 返回 "能力探测异常"                     │
└─────────────────────────────────────────────────┘
```

**测试结果模型：**

```java
@Data
public class AgentTestResult {
    private boolean success;              // 整体是否通过
    private String phase;                 // 失败阶段：TCP / PROTOCOL / CAPABILITY / null
    private String errorMessage;          // 错误信息
    private Integer responseTimeMs;       // 响应时间
    private List<String> capabilities;    // Agent 能力列表（阶段3成功时返回）
    private Map<String, Object> details;  // 详细信息（协议版本等）
}
```

#### 3.8.3 Agent 配置管理 API

```java
@RestController
@RequestMapping("/api/ai/agents")
@PreAuthorize("hasRole('ADMIN')")
public class AgentConfigController {

    /**
     * 获取所有已注册 Agent 列表
     * GET /api/ai/agents
     * @param status 可选筛选状态（ENABLED/DISABLED/ERROR）
     */
    @GetMapping
    public Result<Page<AgentConfigDTO>> listAgents(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size);

    /**
     * 获取 Agent 详情
     * GET /api/ai/agents/{id}
     */
    @GetMapping("/{id}")
    public Result<AgentConfigDTO> getAgent(@PathVariable Long id);

    /**
     * 注册新 Agent
     * POST /api/ai/agents
     * 仅创建配置，状态为 DISABLED，需测试后手动启用
     */
    @PostMapping
    public Result<AgentConfigDTO> createAgent(@RequestBody @Valid AgentConfigDTO config);

    /**
     * 更新 Agent 配置
     * PUT /api/ai/agents/{id}
     * 更新后状态自动变为 DISABLED，需重新测试和启用
     */
    @PutMapping("/{id}")
    public Result<AgentConfigDTO> updateAgent(
            @PathVariable Long id, @RequestBody @Valid AgentConfigDTO config);

    /**
     * 删除 Agent（仅 DISABLED 状态可删除）
     * DELETE /api/ai/agents/{id}
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteAgent(@PathVariable Long id);

    /**
     * 连通性测试（不修改状态，仅返回测试结果）
     * POST /api/ai/agents/test
     * 支持对未保存的配置进行测试（前端"测试连接"按钮）
     */
    @PostMapping("/test")
    public Result<AgentTestResult> testConnection(@RequestBody @Valid AgentConfigDTO config);

    /**
     * 对已保存的 Agent 重新测试
     * POST /api/ai/agents/{id}/test
     */
    @PostMapping("/{id}/test")
    public Result<AgentTestResult> testExistingAgent(@PathVariable Long id);

    /**
     * 启用 Agent（需先通过连通性测试）
     * PUT /api/ai/agents/{id}/enable
     */
    @PutMapping("/{id}/enable")
    public Result<Void> enableAgent(@PathVariable Long id);

    /**
     * 停用 Agent
     * PUT /api/ai/agents/{id}/disable
     */
    @PutMapping("/{id}/disable")
    public Result<Void> disableAgent(@PathVariable Long id);

    /**
     * 获取 Agent 健康报告
     * GET /api/ai/agents/{id}/health
     */
    @GetMapping("/{id}/health")
    public Result<AgentHealthReport> getHealthReport(@PathVariable Long id);

    /**
     * 获取 Agent 告警列表
     * GET /api/ai/agents/{id}/alerts
     */
    @GetMapping("/{id}/alerts")
    public Result<List<AgentAlert>> getAlerts(
            @PathVariable Long id,
            @RequestParam(required = false) Boolean resolved);

    /**
     * 解决告警
     * PUT /api/ai/agents/alerts/{alertId}/resolve
     */
    @PutMapping("/alerts/{alertId}/resolve")
    public Result<Void> resolveAlert(@PathVariable Long alertId);

    /**
     * 获取所有 Agent 健康状态概览（仪表板数据）
     * GET /api/ai/agents/health-overview
     */
    @GetMapping("/health-overview")
    public Result<HealthOverview> getHealthOverview();
}
```

### 3.9 健康监控与告警

#### 3.9.1 健康检查机制

```java
@Component
public class HealthChecker {

    /**
     * 定时健康检查（每 60 秒对 ENABLED Agent 执行）
     * 通过 @Scheduled(fixedRate = 60000) 触发
     */
    @Scheduled(fixedRate = 60000)
    public void scheduledHealthCheck() {
        // 1. 查询所有 ENABLED 状态的 Agent
        // 2. 对每个 Agent 执行轻量级健康探测
        //    - MCP: 发送 ping 请求
        //    - OpenAI: 调用 /v1/models（轻量读操作）
        // 3. 记录健康日志
        // 4. 更新 Agent 的 health_status / last_health_check / avg_response_ms
        // 5. 判断是否触发告警
    }

    /**
     * 单 Agent 健康探测
     */
    public HealthStatus checkAgentHealth(ExternalAgent agent) {
        // 1. 测量响应时间
        // 2. 验证响应格式
        // 3. 返回 HealthStatus
    }
}
```

**健康状态模型：**

```java
@Data
public class HealthStatus {
    private Long agentId;
    private String agentName;
    private HealthState state;           // HEALTHY / DEGRADED / UNHEALTHY / UNKNOWN
    private Integer responseTimeMs;      // 响应时间
    private Integer consecutiveFailures; // 连续失败次数
    private LocalDateTime lastCheckTime;
    private String lastError;
    private List<String> capabilities;   // Agent 能力列表
}

public enum HealthState {
    HEALTHY,    // 响应正常，响应时间 < 阈值
    DEGRADED,   // 可响应但超时/慢（响应时间 > 3s）
    UNHEALTHY,  // 连续 3 次失败
    UNKNOWN     // 未启用或未检测
}
```

#### 3.9.2 告警规则

| 告警类型 | 触发条件 | 严重程度 | 告警消息模板 |
|----------|----------|----------|-------------|
| `CONNECTION_LOST` | TCP 连接失败 | CRITICAL | "Agent {name} 连接中断，请检查服务状态" |
| `AUTH_FAILED` | 认证失败（401/403） | CRITICAL | "Agent {name} 认证失败，密钥可能已过期" |
| `CONSECUTIVE_FAILURE` | 连续 3 次检查失败 | CRITICAL | "Agent {name} 连续 {n} 次健康检查失败" |
| `HIGH_LATENCY` | 响应时间 > 5s | WARNING | "Agent {name} 响应延迟过高（{ms}ms）" |
| `CAPABILITY_CHANGED` | 能力列表变化（工具增减） | INFO | "Agent {name} 能力列表已变更" |
| `RECOVERY` | 从 UNHEALTHY 恢复为 HEALTHY | INFO | "Agent {name} 已恢复正常" |

#### 3.9.3 告警通知方式

```java
@Component
public class AlertNotifier {

    /**
     * 分发告警通知
     * 根据严重程度和配置选择通知方式
     */
    public void notify(AgentAlert alert) {
        // 1. 应用内通知（SSE 推送给在线管理员）
        //    → 前端右上角弹出通知
        // 2. 邮件通知（CRITICAL 级别，发送给所有 ADMIN 用户）
        // 3. Webhook 通知（如果配置了 Webhook URL）
    }
}
```

**应用内通知机制**：利用已有的 SSE 基础设施，新增 `/api/ai/notifications` SSE 端点：

```
GET /api/ai/notifications (SSE)
Authorization: Bearer {token}
仅 ADMIN 角色可订阅

事件格式：
event: agent_alert
data: {
    "alertId": 1,
    "agentId": 3,
    "agentName": "Open Claw - 生产",
    "alertType": "CONNECTION_LOST",
    "severity": "CRITICAL",
    "message": "Agent Open Claw - 生产 连接中断，请检查服务状态",
    "timestamp": "2026-04-24T10:30:00"
}

event: agent_recovery
data: {
    "agentId": 3,
    "agentName": "Open Claw - 生产",
    "message": "Agent Open Claw - 生产 已恢复正常",
    "timestamp": "2026-04-24T10:45:00"
}
```

### 3.10 Chat 功能开关管理

#### 3.10.1 设计目标

管理员可以通过一个全局开关控制所有用户的 AI Chat 功能可用性，也可以按角色独立控制。**开关变更即时生效**，所有在线用户通过 SSE 实时感知，无需刷新页面。

#### 3.10.2 ChatFeatureService 核心逻辑

```java
@Service
public class ChatFeatureService {

    /**
     * 判断指定角色是否能使用 Chat
     * 逻辑：CHAT_GLOBAL 开启 AND CHAT_{role} 开启
     */
    public boolean isChatEnabled(String role) {
        boolean globalEnabled = getSwitch("CHAT_GLOBAL").isEnabled();
        if (!globalEnabled) return false;
        return getSwitch("CHAT_" + role).isEnabled();
    }

    /**
     * 切换功能开关（仅 ADMIN 可调用）
     * @param featureKey CHAT_GLOBAL / CHAT_ADMIN / CHAT_EDITOR / CHAT_VIEWER
     * @param enabled 目标状态
     * @param reason 关闭原因（关闭时必填）
     * @param operatorId 操作人 ID
     */
    @Transactional
    public void toggleFeature(String featureKey, boolean enabled, 
                              String reason, Long operatorId) {
        // 1. 校验 featureKey 合法性
        // 2. 更新数据库
        AiFeatureSwitch sw = repository.findByFeatureKey(featureKey);
        sw.setEnabled(enabled);
        sw.setDisabledReason(enabled ? null : reason);
        sw.setLastToggledBy(operatorId);
        sw.setLastToggledTime(LocalDateTime.now());
        repository.save(sw);
        
        // 3. 发布 SSE 事件，通知所有在线用户
        ChatFeatureStatus status = buildCurrentStatus();
        sseEmitterService.broadcastFeatureStatus(status);
        
        // 4. 如果全局开关关闭，中断所有活跃 Chat SSE 连接
        if ("CHAT_GLOBAL".equals(featureKey) && !enabled) {
            sseEmitterService.closeAllChatConnections();
        }
    }

    /**
     * 获取当前完整的功能状态
     */
    public ChatFeatureStatus buildCurrentStatus() {
        return ChatFeatureStatus.builder()
            .globalEnabled(getSwitch("CHAT_GLOBAL").isEnabled())
            .adminEnabled(getSwitch("CHAT_ADMIN").isEnabled())
            .editorEnabled(getSwitch("CHAT_EDITOR").isEnabled())
            .viewerEnabled(getSwitch("CHAT_VIEWER").isEnabled())
            .disabledReason(getSwitch("CHAT_GLOBAL").getDisabledReason())
            .build();
    }
}
```

#### 3.10.3 SSE 实时同步机制

**管理员切换开关时的推送流程：**

```
管理员点击开关
    │
    ▼
后端 ChatFeatureService.toggleFeature()
    │
    ├──→ 更新数据库 ai_feature_switch
    │
    ├──→ 广播 SSE 事件到 /api/ai/feature-status/stream
    │    event: feature_status_change
    │    data: {
    │        "globalEnabled": false,
    │        "adminEnabled": true,
    │        "editorEnabled": true,
    │        "viewerEnabled": true,
    │        "disabledReason": "系统维护中，预计1小时后恢复",
    │        "toggledBy": "admin",
    │        "toggledTime": "2026-04-24T14:30:00"
    │    }
    │
    └──→ 如果全局关闭：强制关闭所有活跃的 Chat SSE 连接
         前端收到连接断开后，显示"Chat 功能已关闭"提示
```

**前端处理逻辑：**

```typescript
// useAiChat.ts 中的功能开关处理
const featureEnabled = ref(true)
const disabledReason = ref('')

// 1. 页面加载时查询状态
onMounted(async () => {
  const status = await getChatFeatureStatus()
  featureEnabled.value = isChatEnabledForRole(status, userStore.getUserRole)
  disabledReason.value = status.disabledReason || ''
})

// 2. 订阅 SSE 实时变更
const eventSource = new EventSource('/api/ai/feature-status/stream')
eventSource.addEventListener('feature_status_change', (e) => {
  const status = JSON.parse(e.data)
  const newEnabled = isChatEnabledForRole(status, userStore.getUserRole)
  
  if (featureEnabled.value && !newEnabled) {
    // 功能被关闭 → 中断当前对话，显示提示
    if (activeChatConnection.value) {
      activeChatConnection.value.close()
    }
    showToast({ message: `AI Chat 功能已关闭：${status.disabledReason}`, type: 'warning' })
  } else if (!featureEnabled.value && newEnabled) {
    // 功能被开启 → 显示入口，可开始对话
    showToast({ message: 'AI Chat 功能已恢复', type: 'success' })
  }
  
  featureEnabled.value = newEnabled
  disabledReason.value = status.disabledReason || ''
})
```

#### 3.10.4 Chat 关闭时的前端表现

当 Chat 功能对当前用户关闭时：

| 位置 | 表现 |
|------|------|
| **浮动 Chat 按钮** | 隐藏按钮，不可点击 |
| **AI Chat 独立页面** | 显示全屏提示："AI Chat 功能暂未开放" + 关闭原因 + 联系管理员 |
| **进行中的对话** | SSE 连接被服务端断开，显示"对话已中断，Chat 功能已关闭" |
| **聊天输入框** | 禁用状态，placeholder 显示"Chat 功能已关闭" |
| **历史会话列表** | 仍可查看，但不可发起对话 |
| **系统管理菜单** | ADMIN 可看到"AI 功能管理"入口，其他角色不可见 |

#### 3.10.5 Chat 开关管理界面（仅 ADMIN）

在系统管理区域新增"AI 功能管理"页面，路径 `/ai-settings`：

```
┌──────────────────────────────────────────────────────────────┐
│  AI 功能管理                                                  │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  全局开关                                                │ │
│  │                                                         │ │
│  │  AI Chat 功能    [🟢 已开启 ▾]                           │ │
│  │                                                         │ │
│  │  关闭原因（关闭时必填）：                                  │ │
│  │  ┌─────────────────────────────────────────────────┐    │ │
│  │  │ 系统维护中，预计1小时后恢复                        │    │ │
│  │  └─────────────────────────────────────────────────┘    │ │
│  │                                                         │ │
│  │  ⚠️ 关闭全局开关将影响所有用户，请谨慎操作               │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                              │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  角色级开关（全局开关开启时生效）                          │ │
│  │                                                         │ │
│  │  ┌───────────────────────────────────────────────────┐  │ │
│  │  │ 👑 管理员 (ADMIN)      [🟢 已开启]    上次操作：   │  │ │
│  │  │    2026-04-24 10:00 by admin                       │  │ │
│  │  └───────────────────────────────────────────────────┘  │ │
│  │  ┌───────────────────────────────────────────────────┐  │ │
│  │  │ ✏️ 编辑者 (EDITOR)     [🟢 已开启]    上次操作：   │  │ │
│  │  │    2026-04-20 14:30 by admin                       │  │ │
│  │  └───────────────────────────────────────────────────┘  │ │
│  │  ┌───────────────────────────────────────────────────┐  │ │
│  │  │ 👁️ 查看者 (VIEWER)     [🔴 已关闭]    上次操作：   │  │ │
│  │  │    2026-04-22 09:00 by admin                       │  │ │
│  │  │    关闭原因：查看者暂不开放 Chat 功能               │  │ │
│  │  └───────────────────────────────────────────────────┘  │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                              │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  操作日志                                                │ │
│  │  ┌────────────────────────────────────────────────────┐ │ │
│  │  │ 2026-04-24 14:30  admin  关闭全局开关  "系统维护"  │ │ │
│  │  │ 2026-04-24 14:35  admin  开启全局开关              │ │ │
│  │  │ 2026-04-22 09:00  admin  关闭 VIEWER 开关          │ │ │
│  │  └────────────────────────────────────────────────────┘ │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

#### 3.10.6 开关变更的安全保障

| 保障措施 | 说明 |
|----------|------|
| **仅 ADMIN 可操作** | `toggleFeature` API 通过 `@PreAuthorize("hasRole('ADMIN')")` 保护 |
| **关闭原因必填** | 关闭全局开关或角色开关时，`disabledReason` 为必填字段 |
| **二次确认** | 前端弹出确认对话框，显示影响范围（"将影响 N 名用户"） |
| **审计日志** | 每次变更记录操作人、时间、变更内容，写入 `ai_audit_log` |
| **防误操作** | 连续操作间隔 ≥ 3 秒（防止快速反复切换） |
| **优雅降级** | 关闭时先中断活跃对话，再断开连接，避免数据丢失 |

### 3.11 Agent 代理与降级策略

#### 3.10.1 Agent Proxy

当系统需要调用外部 Agent 能力时，统一通过 `AgentProxy` 路由：

```java
@Component
public class AgentProxy {

    /**
     * 调用外部 Agent
     * @param agentCode Agent 编码
     * @param request 调用请求
     * @return 调用结果
     */
    public AgentCallResult call(String agentCode, AgentCallRequest request) {
        ExternalAgent agent = registryService.getByCode(agentCode);
        
        // 1. 检查 Agent 是否启用
        if (agent.getStatus() != AgentStatus.ENABLED) {
            return AgentCallResult.fail("Agent 未启用");
        }
        
        // 2. 检查健康状态
        if (agent.getHealthStatus() == HealthState.UNHEALTHY) {
            // 尝试一次实时检测，如果仍不健康则拒绝
            HealthStatus health = healthChecker.checkAgentHealth(agent);
            if (health.getState() == HealthState.UNHEALTHY) {
                return AgentCallResult.fail("Agent 当前不可用，请稍后重试");
            }
        }
        
        // 3. 创建协议客户端
        AgentClient client = clientFactory.create(agent);
        
        // 4. 执行调用（含超时和重试）
        try {
            AgentCallResult result = client.call(request, agent.getTimeoutMs());
            // 更新成功计数和响应时间
            updateSuccessMetrics(agent, result.getResponseTimeMs());
            return result;
        } catch (Exception e) {
            // 更新失败计数
            updateFailureMetrics(agent, e);
            return AgentCallResult.fail("Agent 调用失败：" + e.getMessage());
        }
    }
}
```

#### 3.10.2 降级策略

| 场景 | 降级策略 |
|------|----------|
| Agent 不可用 | 返回友好提示"该 Agent 当前不可用"，引导用户使用内置 AI |
| Agent 响应超时 | 3 秒后返回部分结果 + 超时提示 |
| Agent 返回异常数据 | 校验响应格式，异常时降级为内置 AI |
| 多 Agent 负载 | 按优先级选择健康 Agent，无可用时降级 |

---

## 四、前端设计

### 4.1 组件结构

```
src/
├── views/
│   └── ai/
│       ├── AiChat.vue              # AI 对话页面（完整页面）
│       ├── AiSettings.vue          # AI 功能管理页面（仅 ADMIN，含全局开关）
│       └── AgentConfig.vue         # 外部 Agent 配置管理页面（仅 ADMIN）
├── components/
│   ├── ai/
│   │   ├── ChatPanel.vue           # 浮动聊天面板（右下角）
│   │   ├── ChatMessage.vue         # 单条消息组件
│   │   ├── ToolCallCard.vue        # 工具调用展示卡片
│   │   ├── SessionList.vue         # 会话列表
│   │   ├── ChatInput.vue           # 输入框（支持 Enter 发送、Shift+Enter 换行）
│   │   ├── ChatDisabledOverlay.vue # Chat 关闭时的遮罩/提示组件
│   │   └── FeatureToggleCard.vue   # 功能开关卡片（AI设置页面使用）
│   └── agent/
│       ├── AgentForm.vue           # Agent 配置表单（新增/编辑）
│       ├── AgentTestDialog.vue     # 连通性测试对话框（三阶段动画展示）
│       ├── AgentHealthCard.vue     # Agent 健康状态卡片
│       ├── AgentHealthDashboard.vue# Agent 健康仪表板（概览）
│       └── AgentAlertList.vue      # Agent 告警列表
├── api/
│   ├── ai.ts                       # AI API 接口
│   └── agent.ts                    # Agent 配置管理 API
├── composables/
│   ├── useAiChat.ts                # AI 对话 Composable
│   ├── useChatFeature.ts           # Chat 功能开关 Composable（状态查询+SSE订阅）
│   ├── useAgentConfig.ts           # Agent 配置管理 Composable
│   └── useAgentNotification.ts     # Agent 告警通知 Composable（SSE）
└── types/
    ├── ai.ts                       # AI 相关类型定义
    └── agent.ts                    # Agent 相关类型定义
```

### 4.2 ChatPanel 浮动面板

- 位置：右下角浮动按钮，点击展开/收起
- 功能：
  - 消息流式展示
  - 工具调用结果展示（折叠卡片）
  - 快捷提问建议
  - 会话切换
- 样式：参考 ChatGPT / Claude 侧边栏风格

### 4.3 快捷提问建议

根据用户角色动态生成：

| 角色 | 建议提问 |
|------|----------|
| ADMIN | "本月价格变更统计"、"待审批事项"、"用户活跃度"、"系统操作日志" |
| EDITOR | "最近价格变动"、"我的审批"、"客户价格查询"、"产品分类概览" |
| VIEWER | "产品价格查询"、"分类产品列表"、"产地信息" |

### 4.4 消息类型渲染

```
┌─────────────────────────────────┐
│ 👤 用户：查询螺纹钢的最新价格     │
├─────────────────────────────────┤
│ 🤖 助手：正在查询产品信息...      │
│                                 │
│ 📦 调用工具: query_products      │
│   参数: {"keyword": "螺纹钢"}    │
│   ┌─────────────────────────┐   │
│   │ 找到 3 个产品            │   │
│   │ 1. HRB400螺纹钢 ¥3,850  │   │
│   │ 2. HRB500螺纹钢 ¥4,200  │   │
│   │ 3. HRB600螺纹钢 ¥4,680  │   │
│   └─────────────────────────┘   │
│                                 │
│ 📦 调用工具: query_prices        │
│   参数: {"product_id": 1}       │
│   ┌─────────────────────────┐   │
│   │ 产品: HRB400螺纹钢       │   │
│   │ 当前价格: ¥3,850/吨      │   │
│   │ 上次变更: 2026-04-20     │   │
│   │ 变更幅度: +2.3%          │   │
│   └─────────────────────────┘   │
│                                 │
│ 根据查询结果，HRB400螺纹钢的     │
│ 最新价格为 ¥3,850/吨，较上次     │
│ 上调 2.3%（上次变更日期4月20日） │
└─────────────────────────────────┘
```

### 4.5 Agent 配置管理页面（AgentConfig.vue）

仅 ADMIN 可访问，路径 `/agent-config`，功能布局：

```
┌──────────────────────────────────────────────────────────────┐
│  外部 Agent 配置                                    [+ 新增]  │
├──────────────────────────────────────────────────────────────┤
│  筛选：[全部 ▾] [健康状态 ▾]                    🔍 搜索...   │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │ 🟢 Open Claw - 生产环境                    [MCP_SSE]  │ │
│  │    http://192.168.1.100:8080/mcp/sse                   │ │
│  │    健康 ✅  延迟 120ms  上次检查 30秒前                  │ │
│  │    请求 1,234 次  失败 2 次  成功率 99.8%              │ │
│  │                          [测试] [编辑] [停用] [告警(3)] │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                              │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │ 🟡 Hermes - 测试环境                       [OPENAI]   │ │
│  │    http://test.hermes.local:8080/v1                     │ │
│  │    降级 ⚠️  延迟 5,200ms  上次检查 30秒前               │ │
│  │    请求 56 次  失败 8 次  成功率 85.7%                  │ │
│  │                          [测试] [编辑] [停用] [告警(1)] │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                              │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │ 🔴 Custom Agent - 旧服务                   [CUSTOM]   │ │
│  │    http://legacy:9090/api                               │ │
│  │    异常 ❌  连续失败 5 次  最后错误: Connection refused   │ │
│  │                          [测试] [编辑] [启用] [告警(8)] │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                              │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │ ⚪ MyAgent - 开发中                        [MCP_SSE]  │ │
│  │    http://localhost:3000/mcp/sse                        │ │
│  │    未启用  (最近测试: 通过 ✅)                           │ │
│  │                          [测试] [编辑] [启用] [删除]    │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 4.6 Agent 新增/编辑表单（AgentForm.vue）

```
┌──────────────────────────────────────────────────────────────┐
│  新增外部 Agent                                              │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  基本信息                                                     │
│  ┌──────────────────────┐  ┌──────────────────────────┐     │
│  │ Agent 名称 *          │  │ Agent 编码 *              │     │
│  │ Open Claw - 生产      │  │ open_claw_prod            │     │
│  └──────────────────────┘  └──────────────────────────┘     │
│                                                              │
│  连接配置                                                     │
│  ┌──────────────────────┐  ┌──────────────────────────┐     │
│  │ 通讯协议 *            │  │ 认证方式                  │     │
│  │ [MCP SSE ▾]          │  │ [Bearer Token ▾]         │     │
│  └──────────────────────┘  └──────────────────────────┘     │
│  ┌──────────────────────────────────────────────────┐       │
│  │ 连接地址 *                                        │       │
│  │ http://192.168.1.100:8080/mcp/sse                 │       │
│  └──────────────────────────────────────────────────┘       │
│  ┌──────────────────────┐  ┌──────────────────────────┐     │
│  │ 认证密钥              │  │ 自定义认证头              │     │
│  │ ••••••••••••          │  │ Authorization             │     │
│  └──────────────────────┘  └──────────────────────────┘     │
│                                                              │
│  高级配置（折叠）                                              │
│  ┌──────────────────────┐  ┌──────────────────────────┐     │
│  │ 超时时间 (ms)         │  │ 重试次数                  │     │
│  │ 10000                 │  │ 3                         │     │
│  └──────────────────────┘  └──────────────────────────┘     │
│  ┌──────────────────────┐                                   │
│  │ 模型名称              │                                   │
│  │ gpt-4o-mini           │                                   │
│  └──────────────────────┘                                   │
│                                                              │
│  备注                                                        │
│  ┌──────────────────────────────────────────────────┐       │
│  │ 生产环境 Open Claw Agent，用于...                  │       │
│  └──────────────────────────────────────────────────┘       │
│                                                              │
│                          [测试连接] [取消] [保存]             │
└──────────────────────────────────────────────────────────────┘
```

### 4.7 连通性测试对话框（AgentTestDialog.vue）

三阶段测试动画展示，实时反馈进度：

**测试成功示例：**

```
┌──────────────────────────────────────────────────────────────┐
│  连通性测试 - Open Claw - 生产环境                           │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ① TCP 连通测试                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ ✅ 连接成功  192.168.1.100:8080  延迟 45ms           │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  ② 协议握手测试                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ ✅ MCP 协议握手成功  版本 2024-11-05                  │   │
│  │    Server: open-claw/2.1.0                            │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  ③ 能力探测测试                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ ✅ 能力探测成功  延迟 120ms                           │   │
│  │                                                      │   │
│  │ 发现 5 个工具：                                      │   │
│  │   • query_market_prices   查询市场价格                │   │
│  │   • get_supply_chain_info 获取供应链信息              │   │
│  │   • analyze_trend         趋势分析                   │   │
│  │   • predict_price         价格预测                   │   │
│  │   • generate_report       生成报告                   │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  测试结果：✅ 全部通过  可安全启用                             │
│                                                              │
│                              [关闭] [立即启用]               │
└──────────────────────────────────────────────────────────────┘
```

**测试失败示例：**

```
┌──────────────────────────────────────────────────────────────┐
│  连通性测试 - Custom Agent - 旧服务                          │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ① TCP 连通测试                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ ❌ 连接失败  http://legacy:9090                       │   │
│  │    错误：Connection refused (连接被拒绝)               │   │
│  │    建议：请检查目标服务是否启动，端口是否正确           │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  ② 协议握手测试                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ ⏭️ 跳过（TCP 未通过）                                 │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  ③ 能力探测测试                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ ⏭️ 跳过（协议握手未通过）                             │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  测试结果：❌ 未通过  无法启用                                 │
│                                                              │
│                     [关闭] [修改配置]                         │
└──────────────────────────────────────────────────────────────┘
```

### 4.8 Agent 健康仪表板（AgentHealthDashboard.vue）

页面顶部概览区域，嵌入 AgentConfig 页面或作为独立 Tab：

```
┌──────────────────────────────────────────────────────────────┐
│  Agent 健康概览                                              │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │    4     │  │    2     │  │    1     │  │    1     │   │
│  │  总计    │  │  健康    │  │  降级    │  │  异常    │   │
│  │          │  │   🟢     │  │   🟡     │  │   🔴     │   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘   │
│                                                              │
│  最近告警                                                     │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ 🔴 CRITICAL  Custom Agent 连接中断         5分钟前   │   │
│  │ 🟡 WARNING   Hermes 响应延迟过高 (5200ms)  10分钟前  │   │
│  │ 🟢 INFO      Open Claw 已恢复正常          30分钟前  │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  健康趋势（24小时）                                           │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ [ECharts 折线图]                                      │   │
│  │  X轴: 时间  Y轴: 响应时间(ms)                        │   │
│  │  每条线: 一个 Agent                                   │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 4.9 告警通知组件（右上角弹出）

管理员登录时自动订阅 `/api/ai/notifications` SSE，收到告警后弹出：

```
┌─────────────────────────────────────────────┐
│ 🔴 Agent 异常告警                     ✕     │
├─────────────────────────────────────────────┤
│ Custom Agent - 旧服务 连接中断              │
│ 已连续失败 5 次                              │
│                                             │
│ [查看详情]  [标记已知]                       │
└─────────────────────────────────────────────┘
```

告警自动消失策略：
- CRITICAL：不自动消失，需手动关闭
- WARNING：15 秒后自动消失
- INFO：8 秒后自动消失

### 5.1 安全层级

```
┌─────────────────────────────────────┐
│  L0: 功能开关（全局/角色级控制）     │
├─────────────────────────────────────┤
│  L1: JWT 认证（必须有合法 Token）    │
├─────────────────────────────────────┤
│  L2: 角色-工具过滤（只暴露有权工具）  │
├─────────────────────────────────────┤
│  L3: 数据范围过滤（行级权限）        │
├─────────────────────────────────────┤
│  L4: 操作审计（全量日志）            │
├─────────────────────────────────────┤
│  L5: 破坏性操作确认（二次确认）      │
├─────────────────────────────────────┤
│  L6: Agent 通信安全（密钥加密+TLS）  │
├─────────────────────────────────────┤
│  L7: Agent 调用隔离（沙箱/降级）     │
└─────────────────────────────────────┘
```

### 5.2 LLM 注入防护

- **系统提示词不可被用户消息覆盖**（使用 System role，置于消息列表首位）
- **工具返回数据脱敏**：用户密码、敏感个人信息等字段在工具返回前清除
- **输入长度限制**：单条消息最大 2000 字符
- **工具参数校验**：按 JSON Schema 校验，拒绝非法参数

### 5.3 破坏性操作确认

对于 `create_*`、`update_*`、`delete_*` 类工具，增加确认机制：

1. LLM 调用工具 → 返回"预览"结果（不实际执行）
2. 前端弹出确认对话框
3. 用户确认 → 发送 `/api/ai/confirm` → 实际执行
4. 用户取消 → 告知 LLM 用户取消操作

---

## 六、实施计划

### 阶段一：基础设施（3-4 天）

| 任务 | 说明 |
|------|------|
| 添加依赖 | Spring AI、WebFlux、MCP SDK |
| 配置类 | AiProperties、AiAutoConfiguration |
| 实体与 Repository | AiChatSession、AiChatMessage、AiAuditLog、**AiFeatureSwitch** |
| AiAuthGuard | 角色-权限映射与工具过滤 |
| 公开配置接口 | `/api/ai/tools` 返回当前角色可用工具 |
| **ChatFeatureService** | **功能开关管理（全局/角色级开关 CRUD + SSE 广播）** |
| **开关初始化** | **DataInitializer 自动创建 4 条预置开关记录** |

### 阶段二：工具引擎（4-5 天）

| 任务 | 说明 |
|------|------|
| ToolDefinition 接口 | 定义工具规范 |
| ToolRegistry | 工具注册中心（自动扫描 @Component） |
| ToolExecutor | 工具执行器（鉴权 → 执行 → 审计） |
| 10 个查询工具 | ProductQuery、PriceQuery 等查询类工具 |
| 6 个操作工具 | CreateProduct、UpdatePrice 等操作类工具 |

### 阶段三：对话服务（3-4 天）

| 任务 | 说明 |
|------|------|
| LlmRouterService | 多模型路由（OpenAI/Claude/Ollama） |
| AiChatService | 核心对话流程（含 tool_call 循环） |
| ChatSessionService | 会话 CRUD、历史消息管理 |
| ChatController | SSE 端点（**含功能开关前置校验**） |
| 系统提示词 | 角色感知的系统提示词构建 |

### 阶段四：MCP Server（2-3 天）

| 任务 | 说明 |
|------|------|
| McpServerHandler | MCP 协议处理器 |
| McpToolProvider | 将 ToolDefinition 转为 MCP Tool |
| McpController | SSE + HTTP 端点 |
| 测试 | 用 Claude Desktop 或 MCP Inspector 测试 |

### 阶段五：OpenAI 兼容 API（2 天）

| 任务 | 说明 |
|------|------|
| OpenAiCompatibleController | /v1/chat/completions |
| 请求/响应转换 | OpenAI 格式 ↔ 内部格式 |
| 测试 | OpenAI SDK / curl 测试 |

### 阶段六：前端 Chat 界面（4-5 天）

| 任务 | 说明 |
|------|------|
| useAiChat composable | SSE 连接、消息管理、流式渲染 |
| **useChatFeature composable** | **功能开关状态查询 + SSE 实时订阅** |
| ChatPanel 组件 | 浮动面板（**集成功能开关判断**） |
| ChatMessage 组件 | 消息渲染（Markdown + 代码块） |
| ToolCallCard 组件 | 工具调用折叠卡片 |
| SessionList 组件 | 会话列表 |
| **ChatDisabledOverlay 组件** | **Chat 关闭时的遮罩/提示** |
| AiChat 页面 | 完整对话页面 |
| **AiSettings 页面** | **AI 功能管理页面（仅 ADMIN，含全局/角色开关）** |
| 快捷建议 | 角色感知的建议列表 |

### 阶段七：安全加固与测试（2-3 天）

| 任务 | 说明 |
|------|------|
| 破坏性操作确认 | 二次确认机制 |
| 数据脱敏 | 工具返回数据过滤 |
| 审计日志查询 | 管理员查看 AI 审计日志 |
| 端到端测试 | 角色权限、工具调用、流式对话 |
| 文档更新 | 更新所有设计/指南文档 |

**总预估：20-26 天**

---

## 七、待讨论事项

| # | 事项 | 选项 | 建议 |
|---|------|------|------|
| 1 | **LLM 提供者选择** | A) OpenAI API B) Claude API C) Ollama 本地 D) 多模型可配 | **D**：配置化切换，默认 Ollama（零成本本地部署），生产可切 OpenAI/Claude |
| 2 | **MCP 实现方式** | A) SSE Transport B) Stdio Transport C) 两者都支持 | **A**：SSE Transport 适合 Web 架构，Stdio 适合本地 CLI |
| 3 | **操作工具是否开放** | A) 只读查询 B) 允许创建/修改 C) 允许删除 | **B**：允许创建/修改但需二次确认，不允许通过 AI 删除 |
| 4 | **Chat 面板形式** | A) 右下角浮动 B) 独立页面 C) 两者都有 | **C**：浮动面板快速问答，独立页面深度分析 |
| 5 | **Token 限制与费用** | A) 无限制 B) 用户级配额 C) 角色+配额 | **C**：ADMIN 5000/天、EDITOR 3000/天、VIEWER 1000/天 |
| 6 | **历史消息存储** | A) 数据库持久化 B) 仅客户端 C) 混合 | **A**：数据库持久化，支持多设备同步和审计 |
| 7 | **是否支持图片输入** | A) 纯文本 B) 支持图片 C) 支持 Excel 文件 | **A**：先纯文本，后续迭代支持图片和文件 |
| 8 | **AI 功能入口权限** | A) 所有角色 B) ADMIN+EDITOR C) 仅 ADMIN D) 全局开关+角色开关 | **D**：管理员通过全局开关+角色开关控制，灵活管控 |

---

## 八、风险与对策

| 风险 | 影响 | 对策 |
|------|------|------|
| LLM 幻觉（编造数据） | 用户获取错误信息 | 系统提示强调"只用工具获取数据"；工具返回数据标注来源 |
| Prompt 注入攻击 | 绕过权限获取数据 | 系统提示不可覆盖；工具鉴权独立于 LLM；输入清洗 |
| Token 费用失控 | 成本超预期 | 用户级配额；模型温度调低减少冗余；本地模型降级 |
| 工具执行超时 | 用户体验差 | 工具执行超时 10s；超时返回友好提示；异步长时间操作 |
| 并发对话资源消耗 | 服务不稳定 | 单用户同时只允许 1 个活跃对话；请求队列 |
