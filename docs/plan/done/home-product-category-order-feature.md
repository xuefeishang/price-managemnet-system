# Home Product Category Order Feature

## Context

`style-settings` 的 Home 页面排序能力目前只完成了“首页组件顺序”和“全局产品顺序”两部分。

现状缺口：

- `frontend/src/components/style-settings/HomeSortPanel.vue` 只按 `product.sort_order` 展示一个扁平产品列表。
- `frontend/src/views/Home.vue` 已经按分类分组展示产品列表，但分组顺序依赖 `product_category.sort_order`，样式设置里没有分类顺序调整入口。
- 产品排序没有按分类分组编辑，用户无法先调整分类，再调整某个分类内的产品前后顺序。
- 当前排序面板虽然拉取 `/api/products` 的启用产品，但交互没有直观展示“当前系统启用分类 + 各分类启用产品”的真实结构。
- 后端已有 `Product.sortOrder` 和 `ProductCategory.sortOrder` 字段，数据库也已有 `product.sort_order`、`product_category.sort_order`，本次优先复用现有字段，不新增表。

目标：

在 `style-settings` 的 Home 排序配置中补齐“产品列表排序”能力：

1. 先调整首页产品列表的分类顺序。
2. 再进入每个分类，调整该分类下启用产品的前后顺序。
3. 面板数据动态来自当前系统真实启用分类和启用产品。
4. Home 页面、重点关注指标、重点走势、分类分组产品列表消费同一排序结果。

## 方案评分目标

目标评分：**9.5+ / 10**

达标标准：

- 业务口径清楚：分类顺序和分类内产品顺序各有唯一数据来源。
- 视觉层级清楚：用户一眼能理解“左边排分类，右边排当前分类产品”。
- 操作路径符合当前样式设置习惯：排序项统一拖拽操作，拖拽过程通过行间插入线提示落点。
- 保存模型不让用户困惑：排序草稿集成到顶部“保存配置”，未保存状态明确。
- 数据真实动态：排序面板展示当前启用分类与启用产品，不使用静态样例。
- Home 页面消费一致：产品列表、重点关注、重点走势不出现不同排序口径。
- 移动端可用：小屏不强行双栏，采用分类选择 + 产品列表的上下结构。

## 实现方案

### 1. 排序数据模型

复用现有数据库字段：

| 对象 | 字段 | 用途 |
|------|------|------|
| `product_category` | `sort_order` | 首页产品列表分类分组顺序 |
| `product` | `sort_order` | 同一分类内产品顺序 |
| `product` | `show_on_home` | 重点关注指标、重点走势候选产品标记 |
| `product.status` | `ACTIVE` | 样式设置排序面板只展示启用产品 |
| `product_category.status` | `ACTIVE` | 样式设置排序面板只展示启用分类 |

排序规则统一为：

```text
分类顺序：category.sortOrder ASC, category.name ASC, category.id ASC
分类内产品顺序：product.sortOrder ASC, product.name ASC, product.id ASC
未分类产品：作为“未分类”虚拟分组，永远排在分类分组之后
```

说明：

- `product.sort_order` 从“全局产品顺序”收敛为“分类内产品顺序”。
- 不要求不同分类下的产品 `sort_order` 全局唯一。
- 当产品移动到其他分类时，后续可在产品保存逻辑中将其放到目标分类末尾；本次排序面板只负责当前分类内排序。

### 2. 后端接口补齐

#### 2.1 分类批量排序

新增接口：

```http
POST /api/categories/batch-sort
```

请求：

```json
[
  { "id": 1, "sortOrder": 1 },
  { "id": 2, "sortOrder": 2 }
]
```

响应：

```json
{
  "code": 200,
  "message": "批量更新分类排序成功",
  "data": null
}
```

涉及文件：

- `backend/src/main/java/com/pricemanagement/controller/ProductCategoryController.java`
- `backend/src/main/java/com/pricemanagement/service/ProductCategoryService.java`
- `frontend/src/api/categories.ts`

权限：

- `ADMIN`、`EDITOR` 可调整。
- 需要记录操作日志，模块建议为 `产品分类管理`，类型 `UPDATE`。

#### 2.2 产品批量排序约束调整

保留现有接口：

```http
POST /api/products/batch-sort
```

但调用方式调整为“按分类提交当前分类内产品顺序”：

```json
[
  { "id": 11, "sortOrder": 1 },
  { "id": 12, "sortOrder": 2 },
  { "id": 13, "sortOrder": 3 }
]
```

后端可继续使用现有 `ProductService.batchUpdateSort()`，但建议补充校验：

- 请求为空直接返回成功。
- 请求中不存在的产品 ID 忽略或返回明确错误，推荐返回错误，避免用户误以为保存成功。
- 如要严格保证“同分类内排序”，可新增可选 `categoryId` 参数或 DTO，但第一阶段可由前端按分类提交。

#### 2.3 排序数据聚合接口（主方案）

为避免前端分别拉分类和产品后自行拼接导致状态不一致，必须新增聚合接口作为排序面板的数据源：

```http
GET /api/home/product-order
```

响应：

```json
[
  {
    "category": {
      "id": 1,
      "name": "黑色金属",
      "code": "BLACK_METAL",
      "sortOrder": 1,
      "status": "ACTIVE"
    },
    "products": [
      {
        "id": 101,
        "name": "铁精粉",
        "code": "IRON_FINE",
        "sortOrder": 1,
        "showOnHome": true,
        "status": "ACTIVE",
        "unit": "元/吨",
        "currency": "CNY"
      }
    ]
  }
]
```

聚合规则：

- 只返回启用分类。
- 每个分类只返回启用产品。
- 启用但无产品的分类保留，显示为“暂无启用产品”，仍可参与分类排序。
- 有启用产品但分类为空时，返回一个虚拟分组：

```json
{
  "category": null,
  "virtualKey": "uncategorized",
  "name": "未分类",
  "products": []
}
```

涉及文件：

- `backend/src/main/java/com/pricemanagement/controller/HomeController.java` 或新增 `HomeProductOrderController.java`
- `backend/src/main/java/com/pricemanagement/service/HomeDashboardService.java` 或新增轻量查询服务
- `frontend/src/api/home.ts`

前端不再在排序面板中用多个接口拼装排序树。`getCategories()` 和 `getProducts()` 仍可被 Home 页面、分类管理和产品管理复用，但 `style-settings` 的 Home 产品排序工作台以 `/api/home/product-order` 为准。

### 3. 前端交互设计

改造 `HomeSortPanel.vue`，保留上半部分“首页组件顺序”，重做下半部分“首页产品列表顺序”。

#### 3.1 最终交互形态

桌面端采用左右双栏，不使用嵌套卡片。排序区域是一个完整配置区，内部左右两列：

```text
首页产品列表顺序
已修改                                      顶部“保存配置”生效

左侧：分类顺序
  1 黑色金属    4 个启用产品
  2 有色金属    6 个启用产品
  3 贵金属      3 个启用产品
  4 化工产品    5 个启用产品

右侧：当前分类内产品顺序
  黑色金属
  1 铁精粉        首页展示
  2 螺纹钢        -
  3 热轧卷板      首页展示
```

移动端可以改为：

- 顶部显示分类分段/列表，点击切换当前分类。
- 分类排序入口仍在上方，用单列列表展示。
- 当前分类产品顺序列表在下方。
- 主保存按钮固定在排序区域底部或随 Style Settings 状态栏展示，不遮挡内容。

视觉层级：

- 分类列宽建议 `280px - 340px`，产品列自适应。
- 分类项选中态使用主色弱底 + 左侧 3px 色条，不使用过重阴影。
- 分类项显示序号、分类名、启用产品数量；无启用产品时数量显示为 `0 个启用产品`。
- 产品项显示序号、产品名、规格、`首页展示` 徽标；规格为空时显示产品编码或 `未填写规格`。
- 拖拽手柄使用图标按钮，按钮有 `title`/tooltip；不再提供上移/下移按钮，减少同一排序任务的重复入口。
- 空状态只说明当前状态，不写教程式文案。

推荐布局：

```text
┌────────────────────────────────────────────────────────────┐
│ 首页产品列表顺序                         已修改            │
├───────────────────────┬────────────────────────────────────┤
│ 分类顺序              │ 黑色金属                            │
│ ① 黑色金属  4         │ ① 铁精粉       首页展示             │
│ ② 有色金属  6         │ ② 螺纹钢                            │
│ ③ 贵金属    3         │ ③ 热轧卷板     首页展示             │
│ ④ 化工产品  5         │                                    │
└───────────────────────┴────────────────────────────────────┘
```

#### 3.2 分类排序能力

功能：

- 拖拽调整分类顺序。
- 拖拽过程中显示选中态、行间插入线和列表底部投放区。
- 显示每个分类的启用产品数量。
- 选中分类后，右侧展示该分类下启用产品。
- 无产品分类仍可排序，并显示空状态。
- 保存由 Style Settings 顶部“保存配置”统一完成，不提供排序区域内的第二个主按钮。

前端 API：

```ts
export const batchUpdateCategorySort = async (
  items: { id: number; sortOrder: number }[]
): Promise<ApiResponse<void>> => {
  return await http.post('/api/categories/batch-sort', items)
}
```

#### 3.3 分类内产品排序能力

功能：

- 拖拽调整当前分类内产品顺序。
- 拖拽过程中显示选中态、行间插入线和列表底部投放区。
- 集成顶部“保存配置”：一次保存分类顺序 + 所有已调整分类的产品顺序。
- 显示 `showOnHome` 标记，但不在此面板修改 `showOnHome`，避免排序和展示开关职责混杂。
- 产品不能跨分类拖动；跨分类调整属于产品编辑里的分类变更。

产品列表只展示：

```ts
product.status === 'ACTIVE'
```

产品排序提交：

```ts
await batchUpdateProductSort(currentGroup.products.map((product, index) => ({
  id: product.id,
  sortOrder: index + 1
})))
```

#### 3.4 草稿与保存状态

建议在 `HomeSortPanel.vue` 内维护局部草稿，不直接修改服务端：

```ts
interface HomeProductOrderGroup {
  category: ProductCategory | null
  virtualKey?: 'uncategorized'
  name: string
  products: Product[]
  dirty?: boolean
}
```

状态：

- `loading`：加载真实启用分类和产品。
- `dirty`：任意分类顺序或产品顺序是否有未保存变更。
- `categoryDirty`：分类顺序是否有未保存变更。
- `productDirtyCategoryIds`：哪些分类内产品顺序有未保存变更。
- `saving`：保存中。
- `selectedGroupKey`：当前正在编辑产品顺序的分类。

保存策略：

- 排序区域不再提供独立主保存按钮，排序草稿纳入顶部 `保存配置`。
- 保存时先提交分类顺序，再提交产品顺序；任一步失败时提示具体失败阶段，并保留草稿不丢失。
- 保存成功后重新拉取 `/api/home/product-order`，以服务端真实结果覆盖本地草稿。
- 顶部“保存配置”同时负责样式字典配置、首页体验配置、分类视觉配置与首页产品排序草稿。
- 产品/分类排序是业务主数据排序，保存成功后触发 `eventBus.emit('product-sort-updated')` 和 `eventBus.emit('category-sort-updated')`。
- 用户离开页面且存在 `dirty` 时，应复用项目现有离开确认机制；如暂未有统一机制，本次至少在切换 Style Settings 分区前提示。

按钮状态：

| 状态 | 顶部保存配置 |
|------|--------------|
| 无变更 | disabled，状态显示 `当前配置` 或 `已保存` |
| 有变更 | enabled，状态显示 `有未保存的更改` |
| 保存中 | disabled，文案 `保存中...` |
| 保存失败 | enabled，保留草稿，显示错误提示 |

#### 3.5 拖拽排序实现建议

首选引入或复用项目已有 Vue 拖拽能力；如项目没有现成依赖，第一阶段可采用按钮排序，并将拖拽列为同一迭代内的增强项。

建议优先评估：

- `vue-draggable-plus`
- `vuedraggable`

选择原则：

- Vue 3 兼容。
- TypeScript 类型可接受。
- 移动端触控排序稳定。
- 不引入重型 UI 框架。

如果不引入依赖：

- 桌面端使用原生 Drag and Drop。
- 移动端沿用拖拽排序交互，避免同一页面出现两套排序入口。
- 验收时不把移动端拖拽作为硬性要求，但桌面端至少要有高效排序方式。

#### 3.6 无障碍与键盘操作

- 每个排序项的拖拽手柄都需要 `title`。
- 选中分类项应有明显视觉状态，并使用 `aria-current="true"` 或等价语义。
- 保存按钮 loading 时禁用，避免重复提交。
- 排序后焦点不应丢失到页面顶部。

### 4. 首页消费排序规则

改造 `frontend/src/views/Home.vue`：

#### 4.1 分类映射

现有：

```ts
const categoryMap = computed(() => {
  const map = new Map<number, ProductCategory>()
  categories.value.forEach(category => map.set(category.id, category))
  return map
})
```

保留，但需要确保 `categories` 由 `getCategories('ACTIVE')` 返回，并按 `sortOrder` 排序。

#### 4.2 产品分组排序

现有 `filteredProductGroups` 已接近目标：

```ts
return Array.from(groups.values()).sort((a, b) => {
  if (a.id === 'uncategorized') return 1
  if (b.id === 'uncategorized') return -1
  return (a.category?.sortOrder || 0) - (b.category?.sortOrder || 0)
})
```

建议补齐稳定排序：

```ts
return Array.from(groups.values()).sort((a, b) => {
  if (a.id === 'uncategorized') return 1
  if (b.id === 'uncategorized') return -1
  return (a.category?.sortOrder ?? 0) - (b.category?.sortOrder ?? 0) ||
    a.name.localeCompare(b.name, 'zh-CN') ||
    Number(a.id) - Number(b.id)
})
```

#### 4.3 重点关注和重点走势

当前：

```ts
const homeProducts = computed(() =>
  sortProductsByHomeOrder(products.value.filter(p => p.showOnHome && p.status === 'ACTIVE'))
)
```

建议调整为“先分类顺序，再分类内产品顺序”，保持与产品列表一致：

```ts
const sortProductsByHomeOrder = (items: Product[]) =>
  [...items].sort((a, b) =>
    getCategorySortOrder(a) - getCategorySortOrder(b) ||
    (a.sortOrder ?? 0) - (b.sortOrder ?? 0) ||
    a.name.localeCompare(b.name, 'zh-CN') ||
    a.id - b.id
  )
```

这样 `showOnHome` 产品取前 N 个时，也会遵循分类顺序。

#### 4.4 事件刷新

现有：

```ts
unsubscribeProductSort = eventBus.on('product-sort-updated', loadData)
```

建议新增：

```ts
unsubscribeCategorySort = eventBus.on('category-sort-updated', async () => {
  await loadCategories()
  await loadData()
})
```

并把 `onMounted` 中加载分类的逻辑抽成 `loadCategories()`，避免重复代码。

### 5. 前端组件拆分建议

第一阶段可以只改 `HomeSortPanel.vue`，但为了长期维护，建议拆分：

| 文件 | 职责 |
|------|------|
| `frontend/src/components/style-settings/HomeSortPanel.vue` | 页面装配，保留首页组件顺序 + 产品排序区域 |
| `frontend/src/components/style-settings/HomeProductOrderPanel.vue` | 分类顺序与分类内产品顺序主交互 |
| `frontend/src/composables/useHomeProductOrderState.ts` | 加载分组、移动分类、移动产品、接入统一保存、dirty 状态 |
| `frontend/src/api/categories.ts` | 新增 `batchUpdateCategorySort()` |
| `frontend/src/api/home.ts` | 推荐新增 `getHomeProductOrder()` |

如时间紧，可先在 `HomeSortPanel.vue` 内完成，后续再抽 composable。

## 关键参考文件

- `frontend/src/components/style-settings/HomeSortPanel.vue`
- `frontend/src/views/Home.vue`
- `frontend/src/composables/useHomeConfig.ts`
- `frontend/src/api/products.ts`
- `frontend/src/api/categories.ts`
- `frontend/src/api/home.ts`
- `frontend/src/types/index.ts`
- `backend/src/main/java/com/pricemanagement/entity/Product.java`
- `backend/src/main/java/com/pricemanagement/entity/ProductCategory.java`
- `backend/src/main/java/com/pricemanagement/controller/ProductController.java`
- `backend/src/main/java/com/pricemanagement/controller/ProductCategoryController.java`
- `backend/src/main/java/com/pricemanagement/service/ProductService.java`
- `backend/src/main/java/com/pricemanagement/service/ProductCategoryService.java`
- `backend/src/main/java/com/pricemanagement/repository/ProductRepository.java`
- `backend/src/main/java/com/pricemanagement/repository/ProductCategoryRepository.java`
- `backend/src/main/resources/init.sql`
- `backend/src/main/resources/数据字典.md`

## 实现步骤

### P0：确认排序口径

1. 明确 `product.sort_order` 改为分类内排序，不再表达全局产品排序。
2. 明确 `product_category.sort_order` 是首页产品列表分类顺序的唯一来源。
3. 明确 `show_on_home` 只控制重点关注和重点走势候选，不控制产品列表是否出现。

### P1：后端分类排序接口

1. 在 `ProductCategoryService` 增加 `batchUpdateSort(List<Map<String, Object>> items)`。
2. 在 `ProductCategoryController` 增加 `POST /api/categories/batch-sort`。
3. 增加操作日志记录。
4. 检查 `ProductCategory` ORM 注解与 `init.sql`、`数据字典.md` 一致：
   - `@Table(name = "product_category")`
   - `@Column(name = "sort_order")`
   - `@Column(name = "status")`

### P2：排序数据加载

1. 新增 `GET /api/home/product-order` 聚合接口。
2. 后端一次性返回启用分类和各分类启用产品。
3. 前端新增 `getHomeProductOrder()`。
4. 排序面板只消费该聚合接口，不再自行拼接分类和产品。

### P3：前端排序面板

1. 改造 `HomeSortPanel.vue` 的产品排序区域。
2. 展示分类顺序列表。
3. 展示选中分类下产品顺序列表。
4. 支持分类拖拽排序，并提供拖拽选中、行间插入线和末尾投放区。
5. 支持分类内产品拖拽排序，并提供拖拽选中、行间插入线和末尾投放区。
6. 将排序 dirty 状态接入顶部 `保存配置`，不在排序区域提供第二个主保存按钮。
7. 保存时调用 `batchUpdateCategorySort()` 和 `batchUpdateProductSort()`。
8. 保存成功后重新拉取聚合接口并触发事件刷新首页。
9. 补齐桌面端和移动端布局，保证文本不溢出、按钮不换行挤压。

### P4：首页排序消费

1. 抽出 `loadCategories()`。
2. 增强 `sortProductsByHomeOrder()`，按分类顺序 + 分类内产品顺序排序。
3. 增强 `filteredProductGroups` 的分类排序稳定性。
4. 监听 `category-sort-updated`。

### P5：文档同步

功能完成后按 AGENTS.md 要求检查并更新：

- `README.md`：补充“首页产品列表支持分类顺序和分类内产品顺序调整”。
- `docs/dev/开发指南.md`：补充排序接口和前后端联动规则。
- `docs/dev/项目设计文档.md`：更新样式设置模块、产品分类接口、首页排序说明。
- `docs/dev/UI设计说明.md`：补充 Style Settings 首页排序面板交互。
- `docs/archive/项目完成总结.md`：更新功能完成情况。
- `backend/src/main/resources/数据字典.md`：本次不新增字段，但需确认 `product_category.sort_order`、`product.sort_order` 说明与“分类顺序/分类内顺序”一致。

## Verification

### 后端验证

```bash
cd backend
mvn test
```

重点检查：

- `/api/categories/batch-sort` 可保存分类顺序。
- `/api/products/batch-sort` 可保存同一分类内产品顺序。
- `GET /api/categories?status=ACTIVE` 返回顺序按 `sort_order ASC`。
- 如新增 `/api/home/product-order`，确认只返回启用分类和启用产品。

### 前端验证

```bash
cd frontend
npm run build
```

手工验证：

1. 进入 `style-settings` 的 Home 排序区域。
2. 分类列表应动态展示当前启用分类，并显示每个分类下启用产品数量。
3. 调整分类顺序并保存，刷新 Home 页面后产品分组顺序同步变化。
4. 选择某个分类，调整分类内产品顺序并保存，刷新 Home 页面后该分类下产品顺序同步变化。
5. 禁用一个产品后，该产品不再出现在排序面板和 Home 产品列表。
6. 禁用一个分类后，该分类不再出现在排序面板；其产品如果仍启用但分类不可用，需要按产品管理现有规则处理，前端至少不应崩溃。
7. 未分类启用产品显示在“未分类”分组，分组位于最后。
8. 重点关注指标和重点走势按分类顺序 + 分类内产品顺序取 `showOnHome` 产品前 N 个。
9. 排序区域有未保存变更时，保存按钮可用且状态明确。
10. 保存失败时草稿不丢失，错误提示能说明是分类排序还是产品排序失败。
11. 桌面端左右双栏在 1280、1440、1920 宽度下不拥挤。
12. 移动端 375、390、430 宽度下采用上下结构，产品名、规格、徽标不重叠。
13. 拖拽排序后序号立即刷新。
14. 首项上移、末项下移按钮禁用。

### 视觉验收

满分 10 分，验收目标 9.5+：

| 维度 | 权重 | 验收标准 |
|------|------|----------|
| 信息架构 | 2 | 分类和产品两个层级清晰，不需要说明文字也能理解 |
| 操作效率 | 2 | 分类和产品都支持快速排序，数量多时不痛苦 |
| 状态反馈 | 2 | loading、dirty、saving、success、error 状态完整 |
| 响应式 | 1.5 | 桌面双栏舒展，移动端上下结构可用 |
| 一致性 | 1.5 | 与 Style Settings 现有视觉语言一致 |
| 稳定性 | 1 | 禁用、空分类、未分类、保存失败等边界不破坏界面 |

必须达到：

- 不使用静态样例数据。
- 不把 UI 说明文字写成教程。
- 不出现卡片套卡片造成的视觉噪音。
- 不用颜色硬编码表达状态名称；状态展示遵循字典规范或通用业务标识。
- 产品项长名称、长规格在桌面和移动端都不能覆盖按钮。

### 一致性检查

- 控制器路径与前端 API 一致：
  - `/api/categories/batch-sort`
  - `/api/products/batch-sort`
  - 可选 `/api/home/product-order`
- TypeScript 类型与后端 Entity 字段一致：
  - `ProductCategory.sortOrder`
  - `Product.sortOrder`
  - `Product.showOnHome`
- 数据库字段与 ORM 注解一致：
  - `product_category.sort_order`
  - `product.sort_order`
  - `product.show_on_home`
- 不新增编码值中文硬编码；状态展示仍通过字典服务或已有通用显示逻辑处理。

## 风险与边界

- 如果继续把 `product.sort_order` 当全局排序使用，分类内排序会和分类顺序互相干扰；本次必须统一口径。
- 排序面板必须使用聚合接口；如果只在前端本地拼接分类和产品，可能出现分类状态变更后的短暂不一致。
- 排序面板不负责修改 `showOnHome`，避免和产品编辑职责冲突。
- 本次不新增数据库字段，因此原则上无需数据库迁移；但需要确认旧数据的 `sort_order` 是否合理，必要时提供一次性 SQL 规范化脚本。
