# 小程序入口分享升级方案

> 说明：本文件按用户指定路径存放于 `docs/plan/`。项目永久规范中 Plan 默认目录为根目录 `plan/`，但当前仓库活跃规划文档实际集中在 `docs/plan/`；如后续统一规划目录，可再整体迁移。

## Context

当前 `frontend-uniapp` 小程序端没有“小程序分享给其他人或微信群”的能力。代码检查结论：

- `frontend-uniapp/src` 中没有 `onShareAppMessage`。
- `frontend-uniapp/src` 中没有 `onShareTimeline`。
- 页面内没有 `open-type="share"` 的分享按钮。
- `App.vue` 只注册了 `onLaunch/onShow/onHide`，没有全局或页面级分享配置。
- `pages.json` 只定义页面、TabBar 和样式，不负责微信分享逻辑。

本次需求只做“小程序入口分享”，不分享某个产品、某条价格或某个业务详情页。用户希望能把小程序转发给微信好友、微信群，并可从微信右上角菜单分享到朋友圈。

## 评审修复记录

当前方案自评：**9.0 / 10**。

实施状态：**已完成代码实现，待微信开发者工具与真机验证**。

本轮已针对上一版评审扣分点补强：

- 明确 `onShareTimeline` 只允许首页注册，登录页、个人中心和业务页面不得注册朋友圈分享。
- 明确 `showShareMenu` 在微信小程序端优先使用 `wx.showShareMenu`，`uni.showShareMenu` 需先通过类型和真机验证。
- 增加分享封面图构建产物检查和真机卡片显示验收。
- 明确本阶段不新增登录 `redirect`，分享落地统一依赖登录成功后进入首页。

## 实施记录

已完成：

- 新增 `frontend-uniapp/src/utils/share.ts`，统一小程序入口分享标题、首页路径、封面图和分享菜单开启逻辑。
- 新增 `frontend-uniapp/src/static/share/app-share.png` 本地分享封面，封面使用 `docs/UI/华夏建龙logo（白色-竖向）png.png` 公司 Logo，不写死系统名称。
- 首页 `frontend-uniapp/src/pages/home/index.vue` 已注册 `onShareAppMessage` 和 `onShareTimeline`，并开启微信右上角好友/群与朋友圈菜单；分享标题读取样式配置 `systemName`，与 PC 端系统名称保持一致。
- 登录页 `frontend-uniapp/src/pages/login/index.vue` 已注册 `onShareAppMessage`，未登录测试或分享落地到登录页时仍可转发小程序入口；登录页不注册 `onShareTimeline`；分享标题读取样式配置 `systemName`。
- 我的页 `frontend-uniapp/src/pages/profile/index.vue` 已新增“分享小程序”按钮，只注册 `onShareAppMessage`，不注册 `onShareTimeline`；分享标题读取样式配置 `systemName`。
- `README.md`、`docs/dev/design/ui.md`、`docs/dev/design/architecture.md`、`docs/archive/项目完成总结.md` 已同步小程序入口分享能力。

已验证：

- `cd frontend-uniapp && npm run typecheck` 通过。
- `cd frontend-uniapp && npm run build:mp-weixin` 通过。
- `rg -n "onShareTimeline" frontend-uniapp/src` 仅命中首页。
- `frontend-uniapp/dist/build/mp-weixin/static/share/app-share.png` 已生成。

待验证：

- 微信开发者工具导入 `frontend-uniapp/dist/build/mp-weixin` 后验证右上角菜单。
- 真机预览验证好友、微信群、朋友圈三类分享卡片标题、封面和落地页。

## 目标

1. 用户可以把小程序入口转发给微信好友或微信群。
2. 用户可以通过微信右上角菜单把小程序入口分享到朋友圈。
3. 分享落地页统一为首页 `/pages/home/index`。
4. 分享内容不携带 token、产品 ID、价格、客户、产地等敏感业务参数。
5. 未登录用户打开分享入口后，仍按现有登录流程进入登录页；登录后进入首页。
6. 该能力只影响小程序端，不改变 PC/H5/API/数据库语义。

## 非目标

- 不实现产品详情分享。
- 不实现价格数据分享。
- 不实现邀请注册、邀请统计、裂变推广。
- 不通过分享链接绕过登录、角色权限或数据权限。
- 不把朋友圈分享做成页面内强制按钮；朋友圈入口按微信小程序平台能力走右上角菜单。

## 实现方案

### 1. 分享策略

统一分享小程序首页入口：

```ts
const MINIAPP_SHARE_TITLE = '矿产品价格管理系统'
const MINIAPP_SHARE_PATH = '/pages/home/index'
const MINIAPP_SHARE_IMAGE = '/static/share/app-share.png'
```

分享给好友/微信群：

```ts
onShareAppMessage(() => ({
  title: MINIAPP_SHARE_TITLE,
  path: MINIAPP_SHARE_PATH,
  imageUrl: MINIAPP_SHARE_IMAGE
}))
```

分享到朋友圈：

```ts
onShareTimeline(() => ({
  title: MINIAPP_SHARE_TITLE,
  query: '',
  imageUrl: MINIAPP_SHARE_IMAGE
}))
```

### 2. 页面接入范围

第一阶段只接入两个页面：

| 页面 | 文件 | 分享能力 | 说明 |
|------|------|----------|------|
| 首页 | `frontend-uniapp/src/pages/home/index.vue` | 好友/微信群 + 朋友圈 | 作为分享落地页和主入口 |
| 登录 | `frontend-uniapp/src/pages/login/index.vue` | 好友/微信群 | 未登录或测试登录页时也可转发小程序入口 |
| 我的 | `frontend-uniapp/src/pages/profile/index.vue` | 好友/微信群 | 提供显式“分享小程序”按钮 |

暂不在 `pages/products/detail.vue`、`pages/history/index.vue`、`pages/price-maintenance/index.vue` 等业务页面注册分享，避免用户误以为分享的是具体产品或价格数据。

### 3. 右上角菜单

在首页页面生命周期中开启微信分享菜单：

```ts
onMounted(() => {
  // #ifdef MP-WEIXIN
  const showShareMenu = (wx?.showShareMenu || uni.showShareMenu) as typeof uni.showShareMenu
  showShareMenu({
    withShareTicket: false,
    menus: ['shareAppMessage', 'shareTimeline']
  })
  // #endif
})
```

说明：

- `shareAppMessage`：右上角菜单转发给好友/微信群。
- `shareTimeline`：右上角菜单分享到朋友圈。
- `withShareTicket` 暂不启用，因为本需求不需要微信群信息或群分享追踪。
- 实施前必须确认当前 `@dcloudio/types` 对 `uni.showShareMenu` 的 `menus` 类型支持；若类型或运行时表现不稳定，微信小程序端在 `#ifdef MP-WEIXIN` 内优先使用 `wx.showShareMenu`。
- `showShareMenu` 调用只放在首页，避免其他业务页面在右上角菜单暴露分享入口。

### 3.1 朋友圈注册边界

朋友圈分享只允许在首页 `frontend-uniapp/src/pages/home/index.vue` 注册 `onShareTimeline`。

约束：

- `frontend-uniapp/src/pages/login/index.vue` 和 `frontend-uniapp/src/pages/profile/index.vue` 只注册 `onShareAppMessage`，不注册 `onShareTimeline`。
- `frontend-uniapp/src/utils/share.ts` 可以提供朋友圈分享内容函数，但只有首页可以调用。
- 产品详情、历史、价格维护、通知等业务页面不得复用朋友圈分享注册，避免朋友圈分享落到错误页面或让用户误以为公开分享了业务数据。
- 若后续新增其他入口页，需要先在本 Plan 或新的 Plan 中明确页面定位、分享落地路径和权限边界，再允许注册 `onShareTimeline`。

### 4. 显式分享入口

在个人中心增加一项“分享小程序”：

```vue
<button class="share-btn" open-type="share">分享小程序</button>
```

交互位置建议：

- 放在 `消息通知` 和 `小程序消息订阅` 同级的菜单区。
- 文案使用“分享小程序”，副文案使用“转发给同事或微信群”。
- 不在按钮旁解释技术规则，不展示“朋友圈”字样，避免误导用户认为页面内按钮可直接发朋友圈。

### 5. 分享封面图

新增本地分享图：

```text
frontend-uniapp/src/static/share/app-share.png
```

设计要求：

- 尺寸建议：`500x400` 或微信推荐比例相近尺寸。
- 使用 `docs/UI/华夏建龙logo（白色-竖向）png.png` 公司 Logo。
- 封面图不写死系统名称，避免与 PC 端动态系统名称不一致。
- 分享卡片标题读取样式配置 `systemName`，与 PC 端当前系统名称保持一致。
- 不出现真实价格、客户名称、内部业务数据。
- 背景与现有品牌色 `#0D6E6E` 保持一致，但避免过多信息。

如第一阶段没有设计资源，可先使用项目 Logo/系统名生成一张默认封面图；若缺图，微信会使用页面截图或默认图，观感不可控。

产物验收要求：

- 构建后必须确认 `frontend-uniapp/dist/build/mp-weixin/static/share/app-share.png` 存在。
- 微信开发者工具和真机预览中必须分别确认好友分享卡片、微信群分享卡片和朋友圈分享均能显示该封面。
- 如果封面图因包体、路径或平台限制未显示，本功能不能标记为完成，应改用确认可被微信小程序访问的 HTTPS 图片或修正静态资源路径。

### 6. 登录与落地页

分享路径统一进入 `/pages/home/index`，不带业务参数。现有首页逻辑已检查 token：

```ts
if (!userStore.token) {
  uni.reLaunch({ url: '/pages/login/index' })
  return
}
```

本阶段不新增 `redirect` 参数，明确依赖现有登录成功后固定 `uni.switchTab({ url: '/pages/home/index' })` 的行为。也就是说：

- 分享入口统一落到首页。
- 未登录时首页跳登录页。
- 登录成功后回到首页。
- 不处理“打开分享后回到分享前页面”或“按来源恢复深层页面”，因为本需求不是产品详情分享。

后续若要增加 redirect，需要单独评估登录页参数校验、白名单路径和敏感参数过滤，不能把 token、产品 ID、价格查询条件等业务参数直接带入分享链路。

### 7. 平台隔离

分享能力只在微信小程序编译目标启用：

```ts
// #ifdef MP-WEIXIN
// 微信小程序分享逻辑
// #endif
```

H5、App、支付宝/百度/抖音小程序暂不启用该逻辑，避免不同平台分享 API 语义差异引入回归。

### 8. 公共封装建议

为避免首页和个人中心重复配置，可新增：

```text
frontend-uniapp/src/utils/share.ts
```

建议内容：

```ts
export const MINIAPP_SHARE_TITLE = '矿产品价格管理系统'
export const MINIAPP_SHARE_PATH = '/pages/home/index'
export const MINIAPP_SHARE_IMAGE = '/static/share/app-share.png'

const normalizeShareTitle = (title?: string) => title?.trim() || MINIAPP_SHARE_TITLE

export const getMiniappEntryShareMessage = (title?: string) => ({
  title: normalizeShareTitle(title),
  path: MINIAPP_SHARE_PATH,
  imageUrl: MINIAPP_SHARE_IMAGE
})

export const getMiniappEntryTimelineShare = (title?: string) => ({
  title: normalizeShareTitle(title),
  query: '',
  imageUrl: MINIAPP_SHARE_IMAGE
})
```

页面只负责注册生命周期：

```ts
import { onShareAppMessage, onShareTimeline } from '@dcloudio/uni-app'
import { useTheme } from '@/composables/useTheme'
import { getMiniappEntryShareMessage, getMiniappEntryTimelineShare } from '@/utils/share'

const { themeConfig } = useTheme()

onShareAppMessage(() => getMiniappEntryShareMessage(themeConfig.value.systemName))
onShareTimeline(() => getMiniappEntryTimelineShare(themeConfig.value.systemName))
```

注意：`getMiniappEntryTimelineShare` 只允许首页调用。个人中心的显式按钮只使用 `getMiniappEntryShareMessage` 和 `open-type="share"`，不注册朋友圈生命周期。

## 关键参考文件

| 文件 | 用途 |
|------|------|
| `frontend-uniapp/src/pages/home/index.vue` | 首页分享生命周期、右上角菜单、分享落地页登录检查 |
| `frontend-uniapp/src/pages/login/index.vue` | 未登录状态或开发者工具测试页的好友/微信群转发兜底 |
| `frontend-uniapp/src/pages/profile/index.vue` | “我的”页显式分享按钮 |
| `frontend-uniapp/src/App.vue` | 确认无需全局分享注册 |
| `frontend-uniapp/src/pages.json` | 确认首页与个人中心路径、TabBar 配置 |
| `frontend-uniapp/src/manifest.json` | 确认 `mp-weixin.appid`、微信小程序构建配置 |
| `frontend-uniapp/src/static/` | 新增分享封面图目录 |
| `frontend-uniapp/src/types/uni-app.d.ts` | 如类型检查缺少 `menus` 等字段时补充类型 |

## 实现步骤

1. 新增 `frontend-uniapp/src/utils/share.ts`，统一小程序入口分享标题、路径和封面图；分享标题支持传入样式配置 `systemName`。
2. 新增 `frontend-uniapp/src/static/share/app-share.png` 分享封面图，使用华夏建龙白色竖向 Logo。
3. 修改 `frontend-uniapp/src/pages/home/index.vue`：
   - 引入 `onShareAppMessage/onShareTimeline`。
   - 注册首页入口分享配置。
   - 调用 `loadThemeConfig()`，分享标题使用 `themeConfig.value.systemName`。
   - 在微信小程序环境调用 `wx.showShareMenu` 或类型确认可用的 `uni.showShareMenu`，启用 `shareAppMessage/shareTimeline`。
4. 修改 `frontend-uniapp/src/pages/login/index.vue`：
   - 引入并注册 `onShareAppMessage`。
   - 分享标题使用 `themeConfig.value.systemName`。
   - 在微信小程序环境只启用 `shareAppMessage` 菜单，不启用朋友圈。
5. 修改 `frontend-uniapp/src/pages/profile/index.vue`：
   - 引入并注册 `onShareAppMessage`。
   - 调用 `loadThemeConfig()`，分享标题使用 `themeConfig.value.systemName`。
   - 在菜单区新增“分享小程序”按钮，使用 `open-type="share"`。
   - 不注册 `onShareTimeline`。
6. 执行小程序端类型检查：
   - `cd frontend-uniapp`
   - `npm run typecheck`
7. 构建微信小程序：
   - `npm run build:mp-weixin`
8. 使用微信开发者工具打开 `frontend-uniapp/dist/build/mp-weixin`，进行开发者工具验证。
9. 使用真机预览验证：
   - 首页右上角可转发给好友/微信群。
   - 首页右上角可分享到朋友圈。
   - 登录页右上角可转发给好友/微信群，但不提供朋友圈分享。
   - 我的页“分享小程序”按钮可转发给好友/微信群。
   - 被分享用户未登录时进入登录页，登录后可进入首页。

## Verification

### 自动检查

| 验证项 | 命令 | 通过标准 |
|--------|------|----------|
| 类型检查 | `cd frontend-uniapp && npm run typecheck` | 无 TypeScript 错误 |
| 小程序构建 | `cd frontend-uniapp && npm run build:mp-weixin` | 生成 `dist/build/mp-weixin` 且无构建错误 |
| 静态搜索 | `rg -n "onShareAppMessage|onShareTimeline|open-type=.share." frontend-uniapp/src` | 首页、个人中心存在预期分享逻辑 |
| 朋友圈边界 | `rg -n "onShareTimeline" frontend-uniapp/src` | 仅首页注册 `onShareTimeline` |
| 分享封面产物 | `Test-Path frontend-uniapp/dist/build/mp-weixin/static/share/app-share.png` | 返回 `True` |

### 手工验收

| 场景 | 操作 | 预期 |
|------|------|------|
| 首页转发好友 | 首页右上角菜单选择转发 | 分享卡片标题为“矿产品价格管理系统”，打开进入首页 |
| 首页转发微信群 | 首页右上角菜单选择转发到群 | 群内卡片可打开小程序首页 |
| 首页发朋友圈 | 首页右上角菜单选择分享到朋友圈 | 朋友圈分享标题和封面正确，点击进入小程序首页 |
| 登录页转发好友 | 未登录停留登录页，右上角菜单选择转发 | 可转发小程序入口，不提示当前页面未设置分享 |
| 我的页按钮分享 | 我的页点击“分享小程序” | 调起转发面板，可发给好友或微信群 |
| 登录页无朋友圈入口 | 登录页右上角菜单 | 不提供朋友圈分享 |
| 我的页无朋友圈入口 | 我的页右上角菜单与页面按钮 | 不提供朋友圈分享，不误导用户 |
| 未登录打开分享 | 清除登录态后打开分享卡片 | 进入登录页，不直接暴露业务数据 |
| 已登录打开分享 | 已登录用户打开分享卡片 | 进入首页并正常加载数据 |
| 敏感信息检查 | 查看分享标题、路径、封面 | 不出现 token、产品 ID、价格、客户等敏感信息 |
| 封面图真机检查 | 真机分别转发好友、微信群、朋友圈 | 三种分享卡片均显示预期封面 |
| 系统名称一致性 | PC 端修改系统名称后进入小程序分享 | 好友/微信群和朋友圈分享标题与 PC 端当前系统名称一致 |

## 前后端与数据库一致性检查

本方案第一阶段只改 `frontend-uniapp` 小程序端页面和静态资源：

- 不新增后端接口。
- 不修改现有 API 请求/响应结构。
- 不修改 Entity、Repository、SQL 或数据库表结构。
- 不需要更新数据字典。
- 不需要更新 `init.sql`。

若后续增加分享统计、邀请追踪或分享审计，则需要新增后端接口和数据库表，并按 AGENTS.md 要求同步更新：

- `docs/dev/api/internal.md`
- `docs/dev/design/api-design.md`
- `docs/dev/design/database.md`
- 数据字典文档

## 文档更新影响

实施完成后建议同步更新：

| 文档 | 更新点 |
|------|--------|
| `README.md` | 多端能力列表补充“小程序入口分享” |
| `docs/dev/design/architecture.md` | 小程序特有能力中补充入口分享 |
| `docs/dev/design/ui.md` | 个人中心新增“分享小程序”菜单项 |
| `docs/archive/项目完成总结.md` | 功能完成情况表格补充小程序分享能力 |

如仅保留本 Plan 而暂不实施代码，不需要立刻更新上述文档。

## 风险与处理

| 风险 | 影响 | 处理 |
|------|------|------|
| 微信能力在开发者工具与真机表现不一致 | 本地看似可用但线上不可用 | 必须做真机预览验证 |
| 朋友圈入口不可通过页面按钮直接触发 | 用户误以为按钮能发朋友圈 | 朋友圈只在首页右上角菜单启用，按钮文案只写“分享小程序” |
| 分享封面缺失或路径不正确 | 分享卡片观感差 | 使用本地静态图并在构建产物中确认存在 |
| `uni.showShareMenu` 类型或运行时不兼容 `menus` | 构建失败或朋友圈菜单未展示 | 微信端优先使用 `wx.showShareMenu`，并保留类型检查与真机验证 |
| `onShareTimeline` 被误注册到非首页 | 朋友圈落地页错误或误导用户 | 静态搜索要求 `onShareTimeline` 仅出现在首页 |
| 未登录用户打开后看不到首页 | 用户以为分享失败 | 保持登录页承接；必要时后续优化登录后回首页提示 |
| 分享路径携带敏感参数 | 数据泄露 | 统一使用 `/pages/home/index`，不拼接任何业务参数 |

## 回滚方案

如上线后分享能力异常，可快速回滚：

1. 移除首页 `onShareAppMessage/onShareTimeline/showShareMenu` 注册。
2. 移除个人中心 `open-type="share"` 按钮。
3. 保留或删除 `static/share/app-share.png` 均不影响业务运行。
4. 重新构建并上传小程序版本。

该功能不涉及数据库和后端接口，回滚风险低。

## 官方资料复核

实施前需以当前微信与 uni-app 官方文档为准复核以下能力：

- 微信小程序分享能力：`onShareAppMessage`、`onShareTimeline`、`wx.showShareMenu`
- uni-app 页面生命周期：`onShareAppMessage`、`onShareTimeline`
- 微信平台关于朋友圈分享入口、分享图片大小、菜单能力的最新限制

参考入口：

- https://developers.weixin.qq.com/miniprogram/dev/framework/open-ability/share.html
- https://developers.weixin.qq.com/miniprogram/dev/reference/api/Page.html
- https://developers.weixin.qq.com/miniprogram/dev/api/share/wx.showShareMenu.html
- https://uniapp.dcloud.net.cn/api/plugins/share.html
