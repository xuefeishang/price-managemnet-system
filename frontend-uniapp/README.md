# 矿产品价格管理系统 - 多端版本

基于 uni-app Vue3 开发的多端应用，支持 H5、微信小程序、APP。移动端以价格查看、历史追溯和轻量价格录入为主，产品、分类、产地、客户、审批、字典、用户等完整运维保留在 PC 端。

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

# 构建微信小程序（生产环境，读取 .env.production）
npm run build:mp-weixin

# 构建微信小程序（本地联调，读取 .env.development）
npm run build:mp-weixin:local

# 构建 APP
npm run build:app
```

## 页面说明

### 主包页面

| 页面 | 路径 | 说明 |
|------|------|------|
| 登录 | pages/login/index | 账号密码登录 + 服务器地址配置 |
| 首页 | pages/home/index | 价格概览 + 分类筛选 + 产品列表 |
| 历史 | pages/history/index | 只读历史价格查询 + 搜索 + 分类筛选 |
| 价格录入 | pages/price-maintenance/index | 管理员/编辑者轻量价格录入 |
| 产品列表 | pages/products/list | 只读产品辅助入口 + PC 运维提示 |
| 产品详情 | pages/products/detail | 价格走势图（30/180/365天）+ 产品信息 |
| 产品编辑 | pages/products/edit | 历史保留页面，不作为移动端主入口 |
| 个人中心 | pages/profile/index | 用户信息 + 账号操作 |

### 分包页面

| 分包 | 页面 | 说明 |
|------|------|------|
| pages-sub/basic | categories | 分类管理 |
| pages-sub/basic | origins | 产地管理 |
| pages-sub/basic | customers | 客户管理 |
| pages-sub/approval | index | 审批管理 |

> 分包运维页面不作为移动端主导航入口。完整基础运维以 PC 端为准，小程序端仅在必要场景保留历史兼容路由。

## 角色化导航

| 角色 | 底部导航 | 说明 |
|------|----------|------|
| VIEWER | 首页 / 历史 / 我的 | 只读查看行情和历史价格 |
| EDITOR | 首页 / 历史 / 录入 / 我的 | 可进行价格录入或补录 |
| ADMIN | 首页 / 历史 / 录入 / 我的 | 移动端只做应急录入，完整运维回到 PC |

移动端一级功能必须放在底部导航中，页面内部不再放跨功能快捷入口：`首页` 只展示行情概览，`历史` 只做历史查询，`录入` 只做价格录入，`我的` 只承载账号信息和退出登录。

## 功能特性

### 登录
- 沿用 PC 端品牌 Logo、系统名称和登录页副标题
- 仅保留账号密码登录，取消微信一键登录
- 登录卡片下方提供轻量“配置服务器地址”入口，可分别填写 IP 地址和端口号
- 小程序登录页提供三个固定环境：本地测试 `http://127.0.0.1:8080` 仅用于开发者工具模拟器；内网正式环境 `http://10.7.5.175:32801` 使用独立 HTTP 端口；公网正式环境 `https://price.jlmining.com:32080` 使用独立 HTTPS 端口。真机禁止应用本地测试；内网 HTTP/IP 仍需真机调试开启“不校验合法域名”。

### 首页
- 日期选择，查看历史价格
- 分类筛选（横向滚动标签 + 滚动指示箭头）
- 重点关注指标卡片（左右分栏：产品信息 + 价格/价差）
- 产品列表展示最新售价、上期售价和较上期
- 首页、历史和录入页产品列表名称按“产品名.产地”展示，如“铁精粉.宽城”
- 价格涨跌趋势标识（↑/↓ + 差值）
- 继承价格显示（最后一次维护价格）

### 产品详情页
- 价格走势图置顶显示
- 30天/180天/12个月切换
- 最高/最低/平均/最新价格统计
- 销售价 + 生效日期显示

### 价格维护
- 面向 ADMIN/EDITOR 的轻量价格录入
- 默认昨日日期，便于次日补录和复盘
- 支持搜索、分类筛选、录入进度统计
- 展示预算价、昨日价、较昨日、月均价参考
- 保存时只提交发生变化的价格行

### 历史
- 面向所有角色的只读历史价格查询
- 支持日期选择、产品搜索、分类筛选
- 展示最新售价、较上期和月均价
- 点击产品可进入详情查看趋势

## 组件说明

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
2. TabBar 使用自定义配置，根据角色展示 首页/历史/录入/我的
3. 默认 API 地址通过 `.env.production` / `.env.development` 配置；登录页“配置服务器地址”可在本机调试或内网部署时覆盖为指定 IP 与端口
4. 图表使用 Canvas 2D API，兼容小程序环境
5. 产品、分类、产地、客户、审批、字典、用户等完整运维在 PC 端完成
