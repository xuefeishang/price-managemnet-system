# Style Settings Review Improvement Feature

## Context

`style-settings` 已完成一轮交互优化：品牌文本输入实时进入草稿，系统显示名称与登录页副标题同排，实时预览取消外层框并支持右侧粘性跟随滚动。

当前代码整体方向正确，前端构建已通过，但评审发现仍有几处会影响长期稳定性、版本对比准确性和配置有效性的细节，需要补齐后再进入下一轮验收。

## 当前评分

- 当前评分：8.8 / 10
- 补齐目标：9.3+
- 若再完成动态显示名替换和颜色输入校验体验优化，可接近 9.5

## 问题清单

### 1. 版本对比漏掉登录页副标题文案

涉及文件：

- `frontend/src/utils/styleConfigDiff.ts`
- `frontend/src/types/theme.ts`

问题：

- `StyleConfig` 已包含 `subtitleText`
- `parseConfigSnapshot()` 未解析 `subtitleText`
- `buildStyleConfigDiff()` 未比较 `subtitleText`
- 版本恢复/版本对比时，登录页副标题文案变化可能不可见

影响：

- 用户修改副标题后，历史版本对比不完整
- 回看历史版本时可能误判配置变更范围

建议：

- 在 `FIELD_LABELS` 增加 `subtitleText: '登录页副标题'`
- 在 `buildStyleConfigDiff()` 增加 `subtitleText` 文本差异
- 在 `parseConfigSnapshot()` 返回值中补齐 `subtitleText`

### 2. StyleSettingsShell resize 监听未卸载

涉及文件：

- `frontend/src/components/style-settings/StyleSettingsShell.vue`

问题：

- `onMounted()` 中注册了 `window.addEventListener('resize', checkMobile)`
- 当前没有在组件卸载时执行 `removeEventListener`

影响：

- 多次进入/离开样式设置页后可能累积 resize 监听
- 属于轻量但不必要的资源泄漏

建议：

- 从 `vue` 引入 `onUnmounted`
- 在 `onUnmounted()` 中执行 `window.removeEventListener('resize', checkMobile)`

### 3. 颜色文本输入实时更新但缺少有效性校验

涉及文件：

- `frontend/src/components/style-settings/ColorSchemePanel.vue`

问题：

- 颜色文本框已改为 `@input` 实时进入草稿
- 用户输入半截值，如 `#E`、`#12`、`red1` 时，也会写入 `draftConfig`
- 预览中的 style 可能短暂产生无效 CSS 值
- 当前保存入口未阻止无效颜色值

影响：

- 实时预览可能闪烁或失效
- 用户可能保存非法颜色配置

建议：

- 增加颜色值校验函数，优先支持 `#RGB` 和 `#RRGGBB`
- 文本框可以维护本地输入态，通过校验后再写入 `draftConfig`
- 非法值时显示轻量错误态，不阻断用户继续输入
- 保存前应确保三个价格颜色均为合法值

### 4. 方案名称静态映射与项目规范存在轻微冲突

涉及文件：

- `frontend/src/types/theme.ts`
- `frontend/src/components/style-settings/StyleOverviewPanel.vue`
- `frontend/src/utils/styleConfigDiff.ts`

问题：

- `COLOR_SCHEME_NAMES`、`LAYOUT_STYLE_NAMES`、`FONT_PRESET_NAMES` 是前端静态中文映射
- 项目规范要求编码值显示名优先从服务端/字典/接口动态获取

影响：

- 服务端新增或调整预设名称后，前端静态映射容易滞后
- 与“禁止硬编码编码显示名”的规范不完全一致

建议：

- `StyleOverviewPanel` 优先从 `workbench.colorSchemes/layoutStyles/fontPresets` 查找名称
- `styleConfigDiff.ts` 可接收预设名称上下文，或封装一个动态名称解析工具
- `theme.ts` 中的静态映射仅作为兜底，不作为首选显示来源

## 实现方案

### 方案 A：快速补齐稳定性问题

优先处理：

1. 补齐 `subtitleText` 的版本差异能力
2. 清理 `resize` 监听
3. 增加颜色文本输入合法性校验

优点：

- 改动范围小
- 风险低
- 能快速把体验与稳定性提升到 9.3+

缺点：

- 方案名称动态化仍保留为后续优化项

### 方案 B：一次性补齐到 9.5

在方案 A 基础上继续处理：

1. 移除或降级 `theme.ts` 中静态方案名称映射
2. 统一通过接口预设缓存解析显示名称
3. 版本对比工具支持传入动态预设列表

优点：

- 更符合项目规范
- 后续新增预设不需要改前端映射

缺点：

- 需要调整 `styleConfigDiff` 的函数签名或新增解析上下文
- 涉及版本对比预览组件调用链，改动略大

## 推荐实施顺序

1. 修复 `styleConfigDiff.ts`
   - 增加 `subtitleText` 字段标签
   - 增加 `subtitleText` 差异比较
   - `parseConfigSnapshot()` 补齐 `subtitleText`

2. 修复 `StyleSettingsShell.vue`
   - 引入 `onUnmounted`
   - 卸载时移除 `resize` 监听

3. 修复 `ColorSchemePanel.vue`
   - 增加颜色校验函数
   - 对文本输入设置错误态
   - 合法颜色才进入草稿

4. 视情况处理动态名称解析
   - 优先让总览页使用接口返回名称
   - 差异工具中保留静态映射作为兜底

## 关键参考文件

- `frontend/src/components/style-settings/ColorSchemePanel.vue`
- `frontend/src/components/style-settings/StyleSettingsShell.vue`
- `frontend/src/components/style-settings/StyleOverviewPanel.vue`
- `frontend/src/utils/styleConfigDiff.ts`
- `frontend/src/types/theme.ts`
- `frontend/src/composables/useStyleSettingsWorkbench.ts`

## Verification

### 构建验证

```bash
cd frontend
npm run build
```

预期：

- `vue-tsc` 通过
- `vite build` 通过
- 允许保留现有 chunk size / dynamic import 警告

### 手工验证

1. 进入 `style-settings?section=brand`
   - 输入系统显示名称，应立即显示“有未保存的更改”
   - 输入登录页副标题，应立即进入草稿
   - 版本对比应能看到副标题文案变化

2. 进入 `style-settings?section=color`
   - 输入合法颜色，如 `#EF4444`，预览立即变化
   - 输入非法颜色，如 `#E`，不应污染草稿颜色值
   - 非法状态应有轻量提示或错误样式

3. 多次进入/离开样式设置页面
   - resize 监听不应累积
   - 页面响应式切换仍正常

4. 进入 `style-settings?section=version`
   - 选择历史版本
   - 副标题文案变化应出现在差异列表中

## 文档更新要求

若执行本 Plan 并修改代码，需要同步检查以下文档是否需要补充：

- `docs/dev/UI设计说明.md`
- `docs/dev/项目设计文档.md`
- `docs/archive/项目完成总结.md`

本 Plan 不涉及数据库结构变化；如仅修复前端交互和版本差异展示，无需更新数据字典。
