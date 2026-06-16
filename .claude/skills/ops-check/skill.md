---
name: ops-check
preamble-tier: 1
version: 1.0.0
description: |
  对价格管理系统进行全面的运维体检。检查生产环境（10.7.5.175）、
  前后端运行状态、业务日志、数据库、Redis、Docker、Harbor 镜像。
  
  每项检查满分 1 分，总分 10 分。**只读 + 建议**：发现问题输出
  详细报告和优化建议，不自动修改任何配置。
  
  使用场景：
  - "运维体检"
  - "运行检查"
  - "健康检查"
  - "/ops-check"
  
  调度时机：deploy skill 完成后可自动调用本 skill。
triggers:
  - 运维检查
  - 运维体检
  - 健康检查
  - 运行检查
  - 体检
  - 扫描生产
allowed-tools:
  - Bash
  - Read
  - Write
---

# 价格管理系统运维体检 Skill

对生产环境（10.7.5.175）和本地仓库进行全面体检，输出 10 分制评分 + 优化建议。

---

## 体检总分

**满分 10 分**，由 10 个独立检查项组成，每项 0 或 1 分：

| # | 检查项 | 分值 | 关键判断 |
|---|--------|------|----------|
| 1 | 生产环境连通性 | 1 分 | SSH + 端口可达 |
| 2 | 容器运行状态 | 1 分 | 3 容器 Up 且 healthy |
| 3 | 关键端口监听 | 1 分 | 5 端口全部 LISTEN |
| 4 | 前端服务可访问 | 1 分 | 32080 + 32801 双入口 200 |
| 5 | 后端 API 业务 | 1 分 | 业务接口 200/符合预期 |
| 6 | 数据库一致性 | 1 分 | Flyway 版本与 dev 一致 |
| 7 | Redis 缓存健康 | 1 分 | ping PONG + 内存 < 80% |
| 8 | 磁盘空间 | 1 分 | 关键目录 < 80% |
| 9 | Harbor 镜像备份 | 1 分 | 当前版本镜像存在 |
| 10 | 日志异常 | 1 分 | 无 ERROR/Exception/5xx |

**评级**：
- **10/10**：健康，无任何问题
- **8-9/10**：基本健康，有轻微警告（建议优化）
- **6-7/10**：存在 1-2 项关键问题（建议尽快处理）
- **≤5/10**：存在多项关键问题（必须立即处理）

---

## 体检流程

按顺序执行 10 项检查，每项独立计分。最后汇总输出报告。

---

### 检查 1：生产环境连通性（1 分）

**目标**：确认能 SSH 到 10.7.5.175 且网络可达。

```bash
# 1.1 SSH 连接测试（带超时）
ssh -o ConnectTimeout=5 -o BatchMode=yes root@10.7.5.175 "echo ok" 2>&1

# 1.2 端口 22 + 80 + 3306 + 8080 网络可达性
nc -zv 10.7.5.175 22 2>&1 | head -2
nc -zv 10.7.5.175 3306 2>&1 | head -2
```

**通过条件**：
- SSH 输出 `ok`
- 所有端口 `succeeded` 或 `open`

**不通过常见原因**：
- VPN 断开
- 服务器宕机
- 防火墙拦截

---

### 检查 2：容器运行状态（1 分）

**目标**：3 个 price-management 容器全部 Up 且 healthy。

```bash
ssh root@10.7.5.175 "docker ps --format 'table {{.Names}}\t{{.Status}}' | grep -E 'price-management'"
```

**通过条件**：3 个容器都返回：
- `price-management-backend` Up + **(healthy)**
- `price-management-frontend` Up
- `price-management-redis` Up + **(healthy)**

**不通过常见原因**：
- 容器 OOM 被杀
- 健康检查失败
- 端口冲突启动失败

---

### 检查 3：关键端口监听（1 分）

**目标**：5 个关键端口全部在生产服务器上 LISTEN。

| 端口 | 用途 |
|------|------|
| 80 | 前端 HTTP（保留）|
| 32080 | 前端 HTTPS（统一入口）|
| 32801 | 前端 HTTP（内网测试）|
| 8080 | 后端 API |
| 6379 | Redis 缓存 |

```bash
ssh root@10.7.5.175 "ss -tln | grep -E ':80\s|:443\s|:32080\s|:32801\s|:8080\s|:6379\s' | wc -l"
# 预期：>= 5
```

**详细检查**（输出每个端口的监听状态）：
```bash
ssh root@10.7.5.175 "ss -tln | grep -E ':80\s|:443\s|:32080\s|:32801\s|:8080\s|:6379\s'"
```

**通过条件**：至少 5 个端口（不含 443）LISTEN 状态。

**不通过常见原因**：
- 前端 nginx.conf 缺 `listen 32801;`（参考 [deploy skill §6.1](../../skills/deploy/skill.md)）
- 后端崩溃退出
- Redis 异常退出

---

### 检查 4：前端服务可访问（1 分）

**目标**：前端两个入口（32080 HTTPS / 32801 HTTP）都能正常响应。

```bash
# 4.1 内网 HTTP 入口
ssh root@10.7.5.175 "curl -s -o /dev/null -w '32801: HTTP %{http_code}\n' http://localhost:32801/"

# 4.2 HTTPS 入口（接受自签证书）
ssh root@10.7.5.175 "curl -s -k -o /dev/null -w '32080: HTTP %{http_code}\n' https://localhost:32080/"
```

**通过条件**：两个端口都返回 **HTTP 200**。

**不通过常见原因**：
- nginx.conf 缺 `listen 32080 ssl;`
- 证书文件 `price.jlmining.com.pem` 缺失
- 前端 dist 未构建

---

### 检查 5：后端 API 业务（1 分）

**目标**：后端业务接口可正常调用（401 是预期行为，说明权限拦截生效）。

```bash
# 5.1 验证码接口（公开端点）
ssh root@10.7.5.175 "curl -s -o /dev/null -w 'captcha: HTTP %{http_code}\n' http://localhost:8080/api/auth/captcha"

# 5.2 业务接口（需登录，401 是预期）
ssh root@10.7.5.175 "curl -s -o /dev/null -w 'products: HTTP %{http_code}\n' http://localhost:8080/api/products?page=0\&size=1"

# 5.3 通过 32801 代理（验证 nginx 代理）
ssh root@10.7.5.175 "curl -s -o /dev/null -w 'proxy: HTTP %{http_code}\n' http://localhost:32801/api/auth/captcha"
```

**通过条件**：
- `captcha: HTTP 200`
- `products: HTTP 401`（未登录，符合 [jwt_public_path_auth_fix](../../memory/jwt_public_path_auth_fix.md)）
- `proxy: HTTP 200`

**不通过常见原因**：
- 后端启动失败
- 数据库连接异常（看后端日志 `Connection refused`）
- nginx 代理配置错误（502 Bad Gateway）

---

### 检查 6：数据库一致性（1 分）

**目标**：生产环境 Flyway 版本号与开发环境一致（v2.1.0 部署后应为 V46）。

```bash
# 6.1 生产 Flyway
DB_PWD=$(grep "^DB_PASSWORD=" .env | cut -d'=' -f2-)
DEV_VERSION=$(MYSQL_PWD="$DB_PWD" mysql -h localhost -P 3306 -u root price_management \
  -N -B -e "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1" 2>/dev/null)

PROD_VERSION=$(MYSQL_PWD="$DB_PWD" mysql -h 10.7.5.175 -P 3306 -u root price_management \
  -N -B -e "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1" 2>/dev/null)

# 6.2 核心表存在性
ssh root@10.7.5.175 "docker exec price-management-mysql mysql -u root -p\${DB_PASSWORD} price_management \
  -e 'SHOW TABLES' 2>&1" || \
ssh root@10.7.5.175 "mysql -h 127.0.0.1 -u root -p\${DB_PASSWORD} price_management \
  -e 'SHOW TABLES' 2>&1" | head -20
```

**通过条件**：
- `dev=$DEV_VERSION == prod=$PROD_VERSION`
- 核心表（sys_user, sys_role, product, price_history, sys_dict）都存在

**不通过常见原因**：
- 部署了代码但未自动跑 Flyway（手动迁移遗漏）
- 数据库配置错误（DB_HOST 写错）

---

### 检查 7：Redis 缓存健康（1 分）

**目标**：Redis 在线 + 内存使用未超警戒线。

```bash
# 7.1 ping 测试（Redis 运行在容器内，需 docker exec；密码从 .env 读取，禁止硬编码）
REDIS_PWD=$(grep "^REDIS_PASSWORD=" /opt/price-management-system/.env | cut -d'=' -f2-)
ssh root@10.7.5.175 "docker exec price-management-redis redis-cli -a '${REDIS_PWD}' --no-auth-warning ping"

# 7.2 内存使用率
ssh root@10.7.5.175 "docker exec price-management-redis redis-cli -a '${REDIS_PWD}' --no-auth-warning info memory | \
  grep -E 'used_memory_human|maxmemory_human|maxmemory_ratio'"
```

**密码处理原则**（必读）：
- ✅ **从 .env 读取**（运行时变量，不入库）
- ✅ **从容器 env 读取**（如果 compose 注入了 REDIS_PASSWORD）
- ❌ **禁止在 skill 文档中硬编码密码**（违反 [CLAUDE.md §敏感信息处理](../../../CLAUDE.md)）
- ❌ **禁止在报告中输出密码**（参见 §安全注意事项）

**通过条件**：
- `ping` 返回 `PONG`
- `used_memory / maxmemory` < 0.8

**不通过常见原因**：
- Redis 未启动（容器 exited）
- 密码错误（NOAUTH 错误）：检查 .env 中 REDIS_PASSWORD 是否与 docker-compose.yml 一致
- 内存爆满（maxmemory-policy 应为 allkeys-lru）

---

### 检查 8：磁盘空间（1 分）

**目标**：关键目录磁盘使用率 < 80%。

```bash
# 8.1 整体磁盘
ssh root@10.7.5.175 "df -h / /data /var 2>&1 | tail -5"

# 8.2 Docker 数据目录
ssh root@10.7.5.175 "du -sh /data/docker 2>&1"

# 8.3 日志目录
ssh root@10.7.5.175 "du -sh /opt/price-management-system/logs 2>&1"
```

**通过条件**：
- 所有分区 `Use%` < 80%
- 关键目录无异常膨胀

**不通过常见原因**：
- 日志未轮转（`/app/logs` 持续增长）
- Docker 旧镜像未清理（`docker system prune` 未执行）
- 数据库备份未清理

---

### 检查 9：Harbor 镜像备份（1 分）

**目标**：当前生产运行的镜像版本在 Harbor 中存在。

```bash
# 9.1 本地镜像版本
ssh root@10.7.5.175 "docker ps --format '{{.Image}}' | grep price-management"

# 9.2 Harbor 中是否存在
CURRENT_VERSION=$(ssh root@10.7.5.175 "cd /opt/price-management-system && git describe --tags --abbrev=0")
curl -s -u 'admin:Harbor@2026' \
  "http://10.7.5.175:8082/api/v2.0/projects/pricemanage/repositories/price-management-backend/artifacts" | \
  python3 -c "
import json, sys
data = json.load(sys.stdin)
versions = []
for a in data:
    for t in a.get('tags', []):
        versions.append(t['name'])
print('Harbor 中的后端镜像版本:', versions)
print('当前版本: $CURRENT_VERSION')
print('✅ 一致' if '$CURRENT_VERSION' in versions else '❌ 缺失备份')
"
```

**通过条件**：当前运行的镜像 tag 在 Harbor 中存在。

**不通过常见原因**：
- Harbor 容器未启动（8082 不可达）
- 部署后未执行 Harbor 备份
- 凭据错误（base64 失效）

---

### 检查 10：日志异常（1 分）

**目标**：最近 100 行关键日志中无 ERROR / Exception / 5xx 响应。

```bash
# 10.1 后端错误日志
ERROR_COUNT=$(ssh root@10.7.5.175 "docker logs price-management-backend --tail=200 2>&1 | \
  grep -ciE 'error|exception|caused by'" 2>/dev/null)

# 10.2 前端 nginx 5xx 错误
NGINX_5XX=$(ssh root@10.7.5.175 "docker logs price-management-frontend --tail=200 2>&1 | \
  grep -cE ' [5][0-9][0-9] '" 2>/dev/null)

# 10.3 Redis 异常
REDIS_ERRORS=$(ssh root@10.7.5.175 "docker logs price-management-redis --tail=100 2>&1 | \
  grep -ciE 'error|warning'" 2>/dev/null)
```

**通过条件**：
- 后端错误数 = 0
- 前端 5xx 数 = 0
- Redis 错误数 = 0（warning 可容忍）

**不通过常见原因**：
- 数据库连接断开（看 HikariPool 错误）
- 业务异常未捕获（看 5xx 数量）
- Redis 连接错误（看 Lettuce 客户端日志）

---

## 体检报告模板

```markdown
# 价格管理系统运维体检报告

**体检时间**：2026-06-16 08:30:00
**目标环境**：生产 10.7.5.175 + 本地仓库
**当前版本**：v2.1.0-20260616 (commit 946dda9)

---

## 总分：X / 10

| # | 检查项 | 状态 | 得分 |
|---|--------|------|------|
| 1 | 生产环境连通性 | ✅ 通过 / ❌ 失败 | 1/1 |
| 2 | 容器运行状态 | ✅ / ❌ | 1/1 |
| 3 | 关键端口监听 | ✅ / ❌ | 1/1 |
| 4 | 前端服务可访问 | ✅ / ❌ | 1/1 |
| 5 | 后端 API 业务 | ✅ / ❌ | 1/1 |
| 6 | 数据库一致性 | ✅ / ❌ | 1/1 |
| 7 | Redis 缓存健康 | ✅ / ❌ | 1/1 |
| 8 | 磁盘空间 | ✅ / ❌ | 1/1 |
| 9 | Harbor 镜像备份 | ✅ / ❌ | 1/1 |
| 10 | 日志异常 | ✅ / ❌ | 1/1 |
| **总分** | | | **X/10** |

**评级**：X/10 - {健康 / 基本健康 / 存在关键问题 / 必须立即处理}

---

## 详细结果

### 1. 生产环境连通性
- SSH 测试：{通过/失败}
- 端口 22/3306 测试：{通过/失败}

### 2. 容器运行状态
```
price-management-backend   Up X hours (healthy)
price-management-frontend  Up X hours
price-management-redis     Up X hours (healthy)
```

### 3. 关键端口监听
- 80: {LISTEN / CLOSE}
- 32080: {LISTEN / CLOSE}
- ...

...

## 优化建议

（如有失败项，按优先级排序）

### 🔴 高优先级（必须处理）
- [ ] {失败项 1}：{具体问题}，{修复方案}

### 🟡 中优先级（建议尽快处理）
- [ ] ...

### 🟢 低优先级（可选优化）
- [ ] ...
```

---

## 与其他 skill 协作

### 部署后自动体检

deploy skill 完成部署后，可调用本 skill 做一次完整体检：

```bash
# 在 deploy skill 的"步骤 4：验证部署结果"后追加：
echo "=== 自动触发运维体检 ==="
# 引用 ops-check skill
```

### 体检失败时联动

体检发现问题时，按问题类型联动对应 skill：
- **数据库问题** → [db-migration skill](../db-migration/SKILL.md)
- **Docker 容器问题** → [deploy skill §6 故障排查](../deploy/skill.md#步骤-6故障排查v210-大幅扩充)
- **Harbor 推送问题** → [deploy skill §5 Harbor 镜像备份](../deploy/skill.md#步骤-5harbor-镜像备份v210-强化)
- **API 接口问题** → [api-doc skill](../api-doc/SKILL.md)

---

## 安全注意事项

1. **本 skill 是只读**，**绝不**修改任何生产配置或数据
2. **不要在报告中输出敏感信息**（密码、Token、密钥、IP 白名单等）
3. **读取 .env 时**只获取 DB_HOST/DB_NAME 等非敏感字段；**绝不在 skill 文档中硬编码密码**
4. **SSH 操作**使用 `BatchMode=yes` 避免交互式密码输入
5. **执行时间长**的命令（如 `docker logs --tail=200`）必须用 `run_in_background: true` 避免阻塞
6. **Redis/DB 密码处理三原则**：
   - ✅ 从 `.env` 读取（运行时变量，不入库）
   - ✅ 从容器 `printenv` 读取（如果 compose 注入了）
   - ❌ **禁止在 skill 文档中硬编码**（违反 [CLAUDE.md §敏感信息处理](../../../CLAUDE.md)）
   - ❌ **禁止在 git 历史中保存**（grep 历史 commit 也不应能搜到）

---

## 体检耗时参考

- 完整 10 项检查：约 1-2 分钟
- 网络良好的情况下：30-60 秒

如某项检查超过 30 秒无响应，应视为网络问题（参见检查 1 失败的处理）。

---

*Skill 版本: 1.0.0*
*最后更新: 2026-06-16 — 初始版本，基于 v2.1.0 部署实战经验*
