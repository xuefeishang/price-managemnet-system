# 外部 API 生产部署检查清单

本文档用于外部 API 授权管理功能上线前检查。开发环境允许使用 `application-dev.yml` 中的兜底 key；生产环境必须显式配置独立随机密钥，并以 `prod` profile 启动。

---

## 1. 环境边界

| 环境 | Profile | API Key 加密主密钥来源 | 说明 |
|------|---------|------------------------|------|
| 本地开发 | `dev` | `application-dev.yml` 兜底，或环境变量覆盖 | 方便本地创建 API Key 和联调 |
| 生产 Docker | `prod` | `.env` 中的 `API_KEY_ENCRYPTION_KEY` | 禁止使用开发兜底 key |

生产部署必须确认：

```env
SPRING_PROFILES_ACTIVE=prod
API_KEY_ENCRYPTION_KEY=生产随机Base64密钥
API_KEY_ENCRYPTION_KEY_VERSION=v1
```

`docker-compose.yml` 已默认设置 `SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-prod}`，不要在生产环境覆盖为 `dev`。

---

## 2. 生产密钥生成

`API_KEY_ENCRYPTION_KEY` 必须是 Base64 编码的 32 字节随机密钥。

Linux：

```bash
openssl rand -base64 32
```

PowerShell：

```powershell
$bytes = New-Object byte[] 32
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
[Convert]::ToBase64String($bytes)
```

注意：

- 生产环境不能使用 `application-dev.yml` 或 `application.yml.example` 中的示例 key。
- 该密钥用于加密数据库中的 API Secret，创建 API Key 后必须长期保管。
- 如果密钥丢失或被替换，旧 API Key 的 Secret 将无法解密，外部调用会失效。
- 如果必须轮换密钥，应先制定密钥轮换方案，不要直接覆盖旧值。

---

## 3. `.env` 配置示例

生产服务器项目目录的 `.env` 至少应包含：

```env
SPRING_PROFILES_ACTIVE=prod

DB_PASSWORD=生产数据库密码
REDIS_PASSWORD=生产Redis密码
JWT_SECRET=生产JWT随机密钥
DEFAULT_USER_PASSWORD=生产默认用户强密码

API_KEY_ENCRYPTION_KEY=生产随机Base64密钥
API_KEY_ENCRYPTION_KEY_VERSION=v1

# 未正式开放外部系统调用前可保持 false
API_KEY_ENABLED=false
```

`API_KEY_ENCRYPTION_KEY` 和 `API_KEY_ENABLED` 的含义不同：

- `API_KEY_ENCRYPTION_KEY`：后台创建 API Key 时加密保存 Secret 的前提。
- `API_KEY_ENABLED=true`：开放 `/api/external/**` 外部签名鉴权入口。

可以先配置生产密钥并保持 `API_KEY_ENABLED=false`，完成后台密钥管理验证；正式对接外部系统时再改为 `true`。

---

## 4. 数据库迁移检查

外部 API 授权管理依赖 Flyway 迁移 `V17__external_api_auth_phase1.sql`。

部署后检查迁移状态：

```sql
SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

检查关键表：

```sql
SHOW TABLES LIKE 'sys_api_%';
SELECT method, path_pattern, permission_code
FROM sys_external_api_endpoint
ORDER BY sort_order;
```

必须存在：

- `sys_api_key`
- `sys_api_key_permission`
- `sys_api_call_log`
- `sys_api_key_operation_log`
- `sys_external_api_endpoint`

---

## 5. Docker 部署步骤

在生产服务器执行：

```bash
cd /opt/price-management
docker compose down
docker compose build --no-cache
docker compose up -d
docker compose logs -f backend
```

启动日志中应看到：

- active profile 为 `prod`
- Flyway 迁移成功
- Tomcat 8080 启动成功
- 没有 `API_KEY_ENCRYPTION_KEY` 缺失或示例 key 禁用错误

---

## 6. 上线验证

1. 登录后台管理员账号。
2. 进入 API 授权管理页面。
3. 创建测试 API Key，确认页面只展示一次 Secret。
4. 查看数据库 `sys_api_key`，确认 `app_secret_cipher` 有值且不是明文 Secret。
5. 如 `API_KEY_ENABLED=false`，外部接口应不可用或不开放给外部系统。
6. 如 `API_KEY_ENABLED=true`，使用 `X-App-Id`、`X-Timestamp`、`X-Nonce`、`X-Signature` 调用 `/api/external/v1/**`，并检查 `sys_api_call_log` 有调用记录。

---

## 7. 回滚与风险

- 回滚应用版本前，确认旧版本是否识别 `sys_api_*` 表；表存在通常不影响旧功能，但旧版本不会管理这些数据。
- 不要在回滚时删除 `sys_api_key` 或 `sys_api_call_log`，除非明确放弃外部 API 数据。
- 不要回滚或替换 `API_KEY_ENCRYPTION_KEY`，否则已创建 API Key 可能无法继续验签。
- 如怀疑密钥泄露，应先停用 `API_KEY_ENABLED`，再吊销受影响 API Key，并制定新主密钥轮换方案。

---

## 8. 部署前确认项

- [ ] 生产 `.env` 已设置 `SPRING_PROFILES_ACTIVE=prod`
- [ ] 生产 `.env` 已设置真实 `API_KEY_ENCRYPTION_KEY`
- [ ] 生产密钥已安全备份，且不在 Git 仓库中
- [ ] `docker-compose.yml` 已透传 `API_KEY_ENCRYPTION_KEY`
- [ ] Flyway 迁移 `V17` 执行成功
- [ ] 后台能创建 API Key
- [ ] 是否启用 `API_KEY_ENABLED=true` 已由业务确认
- [ ] 外部访问入口仅暴露 `/api/external/v1/**`
