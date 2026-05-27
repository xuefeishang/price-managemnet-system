# 分类视觉审查问题处理方案

> 说明：AGENTS.md 规定 Plan 文件默认放在项目根目录 `plan/`。本文件按用户指定路径存放于 `docs/plan/`；如后续统一规划文档目录，应迁移到根目录 `plan/` 或更新 AGENTS.md 的目录规范。

## Context

本方案用于承接“分类视觉组合方案优化 + 预览实时更新”代码审查后的问题处理。审查结论总体可采纳，但部分问题定性需要校准：

- `reactive` 对象逐项 `delete` 不构成内存泄漏，属于低优先级的响应式状态维护优化。
- 色彩校验中的 WCAG 阈值、色距阈值、透明度范围应提取常量，提升可读性和后续维护性。
- `StyleConfigService` 中 Logo 上传逻辑重复明显，应抽取公共私有方法。
- `useCategoryPreviewState.loadCategories` 加载失败时仅记录 `console.error`，调用方无法感知失败，应增加可恢复的错误状态或向上抛出。
- Base64 Logo 存储存在数据库字段容量风险：`sys_style_config.config_value` 为 `TEXT`，1.5MB 图片转 Base64 后可能超过字段容量。
- 前后端和后端关键逻辑缺少针对性测试。

## 问题分级

| 优先级 | 问题 | 判断 | 处理策略 |
|--------|------|------|----------|
| P0 | Base64 Logo 可能超过 `TEXT` 字段容量 | 高风险 | 调整数据库字段类型或改为文件存储方案 |
| P1 | Logo 上传代码重复 | 确认存在 | 抽取公共上传方法，保持现有 API 不变 |
| P1 | 分类视觉加载失败被吞掉 | 确认存在 | 增加错误状态/错误返回，让页面可提示 |
| P2 | 色彩校验魔法数字 | 确认存在 | 提取命名常量并补充说明 |
| P2 | 色彩校验缺测试 | 确认存在 | 补充边界值单元测试 |
| P3 | `reactive` 对象逐项 `delete` | 非内存泄漏 | 作为代码整洁优化，可选处理 |

## 本轮范围

### 必须完成

1. 修复 `sys_style_config.config_value` 字段容量风险，将字段调整为可承载 Base64 Logo 的类型。
2. 重构 `StyleConfigService` 三个 Logo 上传方法的重复逻辑。
3. 收紧 Logo 上传安全校验，避免仅依赖客户端 MIME 类型。
4. 为分类视觉加载失败增加可见错误状态和重试入口。
5. 将分类视觉色彩校验阈值提取为命名常量。
6. 补充后端 Logo 上传服务测试。
7. 按实际代码和数据库变更同步必要文档。

### 本轮暂缓

1. 不引入 OSS、MinIO、本地文件服务等统一文件存储能力。
2. 不重构 `visualConfigs` 为 `ref<Record<...>>`，除非实施中出现明确响应式问题。
3. 不强制引入完整前端测试栈；如引入成本过高，本轮先完成手工验证清单，前端自动化测试另开任务。
4. 不修改现有 Logo 上传 API 路径和返回结构，避免影响前端调用。

## 实现方案

### 1. 数据库容量风险处理

优先选择最小变更方案：

1. 将 `sys_style_config.config_value` 从 `TEXT` 调整为 `MEDIUMTEXT`。
2. 同步更新：
   - `backend/src/main/resources/init.sql`
   - 新增 Flyway migration
   - `docs/dev/项目设计文档.md` 数据库表结构说明
   - 数据字典文档中对应字段说明
3. 保留当前 Base64 存储方式，不引入 OSS 或文件服务，避免扩大改动面。

#### 迁移脚本

新增迁移脚本，命名遵循当前 Flyway 版本顺序，例如：

```text
backend/src/main/resources/db/migration/Vxx__expand_style_config_value_to_mediumtext.sql
```

脚本内容：

```sql
ALTER TABLE sys_style_config
  MODIFY COLUMN config_value MEDIUMTEXT COMMENT '配置值';
```

注意事项：

- 不修改已经发布并可能执行过的历史 migration，只新增 migration。
- 同步修改 `init.sql` 中 `sys_style_config.config_value` 的字段类型，保证新环境初始化结果与迁移后结果一致。
- 如果数据库不支持 `MEDIUMTEXT`，需要在部署文档中注明当前脚本面向 MySQL/MariaDB。

#### ORM 一致性检查

实施时必须检查 `SysStyleConfig`：

- `@Table(name = "sys_style_config")` 表名是否匹配。
- `@Column(name = "config_value")` 是否存在。
- Java 类型应保持 `String`。
- 如当前 `@Column` 有 `length` 限制，应移除或调整。
- 评估是否增加 `@Lob`。如果项目当前 MySQL 方言可正常将 `String` 写入 `MEDIUMTEXT`，可不加；若 Hibernate DDL 校验或生成受影响，再补充 `@Lob`。

备选方案：

- 如后续 Logo、主题资源、图片素材继续增多，再设计统一文件存储服务。
- 当前阶段不建议直接切到 OSS，因为会引入上传鉴权、文件清理、环境配置、生产部署文档更新等额外工作。

### 2. 重构 Logo 上传逻辑

在 `StyleConfigService` 中抽取公共私有方法：

```java
private String uploadLogoToConfig(MultipartFile file, String configKey, String logLabel) throws IOException
```

建议拆分为以下私有方法，降低单个方法复杂度：

```java
private String uploadLogoToConfig(MultipartFile file, String configKey, String logLabel) throws IOException {
    validateLogoFile(file);
    byte[] imageBytes = file.getBytes();
    String mimeType = validateImageContent(imageBytes, file.getContentType());
    String dataUrl = buildDataUrl(imageBytes, mimeType);
    log.info("{} Logo uploaded as Base64, size: {} bytes -> {} chars", logLabel, file.getSize(), dataUrl.length());
    updateConfig(configKey, dataUrl);
    return dataUrl;
}

private void validateLogoFile(MultipartFile file) {
    // 空文件、大小、允许 MIME 类型校验
}

private String validateImageContent(byte[] imageBytes, String contentType) {
    // 图片内容校验，返回最终采用的 MIME 类型
}

private String buildDataUrl(byte[] imageBytes, String mimeType) {
    // Base64 data URL 组装
}
```

保留现有公开方法：

- `uploadLogo(file)` -> `logo_url`
- `uploadLogoLogin(file)` -> `logo_url_login`
- `uploadLogoNav(file)` -> `logo_url_nav`

同时增强上传校验：

- 保留文件大小校验：`MAX_LOGO_SIZE`
- 收紧 MIME 类型白名单：建议仅允许 `image/png`、`image/jpeg`、`image/webp`、`image/gif`
- 增加图片文件头或图片解码校验，避免仅信任客户端 MIME
- 本轮默认不允许 `image/svg+xml`。SVG 如未做消毒，存在脚本与嵌入内容风险；后续如确需支持，应单独设计 SVG 清洗策略。
- 保持 `@Transactional` 和 `@CacheEvict` 行为不变

图片内容校验建议：

- 优先使用 `ImageIO.read(new ByteArrayInputStream(imageBytes))` 验证可解析图片。
- 若 `ImageIO` 对 WebP 支持不足，可对 WebP 采用文件头校验，或本轮先不允许 WebP。
- MIME 类型和文件内容不匹配时拒绝上传。

### 3. 改进分类视觉加载错误处理

在 `useCategoryPreviewState.ts` 中增加错误状态：

```ts
const loadError = ref<string | null>(null)
```

对外导出：

```ts
const clearLoadError = (): void => {
  loadError.value = null
}

export function useCategoryPreviewState() {
  return {
    loadError: computed(() => loadError.value),
    clearLoadError,
    // existing exports
  }
}
```

处理策略：

1. `loadCategories` 开始时清空 `loadError`。
2. 加载失败时记录错误并设置 `loadError`。
3. 保留现有数据，不强行清空 `categories` 和 `visualConfigs`，避免页面闪空。
4. 在 `CategoryVisualPanel.vue` 中根据 `loadError` 显示加载失败提示和“重试”按钮。
5. 点击重试时调用 `categoryState.loadCategories(true)`。
6. 如调用方需要显式失败感知，可让 `loadCategories(force, throwOnError)` 支持可选抛错。

推荐 UI 行为：

- 如果已有旧数据：保留分类列表和当前配置，仅在顶部显示轻量错误提示。
- 如果没有任何数据：显示空态错误提示和重试按钮。
- 错误文案不暴露技术堆栈，例如“分类视觉配置加载失败，请重试”。

### 4. 色彩校验常量化

在 `categoryVisualColor.ts` 中提取常量：

```ts
const WCAG_NORMAL_TEXT_CONTRAST = 4.5
const WCAG_LARGE_TEXT_CONTRAST = 3
const MIN_THEME_COLOR_DISTANCE = 32
const MIN_GLOW_ALPHA = 0.12
const MAX_GLOW_ALPHA = 0.18
```

并补充简短注释说明：

- `4.5`：WCAG AA 普通文本对比度阈值
- `3`：WCAG AA 大字号文本/图形识别阈值
- `32`：项目内用于避免分类主色贴近全局主题色的经验阈值

### 5. `visualConfigs` 清空方式优化

此项不作为 bug 处理。可选方案：

1. 保持当前 `reactive<Record<number, CategoryVisualConfig>>({})`，仅在代码注释中说明逐项清空是为了保留响应式引用。
2. 或改为 `const visualConfigs = ref<Record<number, CategoryVisualConfig>>({})`，加载完成后整体替换。

推荐采用方案 1，原因：

- 当前 `useCategoryPreviewState` 返回 `computed(() => visualConfigs)`，已有组件依赖这个稳定响应式对象。
- 分类数量有限，逐项删除不会形成实际性能瓶颈。
- 避免为了低风险优化扩大改动范围。

### 6. 测试补充

测试按阶段推进，避免为了小范围修复一次性扩大工程配置。

#### 前端测试

当前前端未配置测试脚本。本轮处理策略：

1. 如果团队接受引入测试栈，则新增：
   - Vitest
   - Vue Test Utils（仅在需要测 composable 与组件交互时引入）
2. 如果不引入测试栈，本轮不阻塞交付，但必须完成手工验证清单，并将前端自动化测试作为后续任务。

优先测试 `categoryVisualColor.ts`，因为它是纯函数，测试收益高、环境依赖少：

- `#000000` / `#FFFFFF` 对比度
- 三位 hex 展开逻辑
- 低对比文本色触发 warning
- 主色与白底对比不足触发 warning
- 主题色距离过近触发 warning
- glow alpha 小于 `0.12` 或大于 `0.18` 触发 warning

`useCategoryPreviewState.ts` 测试可放在第二阶段：

- 分类加载成功时写入配置
- 字典 JSON 解析失败时降级到推荐配置
- 加载失败时设置 `loadError`
- 保存失败时继续向上抛错

#### 后端测试

为 `StyleConfigService` 增加测试：

- 空文件上传失败
- 非图片 MIME 上传失败
- 超过大小限制上传失败
- 登录页 Logo 写入 `logo_url_login`
- 导航栏 Logo 写入 `logo_url_nav`
- 默认 Logo 写入 `logo_url`
- 文件内容不是有效图片时失败（如启用图片解码校验）
- SVG 上传失败（本轮默认不允许）
- 伪造 MIME 的文本文件上传失败

## 关键参考文件

| 文件 | 用途 |
|------|------|
| `frontend/src/composables/useCategoryPreviewState.ts` | 分类视觉加载、保存、预览状态 |
| `frontend/src/utils/categoryVisualColor.ts` | 分类视觉色彩校验 |
| `frontend/src/components/style-settings/CategoryVisualPanel.vue` | 分类视觉配置界面 |
| `frontend/src/components/style-settings/StyleSettingsShell.vue` | 样式设置统一保存与错误处理 |
| `backend/src/main/java/com/pricemanagement/service/StyleConfigService.java` | Logo 上传与样式配置持久化 |
| `backend/src/main/java/com/pricemanagement/controller/StyleConfigController.java` | Logo 上传 API |
| `backend/src/main/java/com/pricemanagement/entity/SysStyleConfig.java` | 样式配置实体映射 |
| `backend/src/main/resources/init.sql` | 初始化数据库表结构 |
| `backend/src/main/resources/db/migration/` | 数据库迁移脚本 |
| `docs/dev/项目设计文档.md` | 数据库/API/功能模块设计说明 |

## 实现步骤

1. 检查 `sys_style_config.config_value` 在初始化 SQL、迁移脚本、实体注解和数据字典中的一致性。
2. 新增 migration：`Vxx__expand_style_config_value_to_mediumtext.sql`。
3. 修改 `init.sql`，将 `sys_style_config.config_value` 同步调整为 `MEDIUMTEXT`。
4. 检查并按需调整 `SysStyleConfig.configValue` 的 ORM 注解。
5. 重构 `StyleConfigService` Logo 上传逻辑，抽取公共私有方法。
6. 增加 MIME 白名单和图片内容校验，默认拒绝 SVG。
7. 为 `StyleConfigService` 补充 Logo 上传服务测试。
8. 为 `useCategoryPreviewState` 增加 `loadError` 和 `clearLoadError`。
9. 在 `CategoryVisualPanel.vue` 增加加载失败提示和重试入口。
10. 将 `categoryVisualColor.ts` 中阈值提取为命名常量。
11. 评估是否引入 Vitest；如引入，优先补 `categoryVisualColor.ts` 单元测试。
12. 按 AGENTS.md 要求同步更新 README、开发指南、项目设计文档、完成总结、UI 设计说明中实际受影响的内容。

## Verification

### 静态检查

- 前端执行：
  - `npm run build`
  - 如引入测试脚本，执行 `npm run test`
- 后端执行：
  - `mvn test`

### 功能验证

1. 上传登录页 Logo 后，登录页显示新 Logo。
2. 上传导航栏 Logo 后，登录后导航栏显示新 Logo。
3. 上传接近 1.5MB 的 PNG/JPEG 后，数据库不再出现 `Data too long for column 'config_value'`。
4. 上传超限文件、非图片文件、伪造 MIME 文件、SVG 文件时返回明确错误。
5. 分类视觉配置加载失败时，页面保留已有状态并提示失败。
6. 分类视觉无初始数据且加载失败时，页面显示重试入口。
7. 分类视觉色彩不合规时，仍可保存但展示 warning。
8. 样式设置统一保存后，首页分类视觉预览和实际首页展示一致。

### 一致性检查

- 前端 API 路径与后端 Controller 路径一致：
  - `POST /api/style/logo`
  - `POST /api/style/logo/login`
  - `POST /api/style/logo/nav`
- DTO、TypeScript 类型、数据库字段说明一致。
- `@Column` 与实际数据库字段类型一致。
- 数据字典和项目设计文档中的 `sys_style_config` 表结构一致。

### 验收标准

本方案实施完成后，至少满足以下条件：

1. `sys_style_config.config_value` 在新建环境和迁移后环境中均为 `MEDIUMTEXT`。
2. 三个 Logo 上传 API 的外部行为保持兼容，返回值仍为 Base64 data URL。
3. Logo 上传公共校验逻辑只有一份，公开方法只负责传入配置 key。
4. 非白名单图片类型、SVG、伪造图片内容均无法保存。
5. 分类视觉加载失败时用户可见，并可点击重试。
6. `categoryVisualColor.ts` 不再出现未命名的阈值数字。
7. `mvn test` 通过。
8. `npm run build` 通过。
9. 文档中数据库表结构、API 行为和 UI 交互说明与代码一致。

## 不处理项说明

`visualConfigs` 使用 `reactive` 并逐项 `delete` 不作为内存泄漏修复处理。除非后续出现明确性能证据，否则仅作为可选整洁优化，不进入本轮必须修复范围。

统一文件存储服务不进入本轮。当前问题的直接风险是数据库字段容量与上传校验不足，采用 `MEDIUMTEXT` 和更严格的图片校验即可闭环；文件服务设计应单独评估鉴权、清理、部署、备份与访问控制。
