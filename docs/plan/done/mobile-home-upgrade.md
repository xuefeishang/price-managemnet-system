# 移动端 Home 页面升级方案

## 背景

当前移动端（uni-app）Home 页面功能较简单，仅包含：
- 日期选择
- 分类筛选
- 重点关注指标（横向滚动卡片）
- 产品列表（网格布局）

H5 端 Home 页面功能完善，包含：
- 经营摘要（产品总数、今日更新、覆盖品类、价格异动）
- 核心指标（重点产品价格卡片）
- 重点走势（价格曲线图表）
- 产品列表（支持表格/卡片模式、分页、排序、搜索、分类筛选）
- 风险预警（价格异常提醒）

需要将移动端 Home 页面升级到与 H5 端一致的版式。

---

## 差异分析

### 功能对比

| 功能模块 | H5 端 | 移动端 | 差异 |
|---------|-------|--------|------|
| 日期选择 | ✅ 顶部日期选择器 | ✅ 已有 | 一致 |
| 经营摘要 | ✅ SummarySection 组件 | ❌ 无 | 需新增 |
| 核心指标 | ✅ 重点产品价格卡片 | ✅ 重点关注指标 | 布局不同 |
| 分类筛选 | ✅ CategoryFilterPanel | ✅ 横向滚动 | 样式不同 |
| 重点走势 | ✅ TrendAnalysisChart 图表 | ❌ 无 | 需新增 |
| 产品列表 | ✅ 表格/卡片双模式 + 分页 | ✅ 网格布局 | 需增强 |
| 风险预警 | ✅ RiskAlertsPanel | ❌ 无 | 需新增 |
| 价格曲线联动 | ✅ 点击产品联动主曲线 | ❌ 无 | 需新增 |

### API 对比

| API | H5 端 | 移动端 |
|-----|-------|--------|
| getHomeDashboard | ✅ | ❌ 需新增 |
| getHomeSummary | ✅ | ❌ 需新增 |
| getPriceAlerts | ✅ | ❌ 需新增 |
| getPricesByDateWithStats | ✅ | ✅ 已有 |
| getPriceTrend | ✅ | ❌ 需新增 |
| getCategories | ✅ | ✅ 已有 |
| getProducts | ✅ | ✅ 已有 |

---

## 技术方案

### 1. API 层扩展

新增 `frontend-uniapp/src/api/home.ts`：

```typescript
// 经营摘要
export interface HomeSummary {
  totalProducts: number
  updatedToday: number
  coveredCategories: number
  priceChangeCount: number
}

export async function getHomeSummary(date: string): Promise<ApiResponse<HomeSummary>>

// 价格预警
export interface PriceAlert {
  productId: number
  productName: string
  currentPrice: number
  previousPrice: number
  changePercent: number
  alertLevel: 'HIGH' | 'MEDIUM' | 'LOW'
  alertDate: string
}

export async function getPriceAlerts(date: string): Promise<ApiResponse<PriceAlert[]>>

// 价格趋势
export interface PriceTrendPoint {
  date: string
  price: number
}

export async function getPriceTrend(productId: number, days: number): Promise<ApiResponse<PriceTrendPoint[]>>
```

### 2. 组件设计

#### 2.1 经营摘要组件

**文件位置：** `frontend-uniapp/src/components/home/SummarySection.vue`

**UI 设计：**
- 移动端采用 2x2 网格布局
- 每个指标卡片：图标 + 数值 + 标签
- 支持点击跳转到详情页

```
┌─────────────┬─────────────┐
│  产品总数   │  今日更新   │
│     22      │      5      │
├─────────────┼─────────────┤
│  覆盖品类   │  价格异动   │
│      6      │      3      │
└─────────────┴─────────────┘
```

**样式要点：**
- 背景：渐变色或纯色（跟随主题）
- 圆角：16rpx
- 间距：24rpx
- 数值字号：48rpx（加粗）
- 标签字号：24rpx（次要色）

#### 2.2 重点走势组件

**文件位置：** `frontend-uniapp/src/components/home/TrendChart.vue`

**UI 设计：**
- 使用 uni-app 内置 canvas 或 uCharts
- 支持 30/90/180 天切换
- 响应式高度：400rpx

**数据流：**
1. 选择产品 → 调用 `getPriceTrend(productId, days)`
2. 渲染折线图
3. 支持手势缩放（可选）

**uni-app 图表方案：**
- 方案 A：uCharts（推荐，uni-app 生态）
- 方案 B：lime-echart（ECharts 适配）
- 方案 C：原生 canvas 手绘

#### 2.3 风险预警组件

**文件位置：** `frontend-uniapp/src/components/home/RiskAlertsPanel.vue`

**UI 设计：**
- 纵向列表
- 每项：产品名 + 涨跌幅 + 预警级别标签
- 颜色区分：HIGH（红）、MEDIUM（橙）、LOW（黄）

```
┌─────────────────────────────────┐
│ ⚠️ 风险预警                  3  │
├─────────────────────────────────┤
│ 铜精矿              +5.2%  🔴   │
│ 锌精矿              -3.1%  🟠   │
│ 铅精矿              +2.0%  🟡   │
└─────────────────────────────────┘
```

#### 2.4 产品列表增强

**增强点：**
1. 添加分页（下拉加载更多）
2. 添加排序（按分类/价格/更新时间）
3. 支持表格模式切换（移动端可选）

### 3. 页面布局重构

**新版布局：**

```
┌─────────────────────────────────┐
│  Header: 日期选择 + 刷新        │
├─────────────────────────────────┤
│  经营摘要 SummarySection        │
├─────────────────────────────────┤
│  分类筛选 CategoryFilter        │
├─────────────────────────────────┤
│  核心指标 FeaturedProducts      │
│  (横向滚动卡片)                 │
├─────────────────────────────────┤
│  重点走势 TrendChart            │
│  (选中产品的价格曲线)           │
├─────────────────────────────────┤
│  产品列表 ProductList           │
│  (网格布局 + 分页)              │
├─────────────────────────────────┤
│  风险预警 RiskAlertsPanel       │
│  (可折叠)                       │
└─────────────────────────────────┘
```

### 4. 状态管理

**新增 composable：** `frontend-uniapp/src/composables/useHomeState.ts`

```typescript
export function useHomeState() {
  const selectedDate = ref(getYesterday())
  const selectedProductId = ref<number | null>(null)
  const trendDays = ref(30)
  const homeSummary = ref<HomeSummary | null>(null)
  const priceAlerts = ref<PriceAlert[]>([])

  // 加载经营摘要
  async function loadSummary() { ... }

  // 加载价格预警
  async function loadAlerts() { ... }

  // 加载价格趋势
  async function loadTrend(productId: number) { ... }

  return {
    selectedDate,
    selectedProductId,
    trendDays,
    homeSummary,
    priceAlerts,
    loadSummary,
    loadAlerts,
    loadTrend
  }
}
```

---

## 实现步骤

### 阶段一：API 与数据层（1 天）

1. 新增 `frontend-uniapp/src/api/home.ts`
2. 新增类型定义 `frontend-uniapp/src/types/home.ts`
3. 新增 `useHomeState` composable

### 阶段二：经营摘要组件（0.5 天）

1. 创建 `SummarySection.vue` 组件
2. 实现 2x2 网格布局
3. 接入 API 数据

### 阶段三：重点走势图表（1.5 天）

1. 调研 uni-app 图表方案，选择 uCharts
2. 创建 `TrendChart.vue` 组件
3. 实现产品选择联动
4. 实现时间范围切换

### 阶段四：风险预警组件（0.5 天）

1. 创建 `RiskAlertsPanel.vue` 组件
2. 实现预警列表
3. 实现颜色编码

### 阶段五：产品列表增强（1 天）

1. 添加下拉加载更多
2. 添加排序功能
3. 优化卡片样式（与 H5 一致）

### 阶段六：页面整合与测试（1 天）

1. 重构 `pages/home/index.vue`
2. 集成所有组件
3. 响应式适配测试
4. H5/小程序/APP 三端测试

---

## 关键参考文件

| 文件 | 用途 |
|------|------|
| `frontend/src/views/Home.vue` | H5 端 Home 页面主逻辑 |
| `frontend/src/components/home/SummarySection.vue` | 经营摘要组件 |
| `frontend/src/components/home/TrendAnalysisChart.vue` | 趋势图表组件 |
| `frontend/src/components/home/RiskAlertsPanel.vue` | 风险预警组件 |
| `frontend/src/composables/useHomeConfig.ts` | Home 配置状态管理 |
| `frontend/src/api/home.ts` | Home 相关 API |
| `frontend-uniapp/src/pages/home/index.vue` | 移动端现有 Home 页面 |

---

## 风险与注意事项

1. **图表兼容性**
   - uCharts 在不同平台表现可能有差异
   - 需要在 H5/小程序/APP 三端测试

2. **性能优化**
   - 首页数据量大时需考虑分页加载
   - 图表数据需做缓存

3. **样式一致性**
   - 颜色、字号、圆角需与 H5 端保持一致
   - 参考 `useTheme` 中的 CSS 变量

4. **权限控制**
   - 风险预警可能需要特定权限
   - 编辑功能需检查 `userStore.canEdit`

---

## 验收标准

1. ✅ 经营摘要显示正确（产品总数、今日更新、覆盖品类、价格异动）
2. ✅ 重点走势图表可正常显示，支持产品切换和时间范围切换
3. ✅ 风险预警列表正确显示，颜色编码正确
4. ✅ 产品列表支持分页加载
5. ✅ 三端（H5/小程序/APP）显示一致
6. ✅ 无明显性能问题

---

## 时间估算

| 阶段 | 工作量 |
|------|--------|
| API 与数据层 | 1 天 |
| 经营摘要组件 | 0.5 天 |
| 重点走势图表 | 1.5 天 |
| 风险预警组件 | 0.5 天 |
| 产品列表增强 | 1 天 |
| 页面整合与测试 | 1 天 |
| **总计** | **5.5 天** |

---

*文档版本：v1.0*
*创建日期：2026-05-26*
