
# IDEA部署指南（中文环境）

## 前置条件

在开始之前，请确保您已安装以下软件：

1. **IntelliJ IDEA**（推荐使用 Ultimate 版本，Community 版本也可以）
2. **Java JDK 25** 或更高版本
3. **MySQL 8.4** 或更高版本
4. **Node.js 16** 或更高版本（用于前端开发）
5. **Git**（可选，用于版本控制）

---

## 第一步：准备Java环境

### 1.1 检查Java是否已安装

打开IDEA，点击顶部菜单栏：
- **文件** → **项目结构**（快捷键：`Ctrl+Alt+Shift+S`）

在左侧导航栏选择 **SDKs**：
- 如果已经看到有 Java 25 的 SDK，说明已安装
- 如果没有，点击 **+** 号 → **添加SDK** → **下载JDK**

### 1.2 下载并配置Java 25

1. 在 **下载JDK** 对话框中：
   - 版本：选择 `25`
   - 供应商：选择 `Eclipse Temurin` 或 `AdoptOpenJDK`
   - 点击 **下载**

2. 下载完成后，会自动添加到SDK列表中

---

## 第二步：导入后端项目

### 2.1 打开项目

1. 打开 IntelliJ IDEA
2. 在欢迎界面点击 **打开**（Open）
3. 浏览到项目目录：`E:\ClaudeCodeProject\price-management-system\backend`
4. 点击 **确定**（OK）

### 2.2 等待Maven项目加载

IDEA会自动识别这是一个Maven项目，并开始下载依赖：

1. 观察右下角的进度条
2. 等待所有依赖下载完成（可能需要几分钟）
3. 确保右下角没有红色报错

### 2.3 Maven刷新（如果需要）

如果依赖下载有问题，可以手动刷新：

1. 点击右侧的 **Maven** 工具窗口
2. 点击顶部的 **刷新** 图标（两个旋转箭头）
3. 或右键点击项目根目录 → **Maven** → **重新加载项目**

---

## 第三步：配置数据库

### 3.1 创建数据库和用户

**方式一：使用MySQL命令行**

```bash
# 登录MySQL
mysql -u root -p

# 创建数据库
CREATE DATABASE IF NOT EXISTS price_management DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# （可选）创建专用数据库用户
CREATE USER IF NOT EXISTS 'pricemanagement'@'localhost' IDENTIFIED BY '【敏感-已移除】';
GRANT ALL PRIVILEGES ON price_management.* TO 'pricemanagement'@'localhost';
FLUSH PRIVILEGES;

EXIT;
```

**方式二：使用MySQL Workbench**

1. 打开MySQL Workbench
2. 使用root用户连接到本地MySQL服务器
3. 点击 **创建新的SQL查询**
4. 执行上述SQL语句

### 3.2 执行数据库初始化脚本

1. 在MySQL Workbench中，选择 `price_management` 数据库
2. 打开文件：`E:\ClaudeCodeProject\price-management-system\backend\src\main\resources\init.sql`
3. 点击 **执行**（闪电图标）按钮
4. 确认所有表创建和数据插入成功

> 已部署环境通过 Flyway 执行 `V14__rebalance_font_size_presets.sql` 更新样式设置字号预设；新环境执行 `init.sql` 时已包含同样的字号刻度。

> 首页产品产地展示复用既有 `product.origin_ids` 与 `origin` 字典数据，不需要新增数据库迁移；升级时重新打包后端和前端即可。
> 首页产品表新增 `home_layout.product_list_mode` 与 `home_layout.product_table_page_size` 配置项，由启动初始化补齐；如生产环境已有字典数据，升级后重启后端即可自动补充缺失项。
> 日常价格查询功能通过 Flyway 执行 `V15__daily_price_query_permissions.sql` 补充“价格查询”菜单和 `price:export` 权限；新环境执行 `init.sql` 时已包含同样的菜单和权限数据。

> 年度预算管理通过 Flyway 执行 `V44__product_annual_budget.sql` 新增 `product_annual_budget` 表和“预算管理”菜单；新环境执行 `init.sql` 时已包含同样表结构与菜单数据。
> 价格查询指标洞察通过 Flyway 执行 `V45__price_metric_dict.sql` 新增 `price_metric_group` 与 `price_metric` 字典项；升级后可在“基础运维 -> 字典管理”查看并维护指标分组、名称和说明，新环境执行 `init.sql` 时已包含同样数据。
> `V16__normalize_price_query_menu.sql` 会将 `/price-query` 归一化为“产品管理”下唯一的“价格查询”二级菜单，避免历史环境出现同路径重复菜单或普通用户可见但菜单管理不可见的情况。
> `V17__external_api_auth_phase1.sql` 会新增外部 API 授权管理表、字典、菜单和 `/api/external/v1/**` 端点权限。该功能默认关闭，不影响当前内部 JWT 功能。
> `V19__external_api_endpoint_code_examples.sql` 会为外部 API 端点补充结构化示例、参数 schema 和可复制代码元数据。
> `V20__external_api_runtime_service_switch.sql` 会新增外部 API 运行时服务开关配置，允许后台页面即时暂停/恢复外部 API。
> `V22__personal_profile_management.sql` 会扩展 Refresh Token 设备信息，并新增登录历史与个人偏好表，用于个人中心账号运维。
> `V23__price_draft_publish_notification.sql` 会新增价格草稿/发布日志、站内通知、通用定时任务表和相关字典项。默认价格自动发布任务为停用状态，升级后需管理员在“系统管理 -> 定时任务”确认后手动启用。
> 价格维护“发布全部草稿”升级仅新增后端接口和前端调用，不新增数据库表字段或迁移脚本；升级时重新打包后端、PC 前端和小程序前端即可。
> `V28__notification_phase3_frequency_rules.sql` 会新增通知聚合频控默认规则字典；`V29__notification_provider_health_status_dict.sql` 会新增 Provider 健康状态字典；`V30__notification_aggregate_event_count.sql` 会为通知消息增加聚合事件计数字段；`V31__notification_mini_program_subscription.sql` 会新增小程序订阅授权表和授权状态字典；`V35__notification_mini_program_resolution.sql` 会新增用户级订阅异常处理表；`V36__notification_operations_hardening.sql` 会增加测试投递隔离字段、异常处理乐观锁、细粒度权限并清理历史敏感操作参数；`V37__notification_mini_resolution_status_dict.sql` 会增加异常处理状态字典；`V38__notification_mini_program_eligibility.sql` 会增加小程序订阅用户资格查询快照及状态分页索引；`V39__system_setting_permission_backfill.sql` 会补齐并启用 `system:setting` 权限及 ADMIN 授权，修复历史库保存通知渠道配置时的 403；`V40__notification_mini_program_page_dict.sql` 会补充小程序通知跳转页字典；`V41__notification_mini_program_template_window.sql` 会新增小程序模板版本/历史表和模板状态字典；`V42__notification_mini_program_template_active_unique.sql` 会停用同通知类型的历史重复 ACTIVE，并增加生成列唯一索引。升级后需完成 Flyway 校验并重新打包前端。
> 字典管理分类页签、使用说明和效果展示升级仅涉及前端页面与静态分类元数据，不需要新增数据库迁移；升级时重新打包前端即可。
> Spring Boot 4 需要 `spring-boot-starter-flyway` 才会在启动时自动执行 Flyway。历史库首次接入 Flyway 时会 baseline 到 V12，然后自动执行 V13-V20；空库仍从 V1 开始完整迁移。

> 注意：`init.sql` 包含完整的表结构创建和数据初始化，推荐使用此脚本一步完成初始化。

### 3.4 验证数据是否正确

在MySQL Workbench中执行以下查询验证：

```sql
USE price_management;

-- 查看产品分类（应该有5条记录）
SELECT * FROM product_category;

-- 查看产品数据（应该有20条记录）
SELECT * FROM product;

-- 验证首页产品列表排序字段
SELECT id, name, sort_order, status FROM product_category ORDER BY sort_order;
SELECT id, name, category_id, sort_order, show_on_home, status FROM product ORDER BY category_id, sort_order;

-- 验证日常价格查询菜单和导出权限
SELECT pq.id, p.name AS parent_name, pq.name, pq.path, pq.roles
FROM menu_item pq
LEFT JOIN menu_item p ON p.id = pq.parent_id
WHERE pq.path = '/price-query';
SELECT path, COUNT(*) AS count FROM menu_item WHERE path = '/price-query' GROUP BY path;
SELECT permission_code, permission_name FROM sys_permission WHERE permission_code IN ('price:view', 'price:export');

-- 验证外部 API 授权管理表和菜单
SHOW TABLES LIKE 'sys_api_%';
SELECT method, path_pattern, permission_code FROM sys_external_api_endpoint ORDER BY sort_order LIMIT 20;
SELECT COUNT(*) AS code_example_count FROM sys_external_api_endpoint WHERE query_schema IS NOT NULL OR body_schema IS NOT NULL OR path_params_schema IS NOT NULL;
SELECT config_key, config_value FROM sys_style_config WHERE config_key = 'external_api_service_enabled';
SELECT category, dict_key, dict_value FROM sys_dict WHERE category IN ('api_key_status', 'api_key_environment', 'api_auth_result', 'api_permission') ORDER BY category, sort_order;
SELECT name, path FROM menu_item WHERE path IN ('/api-keys', '/api-call-logs') OR name = 'API授权管理';

-- 查看用户数据（应该有3条记录）
SELECT username, role, status FROM sys_user;
```

### 3.5 配置应用连接数据库

在IDEA中打开配置文件：`E:\ClaudeCodeProject\price-management-system\backend\src\main\resources\application.yml`

确认以下配置（根据您的实际情况）：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/price_management?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root  # 或您创建的专用用户
    password: 【敏感-已移除】  # 修改为您的实际密码
    driver-class-name: com.mysql.cj.jdbc.Driver

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.MySQLDialect

  flyway:
    enabled: true
    baseline-on-migrate: true
    baseline-version: 12
    locations: classpath:db/migration
    encoding: UTF-8
    validate-on-migrate: true
```

**重要提示：**
- 不要将 `ddl-auto` 改为 `update` 或 `create-drop`；表结构统一由 Flyway 迁移脚本管理，JPA 只负责启动校验
- 旧库如果没有 `flyway_schema_history`，启动时会自动建立基线记录并补跑 V13 及之后的迁移
- 如果您使用单独创建的用户，请修改 `username` 和 `password`

### 3.6 外部 API 授权配置（可选）

外部 API 授权管理默认关闭。创建后台 API Key 时必须配置 `API_KEY_ENCRYPTION_KEY`，因为服务端需要用它加密保存 Secret；`API_KEY_ENABLED=true` 仅表示启用 `/api/external/**` 外部签名鉴权入口。

| 环境变量 | 说明 | 示例 |
|----------|------|------|
| `API_KEY_ENABLED` | 是否启用外部 API，默认 `false` | `true` |
| `API_KEY_ENCRYPTION_KEY` | Base64 编码 32 字节 AES-GCM 主密钥 | 使用安全随机值 |
| `API_KEY_ENCRYPTION_KEY_VERSION` | 主密钥版本 | `v1` |
| `API_KEY_TIMESTAMP_WINDOW_SECONDS` | 签名时间戳窗口 | `300` |
| `API_KEY_NONCE_TTL_SECONDS` | Nonce 防重放 TTL | `600` |
| `API_KEY_CACHE_TTL_SECONDS` | 授权缓存 TTL 预留配置，第一阶段授权元数据实时查库 | `300` |
| `API_KEY_LOG_RETENTION_DAYS` | 调用日志保留天数 | `180` |

PowerShell 生成开发用 32 字节 Base64 key：

```powershell
$bytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
[Convert]::ToBase64String($bytes)
```

开发环境默认会从 `application-dev.yml` 使用开发兜底 key，允许本地直接创建 API Key；如需模拟生产密钥，可在 IDEA 的后端 Run Configuration 中覆盖 Environment variables，例如 `API_KEY_ENCRYPTION_KEY=...;API_KEY_ENCRYPTION_KEY_VERSION=v1`。启用后，外部系统只允许调用 `/api/external/v1/**`，内部后台页面仍使用 JWT。生产环境禁止使用 `application-dev.yml` / `application.yml.example` 中的示例 key。

### 3.7 通知 Outbox 配置（可选）

通知中心外部渠道通过 Outbox worker 异步投递。未接入外部 Provider 时，worker 会把对应投递日志记录为 `SKIPPED/PROVIDER_NOT_CONFIGURED`，不影响价格发布等业务事务。

| 环境变量 | 说明 | 默认值 |
|----------|------|--------|
| `NOTIFICATION_OUTBOX_ENABLED` | 是否启用通知 Outbox worker | `true` |
| `NOTIFICATION_OUTBOX_BATCH_SIZE` | 每轮领取任务数量 | `20` |
| `NOTIFICATION_OUTBOX_MAX_RETRIES` | 外部 Provider 失败最大重试次数 | `3` |
| `NOTIFICATION_OUTBOX_LOCK_SECONDS` | 单条任务处理锁定秒数 | `120` |
| `NOTIFICATION_OUTBOX_POLL_DELAY_MS` | worker 轮询间隔毫秒 | `30000` |

Webhook Provider MVP 默认关闭；需要联调外部通知接收方时，在 IDEA 后端 Run Configuration 的 Environment variables 中追加：

| 环境变量 | 说明 | 默认值 |
|----------|------|--------|
| `NOTIFICATION_WEBHOOK_ENABLED` | 是否启用 Webhook Provider | `false` |
| `NOTIFICATION_WEBHOOK_URL` | Webhook 接收地址 | 空 |
| `NOTIFICATION_WEBHOOK_SECRET` | Webhook HMAC 签名密钥 | 空 |
| `NOTIFICATION_WEBHOOK_TIMEOUT_MS` | Provider HTTP 超时毫秒 | `5000` |

Provider 调用会使用 `delivery-{notification_delivery_log.id}` 作为幂等键；未配置时投递日志记录为 `SKIPPED/PROVIDER_NOT_CONFIGURED`，超时或非 2xx 记录为 `FAILED`，不会阻断价格发布、审批、导入导出等业务事务。

微信小程序订阅消息 Provider 默认关闭。启用前必须完成小程序 AppID/AppSecret、订阅消息模板审核、用户微信登录 openid 绑定和小程序端授权入口联调。

这些值需要从微信小程序官方后台获取，不由本系统生成：

1. 登录微信公众平台 `https://mp.weixin.qq.com`，选择对应小程序。
2. 在小程序后台的开发设置/开发者 ID 中获取 `AppID`；`AppSecret` 由具备权限的管理员生成或重置，生成后必须按密钥处理。
3. 在小程序后台进入订阅消息，选择公共模板或申请模板，加入“我的模板”后获取模板 ID 和字段编号，例如 `phrase2`、`thing4`、`thing1`、`time2`。
4. 将 AppID、AppSecret、模板 ID 和字段映射配置到后端环境变量或 PC `/notifications -> 渠道配置` 中。PC 页面只能显示 Provider 是否启用、是否配置完整、脱敏 ID 和投递健康状态，不会也不应明文展示 `AppSecret`。

`application.yml` 不再预置具体 AppID 或正式模板 ID，新环境默认显示未配置。对话、截图或历史介质中暴露过的 AppSecret 必须在微信公众平台重置后，再通过 `WECHAT_MINI_APP_SECRET` 或 PC 密钥托管入口注入运行环境。

| 环境变量 | 说明 | 默认值 |
|----------|------|--------|
| `WECHAT_MINI_NOTIFY_ENABLED` | 是否启用小程序订阅消息 Provider | `false` |
| `WECHAT_MINI_ELIGIBILITY_RECONCILE_CRON` | 小程序订阅资格快照每日校准 Cron | `0 30 3 * * ?` |
| `WECHAT_MINI_APP_ID` | 微信小程序 AppID | 空 |
| `WECHAT_MINI_APP_SECRET` | 微信小程序 AppSecret | 空 |
| `WECHAT_MINI_TEMPLATE_PRICE_PUBLISHED` | 价格发布订阅模板 ID | 空 |
| `WECHAT_MINI_TEMPLATE_SYSTEM_NOTICE` | 系统公告订阅模板 ID | 空 |
| `WECHAT_MINI_PRICE_FIELD_TYPE` | 价格发布模板“类型”字段 | `phrase2` |
| `WECHAT_MINI_PRICE_FIELD_TIP` | 价格发布模板“温馨提示”字段 | `thing4` |
| `WECHAT_MINI_NOTICE_FIELD_CREATOR` | 系统公告模板“创建人”字段 | `thing1` |
| `WECHAT_MINI_NOTICE_FIELD_TIME` | 系统公告模板“创建时间”字段 | `time2` |
| `WECHAT_MINI_PRICE_PAGE` | 价格发布订阅消息点击后的小程序页面 | `pages/home/index` |
| `WECHAT_MINI_NOTICE_PAGE` | 系统公告订阅消息点击后的小程序页面 | `pages/notifications/index` |
| `WECHAT_MINI_NOTIFY_TIMEOUT_MS` | 微信接口超时毫秒 | `5000` |

推荐在 IDEA 后端 Run Configuration 的 Environment variables 中至少追加：

```text
WECHAT_MINI_NOTIFY_ENABLED=true;WECHAT_MINI_APP_ID=微信小程序AppID;WECHAT_MINI_APP_SECRET=重置后的微信小程序密钥
```

如微信后台更换了模板或字段编号，再覆盖对应 `WECHAT_MINI_TEMPLATE_*`、`WECHAT_MINI_PRICE_FIELD_*`、`WECHAT_MINI_NOTICE_FIELD_*` 环境变量。不要把 `WECHAT_MINI_APP_SECRET` 写入 `application.yml`、前端代码、文档或普通日志。

`MINI_PROGRAM` 投递依赖用户授权次数。未配置、未绑定 openid、未授权模板会记录为 `SKIPPED`；微信接口超时、HTTP 非 2xx 或临时错误会记录为 `FAILED` 并走 Outbox 重试状态机；模板无效、字段错误、用户拒绝/授权失效等永久错误会记录为 `SKIPPED`，授权失效会同步清空本地次数。PC `/notifications` 仍是统一发布入口，小程序只负责授权和接收，站内通知始终兜底。

公网正式微信小程序请求地址为 `https://price.jlmining.com:32080`，生产机由 `price-management-frontend` Nginx 容器在 32080 端口终止 TLS。公司内网真机调试可使用独立 HTTP 入口 `http://10.7.5.175:32801`，该入口不能配置为微信 request 合法域名，只能在真机调试开启“不校验合法域名”时使用。证书与私钥部署在项目 `certs/` 目录并只读挂载，不得提交仓库。部署后必须同时检查宿主机端口映射和容器内部监听，确保容器实际监听 `443`、`32080`、`32801`；仅有 Docker 映射但容器未监听时，小程序请求会报 `ERR_CONNECTION_REFUSED`。

生产环境必须设置 `RESET_PASSWORD_ON_STARTUP=false`。主配置默认值也是 `false`，避免后端重启时重置默认用户密码；仅开发环境可按需显式启用。

通知三期新增 SSE 轻事件接口 `/api/notifications/events`。生产反向代理需要允许长连接和流式响应；若代理或浏览器断开连接，PC 前端会自动回退到轮询，不影响站内消息列表。

---

## 第四步：启动后端应用

### 4.1 找到主启动类

在IDEA左侧的项目视图中：
1. 展开 `src` → `main` → `java` → `com.pricemanagement`
2. 找到 `PriceManagementApplication.java` 文件

### 4.2 运行应用

**方式一：右键运行**
1. 右键点击 `PriceManagementApplication.java`
2. 选择 **运行 'PriceManagementApplication.main()'**

**方式二：使用运行按钮**
1. 点击 `PriceManagementApplication.java` 文件
2. 点击代码行号旁边的绿色三角形图标
3. 或使用快捷键 `Shift+F10`

### 4.3 查看控制台输出

观察IDEA底部的 **控制台**（Console）标签页：

**成功启动的标志：**
```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v4.0.6)

... 省略其他日志 ...

Started PriceManagementApplication in X.XXX seconds
```

### 4.4 测试后端API

**方式一：使用浏览器**
打开浏览器访问：`http://localhost:8080/actuator/health`
（如果没有配置actuator，可以跳过此步）

**方式二：使用Postman或其他API工具**
测试登录接口：
- URL：`http://localhost:8080/api/auth/login`
- 方法：POST
- Body（JSON）：
```json
{
  "username": "admin",
  "password": "【敏感-已移除】"
}
```

---

## 第五步：配置IDEA的数据库工具（可选但推荐）

### 5.1 打开Database工具窗口

1. 点击IDEA右侧的 **Database** 工具窗口
2. 如果没有看到，点击顶部菜单 **视图** → **工具窗口** → **Database**

### 5.2 添加MySQL数据源

1. 点击 **Database** 窗口左上角的 **+** 号
2. 选择 **数据源** → **MySQL**

### 5.3 配置连接信息

在弹出的配置对话框中填写：
- **主机**：`localhost`
- **端口**：`3306`
- **用户**：`root`（或您创建的用户）
- **密码**：`【敏感-已移除】`
- **数据库**：`price_management`

### 5.4 测试并保存连接

1. 点击 **测试连接**（Test Connection）
2. 如果显示绿色对勾，说明连接成功
3. 点击 **确定** 保存

### 5.5 使用Database工具操作数据库

- 展开数据源可以查看所有表
- 双击表名可以查看数据
- 可以直接在IDEA中执行SQL查询
- 右键表可以进行导入/导出操作

---

## 第六步：启动前端应用

### 6.1 打开前端项目

**方式一：在同一IDEA窗口中打开**
1. 点击IDEA顶部的 **文件** → **打开**
2. 选择 `E:\ClaudeCodeProject\price-management-system\frontend`
3. 选择 **在新窗口中打开**（推荐）或 **附加到当前项目**

**方式二：使用其他编辑器**
也可以使用 VS Code 或其他编辑器打开前端项目

### 6.2 安装前端依赖

1. 在IDEA中打开 **终端**（Terminal）窗口
2. 确认当前目录是 `frontend`
3. 运行以下命令：
```bash
npm install
```

**注意：**
- 如果安装很慢，可以使用国内镜像：
  ```bash
  npm install --registry=https://registry.npmmirror.com
  ```
- 首次安装可能需要几分钟，请耐心等待

### 6.3 启动前端开发服务器

安装依赖完成后，在终端运行：
```bash
npm run dev
```

**成功启动标志：**
```
  VITE v5.x.x  ready in XXX ms

  ➜  Local:   http://localhost:5173/
  ➜  Network: use --host to expose
```

### 6.4 访问系统

1. 浏览器会自动打开 `http://localhost:5173`
2. 如果没有自动打开，手动在浏览器中访问
3. 使用默认用户登录：
   - 用户名：`admin`
   - 密码：`【敏感-已移除】`

---

## 第七步：调试功能

### 7.1 后端调试

#### 设置断点
1. 在代码行号左侧点击，会出现红色圆点（断点）
2. 或在代码行使用快捷键 `Ctrl+F8`

#### 以调试模式运行
1. 右键点击 `PriceManagementApplication.java`
2. 选择 **调试 'PriceManagementApplication.main()'**
3. 或使用快捷键 `Shift+F9`

#### 调试控制
- **F8**：步过（Step Over）
- **F7**：步入（Step Into）
- **Shift+F8**：步出（Step Out）
- **F9**：继续运行到下一个断点
- **Ctrl+F8**：取消断点

#### 查看变量值
- 使用IDEA底部的 **调试器**（Debugger）工具窗口
- 可以查看当前作用域内的所有变量值
- 使用 **计算表达式**（Evaluate Expression）功能计算任意表达式

### 7.2 前端调试

#### 使用浏览器开发者工具
1. 在浏览器中按 `F12` 打开开发者工具
2. **元素**（Elements）：查看和修改HTML/CSS
3. **控制台**（Console）：查看日志和运行JavaScript
4. **网络**（Network）：查看网络请求
5. **源代码**（Sources）：设置断点和调试

---

## 常见问题排查

### 问题1：Java版本不匹配

**症状**：运行时报错，提示Java版本问题

**解决方法**：
1. **文件** → **项目结构** → **项目**
2. 确保 **SDK** 选择的是 Java 25
3. 确保 **语言级别**（Language level）是 `25 - Type Diagrams, Implicit Class Types`
4. **文件** → **设置** → **构建、执行、部署** → **编译器** → **Java编译器**
5. 确保 **模块字节码版本**（Project bytecode version）是 25

### 问题2：Maven依赖下载失败

**症状**：右下角Maven下载进度条不动或报错

**解决方法**：
1. 检查网络连接
2. 配置国内Maven镜像：
   - 编辑 `~/.m2/settings.xml`（Windows通常在 `C:\Users\用户名\.m2\settings.xml`）
   - 添加阿里云镜像：
     ```xml
     <mirrors>
       <mirror>
         <id>aliyun</id>
         <mirrorOf>central</mirrorOf>
         <name>Aliyun Maven</name>
         <url>https://maven.aliyun.com/repository/public</url>
       </mirror>
     </mirrors>
     ```
3. 在IDEA中刷新Maven项目

### 问题3：数据库连接失败

**症状**：启动时报错，提示无法连接数据库

**检查清单**：
1. MySQL服务是否已启动？
2. 数据库名称、用户名、密码是否正确？
3. 端口3306是否被占用？
4. `application.yml` 中的连接URL是否正确？
5. 是否添加了 `allowPublicKeyRetrieval=true` 参数？

### 问题3.1：JPA 校验缺少表

**症状**：启动时报 `Schema validation: missing table [sys_api_call_log]` 或其他 `missing table`。

**处理方法**：
1. 确认 `pom.xml` 包含 `spring-boot-starter-flyway`、`flyway-core`、`flyway-mysql`
2. 确认 `spring.flyway.enabled=true` 且 `ddl-auto=validate`
3. 重启后端，让 Flyway 先执行迁移，再由 JPA 校验表结构
4. 如旧库没有 `flyway_schema_history`，系统会 baseline 到 V12 并补跑 V13-V20
5. 可执行 `SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;` 确认迁移状态

### 问题3.2：Flyway 校验失败

**症状**：启动时报 `Migration checksum mismatch`。

**处理方法**：
1. 不要修改已经在数据库执行成功的历史迁移文件，例如 `V17__external_api_auth_phase1.sql`
2. 新的表结构或初始化数据变更必须创建新的迁移版本，例如 `V18__external_api_endpoint_docs.sql`
3. 个人中心修改密码策略可通过 `PASSWORD_MIN_LENGTH`、`PASSWORD_MAX_LENGTH`、`PASSWORD_REQUIRE_LETTER`、`PASSWORD_REQUIRE_DIGIT`、`PASSWORD_DISALLOW_WHITESPACE` 调整；生产环境建议保持默认强校验
3. 确认代码中的历史迁移文件已恢复后，再重启后端
4. 如历史迁移文件确认无误但本地库仍记录旧校验值，可在开发环境执行 Flyway repair；生产环境必须先备份并确认迁移文件来源一致

### 问题3.3：Tomcat 临时目录权限失败

**症状**：本地启动时报 `Existing directory ... Temp ... is not owned by ...`。

**处理方法**：
1. 开发环境默认在 `application-dev.yml` 配置 `server.tomcat.basedir=./target/tomcat`，避免使用系统临时目录导致用户归属校验失败
2. 如果需要自定义目录，可在 IDEA Run Configuration 中配置环境变量 `TOMCAT_BASEDIR=E:\tmp\price-management-tomcat`
3. 删除旧的临时目录后重启后端

### 问题4：前端端口被占用

**症状**：运行 `npm run dev` 时报错，提示5173端口已被占用

**解决方法**：
1. 修改前端配置文件 `vite.config.ts`：
   ```typescript
   export default defineConfig({
     server: {
       port: 5174  // 改为其他端口
     }
   })
   ```
2. 或找到占用端口的进程并结束它

### 问题5：前端无法访问后端API

**症状**：前端请求API时报错，或返回404

**检查清单**：
1. 后端服务是否已启动？
2. 后端是否运行在8080端口？
3. 前端API请求的URL是否正确？
4. CORS配置是否正确？

---

## 常用快捷键（中文IDEA）

### 导航和编辑
- **Ctrl + N**：查找类
- **Ctrl + Shift + N**：查找文件
- **Ctrl + F**：当前文件中查找
- **Ctrl + Shift + F**：全项目查找
- **Ctrl + Alt + L**：格式化代码
- **Ctrl + /**：行注释
- **Ctrl + Shift + /**：块注释

### 运行和调试
- **Shift + F10**：运行
- **Shift + F9**：调试
- **F8**：步过
- **F7**：步入
- **Shift + F8**：步出
- **F9**：继续
- **Ctrl + F8**：切换断点

### 项目和视图
- **Alt + 1**：项目视图
- **Alt + 4**：运行窗口
- **Alt + 5**：调试窗口
- **Alt + 9**：版本控制
- **Ctrl + Alt + Shift + S**：项目结构
- **Ctrl + Alt + S**：设置

---

## 下一步

现在您已经成功在IDEA中部署了项目！接下来可以：

1. **探索系统功能**：使用admin用户登录，体验各个功能模块
2. **修改代码**：尝试修改一些代码，观察效果
3. **编写测试**：为您的代码添加单元测试
4. **提交代码**：如果使用Git，可以提交您的更改
5. **阅读文档**：查看其他项目文档了解更多细节

---

## 生产环境部署

### 生产环境要求

#### 硬件配置
- **CPU**: 4核及以上
- **内存**: 8GB及以上
- **硬盘**: 100GB及以上（建议使用 SSD）
- **网络**: 100Mbps及以上

#### 软件配置
- **操作系统**: CentOS 7.6+ 或 Ubuntu 18.04+
- **Java**: OpenJDK 25+
- **MySQL**: 8.0+
- **Nginx**: 1.16+（用于部署前端）

### 后端部署

#### 1. 打包项目
```bash
cd backend
mvn clean package -DskipTests
```

打包成功后，会在 `target` 目录下生成 `price-management-backend-1.0.0.jar` 文件。

#### 2. 部署到服务器

**方式一：直接运行 JAR 包**
```bash
# 创建部署目录
mkdir -p /opt/pricemanagement/backend

# 上传 JAR 包到服务器
scp target/price-management-backend-1.0.0.jar user@server:/opt/pricemanagement/backend/

# 配置应用（创建 application.yml）
cat > /opt/pricemanagement/backend/application.yml <<EOF
server:
  port: 8080

spring:
  application:
    name: price-management-system
  datasource:
    url: jdbc:mysql://localhost:3306/price_management?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: pricemanagement
    password: your_strong_password
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false

jwt:
  secret: your_jwt_secret_key
  expiration: 86400000

logging:
  level:
    com.pricemanagement: info
EOF

# 运行应用
cd /opt/pricemanagement/backend
nohup java -jar price-management-backend-1.0.0.jar --spring.config.location=./application.yml > app.log 2>&1 &
```

**方式二：使用 Systemd 服务**

创建服务文件 `/etc/systemd/system/pricemanagement-backend.service`：
```ini
[Unit]
Description=Price Management System Backend
After=syslog.target network.target

[Service]
Type=simple
User=pricemanagement
WorkingDirectory=/opt/pricemanagement/backend
ExecStart=/usr/bin/java -jar /opt/pricemanagement/backend/price-management-backend-1.0.0.jar
Restart=always
RestartSec=30

[Install]
WantedBy=multi-user.target
```

启用并启动服务：
```bash
systemctl daemon-reload
systemctl enable pricemanagement-backend.service
systemctl start pricemanagement-backend.service
systemctl status pricemanagement-backend.service
```

### 前端部署

#### 1. 打包项目
```bash
cd frontend
npm run build
```

打包成功后，会在 `dist` 目录下生成生产环境的文件。

如需打包 uni-app H5：

```bash
cd frontend-uniapp
npm run typecheck
npm run build:h5
```

#### 2. 部署到 Nginx

创建配置文件 `/etc/nginx/conf.d/pricemanagement.conf`：
```nginx
server {
    listen 80;
    server_name your.domain.com;
    root /var/www/pricemanagement/frontend;
    index index.html;

    # 静态资源缓存
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
        expires 30d;
        add_header Cache-Control "public, immutable";
    }

    # 前端路由重定向
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API 接口代理
    location /api {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        client_max_body_size 10M;
    }
}
```

部署静态文件：
```bash
mkdir -p /var/www/pricemanagement/frontend
scp -r frontend/dist/* user@server:/var/www/pricemanagement/frontend/
nginx -t && nginx -s reload
```

### 数据库初始化（生产环境）

```bash
mysql -u root -p
CREATE DATABASE IF NOT EXISTS price_management DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
EXIT;
mysql -u root -p price_management < /path/to/backend/src/main/resources/init.sql
```

> 注意：用户数据由 Spring Boot 应用启动时的 DataInitializer 自动初始化，密码统一为 【敏感-已移除】。

#### 用户角色关联修复（重要）

如果系统已运行但样式设置保存失败（Access Denied），需执行以下 SQL 修复用户角色关联：

```sql
-- 为默认用户分配角色（修复 sys_user_role 表缺失数据）
INSERT INTO sys_user_role (user_id, role_id, created_time)
SELECT u.id, r.id, NOW()
FROM sys_user u
JOIN sys_role r ON r.role_code = u.role
WHERE NOT EXISTS (
    SELECT 1 FROM sys_user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id
);
```

执行后重新登录即可正常使用管理员功能。

### 安全配置

1. **数据库安全**：使用强密码，限制数据库用户访问IP，定期备份
2. **应用安全**：使用 HTTPS，定期更新依赖库，启用日志审计
3. **外部 API 安全**：启用 `API_KEY_ENABLED=true` 前必须配置独立 `API_KEY_ENCRYPTION_KEY`，并确认外部访问入口只暴露 `/api/external/v1/**`
4. **服务器安全**：配置防火墙，禁用不必要的服务，定期更新系统

### 版本升级

用户安全稳定性整改不涉及数据库结构变更，无需新增 Flyway migration。可通过
`USER_IMPORT_MAX_ROWS` 设置单次用户 Excel 导入最大数据行数，默认值为 `1000`。
升级后仍需确认 Flyway validate 通过，并回归管理员新增、受控更新和用户导入：
参数或导入预检错误应返回 HTTP 400，用户名、工号或写入阶段唯一约束冲突应返回 HTTP 409；
导入存在任一异常时不得写入任何用户，前端对同一失败请求只显示一次安全原因。
第二轮发布还需确认重置密码 URL 不含密码、停用角色不授权、管理员原子编辑失败零更新、
`deptId: null` 可清空部门，以及合法导入使用批量写入。本轮仍无数据库结构变更。

```bash
# 后端升级
systemctl stop pricemanagement-backend.service
cp -r /opt/pricemanagement/backend /opt/pricemanagement/backend.backup
scp target/price-management-backend-1.0.1.jar user@server:/opt/pricemanagement/backend/
systemctl start pricemanagement-backend.service

# 前端升级
scp -r frontend/dist/* user@server:/var/www/pricemanagement/frontend/
nginx -s reload
```

---

祝开发顺利！如有问题，请查看其他项目文档或联系开发团队。
