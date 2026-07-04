# 15. 本项目前端结构：双前端 + 字典服务

> 本项目有 **两套前端**（H5 + uni-app），它们怎么和后端协作？字典服务是什么？

---

## 一、为什么有两套前端？

```
用户群
  │
  ├── 桌面 / 手机浏览器 → frontend/         （Vue3 + Element Plus）
  │   企业员工在 PC 上用
  │
  └── 微信小程序 / APP → frontend-uniapp/   （uni-app + Vue3）
      领导 / 客户在手机上用
```

| 前端 | 技术栈 | 用户 | UI 库 |
|------|--------|------|------|
| **frontend/** | Vue3 + Vite + TS | 内部员工（PC 浏览器） | Element Plus |
| **frontend-uniapp/** | uni-app + Vue3 + TS | 领导/客户（小程序） | 自定义 |

**两套前端共用后端 API**——前端只是"不同的壳"。

## 二、H5 前端结构（frontend/）

```
frontend/
├── public/                       静态资源
├── src/
│   ├── api/                      API 接口（按后端模块分）
│   │   ├── auth.ts
│   │   ├── product.ts
│   │   ├── price.ts
│   │   └── ...
│   ├── assets/                   图片、字体
│   ├── components/               公共组件
│   │   ├── ProductCard.vue
│   │   └── PriceChart.vue
│   ├── composables/              组合式函数（重点）
│   │   ├── useDict.ts            ← 字典服务（必读）
│   │   ├── useAuth.ts
│   │   └── useTable.ts
│   ├── views/                    页面（按路由分）
│   │   ├── Home.vue
│   │   ├── product/
│   │   │   ├── ProductList.vue
│   │   │   └── ProductEdit.vue
│   │   └── ...
│   ├── router/                   路由配置
│   │   └── index.ts
│   ├── stores/                   Pinia 状态
│   │   ├── user.ts
│   │   └── app.ts
│   ├── types/                    TypeScript 类型
│   │   ├── product.ts
│   │   ├── common.ts
│   │   └── ...
│   ├── utils/                    工具函数
│   ├── layouts/                  布局组件
│   ├── styles/                   全局样式
│   ├── App.vue
│   └── main.ts
├── package.json
└── vite.config.ts
```

## 三、uni-app 前端结构（frontend-uniapp/）

```
frontend-uniapp/
├── src/
│   ├── api/                      ← 和 H5 共用接口签名
│   ├── components/               ← 复用组件
│   ├── composables/
│   │   └── useDict.ts            ← 同样的字典服务
│   ├── pages/                    主包页面
│   │   ├── index/
│   │   └── ...
│   ├── pages-sub/                分包页面
│   │   └── product/
│   ├── store/                    Pinia
│   ├── types/                    ← 复用 H5 的类型
│   ├── App.vue
│   ├── main.ts
│   ├── manifest.json             ← uni-app 配置
│   └── pages.json                ← 页面路由配置
└── package.json
```

**关键约定**：两套前端的 **api/、types/、composables/useDict.ts 保持一致**——业务逻辑只写一次。

## 四、字典服务（useDict）

### 4.1 为什么需要字典服务？

**问题**：状态码 `ACTIVE` / `DISABLED` 来自后端，前端怎么显示"启用"/"停用"？

**❌ 错误做法**：

```vue
<span>{{ product.status === 'ACTIVE' ? '启用' : '停用' }}</span>
```

- 硬编码中文标签（违反项目规范）
- 后端新增状态码，要改前端
- 多语言无法支持

**✅ 正确做法**：通过字典服务动态获取。

### 4.2 useDict 实现

`src/composables/useDict.ts`：

```typescript
import { ref, computed } from 'vue';

export type DictItem = {
    label: string;
    value: string;
    color?: string;
};

// 字典缓存（按 category 缓存）
const dictCache = ref<Record<string, DictItem[]>>({});

export function useDict(category: string) {
    const items = computed(() => dictCache.value[category] || []);

    async function loadDict() {
        if (dictCache.value[category]) return;  // 已缓存
        const res = await request.get(`/api/dicts/${category}`);
        dictCache.value[category] = res.data;
    }

    function getLabel(value: string): string {
        return items.value.find(i => i.value === value)?.label || value;
    }

    return {
        items,
        loadDict,
        getLabel
    };
}

// 全局加载所有字典（在 App.vue 或 Layout 里）
export async function loadAllDicts() {
    const categories = ['product_status', 'user_role', 'price_type', ...];
    await Promise.all(categories.map(cat => loadDictByCategory(cat)));
}
```

### 4.3 使用

```vue
<template>
    <el-tag :type="getLabel(product.status) === '启用' ? 'success' : 'danger'">
        {{ getLabel(product.status) }}
    </el-tag>
</template>

<script setup>
import { useDict } from '@/composables/useDict';

const { items, loadDict, getLabel } = useDict('product_status');

onMounted(loadDict);
</script>
```

### 4.4 项目规范要点

**所有编码值的显示必须从字典服务获取**，禁止硬编码。详见 CLAUDE.md：

```vue
<!-- ❌ 禁止 -->
<span>{{ product.status === 'ACTIVE' ? '启用' : '停用' }}</span>

<!-- ✅ 正确 -->
<span>{{ getStatusLabel(product.status) }}</span>
```

## 五、API 封装

### 5.1 Axios 实例

`src/api/request.ts`：

```typescript
import axios, { type AxiosInstance } from 'axios';
import { ElMessage } from 'element-plus';
import { useUserStore } from '@/stores/user';
import router from '@/router';

const instance: AxiosInstance = axios.create({
    baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
    timeout: 15000
});

// 请求拦截器
instance.interceptors.request.use(config => {
    const userStore = useUserStore();
    if (userStore.token) {
        config.headers.Authorization = `Bearer ${userStore.token}`;
    }
    return config;
});

// 响应拦截器
instance.interceptors.response.use(
    response => {
        const data = response.data;
        // Result<T> 统一处理
        if (data.code === 200) {
            return data;     // 成功：返回完整 data（含 data 字段）
        }
        ElMessage.error(data.message || '请求失败');
        return Promise.reject(new Error(data.message));
    },
    error => {
        // 401：未登录
        if (error.response?.status === 401) {
            const userStore = useUserStore();
            userStore.logout();
            router.push('/login');
        }
        ElMessage.error(error.response?.data?.message || '网络错误');
        return Promise.reject(error);
    }
);

export default instance;
```

### 5.2 接口按模块组织

`src/api/product.ts`：

```typescript
import request from './request';
import type { Product, ProductDTO, Page } from '@/types/product';

export const productApi = {
    // 分页查询
    list: (params: { page: number; size: number; keyword?: string }) =>
        request.get<Page<Product>>('/products', { params }),

    // 详情
    getById: (id: number) =>
        request.get<Product>(`/products/${id}`),

    // 创建
    create: (data: ProductDTO) =>
        request.post<Product>('/products', data),

    // 更新
    update: (id: number, data: ProductDTO) =>
        request.put<Product>(`/products/${id}`, data),

    // 删除
    delete: (id: number) =>
        request.delete(`/products/${id}`)
};
```

### 5.3 跨域问题

**开发环境**：前端 `localhost:5173`，后端 `localhost:8080`，**不同端口 = 跨域**。

**后端 CORS 配置**（`SecurityConfig.java`）：

```java
@Configuration
public class CorsConfig {
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOriginPattern("http://localhost:5173");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
```

**或用 Vite 代理**（`vite.config.ts`）：

```typescript
export default defineConfig({
    server: {
        proxy: {
            '/api': {
                target: 'http://localhost:8080',
                changeOrigin: true
            }
        }
    }
});
```

## 六、统一响应格式

后端返回 `Result<T>`：

```json
{
    "code": 200,
    "message": "操作成功",
    "data": { "id": 1, "name": "铜精粉" },
    "timestamp": "2026-06-28 21:00:00"
}
```

前端 TS 类型：

```typescript
// src/types/common.ts
export interface Result<T> {
    code: number;
    message: string;
    data: T;
    timestamp: string;
}

export interface Page<T> {
    content: T[];
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
}
```

## 七、TypeScript 类型共享

**原则**：前后端字段一致，前端用 TS 接口定义。

`src/types/product.ts`：

```typescript
import type { Category } from './category';

export type ProductStatus = 'ACTIVE' | 'DISABLED';

export interface Product {
    id: number;
    name: string;
    price: number;
    status: ProductStatus;
    categoryId: number;
    categoryName?: string;
    remark?: string;
    createdTime: string;
    updatedTime: string;
}

export interface ProductDTO {
    id?: number;
    name: string;
    price: number;
    status: ProductStatus;
    categoryId: number;
    remark?: string;
}
```

**与后端对齐**：每次后端改字段，前端要同步。

## 八、路由与权限

`src/router/index.ts`：

```typescript
import { createRouter, createWebHistory } from 'vue-router';

const routes = [
    {
        path: '/',
        component: () => import('@/layouts/MainLayout.vue'),
        meta: { requiresAuth: true },
        children: [
            { path: '', name: 'Home', component: () => import('@/views/Home.vue') },
            {
                path: 'products',
                name: 'ProductList',
                component: () => import('@/views/product/ProductList.vue'),
                meta: { title: '产品列表' }
            },
            // ...
        ]
    },
    { path: '/login', component: () => import('@/views/Login.vue') }
];

const router = createRouter({
    history: createWebHistory(),
    routes
});

// 权限守卫
router.beforeEach((to, from, next) => {
    const userStore = useUserStore();
    if (to.meta.requiresAuth && !userStore.token) {
        next({ path: '/login', query: { redirect: to.fullPath } });
    } else {
        next();
    }
});

export default router;
```

## 九、菜单与权限

后端根据用户角色返回可见菜单，前端动态生成菜单。

```typescript
// stores/app.ts
export const useAppStore = defineStore('app', () => {
    const menus = ref<Menu[]>([]);

    async function loadMenus() {
        const res = await request.get<Menu[]>('/menus');
        menus.value = res.data;
    }

    return { menus, loadMenus };
});
```

```vue
<!-- MainLayout.vue -->
<template>
    <el-menu>
        <template v-for="menu in menus" :key="menu.id">
            <el-menu-item v-if="!menu.children" :index="menu.path">
                {{ menu.title }}
            </el-menu-item>
            <el-sub-menu v-else :index="menu.path">
                <template #title>{{ menu.title }}</template>
                <el-menu-item v-for="child in menu.children"
                              :key="child.id" :index="child.path">
                    {{ child.title }}
                </el-menu-item>
            </el-sub-menu>
        </template>
    </el-menu>
</template>
```

## 十、uni-app 特有的部分

### 10.1 跨端编译

uni-app 一份代码可编译成多个端：

```
src/
  ↓
  ├── H5: npm run dev:h5           → 浏览器
  ├── 微信小程序: npm run dev:mp-weixin  → 微信开发者工具
  ├── APP: npm run dev:app          → 真机 / 模拟器
  └── 其他: 抖音/支付宝/百度小程序
```

### 10.2 条件编译

```vue
<!-- #ifdef H5 -->
<view>只在 H5 显示</view>
<!-- #endif -->

<!-- #ifdef MP-WEIXIN -->
<view>只在微信小程序显示</view>
<!-- #endif -->
```

```javascript
// #ifdef H5
import axios from 'axios';
// #endif

// #ifdef MP-WEIXIN
import http from '@/utils/wechat-http';
// #endif
```

### 10.3 页面跳转

```javascript
// H5 用 vue-router，uni-app 用 uni API
uni.navigateTo({ url: '/pages/product/detail?id=1' });
uni.switchTab({ url: '/pages/home/index' });
uni.redirectTo({ url: '/pages/login/login' });
```

## 十一、统一端口架构

**项目亮点**：PC 端和小程序**共用 32080 端口**——智能内外网切换。

详见项目记忆 `unified_port_architecture.md`：

```
用户访问 http://公司域名:32080/
  → 内网：直接显示 H5 页面
  → 外网：检测 UA，如果微信内置浏览器，显示小程序码
```

这是本项目**很巧妙的部署方案**。

## 十二、本项目前端规范要点

### 12.1 命名规范

| 类型 | 规范 | 例子 |
|------|------|------|
| 组件文件 | 大驼峰 | `ProductCard.vue` |
| 普通文件 | kebab-case | `use-dict.ts` |
| 变量/函数 | 驼峰 | `productList`, `loadProducts` |
| 常量 | 全大写下划线 | `MAX_PAGE_SIZE` |
| CSS 类名 | kebab-case | `product-card` |
| 组件名 | 大驼峰 | `<ProductCard>` |

### 12.2 目录组织

- 一个页面 = 一个目录（`views/product/ProductList.vue` + 内部组件）
- 公共组件放 `components/`
- 业务代码不写在 main.ts

### 12.3 导入顺序

```typescript
// 1. Vue 核心
import { ref, onMounted } from 'vue';

// 2. 第三方库
import axios from 'axios';
import { ElMessage } from 'element-plus';

// 3. 项目内
import { productApi } from '@/api/product';
import { useDict } from '@/composables/useDict';
import ProductCard from '@/components/ProductCard.vue';

// 4. 相对路径
import type { Product } from './types';
```

## 十三、动手试试

### 实验 1：跑起前端

```bash
cd frontend
npm install       # 第一次需要装依赖
npm run dev       # 启动开发服务器

# 浏览器打开 http://localhost:5173
```

### 实验 2：改个组件

打开 `frontend/src/views/product/ProductList.vue`，改一下表格列。

### 实验 3：调一次 API

浏览器 F12 → Console：

```javascript
// 在 userStore 有 token 的前提下
fetch('/api/products?page=1&size=5', {
    headers: { 'Authorization': 'Bearer ' + localStorage.getItem('token') }
}).then(r => r.json()).then(console.log);
```

### 实验 4：跑起 uni-app

```bash
cd frontend-uniapp
npm install
npm run dev:h5
# 或 npm run dev:mp-weixin，然后打开微信开发者工具
```

## 十四、常见错误

| 错误 | 原因 |
|------|------|
| 401 没跳登录 | 没装响应拦截器 |
| 跨域失败 | 后端 CORS 没配 / Vite proxy 没配 |
| 字典不显示 | 没调 `loadDict()` / 没在 `onMounted` 里调 |
| Token 过期还请求 | 没在拦截器里处理 401 |
| 表格不渲染 | `:key` 缺失或重复 |
| 类型报错 | TS 类型和后端不一致 |
| 字典硬编码 | 违反项目规范，code review 过不了 |

## 十五、关键认知

1. **本项目双前端共用业务逻辑**：API 类型、字典服务保持一致
2. **字典服务是强制规范**：禁止硬编码中文
3. **数据驱动视图**：改 ref，视图自动更新
4. **统一响应格式**：`Result<T>` 在前端响应拦截器里处理
5. **权限由后端控制**：前端只是显示/隐藏菜单
6. **TypeScript 不是负担**：IDE 自动补全让你写得更快

---

下一步：[16 性能调优入门](16-performance-tuning.md) →

回头补课：
- [14 Vue3 核心](14-vue3-essentials.md)
- [13 前端基础](13-frontend-basics.md)