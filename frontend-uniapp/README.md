# 矿产品价格管理系统 - 多端版本

基于 uni-app Vue3 开发的多端应用，支持 H5、微信小程序、APP。

## 技术栈

- **框架**: uni-app + Vue 3 + TypeScript
- **状态管理**: Pinia
- **构建工具**: Vite
- **UI组件**: 原生组件 + 自定义样式
- **图表**: Canvas 2D（价格走势图）

## 项目结构

```
frontend-uniapp/
├── src/
│   ├── api/                # API接口层
│   ├── components/         # 公共组件
│   │   ├── mini-trend-chart/    # 迷你价格走势图（Canvas）
│   │   └── price-trend-chart/   # 详情页价格走势图（Canvas）
│   ├── composables/        # 组合式函数
│   ├── pages/              # 主包页面
│   ├── pages-sub/          # 分包页面
│   ├── static/             # 静态资源
│   ├── store/              # 状态管理
│   ├── types/              # TypeScript类型
│   ├── utils/              # 工具函数
│   ├── App.vue             # 应用入口
│   ├── main.ts             # 主入口
│   ├── manifest.json       # 应用配置
│   └── pages.json          # 页面路由配置
├── package.json
├── vite.config.ts
└── tsconfig.json
```

## 开发命令

```bash
# 安装依赖
npm install

# H5 开发
npm run dev:h5

# 微信小程序开发
npm run dev:mp-weixin

# APP 开发
npm run dev:app

# 构建 H5
npm run build:h5

# 构建微信小程序
npm run build:mp-weixin

# 构建 APP
npm run build:app
```

## 页面说明

### 主包页面

| 页面 | 路径 | 说明 |
|------|------|------|
| 登录 | pages/login/index | 账号密码登录 + 微信登录 |
| 首页 | pages/home/index | 价格概览 + 分类筛选 + 价格走势曲线 |
| 产品列表 | pages/products/list | 统计卡片 + 快捷入口 |
| 产品详情 | pages/products/detail | 价格走势图（30/180/365天）+ 产品信息 |
| 产品编辑 | pages/products/edit | 产品信息编辑 |
| 价格维护 | pages/price-maintenance/index | 价格管理 + 产品规格显示 |
| 个人中心 | pages/profile/index | 用户信息 + 功能入口 |

### 分包页面

| 分包 | 页面 | 说明 |
|------|------|------|
| pages-sub/basic | categories | 分类管理 |
| pages-sub/basic | origins | 产地管理 |
| pages-sub/basic | customers | 客户管理 |
| pages-sub/approval | index | 审批管理 |

## 功能特性

### 首页
- 日期选择，查看历史价格
- 分类筛选（横向滚动标签 + 滚动指示箭头）
- 重点关注指标卡片（左右分栏：产品信息 + 价格/价差）
- 产品列表网格布局（每行两列）
- 价格涨跌趋势标识（↑/↓ + 差值）
- 继承价格显示（最后一次维护价格）

### 产品详情页
- 价格走势图置顶显示
- 30天/180天/12个月切换
- 最高/最低/平均/最新价格统计
- 销售价 + 生效日期显示

### 价格维护
- 产品规格列显示
- 预算价/昨日价/月均价参考

## 组件说明

### mini-trend-chart
迷你价格走势图组件，用于首页产品卡片。

```vue
<mini-trend-chart :productId="1" :days="30" :width="160" :height="40" />
```

Props:
- `productId`: 产品ID
- `days`: 天数（默认30）
- `width`: 宽度（默认120）
- `height`: 高度（默认40）

### price-trend-chart
详情页价格走势图组件，支持时间范围切换。

```vue
<price-trend-chart :productId="1" />
```

Props:
- `productId`: 产品ID

## 后端接口

需要后端新增微信登录接口：

```
POST /api/auth/wechat-login
```

详见 `plan/multi-platform-adaptation.md`

## 注意事项

1. 微信小程序 AppID 需要在 `manifest.json` 中配置（当前：`wxe6ae2d447abb057e`）
2. TabBar 使用原生配置，字体大小 12px
3. 生产环境 API 地址需要在 `.env.production` 中配置
4. 图表使用 Canvas 2D API，兼容小程序环境
