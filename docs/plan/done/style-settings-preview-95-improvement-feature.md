# Style Settings 预览面板 9.5 分改进方案

**创建日期：2026-05-21**  
**适用范围：** `frontend/src/views/StyleSettings.vue`、`frontend/src/components/style-settings/*`、`frontend/src/components/style-settings/preview/*`、`frontend/src/constants/stylePreviewBlocks.ts`、`frontend/src/composables/useStyleSettingsWorkbench.ts`  
**目标：** 将当前 Style Settings 右侧预览从“按菜单切换的静态示意图”升级为“真实配置同源、可决策、可验证的专业设计控制台”，达到 9.5 分以上体验标准。

---

## Context

当前 Style Settings 页面已经完成基础开发：

- PC 三栏工作台和移动端预览抽屉已具备
- `activeSection` 已传入 `StylePreviewPanel`
- `stylePreviewBlocks.ts` 已使用强类型配置定义 section 与 preview block 的关系
- 构建验证通过：`frontend/npm run build`

但目前仍存在影响专业度和可信度的问题：

| 问题 | 当前表现 | 影响 |
|------|----------|------|
| 导航状态可能不同步 | 总览快捷跳转只改父级 `activeSection`，Shell 内部 `currentSection` 可能不同步 | 左侧导航、中间配置区、右侧预览不一致 |
| 版本预览仍是静态占位 | 右侧写死版本号和“选择历史版本”提示 | 用户回滚前无法判断影响 |
| 首页预览未同源 | 预览写死摘要、指标、趋势、列表 | 不能反映 `home_layout`、`home_widget` 的真实变化 |
| 分类预览未同源 | 预览写死“电铜”和图标 | 不能反映当前分类视觉配置 |
| 布局预览存在硬编码 | 圆角、阴影、背景色等部分写死 | 与真实 CSS 变量存在漂移风险 |
| 顶部状态显示 raw key | 可能显示 `scheme_xxx`、`standard` 等技术 key | 产品观感不专业 |
| 预览面板单文件过大 | `StylePreviewPanel.vue` 已接近 29KB | 后续维护和视觉打磨成本高 |

9.5 分版本的一句话标准：

**用户在 Style Settings 里改什么，右侧就以真实业务片段展示什么；用户回滚前能判断差异；刷新、切换、移动端查看都一致可信。**

---

## 实现方案

### 1. 修复 section 单源状态

`StyleSettingsShell.vue` 不应维护与父级重复的真实 section 状态。

建议：

- 以 `props.activeSection` 作为唯一选中来源
- 点击导航只 emit `update:activeSection`
- 如保留内部 ref，仅作为临时 UI 状态，必须 watch props 同步

验收：

- 总览快捷入口跳转后，左侧导航、中间配置区、右侧预览同步
- 移动端横向导航高亮同步

### 2. 拆分预览组件

新增目录：

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

`StylePreviewPanel.vue` 只负责：

- 接收 `editingConfig`、`activeSection`
- 根据 `PREVIEW_BLOCKS_BY_SECTION` 获取区块
- 渲染对应 preview component
- 传递同源预览数据

目标：

- `StylePreviewPanel.vue` 控制在 150 行以内
- 复杂区块独立维护
- 视觉 frame、标题、说明、空状态统一

### 3. 首页预览接入真实配置

`HomePreview.vue` 必须读取与 `HomeExperiencePanel.vue` 同源的数据。

应展示：

| 配置 | 预览表现 |
|------|----------|
| 首页组件启用状态 | 组件显隐即时变化 |
| 首页组件排序 | 缩略图顺序即时变化 |
| PC 卡片列数 | PC 视口缩略图列数变化 |
| 移动端卡片列数 | 移动视口缩略图列数变化 |
| 重点产品数量 | 产品卡片数量摘要变化 |
| 趋势图/预警区开关 | 对应区域显隐变化 |

预览建议：

```text
首页体验预览
[PC] [移动]

PC:
┌摘要┐ ┌指标┐ ┌趋势┐
└产品列表 6 个────────┘

移动:
┌摘要┐
┌指标┐
┌列表┐
```

### 4. 分类视觉预览接入真实配置

`CategoryPreview.vue` 必须读取当前选中分类和对应视觉配置。

应展示：

| 配置 | 预览表现 |
|------|----------|
| 分类名称 | 卡片标题同步 |
| 图标 | 图标同步 |
| 主色/辅色 | 卡片主视觉同步 |
| 文本色 | 标题和数字颜色同步 |
| 边框色 | 卡片边框同步 |
| 光晕色 | 卡片背景光晕同步 |
| 深色模式配置 | 深色背景预览同步 |

预览建议：

```text
分类视觉预览

浅色背景
┌ [图标] 黑色金属
│ 电铜  ¥68,500  +2.5%
│ 主色 / 边框 / 光晕
└

深色背景
┌ [图标] 黑色金属
│ 电铜  ¥68,500  +2.5%
└
```

### 5. 实现真正的版本对比预览

版本恢复区应支持“选择版本 → 右侧预览差异 → 再确认回滚”。

新增状态：

```typescript
const selectedVersion = ref<StyleVersion | null>(null)
```

版本列表行为：

- 点击版本行或“查看”按钮：设置 `selectedVersion`
- 点击“回滚”：二次确认后执行回滚

`VersionComparePreview.vue` props：

```typescript
interface Props {
  currentConfig: StyleConfig
  targetVersion: StyleVersion | null
}
```

差异维度：

- 系统名称
- Logo 是否变化
- 涨价/跌价/持平色
- 图表色板
- 标题/正文/数字字体
- 字号预设
- 布局方案
- 首页配置摘要
- 分类视觉摘要

预览建议：

```text
版本对比

当前版本       →       目标版本
红涨绿跌               蓝涨橙跌
标准字号               紧凑字号
顶部导航               左侧导航

回滚后效果
[导航 + 价格卡片 + 表格 mini preview]
```

### 6. 预览与真实 CSS 变量同源

避免 `StylePreviewPanel.vue` 内部写死布局颜色、圆角和阴影。

建议抽出 layout resolver：

```typescript
export function resolveLayoutPreviewTokens(config: StyleConfig) {
  return {
    navPosition: '',
    navBg: '',
    navText: '',
    pageBg: '',
    cardBg: '',
    cardRadius: '',
    cardShadow: ''
  }
}
```

要求：

- `useStyleSettingsWorkbench.applyLayoutVariables()` 与预览 resolver 共享同一套规则
- 卡片圆角、阴影、导航背景、页面背景全部来自 config/resolver/CSS 变量
- 不在预览中写死 `12px`、固定背景色或固定阴影文案

### 7. 顶部状态栏显示中文方案名

`StyleSettingsShell.vue` 顶部状态应使用中文名称：

- `COLOR_SCHEME_NAMES`
- `LAYOUT_STYLE_NAMES`
- `FONT_PRESET_NAMES`

目标展示：

```text
青绿经典 / 标准字号 / 经典顶部导航
```

避免展示：

```text
scheme_teal_classic / standard
```

### 8. 视觉专业度打磨

每个预览 section 只回答一个核心判断问题：

| 配置域 | 核心判断 |
|--------|----------|
| 品牌 | 系统识别是否清楚 |
| 色彩 | 涨跌/持平是否一眼可辨 |
| 排版 | 中文、数字、表格是否耐读 |
| 布局 | 空间密度和导航位置是否合适 |
| 首页体验 | 首屏信息是否有效 |
| 分类视觉 | 分类辨识度是否稳定 |
| 版本恢复 | 回滚差异是否明确 |

视觉要求：

- 右侧预览不堆叠过多卡片
- 少做“配置说明”，多展示“结果片段”
- 长中文、长系统名、大金额、负数百分比必须不溢出
- PC 和移动缩略视口都要覆盖关键场景

---

## 关键参考文件

| 文件 | 用途 |
|------|------|
| `docs/dev/项目设计规范.md` | 字典边界、动态适配、UnoCSS 与 scoped CSS 分工 |
| `docs/plan/样式设置预览面板动态匹配方案.md` | 当前预览面板动态匹配方案 |
| `docs/plan/样式设置页面响应式配置工作台实施方案.md` | Style Settings 工作台整体方案 |
| `frontend/src/views/StyleSettings.vue` | 页面装配、版本列表、预览 slot |
| `frontend/src/components/style-settings/StyleSettingsShell.vue` | 三栏布局、移动端抽屉、顶部状态栏 |
| `frontend/src/components/style-settings/StylePreviewPanel.vue` | 当前预览面板实现，需拆分 |
| `frontend/src/constants/stylePreviewBlocks.ts` | section 与 preview block 强类型映射 |
| `frontend/src/components/style-settings/HomeExperiencePanel.vue` | 首页配置数据来源参考 |
| `frontend/src/components/style-settings/CategoryVisualPanel.vue` | 分类视觉配置数据来源参考 |
| `frontend/src/composables/useStyleSettingsWorkbench.ts` | 样式状态、CSS 变量应用、保存回滚 |
| `frontend/src/types/theme.ts` | `StyleConfig`、`StyleVersion`、方案名称映射 |

---

## 实现步骤

### Phase 1：状态同步修复（0.5 天）

- 改造 `StyleSettingsShell.vue`，消除或同步内部 `currentSection`
- 从总览快捷入口跳转验证导航高亮
- 移动端导航同步验证

### Phase 2：预览组件拆分（0.5-1 天）

- 新建 `preview/` 目录
- 新建 `PreviewFrame.vue`
- 拆出系统总览、品牌、色彩、排版、布局预览组件
- 保持现有功能不回退

### Phase 3：首页预览同源（1 天）

- 梳理 `HomeExperiencePanel.vue` 的配置来源
- 抽出或复用首页配置 composable
- `HomePreview.vue` 支持 PC/移动视口切换
- 根据真实组件显隐和排序渲染缩略图

### Phase 4：分类视觉预览同源（1 天）

- 梳理 `CategoryVisualPanel.vue` 的选中分类和配置来源
- `CategoryPreview.vue` 渲染当前分类
- 支持浅色/深色背景预览
- 主色、辅色、边框、光晕、图标即时同步

### Phase 5：版本对比预览（1 天）

- `StyleSettings.vue` 增加 `selectedVersion`
- 版本列表支持选择查看
- 新建 `VersionComparePreview.vue`
- 解析 `configSnapshot` 并计算差异
- 回滚前展示差异和目标效果

### Phase 6：预览 token 同源（0.5 天）

- 抽出布局 preview token resolver
- `applyLayoutVariables()` 与预览使用同一规则
- 移除预览中的圆角、阴影、背景硬编码

### Phase 7：顶部状态中文化（0.25 天）

- 使用 `COLOR_SCHEME_NAMES`、`LAYOUT_STYLE_NAMES`、`FONT_PRESET_NAMES`
- 找不到映射时 fallback 到 key
- 开发环境输出 warning

### Phase 8：视觉验收打磨（1 天）

- 检查 PC 三栏信息密度
- 检查移动端抽屉体验
- 增加长文本压力样例
- 优化右侧预览层级和空状态

---

## Verification

### 构建验证

```bash
cd frontend
npm run build
```

### 核心交互验收

| 场景 | 验收标准 |
|------|----------|
| 总览快捷跳转 | 左侧导航、中间配置区、右侧预览同步 |
| 品牌修改 | 系统名称、Logo、Logo 尺寸在预览中同步 |
| 色彩修改 | 涨跌色、图表色板、状态徽章同步 |
| 排版修改 | 字体、字号、表格长文本预览同步 |
| 布局切换 | PC/移动缩略视口、导航位置、卡片变量同步 |
| 首页配置修改 | 组件显隐、排序、列数、重点产品数量同步 |
| 分类视觉修改 | 当前分类颜色、图标、边框、光晕同步 |
| 选择历史版本 | 右侧显示当前/目标差异和回滚后效果 |
| 回滚版本 | 回滚成功后重新加载配置，UI 与服务端一致 |

### 多视口视觉验收

| 视口 | 验收标准 |
|------|----------|
| 1366 x 768 | 三栏可用，无横向滚动，右侧预览不遮挡 |
| 1440 x 900 | 中央配置区和右侧预览比例舒适 |
| 1920 x 1080 | 页面不发散，最大宽度合理 |
| 390 x 844 | 移动端导航可横滑，预览抽屉可操作 |
| 430 x 932 | 按钮、长文本、预览卡片不溢出 |

### 9.5 分判定标准

| 维度 | 标准 |
|------|------|
| 功能完整度 | 预览按 section 动态切换，版本可对比，首页/分类同源 |
| 可信度 | 预览与真实配置共用数据源和 resolver |
| 产品体验 | 用户回滚前能判断影响，修改后能立即看到结果 |
| 视觉专业度 | 克制、清晰、信息密度合理，无配置表单感 |
| 响应式 | PC 三栏稳定，移动端抽屉可用 |
| 可维护性 | 预览组件拆分清楚，主文件不臃肿 |
| 规范符合度 | 不误用字典，业务编码显示仍走 `useDict` |
| 验证质量 | build 通过，多视口检查通过 |

---

## 后续加分项

若要接近 10 分，可进一步将预览直接复用真实业务组件的轻量 variant，而不是手写 mini mock：

- 首页预览复用首页卡片组件的 compact variant
- 分类视觉预览复用产品卡片组件的 preview variant
- 价格/表格预览复用真实 formatter 和列表单元格组件

这样可以最大限度减少真实页面和预览之间的视觉漂移。

