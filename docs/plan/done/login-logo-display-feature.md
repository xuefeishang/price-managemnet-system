# 登录页面动态Logo展示实施方案

## Context

用户希望在登录页面的系统名称左侧展示动态Logo，Logo从数据库保存的样式配置中获取。

### 现有架构分析

1. **数据源已就绪**：
   - `StyleConfig.logoUrl` 字段存储Logo URL
   - `StyleConfig.logoSize` 字段存储Logo尺寸（small/medium/large/xlarge）
   - `useTheme` composable 已提供 `themeConfig.logoUrl` 和 `themeConfig.logoSize`
   - `AVAILABLE_LOGO_SIZES` 定义了尺寸映射：small=24px, medium=36px, large=48px, xlarge=64px

2. **登录页现状**：
   - PC布局：左侧品牌区域显示 `themeConfig.systemName`（第221行）
   - 移动端布局：标题区域显示 `themeConfig.systemName`（第369行）
   - 已在 `onMounted` 中调用 `loadThemeConfig()` 加载主题配置

3. **参考实现**：
   - `Layout.vue` 第30-37行：`logoSizeStyle` 计算属性实现尺寸映射
   - `Layout.vue` 第275行：导航栏Logo展示逻辑

## 实现方案

### 前端修改

#### 1. Login.vue 添加Logo展示

**修改位置**：
- PC布局：`.brand-content` 区域（第220-223行）
- 移动端布局：`.title-section` 区域（第368-371行）

**实现要点**：

```vue
<script setup>
// 添加 logoSizeStyle 计算属性
const logoSizeStyle = computed(() => {
  const sizeMap: Record<string, string> = {
    small: '24px',
    medium: '36px',
    large: '48px',
    xlarge: '64px'
  }
  const size = sizeMap[themeConfig.logoSize] || '36px'
  return { height: size }
})

// Logo URL 处理（参考 Layout.vue）
const logoUrlFull = computed(() => {
  const url = themeConfig.logoUrl
  if (!url) return ''
  return url.startsWith('http') ? url : `${import.meta.env.VITE_API_BASE_URL || ''}${url}`
})
</script>

<template>
  <!-- PC布局品牌区域 -->
  <div class="brand-content">
    <img
      v-if="logoUrlFull"
      :src="logoUrlFull"
      alt="Logo"
      class="brand-logo"
      :style="logoSizeStyle"
    />
    <h1 class="brand-title">{{ themeConfig.systemName }}</h1>
    <p class="brand-subtitle">企业价格展示与管理平台</p>
  </div>

  <!-- 移动端标题区域 -->
  <div class="title-section">
    <img
      v-if="logoUrlFull"
      :src="logoUrlFull"
      alt="Logo"
      class="main-logo"
      :style="logoSizeStyle"
    />
    <h1 class="main-title">{{ themeConfig.systemName }}</h1>
    <p class="subtitle">企业价格展示与管理平台</p>
  </div>
</template>
```

#### 2. CSS样式

```css
/* PC布局 Logo */
.brand-logo {
  margin-bottom: 16px;
  object-fit: contain;
}

/* 移动端 Logo */
.main-logo {
  margin-bottom: 12px;
  object-fit: contain;
}
```

### 后端检查

无需修改。`StyleConfig` 实体已包含 `logoUrl` 和 `logoSize` 字段，API `/api/style/config` 已返回完整配置。

## 实现步骤

1. **修改 Login.vue**：
   - 添加 `logoSizeStyle` 和 `logoUrlFull` 计算属性
   - PC布局 `.brand-content` 添加 `<img>` 标签
   - 移动端布局 `.title-section` 添加 `<img>` 标签
   - 添加对应CSS样式

2. **验证**：
   - 构建验证：`npm run build`
   - 功能验证：未配置Logo时不显示，配置后正确显示

## 关键参考文件

- `frontend/src/views/Login.vue` - 登录页面（待修改）
- `frontend/src/composables/useTheme.ts` - 主题配置（已提供logoUrl/logoSize）
- `frontend/src/components/Layout.vue` - Logo展示参考实现
- `frontend/src/types/theme.ts` - AVAILABLE_LOGO_SIZES尺寸定义

## Verification

1. 未配置Logo时：页面正常显示系统名称，无Logo占位
2. 配置Logo后：Logo显示在系统名称左侧/上方
3. Logo尺寸跟随 `logoSize` 配置变化
4. PC和移动端布局均正确展示
