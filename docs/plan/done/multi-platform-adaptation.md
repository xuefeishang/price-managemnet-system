# 多端适配方案（H5/APP/小程序）

> **实施状态：✅ Phase 3 完成，功能完善**
> - ✅ Phase 1：项目初始化（已完成）
> - ✅ Phase 2：核心层迁移（已完成）
> - ✅ Phase 3：页面迁移（已完成）
> - ✅ 功能完善：价格走势图、分类筛选、产品规格（已完成）
> - ⏳ Phase 4-7：待实施（微信登录、分享、订阅消息等）

## 一、当前技术栈分析

### 1.1 前端技术栈

| 层级 | 当前技术 | 版本 | 多端兼容性评估 |
|------|---------|------|---------------|
| 框架 | Vue 3 | 3.4.0 | ✅ 全平台兼容 |
| UI组件库 | Vant | 4.8.0 | ⚠️ H5原生支持，小程序需用 vant-weapp |
| 状态管理 | Pinia | 2.1.7 | ✅ 全平台兼容 |
| 路由 | Vue Router | 4.2.5 | ❌ 小程序不兼容，需用 uni-app 路由 |
| HTTP客户端 | Axios | 1.15.0 | ⚠️ 小程序需用 uni.request |
| 构建工具 | Vite | 8.0.5 | ❌ 小程序需专用构建工具 |
| 图表 | ECharts | 6.0.0 | ⚠️ 小程序需用 echarts-for-weixin |
| 工具库 | dayjs | 1.11.10 | ✅ 全平台兼容 |
| Excel处理 | xlsx | 0.18.5 | ⚠️ 小程序端需评估包体积影响 |

### 1.2 项目结构分析

```
frontend/src/
├── api/           # API接口层 - 可复用，需适配请求方式
├── components/    # 公共组件 - 需评估小程序兼容性
├── composables/   # 组合式函数 - 大部分可复用
├── store/         # 状态管理 - 可复用，需适配存储API
├── types/         # TypeScript类型 - 完全可复用
├── utils/         # 工具函数 - 部分需适配
└── views/         # 页面视图 - 需迁移为 uni-app 页面
```

### 1.3 页面功能清单

| 页面 | 路径 | 功能 | 小程序必要性 | 迁移优先级 |
|------|------|------|-------------|-----------|
| Login | /login | 用户登录 | ✅ 必需 | P0 |
| Home | /home | 首页仪表盘 | ✅ 必需 | P0 |
| Products | /products | 产品列表 | ✅ 必需 | P0 |
| ProductDetail | /product-detail/:id | 产品详情 | ✅ 必需 | P0 |
| PriceMaintenance | /price-maintenance | 价格维护 | ✅ 必需 | P1 |
| ProductEdit | /product-edit/:id? | 产品编辑 | ⚠️ 可简化 | P1 |
| Categories | /categories | 分类管理 | ⚠️ 可简化 | P2 |
| Origins | /origins | 产地管理 | ⚠️ 可简化 | P2 |
| Customers | /customers | 客户管理 | ⚠️ 可简化 | P2 |
| Import | /import | 导入导出 | ❌ 不推荐 | P3 |
| UserManagement | /users | 用户管理 | ❌ 建议隐藏 | P3 |
| MenuConfig | /menu-config | 菜单配置 | ❌ 建议隐藏 | P3 |
| OperationLog | /operation-log | 操作日志 | ❌ 建议隐藏 | P3 |
| StyleSettings | /style-settings | 样式设置 | ❌ 建议隐藏 | P3 |
| Profile | /profile | 个人管理 | ✅ 必需 | P1 |
| Approval | /approval | 审批管理 | ⚠️ 可简化 | P2 |
| ApprovalConfig | /approval-config | 审批流配置 | ❌ 建议隐藏 | P3 |
| DictManagement | /dict-management | 数据字典 | ❌ 建议隐藏 | P3 |

---

## 二、方案选型评估

### 2.1 候选方案对比

| 维度 | uni-app | Taro | 独立小程序 | Capacitor+小程序 |
|------|---------|------|-----------|-----------------|
| **技术栈匹配** | ⭐⭐⭐⭐⭐ Vue3原生支持 | ⭐⭐⭐⭐ Vue3支持 | ⭐⭐ 需重新开发 | ⭐⭐⭐⭐ H5直接转APP |
| **代码复用率** | 80%+ | 70%+ | 10% | H5 95% / 小程序 10% |
| **学习成本** | 低 | 中 | 高 | 低 |
| **维护成本** | 低 | 低 | 高 | 中 |
| **生态完善度** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |
| **性能表现** | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |
| **调试体验** | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |

### 2.2 方案详细评估

#### 方案一：uni-app Vue3

**优势：**
- Vue3 + Composition API 与现有代码风格完全一致
- Vant 有官方小程序版本（vant-weapp）
- 一套代码输出 H5/APP/小程序/快应用
- 国产框架，中文文档完善，社区活跃
- 条件编译灵活，可针对平台定制

**劣势：**
- 部分高级特性受限（如 Teleport）
- 小程序包体积限制，需分包处理
- 调试体验略逊于原生开发

**适用场景：** 追求一套代码多端运行，团队熟悉 Vue 生态

#### 方案二：Taro 3.x Vue3

**优势：**
- 支持 React/Vue 多框架
- 更接近标准前端开发体验
- 插件生态丰富

**劣势：**
- Vue3 支持不如 uni-app 完善
- 部分第三方库适配需要额外工作
- 学习曲线略陡

**适用场景：** 团队有 React 背景，或需要跨框架开发

#### 方案三：独立小程序 + H5

**优势：**
- 各端独立开发，互不影响
- 可针对各端特性深度优化
- 调试体验最佳

**劣势：**
- 维护成本翻倍
- 功能迭代需同步多端
- 代码无法复用

**适用场景：** 各端功能差异大，有独立团队维护

#### 方案四：Capacitor + 独立小程序

**优势：**
- H5 直接打包为 APP，改动最小
- 小程序独立开发，体验最优
- 技术风险最低

**劣势：**
- 小程序端需独立开发
- APP 体验不如原生
- 两套代码维护

**适用场景：** 快速上线 APP，小程序作为独立渠道

### 2.3 推荐方案

**推荐选择：uni-app Vue3 版本**

**理由：**
1. **技术栈一致性**：Vue3 + Composition API + Pinia 与现有代码高度一致
2. **组件库兼容**：Vant 有小程序版本，迁移成本低
3. **代码复用**：API层、状态管理、类型定义、工具函数可直接复用
4. **长期维护**：一套代码多端运行，维护成本最低
5. **团队能力**：无需学习新技术栈，上手快

---

## 三、技术迁移评估

### 3.1 API层迁移评估

**当前实现：**
- 使用 Axios 作为 HTTP 客户端
- 封装了请求/响应拦截器
- 支持 Token 自动刷新
- 统一错误处理

**迁移方案：**
- 使用 uni.request 封装统一请求方法
- 保持 API 接口签名不变
- 迁移拦截器逻辑

**兼容性评估：**

| 功能 | Axios | uni.request | 迁移难度 |
|------|-------|-------------|---------|
| 请求拦截 | ✅ | ✅ | 低 |
| 响应拦截 | ✅ | ✅ | 低 |
| Token刷新 | ✅ | ✅ | 中 |
| 请求重试 | ✅ | ⚠️ 需手动实现 | 中 |
| 请求取消 | ✅ | ⚠️ 需用 AbortController | 中 |
| 超时控制 | ✅ | ✅ | 低 |

**风险评估：** 低风险，核心功能可完整迁移

### 3.2 状态管理迁移评估

**当前实现：**
- Pinia 状态管理
- localStorage 持久化
- useUserStore / useMenuStore

**迁移方案：**
- Pinia 直接复用
- localStorage → uni.setStorage / uni.getStorage
- 创建统一的存储适配层

**兼容性评估：**

| 功能 | Web API | uni-app API | 迁移难度 |
|------|---------|-------------|---------|
| 同步存储 | localStorage | uni.setStorageSync | 低 |
| 异步存储 | - | uni.setStorage | 低 |
| 存储监听 | storage事件 | 需手动实现 | 中 |

**风险评估：** 低风险，仅需适配存储API

### 3.3 组件迁移评估

**公共组件：**

| 组件 | 功能 | 迁移方案 | 难度 |
|------|------|---------|------|
| Layout.vue | 布局容器 | 拆分为PC侧边栏/移动端TabBar | 高 |
| NavBar.vue | 导航栏 | 使用 uni-app 内置导航栏 | 低 |
| EmptyState.vue | 空状态 | 直接迁移 | 低 |
| Skeleton.vue | 骨架屏 | 直接迁移 | 低 |

**UI组件（Vant）：**

| 组件 | H5版本 | 小程序版本 | 兼容性 |
|------|--------|-----------|--------|
| Button | van-button | van-button | ✅ 完全兼容 |
| Field | van-field | van-field | ✅ 完全兼容 |
| Popup | van-popup | van-popup | ✅ 完全兼容 |
| Toast | showToast | uni.showToast | ⚠️ API略有差异 |
| Dialog | showDialog | uni.showModal | ⚠️ API略有差异 |
| Picker | van-picker | van-picker | ✅ 完全兼容 |
| DatetimePicker | van-datetime-picker | van-datetime-picker | ✅ 完全兼容 |
| SwipeCell | van-swipe-cell | van-swipe-cell | ✅ 完全兼容 |
| PullRefresh | van-pull-refresh | uni.startPullDownRefresh | ⚠️ 使用原生下拉刷新 |
| List | van-list | uni.onReachBottom | ⚠️ 使用原生触底加载 |

**风险评估：** 中风险，部分组件需适配

### 3.4 组合式函数迁移评估

| Composable | 功能 | 迁移方案 | 难度 |
|------------|------|---------|------|
| useDict | 字典服务 | 迁移存储API | 低 |
| useTheme | 主题服务 | 迁移CSS变量 | 中 |
| useLayout | 布局判断 | 使用 uni.getSystemInfo | 低 |
| usePermission | 权限判断 | 直接复用 | 低 |

**风险评估：** 低风险，核心逻辑可复用

### 3.5 图表迁移评估

**当前实现：**
- vue-echarts 组件
- 首页仪表盘图表

**迁移方案：**
- 小程序端使用 echarts-for-weixin
- H5/APP 继续使用 vue-echarts
- 条件编译区分平台

**风险评估：** 中风险，需处理平台差异

---

## 四、后端适配评估

### 4.1 现有API兼容性

| API模块 | 端点数量 | 兼容性 | 改动需求 |
|---------|---------|--------|---------|
| /api/auth | 6 | ✅ 完全兼容 | 新增微信登录接口 |
| /api/products | 8 | ✅ 完全兼容 | 无 |
| /api/categories | 5 | ✅ 完全兼容 | 无 |
| /api/origins | 5 | ✅ 完全兼容 | 无 |
| /api/customers | 5 | ✅ 完全兼容 | 无 |
| /api/users | 5 | ✅ 完全兼容 | 无 |
| /api/menu | 4 | ✅ 完全兼容 | 无 |
| /api/logs | 2 | ✅ 完全兼容 | 无 |
| /api/approval | 6 | ✅ 完全兼容 | 无 |
| /api/dict | 3 | ✅ 完全兼容 | 无 |
| /api/style | 3 | ✅ 完全兼容 | 无 |

### 4.2 新增接口需求

#### 微信登录接口

```
POST /api/auth/wechat-login

请求体：
{
  "code": "微信授权码",
  "encryptedData": "加密数据",
  "iv": "初始向量"
}

响应体：
{
  "code": 200,
  "data": {
    "accessToken": "...",
    "refreshToken": "...",
    "tokenType": "Bearer",
    "expiresIn": 86400,
    "user": { ... }
  }
}
```

**后端改动：**
1. 新增 WechatAuthController
2. 新增 WechatService（调用微信API获取openid）
3. 用户表新增 wechat_openid 字段
4. 支持微信账号与现有账号绑定

### 4.3 数据库改动

```sql
-- 用户表新增微信openid字段
ALTER TABLE user ADD COLUMN wechat_openid VARCHAR(64) DEFAULT NULL COMMENT '微信openid';
ALTER TABLE user ADD UNIQUE INDEX idx_wechat_openid (wechat_openid);
```

**风险评估：** 低风险，后端改动较小

---

## 五、小程序特有功能评估

### 5.1 登录流程差异

**H5登录流程：**
```
用户输入账号密码 → 调用登录API → 获取Token → 存储Token → 跳转首页
```

**小程序登录流程：**
```
用户点击微信登录 → 获取code → 调用微信登录API → 获取Token → 存储Token → 跳转首页
```

**适配方案：**
- 保留账号密码登录（用于管理员）
- 新增微信一键登录（用于普通用户）
- 支持微信账号绑定已有账号

### 5.2 分享功能

**小程序分享：**
```javascript
// 页面内定义分享
onShareAppMessage() {
  return {
    title: '产品价格详情',
    path: '/pages/products/detail?id=xxx',
    imageUrl: '/static/share.png'
  }
}
```

**适配方案：**
- 产品详情页支持分享
- 首页支持分享
- 分享图片自动生成或使用默认图

### 5.3 支付功能

**当前系统：** 无支付功能

**未来需求：** 如需支付，需接入微信支付

**适配方案：**
- 后端新增支付模块
- 前端调用 uni.requestPayment

### 5.4 推送通知

**小程序订阅消息：**
- 价格变动通知
- 审批结果通知
- 系统公告通知

**适配方案：**
- 后端接入微信订阅消息
- 前端调用 uni.requestSubscribeMessage

---

## 六、包体积与性能评估

### 6.1 小程序包体积限制

| 类型 | 限制 | 主包预估 | 分包策略 |
|------|------|---------|---------|
| 主包 | 2MB | 1.5MB | 核心功能 |
| 分包 | 20MB | - | 管理功能 |
| 单个分包 | 2MB | - | 按功能模块 |

### 6.2 分包规划

```
主包（核心功能）：
├── pages/login          # 登录
├── pages/home           # 首页
├── pages/products       # 产品列表/详情
└── pages/profile        # 个人中心

分包A（价格管理）：
├── pages/price-maintenance
└── pages/product-edit

分包B（基础数据）：
├── pages/categories
├── pages/origins
└── pages/customers

分包C（审批流程）：
└── pages/approval

分包D（管理功能，可选）：
├── pages/users
├── pages/menu-config
└── pages/logs
```

### 6.3 性能优化策略

| 优化项 | 方案 |
|--------|------|
| 图片优化 | 使用 CDN + webp 格式 + 懒加载 |
| 代码分割 | 分包加载 + 按需注入 |
| 请求优化 | 数据缓存 + 请求合并 |
| 渲染优化 | 虚拟列表 + 骨架屏 |

---

## 七、风险评估与应对

### 7.1 技术风险

| 风险 | 等级 | 影响 | 应对措施 |
|------|------|------|---------|
| Vant组件API差异 | 中 | 部分组件需适配 | 提前调研，准备替代方案 |
| ECharts小程序兼容 | 中 | 图表功能受限 | 使用 echarts-for-weixin，简化图表 |
| 路由守卫迁移 | 高 | 权限控制需重写 | 使用 uni-app 路由拦截器 |
| 包体积超限 | 中 | 无法发布 | 分包加载，移除非必要功能 |

### 7.2 业务风险

| 风险 | 等级 | 影响 | 应对措施 |
|------|------|------|---------|
| 微信登录对接 | 中 | 用户无法登录 | 提前申请小程序，测试登录流程 |
| 功能裁剪 | 低 | 用户体验差异 | 明确小程序定位，核心功能优先 |
| 审核风险 | 低 | 上线延迟 | 提前了解审核规范，避免违规 |

### 7.3 运维风险

| 风险 | 等级 | 影响 | 应对措施 |
|------|------|------|---------|
| 多端发布 | 低 | 发布流程复杂 | 建立标准化发布流程 |
| 版本同步 | 中 | 功能不一致 | 统一需求管理，同步迭代 |
| 监控告警 | 低 | 问题发现延迟 | 接入小程序监控平台 |

---

## 八、实施步骤

### Phase 1：项目初始化

#### 步骤 1.1：创建 uni-app 项目

**操作：**
```bash
# 使用 Vue3 + TypeScript 模板创建项目
npx degit dcloudio/uni-preset-vue#vite-ts price-management-uniapp

# 安装依赖
cd price-management-uniapp
npm install
```

**产出：**
- uni-app 项目骨架
- TypeScript 配置
- Vite 构建配置

#### 步骤 1.2：配置多端编译

**操作：**
编辑 `manifest.json`，配置应用信息：
```json
{
  "name": "矿产品价格管理系统",
  "appid": "__UNI__XXXXXX",
  "description": "企业价格展示与管理平台",
  "versionName": "1.0.0",
  "versionCode": "100",
  "transformPx": false,
  "app-plus": {
    "usingComponents": true,
    "splashscreen": { ... }
  },
  "mp-weixin": {
    "appid": "微信小程序AppID",
    "setting": {
      "urlCheck": false,
      "es6": true,
      "postcss": true,
      "minified": true
    },
    "usingComponents": true
  },
  "h5": {
    "title": "矿产品价格管理系统",
    "router": { "mode": "history" }
  }
}
```

**产出：**
- H5/APP/小程序三端配置

#### 步骤 1.3：安装依赖

**操作：**
```bash
# 安装 Pinia
npm install pinia

# 安装 Vant 小程序版
npm install @vant/weapp

# 安装工具库
npm install dayjs

# 安装 ECharts 小程序版
npm install echarts-for-weixin
```

**产出：**
- 项目依赖安装完成

#### 步骤 1.4：迁移 TypeScript 类型

**操作：**
1. 复制 `frontend/src/types/` 目录到 `src/types/`
2. 检查类型定义，移除 Web 特有类型（如 Window）

**产出：**
- 类型定义迁移完成

---

### Phase 2：核心层迁移

#### 步骤 2.1：封装请求模块

**操作：**
创建 `src/api/request.ts`：

```typescript
import { useUserStore } from '@/store/useUserStore'

const BASE_URL = import.meta.env.VITE_API_BASE_URL || ''

interface RequestOptions {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  data?: any
  header?: Record<string, string>
  showLoading?: boolean
  showError?: boolean
}

interface ApiResponse<T = any> {
  code: number
  data: T
  message: string
}

// Token 刷新队列
let isRefreshing = false
let refreshQueue: Array<(token: string) => void> = []

export const request = async <T = any>(options: RequestOptions): Promise<ApiResponse<T>> => {
  const userStore = useUserStore()

  return new Promise((resolve, reject) => {
    // 显示加载提示
    if (options.showLoading !== false) {
      uni.showLoading({ title: '加载中...', mask: true })
    }

    uni.request({
      url: BASE_URL + options.url,
      method: options.method || 'GET',
      data: options.data,
      header: {
        'Content-Type': 'application/json',
        'Authorization': userStore.token ? `Bearer ${userStore.token}` : '',
        ...options.header
      },
      success: async (res) => {
        uni.hideLoading()

        const data = res.data as ApiResponse<T>

        // 处理 401 Token 过期
        if (res.statusCode === 401 && !options.url.includes('/auth/login')) {
          if (isRefreshing) {
            // 加入等待队列
            refreshQueue.push((token: string) => {
              options.header = { ...options.header, Authorization: `Bearer ${token}` }
              request(options).then(resolve).catch(reject)
            })
            return
          }

          isRefreshing = true
          try {
            const newToken = await userStore.refreshAccessToken()
            if (newToken) {
              // 重试队列中的请求
              refreshQueue.forEach(cb => cb(newToken))
              refreshQueue = []
              // 重试当前请求
              options.header = { ...options.header, Authorization: `Bearer ${newToken}` }
              request(options).then(resolve).catch(reject)
            } else {
              userStore.logoutAction()
              uni.reLaunch({ url: '/pages/login/index' })
              reject(new Error('Token刷新失败'))
            }
          } catch (error) {
            userStore.logoutAction()
            uni.reLaunch({ url: '/pages/login/index' })
            reject(error)
          } finally {
            isRefreshing = false
          }
          return
        }

        if (data.code === 200) {
          resolve(data)
        } else {
          if (options.showError !== false) {
            uni.showToast({ title: data.message || '请求失败', icon: 'none' })
          }
          reject(new Error(data.message))
        }
      },
      fail: (error) => {
        uni.hideLoading()
        if (options.showError !== false) {
          uni.showToast({ title: '网络错误', icon: 'none' })
        }
        reject(error)
      }
    })
  })
}

// 便捷方法
export const get = <T = any>(url: string, data?: any) =>
  request<T>({ url, method: 'GET', data })

export const post = <T = any>(url: string, data?: any) =>
  request<T>({ url, method: 'POST', data })

export const put = <T = any>(url: string, data?: any) =>
  request<T>({ url, method: 'PUT', data })

export const del = <T = any>(url: string, data?: any) =>
  request<T>({ url, method: 'DELETE', data })
```

**产出：**
- 统一请求封装
- Token 自动刷新
- 错误统一处理

#### 步骤 2.2：迁移状态管理

**操作：**

1. 创建 `src/store/useUserStore.ts`：

```typescript
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { post, get } from '@/api/request'
import type { User, LoginRequest, LoginResponse } from '@/types'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>('')
  const refreshToken = ref<string>('')
  const user = ref<User | null>(null)

  const isAuthenticated = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.role === 'ADMIN')
  const canEdit = computed(() => ['ADMIN', 'EDITOR'].includes(user.value?.role || ''))

  // 从本地存储恢复状态
  const restoreSession = () => {
    token.value = uni.getStorageSync('token') || ''
    refreshToken.value = uni.getStorageSync('refreshToken') || ''
    const userStr = uni.getStorageSync('user')
    if (userStr) {
      user.value = JSON.parse(userStr)
    }
  }

  // 保存状态到本地存储
  const saveSession = (data: LoginResponse) => {
    token.value = data.accessToken
    refreshToken.value = data.refreshToken
    user.value = data.user

    uni.setStorageSync('token', data.accessToken)
    uni.setStorageSync('refreshToken', data.refreshToken)
    uni.setStorageSync('user', JSON.stringify(data.user))
  }

  // 登录
  const loginAction = async (credentials: LoginRequest): Promise<boolean> => {
    try {
      const res = await post<LoginResponse>('/api/auth/login', credentials, { showLoading: false })
      if (res.code === 200 && res.data) {
        saveSession(res.data)
        return true
      }
      return false
    } catch (error) {
      return false
    }
  }

  // 微信登录
  const wechatLoginAction = async (code: string): Promise<boolean> => {
    try {
      const res = await post<LoginResponse>('/api/auth/wechat-login', { code })
      if (res.code === 200 && res.data) {
        saveSession(res.data)
        return true
      }
      return false
    } catch (error) {
      return false
    }
  }

  // 刷新 Token
  const refreshAccessToken = async (): Promise<string | null> => {
    try {
      const res = await post<{ accessToken: string; refreshToken: string }>(
        '/api/auth/refresh-token',
        { refreshToken: refreshToken.value },
        { showLoading: false, showError: false }
      )
      if (res.code === 200 && res.data) {
        token.value = res.data.accessToken
        refreshToken.value = res.data.refreshToken
        uni.setStorageSync('token', res.data.accessToken)
        uni.setStorageSync('refreshToken', res.data.refreshToken)
        return res.data.accessToken
      }
      return null
    } catch (error) {
      return null
    }
  }

  // 登出
  const logoutAction = () => {
    token.value = ''
    refreshToken.value = ''
    user.value = null
    uni.removeStorageSync('token')
    uni.removeStorageSync('refreshToken')
    uni.removeStorageSync('user')
  }

  // 获取用户信息
  const fetchProfile = async () => {
    const res = await get<User>('/api/auth/profile')
    if (res.code === 200 && res.data) {
      user.value = res.data
      uni.setStorageSync('user', JSON.stringify(res.data))
    }
  }

  return {
    token,
    refreshToken,
    user,
    isAuthenticated,
    isAdmin,
    canEdit,
    restoreSession,
    saveSession,
    loginAction,
    wechatLoginAction,
    refreshAccessToken,
    logoutAction,
    fetchProfile
  }
})
```

2. 创建 `src/store/useMenuStore.ts`（从原项目迁移，适配存储API）

**产出：**
- Pinia 状态管理迁移完成
- 存储API适配完成

#### 步骤 2.3：迁移组合式函数

**操作：**

1. 迁移 `useDict.ts`，将 `localStorage` 替换为 `uni.getStorageSync` / `uni.setStorageSync`

2. 迁移 `useTheme.ts`，适配 CSS 变量

3. 迁移 `useLayout.ts`，使用 `uni.getSystemInfoSync()` 判断平台

4. 迁移 `usePermission.ts`，直接复用

**产出：**
- 核心组合式函数迁移完成

#### 步骤 2.4：迁移 API 接口

**操作：**
1. 复制 `frontend/src/api/` 目录到 `src/api/`
2. 修改所有 API 文件，将 `http.post/get/put/delete` 替换为新的 `request` 封装
3. 保持接口签名不变

**产出：**
- API 接口层迁移完成

---

### Phase 3：页面迁移

#### 步骤 3.1：配置页面路由

**操作：**
创建 `src/pages.json`：

```json
{
  "pages": [
    {
      "path": "pages/login/index",
      "style": { "navigationBarTitleText": "登录", "navigationStyle": "custom" }
    },
    {
      "path": "pages/home/index",
      "style": { "navigationBarTitleText": "首页" }
    },
    {
      "path": "pages/products/list",
      "style": { "navigationBarTitleText": "产品列表", "enablePullDownRefresh": true }
    },
    {
      "path": "pages/products/detail",
      "style": { "navigationBarTitleText": "产品详情" }
    },
    {
      "path": "pages/products/edit",
      "style": { "navigationBarTitleText": "产品编辑" }
    },
    {
      "path": "pages/price-maintenance/index",
      "style": { "navigationBarTitleText": "价格维护" }
    },
    {
      "path": "pages/profile/index",
      "style": { "navigationBarTitleText": "个人中心" }
    }
  ],
  "subPackages": [
    {
      "root": "pages-sub/basic",
      "pages": [
        { "path": "categories/index", "style": { "navigationBarTitleText": "分类管理" } },
        { "path": "origins/index", "style": { "navigationBarTitleText": "产地管理" } },
        { "path": "customers/index", "style": { "navigationBarTitleText": "客户管理" } }
      ]
    },
    {
      "root": "pages-sub/approval",
      "pages": [
        { "path": "index", "style": { "navigationBarTitleText": "审批管理" } }
      ]
    }
  ],
  "tabBar": {
    "color": "#999999",
    "selectedColor": "#0D6E6E",
    "backgroundColor": "#FFFFFF",
    "borderStyle": "black",
    "list": [
      { "pagePath": "pages/home/index", "text": "首页", "iconPath": "static/tabbar/home.png", "selectedIconPath": "static/tabbar/home-active.png" },
      { "pagePath": "pages/products/list", "text": "产品", "iconPath": "static/tabbar/product.png", "selectedIconPath": "static/tabbar/product-active.png" },
      { "pagePath": "pages/profile/index", "text": "我的", "iconPath": "static/tabbar/profile.png", "selectedIconPath": "static/tabbar/profile-active.png" }
    ]
  },
  "globalStyle": {
    "navigationBarTextStyle": "black",
    "navigationBarTitleText": "价格管理系统",
    "navigationBarBackgroundColor": "#FFFFFF",
    "backgroundColor": "#F5F5F5"
  }
}
```

**产出：**
- 页面路由配置完成
- TabBar 配置完成
- 分包配置完成

#### 步骤 3.2：迁移登录页

**操作：**
创建 `src/pages/login/index.vue`：

```vue
<template>
  <view class="login-page">
    <!-- 账号密码登录 -->
    <view class="login-form" v-if="loginMode === 'account'">
      <view class="form-title">账号登录</view>
      <van-field v-model="form.username" placeholder="请输入用户名" />
      <van-field v-model="form.password" type="password" placeholder="请输入密码" />
      <van-button type="primary" block @click="handleLogin">登录</van-button>
      <view class="switch-mode" @click="loginMode = 'wechat'">微信登录</view>
    </view>

    <!-- 微信登录 -->
    <view class="wechat-login" v-else>
      <view class="form-title">微信登录</view>
      <van-button type="primary" block open-type="getPhoneNumber" @getphonenumber="handleWechatLogin">
        <van-icon name="wechat" /> 微信一键登录
      </van-button>
      <view class="switch-mode" @click="loginMode = 'account'">账号密码登录</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useUserStore } from '@/store/useUserStore'

const userStore = useUserStore()
const loginMode = ref<'account' | 'wechat'>('account')
const form = ref({ username: '', password: '' })

const handleLogin = async () => {
  if (!form.value.username || !form.value.password) {
    uni.showToast({ title: '请输入用户名和密码', icon: 'none' })
    return
  }
  const success = await userStore.loginAction(form.value)
  if (success) {
    uni.switchTab({ url: '/pages/home/index' })
  }
}

const handleWechatLogin = async (e: any) => {
  if (e.detail.code) {
    const success = await userStore.wechatLoginAction(e.detail.code)
    if (success) {
      uni.switchTab({ url: '/pages/home/index' })
    }
  }
}
</script>
```

**产出：**
- 登录页迁移完成
- 支持账号密码登录和微信登录

#### 步骤 3.3：迁移首页

**操作：**
创建 `src/pages/home/index.vue`：
- 迁移仪表盘统计数据
- 迁移图表（使用 echarts-for-weixin）
- 迁移快捷入口

**产出：**
- 首页迁移完成

#### 步骤 3.4：迁移产品列表

**操作：**
创建 `src/pages/products/list.vue`：
- 迁移产品列表展示
- 迁移搜索筛选
- 迁移下拉刷新和触底加载

**产出：**
- 产品列表页迁移完成

#### 步骤 3.5：迁移产品详情

**操作：**
创建 `src/pages/products/detail.vue`：
- 迁移产品信息展示
- 迁移价格历史图表
- 添加分享功能

**产出：**
- 产品详情页迁移完成

#### 步骤 3.6：迁移其他页面

**操作：**
按优先级依次迁移：
1. 价格维护页
2. 个人中心页
3. 分类管理页
4. 产地管理页
5. 客户管理页
6. 审批管理页

**产出：**
- 所有必需页面迁移完成

---

### Phase 4：组件适配

#### 步骤 4.1：适配布局组件

**操作：**
- 小程序端使用 TabBar 替代侧边栏
- 使用 uni-app 内置导航栏
- 移除 PC 布局相关代码

**产出：**
- 布局适配完成

#### 步骤 4.2：适配 UI 组件

**操作：**
1. 配置 vant-weapp：
```json
// pages.json
{
  "usingComponents": {
    "van-button": "@vant/weapp/button/index",
    "van-field": "@vant/weapp/field/index",
    "van-cell": "@vant/weapp/cell/index",
    "van-cell-group": "@vant/weapp/cell-group/index",
    "van-popup": "@vant/weapp/popup/index",
    "van-picker": "@vant/weapp/picker/index",
    "van-datetime-picker": "@vant/weapp/datetime-picker/index",
    "van-swipe-cell": "@vant/weapp/swipe-cell/index"
  }
}
```

2. 替换 Toast/Dialog 为 uni-app 原生 API

**产出：**
- UI 组件适配完成

#### 步骤 4.3：适配图表组件

**操作：**
1. 安装 echarts-for-weixin
2. 创建图表组件封装
3. 使用条件编译区分 H5 和小程序

**产出：**
- 图表组件适配完成

---

### Phase 5：小程序特有功能

#### 步骤 5.1：实现微信登录

**操作：**
1. 后端新增微信登录接口
2. 前端调用 `wx.login` 获取 code
3. 调用后端接口换取 token

**产出：**
- 微信登录功能完成

#### 步骤 5.2：实现分享功能

**操作：**
在需要分享的页面添加：
```typescript
onShareAppMessage(() => ({
  title: '产品价格详情',
  path: `/pages/products/detail?id=${productId.value}`,
  imageUrl: '/static/share.png'
}))
```

**产出：**
- 分享功能完成

#### 步骤 5.3：实现订阅消息

**操作：**
1. 在微信后台申请订阅消息模板
2. 前端调用 `uni.requestSubscribeMessage`
3. 后端发送订阅消息

**产出：**
- 订阅消息功能完成

---

### Phase 6：后端适配

#### 步骤 6.1：新增微信登录接口

**操作：**

1. 创建 `WechatAuthController.java`：
```java
@RestController
@RequestMapping("/api/auth")
public class WechatAuthController {

    @PostMapping("/wechat-login")
    public Result<LoginResponse> wechatLogin(@RequestBody WechatLoginRequest request) {
        // 调用微信API获取openid
        // 查找或创建用户
        // 生成token
        // 返回登录响应
    }
}
```

2. 创建 `WechatService.java`：
```java
@Service
public class WechatService {

    public String getOpenId(String code) {
        // 调用微信 code2Session 接口
        // 返回 openid
    }
}
```

3. 修改 User 实体，新增 `wechatOpenid` 字段

**产出：**
- 微信登录接口完成

#### 步骤 6.2：数据库变更

**操作：**
执行 SQL：
```sql
ALTER TABLE user ADD COLUMN wechat_openid VARCHAR(64) DEFAULT NULL COMMENT '微信openid';
ALTER TABLE user ADD UNIQUE INDEX idx_wechat_openid (wechat_openid);
```

**产出：**
- 数据库变更完成

---

### Phase 7：测试与发布

#### 步骤 7.1：功能测试

**操作：**
1. H5 端功能测试
2. 微信小程序功能测试
3. APP 端功能测试（如需）
4. 兼容性测试（iOS/Android）

**产出：**
- 测试报告

#### 步骤 7.2：性能优化

**操作：**
1. 检查包体积，确保不超过限制
2. 优化首屏加载时间
3. 优化图片资源

**产出：**
- 性能优化完成

#### 步骤 7.3：发布上线

**操作：**
1. 提交微信小程序审核
2. 部署 H5 版本
3. 打包 APP（如需）

**产出：**
- 多端版本上线

---

## 九、附录

### 9.1 目录结构

```
price-management-uniapp/
├── src/
│   ├── api/                    # API接口层
│   │   ├── request.ts          # 请求封装
│   │   ├── auth.ts             # 认证接口
│   │   ├── products.ts         # 产品接口
│   │   └── ...
│   ├── components/             # 公共组件
│   │   ├── EmptyState.vue
│   │   ├── Skeleton.vue
│   │   └── ...
│   ├── composables/            # 组合式函数
│   │   ├── useDict.ts
│   │   ├── useTheme.ts
│   │   ├── useLayout.ts
│   │   └── usePermission.ts
│   ├── pages/                  # 主包页面
│   │   ├── login/
│   │   ├── home/
│   │   ├── products/
│   │   ├── price-maintenance/
│   │   └── profile/
│   ├── pages-sub/              # 分包页面
│   │   ├── basic/              # 基础数据分包
│   │   └── approval/           # 审批分包
│   ├── static/                 # 静态资源
│   │   ├── tabbar/             # TabBar图标
│   │   └── images/
│   ├── store/                  # 状态管理
│   │   ├── useUserStore.ts
│   │   └── useMenuStore.ts
│   ├── types/                  # TypeScript类型
│   ├── utils/                  # 工具函数
│   ├── App.vue                 # 应用入口
│   ├── main.ts                 # 主入口
│   ├── manifest.json           # 应用配置
│   ├── pages.json              # 页面配置
│   └── uni.scss                # 全局样式
├── unpackage/                  # 编译输出
├── package.json
└── tsconfig.json
```

### 9.2 环境变量配置

```bash
# .env.development
VITE_API_BASE_URL=http://localhost:8080

# .env.production
VITE_API_BASE_URL=https://api.example.com
```

### 9.3 常用命令

```bash
# 开发
npm run dev:h5          # H5开发
npm run dev:mp-weixin   # 小程序开发
npm run dev:app         # APP开发

# 构建
npm run build:h5        # H5构建
npm run build:mp-weixin # 小程序构建
npm run build:app       # APP构建
```

### 9.4 参考资源

- [uni-app 官方文档](https://uniapp.dcloud.net.cn/)
- [Vant Weapp 文档](https://vant-contrib.gitee.io/vant-weapp/)
- [echarts-for-weixin](https://github.com/ecomfe/echarts-for-weixin)
- [微信小程序登录流程](https://developers.weixin.qq.com/miniprogram/dev/framework/open-ability/login.html)
