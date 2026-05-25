# Home 首页价格动态字体适配方案

## Context

Home 页面中的价格数字、币种符号、计量单位在不同显示器分辨率、浏览器宽度、首页卡片列数和产品名称/产地信息长度组合下，容易出现挤压、换行不自然或数字视觉比例失衡。

当前问题集中在：

- PC 产品卡片价格使用固定 `2.25rem`，卡片列数增加或浏览器宽度变小时会挤压单位与趋势图。
- 移动端重点产品卡片宽度较窄，价格、单位、涨跌标签同处底部，容易显得拥挤。
- 普通产品列表右侧价格固定 `1.25rem`，当数字较长、单位较长时会变形或破坏右侧对齐。
- 首页已有全局字号预设，但价格数字是业务关键视觉元素，不能简单跟随全局正文比例线性放大或缩小。

目标是建立一套“有设计感的动态字体系统”：价格保持强识别、单位保持清晰、窄容器不变形、宽容器不浪费空间，并与分类视觉、产地信息、趋势图共同形成稳定层级。

## 设计目标

1. **价格优先**：价格数字是主视觉，单位与币种是辅助信息，不与价格争抢权重。
2. **容器适配**：根据卡片自身宽度调整，而不是直接使用视口宽度，避免大屏/小屏粗暴缩放。
3. **长度适配**：短价格可更有力量，长价格自动收敛，不撑破布局。
4. **单位稳态**：计量单位优先与价格同排；空间不足时变为下方小胶囊或次行信息。
5. **数字美感**：使用等宽数字、紧凑行高、统一基线，保证多卡片扫描时整齐。
6. **动效克制**：尺寸变化使用颜色、透明度、轻微位移和容器过渡，不用夸张动画。

## 核心方案

### 1. 建立首页价格专属 Token

在 Home 页面局部定义价格排版 token，不直接污染全局字号体系：

```css
.home-page {
  --home-price-size-hero: 2.25rem;
  --home-price-size-card: 2rem;
  --home-price-size-list: 1.25rem;
  --home-price-unit-size: var(--font-size-xs);
  --home-price-line-height: 0.95;
  --home-price-gap: 4px;
}
```

这些 token 是“默认态”，后续由容器查询和长度等级覆盖。

### 2. 用容器查询替代视口字号

给产品卡片和列表项开启容器上下文：

```css
.product-card-pc,
.home-featured-item-mobile,
.product-item {
  container-type: inline-size;
}
```

根据卡片实际宽度做离散适配：

| 容器宽度 | 价格字号 | 单位策略 | 适用场景 |
|----------|----------|----------|----------|
| `>= 320px` | 强展示 | 单位同排 | PC 2-3 列卡片 |
| `260-319px` | 标准展示 | 单位同排但缩小 | PC 4列、窄窗口 |
| `220-259px` | 紧凑展示 | 单位可换到次行 | 小屏重点卡片 |
| `< 220px` | 极窄展示 | 单位胶囊化、趋势图弱化 | 移动端横滑小卡 |

示例规则：

```css
@container (max-width: 280px) {
  .price-value {
    font-size: 1.75rem;
  }

  .price-row {
    flex-wrap: wrap;
    row-gap: 2px;
  }

  .price-unit {
    flex-basis: 100%;
  }
}
```

### 3. 按价格字符串长度增加等级

仅靠容器宽度不够，因为 `¥980` 和 `¥128,000.50` 占用空间完全不同。需要在模板中按价格展示文本长度增加 class：

```ts
const getPriceSizeClass = (product: Product) => {
  const value = lastPriceCache.value.get(product.id)
  const length = `${getCurrencySymbolLocal(product.currency)}${value || '--'}`.length
  if (length >= 11) return 'price-long'
  if (length >= 8) return 'price-medium'
  return 'price-short'
}
```

等级含义：

| 等级 | 条件 | 视觉策略 |
|------|------|----------|
| `price-short` | 7字符以内 | 强势字号、宽松留白 |
| `price-medium` | 8-10字符 | 略收字号，保持同排 |
| `price-long` | 11字符以上 | 收敛字号，单位降级，必要时允许次行 |

### 4. 拆分价格结构，强化层级

当前价格和币种在同一个 `.price-value` 内，难以分别控制。建议改为：

```vue
<div class="price-row" :class="getPriceSizeClass(product)">
  <span class="price-currency">{{ getCurrencySymbolLocal(product.currency) }}</span>
  <span class="price-number">{{ lastPriceCache.get(product.id) }}</span>
  <span class="price-unit">/ {{ unit }}</span>
</div>
```

视觉规则：

- `price-number` 使用最大字号和 `font-variant-numeric: tabular-nums`。
- `price-currency` 比数字小一级，顶部略抬，与数字形成金融感层级。
- `price-unit` 使用弱色和小字号，窄容器下变为次行或小胶囊。
- 空价格 `--` 保持中性灰，不占用强视觉权重。

### 5. 单位的三段式适配

单位不再只是跟在数字后面，而是按空间自动切换：

| 状态 | 触发条件 | 展示方式 |
|------|----------|----------|
| Inline | 宽容器/短价格 | `/ 吨` 与价格同排 |
| Subline | 窄容器/中长价格 | 单位换到价格下方，左对齐 |
| Capsule | 移动端小卡/长单位 | 变为浅底小胶囊，避免视觉漂移 |

CSS 方向：

```css
.price-unit {
  color: var(--text-muted);
  font-size: var(--home-price-unit-size);
  white-space: nowrap;
}

@container (max-width: 260px) {
  .price-unit {
    flex-basis: 100%;
    width: fit-content;
    padding: 2px 6px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--primary-color) 8%, transparent);
  }
}
```

### 6. 趋势图与价格互相让位

PC 卡片底部是“价格 + 趋势图”。当容器变窄时，不应让价格被趋势图挤压：

- 宽容器：价格左侧，趋势图右侧。
- 中容器：趋势图宽度从 `80px` 收敛到 `64px`。
- 窄容器：趋势图移到价格下方或隐藏为细线背景。
- 移动重点卡：趋势图已经在底部，可保持 100% 宽度，但价格行优先完整展示。

### 7. 动态变化的设计感

动态不是跳变，而是“有秩序的响应”：

- 卡片容器使用 `transition: border-color, box-shadow, transform, background-color`。
- 价格字号变化通过离散容器查询触发，不使用 `vw`。
- 单位从同排切到次行时使用 `opacity` 与 `transform` 过渡。
- `prefers-reduced-motion: reduce` 下禁用位移动效。

```css
.price-row,
.price-unit,
.chart-area {
  transition:
    color var(--transition-fast),
    opacity var(--transition-fast),
    transform var(--transition-fast);
}
```

## 实现方案

### 前端改造点

1. `frontend/src/views/Home.vue`
   - 新增 `getDisplayPrice(product)`、`getDisplayUnit(product)`、`getPriceSizeClass(product)`。
   - 将首页四处价格展示统一改为 `price-currency + price-number + price-unit` 结构。
   - 给 PC 产品卡片、移动重点卡片、移动列表项加入容器查询。
   - 增加 `price-short / price-medium / price-long` 样式。

2. `frontend/src/components/home/MetricsCardGrid.vue`
   - 若仍用于首页核心指标或后续复用，同步使用同一价格结构和 class。

3. `frontend/src/components/home/TrendAnalysisChart.vue`
   - 重点走势卡片中的当前价也采用同一结构，但尺寸低一级，避免压过图表。

4. `frontend/src/components/style-settings/preview/HomePreview.vue`
   - 预览骨架中加入价格数字长度变化示意，展示宽/窄卡片下的价格层级。

### CSS 设计规则

推荐基础样式：

```css
.price-row {
  display: flex;
  align-items: flex-end;
  gap: var(--home-price-gap);
  min-width: 0;
}

.price-currency {
  font-family: var(--font-mono);
  font-size: 0.48em;
  font-weight: 700;
  line-height: 1;
  color: var(--text-secondary);
  transform: translateY(-0.18em);
}

.price-number {
  font-family: var(--font-mono);
  font-size: var(--home-price-size-card);
  font-weight: 750;
  line-height: var(--home-price-line-height);
  color: var(--primary-color);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.price-row.price-medium .price-number {
  font-size: calc(var(--home-price-size-card) * 0.9);
}

.price-row.price-long .price-number {
  font-size: calc(var(--home-price-size-card) * 0.78);
}
```

### 容器查询建议

```css
@container (max-width: 300px) {
  .price-number {
    font-size: 1.75rem;
  }
}

@container (max-width: 250px) {
  .price-row {
    flex-wrap: wrap;
  }

  .price-unit {
    flex-basis: 100%;
    margin-top: 2px;
  }
}
```

## 关键参考文件

- `frontend/src/views/Home.vue`：首页产品卡片、移动产品列表、价格展示样式。
- `frontend/src/components/home/MetricsCardGrid.vue`：重点产品指标卡片复用组件。
- `frontend/src/components/home/TrendAnalysisChart.vue`：重点走势产品卡片。
- `frontend/src/components/style-settings/preview/HomePreview.vue`：样式设置首页预览。
- `frontend/src/style/variables.css`：全局字体、颜色和价格 token 的现有基础。
- `docs/dev/UI设计说明.md`：首页视觉规则记录。

## 实现步骤

1. **统一价格数据函数**
   - 提取价格字符串、币种符号、单位展示。
   - 增加价格长度等级 class。

2. **改造模板结构**
   - 首页 PC 重点卡片。
   - 首页 PC 分类产品卡片。
   - 移动端重点卡片。
   - 移动端列表项。
   - 趋势卡和指标卡复用组件。

3. **加入容器查询与等级样式**
   - PC 卡片价格尺寸。
   - 移动卡片价格尺寸。
   - 列表右侧价格尺寸。
   - 单位换行/胶囊化。

4. **同步样式设置预览**
   - 预览里体现价格层级和单位位置。

5. **文档同步**
   - 更新 `README.md` 功能说明。
   - 更新 `docs/dev/UI设计说明.md` 首页价格排版规则。
   - 更新 `docs/dev/开发指南.md` 首页价格展示实现约束。
   - 更新 `docs/archive/项目完成总结.md`。
   - 本方案不涉及数据库变更，不需要更新数据字典结构。

## Verification

### 自动验证

- `cd frontend && npm.cmd run build`

### 视觉验证

至少检查这些浏览器宽度：

| 宽度 | 检查点 |
|------|--------|
| 375px | 移动重点卡价格不溢出，单位可读 |
| 430px | 移动列表右侧价格与单位不重叠 |
| 768px | 移动/平板卡片转场自然 |
| 1024px | PC 三列或四列卡片价格不挤压趋势图 |
| 1366px | 常见笔记本宽度卡片比例美观 |
| 1920px | 大屏价格不过度放大，卡片留白协调 |

### 数据验证

手工构造或确认以下价格样本：

- `¥980`
- `¥6,850`
- `¥68,500`
- `¥128,000.50`
- `--`

单位样本：

- `吨`
- `公斤`
- `元/吨`
- `美元/吨`

验收标准：

- 价格、单位、趋势图不重叠。
- 价格数字不横向压缩变形。
- 单位在窄容器下可读，不抢主视觉。
- 多张卡片横向扫描时数字基线整齐。
- 未影响全局字号预设和样式设置已有配置。
