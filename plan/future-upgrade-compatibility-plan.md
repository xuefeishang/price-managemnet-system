# 未来升级兼容性规划报告

## 审计概述

| 项目 | 内容 |
|------|------|
| 审计日期 | 2026-04-30 |
| 审计范围 | 后端架构 + 前端架构 + 依赖管理 + AI/API/MCP 扩展性 |
| 当前版本 | Spring Boot 4.0.6 + Java 25 |
| 目标 | 适配未来 3-5 年技术演进 |

---

## 一、当前架构评估

### 1.1 技术栈现状

| 层级 | 技术 | 版本 | 状态 |
|------|------|------|------|
| 后端框架 | Spring Boot | 4.0.6 | ✅ 最新稳定版 |
| Java | OpenJDK | 25 | ✅ 长期支持版 |
| 安全 | Spring Security | 7.0.5 | ✅ 最新稳定版 |
| 数据库 | MySQL | 8.4 | ✅ 兼容 |
| 缓存 | Redis | - | ✅ 已集成 |
| 前端框架 | Vue 3 | 3.x | ✅ 最新稳定版 |
| 构建工具 | Vite | 8.0.5 | ✅ 最新稳定版 |
| 图表 | ECharts | 5.x | ✅ 兼容 |

### 1.2 当前架构分层

```
┌─────────────────────────────────────────────────────────┐
│                    Frontend (Vue 3)                      │
│  Views (22) → API Layer (12) → Router → Store (Pinia)  │
└─────────────────────────────────────────────────────────┘
                           │ HTTP/REST
┌─────────────────────────────────────────────────────────┐
│                   Backend (Spring Boot 4)                │
│  Controller (13) → Service (12) → Repository (15)      │
│                        ↓                                 │
│  Entity (15) + DTO + Config + Util + Annotation        │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│                    Data Layer                            │
│         MySQL (JPA) + Redis (Cache)                      │
└─────────────────────────────────────────────────────────┘
```

### 1.3 现有组件清单

**后端控制器 (13):**
- AuthController - 认证
- UserController - 用户管理
- ProductController - 产品管理
- PriceController - 价格管理
- ProductCategoryController - 分类管理
- CustomerController - 客户管理
- OriginController - 产地管理
- SysDictController - 字典管理
- MenuController - 菜单管理
- OperationLogController - 日志管理
- ImportController - 导入导出
- ApprovalController - 审批流程
- StyleConfigController - 样式配置

**后端服务 (12):**
- UserService, ProductService, PriceService, ProductCategoryService
- CustomerService, OriginService, SysDictService, MenuItemService
- OperationLogService, ImportExportService, ApprovalService, StyleConfigService

**实体类 (15):**
- User, Product, Price, ProductCategory
- Customer, Origin, SysDict, MenuItem
- OperationLog, SyncLog, ApprovalWorkflow
- ApprovalNode, ApprovalRequest, ApprovalRecord, PriceHistory

---

## 二、差距分析

### 2.1 AI 功能扩展性

| 需求 | 当前状态 | 差距 |
|------|----------|------|
| AI 聊天/问答接口 | ❌ 无 | 需要 AI 服务抽象层 |
| LLM 集成 | ❌ 无 | 需要 LangChain4j/Spring AI 集成 |
| 向量数据库 | ❌ 无 | 需要 Milvus/Chroma 集成 |
| RAG 知识库 | ❌ 无 | 需要文档解析+向量存储 |
| AI 内容审核 | ❌ 无 | 需要 AI 服务封装 |

**影响：**
- 当前架构无法支持 AI 助手功能
- 无法实现智能价格分析
- 无法实现自然语言查询

### 2.2 API 服务扩展性

| 需求 | 当前状态 | 差距 |
|------|----------|------|
| RESTful API 文档 | ⚠️ 缺失 | 需要 OpenAPI/Swagger 集成 |
| API 版本管理 | ❌ 无 | 需要 URL 版本化或 Header 版本 |
| API 限流/熔断 | ❌ 无 | 需要 Resilience4j 集成 |
| API 监控 | ⚠️ 基础 | 需要 Micrometer + Prometheus |
| 外部 API 封装 | ❌ 无 | 需要 BFF 模式或 API Gateway |
| GraphQL | ❌ 无 | 无法满足灵活查询需求 |

**影响：**
- 无法提供外部合作伙伴 API
- 无法支持移动端高效数据请求
- API 变更管理困难

### 2.3 MCP 服务扩展性

| 需求 | 当前状态 | 差距 |
|------|----------|------|
| MCP Server 实现 | ❌ 无 | 需要实现 MCP 协议 |
| AI Agent 工具 | ❌ 无 | 需要提供 Tool 抽象 |
| 流式响应 | ⚠️ 基础 | SSE/WebSocket 需增强 |
| 工具注册中心 | ❌ 无 | 需要动态工具发现 |

**影响：**
- 无法被 Claude/GitHub Copilot 等 AI Agent 调用
- 无法实现 AI Agent 自动化操作
- 无法支持 AI 工作流编排

### 2.4 依赖版本管理

| 问题 | 当前状态 | 建议 |
|------|----------|------|
| BOM 管理 | ❌ 不完整 | 需要自定义 BOM 统一管理 |
| 传递依赖版本 | ⚠️ 手动覆盖 | 需要 dependencyManagement |
| 前端依赖版本 | ⚠️ 分散 | 需要统一版本常量 |
| 插件版本 | ⚠️ 分散 | 需要 maven plugin BOM |

---

## 三、升级方案

### 3.1 第一阶段：架构加固（1-2 周）

#### 3.1.1 统一依赖版本管理

**问题：** 当前 pom.xml 中部分依赖版本通过显式声明覆盖 BOM 版本，导致维护困难。

**方案：** 引入自定义 BOM 和 dependencyManagement

```xml
<!-- 在 pom.xml 中添加 dependencyManagement -->
<dependencyManagement>
    <dependencies>
        <!-- Spring Boot BOM 已包含大部分依赖 -->
        
        <!-- 自定义内部 BOM（可选，未来拆分微服务时） -->
        <dependency>
            <groupId>com.pricemanagement</groupId>
            <artifactId>price-management-bom</artifactId>
            <version>${project.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**迁移步骤：**
1. 清理所有显式版本声明（保留安全补丁版本）
2. 通过 Spring Boot BOM 管理传递依赖
3. 记录无法通过 BOM 管理的依赖及其原因

#### 3.1.2 API 文档增强

**方案：** 集成 SpringDoc OpenAPI

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.8.4</version>
</dependency>
```

**效果：**
- 自动生成 OpenAPI 3.0 文档
- Swagger UI 可视化
- 支持 AI Agent 发现 API 能力

#### 3.1.3 API 限流与监控

**方案：** 集成 Resilience4j + Micrometer

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.3.0</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

### 3.2 第二阶段：AI 集成准备（2-4 周）

#### 3.2.1 AI 服务抽象层

**目标：** 解耦 AI 实现，支持多 AI 提供商切换

```java
// AI 服务接口
public interface AIService {
    String chat(String prompt);
    String chat(String systemPrompt, String userPrompt);
    CompletableFuture<String> chatAsync(String prompt);
}

// 多提供商实现
@Service
public class OpenAIService implements AIService { ... }

@Service  
public class ClaudeService implements AIService { ... }

@Service
public class LocalLLMService implements AIService { ... }
```

#### 3.2.2 LangChain4j 集成

**方案：** 使用 LangChain4j 提供 AI 能力

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

**功能：**
- RAG 知识库
- 工具调用 (Tool Calling)
- 提示词模板管理
- 多模态支持

#### 3.2.3 MCP Server 实现

**方案：** 实现 MCP 协议端点

```java
@RestController
@RequestMapping("/api/mcp")
public class MCPController {
    // 工具列表
    @GetMapping("/tools")
    public List<MCPTool> listTools();
    
    // 工具调用
    @PostMapping("/invoke")
    public MCPToolResult invokeTool(@RequestBody MCPToolInvocation invocation);
}
```

**MCP 工具定义：**
```json
{
  "name": "price_query",
  "description": "查询产品价格",
  "inputSchema": {
    "type": "object",
    "properties": {
      "productName": {"type": "string"},
      "date": {"type": "string", "format": "date"}
    }
  }
}
```

### 3.3 第三阶段：API 服务增强（2-4 周）

#### 3.3.1 GraphQL 支持

**方案：** 添加 GraphQL 端点

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-graphql</artifactId>
</dependency>
```

**适用场景：**
- 移动端高效数据获取
- 复杂关联数据查询
- AI Agent 灵活数据获取

#### 3.3.2 API 版本管理

**方案：** URL 版本化 + Header 版本

```
/api/v1/products  (当前)
/api/v2/products  (未来)
/Accept: application/vnd.api.v2+json
```

### 3.4 第四阶段：前端架构增强（2-3 周）

#### 3.4.1 前端依赖版本统一

**方案：** 在 package.json 中集中管理版本

```json
{
  "dependencies": {
    "vue": "^3.5.0",
    "vite": "^8.0.5",
    "axios": "^1.15.0",
    "vant": "^4.9.0",
    "echarts": "^5.5.0",
    "vue-echarts": "^7.0.0",
    "pinia": "^2.3.0",
    "vue-router": "^4.5.0"
  }
}
```

#### 3.4.2 AI 组件库准备

**预留接口：**
- AIChatComponent - AI 聊天组件
- AIAssistantPanel - AI 助手面板
- AIPromptInput - 提示词输入组件

---

## 四、依赖升级路线图

### 4.1 2026 年内计划

| 时间 | 升级项 | 理由 |
|------|--------|------|
| 2026-Q3 | Spring Boot 4.1.x | 安全更新 + 新特性 |
| 2026-Q3 | Vue 3.6.x | Composition API 增强 |
| 2026-Q4 | Java 21 LTS | 性能提升 + 虚拟线程 |
| 2026-Q4 | LangChain4j 1.x → 2.x | AI 能力增强 |

### 4.2 2027 年计划

| 时间 | 升级项 | 理由 |
|------|--------|------|
| 2027-Q1 | Spring Boot 4.2.x | Jakarta EE 10 支持 |
| 2027-Q2 | 前端微前端架构 | 模块解耦 |
| 2027-Q3 | GraphQL 正式使用 | API 灵活性 |
| 2027-Q4 | 向量数据库集成 | RAG 知识库 |

---

## 五、实施优先级

### P0 - 必须立即修复（当前 sprint）

| 任务 | 负责人 | 预估工时 |
|------|--------|----------|
| pom.xml 依赖清理 | - | 2h |
| SpringDoc OpenAPI 集成 | - | 4h |
| Actuator 监控完善 | - | 2h |

### P1 - 高优先级（下一个 sprint）

| 任务 | 负责人 | 预估工时 |
|------|--------|----------|
| AI 服务抽象层接口设计 | - | 8h |
| MCP Server 协议实现 | - | 16h |
| API 限流配置 | - | 4h |

### P2 - 中优先级（未来 1-2 月）

| 任务 | 负责人 | 预估工时 |
|------|--------|----------|
| LangChain4j 集成 | - | 24h |
| GraphQL 端点 | - | 16h |
| 前端 AI 组件库 | - | 24h |

### P3 - 低优先级（未来规划）

| 任务 | 负责人 | 预估工时 |
|------|--------|----------|
| 向量数据库集成 | - | 32h |
| 多 AI 提供商切换 | - | 16h |
| 微前端架构改造 | - | 40h |

---

## 六、风险评估

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|----------|
| AI API 成本失控 | 中 | 高 | 预算控制 + 缓存 |
| 多 AI 提供商兼容性问题 | 中 | 中 | 抽象层隔离 |
| GraphQL 性能问题 | 低 | 高 | N+1 查询防护 |
| 依赖版本冲突 | 中 | 中 | 充分测试 |

---

## 七、验证清单

### 7.1 架构验证

- [ ] pom.xml 无版本冲突
- [ ] 所有传递依赖通过 BOM 管理
- [ ] OpenAPI 文档可访问
- [ ] Actuator 端点正常
- [ ] API 限流生效

### 7.2 AI 功能验证

- [ ] AI 服务抽象层可切换实现
- [ ] MCP 工具可被 AI Agent 发现
- [ ] 工具调用返回正确结果
- [ ] RAG 知识库检索准确

### 7.3 API 功能验证

- [ ] REST API 版本区分
- [ ] GraphQL 查询正常
- [ ] API 监控数据可采集

---

## 八、推荐行动

### 立即行动（本周）

1. **清理 pom.xml**：移除不必要的显式版本声明，保留安全补丁版本
2. **集成 SpringDoc**：添加 OpenAPI 文档支持
3. **完善 Actuator**：配置 Prometheus 监控端点

### 短期行动（1 个月内）

1. **AI 抽象层设计**：定义 AIService 接口
2. **MCP 协议实现**：实现基础 MCP Server
3. **API 限流**：集成 Resilience4j

### 中期规划（3-6 个月）

1. **LangChain4j 集成**：实现 RAG 知识库
2. **GraphQL 支持**：补充 GraphQL 端点
3. **Java 21 升级**：升级 JDK 版本

### 长期规划（6-12 个月）

1. **向量数据库**：引入 Milvus 或 Chroma
2. **多 AI 提供商**：支持 OpenAI/Claude/本地模型
3. **微前端架构**：拆分大型前端模块

---

## 九、结论

当前架构 **基础扎实**，但 **AI 扩展性不足**。通过以下改造可满足未来 3-5 年需求：

1. **短期**：加固 API 层，添加文档和监控
2. **中期**：引入 AI 抽象层和 MCP 协议
3. **长期**：支持多模态 AI 和向量搜索

**关键决策点：**
- 是否采用 GraphQL？建议小范围试点
- AI 提供商选择？建议优先支持 Claude（API 稳定）
- 向量数据库选择？建议使用 Chroma（轻量级）

---

## 附录

### A. 关键文件清单

| 文件路径 | 用途 |
|----------|------|
| `backend/pom.xml` | Maven 依赖管理 |
| `frontend/package.json` | NPM 依赖管理 |
| `backend/src/main/resources/application.yml` | 应用配置 |

### B. 参考资料

- [Spring Boot 4.0 升级审计报告](spring-boot-4.0.6-upgrade-audit-report.md)
- [Spring AI 官方文档](https://docs.spring.io/spring-ai/reference/)
- [LangChain4j 官方文档](https://docs.langchain4j.dev/)
- [MCP 协议规范](https://modelcontextprotocol.io/)
