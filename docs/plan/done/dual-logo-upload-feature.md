# 双Logo上传功能实施方案

## Context

用户希望在品牌设置中增加2套Logo上传功能，并可以控制哪个Logo显示在登录页，哪个Logo显示在登录后的页面（导航栏）。

### 现有架构分析

1. **数据存储**：
   - `sys_style_config` 表采用 key-value 结构存储配置
   - 当前 `logoUrl` 存储单个Logo URL
   - `logoSize` 存储Logo尺寸

2. **后端API**：
   - `POST /api/style/logo` - 上传Logo，返回URL
   - `GET /api/style/config` - 获取完整配置
   - `PUT /api/style/config` - 更新配置

3. **前端组件**：
   - `BrandSettingsPanel.vue` - 品牌设置面板
   - `Login.vue` - 登录页（使用 `logoUrlFull`）
   - `Layout.vue` - 导航栏（使用 `logoUrlFull`）

4. **配置类型**：
   - `StyleConfigDTO` - 后端DTO
   - `StyleConfig` - 前端类型定义

## 实现方案

### 数据模型变更

#### 1. 新增配置字段

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `logoUrlLogin` | String | 登录页Logo URL |
| `logoUrlNav` | String | 导航栏Logo URL |
| `logoSizeLogin` | String | 登录页Logo尺寸（可选，默认跟随logoSize） |
| `logoSizeNav` | String | 导航栏Logo尺寸（可选，默认跟随logoSize） |

#### 2. 兼容性处理

- 保留 `logoUrl` 和 `logoSize` 作为默认值
- 如果 `logoUrlLogin` 为空，则使用 `logoUrl`
- 如果 `logoUrlNav` 为空，则使用 `logoUrl`

### 后端修改

#### 1. StyleConfigDTO.java

```java
// 新增字段
private String logoUrlLogin;    // 登录页Logo
private String logoUrlNav;      // 导航栏Logo
private String logoSizeLogin;   // 登录页Logo尺寸
private String logoSizeNav;     // 导航栏Logo尺寸
```

#### 2. StyleConfigService.java

新增方法：
```java
// 上传登录页Logo
public String uploadLogoLogin(MultipartFile file) throws IOException;

// 上传导航栏Logo
public String uploadLogoNav(MultipartFile file) throws IOException;
```

#### 3. StyleConfigController.java

新增接口：
```java
@PostMapping("/logo/login")
@PreAuthorize("hasRole('ADMIN')")
public Result<String> uploadLogoLogin(@RequestParam("file") MultipartFile file);

@PostMapping("/logo/nav")
@PreAuthorize("hasRole('ADMIN')")
public Result<String> uploadLogoNav(@RequestParam("file") MultipartFile file);
```

### 前端修改

#### 1. types/theme.ts

```typescript
export interface StyleConfig {
  // ... 现有字段
  logoUrl: string
  logoUrlLogin?: string  // 新增
  logoUrlNav?: string    // 新增
  logoSize: string
  logoSizeLogin?: string // 新增
  logoSizeNav?: string   // 新增
}
```

#### 2. api/style.ts

```typescript
// 上传登录页Logo
export const uploadLogoLogin = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return http.post<string>('/api/style/logo/login', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// 上传导航栏Logo
export const uploadLogoNav = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return http.post<string>('/api/style/logo/nav', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
```

#### 3. BrandSettingsPanel.vue

UI布局：
```
┌─────────────────────────────────────────┐
│ Logo 设置                                │
├─────────────────────────────────────────┤
│ ┌─────────────┐  ┌─────────────┐        │
│ │ 登录页Logo   │  │ 导航栏Logo   │        │
│ │  [预览]     │  │  [预览]      │        │
│ │  [上传]     │  │  [上传]      │        │
│ └─────────────┘  └─────────────┘        │
│                                         │
│ Logo尺寸：                               │
│ 登录页：[小] [中] [大] [特大]            │
│ 导航栏：[小] [中] [大] [特大]            │
│                                         │
│ ☑ 登录页与导航栏使用相同Logo              │
└─────────────────────────────────────────┘
```

功能：
- 双Logo上传区域，独立预览
- 各自独立的尺寸选择
- "使用相同Logo"复选框，勾选后隐藏导航栏Logo设置

#### 4. Login.vue

```typescript
// 登录页Logo优先使用logoUrlLogin，否则使用logoUrl
const logoUrlFull = computed(() => {
  const url = themeConfig.value.logoUrlLogin || themeConfig.value.logoUrl
  if (!url) return ''
  return url.startsWith('http') ? url : `${import.meta.env.VITE_API_BASE_URL || ''}${url}`
})

// 登录页Logo尺寸
const logoSizeStyle = computed(() => {
  const sizeMap: Record<string, string> = { ... }
  const size = themeConfig.value.logoSizeLogin || themeConfig.value.logoSize
  return { height: sizeMap[size] || '72px' }
})
```

#### 5. Layout.vue

类似修改，使用 `logoUrlNav` 和 `logoSizeNav`。

#### 6. useTheme.ts / useStyleSettingsWorkbench.ts

添加新字段的响应式处理。

## 实现步骤

### Phase 1: 后端修改

1. 修改 `StyleConfigDTO.java` 添加新字段
2. 修改 `StyleConfigService.java`：
   - 添加 `uploadLogoLogin` 方法
   - 添加 `uploadLogoNav` 方法
   - 修改 `getStyleConfig` 处理新字段
   - 修改 `updateStyleConfig` 保存新字段
3. 修改 `StyleConfigController.java` 添加新接口

### Phase 2: 前端类型与API

1. 修改 `types/theme.ts` 添加新字段
2. 修改 `api/style.ts` 添加新API
3. 修改 `useTheme.ts` 和 `useStyleSettingsWorkbench.ts`

### Phase 3: 前端UI

1. 重构 `BrandSettingsPanel.vue` 支持双Logo
2. 修改 `Login.vue` 使用登录页Logo
3. 修改 `Layout.vue` 使用导航栏Logo

### Phase 4: 验证

1. 构建验证
2. 功能测试：上传、切换、显示

## 关键参考文件

- `backend/src/main/java/com/pricemanagement/dto/StyleConfigDTO.java`
- `backend/src/main/java/com/pricemanagement/service/StyleConfigService.java`
- `backend/src/main/java/com/pricemanagement/controller/StyleConfigController.java`
- `frontend/src/types/theme.ts`
- `frontend/src/api/style.ts`
- `frontend/src/components/style-settings/BrandSettingsPanel.vue`
- `frontend/src/views/Login.vue`
- `frontend/src/components/Layout.vue`

## Verification

1. 上传登录页Logo → 登录页显示新Logo
2. 上传导航栏Logo → 导航栏显示新Logo
3. 只上传一个Logo → 两处都显示该Logo
4. 尺寸独立控制 → 登录页和导航栏Logo尺寸可不同
5. 兼容性 → 旧数据（只有logoUrl）正常显示
