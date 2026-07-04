# 14. Vue3 核心：组合式 API 与组件化思维

> 本项目用 Vue 3 + TypeScript + Vite。这一章讲**看懂本项目前端代码所需的最少 Vue 知识**。

---

## 一、Vue 是什么？

**Vue = 渐进式 JavaScript 框架**——让写网页变得简单。

**传统写法（Vanilla JS）的问题**：

```javascript
// 改一个数据，要手动改 5 处 DOM
products.forEach(p => {
    const div = document.createElement('div');
    div.innerHTML = `<h3>${p.name}</h3><p>${p.price}</p>`;
    document.body.appendChild(div);
});
```

**Vue 写法**：

```vue
<template>
    <div v-for="p in products" :key="p.id">
        <h3>{{ p.name }}</h3>
        <p>{{ p.price }}</p>
    </div>
</template>

<script setup>
const products = ref([
    { id: 1, name: '铜精粉', price: 5800 },
    { id: 2, name: '铅精粉', price: 1580 }
]);
</script>
```

**核心思想**：**数据驱动视图**——只管改数据，DOM 自动更新。

## 二、本项目前端技术栈

| 技术 | 作用 |
|------|------|
| **Vue 3** | 主框架 |
| **TypeScript** | JS 超集，加类型 |
| **Vite** | 构建工具（类似 Maven for 前端） |
| **Vue Router** | 路由（页面跳转） |
| **Pinia** | 状态管理（Vuex 替代品） |
| **Element Plus** | UI 组件库（PC 端） |
| **ECharts** | 图表库 |
| **Axios** | HTTP 请求 |
| **uni-app** | 多端框架（编译成 H5/APP/小程序） |

## 三、第一个 Vue 组件

### 3.1 单文件组件（SFC）

后缀是 `.vue`，一个文件 = 一个组件：

```vue
<!-- HelloWorld.vue -->
<template>
    <!-- HTML：模板 -->
    <div class="hello">
        <h1>{{ message }}</h1>
        <button @click="count++">点 {{ count }} 次</button>
    </div>
</template>

<script setup>
// JS：逻辑
import { ref } from 'vue';

const message = ref('Hello Vue 3!');
const count = ref(0);
</script>

<style scoped>
/* CSS：样式（scoped 表示只作用于本组件） */
.hello {
    text-align: center;
    padding: 20px;
}
</style>
```

**三个部分**：

- `<template>`：HTML 结构
- `<script setup>`：JS 逻辑（Vue 3 Composition API）
- `<style scoped>`：CSS 样式（scoped = 局部作用域）

### 3.2 用组件

```vue
<template>
    <HelloWorld />
</template>

<script setup>
import HelloWorld from './components/HelloWorld.vue';
</script>
```

## 四、响应式：ref 与 reactive

**Vue 最核心的概念**：数据变 → 视图自动更新。

### 4.1 ref：基本类型响应式

```javascript
import { ref } from 'vue';

const count = ref(0);             // 创建响应式数据
console.log(count.value);         // 0（注意 .value）
count.value = 10;                 // 修改
```

**模板里不用 `.value`**：

```vue
<template>
    <p>{{ count }}</p>             <!-- 自动解包 -->
    <button @click="count++">+1</button>
</template>
```

### 4.2 reactive：对象响应式

```javascript
import { reactive } from 'vue';

const user = reactive({
    name: '张三',
    age: 18,
    address: { city: '北京' }
});

user.name = '李四';        // 改属性会自动更新
user.address.city = '上海'; // 嵌套也响应
```

### 4.3 ref vs reactive 怎么选？

| 类型 | 用 ref | 用 reactive |
|------|--------|-------------|
| 基本类型 | ✅ | ❌ |
| 对象 | ✅（推荐） | ✅ |
| 数组 | ✅（推荐） | ✅ |

**本项目实践**：几乎全用 `ref`，因为在 script 里更一致（统一 `.value`）。

## 五、computed：计算属性

```javascript
import { ref, computed } from 'vue';

const price = ref(5800);
const quantity = ref(2);

// 自动计算：依赖变就重算
const total = computed(() => price.value * quantity.value);

console.log(total.value);   // 11600
quantity.value = 3;
console.log(total.value);   // 17400（自动重算）
```

**模板里**：

```vue
<template>
    <p>总价：{{ total }}</p>
</template>
```

**特点**：有缓存，依赖不变就不重算。

## 六、watch：监听数据变化

```javascript
import { ref, watch } from 'vue';

const productId = ref(1);
const product = ref(null);

// 监听 productId 变化，重新加载
watch(productId, async (newId) => {
    const res = await fetch(`/api/products/${newId}`);
    product.value = await res.json();
});

// 立即执行一次
watch(productId, callback, { immediate: true });

// 深度监听对象内部变化
watch(product, (newVal) => {
    console.log('产品变了', newVal);
}, { deep: true });
```

## 七、生命周期

```javascript
import { onMounted, onUpdated, onUnmounted } from 'vue';

onMounted(() => {
    console.log('组件挂载完成，去拉数据');
    loadProducts();
});

onUpdated(() => {
    console.log('组件更新了');
});

onUnmounted(() => {
    console.log('组件卸载了，清清理定时器');
});
```

**最常用的就是 `onMounted`**——页面渲染完去拉数据。

## 八、模板语法

### 8.1 插值

```vue
<template>
    <p>{{ message }}</p>                          <!-- 文本 -->
    <p v-html="rawHtml"></p>                      <!-- HTML（小心 XSS） -->
    <p :id="dynamicId"></p>                       <!-- 绑定属性 -->
    <button :disabled="isDisabled">按钮</button>
</template>
```

### 8.2 条件渲染

```vue
<template>
    <div v-if="status === 'ACTIVE'">启用</div>
    <div v-else-if="status === 'DISABLED'">停用</div>
    <div v-else>未知</div>

    <div v-show="visible">显示/隐藏（display:none）</div>
</template>
```

`v-if` 真正移除/创建元素；`v-show` 只切换 display。

### 8.3 列表渲染

```vue
<template>
    <ul>
        <li v-for="p in products" :key="p.id">
            {{ p.name }} - {{ p.price }}
        </li>
    </ul>
</template>
```

`:key` 必须有，且唯一。

### 8.4 事件绑定

```vue
<template>
    <button @click="handleClick">点击</button>
    <input @input="onInput" @change="onChange">
    <form @submit.prevent="onSubmit">             <!-- .prevent 阻止默认 -->
</template>

<script setup>
const handleClick = (e) => {
    console.log('点击了', e);
};
const onInput = (e) => {
    console.log('输入', e.target.value);
};
</script>
```

## 九、组件通信

### 9.1 Props：父传子

```vue
<!-- 父组件 -->
<ProductCard :product="product" :show-price="true" />

<!-- 子组件 ProductCard.vue -->
<script setup>
defineProps({
    product: { type: Object, required: true },
    showPrice: { type: Boolean, default: true }
});
</script>

<template>
    <div>
        <h3>{{ product.name }}</h3>
        <p v-if="showPrice">{{ product.price }}</p>
    </div>
</template>
```

### 9.2 Emit：子传父

```vue
<!-- 子组件 -->
<script setup>
const emit = defineEmits(['add-to-cart']);

const handleAdd = () => {
    emit('add-to-cart', { productId: 1, quantity: 1 });
};
</script>

<template>
    <button @click="handleAdd">加入购物车</button>
</template>

<!-- 父组件 -->
<ProductCard @add-to-cart="onAddToCart" />
```

### 9.3 Pinia：跨组件共享

```javascript
// stores/user.js
import { defineStore } from 'pinia';
import { ref, computed } from 'vue';

export const useUserStore = defineStore('user', () => {
    const userInfo = ref(null);
    const token = ref(localStorage.getItem('token') || '');

    const isLoggedIn = computed(() => !!token.value);

    function setToken(t) {
        token.value = t;
        localStorage.setItem('token', t);
    }

    function logout() {
        token.value = '';
        userInfo.value = null;
        localStorage.clear();
    }

    return { userInfo, token, isLoggedIn, setToken, logout };
});
```

```vue
<!-- 任意组件里 -->
<script setup>
import { useUserStore } from '@/stores/user';
const userStore = useUserStore();

console.log(userStore.isLoggedIn);
userStore.setToken('new-token');
</script>
```

## 十、Vue Router：路由

```javascript
// router/index.js
import { createRouter, createWebHistory } from 'vue-router';
import Home from '@/views/Home.vue';
import Login from '@/views/Login.vue';

const routes = [
    { path: '/', component: Home, meta: { requiresAuth: true } },
    { path: '/login', component: Login }
];

const router = createRouter({
    history: createWebHistory(),
    routes
});

// 全局守卫：未登录跳到登录页
router.beforeEach((to, from, next) => {
    const token = localStorage.getItem('token');
    if (to.meta.requiresAuth && !token) {
        next('/login');
    } else {
        next();
    }
});

export default router;
```

```vue
<!-- App.vue -->
<template>
    <router-view />
</template>

<!-- 跳转 -->
<script setup>
import { useRouter } from 'vue-router';
const router = useRouter();
router.push('/products');
</script>

<!-- 链接 -->
<template>
    <router-link to="/products">产品</router-link>
</template>
```

## 十一、Axios：HTTP 请求

### 11.1 基础用法

```javascript
import axios from 'axios';

const res = await axios.get('/api/products', {
    params: { page: 1, size: 20 }
});
console.log(res.data);   // { code, message, data, timestamp }
```

### 11.2 本项目的封装

`src/api/request.ts`：

```typescript
import axios, { type AxiosInstance } from 'axios';
import { ElMessage } from 'element-plus';

const instance: AxiosInstance = axios.create({
    baseURL: import.meta.env.VITE_API_BASE || '/api',
    timeout: 15000
});

// 请求拦截器：自动加 Token
instance.interceptors.request.use(config => {
    const token = localStorage.getItem('token');
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

// 响应拦截器：统一处理错误
instance.interceptors.response.use(
    response => response.data,    // 直接返回 data
    error => {
        ElMessage.error(error.response?.data?.message || '请求失败');
        return Promise.reject(error);
    }
);

export default instance;
```

### 11.3 API 集中管理

`src/api/product.ts`：

```typescript
import request from './request';
import type { Product, Page } from '@/types/product';

export const productApi = {
    list: (page: number, size: number) =>
        request.get<Page<Product>>('/products', { params: { page, size } }),

    getById: (id: number) =>
        request.get<Product>(`/products/${id}`),

    create: (data: Product) =>
        request.post<Product>('/products', data),

    update: (id: number, data: Product) =>
        request.put<Product>(`/products/${id}`, data),

    delete: (id: number) =>
        request.delete(`/products/${id}`)
};
```

## 十二、TypeScript：加类型

### 12.1 基本类型

```typescript
let name: string = '张三';
let age: number = 18;
let active: boolean = true;
let tags: string[] = ['铜', '铅'];
let product: Product = { id: 1, name: '铜精粉', price: 5800 };

// 可选
let description?: string;          // 可能没有
let quantity: number | null = null; // 也可为 null

// 接口（推荐）
interface Product {
    id: number;
    name: string;
    price: number;
    category?: Category;
}
```

### 12.2 在 Vue 里用

```vue
<script setup lang="ts">
import type { Product } from '@/types/product';
import { ref } from 'vue';

const products = ref<Product[]>([]);
const loading = ref(false);

async function load() {
    loading.value = true;
    try {
        const res = await productApi.list(1, 20);
        products.value = res.data.content;
    } finally {
        loading.value = false;
    }
}
</script>
```

## 十三、动手试试

### 实验 1：写一个商品卡片组件

`ProductCard.vue`：

```vue
<template>
    <el-card class="product-card" shadow="hover">
        <h3>{{ product.name }}</h3>
        <p class="category">{{ product.categoryName }}</p>
        <p class="price">¥{{ product.price }}</p>
        <el-button type="primary" @click="handleAdd">加入购物车</el-button>
    </el-card>
</template>

<script setup lang="ts">
import type { Product } from '@/types/product';

const props = defineProps<{
    product: Product;
}>();

const emit = defineEmits<{
    add: [productId: number];
}>();

const handleAdd = () => {
    emit('add', props.product.id);
};
</script>

<style scoped>
.product-card { width: 280px; margin: 10px; }
.price { color: orange; font-size: 24px; font-weight: bold; }
.category { color: #999; font-size: 14px; }
</style>
```

### 实验 2：写一个列表页

```vue
<template>
    <div class="product-list">
        <h1>产品列表</h1>
        <div class="toolbar">
            <el-input v-model="keyword" placeholder="搜索产品" clearable />
            <el-button type="primary" @click="load">查询</el-button>
        </div>

        <el-table v-loading="loading" :data="products">
            <el-table-column prop="name" label="名称" />
            <el-table-column prop="price" label="价格" />
            <el-table-column label="状态">
                <template #default="{ row }">
                    <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'danger'">
                        {{ getStatusLabel(row.status) }}
                    </el-tag>
                </template>
            </el-table-column>
        </el-table>
    </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { productApi } from '@/api/product';
import type { Product } from '@/types/product';
import { getStatusLabel } from '@/composables/useDict';

const keyword = ref('');
const products = ref<Product[]>([]);
const loading = ref(false);

async function load() {
    loading.value = true;
    try {
        const res = await productApi.list(1, 20);
        products.value = res.data.content;
    } finally {
        loading.value = false;
    }
}

onMounted(load);
</script>
```

### 实验 3：登录 + 存 Token

```vue
<template>
    <el-form @submit.prevent="handleLogin">
        <el-input v-model="username" placeholder="用户名" />
        <el-input v-model="password" type="password" placeholder="密码" />
        <el-button native-type="submit" :loading="loading">登录</el-button>
    </el-form>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { useUserStore } from '@/stores/user';
import axios from 'axios';

const username = ref('admin');
const password = ref('');
const loading = ref(false);
const router = useRouter();
const userStore = useUserStore();

async function handleLogin() {
    loading.value = true;
    try {
        const res = await axios.post('/api/auth/login', {
            username: username.value,
            password: password.value
        });
        userStore.setToken(res.data.data.token);
        router.push('/');
    } catch (e) {
        console.error(e);
    } finally {
        loading.value = false;
    }
}
</script>
```

## 十四、关键认知

1. **数据驱动**：改 `ref` 的 `.value`，视图自动更新
2. **组件化**：把页面拆成小组件，每个组件只关心自己
3. **单向数据流**：父 → 子通过 props，子 → 父通过 emit
4. **TypeScript 让 JS 更安全**：写代码时 IDE 帮你查错
5. **Vite 是现代构建工具**：比 webpack 快 10 倍

## 十五、推荐资源

| 资源 | 类型 |
|------|------|
| **Vue 3 官方文档** | https://cn.vuejs.org/ |
| **Vue Router 文档** | https://router.vuejs.org/zh/ |
| **Pinia 文档** | https://pinia.vuejs.org/zh/ |
| **Element Plus** | https://element-plus.org/ |
| **Vite 文档** | https://cn.vitejs.dev/ |
| **Vue Mastery** | 视频课 |

---

下一步：[15 本项目前端结构](15-this-project-frontend.md) →

回头补课：
- [13 前端基础](13-frontend-basics.md)
- [01 宏观架构](01-architecture-overview.md)