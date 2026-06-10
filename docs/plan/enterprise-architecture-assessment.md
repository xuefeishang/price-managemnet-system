# 企业级应用架构评估报告

## 概述

**评估目标**：站在企业级应用系统角度，评估当前矿产品价格管理系统的架构成熟度，识别问题并提出可执行的解决方案，为未来向多模块、多租户、可配置审批流的企业级平台演进提供路线图。

**评估时间**：2026-06-05

**当前状态**：单业务线（价格管理）、单租户、硬编码审批流、单体架构

**当前评分**：6.5/10

**目标评分**：9.5+/10（行业标杆级）

---

## 一、架构评估总览

### 1.1 当前架构优势

| 维度 | 现状 | 评价 |
|------|------|------|
| 技术栈 | Spring Boot 4.0 + Vue 3 + MySQL 8.4 | ✅ 现代化、主流技术 |
| 认证体系 | JWT + API Key HMAC 双链 | ✅ 内外分离、安全性好 |
| 权限模型 | RBAC（用户-角色-权限） | ✅ 标准模型、支持扩展 |
| 数据字典 | sys_dict 集中管理 | ✅ 避免硬编码、易维护 |
| 缓存层 | Redis + 本地降级 | ✅ 高可用设计 |
| 前端架构 | Composition API + TypeScript | ✅ 可维护性强 |

### 1.2 关键差距（企业级不足）

| 维度 | 当前状态 | 企业级要求 | 差距等级 |
|------|----------|------------|----------|
| 多租户 | 单租户 | 租户隔离（数据/配置/权限） | 🔴 严重 |
| 模块化 | 单体紧耦合 | 业务模块独立、可插拔 | 🔴 严重 |
| 审批流配置 | 硬编码 WorkflowType 枚举 | 可视化流程设计器、动态节点 | 🟡 中等 |
| 组织架构 | 简单部门树 | 多维度组织、矩阵管理 | 🟡 中等 |
| 数据权限 | 无 | 行级数据隔离、部门/角色/自定义 | 🔴 严重 |
| 审计追踪 | 操作日志表 | 全链路审计、变更追溯 | 🟡 中等 |
| 服务治理 | 单体 | 微服务/模块化单体、服务发现 | 🟢 可接受 |
| 国际化 | 硬编码中文 | i18n 多语言 | 🟡 中等 |

### 1.3 评分对照表

| 阶段 | 评分 | 状态 |
|------|------|------|
| 当前 | 6.5 | 单体应用，功能完整 |
| Phase 1-2 完成（多租户+模块化+数据权限） | 8.0 | 企业级基础达标 |
| Phase 3 完成（DDD+事件驱动+可观测性） | 8.5 | 架构现代化 |
| Phase 4 完成（微服务+读写分离+安全合规） | 9.0 | 大厂标准 |
| **9.5+ 全部补齐** | 9.5+ | **行业标杆级** |

---

## 二、问题深度分析

### 2.1 多租户缺失（严重）

**现状**：
- 所有实体无 `tenant_id` 字段
- 数据库无租户隔离机制
- 用户、产品、价格等数据全局共享

**影响**：
- 无法支撑多客户/多子公司独立运营
- 数据安全风险：客户A可看到客户B的产品
- 无法提供 SaaS 化服务

**典型问题代码**：
```java
// backend/src/main/java/com/pricemanagement/entity/Product.java
@Entity
@Table(name = "product")
public class Product {
    @Id
    private Long id;
    // ❌ 缺少 tenant_id 字段
    private String name;
    // ...
}
```

### 2.2 模块边界模糊（严重）

**现状**：
- 包名 `com.pricemanagement` 缺乏模块划分
- Service 层直接跨域调用（ApprovalService 直接操作 ProductRepository、PriceRepository）
- 前端视图按功能分，但无模块隔离

**影响**：
- 难以拆分为独立部署的业务模块
- 代码耦合度高，牵一发动全身
- 无法按模块独立迭代、测试

**典型问题代码**：
```java
// backend/src/main/java/com/pricemanagement/service/ApprovalService.java
@Service
public class ApprovalService {
    private final ProductRepository productRepository;      // ❌ 跨域直接依赖
    private final PriceRepository priceRepository;          // ❌ 跨域直接依赖
    private final PriceHistoryRepository priceHistoryRepository; // ❌ 跨域直接依赖
    // ...
}
```

### 2.3 审批流硬编码（中等）

**现状**：
- `ApprovalWorkflow.WorkflowType` 枚举硬编码业务类型
- 新增业务类型需修改代码、重新部署
- 无法通过配置动态扩展

**影响**：
- 无法支持"自定义审批流程"需求
- 每次新增业务类型都需要发版

**典型问题代码**：
```java
// backend/src/main/java/com/pricemanagement/entity/ApprovalWorkflow.java:58
public enum WorkflowType {
    PRICE_CHANGE,    // 价格变更
    PRODUCT_CREATE   // 产品创建
    // ❌ 新增类型必须改代码
}

// backend/src/main/java/com/pricemanagement/service/ApprovalService.java:207-209
ApprovalWorkflow.WorkflowType workflowType = request.getBusinessType() == ApprovalRequest.BusinessType.PRICE
        ? ApprovalWorkflow.WorkflowType.PRICE_CHANGE
        : ApprovalWorkflow.WorkflowType.PRODUCT_CREATE;
// ❌ 硬编码映射关系
```

### 2.4 数据权限缺失（严重）

**现状**：
- 所有用户可访问所有数据（受角色功能权限控制，无数据范围限制）
- 无法实现"部门用户只能看本部门产品"
- 无法实现"分公司只能管理自己区域的价格"

**影响**：
- 数据越权访问风险
- 无法满足多层级组织管理需求

### 2.5 国际化硬编码（中等）

**现状**：
- 代码中大量硬编码中文字符串
- 异常消息、提示文本未抽取
- 前端部分国际化，后端完全未处理

**典型问题代码**：
```java
// backend/src/main/java/com/pricemanagement/service/ApprovalService.java
throw new IllegalStateException("未找到可用的审批工作流");
throw new IllegalArgumentException("审批人不存在: " + approverId);
// ❌ 硬编码中文消息
```

---

## 三、解决方案（达到 8.0 分）

### 3.1 多租户改造方案

#### 方案概述

采用 **共享数据库 + 租户字段隔离** 模式，兼顾成本与隔离性。

#### 实施步骤

**Phase 1：数据库改造**

```sql
-- 1. 新增租户表
CREATE TABLE sys_tenant (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_code VARCHAR(50) UNIQUE NOT NULL COMMENT '租户编码',
    tenant_name VARCHAR(100) NOT NULL COMMENT '租户名称',
    status VARCHAR(20) DEFAULT 'ACTIVE',
    expire_time DATETIME COMMENT '过期时间',
    created_time DATETIME,
    updated_time DATETIME
);

-- 2. 业务表添加 tenant_id
ALTER TABLE product ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID';
ALTER TABLE price ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE sys_user ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 1;
-- ... 所有业务表

-- 3. 添加复合索引
CREATE INDEX idx_product_tenant ON product(tenant_id, id);
CREATE INDEX idx_price_tenant ON price(tenant_id, product_id);
```

**Phase 2：后端租户上下文**

```java
// 新建租户上下文
public class TenantContext {
    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();
    
    public static void setTenantId(Long tenantId) {
        CURRENT_TENANT.set(tenantId);
    }
    
    public static Long getTenantId() {
        return CURRENT_TENANT.get();
    }
    
    public static void clear() {
        CURRENT_TENANT.remove();
    }
}

// 实体基类
@MappedSuperclass
public abstract class BaseEntity {
    @Column(name = "tenant_id")
    private Long tenantId;
    
    @PrePersist
    public void prePersist() {
        if (this.tenantId == null) {
            this.tenantId = TenantContext.getTenantId();
        }
    }
}

// Hibernate Filter 自动注入租户条件
@Entity
@Table(name = "product")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = Long.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Product extends BaseEntity {
    // ...
}
```

**Phase 3：租户拦截器**

```java
@Component
public class TenantInterceptor implements HandlerInterceptor {
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 从 JWT 或 Header 获取租户ID
        Long tenantId = extractTenantId(request);
        TenantContext.setTenantId(tenantId);
        return true;
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                                 Object handler, Exception ex) {
        TenantContext.clear();
    }
}
```

#### 改造优先级

| 优先级 | 范围 | 工作量 |
|--------|------|--------|
| P0 | 核心业务表（product, price, sys_user） | 2周 |
| P1 | 审批流、字典、日志表 | 1周 |
| P2 | 其他附属表 | 1周 |

---

### 3.2 模块化重构方案

#### 目标架构

```
price-management-system/
├── modules/
│   ├── price-core/           # 价格核心模块（未来独立服务）
│   │   ├── domain/
│   │   ├── application/
│   │   ├── infrastructure/
│   │   └── api/
│   ├── approval-engine/      # 审批引擎模块（通用）
│   │   ├── domain/
│   │   ├── application/
│   │   └── spi/              # 业务扩展点
│   ├── notification-center/  # 通知中心模块
│   ├── system-admin/         # 系统管理模块（用户/角色/权限）
│   └── tenant-management/    # 租户管理模块
├── platform/
│   ├── common/               # 公共组件
│   ├── security/             # 安全组件
│   └── gateway/              # API 网关（未来微服务入口）
└── starter/
    └── application/          # 启动模块
```

#### 模块依赖原则

```
┌─────────────────────────────────────────────────────────┐
│                    Application Layer                     │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐    │
│  │ Price   │  │Approval │  │Notify   │  │ System  │    │
│  │ Module  │  │ Engine  │  │ Center  │  │ Admin   │    │
│  └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘    │
│       │            │            │            │          │
│       └────────────┴────────────┴────────────┘          │
│                          │                              │
│                          ▼                              │
│              ┌───────────────────────┐                  │
│              │   Platform Common     │                  │
│              │   (SPI / Events)      │                  │
│              └───────────────────────┘                  │
└─────────────────────────────────────────────────────────┘

原则：
1. 模块间通过 SPI/Event 解耦，禁止直接依赖
2. 审批引擎作为通用组件，通过 SPI 接入业务
3. 通知中心通过事件订阅，不侵入业务模块
```

#### 审批引擎 SPI 设计

```java
// 审批引擎 SPI - 业务模块实现此接口
public interface ApprovalBusinessHandler {
    // 业务类型标识
    String getBusinessType();
    
    // 审批通过回调
    void onApproved(ApprovalContext context);
    
    // 审批拒绝回调
    void onRejected(ApprovalContext context);
    
    // 获取业务详情（用于通知）
    ApprovalBusinessDetail getDetail(Long businessId);
}

// 价格模块实现
@Component
public class PriceApprovalHandler implements ApprovalBusinessHandler {
    @Override
    public String getBusinessType() {
        return "PRICE_CHANGE";
    }
    
    @Override
    public void onApproved(ApprovalContext context) {
        // 价格变更生效逻辑
        priceService.executePriceChange(context.getBusinessId(), context.getRequestData());
    }
    // ...
}
```

#### 重构路径

| 阶段 | 目标 | 方式 |
|------|------|------|
| 阶段一 | 解耦审批与业务 | 引入 SPI，ApprovalService 调用 Handler |
| 阶段二 | 模块边界划分 | 按 package 模块化，内部高内聚 |
| 阶段三 | 独立模块部署 | Maven 多模块，按需打包 |

---

### 3.3 可配置审批流方案

#### 目标

- 支持管理后台可视化配置审批流
- 动态扩展业务类型（无需改代码）
- 支持条件分支、并行审批、会签等高级场景

#### 数据模型改造

```sql
-- 工作流定义表（去除硬编码类型）
CREATE TABLE approval_workflow_v2 (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    workflow_code VARCHAR(50) UNIQUE NOT NULL COMMENT '流程编码',
    workflow_name VARCHAR(100) NOT NULL,
    business_type VARCHAR(50) NOT NULL COMMENT '业务类型：可配置',
    trigger_condition JSON COMMENT '触发条件（JSON）',
    version INT DEFAULT 1,
    is_active BOOLEAN DEFAULT TRUE,
    created_time DATETIME,
    updated_time DATETIME
);

-- 流程节点表（支持多种节点类型）
CREATE TABLE approval_node_v2 (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    workflow_id BIGINT NOT NULL,
    node_code VARCHAR(50) NOT NULL,
    node_name VARCHAR(100) NOT NULL,
    node_type VARCHAR(20) NOT NULL COMMENT 'START/APPROVER/NOTIFIER/CONDITION/END',
    assignee_type VARCHAR(20) COMMENT 'ROLE/USER/DEPT_LEADER/EXPRESSION',
    assignee_value VARCHAR(200) COMMENT '角色编码/用户ID/表达式',
    condition_expression TEXT COMMENT '条件表达式（CONDITION节点）',
    timeout_hours INT COMMENT '超时时间（小时）',
    auto_approve_timeout BOOLEAN DEFAULT FALSE,
    node_order INT NOT NULL,
    created_time DATETIME
);

-- 流程分支表（支持条件分支）
CREATE TABLE approval_branch (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    workflow_id BIGINT NOT NULL,
    from_node_id BIGINT NOT NULL,
    to_node_id BIGINT NOT NULL,
    condition_expr TEXT COMMENT '分支条件表达式',
    branch_order INT DEFAULT 0
);
```

#### 业务类型配置化

```java
// 业务类型注册表（替代枚举）
@Component
public class BusinessTypeRegistry {
    private final Map<String, BusinessTypeDefinition> types = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void init() {
        // 从数据库或配置文件加载
        register("PRICE_CHANGE", "价格变更审批", "priceApprovalHandler");
        register("PRODUCT_CREATE", "产品创建审批", "productApprovalHandler");
        // 新增业务类型只需配置，无需改代码
    }
    
    public void register(String code, String name, String handlerBean) {
        types.put(code, new BusinessTypeDefinition(code, name, handlerBean));
    }
    
    public BusinessTypeDefinition get(String code) {
        return types.get(code);
    }
}

// 管理接口：动态新增业务类型
@RestController
@RequestMapping("/api/admin/business-types")
public class BusinessTypeController {
    @PostMapping
    public Result<Void> register(@RequestBody BusinessTypeRequest request) {
        businessTypeRegistry.register(request.getCode(), request.getName(), request.getHandler());
        return Result.success();
    }
}
```

#### 流程设计器集成

推荐方案：集成 **Activiti** 或 **Camunda** 流程引擎

| 方案 | 优势 | 劣势 | 适用场景 |
|------|------|------|----------|
| 自研轻量引擎 | 简单可控、易定制 | 功能有限 | 当前需求足够，无需复杂流程 |
| Activiti | 成熟稳定、可视化设计器 | 较重、学习成本 | 需要复杂流程编排 |
| Camunda | 云原生、功能强大 | 商业版收费 | 企业级复杂场景 |

**建议**：当前阶段采用自研轻量引擎 + 配置化，预留 Activiti 集成接口。

---

### 3.4 数据权限方案

#### 数据权限模型

```sql
-- 数据权限规则表
CREATE TABLE sys_data_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL COMMENT '规则名称',
    target_entity VARCHAR(100) NOT NULL COMMENT '目标实体',
    scope_type VARCHAR(20) NOT NULL COMMENT '范围类型：ALL/DEPT/DEPT_TREE/CUSTOM',
    scope_value TEXT COMMENT '范围值：部门ID列表/自定义SQL',
    created_time DATETIME
);

-- 角色数据权限关联
CREATE TABLE sys_role_data_permission (
    role_id BIGINT NOT NULL,
    data_permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, data_permission_id)
);
```

#### 实现方案

```java
// 数据权限注解
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DataPermission {
    String entity();           // 目标实体
    String deptField();        // 部门字段
    String creatorField() default "createdBy";  // 创建人字段
}

// 数据权限切面
@Aspect
@Component
public class DataPermissionAspect {
    
    @Around("@annotation(dataPermission)")
    public Object applyDataPermission(ProceedingJoinPoint pjp, DataPermission dataPermission) {
        // 获取当前用户的数据权限范围
        Set<Long> deptIds = getCurrentUserDataScope();
        
        // 注入到查询条件
        DataPermissionContext.setScope(dataPermission.entity(), deptIds);
        
        try {
            return pjp.proceed();
        } finally {
            DataPermissionContext.clear();
        }
    }
}

// Repository 层自动追加条件
public class DataPermissionRepositoryImpl<T, ID> extends SimpleJpaRepository<T, ID> {
    
    @Override
    protected Specification<T> applyDataPermission(Specification<T> spec) {
        DataScope scope = DataPermissionContext.getScope(entityInformation.getJavaType());
        if (scope != null) {
            spec = spec.and((root, query, cb) -> 
                root.get("deptId").in(scope.getDeptIds())
            );
        }
        return spec;
    }
}
```

---

### 3.5 国际化改造方案

#### 后端国际化

```java
// 消息源配置
@Configuration
public class I18nConfig {
    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasenames("i18n/messages", "i18n/errors", "i18n/validations");
        source.setDefaultEncoding("UTF-8");
        return source;
    }
}

// 消息工具类
@Component
public class I18nUtil {
    @Autowired
    private MessageSource messageSource;
    
    public String getMessage(String code, Object... args) {
        Locale locale = LocaleContextHolder.getLocale();
        return messageSource.getMessage(code, args, locale);
    }
}

// 异常消息国际化
public class BusinessException extends RuntimeException {
    public BusinessException(String code, Object... args) {
        super(I18nUtil.getMessage(code, args));
    }
}

// 使用方式
throw new BusinessException("error.approval.not_found", requestId);
```

#### 资源文件

```properties
# i18n/messages_zh_CN.properties
error.approval.not_found=审批请求不存在: {0}
error.tenant.invalid=无效的租户标识

# i18n/messages_en_US.properties
error.approval.not_found=Approval request not found: {0}
error.tenant.invalid=Invalid tenant identifier
```

---

## 四、9.5+ 标杆级升级方案

### 4.1 架构层面升级（+1.5分）

#### 4.1.1 领域驱动设计（DDD）

| 缺失项 | 现状 | 9.5+ 标准 | 实施 |
|--------|------|-----------|------|
| 分层架构 | Service 直接操作 Repository | DDD 分层：Domain/Application/Infrastructure，聚合根、值对象、领域事件 | 重构核心模块为 DDD 模式 |
| 领域模型 | 贫血模型 | 充血模型，业务逻辑内聚于聚合根 | 领域对象行为封装 |
| 值对象 | 无 | 金额、计量单位等封装为值对象 | 引入值对象类型 |

**DDD 重构示例**：

```java
// 聚合根：Product（价格管理领域）
@Entity
public class Product extends AggregateRoot<Product> {
    
    @Embedded
    private ProductName name;           // 值对象
    
    @Embedded
    private Money sellingPrice;         // 值对象（金额）
    
    @Embedded
    private Currency currency;          // 值对象
    
    private ProductStatus status;
    
    // 行为内聚，而非贫血的 getter/setter
    public void updatePrice(Money newPrice, PriceChangeReason reason) {
        // 业务规则校验
        if (this.status == ProductStatus.DISCONTINUED) {
            throw new BusinessException("product.discontinued");
        }
        
        // 发布领域事件
        registerEvent(new PriceChangedEvent(this.id, this.sellingPrice, newPrice, reason));
        
        this.sellingPrice = newPrice;
    }
    
    // 工厂方法
    public static Product create(ProductName name, ProductCategory category) {
        Product product = new Product();
        product.name = name;
        product.category = category;
        product.status = ProductStatus.ACTIVE;
        product.registerEvent(new ProductCreatedEvent(product));
        return product;
    }
}

// 值对象：Money
@Embeddable
public class Money implements ValueObject {
    
    @Column(precision = 15, scale = 4)
    private BigDecimal amount;
    
    @Column(length = 3)
    private String currency;  // CNY, USD, EUR
    
    // 值对象不可变
    private Money() {}
    
    public static Money of(BigDecimal amount, String currency) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Invalid amount");
        }
        Money money = new Money();
        money.amount = amount;
        money.currency = currency;
        return money;
    }
    
    // 行为
    public Money multiply(BigDecimal multiplier) {
        return Money.of(this.amount.multiply(multiplier), this.currency);
    }
    
    public Money add(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException("Currency mismatch");
        }
        return Money.of(this.amount.add(other.amount), this.currency);
    }
}
```

**DDD 目录结构**：

```
modules/price-core/
├── domain/                          # 领域层
│   ├── model/
│   │   ├── aggregate/               # 聚合根
│   │   │   ├── Product.java
│   │   │   └── Price.java
│   │   ├── entity/                  # 实体
│   │   ├── valueobject/             # 值对象
│   │   │   ├── Money.java
│   │   │   ├── ProductName.java
│   │   │   └── Currency.java
│   │   └── event/                   # 领域事件
│   │       ├── PriceChangedEvent.java
│   │       └── ProductCreatedEvent.java
│   ├── repository/                  # 仓储接口（领域层定义）
│   │   └── ProductRepository.java
│   └── service/                     # 领域服务
│       └── PriceCalculationService.java
├── application/                     # 应用层
│   ├── service/
│   │   ├── ProductApplicationService.java
│   │   └── PriceApplicationService.java
│   ├── command/                     # 命令（CQRS）
│   │   ├── CreateProductCommand.java
│   │   └── UpdatePriceCommand.java
│   ├── query/                       # 查询（CQRS）
│   │   └── ProductQueryService.java
│   └── dto/
├── infrastructure/                  # 基础设施层
│   ├── persistence/
│   │   ├── ProductRepositoryImpl.java
│   │   └── ProductPO.java          # 持久化对象
│   ├── messaging/
│   │   └── DomainEventPublisher.java
│   └── external/
│       └── ErpIntegrationAdapter.java
└── api/                             # 接口层
    ├── controller/
    └── assembler/                   # DTO 组装器
```

#### 4.1.2 事件驱动架构

| 缺失项 | 现状 | 9.5+ 标准 | 实施 |
|--------|------|-----------|------|
| 领域事件 | 无 | 模块间通过事件解耦，最终一致性 | Spring Event / Kafka |
| 事件溯源 | 无 | 关键业务事件可追溯、可重放 | Event Sourcing 模式 |
| Saga 编排 | 无 | 跨聚合事务协调 | Saga 模式 |

**事件驱动架构**：

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Event-Driven Architecture                    │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────┐     PriceChangedEvent     ┌──────────────┐        │
│  │ Price Module │ ─────────────────────────▶│Notification  │        │
│  │              │                            │   Center     │        │
│  │  (发布事件)   │◀───────────────────────────│ (订阅事件)   │        │
│  └──────────────┘     NotificationSentEvent  └──────────────┘        │
│         │                                    │                       │
│         │ PriceChangedEvent                  │                       │
│         ▼                                    ▼                       │
│  ┌──────────────┐                    ┌──────────────┐               │
│  │Approval      │                    │Analytics     │               │
│  │  Engine      │                    │  Module      │               │
│  │(订阅审批结果) │                    │(订阅所有事件) │               │
│  └──────────────┘                    └──────────────┘               │
│                                                                      │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │              Event Bus (Spring Event / Kafka)                │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

**事件发布示例**：

```java
// 领域事件基类
public abstract class DomainEvent {
    private final UUID eventId;
    private final LocalDateTime occurredAt;
    private final String aggregateType;
    private final String aggregateId;
    
    protected DomainEvent(String aggregateType, String aggregateId) {
        this.eventId = UUID.randomUUID();
        this.occurredAt = LocalDateTime.now();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
    }
}

// 价格变更事件
public class PriceChangedEvent extends DomainEvent {
    private final Long productId;
    private final Money oldPrice;
    private final Money newPrice;
    private final String reason;
    
    public PriceChangedEvent(Long productId, Money oldPrice, Money newPrice, String reason) {
        super("Product", productId.toString());
        this.productId = productId;
        this.oldPrice = oldPrice;
        this.newPrice = newPrice;
        this.reason = reason;
    }
}

// 事件发布器
@Component
public class DomainEventPublisher {
    
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    
    @Autowired
    private EventStoreRepository eventStoreRepository;
    
    @Transactional
    public void publishAll(List<DomainEvent> events) {
        for (DomainEvent event : events) {
            // 1. 持久化事件（Event Sourcing）
            eventStoreRepository.save(EventStore.from(event));
            
            // 2. 发布到 Spring Event Bus
            eventPublisher.publishEvent(event);
            
            // 3. 可选：发布到 Kafka（跨服务）
            kafkaTemplate.send("domain-events", event);
        }
    }
}

// 事件订阅者
@Component
public class NotificationEventHandler {
    
    @EventListener
    @Async
    public void onPriceChanged(PriceChangedEvent event) {
        // 发送价格变更通知
        notificationService.sendPriceChangeNotification(
            event.getProductId(),
            event.getOldPrice(),
            event.getNewPrice()
        );
    }
}
```

#### 4.1.3 CQRS 模式

| 缺失项 | 现状 | 9.5+ 标准 | 实施 |
|--------|------|-----------|------|
| 读写分离 | 读写混合 | 命令查询分离，复杂查询走独立读模型 | CQRS + Read Model |
| 查询优化 | 直接查主库 | 读模型针对查询场景优化（ES/ClickHouse） | 读写数据源分离 |

**CQRS 架构**：

```
┌─────────────────────────────────────────────────────────────────────┐
│                          CQRS Architecture                           │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│   ┌─────────────┐         ┌─────────────┐                           │
│   │   Command   │         │   Query     │                           │
│   │   (写操作)   │         │   (读操作)   │                           │
│   └──────┬──────┘         └──────┬──────┘                           │
│          │                       │                                  │
│          ▼                       ▼                                  │
│   ┌─────────────┐         ┌─────────────┐                           │
│   │  Command    │         │   Query     │                           │
│   │  Handler    │         │   Handler   │                           │
│   └──────┬──────┘         └──────┬──────┘                           │
│          │                       │                                  │
│          ▼                       ▼                                  │
│   ┌─────────────┐         ┌─────────────┐                           │
│   │   Write     │         │   Read      │                           │
│   │   Model     │         │   Model     │                           │
│   │  (MySQL)    │         │(ES/Redis)   │                           │
│   └──────┬──────┘         └─────────────┘                           │
│          │                                                          │
│          │  Event Synchronization                                   │
│          ▼                                                          │
│   ┌─────────────┐                                                   │
│   │ Event Store │                                                   │
│   └─────────────┘                                                   │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

**CQRS 实现示例**：

```java
// Command（命令）
public class UpdatePriceCommand {
    private final Long productId;
    private final BigDecimal newPrice;
    private final String currency;
    private final String reason;
}

// Command Handler
@Component
public class UpdatePriceCommandHandler {
    
    @Transactional
    public void handle(UpdatePriceCommand command) {
        // 1. 加载聚合
        Product product = productRepository.findById(command.getProductId());
        
        // 2. 执行业务逻辑
        product.updatePrice(
            Money.of(command.getNewPrice(), command.getCurrency()),
            PriceChangeReason.of(command.getReason())
        );
        
        // 3. 持久化
        productRepository.save(product);
        
        // 4. 发布事件（同步到读模型）
        eventPublisher.publishAll(product.getDomainEvents());
    }
}

// Query Model（读模型 - 针对查询优化）
@Document(indexName = "product_read_model")
public class ProductReadModel {
    @Id
    private String id;
    
    @Field(type = FieldType.Keyword)
    private String name;
    
    @Field(type = FieldType.Keyword)
    private String categoryName;
    
    @Field(type = FieldType.Double)
    private Double currentPrice;
    
    @Field(type = FieldType.Keyword)
    private String currency;
    
    @Field(type = FieldType.Double)
    private Double priceChangeRate;  // 预计算字段
    
    @Field(type = FieldType.Date)
    private LocalDateTime lastPriceUpdateTime;
}

// Query Service
@Service
public class ProductQueryService {
    
    @Autowired
    private ProductReadRepository readRepository;  // Elasticsearch
    
    public Page<ProductReadModel> searchProducts(ProductQuery query) {
        // 直接查询读模型，性能优化
        NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
            .withQuery(QueryBuilders.boolQuery()
                .must(QueryBuilders.matchQuery("name", query.getKeyword()))
                .filter(QueryBuilders.termQuery("categoryName", query.getCategory()))
            )
            .withSort(SortBuilders.fieldSort("currentPrice").order(SortOrder.ASC))
            .withPageable(PageRequest.of(query.getPage(), query.getSize()))
            .build();
            
        return readRepository.search(searchQuery);
    }
}

// 读模型同步（事件监听）
@Component
public class ProductReadModelSyncer {
    
    @EventListener
    @Async
    public void onPriceChanged(PriceChangedEvent event) {
        // 更新读模型
        ProductReadModel readModel = readRepository.findById(event.getProductId())
            .orElse(new ProductReadModel());
        
        readModel.setCurrentPrice(event.getNewPrice().getAmount().doubleValue());
        readModel.setLastPriceUpdateTime(LocalDateTime.now());
        
        // 计算价格变化率（预计算）
        if (readModel.getCurrentPrice() != null && event.getOldPrice() != null) {
            double changeRate = (event.getNewPrice().getAmount().doubleValue() 
                - event.getOldPrice().getAmount().doubleValue()) 
                / event.getOldPrice().getAmount().doubleValue() * 100;
            readModel.setPriceChangeRate(changeRate);
        }
        
        readRepository.save(readModel);
    }
}
```

#### 4.1.4 API 版本管理

| 缺失项 | 现状 | 9.5+ 标准 | 实施 |
|--------|------|-----------|------|
| 版本策略 | 无 | URL 版本化 + Header 版本协商 | `/api/v1/` + 版本路由 |
| 平滑升级 | 无 | 多版本共存，灰度迁移 | 版本路由 + 废弃公告 |

```java
// API 版本注解
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiVersion {
    int value();
    boolean deprecated() default false;
    String sunsetDate() default "";
}

// 版本路由
@RestController
@RequestMapping("/api")
public class ProductController {
    
    @GetMapping("/v1/products/{id}")
    @ApiVersion(1)
    public ProductDTOV1 getProductV1(@PathVariable Long id) {
        // V1 实现
    }
    
    @GetMapping("/v2/products/{id}")
    @ApiVersion(2)
    public ProductDTOV2 getProductV2(@PathVariable Long id) {
        // V2 实现（新增字段）
    }
    
    @GetMapping(value = "/products/{id}", headers = "X-API-Version=2")
    public ProductDTOV2 getProductByHeader(@PathVariable Long id) {
        // Header 版本协商
        return getProductV2(id);
    }
}

// 版本废弃提示
@Deprecated(since = "2026-06-01", forRemoval = true)
@GetMapping("/v1/products/{id}")
@ApiVersion(value = 1, deprecated = true, sunsetDate = "2026-12-01")
public ProductDTOV1 getProductV1(@PathVariable Long id) {
    response.setHeader("Deprecation", "true");
    response.setHeader("Sunset", "Sat, 01 Dec 2026 00:00:00 GMT");
    response.setHeader("Link", "</api/v2/products>; rel=\"successor-version\"");
    // ...
}
```

---

### 4.2 数据层面升级（+1.0分）

#### 4.2.1 读写分离

| 缺失项 | 现状 | 9.5+ 标准 | 实施 |
|--------|------|-----------|------|
| 数据源 | 单 MySQL | 主库写、从库读，自动路由 | MySQL 主从 + Dynamic DataSource |
| 负载均衡 | 无 | 读请求分发到多个从库 | 负载均衡策略 |

**读写分离配置**：

```yaml
# application.yml
spring:
  datasource:
    dynamic:
      primary: master  # 默认主库
      strict: false    # 允许非严格模式
      datasource:
        master:
          url: jdbc:mysql://master.db:3306/price_management
          username: root
          password: ${DB_PASSWORD}
        slave-1:
          url: jdbc:mysql://slave-1.db:3306/price_management
          username: root
          password: ${DB_PASSWORD}
        slave-2:
          url: jdbc:mysql://slave-2.db:3306/price_management
          username: root
          password: ${DB_PASSWORD}
```

```java
// 读写分离注解
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DataSource {
    String value() default "master";  // master / slave
}

// 读操作走从库
@Service
public class ProductQueryService {
    
    @DataSource("slave")
    public Page<Product> listProducts(ProductQuery query) {
        return productRepository.findAll(query.toSpecification(), query.toPageable());
    }
}

// 写操作走主库
@Service
public class ProductService {
    
    @DataSource("master")
    @Transactional
    public Product createProduct(CreateProductCommand command) {
        Product product = Product.create(command.getName(), command.getCategory());
        return productRepository.save(product);
    }
}
```

#### 4.2.2 数据归档

| 缺失项 | 现状 | 9.5+ 标准 | 实施 |
|--------|------|-----------|------|
| 历史数据处理 | 永久保留 | 冷数据自动归档（>3年），降低主库负担 | 归档策略 + 历史库 |
| 数据生命周期 | 无管理 | 自动归档 + 按需查询 | 生命周期管理 |

```sql
-- 归档表结构
CREATE TABLE price_archive (
    id BIGINT PRIMARY KEY,
    product_id BIGINT,
    current_price DECIMAL(15,4),
    effective_date DATE,
    original_created_time DATETIME,
    archived_time DATETIME,
    archive_batch VARCHAR(50),
    INDEX idx_archive_product_date (product_id, effective_date)
) ENGINE=InnoDB;

-- 归档存储过程
DELIMITER //
CREATE PROCEDURE archive_price_data(IN years_to_keep INT)
BEGIN
    DECLARE archive_date DATE;
    SET archive_date = DATE_SUB(CURDATE(), INTERVAL years_to_keep YEAR);
    
    -- 插入归档表
    INSERT INTO price_archive
    SELECT *, NOW(), CONCAT('archive_', DATE_FORMAT(NOW(), '%Y%m%d'))
    FROM price
    WHERE effective_date < archive_date;
    
    -- 删除主表数据
    DELETE FROM price
    WHERE effective_date < archive_date;
    
    -- 记录归档日志
    INSERT INTO archive_log (archive_date, records_archived, archive_batch)
    VALUES (archive_date, ROW_COUNT(), CONCAT('archive_', DATE_FORMAT(NOW(), '%Y%m%d')));
END //
DELIMITER ;

-- 定时任务（每年执行）
-- CALL archive_price_data(3);
```

#### 4.2.3 向量数据库（AI 增强）

| 缺失项 | 现状 | 9.5+ 标准 | 实施 |
|--------|------|-----------|------|
| 语义搜索 | 无 | 产品知识库语义搜索、智能问答 | PostgreSQL pgvector / Pinecone |
| RAG 支持 | 无 | AI 基于知识库回答业务问题 | 向量 Embedding + LLM |

```java
// 向量存储
@Entity
@Table(name = "document_embedding")
public class DocumentEmbedding {
    @Id
    private Long id;
    
    private Long documentId;
    
    @Column(columnDefinition = "vector(1536)")  // OpenAI embedding dimension
    private float[] embedding;
    
    private String contentType;  // PRODUCT / PRICE_HISTORY / POLICY
    
    private String contentChunk;  // 文本片段
    
    private LocalDateTime createdTime;
}

// 向量搜索服务
@Service
public class VectorSearchService {
    
    @Autowired
    private EmbeddingClient embeddingClient;  // OpenAI / 本地模型
    
    @Autowired
    private DocumentEmbeddingRepository embeddingRepository;
    
    public List<DocumentEmbedding> semanticSearch(String query, int topK) {
        // 1. 查询文本转向量
        float[] queryVector = embeddingClient.embed(query);
        
        // 2. 向量相似度搜索
        return embeddingRepository.findNearest(queryVector, topK);
    }
    
    public void indexDocument(Long documentId, String content, String type) {
        // 1. 文本分块
        List<String> chunks = textSplitter.split(content, 500);
        
        // 2. 批量向量化
        for (String chunk : chunks) {
            float[] embedding = embeddingClient.embed(chunk);
            
            DocumentEmbedding doc = new DocumentEmbedding();
            doc.setDocumentId(documentId);
            doc.setContentType(type);
            doc.setContentChunk(chunk);
            doc.setEmbedding(embedding);
            
            embeddingRepository.save(doc);
        }
    }
}

// RAG 问答
@Service
public class RAGQAService {
    
    @Autowired
    private VectorSearchService vectorSearchService;
    
    @Autowired
    private LLMClient llmClient;
    
    public String answerQuestion(String question) {
        // 1. 检索相关文档
        List<DocumentEmbedding> relevantDocs = vectorSearchService.semanticSearch(question, 5);
        
        // 2. 构建上下文
        String context = relevantDocs.stream()
            .map(DocumentEmbedding::getContentChunk)
            .collect(Collectors.joining("\n\n"));
        
        // 3. 调用 LLM 生成答案
        String systemPrompt = """
            你是一个价格管理系统的智能助手。
            请基于以下上下文回答用户问题。
            如果上下文中没有相关信息，请诚实地说"我不知道"。
            
            上下文：
            %s
            """.formatted(context);
        
        return llmClient.chat(systemPrompt, question);
    }
}
```

#### 4.2.4 变更数据捕获（CDC）

| 缺失项 | 现状 | 9.5+ 标准 | 实施 |
|--------|------|-----------|------|
| 数据变更追踪 | price_history 表 | 全实体变更追溯，类似 Git 版本 | Debezium CDC |
| 审计追溯 | 操作日志 | 数据级变更历史，任意时间点恢复 | Event Sourcing |

```yaml
# Debezium 配置
debezium:
  source:
    connector.class: io.debezium.connector.mysql.MySqlConnector
    database.hostname: mysql
    database.port: 3306
    database.user: debezium
    database.password: ${DEBEZIUM_PASSWORD}
    database.server.id: 184054
    database.server.name: price_management
    database.include.list: price_management
    table.include.list: price_management.product,price_management.price
    database.history.kafka.bootstrap.servers: kafka:9092
    database.history.kafka.topic: schema-changes.price_management
```

```java
// CDC 事件处理
@Component
public class CDCEventHandler {
    
    @KafkaListener(topics = "price_management.price_management.price")
    public void onPriceChange(ChangeEvent<String, String> event) {
        // 解析变更事件
        JsonObject before = parseJson(event.getBefore());
        JsonObject after = parseJson(event.getAfter());
        String operation = event.getOp();  // c=create, u=update, d=delete
        
        // 记录变更历史
        ChangeRecord record = new ChangeRecord();
        record.setTableName("price");
        record.setRecordId(after.get("id").getAsLong());
        record.setOperation(operation);
        record.setBeforeValue(before);
        record.setAfterValue(after);
        record.setChangedAt(LocalDateTime.now());
        record.setChangedBy(extractUserFromContext());
        
        changeRecordRepository.save(record);
        
        // 发布领域事件
        eventPublisher.publishEvent(new DataChangedEvent(record));
    }
}

// 变更历史查询
@Service
public class ChangeHistoryService {
    
    public List<ChangeRecord> getHistory(String tableName, Long recordId) {
        return changeRecordRepository.findByTableNameAndRecordIdOrderByChangedAtDesc(tableName, recordId);
    }
    
    public Optional<T> getRecordAtTime(String tableName, Long recordId, LocalDateTime timestamp) {
        // 重建任意时间点的数据状态
        List<ChangeRecord> changes = changeRecordRepository
            .findByTableNameAndRecordIdAndChangedAtBeforeOrderByChangedAtDesc(tableName, recordId, timestamp);
        
        if (changes.isEmpty()) {
            return Optional.empty();
        }
        
        // 应用变更重建状态
        JsonObject state = new JsonObject();
        for (ChangeRecord change : changes.reversed()) {
            if ("d".equals(change.getOperation())) {
                return Optional.empty();  // 已删除
            }
            state = merge(state, change.getAfterValue());
        }
        
        return Optional.of(parseEntity(state));
    }
}
```

---

### 4.3 安全合规升级（+0.8分）

#### 4.3.1 零信任架构

| 缺失项 | 现状 | 9.5+ 标准 | 实施 |
|--------|------|-----------|------|
| 信任模型 | 边界信任 | 零信任：每次请求验证身份+权限+设备 | 设备指纹 + 持续认证 |
| 设备绑定 | 无 | 设备注册 + 异常设备告警 | 设备管理模块 |

```java
// 零信任认证过滤器
@Component
public class ZeroTrustFilter extends OncePerRequestFilter {
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain chain) {
        // 1. 提取 Token
        String token = extractToken(request);
        if (token == null) {
            reject(response, "Missing token");
            return;
        }
        
        // 2. 验证 Token 有效性
        TokenClaims claims = jwtService.validateToken(token);
        if (claims == null) {
            reject(response, "Invalid token");
            return;
        }
        
        // 3. 提取设备指纹
        String deviceFingerprint = extractDeviceFingerprint(request);
        
        // 4. 验证设备是否已注册/可信
        DeviceTrustLevel trustLevel = deviceService.checkDevice(
            claims.getUserId(), 
            deviceFingerprint
        );
        
        if (trustLevel == DeviceTrustLevel.BLOCKED) {
            reject(response, "Device blocked");
            return;
        }
        
        // 5. 行为分析（异常检测）
        RiskScore riskScore = behaviorAnalyzer.analyze(
            claims.getUserId(),
            request.getRequestURI(),
            request.getRemoteAddr(),
            deviceFingerprint
        );
        
        if (riskScore.isHigh()) {
            // 触发二次认证
            requireMFA(response, claims.getUserId());
            return;
        }
        
        // 6. 细粒度权限检查
        if (!permissionService.hasPermission(claims.getUserId(), request.getRequestURI(), request.getMethod())) {
            reject(response, "Permission denied");
            return;
        }
        
        // 7. 设置认证上下文
        SecurityContext context = new SecurityContext(claims, deviceFingerprint, trustLevel);
        SecurityContextHolder.setContext(context);
        
        chain.doFilter(request, response);
    }
    
    private String extractDeviceFingerprint(HttpServletRequest request) {
        // 组合多个浏览器特征生成指纹
        StringBuilder sb = new StringBuilder();
        sb.append(request.getHeader("User-Agent"));
        sb.append(request.getHeader("Accept-Language"));
        sb.append(request.getHeader("Accept-Encoding"));
        sb.append(getClientIP(request));
        // 可添加更多特征
        
        return DigestUtils.sha256Hex(sb.toString());
    }
}

// 设备信任级别
public enum DeviceTrustLevel {
    TRUSTED,      // 已注册可信设备
    KNOWN,        // 已知设备，需监控
    UNKNOWN,      // 未知设备，需二次认证
    BLOCKED       // 已封禁设备
}

// 风险评分
public class RiskScore {
    private int score;  // 0-100
    private List<String> riskFactors;
    
    public boolean isHigh() {
        return score >= 70;
    }
}
```

#### 4.3.2 敏感数据加密

| 缺失项 | 现状 | 9.5+ 标准 | 实施 |
|--------|------|-----------|------|
| PII 保护 | 明文存储 | 应用层加密，数据库密文存储 | 字段级加密 + 密钥轮换 |
| 密钥管理 | 配置文件 | 密钥管理系统（KMS），自动轮换 | Vault / AWS KMS |

```java
// 字段加密转换器
public class PIIEncryptor implements AttributeConverter<String, String> {
    
    @Autowired
    private KeyManagementService kms;
    
    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return attribute;
        }
        
        // 获取当前加密密钥
        EncryptionKey key = kms.getCurrentKey("pii-encryption");
        
        // AES-GCM 加密
        return AESGCM.encrypt(attribute, key.getValue(), key.getVersion());
    }
    
    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return dbData;
        }
        
        // 解析密钥版本
        String keyVersion = extractKeyVersion(dbData);
        
        // 获取对应版本的密钥
        EncryptionKey key = kms.getKeyByVersion("pii-encryption", keyVersion);
        
        // 解密
        return AESGCM.decrypt(dbData, key.getValue());
    }
}

// 实体字段加密
@Entity
public class User {
    
    @Id
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String username;  // 不加密（需要查询）
    
    @Convert(converter = PIIEncryptor.class)
    @Column(name = "phone")
    private String phone;  // 加密存储
    
    @Convert(converter = PIIEncryptor.class)
    @Column(name = "email")
    private String email;  // 加密存储
    
    @Convert(converter = PIIEncryptor.class)
    @Column(name = "id_card")
    private String idCard;  // 身份证号，加密存储
}

// 密钥轮换服务
@Service
public class KeyRotationService {
    
    @Scheduled(cron = "0 0 0 1 * ?")  // 每月1号
    public void rotateKeys() {
        // 1. 生成新密钥
        EncryptionKey newKey = kms.generateKey("pii-encryption");
        
        // 2. 设置新密钥为当前密钥
        kms.setCurrentKey("pii-encryption", newKey);
        
        // 3. 后台任务：重新加密旧数据
        reencryptOldData(newKey);
    }
    
    @Async
    private void reencryptOldData(EncryptionKey newKey) {
        // 批量重新加密用户数据
        Page<User> users = userRepository.findAll(PageRequest.of(0, 1000));
        while (users.hasContent()) {
            for (User user : users) {
                // 触发重新加密
                userRepository.save(user);
            }
            users = userRepository.findAll(users.nextPageable());
        }
    }
}
```

#### 4.3.3 SOC2 合规

| 缺失项 | 现状 | 9.5+ 标准 | 实施 |
|--------|------|-----------|------|
| 访问控制审计 | 操作日志 | SOC2 Type II 合规：访问控制、变更追溯 | 增强审计日志 |
| 合规报告 | 无 | 自动生成 SOC2 合规报告 | 合规报告生成器 |

```java
// SOC2 审计日志
@Entity
@Table(name = "audit_log")
public class AuditLog {
    @Id
    private Long id;
    
    private String userId;
    private String username;
    private String tenantId;
    
    private String action;          // LOGIN / LOGOUT / CREATE / UPDATE / DELETE / ACCESS
    private String resourceType;    // PRODUCT / PRICE / USER
    private String resourceId;
    
    @Column(columnDefinition = "JSON")
    private String oldValues;       // 变更前值
    
    @Column(columnDefinition = "JSON")
    private String newValues;       // 变更后值
    
    private String ipAddress;
    private String userAgent;
    private String deviceFingerprint;
    
    private LocalDateTime timestamp;
    private String sessionId;
    
    private String result;          // SUCCESS / FAILURE
    private String failureReason;
}

// 审计切面
@Aspect
@Component
public class AuditAspect {
    
    @Autowired
    private AuditLogRepository auditLogRepository;
    
    @AfterReturning(pointcut = "@annotation(auditable)", returning = "result")
    public void auditSuccess(JoinPoint jp, Auditable auditable, Object result) {
        AuditLog log = createAuditLog(jp, auditable, "SUCCESS", null);
        auditLogRepository.save(log);
    }
    
    @AfterThrowing(pointcut = "@annotation(auditable)", throwing = "ex")
    public void auditFailure(JoinPoint jp, Auditable auditable, Exception ex) {
        AuditLog log = createAuditLog(jp, auditable, "FAILURE", ex.getMessage());
        auditLogRepository.save(log);
    }
    
    private AuditLog createAuditLog(JoinPoint jp, Auditable auditable, String result, String failureReason) {
        AuditLog log = new AuditLog();
        log.setUserId(SecurityContext.getCurrentUserId());
        log.setTenantId(SecurityContext.getCurrentTenantId());
        log.setAction(auditable.action());
        log.setResourceType(auditable.resourceType());
        log.setTimestamp(LocalDateTime.now());
        log.setIpAddress(SecurityContext.getClientIP());
        log.setResult(result);
        log.setFailureReason(failureReason);
        
        // 提取变更数据
        if (auditable.logChanges()) {
            Object[] args = jp.getArgs();
            // ... 提取 old/new 值
        }
        
        return log;
    }
}

// 审计注解
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {
    String action();
    String resourceType();
    boolean logChanges() default false;
}

// 使用
@Auditable(action = "UPDATE", resourceType = "PRICE", logChanges = true)
public Price updatePrice(Long priceId, BigDecimal newPrice) {
    // ...
}

// 合规报告生成
@Service
public class ComplianceReportService {
    
    public SOC2Report generateReport(LocalDate startDate, LocalDate endDate) {
        SOC2Report report = new SOC2Report();
        
        // 1. 访问控制统计
        report.setTotalAccessEvents(countAccessEvents(startDate, endDate));
        report.setUniqueUsersAccessed(countUniqueUsers(startDate, endDate));
        
        // 2. 异常访问检测
        report.setAnomalousAccessEvents(detectAnomalousAccess(startDate, endDate));
        
        // 3. 数据变更审计
        report.setDataChanges(getDataChanges(startDate, endDate));
        
        // 4. 权限变更记录
        report.setPermissionChanges(getPermissionChanges(startDate, endDate));
        
        // 5. 敏感数据访问
        report.setSensitiveDataAccess(getSensitiveDataAccess(startDate, endDate));
        
        return report;
    }
}
```

#### 4.3.4 漏洞扫描集成

| 缺失项 | 现状 | 9.5+ 标准 | 实施 |
|--------|------|-----------|------|
| SAST | 无 | 静态代码分析，CI/CD 集成 | SonarQube |
| DAST | 无 | 动态安全测试 | OWASP ZAP |
| 依赖检查 | 无 | 第三方依赖漏洞扫描 | Dependency-Check |

```yaml
# GitHub Actions CI/CD 安全扫描
name: Security Scan

on:
  push:
    branches: [ master, develop ]
  pull_request:
    branches: [ master ]

jobs:
  security:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      # SAST - SonarQube
      - name: SonarQube Scan
        uses: sonarsource/sonarqube-scan-action@master
        env:
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
          SONAR_HOST_URL: ${{ secrets.SONAR_HOST_URL }}
      
      # 依赖漏洞扫描
      - name: OWASP Dependency Check
        uses: dependency-check/Dependency-Check_Action@main
        with:
          project: 'price-management-system'
          path: '.'
          format: 'HTML'
          out: 'reports'
        env:
          JAVA_HOME: /opt/java/openjdk
      
      # 上传报告
      - name: Upload Security Reports
        uses: actions/upload-artifact@v3
        with:
          name: security-reports
          path: reports/
      
      # DAST - OWASP ZAP（针对测试环境）
      - name: OWASP ZAP Scan
        if: github.ref == 'refs/heads/develop'
        uses: zaproxy/action-full-scan@master
        with:
          target: 'https://test.price-management.com'
```

---

### 4.4 可观测性升级（+0.7分）

#### 4.4.1 分布式追踪

| 缺失项 | 现状 | 9.5+ 标准 | 实施 |
|--------|------|-----------|------|
| 链路追踪 | 无 | OpenTelemetry 全链路追踪 | Jaeger / Zipkin |
| 跨服务调用 | 无追踪 | 跨服务调用可视化，性能分析 | Trace ID 传递 |

```java
// OpenTelemetry 配置
@Configuration
public class TracingConfig {
    
    @Bean
    public OpenTelemetry openTelemetry() {
        // Jaeger Exporter
        JaegerGrpcSpanExporter jaegerExporter = JaegerGrpcSpanExporter.builder()
            .setEndpoint("http://jaeger:14250")
            .build();
        
        // Tracer Provider
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(jaegerExporter).build())
            .setResource(Resource.create(Attributes.of(
                ResourceAttributes.SERVICE_NAME, "price-management-backend"
            )))
            .build();
        
        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
            .build();
    }
}

// 自定义 Span
@Service
public class PriceService {
    
    private final Tracer tracer;
    
    public PriceService(OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer("price-service");
    }
    
    public Price updatePrice(Long productId, BigDecimal newPrice) {
        Span span = tracer.spanBuilder("updatePrice")
            .setAttribute("product.id", productId)
            .setAttribute("price.new", newPrice)
            .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            // 业务逻辑
            Product product = productRepository.findById(productId);
            product.updatePrice(Money.of(newPrice, "CNY"));
            return productRepository.save(product);
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }
}

// HTTP 客户端追踪拦截器
@Component
public class TracingInterceptor implements ClientHttpRequestInterceptor {
    
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, 
                                        ClientHttpRequestExecution execution) {
        Span span = tracer.spanBuilder("HTTP " + request.getMethod())
            .setAttribute("http.url", request.getURI().toString())
            .startSpan();
        
        try (Scope scope = span.makeCurrent()) {
            // 注入 Trace Context 到请求头
            openTelemetry.getPropagators().getTextMapPropagator()
                .inject(Context.current(), request.getHeaders(), 
                    (carrier, key, value) -> carrier.add(key, value));
            
            return execution.execute(request, body);
        } finally {
            span.end();
        }
    }
}
```

#### 4.4.2 Prometheus 监控

| 缺失项 | 现状 | 9.5+ 标准 | 实施 |
|--------|------|-----------|------|
| 系统指标 | 无 | CPU/内存/响应时间/错误率 | Prometheus + Micrometer |
| 业务指标 | 无 | 价格变更率、审批时效、活跃用户 | 自定义业务指标 |

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: prometheus,health,info,metrics
  metrics:
    tags:
      application: price-management
      environment: ${SPRING_PROFILES_ACTIVE:dev}
    distribution:
      percentiles-histogram:
        http.server.requests: true
      percentiles:
        http.server.requests: 0.5,0.95,0.99
```

```java
// 业务指标定义
@Configuration
public class MetricsConfig {
    
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config().commonTags(
            "application", "price-management",
            "version", getVersion()
        );
    }
}

// 业务指标使用
@Service
public class PriceService {
    
    private final Counter priceUpdateCounter;
    private final Timer priceUpdateTimer;
    private final Gauge activeProductsGauge;
    
    public PriceService(MeterRegistry registry) {
        this.priceUpdateCounter = Counter.builder("price.updates")
            .description("Number of price updates")
            .tag("type", "product")
            .register(registry);
        
        this.priceUpdateTimer = Timer.builder("price.update.duration")
            .description("Time taken to update price")
            .register(registry);
        
        this.activeProductsGauge = Gauge.builder("products.active.count", this, 
            s -> s.countActiveProducts())
            .description("Number of active products")
            .register(registry);
    }
    
    @Timed(value = "price.update", description = "Price update operation", percentiles = {0.5, 0.95})
    public void updatePrice(Long productId, BigDecimal newPrice) {
        priceUpdateCounter.increment();
        
        priceUpdateTimer.record(() -> {
            // 业务逻辑
        });
    }
}

// 审批时效指标
@Service
public class ApprovalMetrics {
    
    private final Timer approvalDurationTimer;
    private final Counter approvalSuccessCounter;
    private final Counter approvalRejectCounter;
    
    public ApprovalMetrics(MeterRegistry registry) {
        this.approvalDurationTimer = Timer.builder("approval.duration")
            .description("Time from request creation to final decision")
            .register(registry);
        
        this.approvalSuccessCounter = Counter.builder("approval.decisions")
            .tag("result", "approved")
            .register(registry);
        
        this.approvalRejectCounter = Counter.builder("approval.decisions")
            .tag("result", "rejected")
            .register(registry);
    }
    
    public void recordApproval(ApprovalRequest request) {
        Duration duration = Duration.between(request.getCreatedTime(), LocalDateTime.now());
        approvalDurationTimer.record(duration);
        
        if (request.getStatus() == RequestStatus.APPROVED) {
            approvalSuccessCounter.increment();
        } else {
            approvalRejectCounter.increment();
        }
    }
}
```

```yaml
# Prometheus 告警规则
groups:
  - name: price-management-alerts
    rules:
      # 高错误率
      - alert: HighErrorRate
        expr: |
          sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) 
          / sum(rate(http_server_requests_seconds_count[5m])) > 0.05
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "High error rate detected"
          description: "Error rate is {{ $value | humanizePercentage }}"
      
      # 响应时间过长
      - alert: HighLatency
        expr: |
          histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m])) > 2
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High latency detected"
          description: "P95 latency is {{ $value | humanizeDuration }}"
      
      # 审批积压
      - alert: ApprovalBacklog
        expr: |
          count(approval_requests_pending) > 50
        for: 30m
        labels:
          severity: warning
        annotations:
          summary: "Too many pending approvals"
          description: "{{ $value }} approval requests are pending"
```

#### 4.4.3 日志聚合

| 缺失项 | 现状 | 9.5+ 标准 | 实施 |
|--------|------|-----------|------|
| 日志存储 | 本地文件 | ELK/Loki 日志聚合，全文检索 | Elasticsearch + Kibana |
| 日志分析 | 无 | 异常聚类分析、自动告警 | 日志分析规则 |

```xml
<!-- Logback 配置（输出 JSON 格式） -->
<appender name="JSON" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>logs/application.json</file>
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <includeMdcKeyName>traceId</includeMdcKeyName>
        <includeMdcKeyName>spanId</includeMdcKeyName>
        <includeMdcKeyName>tenantId</includeMdcKeyName>
        <includeMdcKeyName>userId</includeMdcKeyName>
    </encoder>
    <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
        <fileNamePattern>logs/application-%d{yyyy-MM-dd}.json.gz</fileNamePattern>
        <maxHistory>30</maxHistory>
    </rollingPolicy>
</appender>
```

```java
// 结构化日志
@Slf4j
@Service
public class PriceService {
    
    public void updatePrice(Long productId, BigDecimal newPrice) {
        // MDC 注入上下文
        MDC.put("productId", productId.toString());
        MDC.put("newPrice", newPrice.toString());
        
        try {
            log.info("Updating price for product");
            // 业务逻辑
            log.info("Price updated successfully");
        } catch (Exception e) {
            log.error("Failed to update price", e);
            throw e;
        } finally {
            MDC.clear();
        }
    }
}

// 日志输出示例（JSON）
{
    "@timestamp": "2026-06-05T10:30:00.000Z",
    "@version": "1",
    "message": "Price updated successfully",
    "logger_name": "com.pricemanagement.service.PriceService",
    "level": "INFO",
    "traceId": "abc123def456",
    "spanId": "xyz789",
    "tenantId": "1",
    "userId": "42",
    "productId": "100",
    "newPrice": "5000.00"
}
```

#### 4.4.4 业务埋点

| 缺失项 | 现状 | 9.5+ 标准 | 实施 |
|--------|------|-----------|------|
| 用户行为 | 无 | 页面访问、按钮点击、功能使用率 | 前端埋点 + 后端收集 |
| 转化漏斗 | 无 | 关键业务转化分析 | 漏斗分析 |

```typescript
// 前端埋点 SDK
export const tracking = {
  // 页面访问
  trackPageView(pageName: string) {
    this.send('page_view', { page: pageName });
  },
  
  // 按钮点击
  trackClick(buttonName: string, metadata?: Record<string, any>) {
    this.send('button_click', { button: buttonName, ...metadata });
  },
  
  // 业务事件
  trackEvent(eventName: string, properties?: Record<string, any>) {
    this.send(eventName, properties);
  },
  
  private send(event: string, data: any) {
    fetch('/api/tracking', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        event,
        data,
        timestamp: new Date().toISOString(),
        userId: getUserId(),
        sessionId: getSessionId(),
        deviceInfo: getDeviceInfo()
      })
    });
  }
};

// 使用示例
tracking.trackPageView('price-maintenance');
tracking.trackClick('publish-price', { productId: 123, price: 5000 });
tracking.trackEvent('price_published', { productId: 123, oldPrice: 4800, newPrice: 5000 });
```

```java
// 后端埋点收集
@RestController
@RequestMapping("/api/tracking")
public class TrackingController {
    
    @Autowired
    private TrackingEventRepository repository;
    
    @PostMapping
    public void track(@RequestBody TrackingEvent event) {
        // 持久化到 ClickHouse（高性能分析）
        repository.save(event);
    }
}

// 埋点分析查询
@Service
public class TrackingAnalyticsService {
    
    // 页面访问排行
    public List<PageViewStats> getTopPages(LocalDate date) {
        return repository.query("""
            SELECT page, COUNT(*) as views
            FROM tracking_events
            WHERE event = 'page_view'
              AND toDate(timestamp) = ?
            GROUP BY page
            ORDER BY views DESC
            LIMIT 20
            """, date);
    }
    
    // 漏斗分析
    public FunnelAnalysis getFunnelAnalysis(String[] steps, LocalDate date) {
        // 分析用户完成多步骤流程的转化率
        // 例如：价格维护页 → 点击发布 → 发布成功
        return repository.analyzeFunnel(steps, date);
    }
}
```

---

### 4.5 运维自动化升级（+0.5分）

#### 4.5.1 GitOps 部署

| 缺失项 | 现状 | 9.5+ 标准 | 实施 |
|--------|------|-----------|------|
| 部署方式 | docker-compose | GitOps：声明式部署，版本追溯 | Kubernetes + ArgoCD |
| 环境管理 | 手动配置 | 多环境（dev/test/prod）自动同步 | Kustomize / Helm |

```yaml
# k8s/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: price-management-backend
  labels:
    app: price-management
    component: backend
spec:
  replicas: 3
  selector:
    matchLabels:
      app: price-management
      component: backend
  template:
    metadata:
      labels:
        app: price-management
        component: backend
    spec:
      containers:
        - name: backend
          image: jlmining.com/pricemanage/price-management-backend:v1.5.0
          ports:
            - containerPort: 8080
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "prod"
            - name: DB_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: price-management-secrets
                  key: db-password
          resources:
            requests:
              memory: "512Mi"
              cpu: "250m"
            limits:
              memory: "1Gi"
              cpu: "500m"
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata:
  name: price-management-backend
spec:
  selector:
    app: price-management
    component: backend
  ports:
    - port: 8080
      targetPort: 8080
```

```yaml
# argocd/application.yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: price-management
  namespace: argocd
spec:
  project: default
  source:
    repoURL: https://github.com/company/price-management-system.git
    targetRevision: HEAD
    path: k8s/overlays/prod
  destination:
    server: https://kubernetes.default.svc
    namespace: price-management
  syncPolicy:
    automated:
      prune: true
      selfHeal: true
    syncOptions:
      - CreateNamespace=true
```

#### 4.5.2 混沌工程

| 缺失项 | 现状 | 9.5+ 标准 | 实施 |
|--------|------|-----------|------|
| 故障注入 | 无 | Chaos Monkey/ChaosBlade，主动注入故障 | 混沌测试 |
| 韧性验证 | 无 | 定期混沌演练，验证系统韧性 | 演练计划 |

```yaml
# Chaos Mesh 配置
apiVersion: chaos-mesh.org/v1alpha1
kind: PodChaos
metadata:
  name: backend-pod-kill
  namespace: chaos-testing
spec:
  action: pod-kill
  mode: one
  selector:
    namespaces:
      - price-management
    labelSelectors:
      app: price-management
      component: backend
  scheduler:
    cron: "0 2 * * 6"  # 每周六凌晨2点
---
apiVersion: chaos-mesh.org/v1alpha1
kind: NetworkChaos
metadata:
  name: backend-network-delay
  namespace: chaos-testing
spec:
  action: delay
  mode: one
  selector:
    namespaces:
      - price-management
    labelSelectors:
      app: price-management
      component: backend
  delay:
    latency: "100ms"
    correlation: "50"
  duration: "5m"
```

#### 4.5.3 容量规划

| 缺失项 | 现状 | 9.5+ 标准 | 实施 |
|--------|------|-----------|------|
| 扩缩容 | 手动 | 基于 CPU/内存/请求量的自动扩缩容 | K8s HPA |
| 容量预测 | 无 | 基于历史数据的容量预测 | Prometheus + 预测算法 |

```yaml
# Horizontal Pod Autoscaler
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: price-management-backend-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: price-management-backend
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
    - type: Pods
      pods:
        metric:
          name: http_requests_per_second
        target:
          type: AverageValue
          averageValue: "1000"
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 60
      policies:
        - type: Percent
          value: 100
          periodSeconds: 60
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
        - type: Percent
          value: 10
          periodSeconds: 60
```

---

### 4.6 AI 增强升级（+0.5分）

#### 4.6.1 智能定价建议

| 缺失项 | 现状 | 9.5+ 标准 | 实施 |
|--------|------|-----------|------|
| 定价决策 | 纯人工 | AI 辅助定价：历史趋势 + 市场行情 + 季节因素 | ML 模型 + 推理服务 |
| 价格预测 | 无 | 基于历史数据的价格走势预测 | Time Series 模型 |

```java
// 定价建议服务
@Service
public class PricingSuggestionService {
    
    @Autowired
    private MLModelService mlModelService;
    
    @Autowired
    private MarketDataService marketDataService;
    
    public PricingSuggestion suggestPrice(Long productId, LocalDate effectiveDate) {
        // 1. 获取产品历史价格
        List<PriceHistory> history = priceHistoryRepository.findByProductId(productId);
        
        // 2. 获取市场行情数据
        MarketData marketData = marketDataService.getLatestMarketData(productId);
        
        // 3. 调用 ML 模型预测
        MLPrediction prediction = mlModelService.predict(PredictionRequest.builder()
            .productId(productId)
            .historicalPrices(history)
            .marketData(marketData)
            .targetDate(effectiveDate)
            .build());
        
        // 4. 生成定价建议
        return PricingSuggestion.builder()
            .suggestedPrice(prediction.getPredictedPrice())
            .confidence(prediction.getConfidence())
            .factors(List.of(
                new PricingFactor("历史趋势", prediction.getTrendContribution()),
                new PricingFactor("市场行情", prediction.getMarketContribution()),
                new PricingFactor("季节因素", prediction.getSeasonalContribution())
            ))
            .priceRange(new PriceRange(
                prediction.getLowerBound(),
                prediction.getUpperBound()
            ))
            .explanation(generateExplanation(prediction))
            .build();
    }
    
    private String generateExplanation(MLPrediction prediction) {
        return String.format(
            "基于过去%d天历史数据分析，结合当前市场行情（%s），建议定价为 %s，" +
            "置信度 %.1f%%。价格区间：[%s, %s]。",
            prediction.getDataDays(),
            prediction.getMarketTrend(),
            prediction.getPredictedPrice(),
            prediction.getConfidence() * 100,
            prediction.getLowerBound(),
            prediction.getUpperBound()
        );
    }
}
```

#### 4.6.2 异常检测

| 缺失项 | 现状 | 9.5+ 标准 | 实施 |
|--------|------|-----------|------|
| 价格监控 | 无 | 自动检测价格异常波动，主动预警 | Time Series 异常检测 |
| 根因分析 | 无 | AI 辅助分析异常原因 | 因果推断 |

```java
// 异常检测服务
@Service
public class PriceAnomalyDetector {
    
    @Autowired
    private StatisticalAnomalyDetector statisticalDetector;
    
    @Autowired
    private MLAnomalyDetector mlDetector;
    
    public AnomalyResult detect(Long productId, BigDecimal newPrice) {
        // 获取历史数据
        List<BigDecimal> history = priceHistoryRepository.findRecentPrices(productId, 30);
        
        // 方法1：统计学方法（3σ 规则）
        AnomalyResult statisticalResult = detectByStatistics(history, newPrice);
        
        // 方法2：机器学习方法（Isolation Forest）
        AnomalyResult mlResult = detectByML(history, newPrice);
        
        // 综合判断
        return AnomalyResult.combine(statisticalResult, mlResult);
    }
    
    private AnomalyResult detectByStatistics(List<BigDecimal> history, BigDecimal newPrice) {
        double mean = calculateMean(history);
        double stdDev = calculateStdDev(history);
        double zScore = (newPrice.doubleValue() - mean) / stdDev;
        
        if (Math.abs(zScore) > 3) {
            return AnomalyResult.anomaly(
                AnomalyLevel.HIGH,
                String.format("价格偏离超过 3σ（z-score=%.2f），异常概率 > 99.7%%", zScore)
            );
        } else if (Math.abs(zScore) > 2) {
            return AnomalyResult.anomaly(
                AnomalyLevel.MEDIUM,
                String.format("价格偏离超过 2σ（z-score=%.2f），异常概率 > 95%%", zScore)
            );
        }
        
        return AnomalyResult.normal();
    }
    
    private AnomalyResult detectByML(List<BigDecimal> history, BigDecimal newPrice) {
        // 特征工程
        double[] features = extractFeatures(history, newPrice);
        
        // Isolation Forest 预测
        double anomalyScore = mlDetector.predictAnomalyScore(features);
        
        if (anomalyScore > 0.8) {
            return AnomalyResult.anomaly(
                AnomalyLevel.HIGH,
                String.format("ML 模型判定异常（score=%.2f）", anomalyScore)
            );
        } else if (anomalyScore > 0.6) {
            return AnomalyResult.anomaly(
                AnomalyLevel.MEDIUM,
                String.format("ML 模型疑似异常（score=%.2f）", anomalyScore)
            );
        }
        
        return AnomalyResult.normal();
    }
    
    // 自动预警
    @EventListener
    public void onPriceUpdate(PriceChangedEvent event) {
        AnomalyResult result = detect(event.getProductId(), event.getNewPrice().getAmount());
        
        if (result.isAnomaly()) {
            // 发送预警通知
            alertService.sendPriceAnomalyAlert(event.getProductId(), result);
        }
    }
}
```

#### 4.6.3 智能客服

| 缺失项 | 现状 | 9.5+ 标准 | 实施 |
|--------|------|-----------|------|
| 客服支持 | 无 | RAG 知识库问答，自然语言查询 | 向量数据库 + LLM |
| 智能助手 | 无 | 价格查询、审批查询、操作指导 | AI Agent |

```java
// 智能客服服务
@Service
public class AIAssistantService {
    
    @Autowired
    private VectorSearchService vectorSearch;
    
    @Autowired
    private LLMClient llmClient;
    
    @Autowired
    private FunctionCallingExecutor functionExecutor;
    
    public AssistantResponse chat(String userQuestion, Long userId) {
        // 1. 意图识别
        UserIntent intent = intentClassifier.classify(userQuestion);
        
        // 2. 根据意图执行不同策略
        switch (intent.getType()) {
            case PRICE_QUERY:
                return handlePriceQuery(userQuestion, userId);
            case APPROVAL_QUERY:
                return handleApprovalQuery(userQuestion, userId);
            case GENERAL_QUESTION:
                return handleGeneralQuestion(userQuestion);
            default:
                return handleWithRAG(userQuestion);
        }
    }
    
    private AssistantResponse handlePriceQuery(String question, Long userId) {
        // 提取实体（产品名称）
        List<String> productNames = entityExtractor.extractProducts(question);
        
        // 调用价格查询工具
        String toolResult = functionExecutor.execute("query_price", Map.of(
            "productNames", productNames,
            "userId", userId
        ));
        
        // 生成自然语言回答
        String answer = llmClient.chat("""
            用户问题：%s
            查询结果：%s
            
            请用自然语言回答用户问题，包含具体价格信息。
            """.formatted(question, toolResult));
        
        return AssistantResponse.of(answer, List.of(new FunctionCall("query_price", toolResult)));
    }
    
    private AssistantResponse handleGeneralQuestion(String question) {
        // RAG 检索相关文档
        List<DocumentEmbedding> relevantDocs = vectorSearch.semanticSearch(question, 5);
        
        String context = relevantDocs.stream()
            .map(DocumentEmbedding::getContentChunk)
            .collect(Collectors.joining("\n\n"));
        
        String systemPrompt = """
            你是价格管理系统的智能助手。
            请基于以下上下文回答用户问题。
            如果上下文中没有相关信息，请诚实地说"我不确定，请联系管理员"。
            
            上下文：
            %s
            """.formatted(context);
        
        String answer = llmClient.chat(systemPrompt, question);
        
        // 添加引用来源
        List<SourceReference> sources = relevantDocs.stream()
            .map(d -> new SourceReference(d.getDocumentId(), d.getContentType()))
            .toList();
        
        return AssistantResponse.of(answer, sources);
    }
}

// Function Calling 工具定义
@Component
public class QueryPriceFunction implements AIFunction {
    
    @Override
    public String getName() {
        return "query_price";
    }
    
    @Override
    public String getDescription() {
        return "查询指定产品的最新价格";
    }
    
    @Override
    public Map<String, Object> getParameters() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "productNames", Map.of(
                    "type", "array",
                    "items", Map.of("type", "string"),
                    "description", "产品名称列表"
                )
            ),
            "required", List.of("productNames")
        );
    }
    
    @Override
    public String execute(Map<String, Object> arguments) {
        @SuppressWarnings("unchecked")
        List<String> productNames = (List<String>) arguments.get("productNames");
        
        // 查询价格
        List<ProductPriceInfo> prices = priceQueryService.queryByNames(productNames);
        
        return toJson(prices);
    }
}
```

#### 4.6.4 审批智能路由

| 缺失项 | 现状 | 9.5+ 标准 | 实施 |
|--------|------|-----------|------|
| 审批分配 | 手动/按角色 | 基于审批人负载、专业领域的智能分派 | 规则引擎 + ML |

```java
// 智能审批路由服务
@Service
public class ApprovalRoutingService {
    
    @Autowired
    private ApproverProfileService profileService;
    
    @Autowired
    private WorkloadAnalyzer workloadAnalyzer;
    
    public ApproverAssignment suggestApprover(String businessType, BigDecimal amount, String department) {
        // 1. 获取有权限的审批人列表
        List<ApproverProfile> eligibleApprovers = profileService.getEligibleApprovers(businessType, amount);
        
        // 2. 计算每个审批人的适配分数
        List<ApproverScore> scores = eligibleApprovers.stream()
            .map(approver -> calculateScore(approver, businessType, amount, department))
            .sorted(Comparator.comparing(ApproverScore::getTotalScore).reversed())
            .toList();
        
        // 3. 返回最佳匹配
        ApproverScore best = scores.get(0);
        
        return ApproverAssignment.builder()
            .approverId(best.getApprover().getUserId())
            .approverName(best.getApprover().getName())
            .confidence(best.getTotalScore())
            .reasons(best.getReasons())
            .build();
    }
    
    private ApproverScore calculateScore(ApproverProfile approver, String businessType, 
                                         BigDecimal amount, String department) {
        List<String> reasons = new ArrayList<>();
        double totalScore = 0;
        
        // 因素1：当前负载（负载越低分数越高）
        WorkloadInfo workload = workloadAnalyzer.getWorkload(approver.getUserId());
        double workloadScore = 100 - workload.getPendingCount() * 10;
        totalScore += workloadScore * 0.3;  // 权重 30%
        reasons.add(String.format("当前待审批 %d 项，负载分数 %.1f", 
            workload.getPendingCount(), workloadScore));
        
        // 因素2：专业领域匹配度
        double expertiseScore = calculateExpertiseScore(approver, businessType);
        totalScore += expertiseScore * 0.3;  // 权重 30%
        reasons.add(String.format("专业领域匹配度 %.1f%%", expertiseScore));
        
        // 因素3：金额权限匹配（金额越大，需要更高级别审批人）
        double amountScore = calculateAmountScore(approver, amount);
        totalScore += amountScore * 0.25;  // 权重 25%
        reasons.add(String.format("金额权限匹配分数 %.1f", amountScore));
        
        // 因素4：历史处理效率
        double efficiencyScore = calculateEfficiencyScore(approver);
        totalScore += efficiencyScore * 0.15;  // 权重 15%
        reasons.add(String.format("历史平均处理时间 %.1f 小时", approver.getAvgProcessTime()));
        
        return new ApproverScore(approver, totalScore, reasons);
    }
}
```

---

### 4.7 前端体验升级（+0.3分）

#### 4.7.1 离线模式

| 缺失项 | 现状 | 9.5+ 标准 | 实施 |
|--------|------|-----------|------|
| 离线支持 | 无 | PWA + Service Worker，关键数据离线可访问 | Vite PWA 插件 |
| 数据同步 | 无 | 离线操作自动同步 | IndexedDB + Sync |

```typescript
// vite.config.ts - PWA 配置
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  plugins: [
    vue(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.ico', 'robots.txt'],
      manifest: {
        name: '价格管理系统',
        short_name: '价格管理',
        theme_color: '#0D6E6E',
        icons: [
          {
            src: 'pwa-192x192.png',
            sizes: '192x192',
            type: 'image/png'
          },
          {
            src: 'pwa-512x512.png',
            sizes: '512x512',
            type: 'image/png'
          }
        ]
      },
      workbox: {
        runtimeCaching: [
          {
            // 缓存价格数据（离线可访问）
            urlPattern: /^https:\/\/.*\/api\/price-query/,
            handler: 'NetworkFirst',
            options: {
              cacheName: 'price-data',
              expiration: {
                maxEntries: 100,
                maxAgeSeconds: 60 * 60 * 24 // 1天
              },
              networkTimeoutSeconds: 10
            }
          },
          {
            // 缓存字典数据
            urlPattern: /^https:\/\/.*\/api\/dict/,
            handler: 'CacheFirst',
            options: {
              cacheName: 'dict-data',
              expiration: {
                maxEntries: 50,
                maxAgeSeconds: 60 * 60 * 24 * 7 // 7天
              }
            }
          }
        ]
      }
    })
  ]
})

// 离线数据管理
export class OfflineDataManager {
  private db: IDBDatabase
  
  async init() {
    this.db = await openDB('price-management-offline', 1, {
      upgrade(db) {
        // 离线价格草稿
        db.createObjectStore('price-drafts', { keyPath: 'id', autoIncrement: true })
        // 离线操作队列
        db.createObjectStore('sync-queue', { keyPath: 'id', autoIncrement: true })
      }
    })
  }
  
  // 保存离线价格草稿
  async saveDraftOffline(draft: PriceDraft) {
    await this.db.put('price-drafts', {
      ...draft,
      savedAt: new Date().toISOString(),
      synced: false
    })
  }
  
  // 获取未同步的草稿
  async getUnsyncedDrafts(): Promise<PriceDraft[]> {
    const all = await this.db.getAll('price-drafts')
    return all.filter(d => !d.synced)
  }
  
  // 标记已同步
  async markSynced(id: number) {
    const draft = await this.db.get('price-drafts', id)
    if (draft) {
      draft.synced = true
      await this.db.put('price-drafts', draft)
    }
  }
}

// 在线时自动同步
window.addEventListener('online', async () => {
  const offlineManager = new OfflineDataManager()
  const unsyncedDrafts = await offlineManager.getUnsyncedDrafts()
  
  for (const draft of unsyncedDrafts) {
    try {
      await api.savePriceDraft(draft)
      await offlineManager.markSynced(draft.id)
      showToast('离线数据已同步')
    } catch (e) {
      console.error('同步失败', e)
    }
  }
})
```

#### 4.7.2 实时协作

| 缺失项 | 现状 | 9.5+ 标准 | 实施 |
|--------|------|-----------|------|
| 多人协作 | 无 | WebSocket 实时通知，冲突解决 | WebSocket + OT |
| 编辑提示 | 无 | "某人正在编辑"提示 | Presence 服务 |

```java
// WebSocket 协作服务
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(collaborationHandler(), "/ws/collaboration")
            .setAllowedOrigins("*");
    }
    
    @Bean
    public CollaborationHandler collaborationHandler() {
        return new CollaborationHandler();
    }
}

// 协作处理器
@Component
public class CollaborationHandler extends TextWebSocketHandler {
    
    private final Map<String, Set<WebSocketSession>> documentSessions = new ConcurrentHashMap<>();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String documentId = extractDocumentId(session);
        documentSessions.computeIfAbsent(documentId, k -> ConcurrentHashMap.newKeySet()).add(session);
        
        // 通知其他用户：新用户加入
        broadcastPresence(documentId, session);
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        CollaborationMessage msg = parseMessage(message.getPayload());
        
        switch (msg.getType()) {
            case CURSOR_MOVE:
                // 广播光标位置
                broadcastToOthers(msg.getDocumentId(), session, msg);
                break;
            case EDIT:
                // 广播编辑操作
                broadcastToOthers(msg.getDocumentId(), session, msg);
                break;
            case LOCK:
                // 锁定编辑
                handleLock(msg.getDocumentId(), session, msg);
                break;
        }
    }
    
    private void broadcastPresence(String documentId, WebSocketSession newSession) {
        // 获取当前编辑该文档的所有用户
        Set<WebSocketSession> sessions = documentSessions.get(documentId);
        List<UserPresence> users = sessions.stream()
            .filter(s -> s != newSession)
            .map(s -> getUserPresence(s))
            .toList();
        
        // 发送给新用户
        sendToSession(newSession, new PresenceMessage(users));
        
        // 通知其他用户新用户加入
        broadcastToOthers(documentId, newSession, 
            new UserJoinMessage(getUserPresence(newSession)));
    }
}
```

```typescript
// 前端协作组件
export function useCollaboration(documentId: string) {
  const ws = ref<WebSocket | null>(null)
  const activeUsers = ref<UserPresence[]>([])
  const lockedBy = ref<string | null>(null)
  
  function connect() {
    ws.value = new WebSocket(`${WS_URL}/ws/collaboration?doc=${documentId}`)
    
    ws.value.onmessage = (event) => {
      const msg = JSON.parse(event.data)
      
      switch (msg.type) {
        case 'presence':
          activeUsers.value = msg.users
          break
        case 'user_join':
          activeUsers.value.push(msg.user)
          showToast(`${msg.user.name} 正在查看此页面`)
          break
        case 'user_leave':
          activeUsers.value = activeUsers.value.filter(u => u.id !== msg.userId)
          break
        case 'edit':
          // 应用远程编辑
          applyRemoteEdit(msg)
          break
        case 'lock':
          lockedBy.value = msg.userName
          showToast(`${msg.userName} 正在编辑`)
          break
        case 'unlock':
          lockedBy.value = null
          break
      }
    }
  }
  
  function sendEdit(operation: EditOperation) {
    ws.value?.send(JSON.stringify({
      type: 'edit',
      documentId,
      operation
    }))
  }
  
  function requestLock() {
    ws.value?.send(JSON.stringify({
      type: 'lock',
      documentId
    }))
  }
  
  return {
    activeUsers,
    lockedBy,
    connect,
    sendEdit,
    requestLock
  }
}
```

#### 4.7.3 低代码扩展

| 缺失项 | 现状 | 9.5+ 标准 | 实施 |
|--------|------|-----------|------|
| 表单定制 | 无 | 管理员可自定义表单字段 | 表单设计器 |
| 页面定制 | 无 | 可自定义页面布局 | 页面编辑器 |

```typescript
// 表单 Schema 定义
interface FormSchema {
  fields: FormField[]
  layout: FormLayout
  rules: ValidationRule[]
}

interface FormField {
  name: string
  label: string
  type: 'text' | 'number' | 'select' | 'date' | 'textarea'
  required: boolean
  defaultValue?: any
  options?: { label: string; value: any }[]
  validation?: ValidationRule[]
  visible?: ConditionExpression
  disabled?: ConditionExpression
}

// 动态表单组件
export const DynamicForm = defineComponent({
  props: {
    schema: { type: Object as PropType<FormSchema>, required: true },
    modelValue: { type: Object, required: true }
  },
  setup(props, { emit }) {
    const formData = reactive(props.modelValue)
    
    return () => (
      <van-form>
        {props.schema.fields.map(field => (
          <DynamicField
            key={field.name}
            field={field}
            value={formData[field.name]}
            onUpdate:value={(v: any) => {
              formData[field.name] = v
              emit('update:modelValue', formData)
            }}
          />
        ))}
      </van-form>
    )
  }
})

// 动态字段渲染
const DynamicField = defineComponent({
  props: {
    field: { type: Object as PropType<FormField>, required: true },
    value: { type: [String, Number, Boolean, Array] }
  },
  setup(props, { emit }) {
    // 根据类型渲染不同组件
    const fieldComponents = {
      text: VanField,
      number: VanField,
      select: VanPicker,
      date: VanDatetimePicker,
      textarea: VanField
    }
    
    return () => {
      const Component = fieldComponents[props.field.type]
      return h(Component, {
        label: props.field.label,
        modelValue: props.value,
        'onUpdate:modelValue': (v: any) => emit('update:value', v),
        required: props.field.required,
        ...getExtraProps(props.field)
      })
    }
  }
})
```

---

### 4.8 开放生态升级（+0.2分）

#### 4.8.1 插件市场

| 缺失项 | 现状 | 9.5+ 标准 | 实施 |
|--------|------|-----------|------|
| 扩展机制 | 无 | 第三方插件生态，可扩展功能 | 插件 SPI + 管理平台 |
| 插件管理 | 无 | 插件安装/启用/禁用/卸载 | 插件生命周期管理 |

```java
// 插件 SPI
public interface Plugin {
    // 插件元数据
    PluginMetadata getMetadata();
    
    // 插件入口
    void initialize(PluginContext context);
    
    // 插件销毁
    void destroy();
}

// 插件元数据
public class PluginMetadata {
    private String id;
    private String name;
    private String version;
    private String author;
    private String description;
    private List<String> permissions;  // 需要的权限
    private List<String> dependencies; // 依赖的其他插件
}

// 插件管理器
@Service
public class PluginManager {
    
    private final Map<String, Plugin> plugins = new ConcurrentHashMap<>();
    private final Map<String, PluginClassLoader> classLoaders = new ConcurrentHashMap<>();
    
    // 安装插件
    public void install(Path pluginJar) {
        // 1. 加载插件 JAR
        PluginClassLoader classLoader = new PluginClassLoader(pluginJar);
        
        // 2. 读取插件元数据
        PluginMetadata metadata = classLoader.loadMetadata();
        
        // 3. 检查依赖
        checkDependencies(metadata);
        
        // 4. 实例化插件
        Plugin plugin = classLoader.loadPlugin();
        
        // 5. 初始化插件
        plugin.initialize(new PluginContext());
        
        // 6. 注册插件
        plugins.put(metadata.getId(), plugin);
        classLoaders.put(metadata.getId(), classLoader);
    }
    
    // 卸载插件
    public void uninstall(String pluginId) {
        Plugin plugin = plugins.get(pluginId);
        if (plugin != null) {
            plugin.destroy();
            plugins.remove(pluginId);
            classLoaders.remove(pluginId);
        }
    }
    
    // 启用/禁用插件
    public void setEnabled(String pluginId, boolean enabled) {
        Plugin plugin = plugins.get(pluginId);
        if (plugin != null) {
            if (enabled) {
                plugin.initialize(new PluginContext());
            } else {
                plugin.destroy();
            }
        }
    }
}
```

#### 4.8.2 开放平台

| 缺失项 | 现状 | 9.5+ 标准 | 实施 |
|--------|------|-----------|------|
| 开发者门户 | 无 | API 文档、SDK、沙箱环境 | 开发者中心 |
| SDK 发布 | 无 | 多语言 SDK（Java/Python/JS） | SDK 生成器 |

```java
// SDK 生成器
@Service
public class SDKGenerator {
    
    public byte[] generateSDK(String language, String version) {
        // 1. 扫描所有外部 API
        List<APIEndpoint> endpoints = scanExternalAPIs();
        
        // 2. 根据语言生成 SDK
        switch (language) {
            case "java":
                return generateJavaSDK(endpoints, version);
            case "python":
                return generatePythonSDK(endpoints, version);
            case "javascript":
                return generateJSSDK(endpoints, version);
            default:
                throw new IllegalArgumentException("Unsupported language: " + language);
        }
    }
    
    private byte[] generateJavaSDK(List<APIEndpoint> endpoints, String version) {
        // 使用 Mustache 模板生成 Java 客户端代码
        // ...
    }
}

// 沙箱环境
@Service
public class SandboxService {
    
    // 创建沙箱 API Key
    public APIKey createSandboxKey(Long developerId) {
        return apiKeyService.create(APIKeyCreateRequest.builder()
            .name("Sandbox Key")
            .environment("SANDBOX")
            .rateLimitPerMinute(100)
            .dailyLimit(1000)
            .expireTime(LocalDateTime.now().plusMonths(1))
            .build());
    }
    
    // 沙箱数据隔离
    @Transactional
    public void executeInSandbox(Runnable operation) {
        // 设置沙箱上下文
        SandboxContext.setSandboxMode(true);
        
        try {
            operation.run();
        } finally {
            SandboxContext.clear();
        }
    }
}
```

#### 4.8.3 Webhook 回调

| 缺失项 | 现状 | 9.5+ 标准 | 实施 |
|--------|------|-----------|------|
| 事件订阅 | 无 | 外部系统实时接收变更通知 | Webhook 管理 |
| 回调管理 | 无 | Webhook 注册/测试/重试 | 回调管理平台 |

```java
// Webhook 定义
@Entity
@Table(name = "sys_webhook")
public class Webhook {
    @Id
    private Long id;
    
    private String name;
    private String url;
    private String secret;  // 用于签名验证
    
    @Column(columnDefinition = "JSON")
    private String events;  // 订阅的事件类型列表
    
    private boolean enabled;
    private LocalDateTime createdTime;
}

// Webhook 服务
@Service
public class WebhookService {
    
    @Autowired
    private WebhookRepository webhookRepository;
    
    @Autowired
    private WebhookDeliveryService deliveryService;
    
    // 发送 Webhook
    @Async
    public void sendWebhook(String eventType, Object payload) {
        // 1. 查找订阅该事件的所有 Webhook
        List<Webhook> webhooks = webhookRepository.findByEventAndEnabled(eventType, true);
        
        // 2. 异步发送
        for (Webhook webhook : webhooks) {
            deliveryService.deliver(webhook, eventType, payload);
        }
    }
}

// Webhook 投递服务
@Service
public class WebhookDeliveryService {
    
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public void deliver(Webhook webhook, String eventType, Object payload) {
        // 1. 构建请求体
        WebhookPayload webhookPayload = new WebhookPayload(
            UUID.randomUUID().toString(),
            eventType,
            payload,
            System.currentTimeMillis()
        );
        
        // 2. 生成签名
        String signature = generateSignature(webhook.getSecret(), webhookPayload);
        
        // 3. 发送 HTTP 请求
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Webhook-Signature", signature);
        headers.set("X-Webhook-Event", eventType);
        headers.set("X-Webhook-ID", webhookPayload.getId());
        
        HttpEntity<WebhookPayload> request = new HttpEntity<>(webhookPayload, headers);
        
        ResponseEntity<String> response = restTemplate.postForEntity(
            webhook.getUrl(), request, String.class
        );
        
        // 4. 记录投递日志
        logDelivery(webhook, webhookPayload, response);
    }
    
    private String generateSignature(String secret, WebhookPayload payload) {
        String json = objectMapper.writeValueAsString(payload);
        return HmacUtils.hmacSha256Hex(secret, json);
    }
}

// 使用示例
@EventListener
public void onPriceChanged(PriceChangedEvent event) {
    webhookService.sendWebhook("price.changed", event);
}
```

---

## 五、实施路线图

### 5.1 总体规划

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        企业级平台演进路线图（9.5+ 完整版）                          │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  Phase 1 (2026 Q3)            Phase 2 (2026 Q4)                                 │
│  ┌─────────────────────┐      ┌─────────────────────┐                           │
│  │ 多租户基础改造       │      │ DDD 重构            │                           │
│  │ 模块边界划分         │ ───▶ │ 事件驱动架构        │                           │
│  │ 国际化基础           │      │ 数据权限体系        │                           │
│  │ 读写分离            │      │ 审批流配置化        │                           │
│  └─────────────────────┘      └─────────────────────┘                           │
│           │                            │                                        │
│           │ 评分: 8.0                  │ 评分: 8.5                              │
│           ▼                            ▼                                        │
│  Phase 3 (2027 Q1)            Phase 4 (2027 Q2)                                 │
│  ┌─────────────────────┐      ┌─────────────────────┐                           │
│  │ CQRS 实现           │      │ 零信任架构          │                           │
│  │ 可观测性完善         │ ───▶ │ SOC2 合规          │                           │
│  │ 分布式追踪          │      │ GitOps 部署         │                           │
│  │ AI 异常检测         │      │ 混沌工程            │                           │
│  └─────────────────────┘      └─────────────────────┘                           │
│           │                            │                                        │
│           │ 评分: 9.0                  │ 评分: 9.5                              │
│           ▼                            ▼                                        │
│  Phase 5 (2027 Q3+)                                                             │
│  ┌─────────────────────┐                                                        │
│  │ AI 能力增强          │                                                        │
│  │ 向量数据库           │                                                        │
│  │ 开放平台            │                                                        │
│  │ 插件生态            │                                                        │
│  └─────────────────────┘                                                        │
│           │                                                                     │
│           │ 评分: 9.5+                                                          │
│           ▼                                                                     │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 5.2 Phase 1：基础改造（2026 Q3）→ 8.0分

| 任务 | 工作量 | 依赖 | 交付物 |
|------|--------|------|--------|
| 租户表结构设计 + 业务表改造 | 1周 | 无 | DDL 脚本 + 迁移脚本 |
| TenantContext + Hibernate Filter | 3天 | 表结构 | 租户上下文组件 |
| 读写分离配置 | 3天 | 无 | Dynamic DataSource 配置 |
| 模块边界分析 + SPI 设计 | 3天 | 无 | 模块依赖图 + SPI 接口 |
| 国际化消息源配置 | 2天 | 无 | i18n 资源文件 |

**里程碑**：支持多租户数据隔离 + 读写分离

### 5.3 Phase 2：架构升级（2026 Q4）→ 8.5分

| 任务 | 工作量 | 依赖 | 交付物 |
|------|--------|------|--------|
| DDD 重构（价格模块） | 2周 | 模块边界 | DDD 分层代码 |
| 领域事件 + Spring Event | 1周 | DDD 重构 | 事件驱动机制 |
| 数据权限切面实现 | 1周 | 多租户 | 数据权限组件 |
| 审批工作流 V2 表结构 | 3天 | 无 | 可配置审批流 |
| BusinessTypeRegistry | 3天 | 审批表 | 动态业务类型 |

**里程碑**：DDD 架构 + 事件驱动 + 可配置审批

### 5.4 Phase 3：可观测性（2027 Q1）→ 9.0分

| 任务 | 工作量 | 依赖 | 交付物 |
|------|--------|------|--------|
| OpenTelemetry 集成 | 1周 | 无 | 分布式追踪 |
| Prometheus 指标埋点 | 1周 | 无 | 监控仪表盘 |
| CQRS 读模型（ES） | 2周 | DDD 重构 | Elasticsearch 读模型 |
| 日志聚合（ELK） | 1周 | 无 | ELK Stack |
| AI 异常检测模型 | 2周 | 无 | 异常检测服务 |

**里程碑**：全链路可观测 + CQRS + AI 增强监控

### 5.5 Phase 4：安全合规（2027 Q2）→ 9.5分

| 任务 | 工作量 | 依赖 | 交付物 |
|------|--------|------|--------|
| 零信任架构改造 | 2周 | 无 | 设备认证 + 行为分析 |
| PII 字段加密 | 1周 | 无 | 字段加密转换器 |
| SOC2 审计日志增强 | 1周 | 无 | 合规审计日志 |
| Kubernetes + ArgoCD | 2周 | 无 | GitOps 部署流水线 |
| Chaos Mesh 集成 | 1周 | K8s | 混沌测试 |

**里程碑**：安全合规 + GitOps + 混沌工程

### 5.6 Phase 5：生态扩展（2027 Q3+）→ 9.5+

| 任务 | 工作量 | 依赖 | 交付物 |
|------|--------|------|--------|
| 向量数据库集成 | 1周 | 无 | RAG 知识库 |
| 智能客服（AI Agent） | 2周 | 向量库 | AI 助手 |
| 插件 SPI + 管理 | 2周 | 无 | 插件平台 |
| Webhook 服务 | 1周 | 无 | 事件订阅 |
| 开发者门户 | 2周 | 无 | 开发者中心 |

**里程碑**：AI 增强 + 开放生态 + 插件市场

---

## 六、投入估算汇总

| 类别 | Phase | 工作量 | 周期 | 评分提升 |
|------|-------|--------|------|----------|
| 基础改造 | Phase 1 | 3-4 周 | 2026 Q3 | 6.5 → 8.0 |
| 架构升级 | Phase 2 | 5-6 周 | 2026 Q4 | 8.0 → 8.5 |
| 可观测性 | Phase 3 | 6-7 周 | 2027 Q1 | 8.5 → 9.0 |
| 安全合规 | Phase 4 | 6-7 周 | 2027 Q2 | 9.0 → 9.5 |
| 生态扩展 | Phase 5 | 持续 | 2027 Q3+ | 9.5 → 9.5+ |

**总投入**：约 20-25 周（5-6 个月）达到 9.5，持续迭代达到 9.5+

---

## 七、风险与对策

### 7.1 技术风险

| 风险 | 影响 | 对策 |
|------|------|------|
| DDD 重构复杂度高 | 延期 | 分模块渐进式重构，先价格模块试点 |
| 多租户数据迁移风险 | 数据丢失 | 双写校验 + 灰度发布 + 回滚预案 |
| 读写分离延迟 | 数据不一致 | 临界操作强制走主库 + 最终一致性 |
| AI 模型效果不佳 | 业务价值低 | A/B 测试 + 持续优化 + 降级策略 |

### 7.2 组织风险

| 风险 | 影响 | 对策 |
|------|------|------|
| 团队技能差距 | 延期 | DDD 培训 + 外部咨询 + 结对编程 |
| 业务需求优先级冲突 | 资源不足 | 技术债务偿还计划 + 最小可行改造 |
| 改造期间业务中断 | 业务损失 | 蓝绿部署 + 灰度发布 + 回滚机制 |

---

## 八、总结

### 核心结论

1. **当前 6.5 分**：功能完整的单体应用，适合中小企业
2. **目标 9.5+ 分**：行业标杆级企业平台，支持多租户、DDD、AI 增强
3. **关键路径**：多租户 → DDD → 可观测性 → 安全合规 → AI 生态
4. **时间投入**：5-6 个月达到 9.5，持续迭代达到 9.5+

### 优先级矩阵

| 优先级 | 改造项 | ROI | 风险 |
|--------|--------|-----|------|
| **P0** | 多租户数据隔离 | ⭐⭐⭐⭐⭐ | 中 |
| **P0** | DDD 重构 | ⭐⭐⭐⭐⭐ | 高 |
| **P0** | 可观测性（追踪+监控） | ⭐⭐⭐⭐⭐ | 低 |
| **P1** | 读写分离 + 数据归档 | ⭐⭐⭐⭐ | 中 |
| **P1** | 零信任 + SOC2 合规 | ⭐⭐⭐⭐ | 中 |
| **P2** | AI 异常检测 + 智能定价 | ⭐⭐⭐ | 低 |
| **P2** | Webhook + 开放平台 | ⭐⭐⭐ | 低 |

### 下一步行动

1. ✅ 评审本评估报告，确认改造范围和优先级
2. ⏳ 启动 Phase 1 多租户改造
3. ⏳ 制定详细的数据库迁移方案
4. ⏳ 组织 DDD 培训，建立团队共识

---

**文档版本**：v2.0（含 9.5+ 升级方案）
**编写日期**：2026-06-05
**维护者**：架构组
