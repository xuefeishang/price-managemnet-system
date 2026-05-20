# 样式系统升级计划：颜色方案 + 布局样式

**目标：实现颜色方案与布局样式分离，支持独立切换和自由组合**

---

## 一、需求分析

### 1.1 用户需求

1. **颜色方案独立选择**：预制配色方案，由专业配色师制定
2. **布局样式独立选择**：至少4种布局样式，由UI设计师制定
3. **自由组合**：颜色 + 布局可独立切换，互不影响
4. **参考图还原**：新增一套"深矿蓝仪表盘"布局，1:1还原参考图

### 1.2 现有系统分析

**当前架构：**
- 样式配置存储在 `sys_dict` 表（category='theme'）
- 前端通过 `useTheme` composable 管理主题
- 样式设置页面 `StyleSettings.vue` 提供主题切换

**当前问题：**
- 颜色和布局耦合在一起（主题=颜色+布局）
- 无法独立切换颜色或布局
- 缺少布局样式选择功能

### 1.3 参考图分析

**参考图特征：**
- 左侧深色固定导航栏（深蓝背景）
- 顶部标题区（大标题 + 日期选择器）
- 经营摘要区（4卡片网格）
- 核心指标区（大数字 + 涨跌标签 + 迷你图）
- 渐变面积图
- 紧凑数据表格

**颜色体系：**
| 元素 | 颜色值 |
|------|--------|
| 主色 | `#165DFF` |
| 导航背景 | `#1E3A5F` |
| 页面背景 | `#F5F5F5` |
| 卡片背景 | `#FFFFFF` |
| 涨价色 | `#EF4444` |
| 跌价色 | `#10B981` |

---

## 二、设计方案

### 2.1 架构设计：颜色与布局分离

```
┌─────────────────────────────────────────────────────────────┐
│                      样式配置                                 │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────────┐  ┌─────────────────────────────┐   │
│  │   颜色方案           │  │   布局样式                   │   │
│  │   (ColorScheme)     │  │   (LayoutStyle)             │   │
│  │                     │  │                             │   │
│  │  - 涨跌颜色         │  │  - 导航栏样式                 │   │
│  │  - 图表配色         │  │  - 页面布局                   │   │
│  │  - 主色调           │  │  - 卡片样式                   │   │
│  │                     │  │  - 表格样式                   │   │
│  └─────────────────────┘  └─────────────────────────────┘   │
│           │                          │                       │
│           └──────────┬───────────────┘                       │
│                      ▼                                       │
│              组合应用 = 颜色方案 + 布局样式                    │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 颜色方案设计（配色师制定）

#### 方案一：青绿经典（当前默认，保留）
| 配色项 | 颜色值 | 说明 |
|--------|--------|------|
| 涨价色 | `#EF4444` | 红色，醒目警示 |
| 跌价色 | `#10B981` | 绿色，温和安抚 |
| 持平色 | `#9CA3AF` | 灰色，中性 |
| 图表主色 | `#0D6E6E` | 青绿，专业稳重（系统主色） |
| 图表配色 | `#0D6E6E, #10B981, #F59E0B, #EF4444, #8B5CF6, #EC4899, #6366F1, #14B8A6, #64748B` | 9色渐变 |

#### 方案二：经典红绿（传统）
| 配色项 | 颜色值 | 说明 |
|--------|--------|------|
| 涨价色 | `#EF4444` | 红色，醒目警示 |
| 跌价色 | `#10B981` | 绿色，温和安抚 |
| 持平色 | `#9CA3AF` | 灰色，中性 |
| 图表主色 | `#0D6E6E` | 青绿，专业稳重 |
| 图表配色 | `#0D6E6E, #10B981, #F59E0B, #EF4444, #8B5CF6, #EC4899, #6366F1, #14B8A6, #64748B` | 9色渐变 |

**说明：方案一与方案二配色相同，方案一标记为"当前默认"，方便用户识别。**

#### 方案二：美股绿红（现有）
| 配色项 | 颜色值 | 说明 |
|--------|--------|------|
| 涨价色 | `#10B981` | 绿色，代表增长 |
| 跌价色 | `#EF4444` | 红色，代表下跌 |
| 持平色 | `#9CA3AF` | 灰色 |
| 图表主色 | `#0D6E6E` | 青绿 |
| 图表配色 | 同方案一 | |

#### 方案三：商务蓝橙（现有）
| 配色项 | 颜色值 | 说明 |
|--------|--------|------|
| 涨价色 | `#3B82F6` | 蓝色，商务专业 |
| 跌价色 | `#F97316` | 橙色，警示醒目 |
| 持平色 | `#9CA3AF` | 灰色 |
| 图表主色 | `#3B82F6` | 蓝色 |
| 图表配色 | `#3B82F6, #F97316, #0D6E6E, #8B5CF6, #EC4899, #6366F1, #14B8A6, #64748B, #10B981` | |

#### 方案四：高贵紫金（现有）
| 配色项 | 颜色值 | 说明 |
|--------|--------|------|
| 涨价色 | `#8B5CF6` | 紫色，高贵神秘 |
| 跌价色 | `#EAB308` | 金色，财富象征 |
| 持平色 | `#9CA3AF` | 灰色 |
| 图表主色 | `#8B5CF6` | 紫色 |
| 图表配色 | `#8B5CF6, #EAB308, #0D6E6E, #EC4899, #6366F1, #14B8A6, #64748B, #10B981, #F59E0B` | |

#### 方案五：深矿蓝（新增，参考图配色）
| 配色项 | 颜色值 | 说明 |
|--------|--------|------|
| 涨价色 | `#EF4444` | 红色，涨跌分明 |
| 跌价色 | `#10B981` | 绿色 |
| 持平色 | `#9CA3AF` | 灰色 |
| 图表主色 | `#165DFF` | 深矿蓝，专业科技 |
| 图表配色 | `#165DFF, #10B981, #F59E0B, #EF4444, #8B5CF6, #EC4899, #6366F1, #14B8A6, #64748B` | |

#### 方案六：暖色系（新增）
| 配色项 | 颜色值 | 说明 |
|--------|--------|------|
| 涨价色 | `#F97316` | 橙色，温暖活力 |
| 跌价色 | `#06B6D4` | 青色，冷静理性 |
| 持平色 | `#9CA3AF` | 灰色 |
| 图表主色 | `#F97316` | 橙色 |
| 图表配色 | `#F97316, #06B6D4, #F59E0B, #EF4444, #8B5CF6, #EC4899, #6366F1, #14B8A6, #64748B` | |

### 2.3 布局样式设计（UI设计师制定）

#### 布局一：经典顶部导航（现有）
```
┌─────────────────────────────────────────────────────────────┐
│  Logo    导航菜单                              用户信息      │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│                      主内容区                                │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```
**特点：**
- 顶部固定导航栏
- 浅色背景
- 适合传统后台管理

#### 布局二：左侧导航（现有变体）
```
┌──────────┬──────────────────────────────────────────────────┐
│  Logo    │                                                  │
│  菜单1   │                   主内容区                       │
│  菜单2   │                                                  │
│  菜单3   │                                                  │
│          │                                                  │
└──────────┴──────────────────────────────────────────────────┘
```
**特点：**
- 左侧固定导航栏
- 浅色导航背景
- 适合功能较多的系统

#### 布局三：深矿蓝仪表盘（新增，参考图布局）
```
┌──────────┬──────────────────────────────────────────────────┐
│ ████████ │  Dashboard        Today, Jun 29  [日期选择]     │
│ ████████ ├──────────────────────────────────────────────────┤
│ █ 导航█ │  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐             │
│ █ 菜单█ │  │产品数│ │今日更│ │涨价数│ │跌价数│             │
│ ████████ │  └──────┘ └──────┘ └──────┘ └──────┘             │
│ ████████ ├──────────────────────────────────────────────────┤
│ ████████ │  ┌─────────────────────────────────────────────┐ │
│ ████████ │  │ 电铜  ¥68,500  +2.5%↑  [迷你图]             │ │
│ ████████ │  │ 金    ¥450.2   +1.2%↑  [迷你图]             │ │
│ ████████ │  └─────────────────────────────────────────────┘ │
│          ├──────────────────────────────────────────────────┤
│          │  ████████████████████████ 渐变面积图              │
│          ├──────────────────────────────────────────────────┤
│          │  产品 │ 价格 │ 涨跌 │ 分类 │ ...                 │
└──────────┴──────────────────────────────────────────────────┘
```
**特点：**
- 左侧深色导航栏（`#1E3A5F`）
- 顶部标题区（大标题 + 日期选择器）
- 经营摘要区（4卡片网格）
- 核心指标区（大数字 + 涨跌标签 + 迷你图）
- 渐变面积图
- 紧凑数据表格
- 页面背景 `#F5F5F5`

#### 布局四：极简卡片式（新增）
```
┌─────────────────────────────────────────────────────────────┐
│  [≡]  系统名称                              [用户头像]      │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                    大卡片                              │   │
│  │              核心数据展示区域                           │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌───────────┐  ┌───────────┐  ┌───────────┐              │
│  │  小卡片   │  │  小卡片   │  │  小卡片   │              │
│  └───────────┘  └───────────┘  └───────────┘              │
└─────────────────────────────────────────────────────────────┘
```
**特点：**
- 极简顶部导航
- 大卡片 + 小卡片组合
- 无侧边栏
- 适合数据展示型系统

---

## 三、数据结构设计

### 3.1 数据库扩展

**新增字典分类：**

```sql
-- 颜色方案分类（7套方案）
INSERT INTO sys_dict (category, dict_key, dict_value, extra_value, sort_order, status)
VALUES 
('color_scheme', 'scheme_teal_classic', '青绿经典（默认）', '{"priceRise":"#EF4444","priceFall":"#10B981","priceFlat":"#9CA3AF","chartPrimary":"#0D6E6E","chartColors":["#0D6E6E","#10B981","#F59E0B","#EF4444","#8B5CF6","#EC4899","#6366F1","#14B8A6","#64748B"]}', 1, 'ACTIVE'),
('color_scheme', 'scheme_classic', '经典红绿', '{"priceRise":"#EF4444","priceFall":"#10B981","priceFlat":"#9CA3AF","chartPrimary":"#0D6E6E","chartColors":["#0D6E6E","#10B981","#F59E0B","#EF4444","#8B5CF6","#EC4899","#6366F1","#14B8A6","#64748B"]}', 2, 'ACTIVE'),
('color_scheme', 'scheme_us_stock', '美股绿红', '{"priceRise":"#10B981","priceFall":"#EF4444","priceFlat":"#9CA3AF","chartPrimary":"#0D6E6E","chartColors":["#0D6E6E","#10B981","#F59E0B","#EF4444","#8B5CF6","#EC4899","#6366F1","#14B8A6","#64748B"]}', 2, 'ACTIVE'),
('color_scheme', 'scheme_business', '商务蓝橙', '{"priceRise":"#3B82F6","priceFall":"#F97316","priceFlat":"#9CA3AF","chartPrimary":"#3B82F6","chartColors":["#3B82F6","#F97316","#0D6E6E","#8B5CF6","#EC4899","#6366F1","#14B8A6","#64748B","#10B981"]}', 3, 'ACTIVE'),
('color_scheme', 'scheme_noble', '高贵紫金', '{"priceRise":"#8B5CF6","priceFall":"#EAB308","priceFlat":"#9CA3AF","chartPrimary":"#8B5CF6","chartColors":["#8B5CF6","#EAB308","#0D6E6E","#EC4899","#6366F1","#14B8A6","#64748B","#10B981","#F59E0B"]}', 4, 'ACTIVE'),
('color_scheme', 'scheme_deep_blue', '深矿蓝', '{"priceRise":"#EF4444","priceFall":"#10B981","priceFlat":"#9CA3AF","chartPrimary":"#165DFF","chartColors":["#165DFF","#10B981","#F59E0B","#EF4444","#8B5CF6","#EC4899","#6366F1","#14B8A6","#64748B"]}', 5, 'ACTIVE'),
('color_scheme', 'scheme_warm', '暖色系', '{"priceRise":"#F97316","priceFall":"#06B6D4","priceFlat":"#9CA3AF","chartPrimary":"#F97316","chartColors":["#F97316","#06B6D4","#F59E0B","#EF4444","#8B5CF6","#EC4899","#6366F1","#14B8A6","#64748B"]}', 6, 'ACTIVE');

-- 布局样式分类
INSERT INTO sys_dict (category, dict_key, dict_value, extra_value, sort_order, status)
VALUES 
('layout_style', 'layout_top_nav', '经典顶部导航', '{"navPosition":"top","navBgColor":"#FFFFFF","navTextColor":"#1A1A1A","pageBgColor":"#FAFAFA","cardBgColor":"#FFFFFF","cardShadow":"0 1px 3px rgba(0,0,0,0.1)","borderRadius":"12px"}', 1, 'ACTIVE'),
('layout_style', 'layout_left_nav', '左侧导航', '{"navPosition":"left","navBgColor":"#FFFFFF","navTextColor":"#1A1A1A","pageBgColor":"#FAFAFA","cardBgColor":"#FFFFFF","cardShadow":"0 1px 3px rgba(0,0,0,0.1)","borderRadius":"12px"}', 2, 'ACTIVE'),
('layout_style', 'layout_dashboard', '深矿蓝仪表盘', '{"navPosition":"left","navBgColor":"#1E3A5F","navTextColor":"#FFFFFF","pageBgColor":"#F5F5F5","cardBgColor":"#FFFFFF","cardShadow":"0 1px 3px rgba(0,0,0,0.1)","borderRadius":"8px","showTitleBar":true,"showMiniChart":true,"gradientChart":true}', 3, 'ACTIVE'),
('layout_style', 'layout_minimal', '极简卡片式', '{"navPosition":"top-minimal","navBgColor":"transparent","navTextColor":"#1A1A1A","pageBgColor":"#FAFAFA","cardBgColor":"#FFFFFF","cardShadow":"0 4px 6px rgba(0,0,0,0.1)","borderRadius":"16px"}', 4, 'ACTIVE');
```

### 3.2 StyleConfig 扩展

```typescript
// frontend/src/types/theme.ts

export interface StyleConfig {
  systemName?: string
  
  // 颜色方案
  activeColorScheme: string  // 新增：颜色方案Key
  priceRiseColor: string
  priceFallColor: string
  priceFlatColor: string
  chartPrimaryColor: string
  chartBudgetColor: string
  chartColors: string[]
  
  // 布局样式
  activeLayoutStyle: string  // 新增：布局样式Key
  
  // 字体配置
  headingFont: string
  bodyFont: string
  numberFont: string
  fontSizeXs: string
  fontSizeSm: string
  fontSizeBase: string
  fontSizeLg: string
  fontSizeXl: string
  fontSize2xl: string
  fontSize3xl: string
  
  // Logo
  logoUrl: string
  logoSize: string
  
  // 兼容旧版本
  activeTheme: string  // 保留，用于兼容
}

// 新增：颜色方案类型
export interface ColorScheme {
  key: string
  name: string
  description: string
  colors: {
    priceRise: string
    priceFall: string
    priceFlat: string
    chartPrimary: string
    chartColors: string[]
  }
}

// 新增：布局样式类型
export interface LayoutStyle {
  key: string
  name: string
  description: string
  layout: {
    navPosition: 'top' | 'left' | 'top-minimal'
    navBgColor: string
    navTextColor: string
    pageBgColor: string
    cardBgColor: string
    cardShadow: string
    borderRadius: string
    showTitleBar?: boolean
    showMiniChart?: boolean
    gradientChart?: boolean
  }
}

// 预制颜色方案（7套）
export const COLOR_SCHEMES: ColorScheme[] = [
  {
    key: 'scheme_teal_classic',
    name: '青绿经典（默认）',
    description: '当前系统默认配色，青绿主色，专业稳重',
    colors: {
      priceRise: '#EF4444',
      priceFall: '#10B981',
      priceFlat: '#9CA3AF',
      chartPrimary: '#0D6E6E',
      chartColors: ['#0D6E6E', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6', '#EC4899', '#6366F1', '#14B8A6', '#64748B']
    }
  },
  {
    key: 'scheme_classic',
    name: '经典红绿',
    description: '传统配色，涨价红色，跌价绿色',
    colors: {
      priceRise: '#EF4444',
      priceFall: '#10B981',
      priceFlat: '#9CA3AF',
      chartPrimary: '#0D6E6E',
      chartColors: ['#0D6E6E', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6', '#EC4899', '#6366F1', '#14B8A6', '#64748B']
    }
  },
  {
    key: 'scheme_us_stock',
    name: '美股绿红',
    description: '美股风格，涨价绿色，跌价红色',
    colors: {
      priceRise: '#10B981',
      priceFall: '#EF4444',
      priceFlat: '#9CA3AF',
      chartPrimary: '#0D6E6E',
      chartColors: ['#0D6E6E', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6', '#EC4899', '#6366F1', '#14B8A6', '#64748B']
    }
  },
  {
    key: 'scheme_business',
    name: '商务蓝橙',
    description: '商务风格配色',
    colors: {
      priceRise: '#3B82F6',
      priceFall: '#F97316',
      priceFlat: '#9CA3AF',
      chartPrimary: '#3B82F6',
      chartColors: ['#3B82F6', '#F97316', '#0D6E6E', '#8B5CF6', '#EC4899', '#6366F1', '#14B8A6', '#64748B', '#10B981']
    }
  },
  {
    key: 'scheme_noble',
    name: '高贵紫金',
    description: '高贵风格配色',
    colors: {
      priceRise: '#8B5CF6',
      priceFall: '#EAB308',
      priceFlat: '#9CA3AF',
      chartPrimary: '#8B5CF6',
      chartColors: ['#8B5CF6', '#EAB308', '#0D6E6E', '#EC4899', '#6366F1', '#14B8A6', '#64748B', '#10B981', '#F59E0B']
    }
  },
  {
    key: 'scheme_deep_blue',
    name: '深矿蓝',
    description: '参考图配色，专业科技风格',
    colors: {
      priceRise: '#EF4444',
      priceFall: '#10B981',
      priceFlat: '#9CA3AF',
      chartPrimary: '#165DFF',
      chartColors: ['#165DFF', '#10B981', '#F59E0B', '#EF4444', '#8B5CF6', '#EC4899', '#6366F1', '#14B8A6', '#64748B']
    }
  },
  {
    key: 'scheme_warm',
    name: '暖色系',
    description: '温暖活力配色',
    colors: {
      priceRise: '#F97316',
      priceFall: '#06B6D4',
      priceFlat: '#9CA3AF',
      chartPrimary: '#F97316',
      chartColors: ['#F97316', '#06B6D4', '#F59E0B', '#EF4444', '#8B5CF6', '#EC4899', '#6366F1', '#14B8A6', '#64748B']
    }
  }
]

// 预制布局样式
export const LAYOUT_STYLES: LayoutStyle[] = [
  {
    key: 'layout_top_nav',
    name: '经典顶部导航',
    description: '传统后台管理布局',
    layout: {
      navPosition: 'top',
      navBgColor: '#FFFFFF',
      navTextColor: '#1A1A1A',
      pageBgColor: '#FAFAFA',
      cardBgColor: '#FFFFFF',
      cardShadow: '0 1px 3px rgba(0,0,0,0.1)',
      borderRadius: '12px'
    }
  },
  {
    key: 'layout_left_nav',
    name: '左侧导航',
    description: '功能较多的系统布局',
    layout: {
      navPosition: 'left',
      navBgColor: '#FFFFFF',
      navTextColor: '#1A1A1A',
      pageBgColor: '#FAFAFA',
      cardBgColor: '#FFFFFF',
      cardShadow: '0 1px 3px rgba(0,0,0,0.1)',
      borderRadius: '12px'
    }
  },
  {
    key: 'layout_dashboard',
    name: '深矿蓝仪表盘',
    description: '参考图布局，专业数据展示',
    layout: {
      navPosition: 'left',
      navBgColor: '#1E3A5F',
      navTextColor: '#FFFFFF',
      pageBgColor: '#F5F5F5',
      cardBgColor: '#FFFFFF',
      cardShadow: '0 1px 3px rgba(0,0,0,0.1)',
      borderRadius: '8px',
      showTitleBar: true,
      showMiniChart: true,
      gradientChart: true
    }
  },
  {
    key: 'layout_minimal',
    name: '极简卡片式',
    description: '简洁现代布局',
    layout: {
      navPosition: 'top-minimal',
      navBgColor: 'transparent',
      navTextColor: '#1A1A1A',
      pageBgColor: '#FAFAFA',
      cardBgColor: '#FFFFFF',
      cardShadow: '0 4px 6px rgba(0,0,0,0.1)',
      borderRadius: '16px'
    }
  }
]
```

---

## 四、实施步骤

### 阶段一：数据结构扩展（0.5天）

**任务：**
1. 数据库新增 `color_scheme` 和 `layout_style` 字典分类
2. 扩展 `StyleConfig` 类型定义
3. 扩展 `useTheme` composable

**文件：**
- `backend/src/main/resources/init.sql` — 新增字典数据
- `frontend/src/types/theme.ts` — 新增类型定义
- `frontend/src/composables/useTheme.ts` — 扩展主题服务

### 阶段二：样式设置页面改造（1天）

**任务：**
1. 新增"颜色方案"选择区（6套方案）
2. 新增"布局样式"选择区（4套布局）
3. 实时预览功能
4. 保存配置功能

**文件：**
- `frontend/src/views/StyleSettings.vue` — 页面改造

**UI设计：**
```
┌─────────────────────────────────────────────────────────────┐
│  全局样式设置                                                 │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ 颜色方案（7选1）                                      │   │
│  │ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐┌──────┐│   │
│  │ │青绿经│ │经典红│ │美股绿│ │商务蓝│ │高贵紫│ │深矿蓝│ │暖色系││   │
│  │ │典(默│ │  绿  │ │  红  │ │  橙  │ │  金  │ │      │ │      ││   │
│  │ │ 认) │ │      │ │      │ │      │ │      │ │      │ │      ││   │
│  │ └──────┘ └──────┘ └──────┘ └──────┘ └──────┘ └──────┘└──────┘│   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ 布局样式（4选1）                                      │   │
│  │ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐        │   │
│  │ │顶部导航│ │左侧导航│ │深矿蓝仪│ │极简卡片│        │   │
│  │ │        │ │        │ │ 表盘   │ │        │        │   │
│  │ └────────┘ └────────┘ └────────┘ └────────┘        │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ 实时预览                                              │   │
│  │  [预览区域：颜色 + 布局组合效果]                       │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                             │
│                                    [重置] [保存配置]        │
└─────────────────────────────────────────────────────────────┘
```

### 阶段三：布局组件改造（2天）

**任务：**
1. Layout.vue 支持多种导航布局
2. 首页 Home.vue 支持仪表盘布局
3. 卡片组件样式动态化
4. 表格组件样式动态化

**文件：**
- `frontend/src/components/Layout.vue` — 导航布局
- `frontend/src/views/Home.vue` — 首页布局
- `frontend/src/components/DashboardCard.vue` — 新增卡片组件
- `frontend/src/components/MiniChart.vue` — 新增迷你图组件

### 阶段四：图表美化（0.5天）

**任务：**
1. 面积图渐变填充
2. 迷你图组件
3. 图表配色动态化

**文件：**
- `frontend/src/components/TrendChart.vue` — 图表美化

### 阶段五：后端API扩展（0.5天）

**任务：**
1. 新增颜色方案和布局样式API
2. 保存/获取配置API扩展

**文件：**
- `backend/src/main/java/com/pricemanagement/controller/StyleConfigController.java`
- `backend/src/main/java/com/pricemanagement/service/StyleConfigService.java`

---

## 五、验收标准

| 功能 | 验收标准 |
|------|----------|
| 颜色方案切换 | 选择任意颜色方案，涨跌颜色和图表配色立即变化 |
| 布局样式切换 | 选择任意布局样式，导航栏和页面布局立即变化 |
| 组合效果 | 颜色方案 + 布局样式可自由组合，互不影响 |
| 深矿蓝仪表盘 | 选择"深矿蓝"颜色 + "深矿蓝仪表盘"布局，1:1还原参考图 |
| 实时预览 | 切换颜色或布局时，预览区域实时更新 |
| 保存配置 | 点击保存后，配置持久化，刷新页面保持 |
| 兼容性 | 保留原有主题切换功能，不影响现有用户 |

---

## 六、工时估算

| 阶段 | 工时 | 说明 |
|------|------|------|
| 阶段一 | 0.5天 | 数据结构扩展 |
| 阶段二 | 1天 | 样式设置页面改造 |
| 阶段三 | 2天 | 布局组件改造 |
| 阶段四 | 0.5天 | 图表美化 |
| 阶段五 | 0.5天 | 后端API扩展 |
| **总计** | **4.5天** | — |

---

## 七、风险与降级

### 风险

1. **布局切换复杂**：不同布局的导航栏结构差异大
2. **样式冲突**：新旧样式系统可能冲突
3. **响应式适配**：新布局在移动端需特殊处理

### 降级方案

1. **渐进式改造**：先完成颜色方案，再完成布局样式
2. **布局隔离**：不同布局使用独立组件
3. **移动端固定**：移动端保持现有布局，仅切换颜色

---

## 八、实施顺序建议

**推荐顺序：**
1. 阶段一（数据结构）→ 基础设施
2. 阶段五（后端API）→ 接口就绪
3. 阶段二（样式设置页面）→ 可切换验证
4. 阶段三（布局组件）→ 布局变化
5. 阶段四（图表美化）→ 样式完善

---

*计划创建日期：2026-05-20*
*最后更新：2026-05-20 — 颜色与布局分离设计*