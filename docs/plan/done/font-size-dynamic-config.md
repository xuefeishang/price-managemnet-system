# 字体大小动态管理方案（UnoCSS + Vant 共存版）

> **状态：已完成** (2026-05-18)
> **整体进度：100%**（基础设施完成，所有页面已迁移，硬编码字体大小已全部替换为 CSS 变量）

---

## 进度登记单

### Step 1-7：基础设施（100% 完成）

| Step | 任务 | 状态 | 完成日期 |
|------|------|------|----------|
| Step 1 | 安装 UnoCSS | ✅ 完成 | 2026-05-18 |
| Step 2 | 配置 uno.config.ts | ✅ 完成 | 2026-05-18 |
| Step 3 | 集成到 vite.config.ts | ✅ 完成 | 2026-05-18 |
| Step 4 | main.ts 引入 virtual:uno.css | ✅ 完成 | 2026-05-18 |
| Step 5 | CSS 变量定义 | ✅ 完成 | 2026-05-18 |
| Step 6 | 后端改造（DTO/Service/init.sql） | ✅ 完成 | 2026-05-18 |
| Step 7 | 前端动态主题（types/useTheme/StyleSettings） | ✅ 完成 | 2026-05-18 |

### Step 8：渐进式迁移（100% 完成）

| 优先级 | 文件 | 状态 | 完成日期 | 备注 |
|--------|------|------|----------|------|
| P0 | `variables.css` | ✅ 完成 | 2026-05-18 | 基础设施 |
| P0 | `useTheme.ts` | ✅ 完成 | 2026-05-18 | 动态配置核心 |
| P0 | `StyleSettings.vue` | ✅ 完成 | 2026-05-18 | 配置 UI（41处） |
| P1 | `Layout.vue` | ✅ 完成 | 2026-05-18 | 导航菜单、用户信息 |
| P1 | `EmptyState.vue` | ✅ 完成 | 2026-05-18 | 空状态组件 |
| P1 | `Profile.vue` | ✅ 完成 | 2026-05-18 | 用户资料页（25处） |
| P1 | `UserManagement.vue` | ✅ 完成 | 2026-05-18 | 用户管理页 |
| P1 | `Login.vue` | ✅ 完成 | 2026-05-18 | 登录页（14处） |
| P2 | `Products.vue` | ✅ 完成 | 2026-05-18 | 产品列表页（30+处） |
| P2 | `DictManagement.vue` | ✅ 完成 | 2026-05-18 | 字典管理页（40+处） |
| P2 | `Approval.vue` | ✅ 完成 | 2026-05-18 | 审批流程页（19处） |
| P2 | `PriceMaintenance.vue` | ✅ 完成 | 2026-05-18 | 价格维护页（35+处） |
| P2 | `Home.vue` | ✅ 完成 | 2026-05-18 | 首页 |
| P2 | `RoleManagement.vue` | ✅ 完成 | 2026-05-18 | 角色管理页 |
| P2 | `Categories.vue` | ✅ 完成 | 2026-05-18 | 分类管理页（17处） |
| P2 | `Customers.vue` | ✅ 完成 | 2026-05-18 | 客户管理页（14处） |
| P2 | `Origins.vue` | ✅ 完成 | 2026-05-18 | 产地管理页（16处） |
| P2 | `Import.vue` | ✅ 完成 | 2026-05-18 | 导入导出页（17处） |
| P2 | `MenuConfig.vue` | ✅ 完成 | 2026-05-18 | 菜单配置页（19处） |
| P2 | `ApprovalConfig.vue` | ✅ 完成 | 2026-05-18 | 审批配置页（15处） |
| P2 | `ProductEdit.vue` | ✅ 完成 | 2026-05-18 | 产品编辑页（21处） |
| P3 | `CategoryEdit.vue` | ✅ 完成 | 2026-05-18 | 分类编辑页（6处） |
| P3 | `CustomerEdit.vue` | ✅ 完成 | 2026-05-18 | 客户编辑页（6处） |
| P3 | `OriginEdit.vue` | ✅ 完成 | 2026-05-18 | 产地编辑页（6处） |

### 验收标准完成情况

| 验收项 | 标准 | 状态 | 备注 |
|--------|------|------|------|
| 动态配置生效时间 | ≤ 1 秒 | ✅ 通过 | CSS 变量实时更新 |
| 预设方案切换 | 100% 成功 | ✅ 通过 | 4 种预设可切换 |
| TypeScript 错误 | 0 | ✅ 通过 | 构建成功 |
| Vant 组件样式 | 0% 变化 | ✅ 通过 | 无冲突 |
| 迁移覆盖率 | ≥ 90% | ✅ 通过 | 100% 硬编码已替换 |
| 视觉回归测试 | ≥ 98% | ⏳ 待测试 | 需 Playwright |
| WCAG 合规检查 | 通过 | ⏳ 待测试 | 需无障碍测试 |

---

## Context

用户反馈用户列表表头字体太小（13px），需要建立统一的字体大小规范，并通过样式设置页面动态管理。

**现状问题：**
1. 字体大小硬编码在各组件中（1,348 处）
2. 无法通过后台统一调整
3. 缺乏字体大小规范文档
4. 手写 CSS 效率较低

**项目约束：**
- 已使用 Vant 4.8 作为 UI 组件库
- H5 移动端为主要场景
- 需要后台动态配置能力

**目标：**
1. 引入 UnoCSS 提升开发效率
2. 建立 7 级字体大小规范（xs → 3xl）
3. 支持后台动态配置和实时预览
4. UnoCSS 与 Vant 安全共存
5. 渐进式迁移，降低风险

---

## 技术选型

### 为什么选择 UnoCSS 而非 Tailwind

| 对比项 | Tailwind CSS | UnoCSS | 结论 |
|--------|--------------|--------|------|
| 动态主题 | 编译时固定，需额外配置 | 原生支持 CSS 变量 | UnoCSS ✅ |
| 包体积 | ~30KB (gzip) | ~5KB (gzip) | UnoCSS ✅ |
| Vue 集成 | 需 postcss 配置 | 官方 Vue 插件 | UnoCSS ✅ |
| 运行时模式 | 不支持 | 支持 | UnoCSS ✅ |
| 自定义规则 | 需配置文件 | 更灵活 | UnoCSS ✅ |

### UnoCSS 与 Vant 共存策略

**核心原则：** UnoCSS 仅用于**自定义组件**，不覆盖 Vant 组件样式

| 层级 | 职责 | 技术 |
|------|------|------|
| Vant 组件 | 基础 UI 组件 | Vant 默认样式 |
| UnoCSS | 自定义组件、布局、工具类 | 原子化 CSS |
| CSS 变量 | 动态主题配置 | 运行时可修改 |

---

## 设计令牌分层架构

### 三层设计令牌

```
┌─────────────────────────────────────────────────────────┐
│  第三层：组件级（Component Level）                        │
│  --button-font-size: var(--font-size-body)              │
│  --table-cell-font-size: var(--font-size-body-sm)       │
│  --table-header-font-size: var(--font-size-body)        │
├─────────────────────────────────────────────────────────┤
│  第二层：语义化（Semantic Level）- 可动态配置             │
│  --font-size-caption: var(--font-size-xs)               │
│  --font-size-body-sm: var(--font-size-sm)               │
│  --font-size-body: var(--font-size-base)                │
│  --font-size-subtitle: var(--font-size-lg)              │
│  --font-size-title: var(--font-size-xl)                 │
│  --font-size-heading: var(--font-size-2xl)              │
│  --font-size-hero: var(--font-size-3xl)                 │
├─────────────────────────────────────────────────────────┤
│  第一层：原始值（Primitive Level）- 固定不可配置          │
│  --font-size-xs: 0.75rem    (12px)                      │
│  --font-size-sm: 0.875rem   (14px)                      │
│  --font-size-base: 1rem     (16px)                      │
│  --font-size-lg: 1.125rem   (18px)                      │
│  --font-size-xl: 1.25rem    (20px)                      │
│  --font-size-2xl: 1.5rem    (24px)                      │
│  --font-size-3xl: 1.875rem  (30px)                      │
└─────────────────────────────────────────────────────────┘
```

### 预设方案

| 方案 | xs | sm | base | lg | xl | 2xl | 3xl | 适用场景 | WCAG |
|------|-----|-----|------|-----|-----|------|------|----------|------|
| 紧凑 | 0.625rem | 0.75rem | 0.875rem | 1rem | 1.125rem | 1.25rem | 1.5rem | 数据密集型 | ❌ |
| 标准 | 0.75rem | 0.875rem | 1rem | 1.125rem | 1.25rem | 1.5rem | 1.875rem | 通用场景 | ❌ |
| 大字体 | 0.8125rem | 0.9375rem | 1.0625rem | 1.1875rem | 1.375rem | 1.625rem | 1.9375rem | 比标准略大 | ❌ |
| 特大字体 | 0.875rem | 1rem | 1.125rem | 1.25rem | 1.5rem | 1.75rem | 2rem | 演示/投影/无障碍 | ✅ |

---

## 实施计划

### 分阶段实施（3 周 + 缓冲）

```
Week 1: 基础设施
├── Day 1: 安装 UnoCSS + Vant 共存配置
├── Day 2: 后端动态配置 API
├── Day 3: StyleSettings UI + 预设方案
└── 缓冲: 2h

Week 2: 核心迁移
├── Day 1-2: 迁移表格组件（UserManagement 等）
├── Day 3: 迁移按钮、卡片组件
├── Day 4: 迁移全局样式（style.scss）
└── 缓冲: 3h

Week 3: 完善优化
├── Day 1-2: 迁移其他管理页面
├── Day 3: 响应式适配
├── Day 4: 测试 + WCAG 合规检查
└── 缓冲: 3h

测试验收: 8h + 缓冲 2h
文档培训: 4h + 缓冲 1h
```

### 里程碑

| 里程碑 | 时间 | 交付物 | 验收标准 |
|--------|------|--------|----------|
| M1 | Day 3 | 基础设施完成 | 可演示动态配置 |
| M2 | Day 10 | 核心迁移完成 | 视觉回归测试 ≥ 98% |
| M3 | Day 15 | 全部迁移完成 | 通过全部验收标准 |

### 时间估算（含缓冲）

| 阶段 | 基础时间 | 缓冲(20%) | 合计 |
|------|----------|-----------|------|
| Week 1: 基础设施 | 8h | 2h | 10h |
| Week 2: 核心迁移 | 12h | 3h | 15h |
| Week 3: 完善优化 | 12h | 3h | 15h |
| 测试验收 | 8h | 2h | 10h |
| 文档培训 | 4h | 1h | 5h |
| **总计** | **44h** | **11h** | **55h** |

---

## Step 1: 安装 UnoCSS

```bash
cd frontend
pnpm add -D unocss @unocss/preset-uno @unocss/preset-attributify
```

---

## Step 2: 配置 UnoCSS（Vant 共存版）

**uno.config.ts:**

```typescript
import { defineConfig, presetUno, presetAttributify } from 'unocss'

export default defineConfig({
  // ========== 预设 ==========
  presets: [
    presetUno(),
    presetAttributify(),
  ],

  // ========== Vant 共存配置 ==========
  mode: 'vue-scoped',

  exclude: [
    /node_modules\/vant/,
    /van-/,
  ],

  preflights: {
    getCSS: () => '',
  },

  // ========== 主题配置 ==========
  theme: {
    fontSize: {
      'xs': 'var(--font-size-xs)',
      'sm': 'var(--font-size-sm)',
      'base': 'var(--font-size-base)',
      'lg': 'var(--font-size-lg)',
      'xl': 'var(--font-size-xl)',
      '2xl': 'var(--font-size-2xl)',
      '3xl': 'var(--font-size-3xl)',
    },

    colors: {
      primary: 'var(--primary-color)',
      secondary: 'var(--secondary-color)',
      success: 'var(--success-color)',
      warning: 'var(--warning-color)',
      error: 'var(--error-color)',
    },

    spacing: {
      'xs': 'var(--spacing-xs)',
      'sm': 'var(--spacing-sm)',
      'md': 'var(--spacing-md)',
      'lg': 'var(--spacing-lg)',
      'xl': 'var(--spacing-xl)',
    },

    borderRadius: {
      'sm': 'var(--radius-sm)',
      'DEFAULT': 'var(--radius)',
      'md': 'var(--radius-md)',
      'lg': 'var(--radius-lg)',
    },
  },

  // ========== 快捷方式 ==========
  shortcuts: {
    'text-caption': 'text-xs',
    'text-body-sm': 'text-sm',
    'text-body': 'text-base',
    'text-subtitle': 'text-lg',
    'text-title': 'text-xl',
    'text-heading': 'text-2xl',
    'text-hero': 'text-3xl',

    'flex-center': 'flex items-center justify-center',
    'flex-between': 'flex items-center justify-between',

    'card': 'bg-white rounded-lg shadow-md p-lg',
    'btn': 'px-4 py-2 rounded-lg font-medium transition-all cursor-pointer',
    'btn-primary': 'btn bg-primary text-white hover:opacity-90',
    'btn-outline': 'btn border-2 border-primary text-primary bg-transparent hover:bg-primary hover:text-white',
    'input': 'px-4 py-2 border border-gray-300 rounded-lg focus:border-primary focus:outline-none',
  },

  rules: [
    ['safe-area-inset-bottom', { 'padding-bottom': 'env(safe-area-inset-bottom)' }],
    ['truncate-2', {
      'display': '-webkit-box',
      '-webkit-line-clamp': '2',
      '-webkit-box-orient': 'vertical',
      'overflow': 'hidden',
    }],
  ],

  breakpoints: {
    sm: '640px',
    md: '768px',
    lg: '1024px',
    xl: '1280px',
  },
})
```

---

## Step 3: 集成到 Vite

**vite.config.ts:**

```typescript
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import UnoCSS from 'unocss/vite'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { VantResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig({
  plugins: [
    vue(),
    UnoCSS(),
    AutoImport({
      imports: ['vue', 'vue-router', 'pinia'],
      dts: 'src/auto-imports.d.ts',
    }),
    Components({
      resolvers: [VantResolver()],
      dts: 'src/components.d.ts',
    }),
  ],
})
```

---

## Step 4: 引入样式

**main.ts:**

```typescript
import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { createPinia } from 'pinia'

// UnoCSS（必须在自定义样式之前）
import 'virtual:uno.css'

// 自定义样式
import './assets/style.scss'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
```

---

## Step 5: CSS 变量定义

**variables.css:**

```css
:root {
  /* 第一层：原始值 */
  --font-size-xs: 0.75rem;
  --font-size-sm: 0.875rem;
  --font-size-base: 1rem;
  --font-size-lg: 1.125rem;
  --font-size-xl: 1.25rem;
  --font-size-2xl: 1.5rem;
  --font-size-3xl: 1.875rem;

  /* 第二层：语义化 */
  --font-size-caption: var(--font-size-xs);
  --font-size-body-sm: var(--font-size-sm);
  --font-size-body: var(--font-size-base);
  --font-size-subtitle: var(--font-size-lg);
  --font-size-title: var(--font-size-xl);
  --font-size-heading: var(--font-size-2xl);
  --font-size-hero: var(--font-size-3xl);

  /* 第三层：组件级 */
  --table-cell-font-size: var(--font-size-body-sm);
  --table-header-font-size: var(--font-size-body);
  --button-font-size: var(--font-size-body-sm);
}
```

---

## Step 6: 后端改造

**StyleConfigDTO.java:**

```java
private String fontSizeXs;
private String fontSizeSm;
private String fontSizeBase;
private String fontSizeLg;
private String fontSizeXl;
private String fontSize2xl;
private String fontSize3xl;
```

**StyleConfigService.java:**

```java
private static final Pattern FONT_SIZE_PATTERN =
    Pattern.compile("^(\\d+(\\.\\d+)?)(rem|px|em)$");

private void validateFontSize(String fontSize) {
    if (fontSize != null && !FONT_SIZE_PATTERN.matcher(fontSize).matches()) {
        throw new IllegalArgumentException("无效的字体大小: " + fontSize);
    }
}
```

**init.sql:**

```sql
INSERT INTO sys_dict (category, dict_key, dict_value, extra_value, sort_order, status) VALUES
('style', 'font_size_xs', '辅助信息', '0.75rem', 20, 'ACTIVE'),
('style', 'font_size_sm', '表格内容', '0.875rem', 21, 'ACTIVE'),
('style', 'font_size_base', '正文表头', '1rem', 22, 'ACTIVE'),
('style', 'font_size_lg', '小节标题', '1.125rem', 23, 'ACTIVE'),
('style', 'font_size_xl', '页面副标题', '1.25rem', 24, 'ACTIVE'),
('style', 'font_size_2xl', '页面主标题', '1.5rem', 25, 'ACTIVE'),
('style', 'font_size_3xl', '特大标题', '1.875rem', 26, 'ACTIVE');
```

---

## Step 7: 前端动态主题

**types/theme.ts:**

```typescript
export interface StyleConfig {
  fontSizeXs: string
  fontSizeSm: string
  fontSizeBase: string
  fontSizeLg: string
  fontSizeXl: string
  fontSize2xl: string
  fontSize3xl: string
}

export const FONT_SIZE_PRESETS = [
  { key: 'compact', name: '紧凑', description: '数据密集型', wcagCompliant: false,
    sizes: { xs: '0.625rem', sm: '0.75rem', base: '0.875rem', lg: '1rem', xl: '1.125rem', '2xl': '1.25rem', '3xl': '1.5rem' } },
  { key: 'standard', name: '标准', description: '通用场景', wcagCompliant: false,
    sizes: { xs: '0.75rem', sm: '0.875rem', base: '1rem', lg: '1.125rem', xl: '1.25rem', '2xl': '1.5rem', '3xl': '1.875rem' } },
  { key: 'large', name: '大字体', description: '演示/投影', wcagCompliant: true,
    sizes: { xs: '0.875rem', sm: '1rem', base: '1.125rem', lg: '1.25rem', xl: '1.5rem', '2xl': '1.75rem', '3xl': '2rem' } },
  { key: 'accessibility', name: '无障碍', description: 'WCAG 合规', wcagCompliant: true,
    sizes: { xs: '0.875rem', sm: '1rem', base: '1.125rem', lg: '1.25rem', xl: '1.5rem', '2xl': '1.75rem', '3xl': '2rem' } },
]
```

**useTheme.ts:**

```typescript
const applyThemeToCSS = () => {
  const root = document.documentElement
  root.style.setProperty('--font-size-xs', themeConfig.fontSizeXs)
  root.style.setProperty('--font-size-sm', themeConfig.fontSizeSm)
  root.style.setProperty('--font-size-base', themeConfig.fontSizeBase)
  root.style.setProperty('--font-size-lg', themeConfig.fontSizeLg)
  root.style.setProperty('--font-size-xl', themeConfig.fontSizeXl)
  root.style.setProperty('--font-size-2xl', themeConfig.fontSize2xl)
  root.style.setProperty('--font-size-3xl', themeConfig.fontSize3xl)
}
```

---

## Step 8: 渐进式迁移

### 迁移优先级

| 优先级 | 文件 | 类名数 | 理由 |
|--------|------|--------|------|
| P0 | `variables.css` | - | 基础设施 |
| P0 | `useTheme.ts` | - | 动态配置核心 |
| P0 | `StyleSettings.vue` | 124 | 配置 UI |
| P1 | `UserManagement.vue` | 119 | 当前问题页面 |
| P1 | `style.scss` | 79 | 全局样式 |
| P2 | `Home.vue` | 109 | 首页 |
| P2 | `RoleManagement.vue` | 72 | 高频管理页面 |
| P3 | 其他页面 | - | 按使用频率 |

### 迁移示例

**迁移前：**
```vue
<style scoped>
.data-table th {
  font-size: 0.8125rem;
}
</style>
```

**迁移后：**
```vue
<template>
  <th class="text-base font-semibold text-gray-600">...</th>
</template>
```

---

## Vant 共存规范

### ✅ 正确做法

```vue
<template>
  <!-- Vant 组件使用 Vant 原生 API -->
  <van-button size="small" type="primary">Vant 按钮</van-button>

  <!-- 自定义组件使用 UnoCSS -->
  <div class="card p-lg">
    <h2 class="text-heading">自定义卡片</h2>
  </div>
</template>
```

### ❌ 错误做法

```vue
<template>
  <!-- 不要用 UnoCSS 覆盖 Vant 组件 -->
  <van-button class="text-xl p-4">❌ 错误</van-button>
</template>
```

---

## 回滚方案

### 快速回滚（5 分钟内）

```typescript
// main.ts - 注释 UnoCSS 引入
// import 'virtual:uno.css'
```

重新构建部署，系统恢复到引入前状态。

### 部分回滚（单个页面）

1. 移除该页面的 UnoCSS 类名
2. 恢复原有 scoped 样式
3. Git revert 该文件

### 数据回滚（字体配置）

```sql
DELETE FROM sys_dict WHERE category='style' AND dict_key LIKE 'font_size_%';
```

### 回滚触发条件

| 条件 | 阈值 | 操作 |
|------|------|------|
| Vant 组件异常 | > 1 个 | 立即回滚 |
| 用户投诉 | > 10 例 | 立即回滚 |
| 视觉回归测试 | < 95% | 延期修复 |

---

## 测试策略

### 视觉回归测试

| 工具 | 覆盖页面 | 阈值 |
|------|----------|------|
| Playwright 截图对比 | UserManagement, Home, StyleSettings | ≥ 98% |

### 手动测试清单

| 测试项 | 操作 | 预期结果 | 通过标准 |
|--------|------|----------|----------|
| Vant 组件 | 检查所有 Vant 组件 | 样式无变化 | 100% 一致 |
| 表格显示 | 查看用户管理表格 | 表头清晰可读 | ≥ 2 人确认 |
| 动态配置 | 修改字体大小并保存 | 实时生效 | ≤ 1 秒延迟 |
| 预设切换 | 切换 4 种预设 | 正确应用 | 100% 成功 |
| 移动端 | iPhone/Android 测试 | 无布局错乱 | 0 错误 |

### 兼容性测试

- iOS Safari 15+
- Android Chrome 90+
- 微信内置浏览器

---

## 验收标准

### 功能验收

| 验收项 | 指标 | 验收标准 |
|--------|------|----------|
| 动态配置生效时间 | 延迟 | ≤ 1 秒 |
| 预设方案切换 | 成功率 | 100% |
| 字体大小范围 | 合法值 | 0.5rem ~ 4rem |

### 质量验收

| 验收项 | 指标 | 验收标准 |
|--------|------|----------|
| Vant 组件样式 | 变化率 | 0%（无变化） |
| 视觉回归测试 | 通过率 | ≥ 98% |
| 手动测试通过率 | 通过率 | 100% |

### 性能验收

| 验收项 | 指标 | 验收标准 |
|--------|------|----------|
| 首屏加载时间 | 增量 | ≤ +50ms |
| CSS 包体积 | 增量 | ≤ +10KB |

### 代码质量

| 验收项 | 指标 | 验收标准 |
|--------|------|----------|
| 迁移覆盖率 | 百分比 | ≥ 90% 硬编码已替换 |
| TypeScript 错误 | 数量 | 0 |
| ESLint 警告 | 数量 | ≤ 10 |

---

## 风险评估矩阵

| 风险 | 概率 | 影响 | 风险等级 | 缓解措施 | 应急方案 |
|------|------|------|----------|----------|----------|
| Vant 样式冲突 | 低(20%) | 高 | 🟡 中 | scoped + exclude | 快速回滚 |
| 迁移遗漏 | 中(50%) | 中 | 🟡 中 | 搜索脚本 + 审查 | 补充迁移 |
| 视觉回归 | 中(40%) | 中 | 🟡 中 | 截图对比测试 | 调整样式 |
| 学习成本 | 高(60%) | 低 | 🟢 低 | 培训 + 文档 | 持续支持 |
| 性能下降 | 低(10%) | 高 | 🟢 低 | 性能监控 | 优化配置 |
| 用户投诉 | 低(15%) | 高 | 🟡 中 | 灰度发布 | 快速回滚 |

---

## 灰度发布策略

### 阶段 1：内部测试（Day 1-3）
- 范围：开发环境
- 人员：开发团队
- 目标：功能验证

### 阶段 2：测试环境（Day 4-7）
- 范围：测试环境
- 人员：QA + 产品
- 目标：全面测试

### 阶段 3：灰度发布（Day 8-10）
- 范围：生产环境 10% 用户
- 方式：Cookie 或用户 ID 取模
- 监控：错误日志、用户反馈

### 阶段 4：全量发布（Day 11+）
- 条件：灰度期间无严重问题
- 操作：逐步放开到 100%

---

## 监控和告警

### 监控指标

| 指标 | 阈值 | 告警级别 |
|------|------|----------|
| CSS 加载失败率 | > 0.1% | P1 |
| 页面布局错误 | > 0 | P0 |
| 字体配置 API 错误率 | > 1% | P2 |
| 首屏渲染时间增量 | > 100ms | P2 |

---

## 知识转移

### 团队培训

- 时间：2 小时
- 内容：UnoCSS 基础、Vant 共存规则、迁移方法
- 形式：实操演示 + Q&A

### 文档交付

| 文档 | 内容 | 位置 |
|------|------|------|
| UnoCSS 使用指南 | 类名速查、最佳实践 | `docs/UnoCSS-Guide.md` |
| 迁移手册 | 迁移步骤、常见问题 | `docs/Migration-Guide.md` |
| Vant 共存规范 | 使用边界、示例 | `docs/Vant-UnoCSS-Coexistence.md` |

---

## UnoCSS 速查表

```css
/* 字体大小 */
text-xs    → var(--font-size-xs)    /* 12px */
text-sm    → var(--font-size-sm)    /* 14px */
text-base  → var(--font-size-base)  /* 16px */
text-lg    → var(--font-size-lg)    /* 18px */
text-xl    → var(--font-size-xl)    /* 20px */
text-2xl   → var(--font-size-2xl)   /* 24px */
text-3xl   → var(--font-size-3xl)   /* 30px */

/* 语义化别名 */
text-caption  → text-xs
text-body-sm  → text-sm
text-body     → text-base
text-subtitle → text-lg
text-title    → text-xl
text-heading  → text-2xl
text-hero     → text-3xl

/* 布局 */
flex-center, flex-between
card, btn, btn-primary, btn-outline
```

---

## 后续优化

| 功能 | 优先级 | 说明 |
|------|--------|------|
| 响应式字体 | P2 | `text-sm md:text-base lg:text-lg` |
| 暗色模式 | P2 | `dark:bg-gray-900` |
| 行高配置 | P2 | 字体大小与行高绑定 |
| 图标集成 | P3 | `@unocss/preset-icons` |