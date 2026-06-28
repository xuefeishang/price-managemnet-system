---
title: 技术栈
version: v2.0.0
last_updated: 2026-06-15
source: docs/dev/backup/技术栈简明说明.md
---

# 矿产品价格管理系统 - 技术栈

> 本文档整合原 `docs/dev/backup/技术栈简明说明.md` (v2.5) 的核心内容，包含系统架构、前后端技术栈、Docker 部署、版本信息和选型理由。

---

## 系统架构图

```
┌─────────────────────────────────────────────────────────────┐
│                      用户浏览器 (PC/手机)                    │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           │ 访问前端页面 (http://xxx:32080)
                           ▼
┌─────────────────────────────────────────────────────────────┐
│              Docker 容器端口映射 (32080:32080)                │
│                         ↓                                    │
│                   Nginx (反向代理 + 静态资源)                 │
│  - 提供 Vue 编译后的静态文件 (HTML/CSS/JS)                    │
│  - 将 /api/* 请求代理到后端                                    │
│  - Gzip 压缩 / 静态资源缓存 (30天)                             │
└───────────┬──────────────────────────────┬─────────────────┘
            │ 静态文件                      │ /api/* 代理
            ▼                              ▼
┌─────────────────────┐  ┌─────────────────────────────────────┐
│  Vue 3 前端 (SPA)   │  │       Spring Boot 后端 (JDK 25)     │
│  - 页面展示和交互    │  │  - 业务逻辑处理                       │
│  - 图表可视化        │  │  - JWT 权限验证                       │
│  - 状态管理 (Pinia)  │  │  - JPA 数据读写                       │
│  - 路由守卫          │  │  - Redis 缓存加速                     │
└─────────────────────┘  └───┬──────────────┬──────────────────┘
                             │              │
                             ▼              ▼
                      ┌───────────┐  ┌───────────┐
                      │  MySQL    │  │  Redis    │
                      │  持久存储  │  │  缓存     │
                      └───────────┘  └───────────┘
```

### 统一端口架构

项目采用公网 HTTPS 与内网 HTTP 分端口架构：

| 客户端 | 内网访问 | 外网访问 |
|--------|---------|---------|
| **PC端 (H5)** | `http://10.7.5.175:32801` | `https://price.jlmining.com:32080` |
| **微信小程序** | `http://10.7.5.175:32801/api/*`（仅真机调试） | `https://price.jlmining.com:32080/api/*` |

公网正式微信小程序使用 `https://price.jlmining.com:32080`；公司内网真机调试可使用独立 HTTP 入口 `http://10.7.5.175:32801`。

#### Docker 部署端口完整表

| 容器 | 端口映射 | 用途 | 网络模式 |
|------|----------|------|----------|
| frontend | `80:80` | H5 备用 HTTP | bridge |
| frontend | `443:443` | **正式 HTTPS**（证书终止） | bridge |
| frontend | `32080:32080` | **统一 HTTPS 入口**（PC 端 + 微信小程序） | bridge |
| frontend | `32801:32801` | **内网正式 HTTP**（免证书快速访问） | bridge |
| backend | `8080:8080` | 后端 API | host |
| redis | `6379:6379` | 缓存/限流/会话 | host |

**URL 对应关系：**

- 公网 HTTPS：`https://price.jlmining.com:32080`
- 公网 HTTPS 标准端口：`https://price.jlmining.com:443`
- 内网 HTTPS：`https://10.7.5.175:32080`
- 内网 HTTP：`http://10.7.5.175:32801`
- 后端 API：`http://10.7.5.175:8080/api`

---

## 一、前端技术

### 1.1 Vue 3 + Composition API

**核心原理：** Vue 3 使用 ES6 Proxy 实现响应式系统。当你修改一个响应式变量（`ref` / `reactive`），Vue 自动追踪依赖并重新渲染相关 DOM。

**为什么用 `ref` 而不是 `reactive`：**
- `ref` 可以替换整个值（`products.value = newList`），`reactive` 不能
- `ref` 对基本类型和对象都适用，`reactive` 只适用于对象
- 项目统一使用 `ref` 作为状态声明方式

```vue
<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'

const products = ref<Product[]>([])
const loading = ref(false)
const searchQuery = ref('')

const filteredProducts = computed(() =>
  products.value.filter(p => p.name.includes(searchQuery.value))
)

onMounted(() => { loadProducts() })
</script>
```

**模板中的双端适配（PC/移动端）：**

```vue
<template>
  <template v-if="isPCLayout">
    <div class="table-container">...</div>
  </template>
  <template v-else>
    <div class="card-container">...</div>
  </template>
</template>
```

`isPCLayout` 来自 `useLayout()` composable，基于 `window.innerWidth >= 1024` 判断。

---

### 1.2 TypeScript — 类型安全

**核心价值：** 在编译时捕获错误，而不是运行时崩溃。

**API 响应类型**（与后端 `Result<T>` 对应）：

```typescript
interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
  timestamp: number
}

interface PageResponse<T> {
  content: T[]
  totalPages: number
  totalElements: number
  number: number
  size: number
  first: boolean
  last: boolean
}

// 业务实体类型 — 与后端 Entity 字段一一对应
interface Product {
  id: number
  code?: string
  name: string
  sellingPrice?: number
  budgetPrice?: number
  categoryId?: number
  status: ProductStatus
  currency?: Currency
  createdTime: string
  updatedTime: string
}

type Role = 'ADMIN' | 'EDITOR' | 'VIEWER'
type ProductStatus = 'ACTIVE' | 'INACTIVE'
```

**API 函数的类型约束：**

```typescript
export const createProduct = async (
  data: Omit<Product, 'id' | 'createdTime' | 'updatedTime'>
): Promise<ApiResponse<Product>> => {
  return await http.post('/api/products', data)
}
```

---

### 1.3 Vant UI

**选择理由：** 项目需要同时支持 PC 和移动端，Vant 的移动端体验优于 Element Plus 等桌面组件库。

```vue
<template>
  <van-field v-model="form.name" label="产品名称" placeholder="请输入" />
  <van-picker :columns="statusOptions" @confirm="onStatusConfirm" />
</template>

<script setup lang="ts">
// 字典选项 — 动态获取，不硬编码中文
const statusOptions = computed(() => getDictOptions('common_status'))
</script>
```

---

### 1.4 ECharts + vue-echarts

**主题色与样式系统联动：**

```typescript
const chartOption = computed(() => ({
  color: [
    themeConfig.chartPrimaryColor,
    themeConfig.chartBudgetColor,
    ...themeConfig.chartColors
  ],
  xAxis: { type: 'category', data: dates },
  yAxis: { type: 'value' },
  series: [{ type: 'line', data: prices }]
}))
```

```vue
<v-chart :option="chartOption" autoresize />
```

---

### 1.5 Pinia — 状态管理

**useUserStore（用户认证）：**

```typescript
export const useUserStore = defineStore('user', () => {
  const user = ref<User | null>(null)
  const token = ref<string | null>(localStorage.getItem('token'))

  const isAuthenticated = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const canEdit = computed(() =>
    user.value?.role === 'ADMIN' || user.value?.role === 'EDITOR'
  )

  const loginAction = async (data: LoginRequest) => {
    const response = await login(data)
    token.value = response.data.token
    localStorage.setItem('token', response.data.token)
  }

  return { user, token, isAuthenticated, isAdmin, canEdit, loginAction, logoutAction, fetchProfile }
})
```

**useMenuStore（菜单状态）：** 通过 `version` 自增触发 `Layout` 重新渲染，菜单变更实时生效。

---

### 1.6 Vue Router — 路由与权限守卫

```typescript
const routes = [
  { path: '/login', component: Login, meta: { requiresAuth: false } },
  {
    path: '/',
    component: Layout,
    meta: { requiresAuth: true },
    children: [
      { path: 'home', component: () => import('../views/Home.vue') },
      { path: 'users', component: () => import('../views/UserManagement.vue'),
        meta: { adminOnly: true } },
    ]
  }
]

router.beforeEach(async (to, _from, next) => {
  if (!to.meta.requiresAuth) return next()
  if (!userStore.isAuthenticated) return next('/login')
  if (to.meta.adminOnly && !userStore.isAdmin) return next('/home')
  next()
})
```

**关键理解：** 前端路由守卫是**用户体验层**的权限控制，真正的安全在**后端 `@PreAuthorize`**。

---

### 1.7 Axios — HTTP 请求层

```typescript
const instance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 30000,
})

// 请求拦截器 — 自动附加 Token + 慢请求检测
instance.interceptors.request.use(config => {
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  config.metadata = { startTime: Date.now() }
  return config
})

// 响应拦截器 — 解包 + 错误处理 + Token 自动刷新
instance.interceptors.response.use(
  response => response.data,  // 解包：调用方直接拿到 ApiResponse<T>
  async error => {
    if (error.response?.status === 401) {
      // 触发 Refresh Token 队列重试
    }
    showToast(getErrorMessage(error))
    return Promise.reject(error)
  }
)
```

**解包机制：** 后端返回 `{ code, message, data, timestamp }`，拦截器返回 `data` 本身，调用方拿到 `ApiResponse<T>`，再通过 `.data` 取业务数据。

---

### 1.8 useDict — 字典服务（核心 Composable）

**设计意图：** 所有编码值的显示名称从后端动态获取，前端绝不硬编码中文标签。

```typescript
// src/composables/useDict.ts
const dictCache = reactive(new Map<string, SysDict[]>())

export const loadAllDicts = async () => {
  if (loaded.value) return
  const response = await getDicts()
  // 按 category 分组存入 Map
  for (const dict of response.data) {
    const list = dictCache.get(dict.category) || []
    list.push(dict)
    dictCache.set(dict.category, list)
  }
}

export const getDictValue = (category: string, dictKey: string): string =>
  dictCache.get(category)?.find(d => d.dictKey === dictKey)?.dictValue || dictKey

export const getDictOptions = (category: string) =>
  dictCache.get(category)?.filter(d => d.status === 'ACTIVE').map(d => ({
    value: d.dictKey, label: d.dictValue, extra: d.extraValue || undefined
  }))

// 便捷方法
export const getStatusLabel = (key: string) => getDictValue('common_status', key)
export const getRoleLabel = (key: string) => getDictValue('user_role', key)
export const getCurrencySymbol = (key: string) => getDictValue('currency', key)
```

---

### 1.9 Vite — 构建工具

```typescript
// vite.config.ts 关键配置
export default defineConfig({
  plugins: [
    vue(),
    AutoImport({ imports: ['vue', 'vue-router', 'pinia'] }),
    Components({ resolvers: [VantResolver()] }),
  ],
  server: {
    proxy: { '/api': { target: 'http://localhost:8080' } }
  }
})
```

| 环境 | 前端地址 | API 请求路径 | 代理方式 |
|------|---------|------------|---------|
| 开发 | `localhost:5173` | `/api/*` → Vite 代理到 `localhost:8080` | Vite proxy |
| 生产 | `服务器:80/32080` | `/api/*` → Nginx 代理到 `backend:8080` | Nginx proxy_pass |

---

### 1.10 frontend-uniapp 多端项目（v2.5 增量）

| 维度 | frontend（H5） | frontend-uniapp（多端） |
|------|---------------|------------------------|
| 项目类型 | Vue 3 + Vite SPA | uni-app Vue3（**H5/APP/小程序三端**） |
| 路由 | vue-router 4 | pages.json |
| UI 库 | Vant 4.8 | uni-app 内置 + 自研 mini-trend-chart |
| 构建工具 | Vite 8.0.5 | uni-cli 3.0.0-alpha-5000920260515001 |
| 包管理 | npm | npm |
| 端口（dev） | 5173 (Vite 默认) | 8080 (H5 模式) / 微信开发者工具 |
| 入口页面 | `views/Home.vue` | `pages/home/index.vue`（主包） |
| 状态管理 | Pinia 2.1.7 | Pinia 2.1.7（**版本固定非 ^**） |

**uniapp 端 H5 复用 PC 端同源 32080 端口**（与 `frontend` 容器共用 Nginx），小程序走 `price.jlmining.com:32080` 公网域名。

详见 [frontend-uniapp/README.md](../../frontend-uniapp/README.md)。

---

## 二、后端技术

### 2.1 Spring Boot 4.0.6 — 核心框架

**请求处理流程：**

```
HTTP 请求
  ↓
JwtAuthenticationFilter (提取 Token, 设置 SecurityContext)
  ↓
Controller (@RestController, @PreAuthorize)
  ↓
Service (@Service, @Transactional)
  ↓
Repository (JpaRepository)
  ↓
Hibernate (ORM) → MySQL
  ↓
原路返回: Entity → DTO → Result<T> → JSON
```

**Controller 层实际代码模式：**

```java
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR', 'VIEWER')")
    public Result<Page<Product>> getProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) CommonStatus status) {
        return Result.success("查询成功",
            productService.getProducts(page, size, keyword, status));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EDITOR')")
    public Result<Product> createProduct(@Valid @RequestBody Product product) {
        return Result.success("创建成功", productService.createProduct(product));
    }
}
```

**统一响应 `Result<T>`：**

```java
@Data
public class Result<T> {
    private Integer code;      // 200=成功, 4xx=客户端, 5xx=服务端
    private String message;    // 默认 "操作成功"
    private T data;            // 业务数据
    private Long timestamp;    // 毫秒时间戳（自 1970-01-01 UTC）
}
```

---

### 2.2 Spring Security — 认证与授权

**安全过滤器链：**

```
HTTP 请求 → CorsFilter → JwtAuthenticationFilter → @PreAuthorize → Controller
                                                              ↓
                                                    ExceptionTranslationHandler
                                                    (401/403 JSON 响应)
```

**公开路径 (`PUBLIC_PATHS`)：**

| 路径 | 说明 |
|------|------|
| `/api/auth/login` | 登录接口 |
| `/api/auth/refresh-token` | Token 刷新接口 |
| `/api/menus/tree` | 菜单树（前端路由需要） |
| `/api/menus/visible` | 可见菜单 |
| `/api/static/**` | 静态资源 |
| `/api/style/themes` | 主题配置（登录页样式需要） |

**JWT Token 结构：**

```
Header:  { "alg": "HS256", "typ": "JWT" }
Payload: { "sub": "admin", "userId": 1, "role": "ADMIN", "iat": ..., "exp": ... }
Signature: HMACSHA256(base64(header)+"."+base64(payload), secret)
```

---

### 2.3 Refresh Token 机制

| Token 类型 | 有效期 | 存储位置 | 用途 |
|-----------|--------|---------|------|
| Access Token | 24 小时 | 前端 localStorage | API 请求认证 |
| Refresh Token | 7 天 | 前端 localStorage + 后端数据库 | 刷新 Access Token |

**刷新流程：**

```
1. Access Token 过期 (401 响应)
2. 前端拦截器检测到 401
3. 调用 POST /api/auth/refresh-token { refreshToken }
4. 后端验证 RefreshToken: 存在/未撤销/未过期
5. 验证通过 → 生成新 Access Token → 返回
6. 前端更新 localStorage，重试原请求
```

**异常：** Refresh Token 不存在/已撤销/已过期 → 返回 401 → 跳转登录页。

---

### 2.4 API 限流保护

**基于 Redis + 自定义 `@RateLimiter` 注解的滑动窗口限流：**

```java
@PostMapping("/login")
@RateLimiter(time = 60, count = 5, limitType = RateLimiter.LimitType.IP,
             message = "登录尝试次数过多，请1分钟后再试")
public Result<?> login(@RequestBody LoginRequest request) { ... }

@PostMapping
@RateLimiter(time = 60, count = 50, limitType = RateLimiter.LimitType.USER)
public Result<Product> createProduct(@RequestBody Product product) { ... }
```

**限流策略：**

| 策略 | 说明 | 适用场景 |
|------|------|---------|
| `DEFAULT` | 全局限流 | 一般接口 |
| `IP` | 按 IP 限流 | 登录、刷新令牌 |
| `USER` | 按用户 ID 限流 | 需登录的操作接口 |

**降级：** Redis 不可用时自动降级为内存限流（`ConcurrentHashMap`）。

---

### 2.5 告警系统

**告警级别：**

| 级别 | 说明 | 建议操作 |
|------|------|---------|
| `CRITICAL` | 严重故障 | 立即检查服务器状态，必要时回滚 |
| `WARNING` | 警告 | 监控相关指标 |
| `INFO` | 信息 | 持续观察 |

**告警触发场景：**

1. 应用启动完成 — INFO
2. 内存使用率过高（默认 90%） — WARNING
3. CPU 使用率过高（默认 80%） — WARNING

**配置：** 通过 `ALERT_DINGTALK_*` / `ALERT_WECHAT_*` 环境变量启用钉钉/企业微信 Webhook。

---

### 2.6 JPA / Hibernate

**核心映射：**

```
Java Entity                    数据库表
@Entity @Table(name="product") →  CREATE TABLE product (...)
@Id                            →  id BIGINT PRIMARY KEY
@Column(name="xxx")            →  xxx VARCHAR(...)
@ManyToOne @JoinColumn         →  category_id BIGINT (外键)
@CreationTimestamp             →  created_time DATETIME
@Version                       →  version BIGINT (乐观锁)
@Transient                     →  不映射到数据库
```

**Repository 的三种查询方式：**

```java
@Repository
public interface ProductRepository extends JpaRepository<Product, Long>,
                                          JpaSpecificationExecutor<Product> {

    // 1. 方法名派生查询
    List<Product> findByStatus(CommonStatus status);

    // 2. @Query 自定义 JPQL
    @Query("SELECT p FROM Product p WHERE p.price BETWEEN :min AND :max")
    List<Product> findByPriceRange(@Param("min") BigDecimal min,
                                   @Param("max") BigDecimal max);

    // 3. JpaSpecification — 动态条件查询
}
```

**N+1 防范：**

- `jpa.open-in-view: false`（避免隐藏额外查询）
- `hibernate.default_batch_fetch_size: 50`
- 使用 JOIN FETCH 或 `@EntityGraph`

---

### 2.7 Flyway — 数据库迁移管理

**配置（application.yml）：**

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate    # 仅验证，不自动修改表结构
  flyway:
    enabled: true
    baseline-on-migrate: true
    baseline-version: 12
    locations: classpath:db/migration
    validate-on-migrate: true
```

**迁移脚本命名：** `V{version}__{description}.sql`，例：`V13__add_approval_workflow_tables.sql`

**注意事项：**
- 脚本必须**幂等**（可重复执行不报错）
- 禁止 `DROP TABLE` / `DROP COLUMN`
- 重要迁移前**必须备份数据库**
- `baseline-version: 12` 表示 V1-V12 已视为已执行（项目历史遗留基线）

**当前进度：** V1 ~ V46（2026-06-14）。

---

### 2.8 Redis — 缓存层（支持懒加载与自动降级）

**设计理念：** 系统优先使用 Redis，如果 Redis 不可用则自动降级为内存缓存。

```
应用启动 → RedisConfig (懒加载检测)
  ├─ Redis 可用 → RedisCacheManager (分布式缓存)
  └─ Redis 不可用 → ConcurrentMapCacheManager (内存缓存)
                ↓
   Spring Cache 抽象 (@Cacheable / @CacheEvict)
```

**关键配置：** `management.health.redis.enabled: false` — **禁用 Redis 健康检查**，确保 Redis 不可达时应用仍能启动。

**序列化（v2.4 修复）：** 使用 `GenericJackson2JsonRedisSerializer` 支持 `StyleConfigDTO` 等复杂对象（含嵌套对象、`LocalDateTime` 字段），并通过 `activateDefaultTyping` 嵌入类型信息。

**缓存清单：**

| 缓存名 | 基础 TTL | 用途 |
|--------|---------|------|
| `dict` | 2 小时 | 数据字典 |
| `style` | 1 小时 | 样式配置 |
| `products` | 30 分钟 | 产品数据 |
| `categories` | 1 小时 | 分类数据 |
| `users` | 15 分钟 | 用户信息 |
| `menu` | 1 小时 | 菜单数据 |

**随机过期时间防缓存雪崩：** 基础 TTL 的 80%-120% 随机。

---

### 2.9 MySQL — 持久化存储

**核心表：**

| 表名 | 说明 | 核心字段 |
|------|------|---------|
| `user` | 用户 | username, password(BCrypt), role, status |
| `product` | 产品 | name, selling_price, budget_price, category_id |
| `product_category` | 分类 | name, code, sort_order |
| `origin` / `customer` | 产地/客户 | name, code, contact |
| `price` / `price_history` | 价格/价格变更 | product_id, current_price, old_price |
| `sys_dict` | 数据字典 | category, dict_key, dict_value |
| `menu_item` | 菜单 | parent_id, name, path, roles |
| `operation_log` | 操作日志 | username, operation_type, ip |
| `refresh_token` | 刷新令牌 | token, user_id, expiry_date, revoked |
| `approval_*` | 审批流 | workflow_id, approver_id, status |
| `notification_*` | 站内通知 | type, business_type, recipient |

**JPA 模式：** `ddl-auto: validate` + Flyway 管理迁移（生产推荐）。

---

### 2.10 数据字典系统

```
MySQL sys_dict 表
    ↓ (启动时加载)
后端 @Cacheable("dict")
    ↓ (GET /api/dict)
前端 dictCache (reactive Map)
    ↓ (同步查找)
模板中: getStatusLabel('ACTIVE') → '启用'
```

**sys_dict 字段：**

| 字段 | 说明 | 示例 |
|------|------|------|
| `category` | 分类 | `common_status` |
| `dict_key` | 编码值 | `ACTIVE` |
| `dict_value` | 显示名称 | `启用` |
| `extra_value` | 扩展值 | `#67C23A` (颜色) |
| `sort_order` | 排序 | `1` |
| `status` | 状态 | `ACTIVE` / `INACTIVE` |

主要字典分类：`common_status` / `user_role` / `currency` / `unit` / `origin` / `customer` / `approval_status` / `workflow_type` / `notification_*` / `theme` / `style`。详见 [coding-standards.md](coding-standards.md#统一文字管理)。

---

### 2.11 EasyExcel — Excel 导入导出

```java
// 导出
EasyExcel.write(response.getOutputStream(), Product.class)
    .sheet("产品数据").doWrite(productList);

// 导入（流式读取）
EasyExcel.read(inputStream, ProductListener.class)
    .sheet().doRead();
```

---

### 2.12 application.yml 关键配置

**配置分层优先级（高→低）：**

1. 命令行参数：`--spring.profiles.active=prod`
2. 环境变量：`SPRING_PROFILES_ACTIVE=prod`
3. `application-{profile}.yml`
4. `application.yml`
5. `@ConfigurationProperties` 默认值

**关键配置项：**

| 配置项 | 默认值 | 作用 |
|--------|--------|------|
| `server.port` | `8080` | Spring Boot 监听端口 |
| `spring.datasource.password` | `${DB_PASSWORD}` | **必须环境变量注入** |
| `spring.jpa.open-in-view` | `false` | 禁用 OSIV，防懒加载事务问题 |
| `spring.jpa.hibernate.ddl-auto` | `validate` | 仅验证表结构 |
| `spring.cache.type` | `redis` | 启用 Redis 缓存 |
| `management.health.redis.enabled` | `false` | **关键**：Redis 故障不阻止启动 |
| `security.jwt-secret` | `${JWT_SECRET}` | JWT 签名密钥 |
| `security.jwt-expiration` | `86400000` (24h) | Access Token 有效期 |
| `security.reset-password-onStartup` | `false` | 生产必须 false |
| `security.cors-allowed-origins` | 空 | 生产配置实际域名 |
| `security.password-policy.min-length` | `8` | 密码最小长度 |
| `alert.enabled` | `false` | 生产建议 true |
| `api-key.enabled` | `true` | 外部 API 开关 |

详细配置（如通知中心 Outbox / Webhook / 小程序模板）见 [API调用手册](api/README.md) 与 `backend/src/main/resources/application.yml`。

---

## 三、Docker 部署架构

### 生产部署拓扑

```
服务器 (10.7.5.175)
├── Docker: price-management-frontend  (端口 80, 443, 32080, 32801)
│   │
│   │  端口映射原理：
│   │  ┌─────────────────────────────────────────────┐
│   │  │  用户请求 :32080                             │
│   │  │       ↓                                      │
│   │  │  服务器网卡 (监听 32080)                      │
│   │  │       ↓                                      │
│   │  │  iptables 防火墙规则 (允许 32080)             │
│   │  │       ↓                                      │
│   │  │  docker-proxy 进程 (端口转发)                │
│   │  │       ↓                                      │
│   │  │  Nginx 容器 (监听容器内 32080)               │
│   │  │       ├── /api/* → 后端 8080                │
│   │  │       └── 其他 → 前端静态资源                 │
│   │  └─────────────────────────────────────────────┘
│   │
│   └── Nginx (统一入口 32080)
│       ├── 静态文件服务 (Vue 编译产物)
│       ├── SPA 路由支持 (try_files $uri /index.html)
│       └── /api/* → proxy_pass http://host.docker.internal:8080
│
├── Docker: price-management-backend   (端口 8080, host 网络模式)
│   └── Spring Boot (JRE 25, 非root用户 appuser)
│       ├── 连接 MySQL (10.7.5.175:3306, 外部服务)
│       └── 连接 Redis (10.7.5.175:6379, 外部服务)
│
├── MySQL 8.0 (非 Docker, 系统服务, 端口 3306)
└── Redis 7.x (非 Docker, 系统服务, 端口 6379, AOF 持久化)
```

### 网络模式对比

| 网络模式 | 工作原理 | 性能 | 适用场景 |
|---------|---------|------|---------|
| **bridge + 端口映射** | 容器独立网络，通过 `docker-proxy` 转发 | 多一层转发 | 多实例部署 |
| **host 网络** | 容器直接使用宿主机网络 | 性能最优 | 单实例性能敏感服务 |

**当前配置：** backend 用 host 网络（监听 8080），frontend 用 bridge 网络（端口映射）。

---

### 多阶段构建

**Dockerfile.backend（开发版）：**

```dockerfile
# 阶段1: Maven 编译
FROM maven:3.9-eclipse-temurin-25-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

# 阶段2: JRE 运行（非 root）
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

---

### Nginx 配置要点

```nginx
server {
    listen 32080;
    server_name localhost;

    root /usr/share/nginx/html;
    index index.html;

    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;

    gzip on;
    gzip_types text/css application/javascript application/json;

    # API 代理
    location ^~ /api/ {
        proxy_pass http://host.docker.internal:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        add_header Access-Control-Allow-Origin * always;
        add_header Access-Control-Allow-Methods "GET, POST, PUT, DELETE, OPTIONS" always;
    }

    # SPA 路由
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 静态资源长缓存
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2)$ {
        expires 30d;
        add_header Cache-Control "public, immutable";
    }
}
```

---

## 七、详细版本信息

### 后端版本清单（v2.5 实测）

| 组件 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 4.0.6 | 核心框架 |
| Java (JDK) | 25 | 编程语言 |
| Spring Security | 7.0.5 | 安全框架 |
| Hibernate | 随 Spring Boot 4.0.6 | ORM（未显式声明，由 BOM 管理） |
| Flyway | 随 Spring Boot 4.0.6 | 数据库迁移（未显式声明） |
| MySQL Connector | 随 Spring Boot 4.0.6 | 数据库驱动（未显式声明） |
| JJWT | 0.12.6 | JWT 令牌处理 |
| EasyExcel | 4.0.3 | Excel 读写 |
| Apache POI | 5.4.0 | Office 文档 |
| Lombok | 1.18.46 | 代码简化 |
| Jackson core | 2.21.1 / 3.1.1 | JSON 序列化 |
| Logback | 1.5.25 | 日志框架 |
| Tomcat (Embed) | 11.0.21 | 内嵌服务器 |
| Commons IO | 2.18.0 | IO 工具 |
| Commons Compress | 1.26.2 | 压缩工具 |

> 注：未显式声明的版本以 `mvn dependency:tree` 实测为准。

### 前端版本清单（H5）

| 组件 | 版本 | 说明 |
|------|------|------|
| Vue | `^3.4.0` | 核心框架 |
| TypeScript | `^5.3.3` | 类型系统 |
| Vite | `^8.0.5` | 构建工具 |
| vue-tsc | `^3.2.6` | 类型检查 |
| Vue Router | `^4.2.5` | 路由管理 |
| Pinia | `^2.1.7` | 状态管理 |
| Vant | `^4.8.0` | UI 组件库 |
| ECharts | `^6.0.0` | 图表库 |
| Vue ECharts | `^8.0.1` | ECharts Vue 组件 |
| Axios | `^1.15.0` | HTTP 客户端 |
| Day.js | `^1.11.10` | 日期处理 |
| XLSX | `^0.18.5` | Excel 处理 |
| File Saver | `^2.0.5` | 文件下载 |
| Vue Draggable Plus | `^0.6.1` | 拖拽 |
| UnoCSS | `^66.6.8` | 原子化 CSS |
| unplugin-auto-import | `^0.17.2` | 自动导入 |
| unplugin-vue-components | `^0.26.0` | 组件自动注册 |
| Sass | `^1.69.5` | SCSS（项目无 .scss 文件，未实际使用） |

### frontend-uniapp 精确版本

| 依赖 | 版本 |
|------|------|
| `vue` | `3.4.21`（精确） |
| `@dcloudio/uni-app` | `3.0.0-alpha-5000920260515001` |
| `pinia` | `2.1.7`（精确） |
| `echarts` | `^6.0.0` |
| `echarts-for-weixin` | `^1.0.2` |
| `dayjs` | `^1.11.10` |
| `vite` | `5.2.8` |
| `typescript` | `5.3.3` |
| `vue-tsc` | `^2.2.10` |
| `@types/node` | `^22.15.21` |

### 数据库版本

| 组件 | 版本 | 说明 |
|------|------|------|
| MySQL (生产) | 8.0 | 生产数据库 |
| MySQL (本地) | 8.4 | 本地开发 |
| Redis | 7.x | 缓存服务器 |

---

## 八、环境变量配置

| 环境变量 | 说明 | 默认值 | 生产建议 |
|---------|------|--------|---------|
| `DB_HOST` | 数据库地址 | `localhost` | 实际 IP |
| `DB_PORT` | 数据库端口 | `3306` | — |
| `DB_NAME` | 数据库名称 | `price_management` | — |
| `DB_USERNAME` | 数据库用户名 | `root` | 专用账号 |
| `DB_PASSWORD` | 数据库密码 | 无默认值（必须配置） | 强密码 |
| `DB_USE_SSL` | 是否使用 SSL | `true` | 生产保持 true |
| `REDIS_HOST` | Redis 地址 | `10.7.5.175` | 实际 IP |
| `REDIS_PORT` | Redis 端口 | `6379` | — |
| `REDIS_USERNAME` | Redis 用户名 | `default` | — |
| `REDIS_PASSWORD` | Redis 密码 | 无默认值（必须配置） | 强密码 |
| `JWT_SECRET` | JWT 密钥 | 无默认值（必须配置） | 随机 256 位密钥 |
| `JWT_EXPIRATION` | JWT 过期时间(毫秒) | `86400000` (24小时) | — |
| `DEFAULT_USER_PASSWORD` | 默认用户密码 | 无默认值（必须配置） | 强密码 |
| `RESET_PASSWORD_ON_STARTUP` | 启动时重置密码 | `false` | 生产必须 false |
| `CORS_ALLOWED_ORIGINS` | CORS 允许来源 | 空 | 实际前端域名 |
| `SPRING_PROFILES_ACTIVE` | 环境 profile | `dev` | `prod` |
| `ALERT_ENABLED` | 是否启用告警 | `false` | 生产建议 true |
| `ALERT_DINGTALK_ENABLED` | 启用钉钉告警 | `false` | 按需配置 |
| `ALERT_DINGTALK_WEBHOOK` | 钉钉 Webhook | 空 | 钉钉群机器人地址 |
| `ALERT_DINGTALK_SECRET` | 钉钉签名密钥 | 空 | 钉钉机器人安全设置 |
| `ALERT_WECHAT_ENABLED` | 启用企业微信告警 | `false` | 按需配置 |
| `ALERT_THRESHOLD_MEMORY` | 内存告警阈值(%) | `90.0` | 按需调整 |
| `ALERT_THRESHOLD_CPU` | CPU 告警阈值(%) | `80.0` | 按需调整 |
| `API_KEY_ENABLED` | 外部 API 开关 | `false` | 按需开启 |
| `API_KEY_ENCRYPTION_KEY` | API Key 加密密钥 | 空 | 32 字节随机 |
| `IP_BLACKLIST_ENABLED` | IP 黑名单过滤器开关 | `true` | 生产保持开启 |
| `IP_BLACKLIST_OBSERVATION_MODE` | IP 黑名单观察模式 | `false` | 首次发布可设为 `true` |
| `IP_BLACKLIST_CACHE_TTL_SECONDS` | IP 黑名单命中结果本地缓存 TTL | `30` | 不超过记录 `expires_at` |
| `IP_BLACKLIST_NEGATIVE_CACHE_TTL_SECONDS` | IP 黑名单未命中结果缓存 TTL | `0` | 默认不缓存，新增黑名单立即生效 |
| `CLIENT_IP_FORWARDED_HEADER_ENABLED` | 是否允许统一客户端 IP 解析器采信可信代理转发头 | `true` | 直连部署可设为 `false` |
| `CLIENT_IP_TRUSTED_PROXIES` | 允许采信代理头的反向代理 IP/CIDR | loopback | 只配置真实反向代理 |
| `IP_BLACKLIST_BYPASS_SOURCES` | 跳过黑名单拦截的来源 IP/CIDR | loopback | 谨慎配置 |
| `NOTIFICATION_OUTBOX_ENABLED` | 通知 Outbox 开关 | `true` | 生产保持 |
| `WECHAT_MINI_NOTIFY_ENABLED` | 小程序通知开关 | `false` | 按需 |

**安全提醒：** 生产环境必须修改所有默认密码和密钥，通过环境变量注入，**禁止在代码或配置文件中硬编码**。

---

## 九、技术选型理由

| 技术 | 选择理由 |
|------|---------|
| Spring Boot 4.x | 最新稳定版，性能提升，支持 Java 25 |
| Java 25 | 最新 LTS，虚拟线程等新特性 |
| Vue 3 | Composition API 更灵活，TypeScript 支持更好，包体积更小 |
| TypeScript | 编译时类型检查，减少运行时错误，IDE 提示更智能 |
| Vant | 移动优先，天然适配 PC/移动双端 |
| Pinia | Vue 3 官方推荐，比 Vuex 更简洁，TypeScript 友好 |
| Redis | 高性能内存数据库，适合缓存热点数据；支持懒加载降级 |
| JWT | 无状态认证，不依赖服务器 Session，天然支持水平扩展 |
| MySQL | 成熟稳定的关系型数据库，生态完善 |
| Flyway | 数据库迁移版本化管理，避免 `ddl-auto: update` 误删数据 |
| EasyExcel | 阿里开源，流式读写，内存占用远低于 POI |
| Docker | 环境一致性，快速部署，便于回滚 |
| Nginx | 高性能反向代理，静态资源服务，Gzip 压缩 |
| Vite | 极速 HMR，Rollup 打包，原生 ES Module |
| ECharts | 中文文档完善，图表类型丰富，主题切换灵活 |

---

*文档版本：v2.0.0 (重构自 技术栈简明说明.md v2.5)*
*最后更新：2026-06-15*
