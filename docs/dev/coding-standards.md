---
title: 代码规范
version: v2.0.0
last_updated: 2026-06-15
source: docs/dev/backup/项目设计规范.md + docs/dev/backup/开发指南.md
---

# 矿产品价格管理系统 - 代码规范

> 本文档整合原 `项目设计规范.md` (一~六) 和 `开发指南.md` (代码风格/注释) 的核心规范，作为新功能开发的统一标准。

---

## 一、统一文字管理（核心原则：禁止硬编码）

### 1.1 字典服务架构

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   sys_dict 表   │────►│  SysDictService │────►│   useDict.ts    │
│  (数据源)       │     │  (后端服务)     │     │  (前端缓存)     │
└─────────────────┘     └─────────────────┘     └─────────────────┘
```

### 1.2 字典数据结构

| 字段 | 类型 | 说明 | 示例 |
|------|------|------|------|
| `category` | VARCHAR(50) | 字典分类 | `currency` |
| `dict_key` | VARCHAR(100) | 字典键 | `CNY` |
| `dict_value` | VARCHAR(200) | 显示值 | `人民币` |
| `extra_value` | TEXT | 扩展值 | `¥` / JSON配置 |
| `sort_order` | INT | 排序 | `1` |
| `status` | VARCHAR(20) | 状态 | `ACTIVE` |

### 1.3 字典分类清单（核心）

| 分类 | 说明 | 示例键值 |
|------|------|----------|
| `currency` | 币种 | CNY→人民币(¥), USD→美元($) |
| `common_status` | 通用状态 | ACTIVE→启用, INACTIVE→停用 |
| `user_role` | 用户角色 | ADMIN→管理员, EDITOR→编辑者, VIEWER→查看者 |
| `dept_type` | 部门类型 | HEADQUARTERS→总部, COMPANY→子公司 |
| `approval_status` | 审批状态 | PENDING→待审批, APPROVED→已通过 |
| `workflow_type` | 工作流类型 | PRICE_CHANGE→价格变更 |
| `node_type` | 节点类型 | APPROVER→审批节点 |
| `operation_type` | 操作类型 | LOGIN→登录, CREATE→创建 |
| `operation_module` | 操作模块 | 用户管理, 定时任务 |
| `unit` | 计量单位 | 元/吨, 万元/吨, 元/克 |
| `origin` / `customer` | 产地/客户 | 自定义编码 |
| `price_metric_group` | 价格指标分组 | PRICE_STATUS→价格现状 (V45+) |
| `price_metric` | 价格指标 | LATEST_PRICE→最新价格 (V45+) |
| `notification_type` | 通知类型 | PRICE_PUBLISHED→价格发布 (V25+) |
| `notification_channel` | 通知渠道 | IN_APP→站内通知 (V25+) |
| `theme` / `style` | 样式主题/配置 | theme_red_green, system_name |

完整字典分类参见 `useDict.ts` 中的 `CATEGORY_LABELS`，新增分类须同步维护。

### 1.4 受保护分类（字典管理页面默认隐藏）

| 分类 | 管理入口 | 原因 |
|------|----------|------|
| `style` / `theme` | 样式设置 | 全局样式配置 |
| `color_scheme` / `layout_style` | 样式设置 | 色彩/布局方案 |
| `font_preset` | 样式设置 | 字号预设 |
| `home_layout` / `home_widget` | 样式设置 | 首页布局/组件 |
| `category_visual_config` | 分类视觉 | 分类视觉方案 |
| `scheduled_task_type` / `scheduled_task_status` | 定时任务 | 调度任务枚举 |

**实现位置：** 前端纯前端过滤（`frontend/src/constants/dictCategoryMeta.ts` 的 `PROTECTED_CATEGORIES`），不在后端做权限隔离。

### 1.5 extraValue 渲染模式

| 模式 | 渲染效果 | 适用分类 |
|------|----------|----------|
| `color` | 色块+色值 | common_status, dept_type, approval_status |
| `icon` | 图标预览 | user_role |
| `json` | 格式化+复制 | category_visual_config |
| `text` | 文本徽章 | currency, unit, origin |
| `readonly` | 只读文本 | 受保护分类 |

### 1.6 前端使用规范

**正确示例：**

```vue
<script setup lang="ts">
import { useDict } from '@/composables/useDict'

const { getStatusLabel, getRoleLabel, getDictOptions, loadAllDicts } = useDict()

onMounted(() => {
  loadAllDicts() // 必须调用，加载字典缓存
})
</script>

<template>
  <span>{{ getStatusLabel(user.status) }}</span>

  <select>
    <option v-for="opt in getDictOptions('user_role')" :key="opt.value" :value="opt.value">
      {{ opt.label }}
    </option>
  </select>

  <span>{{ getCurrencySymbol(product.currency) }}</span>
</template>
```

**禁止示例：**

```vue
<!-- ❌ 禁止硬编码 -->
<span>{{ user.status === 'ACTIVE' ? '启用' : '停用' }}</span>
<option value="ADMIN">管理员</option>
```

### 1.7 后端使用规范

```java
@Service
@RequiredArgsConstructor
public class MyService {
    private final SysDictService sysDictService;

    public String getStatusLabel(String status) {
        return sysDictService.getDictByCategoryAndKey("common_status", status)
            .map(SysDict::getDictValue)
            .orElse(status);
    }
}
```

### 1.8 硬编码禁止模式速查表

| 禁止写法 | 正确写法 | 说明 |
|----------|----------|------|
| `status === 'ACTIVE' ? '启用' : '停用'` | `getStatusLabel(status)` | 状态标签 |
| `<option value="ADMIN">管理员</option>` | `v-for="opt in roleOptions"` | 下拉选项 |
| `{ ADMIN: '管理员', EDITOR: '编辑者' }` | `getRoleLabel(role)` | 角色映射 |
| `currency === 'CNY' ? '¥' : '$'` | `getCurrencySymbol(currency)` | 货币符号 |
| `{ CREATE: '创建', UPDATE: '更新' }` | `getDictValue('change_type', key)` | 通用字典值 |

**例外情况（允许硬编码）：**
- API 请求/响应中的枚举值（如 `status: 'ACTIVE'`） — 数据协议
- 后端 Entity/Enum 中的常量定义
- CSS 类名绑定（如 `:class="status?.toLowerCase()"`）

补充约束：
- 计量单位、状态 toast、角色名称等显示文本不得在模块加载时用中文常量兜底；页面应通过 `computed(() => getDictOptions('unit'))` 或 `getDictValue('common_status', key)` 动态读取。
- 禁止新增不受控的 `v-html` / `innerHTML`。静态 SVG 图标使用受控 key、组件或模板条件渲染；确需富文本时必须先引入白名单 sanitizer 并限制来源。

### 1.9 新增字典流程

1. **数据库插入**：在 `sys_dict` 表新增记录
2. **前端分类标签**：在 `useDict.ts` 的 `CATEGORY_LABELS` 添加分类中文名
3. **便捷方法**：如需要，在 `useDict.ts` 添加便捷方法（如 `getXxxLabel`）
4. **页面使用**：通过 `getDictValue` 或便捷方法获取显示值

---

## 二、统一功能模块

### 2.1 功能模块三层架构

```
功能模块
├── 后端
│   ├── controller/XxxController.java   # REST API
│   ├── service/XxxService.java         # 业务逻辑
│   ├── repository/XxxRepository.java   # 数据访问
│   ├── entity/Xxx.java                  # 实体类
│   └── dto/XxxDTO.java                 # 数据传输对象
└── 前端
    ├── api/xxx.ts                       # API 接口
    ├── views/Xxx.vue                    # 页面组件
    └── types/xxx.ts                     # 类型定义
```

### 2.2 RESTful API 规范

| 操作 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 列表查询 | GET | `/api/xxx` | 分页参数：`page`, `size` |
| 详情查询 | GET | `/api/xxx/{id}` | 返回单个对象 |
| 新增 | POST | `/api/xxx` | 请求体为 JSON |
| 更新 | PUT | `/api/xxx/{id}` | 请求体为 JSON |
| 删除 | DELETE | `/api/xxx/{id}` | 无请求体 |

**统一响应格式：**

```json
{
  "code": 200,
  "message": "操作成功",
  "data": { ... },
  "timestamp": 1779990000000
}
```

### 2.3 Result 完整字段（v1.6.8 实测）

```java
public class Result<T> {
    private Integer code;        // 状态码：200/201/400/401/403/404/409/429/500
    private String  message;     // 默认 "操作成功"（非英文）
    private T       data;        // 业务数据（列表场景为 PageResponse<T>）
    private Long    timestamp;   // 服务端毫秒时间戳
}
```

**字段语义：**
- `code` 业务态码：200=成功，4xx=客户端错误，5xx=服务端错误
- `message` 人类可读文案，默认中文"操作成功"
- `data` 泛型；分页场景下为 `PageResponse<T>`（含 content/totalElements/totalPages/number/size/first/last）
- `timestamp` Long 毫秒时间戳，前端可用于时钟校正

### 2.4 分页查询规范

**请求参数：**

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `page` | int | 0 | 页码（从0开始） |
| `size` | int | 20 | 每页数量 |
| `sort` | string | — | 排序字段 |

---

## 三、统一配置项管理

### 3.1 配置优先级（从高到低）

```
1. 环境变量（生产敏感信息）    DB_PASSWORD, REDIS_PASSWORD, JWT_SECRET
2. application-{profile}.yml  application-dev.yml, application-prod.yml
3. application.yml（默认配置）通用配置、非敏感信息
4. sys_dict 表（动态配置）    系统名称、主题颜色、字体配置
```

### 3.2 敏感配置环境变量化

| 配置项 | 环境变量 | 示例 |
|--------|----------|------|
| 数据库密码 | `DB_PASSWORD` | `${DB_PASSWORD:password}` |
| Redis密码 | `REDIS_PASSWORD` | `${REDIS_PASSWORD:}` |
| JWT密钥 | `JWT_SECRET` | `${JWT_SECRET:secret}` |
| 默认用户密码 | `DEFAULT_USER_PASSWORD` | 通过 `SecurityProperties` |
| API Key 加密密钥 | `API_KEY_ENCRYPTION_KEY` | 32 字节随机 |

### 3.3 SecurityProperties 完整字段（v1.6.8 实测）

**位置：** `backend/src/main/java/com/pricemanagement/config/properties/SecurityProperties.java`

| 字段 | 类型 | 默认值 | 用途 |
|------|------|--------|------|
| `defaultUserPassword` | String | `${DEFAULT_USER_PASSWORD}` | 新用户初始密码 |
| `resetPasswordOnStartup` | boolean | `${RESET_PASSWORD_ON_STARTUP:false}` | 是否每次启动重置密码 |
| `jwtSecret` | String | `${JWT_SECRET}` | JWT 签名密钥（HS256） |
| `jwtExpiration` | long | `86400000L` (24h) | Access Token 过期时间 |
| `corsAllowedOrigins` | List\<String\> | 见 application.yml | CORS 允许的源 |
| `passwordPolicy` | PasswordPolicy | 嵌套对象 | 密码复杂度策略 |
| `clientIp` | ClientIp | 嵌套对象 | 统一客户端 IP 解析、可信代理配置 |
| `ipBlacklist` | IpBlacklist | 嵌套对象 | 应用层 IP 黑名单与缓存配置 |

**PasswordPolicy 嵌套对象：**

| 字段 | 类型 | 默认值 | 用途 |
|------|------|--------|------|
| `minLength` | int | 8 | 密码最小长度 |
| `maxLength` | int | 32 | 密码最大长度 |
| `requireLetter` | boolean | true | 必须包含字母 |
| `requireDigit` | boolean | true | 必须包含数字 |
| `disallowWhitespace` | boolean | true | 禁止密码包含空格 |

> v1.6.11 实测：当前实现仅校验"必须含字母 + 数字 + 无空格 + 长度 8-32"。如需更细粒度（如大写/小写/特殊字符）需在 `PasswordPolicyValidator` 中扩展。

### 3.4 业务分组规范（v1.6.8 增量）

`SecurityProperties` 的所有配置项按业务分组归类：

| 业务组 | 字段 |
|--------|------|
| JWT | `jwtSecret`, `jwtExpiration` |
| 默认用户 | `defaultUserPassword`, `resetPasswordOnStartup` |
| CORS | `corsAllowedOrigins` |
| 密码策略 | `passwordPolicy` (minLength, maxLength, requireLetter, requireDigit, disallowWhitespace) |
| 客户端 IP | `clientIp.forwardedHeaderEnabled`, `clientIp.trustedProxies` |
| IP 黑名单 | `ipBlacklist.enabled`, `ipBlacklist.observationMode`, `ipBlacklist.cacheTtlSeconds`, `ipBlacklist.negativeCacheTtlSeconds`, `ipBlacklist.bypassSources` |

新增配置项必须按业务组归类，禁止散落在多个 Properties 类。

客户端 IP 获取必须通过 `ClientIpResolver.resolve(request)`，业务代码不得直接读取 `X-Forwarded-For` / `X-Real-IP` 或自行拆分代理头。`IpAddressUtil.getClientIp(request)` 仅作为低层安全默认返回 `remoteAddr`；是否采信代理头由 `security.client-ip.*` 统一控制。

### 3.5 动态配置（sys_dict 样式分类）

**样式配置存储在 sys_dict 表（category='style'）：**

| dict_key | 用途 | 示例值 |
|----------|------|--------|
| `system_name` | 系统名称 | 价格管理系统 |
| `price_rise_color` | 涨价颜色 | #EF4444 |
| `price_fall_color` | 跌价颜色 | #10B981 |
| `heading_font` | 标题字体 | Newsreader |
| `body_font` | 正文字体 | Inter |
| `number_font` | 数字字体 | JetBrains Mono |
| `logo_url` | Logo 地址 | /api/static/logo.png |
| `logo_size` | Logo 尺寸 | medium |

**前端加载模式：**

```typescript
// App.vue - 应用启动时加载全局配置
onMounted(async () => {
  await loadAllDicts()      // 加载字典缓存
  await loadThemeConfig()   // 加载主题配置
})
```

---

## 四、自定义颜色系统

### 4.1 CSS 变量体系

```css
:root {
  /* 主色调 */
  --primary-color: #0D6E6E;
  --primary-light: #0D8A8A;
  --primary-dark: #0A5555;

  /* 中性色 */
  --gray-50: #FAFAFA;
  --gray-900: #1A1A1A;

  /* 功能色 */
  --success-color: #10B981;
  --warning-color: #F59E0B;
  --error-color: #EF4444;
  --info-color: #3B82F6;

  /* 动态主题色（运行时可切换） */
  --price-rise-color: #EF4444;
  --price-fall-color: #10B981;
  --price-flat-color: #9CA3AF;

  /* 图表配色（9色） */
  --chart-color-1: #0D6E6E;
  --chart-color-2: #10B981;
  /* ... */
}
```

> v1.6.10 实测核对：上述 CSS 变量值与 `frontend/src/style/variables.css` 实际值完全一致。

### 4.2 主题切换机制

**预设主题：**

| 主题 Key | 名称 | 涨价色 | 跌价色 |
|---------|------|--------|--------|
| `theme_red_green` | 红涨绿跌 | #EF4444 | #10B981 |
| `theme_green_red` | 绿涨红跌 | #10B981 | #EF4444 |
| `theme_blue_orange` | 蓝涨橙跌 | #3B82F6 | #F97316 |
| `theme_purple_gold` | 紫涨金跌 | #8B5CF6 | #EAB308 |

**切换流程：**

```
用户选择主题 → PUT /api/style/theme/{themeKey}
    ↓
后端更新 sys_dict 表
    ↓
前端 useTheme.ts 接收响应 → applyThemeToCSS() 更新 CSS 变量
    ↓
全局样式实时生效
```

### 4.3 颜色使用规范

```vue
<template>
  <!-- 使用 CSS 变量 -->
  <span :style="{ color: `var(--price-${direction}-color)` }">{{ price }}</span>

  <!-- 或使用 useTheme -->
  <span :style="{ color: getPriceColor('up') }">↑</span>
</template>
```

**图表配色（ECharts）：**

```typescript
const chartColors = Array.from({ length: 9 }, (_, i) =>
  getComputedStyle(document.documentElement)
    .getPropertyValue(`--chart-color-${i + 1}`).trim()
)
```

### 4.4 新增颜色变量流程

1. **CSS 变量定义**：在 `variables.css` 添加变量
2. **主题配置**：在 `useTheme.ts` 的 `themeConfig` 添加属性
3. **后端存储**：在 `sys_dict` 表添加配置项（category='style'）
4. **API 支持**：在 `StyleConfigDTO` 添加字段
5. **前端应用**：在 `applyThemeToCSS()` 绑定到 CSS 变量

---

## 五、字体系统

### 5.1 字体层级

| 类型 | CSS 变量 | 默认字体 | 用途 |
|------|---------|----------|------|
| 标题 | `--font-heading` | Newsreader | 页面标题、大标题 |
| 正文 | `--font-body` | Inter | 正文、表单、按钮 |
| 数字 | `--font-mono` | JetBrains Mono | 价格、数字、代码 |

### 5.2 字号规范

| 变量 | 值 | 用途 |
|------|-----|------|
| `--font-size-xs` | 0.8125rem (13px) | 辅助信息、标签、小徽章 |
| `--font-size-sm` | 0.875rem (14px) | 表格内容、小标题 |
| `--font-size-base` | 1rem (16px) | 正文、表头、主要文字 |
| `--font-size-lg` | 1.125rem (18px) | 小节标题、卡片标题 |
| `--font-size-xl` | 1.25rem (20px) | 页面副标题 |
| `--font-size-2xl` | 1.5rem (24px) | 页面主标题 |
| `--font-size-3xl` | 2rem (32px) | 特大标题、价格数字 |

### 5.3 字号预设方案

| 方案 | base | 适用场景 |
|------|------|----------|
| 紧凑 | 0.9375rem | 高密度但保持可读 |
| 标准 | 1rem | 通用场景 |
| 大字体 | 1.125rem | 阅读友好 |
| 特大字体 | 1.25rem | 演示/投影/无障碍 |

---

## 六、组件设计规范

### 6.1 命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| Vue 组件 | PascalCase | `ProductList.vue` |
| API 文件 | camelCase | `product.ts` |
| 类型文件 | camelCase | `product.ts` |
| CSS 类名 | kebab-case | `.product-card` |
| CSS 变量 | kebab-case | `--primary-color` |
| Java 类 | PascalCase | `ProductService` |
| Java 方法/变量 | camelCase | `getProducts()` |
| Java 常量 | UPPER_SNAKE_CASE | `MAX_PAGE_SIZE` |
| 数据库表 | 下划线命名法 | `product_category` |

### 6.2 Vue 组件结构模板

```vue
<template>
  <!-- 模板 -->
</template>

<script setup lang="ts">
// 1. 导入
import { ref, onMounted } from 'vue'
import { useDict } from '@/composables/useDict'

// 2. 类型定义
interface Props { ... }

// 3. 响应式状态
const loading = ref(false)

// 4. 组合式函数
const { loadAllDicts } = useDict()

// 5. 生命周期
onMounted(() => { loadAllDicts() })

// 6. 方法
const handleClick = () => { ... }
</script>

<style scoped>
/* 组件样式 */
</style>
```

### 6.3 表单验证规范

```typescript
const rules = {
  name: [
    { required: true, message: '请输入名称' },
    { pattern: /^[一-龥a-zA-Z0-9]+$/, message: '仅支持中英文和数字' }
  ]
}
```

### 6.4 动态适配规范（v1.6.8 增量）

**核心原则：优先动态适配，避免静态硬编码。**

所有涉及数量、颜色、配置项的渲染，必须优先考虑动态适配，而非静态写死。

| 场景 | 禁止写法 | 正确写法 |
|------|----------|----------|
| 列表渲染 | 写死固定数量的元素 | 使用 `v-for` 动态渲染 |
| 颜色显示 | 写死颜色值 | 使用 CSS 变量或 props |
| 配置项数量 | 固定 N 个配置项 | 根据数据源动态生成 |

```vue
<!-- ❌ 禁止：固定 6 个元素 -->
<div class="chart-bar color-1"></div>
<div class="chart-bar color-2"></div>

<!-- ✅ 正确：动态渲染 + CSS 变量 -->
<div
  v-for="idx in chartColorCount"
  :key="idx"
  class="chart-bar"
  :style="{
    background: `var(--chart-color-${idx})`,
    height: `${baseHeight + (idx - 1) * step}px`
  }"
></div>
```

**检查清单：**

- [ ] 列表/数组类数据使用 `v-for` 动态渲染
- [ ] 颜色值使用 CSS 变量，不写死
- [ ] 数量由数据源决定，不写死固定值
- [ ] 后端扩展配置项时，前端无需修改

---

## 七、数据库表设计规范

### 7.1 表命名规范

- 使用下划线命名法：`product_category`
- 系统表前缀：`sys_`（如 `sys_user`, `sys_dict`）
- 业务表无前缀：`product`, `price`

### 7.2 必须字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | 主键，自增 |
| `created_time` | DATETIME | 创建时间 |
| `updated_time` | DATETIME | 更新时间 |

### 7.3 状态字段

```sql
status VARCHAR(20) DEFAULT 'ACTIVE'
```

**状态值统一使用字典：**

| 键 | 值 |
|------|------|
| `ACTIVE` | 启用 |
| `INACTIVE` | 停用 |

### 7.4 乐观锁

涉及并发更新的表必须添加版本号：

```sql
version BIGINT NOT NULL DEFAULT 0
```

```java
@Entity
public class Product {
    @Version
    private Long version;
}
```

### 7.5 Flyway 迁移规范（v1.6.8 增量）

**配置：**

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-version: 12
    baseline-on-migrate: true
    validate-on-migrate: true
```

**脚本存放：** `backend/src/main/resources/db/migration/V{n}__{description}.sql`

**命名规范：**

- `V{n}` 从 V1 顺序递增，n 为单调递增整数
- 文件名描述用下划线分隔，全英文小写
- 同一文件内禁止包含多条 DDL 语义（如必须分文件）
- 修改既有表的字段：`ALTER TABLE` 不得破坏数据；新增字段必须允许 NULL 或有默认值
- 删除字段：先 `ALTER TABLE ... DROP COLUMN` 并在 `operation_log` 记录

**当前进度：** V1 ~ V46（2026-06-14）。

### 7.6 字典分类同步（v1.6.8 增量）

V23 之后新增的字典分类，必须同步更新到 `useDict.ts` 的 `CATEGORY_LABELS`：

| 分类 | 引入版本 | 示例 |
|------|----------|------|
| `price_metric_group` | V45 | PRICE_STATUS→价格现状 |
| `price_metric` | V45 | LATEST_PRICE→最新价格 |
| `notification_type` | V25 | PRICE_PUBLISHED→价格发布 |
| `notification_channel` | V25 | IN_APP→站内通知 |

新增 Flyway 脚本引入新字典分类时，**必须同步更新前端**。

---

## 八、安全规范

### 8.1 认证机制

- **JWT 双 Token**：Access Token（24h）+ Refresh Token（7d）
- **Token 存储**：localStorage（H5）/ AsyncStorage（APP）
- **请求头**：`Authorization: Bearer {token}`

### 8.2 权限控制

**后端（@PreAuthorize）：**

```java
@PreAuthorize("hasRole('ADMIN')")
@GetMapping("/admin/users")
public Result<List<User>> listUsers() { ... }
```

**前端（路由守卫）：**

```typescript
router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  if (to.meta.roles && !to.meta.roles.includes(userStore.role)) {
    next('/403')
  } else {
    next()
  }
})
```

### 8.3 XSS 防护

```typescript
import DOMPurify from 'dompurify'
const safeHtml = DOMPurify.sanitize(userInput)
```

### 8.4 错误处理规范

**后端异常处理：**

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return Result.fail(400, message);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("系统异常", e);
        return Result.fail(500, "系统繁忙，请稍后重试");
    }
}
```

**自定义业务异常：**

```java
public class BusinessException extends RuntimeException {
    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BusinessException notFound(String resource) {
        return new BusinessException(404, resource + "不存在");
    }
}
```

**前端错误处理：**

```typescript
async function request<T>(config: AxiosRequestConfig): Promise<T> {
  try {
    const response = await axios.request<Result<T>>(config)
    if (response.data.code !== 200) {
      showToast(response.data.message)
      throw new Error(response.data.message)
    }
    return response.data.data
  } catch (error: any) {
    if (!error.response) {
      showToast('网络连接失败')
      throw error
    }
    if (error.response?.status === 401) {
      router.push('/login')
      throw error
    }
    const message = error.response?.data?.message || '请求失败'
    showToast(message)
    throw error
  }
}
```

**错误码规范：**

| 错误码 | 说明 | 处理方式 |
|--------|------|----------|
| 400 | 参数错误 | 显示错误信息 |
| 401 | 未认证 | 跳转登录页 |
| 403 | 无权限 | 显示无权限提示 |
| 404 | 资源不存在 | 显示不存在提示 |
| 409 | 业务冲突 | 显示冲突原因 |
| 500 | 服务器错误 | 显示通用错误 |

### 8.5 操作日志规范（@OperationLog 注解）

所有数据变更操作必须记录日志：

```java
@PostMapping
@OperationLog(module = "产品管理", type = OperationType.CREATE, description = "新增产品")
public Result<Product> create(@RequestBody ProductDTO dto) {
    // ...
}
```

**操作类型枚举：**

| 类型 | 说明 |
|------|------|
| `LOGIN` | 用户登录 |
| `LOGOUT` | 用户登出 |
| `CREATE` | 新增数据 |
| `UPDATE` | 更新数据 |
| `DELETE` | 删除数据 |
| `VIEW` | 查看数据 |
| `EXPORT` | 导出数据 |
| `IMPORT` | 导入数据 |

---

## 九、代码风格规范

### 9.1 后端 Java 规范

- **缩进**：4 个空格
- **变量和方法**：camelCase
- **类名**：PascalCase
- **常量**：UPPER_SNAKE_CASE
- **Lombok**：使用 `@Data` / `@RequiredArgsConstructor` 简化代码
- **日志**：使用 `@Slf4j`（如 `@Slf4j public class XxxService { ... }`）

### 9.2 前端代码规范

- **缩进**：2 个空格
- **变量和方法**：camelCase
- **组件名**：PascalCase
- **文件名**：kebab-case
- **类型安全**：使用 TypeScript
- **代码检查**：使用 ESLint

### 9.3 前端编码规范（v1.6.8 增量）

**Composition API 优先：**

```vue
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'

const count = ref(0)
const double = computed(() => count.value * 2)
onMounted(() => { /* ... */ })
</script>
```

**类型定义放在 `src/types/`：**

```typescript
// src/types/product.ts
export interface Product {
  id: number
  name: string
  // ...
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  // ...
}
```

**API 抽象放在 `src/api/`：**

```typescript
// src/api/products.ts
export const getProducts = async (params: PageRequest): Promise<ApiResponse<PageResponse<Product>>> => {
  return await http.get('/api/products', { params })
}
```

---

## 十、注释规范

### 10.1 注释原则

1. **说明 WHY，而非 WHAT**：代码本身应该足够清晰表达"做什么"，注释应解释"为什么这样做"
2. **保持注释与代码同步**：修改代码时必须同步更新相关注释
3. **避免冗余注释**：不要注释显而易见的代码
4. **使用标准格式**：统一使用 Javadoc（Java）和 JSDoc（TypeScript）格式

### 10.2 后端 Java 注释

**类级别注释：**

```java
/**
 * 用户认证服务
 * 处理用户登录、登出、Token 刷新等认证相关操作
 *
 * <p>设计说明：
 * <ul>
 *   <li>使用 JWT 无状态认证，支持分布式部署</li>
 *   <li>Access Token 有效期 24 小时，Refresh Token 有效期 7 天</li>
 * </ul>
 *
 * @see JwtUtil
 * @see RefreshTokenService
 */
@Service
public class AuthService { }
```

**方法级别注释：**

```java
/**
 * 用户登录
 *
 * @param request 登录请求，包含用户名和密码
 * @return 登录响应，包含 Access Token、Refresh Token 和用户信息
 * @throws BadCredentialsException 用户名或密码错误
 */
public LoginResponse login(LoginRequest request) { }
```

**字段注释（实体类）：**

```java
/**
 * 刷新令牌表
 * 存储 JWT Refresh Token，支持 Token 撤销和续期
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {
    /** Token 值（唯一） */
    @Column(nullable = false, unique = true, length = 500)
    private String token;

    /** 过期时间 */
    @Column(nullable = false)
    private LocalDateTime expiryDate;
}
```

**章节分隔（复杂类）：**

```java
// ==================== 状态定义 ====================
private User user;

// ==================== 登录/登出 ====================
public void login() { }

// ==================== Token 管理 ====================
```

### 10.3 前端 TypeScript/Vue 注释

**模块级别：**

```typescript
/**
 * 用户状态管理 Store
 * 管理用户登录状态、Token、用户信息等
 *
 * Token 存储策略：
 * - Access Token：24 小时，存储在 localStorage
 * - Refresh Token：7 天，存储在 localStorage
 * - 自动刷新：http.ts 拦截器在 401 时自动使用 refresh token 刷新
 */
```

**函数注释：**

```typescript
/**
 * 刷新 Access Token
 * 使用 Refresh Token 获取新的 Access Token
 *
 * @returns 新的 Access Token，失败返回 null
 */
const refreshAccessToken = async (): Promise<string | null> => { }
```

---

## 十一、uniapp 多端编码说明（v1.6.8 增量）

`frontend-uniapp` 项目独立技术栈，**禁止直接复制 H5 代码**：

| 差异点 | H5 (frontend) | uniapp |
|--------|---------------|--------|
| 路由 API | `useRouter()` (vue-router) | `uni.navigateTo()` |
| 存储 | `localStorage` | `uni.getStorageSync()` |
| 网络请求 | `axios` (http.ts) | `uni.request()` |
| UI 组件 | Vant 4 | uni-app 内置组件 |
| 响应式单位 | px / rem | rpx |

**适配原则：**

1. 复用 `useDict` / `useUserStore` 等纯逻辑 composable
2. 网络层可复用 API 签名，但底层实现用 `uni.request`
3. UI 必须用 uni-app 组件，**禁止在 uniapp 中用 Vant**
4. 平台特定代码通过 `uni.getSystemInfoSync().platform` 判断

详见 [frontend-uniapp/README.md](../../frontend-uniapp/README.md)。

---

## 十二、检查清单（新增功能）

开发完成后逐项检查：

- [ ] 控制器接口路径与前端 API 调用路径一致
- [ ] Entity 字段与数据库表结构一致（`@Table` / `@Column` / `@JoinColumn`）
- [ ] `@Transient` 标记不映射到数据库的字段
- [ ] 所有编码值显示名称通过 `getXxxLabel()` / `getDictValue()` 获取
- [ ] 列表/配置项使用 `v-for` 动态渲染，不写死数量
- [ ] 颜色使用 CSS 变量，不写死颜色值
- [ ] 涉及数据变更的接口添加 `@OperationLog` 注解
- [ ] 敏感配置通过环境变量管理，不硬编码
- [ ] 新增字典分类同步更新 `useDict.ts` 的 `CATEGORY_LABELS`
- [ ] Flyway 迁移脚本幂等、可重复执行
- [ ] 双端适配：H5 页面同时支持 PC 和移动端布局
- [ ] 错误处理使用 `Result.fail()` + `BusinessException`，不直接抛 500

---

*文档版本：v2.0.0 (重构自 项目设计规范.md v1.6.8 + 开发指南.md v1.6.8)*
*最后更新：2026-06-15*
