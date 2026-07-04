# 13. 前端基础：HTML / CSS / JavaScript 30 分钟速成

> 学 Vue3 之前要懂的最少必要知识。这一章只讲**本项目用到的**前端三件套。

---

## 一、前端是什么？

**前端 = 浏览器上看到的、点的东西**。

```
你看到的"产品列表页"
  │
  ├── HTML（结构）    → "有哪些元素"：标题、表格、按钮
  ├── CSS（样式）     → "长什么样"：颜色、大小、位置
  └── JavaScript（行为）→ "能做什么"：点击、提交、计算
```

**类比**：

| 前端 | 后端 | 比喻 |
|------|------|------|
| HTML | 数据库表 | 骨架 |
| CSS | 设计稿 | 皮肤 |
| JavaScript | 业务逻辑 | 灵魂 |

## 二、本项目前端技术栈

```
本项目双前端：
  ├── frontend/         H5（PC/手机浏览器）
  │   └── Vue 3 + Vite + TypeScript + Element Plus + ECharts
  │
  └── frontend-uniapp/  多端（H5 / APP / 小程序）
      └── uni-app + Vue 3 + TypeScript
```

**两个前端共用后端 API**。

## 三、HTML：网页骨架

### 3.1 基本结构

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <title>产品价格</title>
</head>
<body>
    <h1>产品列表</h1>
    <table>
        <tr>
            <th>名称</th>
            <th>价格</th>
        </tr>
        <tr>
            <td>铜精粉</td>
            <td>5800.00</td>
        </tr>
    </table>
    <button onclick="alert('点击了')">点我</button>
</body>
</html>
```

### 3.2 常用标签

| 标签 | 作用 | 例子 |
|------|------|------|
| `<h1>~<h6>` | 标题 | `<h1>大标题</h1>` |
| `<p>` | 段落 | `<p>一段文字</p>` |
| `<a>` | 链接 | `<a href="/products">产品</a>` |
| `<img>` | 图片 | `<img src="logo.png" alt="logo">` |
| `<ul> <li>` | 无序列表 | `<ul><li>项1</li></ul>` |
| `<table> <tr> <td>` | 表格 | 见上 |
| `<form>` | 表单 | `<form><input name="username"></form>` |
| `<input>` | 输入框 | `<input type="text" placeholder="请输入">` |
| `<button>` | 按钮 | `<button>提交</button>` |
| `<div>` | 块级容器 | `<div>一个区块</div>` |
| `<span>` | 行内容器 | `<span>一行内的小块</span>` |

### 3.3 表单

```html
<form action="/api/login" method="POST">
    <input type="text" name="username" placeholder="用户名" required>
    <input type="password" name="password" placeholder="密码" required>
    <button type="submit">登录</button>
</form>
```

**关键属性**：

| 属性 | 作用 |
|------|------|
| `name` | 表单字段名（提交时用） |
| `type` | 类型：`text` / `password` / `number` / `email` / `date` |
| `placeholder` | 占位提示 |
| `required` | 必填 |
| `disabled` | 禁用 |
| `value` | 默认值 |

## 四、CSS：让网页好看

### 4.1 三种写法

```html
<!-- 1. 内联（不推荐） -->
<p style="color: red;">红色文字</p>

<!-- 2. 内嵌（在 head 里） -->
<head>
    <style>
        p { color: red; }
    </style>
</head>

<!-- 3. 外链（推荐） -->
<head>
    <link rel="stylesheet" href="style.css">
</head>
```

### 4.2 选择器

```css
/* 1. 标签选择器 */
p { color: red; }

/* 2. 类选择器（最常用） */
.product-name { font-size: 18px; font-weight: bold; }

/* 3. ID 选择器 */
#header { background: blue; }

/* 4. 后代选择器 */
.product-card .price { color: orange; }

/* 5. 状态选择器 */
button:hover { background: #1890ff; }     /* 鼠标悬停 */
input:focus { border-color: blue; }       /* 获得焦点 */
button:disabled { opacity: 0.5; }         /* 禁用 */

/* 6. 第一个 / 最后一个 */
li:first-child { color: red; }
li:last-child { color: blue; }
```

### 4.3 常用属性

```css
/* 文字 */
color: #333;                  /* 颜色 */
font-size: 16px;              /* 字号 */
font-weight: bold;            /* 加粗 */
text-align: center;           /* 对齐：left/center/right */
line-height: 1.5;             /* 行高 */

/* 盒子 */
width: 200px;                 /* 宽度 */
height: 100px;                /* 高度 */
padding: 10px;                /* 内边距 */
margin: 10px;                 /* 外边距 */
border: 1px solid #ccc;       /* 边框 */
border-radius: 4px;           /* 圆角 */
background: #fff;             /* 背景色 */

/* 布局 */
display: flex;                /* 弹性盒布局 */
position: relative;           /* 定位：relative/absolute/fixed */
```

### 4.4 Flex 布局（最常用）

```css
.container {
    display: flex;              /* 横向排列 */
    justify-content: center;    /* 主轴居中 */
    align-items: center;        /* 交叉轴居中 */
    gap: 10px;                  /* 子元素间距 */
}

.container-vertical {
    display: flex;
    flex-direction: column;     /* 纵向排列 */
}
```

## 五、JavaScript：让网页会动

### 5.1 第一个 JS

新建 `hello.html`：

```html
<!DOCTYPE html>
<html>
<body>
    <h1 id="title">你好</h1>
    <button onclick="changeText()">点我</button>

    <script>
        function changeText() {
            document.getElementById('title').innerText = '世界';
        }
    </script>
</body>
</html>
```

### 5.2 变量与类型

```javascript
// 变量（let / const，不用 var）
let name = '张三';                // 可以改
const PI = 3.14159;               // 不能改

// 类型
let age = 18;                      // number
let price = 5800.50;               // number（不区分 int/float）
let active = true;                 // boolean
let tags = ['铜', '铅', '锌'];      // array
let product = {                    // object
    id: 1,
    name: '铜精粉',
    price: 5800
};
let nothing = null;                // 空
let undefined;                     // 未定义

// 模板字符串（用反引号）
let msg = `你好，${name}，价格是 ${price}`;
```

### 5.3 函数

```javascript
// 传统函数
function add(a, b) {
    return a + b;
}

// 箭头函数（推荐）
const add = (a, b) => a + b;

// 异步函数
async function loadProducts() {
    const res = await fetch('/api/products');
    const data = await res.json();
    console.log(data);
}
```

### 5.4 数组操作

```javascript
let products = [
    { id: 1, name: '铜精粉', price: 5800 },
    { id: 2, name: '铅精粉', price: 1580 },
    { id: 3, name: '锌精粉', price: 2380 },
];

// 遍历
products.forEach(p => console.log(p.name));

// map：转换
let names = products.map(p => p.name);  // ['铜精粉', '铅精粉', '锌精粉']

// filter：过滤
let expensive = products.filter(p => p.price > 5000);

// find：找第一个
let p = products.find(p => p.id === 1);

// reduce：聚合
let total = products.reduce((sum, p) => sum + p.price, 0);

// sort：排序
products.sort((a, b) => a.price - b.price);
```

### 5.5 解构

```javascript
let product = { id: 1, name: '铜精粉', price: 5800 };

// 解构对象
let { id, name, price } = product;
console.log(name);   // '铜精粉'

// 解构数组
let [first, second] = [1, 2, 3];

// 默认值
let { name, category = '未知' } = product;
```

### 5.6 异步：Promise 与 async/await

**为什么需要异步？**

```javascript
// ❌ 同步阻塞
const result = fetch('/api/products');   // 卡住等响应
console.log(result);                      // 一直执行不到

// ✅ 异步非阻塞
async function load() {
    const res = await fetch('/api/products');  // 等，但不阻塞其他
    const data = await res.json();
    console.log(data);
}
load();
```

**Promise 链**：

```javascript
fetch('/api/products')
    .then(res => res.json())    // 1. 转 JSON
    .then(data => console.log(data))    // 2. 处理
    .catch(err => console.error(err));  // 3. 异常
```

**async/await**（更直观）：

```javascript
async function loadProducts() {
    try {
        const res = await fetch('/api/products');
        if (!res.ok) throw new Error('请求失败');
        const data = await res.json();
        return data;
    } catch (e) {
        console.error('加载失败:', e);
    }
}
```

### 5.7 fetch：调用后端 API

```javascript
// GET
async function getProducts() {
    const res = await fetch('/api/products?page=1&size=20', {
        headers: { 'Authorization': 'Bearer ' + token }
    });
    const json = await res.json();
    return json.data;   // Result<T> 格式
}

// POST
async function createProduct(product) {
    const res = await fetch('/api/products', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer ' + token
        },
        body: JSON.stringify(product)
    });
    return await res.json();
}
```

## 六、DOM 操作：JS 操作网页

```javascript
// 找元素
const title = document.getElementById('title');
const items = document.querySelectorAll('.product-item');

// 改内容
title.innerText = '新标题';
title.innerHTML = '<b>加粗标题</b>';

// 改样式
title.style.color = 'red';

// 改属性
title.setAttribute('data-id', '1');

// 监听事件
button.addEventListener('click', () => {
    console.log('被点击了');
});

// 创建元素
const newDiv = document.createElement('div');
newDiv.innerText = '新元素';
document.body.appendChild(newDiv);
```

**注意**：现代前端框架（Vue3）几乎不用直接操作 DOM，用**数据驱动视图**。

## 七、ES6+ 常用语法（Vue3 必备）

```javascript
// 1. 箭头函数
const fn = (x) => x * 2;

// 2. 模板字符串
const msg = `Hello, ${name}!`;

// 3. 解构
const { a, b } = obj;
const [x, y] = arr;

// 4. 扩展运算符
const arr2 = [...arr1, 4, 5];
const obj2 = { ...obj1, age: 18 };

// 5. 可选链
const name = user?.profile?.name;   // user 为 null 时不报错

// 6. 空值合并
const display = name ?? '匿名';      // name 为 null/undefined 用 '匿名'

// 7. 对象简写
const name = '张三';
const obj = { name };    // 等价于 { name: name }

// 8. 模块导入导出
import { ref } from 'vue';
export default { ... };
```

## 八、动手试试

### 实验 1：HTML + CSS 写一个卡片

新建 `card.html`：

```html
<!DOCTYPE html>
<html>
<head>
    <style>
        .card {
            width: 300px;
            padding: 20px;
            border: 1px solid #ddd;
            border-radius: 8px;
            box-shadow: 0 2px 8px rgba(0,0,0,0.1);
            font-family: sans-serif;
        }
        .card h2 { color: #1890ff; margin: 0 0 10px; }
        .card .price { color: orange; font-size: 24px; font-weight: bold; }
        .card button {
            background: #1890ff;
            color: white;
            border: none;
            padding: 10px 20px;
            border-radius: 4px;
            cursor: pointer;
        }
        .card button:hover { background: #40a9ff; }
    </style>
</head>
<body>
    <div class="card">
        <h2>铜精粉</h2>
        <p>分类：有色金属</p>
        <p class="price">¥5800.00</p>
        <button onclick="alert('已加入购物车')">加入购物车</button>
    </div>
</body>
</html>
```

### 实验 2：JS 调后端

打开浏览器控制台（F12 → Console）：

```javascript
// 在你启动后端服务的前提下
fetch('/api/products', {
    headers: { 'Authorization': 'Bearer 你的token' }
})
.then(res => res.json())
.then(data => console.log(data))
.catch(err => console.error(err));
```

### 实验 3：解构 + 异步

```javascript
async function demo() {
    const res = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: 'admin', password: 'Admin@123456' })
    });
    const { code, data } = await res.json();
    console.log('返回码:', code);
    console.log('Token:', data.token);
}
demo();
```

## 九、推荐资源

| 资源 | 类型 | 链接 |
|------|------|------|
| **MDN Web Docs** | 文档 | https://developer.mozilla.org/zh-CN/ |
| **W3School** | 教程 | https://www.w3school.com.cn/ |
| **菜鸟教程** | 中文 | https://www.runoob.com/ |
| **JavaScript.info** | 教程 | https://zh.javascript.info/ |

## 十、关键认知

1. **HTML 是骨架，CSS 是皮肤，JS 是灵魂**——三者缺一不可
2. **现代前端用框架（Vue/React）开发**，不直接操作 DOM
3. **JS 异步是核心**：async/await 必会
4. **HTTP 请求是前后端的桥梁**：fetch 是基础
5. **不要死记硬背语法**，多用 IDE 提示 + MDN 查询

---

下一步：[14 Vue3 核心](14-vue3-essentials.md) →

回头补课：本章已涵盖学 Vue3 所需的最少前端知识