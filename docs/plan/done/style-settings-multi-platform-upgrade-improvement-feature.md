# Style Settings 多端升级后改善开发方案

**创建日期：2026-05-21**  
**适用范围：** `frontend/src/views/StyleSettings.vue`、`frontend/src/components/style-settings/*`、`frontend/src/components/style-settings/preview/*`、`frontend/src/composables/useStyleSettingsWorkbench.ts`、`frontend/src/composables/useTheme.ts`、`frontend/src/views/Login.vue`  
**目标：** 修复 Style Settings 多端升级后的构建阻断问题，并将预览体验从“可用”提升到“可发布、可信、可决策”的 9.5 分水平。

> 文档定位：本文作为 Style Settings 预览与多端升级后的唯一后续执行方案，合并并替代 `style-settings-preview-95-improvement-feature.md` 的未完成改进项。旧文档可归档为阶段性目标记录，后续开发以本文为准。

---

## Context

Style Settings 页面已按照 `docs/plan/multi-platform-adaptation.md` 的多端适配思路完成升级：

- PC 端三栏工作台已具备
- 移动端横向导航与预览抽屉已具备
- `StylePreviewPanel.vue` 已拆分为多个 preview 子组件
- `activeSection` 已由父组件驱动，避免导航状态不同步
- 顶部状态栏已显示中文方案名
- 首页、分类、布局、版本预览已经具备初步同源和对比能力

但当前仍存在以下问题：

| 问题 | 当前表现 | 影响 |
|------|----------|------|
| 前端构建失败 | `npm run build` 报 `Login.vue` 中 `themeConfig.value.logoSize/logoUrl` 类型错误 | 阻断发布 |
| 版本对比不够可决策 | `VersionComparePreview` 只展示 `changeSummary`，未解析 `configSnapshot` | 回滚前无法判断具体影响 |
| 首页预览非实时编辑态同源 | `HomePreview` mounted 时读取字典缓存 | 中央编辑变化不一定即时反映 |
| 分类预览非实时编辑态同源 | `CategoryPreview` 自己维护选中分类与视觉配置 | 中央编辑分类时右侧可能不同步 |
| 布局 resolver 仍有复制逻辑 | `layoutPreviewResolver` 和 `applyLayoutVariables` 各写一套规则 | 后续可能漂移 |
| 配置保存与预览边界不清 | 配置调整可能立即持久化并创建版本快照 | 高频试错会污染版本记录，Logo base64 会放大快照体积 |
| 移动端视口尚未系统验收 | 有抽屉和横向导航，但缺少 390/430 等截图验收 | 小屏体验存在未知风险 |

本方案用于把升级后的系统从当前约 **8.2/10** 提升至 **9.5/10**。

9.5 分版本的一句话标准：

**用户在 Style Settings 里改什么，右侧就以真实业务片段展示什么；用户点击保存后才全局生效并形成有意义版本；用户回滚前能判断差异；刷新、切换、移动端查看都一致可信。**

本文保留原 9.5 改进方案中的体验总纲，并结合当前升级后的实际代码状态，重新组织为当前阶段可执行任务。

---

## 实现方案

### 0. 已完成能力基线与保留标准

以下能力在当前升级中已基本完成，但仍作为后续验收标准保留，避免回退：

| 能力 | 当前状态 | 保留标准 |
|------|----------|----------|
| section 单源状态 | 已由 `props.activeSection` 驱动 | 总览快捷跳转后，导航、中间配置、右侧预览必须同步 |
| 预览组件拆分 | 已拆出 `preview/` 子目录 | `StylePreviewPanel.vue` 只做编排，复杂区块独立维护 |
| 顶部中文方案名 | 已使用中文映射 | 顶部不展示 `scheme_xxx`、`layout_xxx`、`standard` 等技术 key |
| PC/移动工作台 | 已有 PC 三栏和移动端抽屉 | 后续改动不得破坏 1024px 以下移动可用性 |

预览组件清单应保持完整：

```text
frontend/src/components/style-settings/preview/
  PreviewFrame.vue
  SystemOverviewPreview.vue
  BrandPreview.vue
  ColorPreview.vue
  TypographyPreview.vue
  LayoutPreview.vue
  HomePreview.vue
  CategoryPreview.vue
  VersionComparePreview.vue
```

`StylePreviewPanel.vue` 职责边界：

- 接收 `editingConfig`、`activeSection`、`targetVersion`
- 根据 `PREVIEW_BLOCKS_BY_SECTION` 获取区块
- 渲染对应 preview component
- 传递同源预览数据
- 不承载具体业务预览模板和复杂样式

目标：

- `StylePreviewPanel.vue` 控制在 150 行以内
- preview 子组件职责清楚
- `PreviewFrame.vue` 统一标题、说明、内容和空状态样式

### 1. 修复构建阻断：Login 主题配置类型访问

当前构建错误：

```text
src/views/Login.vue(23,36): Property 'logoSize' does not exist on type 'ComputedRef<...>'
src/views/Login.vue(29,27): Property 'logoUrl' does not exist on type 'ComputedRef<...>'
```

修复方向：

- 检查 `useTheme()` 返回的 `themeConfig`
- 明确 `themeConfig` 是 `ComputedRef<StyleConfig>` 还是 reactive 对象
- 统一访问方式，避免 TypeScript 推断失配

推荐修复：

```typescript
// Login.vue
const logoSizeStyle = computed(() => {
  const config = themeConfig.value
  const size = sizeMap[config.logoSize] || '36px'
  return { height: size }
})

const logoUrlFull = computed(() => {
  const url = themeConfig.value.logoUrl
  if (!url) return ''
  return url.startsWith('http') ? url : `${import.meta.env.VITE_API_BASE_URL || ''}${url}`
})
```

如类型仍不稳定，则在 `useTheme.ts` 中显式返回：

```typescript
const readonlyThemeConfig = computed<StyleConfig>(() => themeConfig)
```

验收：

- `cd frontend && npm run build` 通过
- 登录页 Logo 仍能正常显示
- Style Settings 修改 Logo 后，登录页刷新后能读取最新配置

### 2. 统一保存与草稿预览机制

当前 Style Settings 属于高频试错页面。色彩、字号、布局、Logo 展示等配置不应在每次输入、切换、拖动时立即持久化，否则会造成两个问题：

- 用户还在探索视觉方案时，系统已经改变全局配置
- 每个细小操作都生成版本快照，版本记录失去回滚价值

目标：

- 配置变化时只更新右侧预览
- 用户点击“保存配置”后才持久化到服务端
- 保存成功后才创建版本快照并应用到全局 CSS 变量
- 用户可放弃修改，恢复到最近一次服务端保存状态

三阶段状态模型：

| 状态 | 含义 | 用途 |
|------|------|------|
| `serverConfig` | 服务端已保存配置 | 放弃修改、回滚基准、刷新后的真实状态 |
| `draftConfig` | 当前编辑中配置 | 中央表单与右侧预览的数据源 |
| `appliedConfig` | 已应用到全局 CSS 变量的配置 | 登录页、首页、业务页面实际生效样式 |

交互数据流：

```text
中央配置面板修改
  → updateDraft(patch)
  → draftConfig 更新
  → StylePreviewPanel 局部响应
  → 不调用保存接口，不写入全局 CSS 变量

点击保存配置
  → saveConfig()
  → updateStyleConfig(draftConfig)
  → 服务端持久化
  → 创建轻量版本快照
  → serverConfig / appliedConfig 同步
  → applyThemeToCSS(savedConfig)
  → 全局样式生效

点击放弃修改
  → discardChanges()
  → draftConfig 恢复为 serverConfig
  → 预览恢复到最近保存状态
```

前端改造重点：

| 文件 | 改造内容 |
|------|----------|
| `frontend/src/composables/useStyleSettingsWorkbench.ts` | 新增 `updateDraft`、`saveConfig`、`discardChanges`，废弃“修改即保存”的组合方法 |
| `frontend/src/views/StyleSettings.vue` | 接入保存状态、离开保护、保存/放弃操作 |
| `frontend/src/components/style-settings/StyleSettingsShell.vue` | 在工作台顶部或操作区展示保存状态和主操作按钮 |
| `frontend/src/components/style-settings/*Panel.vue` | 表单变化统一调用 `updateDraft(patch)` |
| `frontend/src/components/style-settings/StylePreviewPanel.vue` | 绑定 `draftConfig`，不读取全局已应用状态作为预览主源 |

`useStyleSettingsWorkbench.ts` 推荐接口：

```typescript
type SaveStatus = 'idle' | 'dirty' | 'saving' | 'saved' | 'failed'

const updateDraft = (patch: Partial<StyleConfig>) => {
  if (!draftConfig.value) return
  Object.assign(draftConfig.value, patch)
  saveStatus.value = 'dirty'
}

const saveConfig = async () => {
  if (!draftConfig.value || saveStatus.value === 'saving') return
  saveStatus.value = 'saving'

  try {
    const savedConfig = await updateStyleConfig(draftConfig.value)
    serverConfig.value = cloneStyleConfig(savedConfig)
    draftConfig.value = cloneStyleConfig(savedConfig)
    appliedConfig.value = cloneStyleConfig(savedConfig)
    applyThemeToCSS(savedConfig)
    saveStatus.value = 'saved'
    lastSavedAt.value = new Date().toISOString()
  } catch (error) {
    saveStatus.value = 'failed'
    lastError.value = getErrorMessage(error)
  }
}

const discardChanges = () => {
  if (!serverConfig.value) return
  draftConfig.value = cloneStyleConfig(serverConfig.value)
  saveStatus.value = 'idle'
  lastError.value = ''
}
```

保存状态展示原则：

| 状态 | 展示 | 行为 |
|------|------|------|
| `idle` | 无未保存修改 | 保存、放弃按钮置灰 |
| `dirty` | 有未保存修改 | 可保存、可放弃 |
| `saving` | 保存中 | 禁止重复保存 |
| `saved` | 已保存 | 短暂反馈后回到稳定态 |
| `failed` | 保存失败 | 保留草稿，可重试保存 |

说明：

- `saveStatus` 是组件内部 UI 状态，不属于业务编码值；可在组件内维护中文提示。
- 若未来该状态进入后端、日志、字典配置或跨页面展示，则必须改为常量 + 字典/统一映射，遵守项目“禁止硬编码编码显示名”规范。

未保存离开保护：

- 路由离开时，如果 `saveStatus === 'dirty'`，提示用户保存或放弃。
- 浏览器刷新/关闭时，如果存在未保存修改，触发离开确认。
- 保存失败时不得覆盖 `draftConfig`，用户修正后可再次保存。
- 放弃修改只恢复当前页面草稿，不额外创建版本。

全局样式边界：

- 草稿编辑期间禁止调用 `applyThemeToCSS()` 写入 `document.documentElement`。
- 右侧预览通过局部 style、CSS 变量容器或 preview token 响应 `draftConfig`。
- 保存成功后才将 `savedConfig` 应用到全局 CSS 变量。
- 放弃修改后预览必须回到 `serverConfig`，真实业务页面保持 `appliedConfig`。

### 3. 版本快照轻量化与 Logo 治理

当前 Logo 可能以 base64 或长 URL 形式进入配置。如果完整写入 `configSnapshot`，会造成版本记录体积异常，并降低版本列表加载、解析和对比效率。

目标：

- 版本快照只记录可用于回滚决策的配置项
- Logo 图片大字段不进入快照
- 仍保留 Logo 是否变化、引用信息或 hash，避免审计信息完全丢失

推荐快照结构：

```json
{
  "config": {
    "systemName": "矿产品价格管理系统",
    "activeColorScheme": "scheme_blue",
    "activeLayoutStyle": "layout_compact",
    "fontSizePreset": "standard"
  },
  "assetRefs": {
    "logoUrl": {
      "changed": true,
      "hash": "sha256:xxxx",
      "storageKey": "style/logo/main.png"
    }
  },
  "excludedFields": ["logoUrl", "logoUrlLogin", "logoUrlNav"]
}
```

后端改造重点：

| 文件 | 改造内容 |
|------|----------|
| `backend/src/main/java/com/pricemanagement/service/StyleConfigService.java` | 创建快照时排除 Logo base64/图片大字段 |
| `backend/src/main/java/com/pricemanagement/service/StyleVersionService.java` | 统一快照结构、差异摘要、排除字段说明 |
| `backend/src/main/java/com/pricemanagement/dto/*Style*DTO.java` | 确认 Logo 引用字段、快照字段与前端类型一致 |
| `backend/src/main/java/com/pricemanagement/entity/*Style*.java` | 确认 Entity 字段与数据库表一致 |
| `backend/src/main/resources/init.sql` | 如新增 Logo 引用/hash/storageKey 字段，必须同步表结构 |

Logo 回滚策略必须在开发前明确二选一：

| 策略 | 行为 | 适用情况 |
|------|------|----------|
| Logo 不随版本回滚 | 回滚只恢复颜色、字体、布局等配置，Logo 保持当前值 | Logo 被视为品牌资产，不希望历史版本覆盖 |
| Logo 随版本回滚 | 快照保存 Logo 引用/hash，回滚时恢复对应资源引用 | Logo 是样式版本的一部分，需要完整审计 |

无论选择哪种策略，都必须在版本对比区明确展示，避免用户误判。

操作日志要求：

- 保存样式配置记录 `UPDATE` 操作日志
- 回滚版本记录 `UPDATE` 操作日志
- Logo 上传/替换记录 `UPDATE` 操作日志
- 保存失败或回滚失败应记录失败状态与错误原因

### 4. 版本对比升级为字段级 diff

当前 `VersionComparePreview.vue` 只显示版本号和 `changeSummary`，不足以支撑回滚决策。

新增能力：

- 解析 `targetVersion.configSnapshot`
- 与 `editingConfig` 逐项比较
- 只展示发生变化的字段
- 展示回滚后 mini preview

建议 diff 字段：

| 字段 | 展示方式 |
|------|----------|
| `systemName` | 当前名称 → 目标名称 |
| `logoUrl` | Logo 有变化 / 无变化 |
| `priceRiseColor` | 色块 + 色值对比 |
| `priceFallColor` | 色块 + 色值对比 |
| `priceFlatColor` | 色块 + 色值对比 |
| `chartColors` | 色板对比 |
| `headingFont` | 字体名对比 |
| `bodyFont` | 字体名对比 |
| `numberFont` | 字体名对比 |
| `fontSizePreset` | 中文方案名对比 |
| `activeLayoutStyle` | 中文布局名对比 |
| `activeColorScheme` | 中文色彩方案名对比 |

建议实现：

```typescript
const targetConfig = computed<StyleConfig | null>(() => {
  if (!props.targetVersion?.configSnapshot) return null
  try {
    return JSON.parse(props.targetVersion.configSnapshot)
  } catch {
    return null
  }
})

const diffItems = computed(() => {
  if (!targetConfig.value) return []
  return buildStyleConfigDiff(props.editingConfig, targetConfig.value)
})
```

新增工具：

```text
frontend/src/utils/styleConfigDiff.ts
```

验收：

- 选择历史版本后，右侧显示具体变化项
- 无变化字段不展示
- `configSnapshot` 解析失败时显示友好错误
- 回滚前能看到目标版本的导航/价格/表格 mini preview

### 5. 首页预览接入编辑态同源数据

当前 `HomePreview.vue` 读取 `home_layout`、`home_widget` 字典，属于“数据来源同源”，但不是“编辑态同源”。

目标：

- 中央 `HomeExperiencePanel` 调整组件显隐、排序、列数时，右侧预览立即同步
- 不依赖重新 mounted 或重新加载字典

建议：

1. 抽出统一 composable：

```text
frontend/src/composables/useHomePreviewState.ts
```

2. `HomeExperiencePanel.vue` 与 `HomePreview.vue` 共用同一状态：

```typescript
export function useHomePreviewState() {
  return {
    layoutConfig,
    widgets,
    selectedViewport,
    enabledWidgets,
    updateLayoutConfig,
    updateWidgetEnabled,
    updateWidgetOrder
  }
}
```

3. 若当前已有 `useHomeConfig.ts`，优先复用或扩展，不重复创建状态源。

验收：

- 关闭首页组件，右侧预览立即减少组件
- 调整组件排序，右侧顺序立即变化
- 调整 PC/移动列数，缩略图布局即时变化
- 进入页面后配置从服务端/字典正确回显

### 6. 分类视觉预览接入编辑态同源数据

当前 `CategoryPreview.vue` 自己读取 `category_visual_config` 并维护选中分类。它比静态 mock 好，但可能和中央 `CategoryVisualPanel` 的当前编辑分类不同步。

目标：

- 中央选中哪个分类，右侧预览哪个分类
- 中央改颜色、图标、光晕，右侧立即同步

建议：

1. 抽出统一 composable：

```text
frontend/src/composables/useCategoryVisualPreviewState.ts
```

2. 共享状态：

```typescript
export function useCategoryVisualPreviewState() {
  return {
    categories,
    selectedCategory,
    selectedVisualConfig,
    selectCategory,
    patchVisualConfig
  }
}
```

3. `CategoryVisualPanel.vue` 的表单编辑与 `CategoryPreview.vue` 使用同一个 `selectedVisualConfig`。

验收：

- 中央切换分类，右侧分类同步
- 改主色/辅色/文本色/边框色/光晕色，右侧即时变化
- 改图标，右侧即时变化
- 深色背景预览同步深色配置

### 7. 布局 token resolver 成为唯一来源

当前：

- `layoutPreviewResolver.ts` 有一套规则
- `useStyleSettingsWorkbench.applyLayoutVariables()` 也有一套规则

目标：

- 布局 token 只维护一处
- 预览和真实 CSS 变量使用同一 resolver

建议：

1. 将 `layoutPreviewResolver.ts` 改名或扩展为：

```text
frontend/src/utils/layoutTokenResolver.ts
```

2. 输出平台无关 token：

```typescript
export interface LayoutTokens {
  navPosition: 'top' | 'left' | 'top-minimal'
  navPositionLabel: string
  navBg: string
  navText: string
  pageBg: string
  cardBg: string
  cardRadius: string
  cardShadow: string
  cardShadowLabel: string
}
```

3. `useStyleSettingsWorkbench.applyLayoutVariables()` 调用 resolver：

```typescript
const tokens = resolveLayoutTokens(config)
root.style.setProperty('--app-nav-position', tokens.navPosition)
root.style.setProperty('--app-nav-bg', tokens.navBg)
root.style.setProperty('--app-nav-text', tokens.navText)
root.style.setProperty('--app-page-bg', tokens.pageBg)
root.style.setProperty('--app-card-bg', tokens.cardBg)
root.style.setProperty('--app-card-shadow', tokens.cardShadow)
root.style.setProperty('--app-card-radius', tokens.cardRadius)
```

验收：

- 新增布局方案时，只需改 resolver
- 真实页面和预览中的布局表现一致
- 不再出现两处 switch 漂移

### 8. 多视口体验验收与视觉打磨

根据 `multi-platform-adaptation.md`，Style Settings 属于管理型页面，小程序端建议隐藏；但 H5 端必须保证 PC 和移动浏览器可用。

每个预览 section 只回答一个核心判断问题：

| 配置域 | 核心判断 |
|--------|----------|
| 总览 | 当前系统整体视觉是否可信 |
| 品牌 | 系统识别是否清楚 |
| 色彩 | 涨跌/持平是否一眼可辨 |
| 排版 | 中文、数字、表格是否耐读 |
| 布局 | 空间密度和导航位置是否合适 |
| 首页体验 | 首屏信息是否有效 |
| 分类视觉 | 分类辨识度是否稳定 |
| 版本恢复 | 回滚差异是否明确 |

视觉原则：

- 少做“配置说明”，多展示“结果片段”
- 右侧预览不堆叠过多卡片
- 每个 section 聚焦 1-3 个关键判断点
- 长中文、长系统名、大金额、负数百分比必须不溢出
- PC 和移动缩略视口都要覆盖关键场景
- 空状态、错误状态、加载状态要可理解，不让用户误以为配置丢失

验收视口：

| 视口 | 目标 |
|------|------|
| 1366 x 768 | 三栏完整，无横向滚动 |
| 1440 x 900 | 中央配置区与右侧预览比例舒适 |
| 1920 x 1080 | 页面最大宽度合理，不发散 |
| 390 x 844 | 移动端横向导航可用，预览抽屉不溢出 |
| 430 x 932 | 长文本、按钮、抽屉内容不遮挡 |

视觉检查：

- 右侧预览每个 section 聚焦 1-3 个判断点
- 不堆叠过多卡片
- 长系统名、长产品名、大金额数字不溢出
- 预览抽屉关闭按钮易点，底部悬浮按钮不挡主要操作

---

## 关键参考文件

| 文件 | 用途 |
|------|------|
| `docs/plan/multi-platform-adaptation.md` | 多端适配目标与 Style Settings 定位 |
| `docs/plan/style-settings-preview-95-improvement-feature.md` | 9.5 分预览面板改进目标 |
| `docs/plan/样式设置预览面板动态匹配方案.md` | 统一保存、草稿预览、版本快照治理来源，已合并到本文 |
| `docs/dev/项目设计规范.md` | 字典边界、动态适配、布局规范 |
| `docs/dev/项目设计文档.md` | 若接口、DTO、数据库结构变化，需同步更新 |
| `docs/dev/UI设计说明.md` | 若保存/放弃、未保存离开保护、版本对比交互变化，需同步更新 |
| `frontend/src/views/Login.vue` | 当前构建错误位置 |
| `frontend/src/composables/useTheme.ts` | 登录页 Logo 配置来源 |
| `frontend/src/views/StyleSettings.vue` | Style Settings 页面装配、版本选择 |
| `frontend/src/components/style-settings/StyleSettingsShell.vue` | PC/移动布局、顶部状态栏 |
| `frontend/src/components/style-settings/StylePreviewPanel.vue` | 预览组件编排 |
| `frontend/src/components/style-settings/preview/*` | 各独立预览组件 |
| `frontend/src/utils/layoutPreviewResolver.ts` | 当前布局预览 token 解析 |
| `frontend/src/composables/useStyleSettingsWorkbench.ts` | 样式状态与 CSS 变量应用 |
| `frontend/src/constants/stylePreviewBlocks.ts` | section 与 preview block 强类型映射 |
| `frontend/src/types/theme.ts` | `StyleConfig`、`StyleVersion`、方案名称映射 |
| `backend/src/main/java/com/pricemanagement/service/StyleConfigService.java` | 样式保存、快照创建、操作日志 |
| `backend/src/main/java/com/pricemanagement/service/StyleVersionService.java` | 版本快照结构、回滚策略、差异摘要 |

---

## 实现步骤

### Phase 1：构建阻断修复（0.25 天）

- 修复 `Login.vue` 中 `themeConfig` 类型访问问题
- 如有必要，调整 `useTheme.ts` 的返回类型
- 运行 `npm run build`

### Phase 1.5：完成文档合并后的基线检查（0.25 天）

- 确认 `StylePreviewPanel.vue` 只保留编排职责
- 确认 `preview/` 目录组件清单完整
- 确认顶部状态栏仍显示中文方案名
- 确认 `activeSection` 仍由父级单源驱动

### Phase 2：统一保存与草稿预览机制（1 天）

- 在 `useStyleSettingsWorkbench.ts` 建立 `serverConfig / draftConfig / appliedConfig`
- 新增 `updateDraft()`，配置面板变化只更新草稿
- 新增 `saveConfig()`，保存成功后才持久化、创建版本、应用全局 CSS 变量
- 新增 `discardChanges()`，恢复到最近一次服务端保存状态
- `StylePreviewPanel.vue` 绑定 `draftConfig`
- `StyleSettings.vue` 或 `StyleSettingsShell.vue` 增加保存、放弃、保存状态提示
- 增加路由离开与浏览器刷新未保存保护

### Phase 3：版本快照轻量化与 Logo 治理（0.5-1 天）

- 后端创建快照时排除 Logo base64/图片大字段
- 在快照中保留 `excludedFields` 和必要的 Logo 引用/hash/变更摘要
- 明确 Logo 是否随版本回滚，并在版本对比区展示
- 保存、回滚、Logo 替换记录 `UPDATE` 操作日志
- 检查 DTO、Entity、数据库表、前端 `StyleConfig` 类型是否一致

### Phase 4：版本字段级 diff（1 天）

- 新增 `styleConfigDiff.ts`
- `VersionComparePreview.vue` 解析 `configSnapshot`
- 展示字段级差异
- 增加目标版本 mini preview

### Phase 5：首页编辑态同源（1 天）

- 抽出或复用首页配置 composable
- `HomeExperiencePanel.vue` 与 `HomePreview.vue` 共享状态
- 验证组件显隐、排序、列数即时预览

### Phase 6：分类视觉编辑态同源（1 天）

- 抽出或复用分类视觉状态 composable
- `CategoryVisualPanel.vue` 与 `CategoryPreview.vue` 共享选中分类和视觉配置
- 验证颜色、图标、光晕即时预览

### Phase 7：布局 token 单源化（0.5 天）

- 将 preview resolver 升级为通用 layout token resolver
- `applyLayoutVariables()` 复用 resolver
- 删除重复 switch 逻辑

### Phase 8：多视口视觉验收（0.5 天）

- 检查 1366/1440/1920 PC 视口
- 检查 390/430 移动端视口
- 修复文本溢出、按钮遮挡、抽屉高度问题

### Phase 9：真实业务组件复用评估（可选，0.5-1 天）

若 Phase 1-8 完成后仍要冲击接近 10 分，可评估将预览组件从 mini mock 升级为真实业务组件的轻量 variant：

- 首页预览复用首页卡片组件的 compact variant
- 分类视觉预览复用产品卡片组件的 preview variant
- 价格/表格预览复用真实 formatter 和列表单元格组件

要求：

- 不为了复用引入过重依赖
- 不在预览面板中触发真实页面的副作用请求
- 组件 variant 必须支持固定尺寸和响应式约束

### Phase 10：代码审查后必修项（1-1.5 天）

本阶段来自 2026-05-21 代码审查结论。当前前端 `npm run build` 与后端 `mvn test` 已通过，但实现仍存在若干与本文目标、`docs/dev/项目设计规范.md` 不一致的问题。若目标是达到 9.5 分，本阶段必须完成。

- 修复统一保存闭环：移除 Style Settings 内部“切换即保存”的路径。
- 明确首页体验、分类视觉是否纳入统一保存；若继续自动保存，必须在 UI 和方案中明确区分。
- 补齐样式保存、预设切换、Logo 上传、回滚失败等操作日志。
- 增加未保存离开保护。
- 将方案名称、布局名称、字号名称等编码显示名改为动态来源或统一映射治理。
- 清理明显违背布局规范的硬编码布局值。
- 修正版本迁移脚本文案，避免与轻量快照策略冲突。

---

## 代码审查后修复章节

### A. 统一保存闭环修复

当前风险：

`useStyleSettingsWorkbench.ts` 已建立 `serverConfig / draftConfig / appliedConfig`，但仍存在立即保存方法：

| 方法 | 当前问题 | 修复要求 |
|------|----------|----------|
| `applyAndPersist()` | 更新草稿后立即 `saveConfig()` | 删除或仅保留为内部兼容方法，页面不得调用 |
| `applyColorScheme()` | 调用 `/api/style/color-scheme/{schemeKey}` 立即落库 | 改为从预设配置中合并颜色到 `draftConfig` |
| `applyLayoutStyle()` | 调用 `/api/style/layout-style/{layoutKey}` 并写全局 CSS | 改为只更新 `draftConfig.activeLayoutStyle`，预览局部响应 |
| `applyFontPreset()` | 调用 `/api/style/font-preset/{presetKey}` 立即落库 | 改为把预设字号合并到 `draftConfig` |
| `resetToDefault()` | 通过 `applyAndPersist()` 立即保存 | 改为只重置草稿，等待用户点击保存 |

修复目标：

```text
配置面板点击/输入
  → updateDraft()
  → saveStatus = dirty
  → 右侧预览即时变化
  → 不调用 PUT/POST 保存接口
  → 不写入 document.documentElement

用户点击保存配置
  → saveConfig()
  → PUT /api/style/config
  → 后端生成版本快照
  → 前端 appliedConfig 更新
  → 全局 CSS 变量生效
```

验收：

- 切换色彩方案后，刷新页面但未保存时应恢复旧配置。
- 切换布局方案后，除右侧预览外，真实业务页面不应变化。
- 切换字号预设后，顶部状态显示“有未保存的更改”。
- 点击放弃修改后，色彩、布局、字号、系统名、Logo 尺寸均恢复到 `serverConfig`。

### B. 首页体验与分类视觉保存策略统一

当前风险：

- `useHomePreviewState.ts` 中 `saveLayoutConfig()`、`saveWidgetConfig()` 会直接调用 `updateDict()`。
- `CategoryVisualPanel.vue` 中颜色、预设、图标、光晕修改会直接保存。
- 用户在 Style Settings 顶部看到统一“保存配置”，但首页/分类区域已经自动落库，产品语义不一致。

必须二选一：

| 策略 | 要求 | 适用场景 |
|------|------|----------|
| 纳入统一保存 | 首页/分类改动进入草稿态，点击“保存配置”后统一持久化 | 推荐，体验最一致 |
| 保持自动保存 | 在首页/分类区域显示“此区域自动保存”，不受顶部保存按钮控制 | 短期兼容，需清楚告知用户 |

推荐策略：

- 首页体验、分类视觉作为 Style Settings 的配置域，应优先纳入统一保存。
- 建立 `homeDraftConfig`、`categoryVisualDraftConfig`，统一由工作台保存入口提交。
- 保存成功后刷新字典缓存，保证真实页面、预览、服务端一致。

验收：

- 修改首页组件顺序后，未保存刷新页面应恢复旧顺序。
- 修改分类颜色后，右侧预览即时变化，但未保存不落库。
- 点击保存后，首页/分类配置才更新到后端字典。

### C. 操作日志补齐

当前风险：

`StyleConfigController` 仅回滚接口记录了操作日志，以下数据变更缺少日志：

- `PUT /api/style/config`
- `PUT /api/style/theme/{themeKey}`
- `PUT /api/style/color-scheme/{schemeKey}`
- `PUT /api/style/layout-style/{layoutKey}`
- `PUT /api/style/font-preset/{presetKey}`
- `POST /api/style/logo`
- `POST /api/style/logo/login`
- `POST /api/style/logo/nav`

修复要求：

- 成功时记录 `OperationType.UPDATE`。
- 失败时记录错误日志，包含失败原因。
- 日志模块统一为“样式设置”。
- 日志描述避免记录 Logo base64、完整配置 JSON、敏感 token。

推荐描述：

| 操作 | 日志描述 |
|------|----------|
| 保存配置 | `保存样式配置` |
| 切换色彩方案 | `切换色彩方案: {schemeKey}` |
| 切换布局方案 | `切换布局方案: {layoutKey}` |
| 切换字号预设 | `切换字号预设: {presetKey}` |
| 上传登录页 Logo | `上传登录页 Logo` |
| 上传导航栏 Logo | `上传导航栏 Logo` |

验收：

- 保存成功后，日志管理页面可看到样式设置 UPDATE 记录。
- 保存失败、上传失败、回滚失败均有失败日志。
- 日志详情不包含 base64 图片正文。

### D. 未保存离开保护

当前风险：

Style Settings 顶部已有保存状态，但缺少路由离开、刷新、关闭页面保护。用户有未保存修改时可能误离开页面。

修复要求：

- 在 `StyleSettings.vue` 或 `StyleSettingsShell.vue` 中增加 `onBeforeRouteLeave`。
- 在 `onMounted` 注册 `beforeunload`，在 `onUnmounted` 移除监听。
- 只有 `saveStatus === 'dirty'` 时触发保护。
- 保存成功或放弃修改后不再拦截。

验收：

- 有未保存修改时切换菜单，出现确认提示。
- 有未保存修改时刷新浏览器，出现浏览器原生确认。
- 保存后再次切换页面不出现提示。

### E. 编码显示名与字典规范修复

当前风险：

当前代码中存在方案名称和字段名称硬编码映射，例如：

- `COLOR_SCHEME_NAMES`
- `LAYOUT_STYLE_NAMES`
- `FONT_PRESET_NAMES`
- `styleConfigDiff.ts` 中的 `FIELD_LABELS`

治理原则：

- 后端预设已有 `presetName` 时，前端展示必须优先使用后端返回名称。
- 字典/后端可配置的编码显示名，不在前端重复硬编码中文。
- 仅组件内部 UI 状态，如 `dirty/saving/saved/failed`，可作为局部状态文案管理。

修复要求：

- 顶部当前方案名从已加载预设列表中按 key 查 name。
- 版本 diff 的方案字段显示名从预设 name 或统一 label resolver 获取。
- `FIELD_LABELS` 若短期保留，需明确为前端固定字段标签，不用于业务编码值显示。
- 后续新增方案时，不应修改前端映射表即可正确显示。

验收：

- 后端新增一个色彩方案，前端顶部和版本 diff 可显示后端名称。
- 前端不再因为新增 layout/font preset key 而显示技术 key。

### F. 页面布局规范修复

当前风险：

Style Settings 页面存在较多硬编码布局值，例如：

- `min-height: 100vh`
- `max-width: 1600px`
- `grid-template-columns: 200px 1fr 360px`
- 多处固定 padding、gap、border-radius、颜色值

修复要求：

- 页面级布局优先使用 `page-pc`、`section`、`header-row` 等 UnoCSS shortcut 或 CSS 变量。
- 固定列宽改为 CSS 变量或响应式约束，例如 `--style-nav-width`、`--style-preview-width`。
- 色彩优先使用项目 CSS 变量，不在组件内大量写死 `#0D6E6E`、`#E5E5E5`。
- 保留必要固定尺寸时，需有明确组件语义，如按钮高度、缩略图预览尺寸。

验收：

- 1366、1440、1920 宽度下无横向滚动。
- 390、430 移动端下按钮、导航、抽屉不遮挡。
- 页面布局符合 `docs/dev/项目设计规范.md` 的页面容器要求。

### G. 版本快照与迁移脚本文案修复

当前风险：

后端代码已实现轻量快照，但 `V12__fix_style_version_config_snapshot_length.sql` 的注释仍描述“完整 StyleConfigDTO”“Logo base64 导致 JSON 过长”，容易误导后续维护。

修复要求：

- 如果保留 `LONGTEXT`，注释应说明是为了兼容历史快照和复杂配置扩展。
- 当前快照策略以 `StyleVersionService.createLightweightSnapshot()` 为准。
- 版本对比区继续明确“Logo 不随版本回滚”。

验收：

- 新快照不包含 `logoUrl`、`logoUrlLogin`、`logoUrlNav` 原始 base64。
- `assetRefs` 仅记录是否有值、类型、大小估计或 URL 引用。
- 迁移脚本文案与实际策略一致。

---

## Verification

### 构建验证

```bash
cd frontend
npm run build
```

通过标准：

- `vue-tsc` 无错误
- `vite build` 成功
- 无新增阻断性 warning

### 功能验收

| 场景 | 验收标准 |
|------|----------|
| 登录页 Logo | Logo URL 和尺寸正常读取，构建无类型错误 |
| 总览快捷跳转 | 导航、中间配置、右侧预览同步 |
| 草稿预览 | 修改配置后右侧即时变化，但不调用保存接口 |
| 保存配置 | 点击保存后才持久化配置、创建版本快照、应用全局 CSS 变量 |
| 放弃修改 | 草稿恢复到最近一次服务端保存状态，不创建版本 |
| 未保存离开 | 路由离开、刷新、关闭页面时有明确确认 |
| 保存失败 | 草稿保留，状态显示失败，可重试保存 |
| Logo 快照 | 版本快照不包含 Logo base64/图片大字段，保留排除说明或引用信息 |
| 版本选择 | 右侧显示目标版本字段级差异 |
| 版本回滚 | 回滚成功后重新加载配置，UI 与服务端一致 |
| 首页配置 | 开关、排序、列数变化即时反映到预览 |
| 分类视觉 | 当前分类、颜色、图标、光晕即时反映到预览 |
| 布局切换 | 真实页面 CSS 变量与预览 token 同步 |

### 一致性验收

| 检查项 | 验收标准 |
|--------|----------|
| 前后端接口 | `/api` 路径、请求字段、响应字段与前端 API 调用一致 |
| TypeScript 类型 | `StyleConfig`、`StyleVersion` 与后端 DTO 字段一致 |
| Entity/数据库 | Entity `@Column`、`@Table` 与数据库表结构一致 |
| init.sql | 如新增 Logo 引用/hash/storageKey 等字段，初始化 SQL 同步更新 |
| 字典规范 | 业务编码显示名仍走 `useDict`，组件内部临时 UI 状态不误入业务字典 |
| 权限控制 | 保存、回滚、Logo 替换仅授权角色可执行 |
| 操作日志 | 保存、回滚、Logo 替换记录 `UPDATE`，失败场景记录错误原因 |
| 文档同步 | 涉及 API、数据库、UI 行为变化时同步更新设计文档和 UI 说明 |

### 多视口验收

| 视口 | 验收标准 |
|------|----------|
| 1366 x 768 | 三栏无横向滚动，右侧预览可滚动 |
| 1440 x 900 | 操作区与预览区比例舒适 |
| 1920 x 1080 | 页面不发散，最大宽度合理 |
| 390 x 844 | 移动端预览抽屉可用，按钮不遮挡 |
| 430 x 932 | 长文本不溢出，抽屉内容可滚动 |

### 评分目标

| 维度 | 当前目标 |
|------|----------|
| 工程交付 | build 必须通过 |
| 功能完整度 | 预览按 section 动态切换，版本可对比，首页/分类同源 |
| 预览可信度 | 首页/分类/布局/版本均与真实配置同源 |
| 回滚安全感 | 回滚前可看到字段级差异 |
| 产品体验 | 用户修改后能立即预览，保存后才全局生效，回滚前能判断影响 |
| 视觉专业度 | 克制、清晰、信息密度合理，无配置表单感 |
| 多端体验 | H5 PC/移动可用，小程序端继续隐藏管理功能 |
| 可维护性 | preview 子组件职责清晰，resolver 单源，保存状态模型单源 |
| 规范符合度 | 不误用字典，业务编码显示仍走 `useDict` |
| 验证质量 | build 通过，多视口检查通过 |

达成以上标准后，Style Settings 可评为 **9.5/10**。

---

## 注意事项

1. 本方案不要求将 Style Settings 迁移到 `frontend-uniapp`，因为 `multi-platform-adaptation.md` 已明确 Style Settings 为 P3 管理功能，建议隐藏。
2. 首页和分类预览若短期无法完全接入编辑态，也必须至少暴露清晰的 TODO 和降级说明，避免误以为已完全同源。
3. 版本 diff 必须容错 `configSnapshot` 为空、格式错误、字段缺失的情况。
4. 涉及 UI/交互变更后，需按 AGENTS.md 更新对应设计文档或归档完成记录。
5. 本文已合并 `style-settings-preview-95-improvement-feature.md` 中仍有价值的目标项；后续如两份文档冲突，以本文为准。
6. 本文已合并 `样式设置预览面板动态匹配方案.md` 的统一保存、草稿预览、版本快照治理内容；该文档后续仅作为历史来源，不再单独执行。
7. 若实现过程中需要新增或调整 Logo 相关字段，必须同步检查前端类型、后端 DTO、Entity、数据库表、`init.sql` 与数据字典说明。
8. 任何草稿态编辑都不得提前写入全局 CSS 变量；全局生效只能发生在保存成功之后。

---

## 后续加分项

若要接近 10 分，可进一步将预览直接复用真实业务组件的轻量 variant，而不是长期维护手写 mini mock：

- 首页预览复用首页卡片组件的 compact variant
- 分类视觉预览复用产品卡片组件的 preview variant
- 价格/表格预览复用真实 formatter 和列表单元格组件

收益：

- 最大限度减少真实页面和预览之间的视觉漂移
- 降低重复样式维护成本
- 让 Style Settings 成为真正可信的设计控制台

边界：

- 复用组件必须支持 preview 模式
- preview 模式不得触发真实编辑、删除、提交等副作用
- preview 模式必须有固定尺寸和长文本防溢出策略
