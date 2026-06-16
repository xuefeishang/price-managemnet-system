---
name: ops-check
preamble-tier: 1
version: 1.1.0
description: |
  对价格管理系统进行全面的运维体检。检查生产环境（10.7.5.175）、
  前后端运行状态、业务日志、数据库（含 V47 安全事件表）、Redis、
  Docker、Harbor 镜像、iptables 限源、Nginx 攻击拦截、SSH 加固。
  
  每项检查满分 1 分，总分 10 分。**只读 + 建议**：发现问题输出
  详细报告和优化建议，不自动修改任何配置。
  
  使用场景：
  - "运维体检"
  - "运行检查"
  - "健康检查"
  - "/ops-check"
  
  调度时机：deploy skill 完成后可自动调用本 skill。
  适用版本：v2.1.0 起（v2.1.0 引入了 V47 + iptables + nginx 限流）。
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
v1.1.0 融入 v2.1.0 安全加固（V47、iptables、Nginx 限流、SSH 加固）。

---

## 体检总分

**满分 10 分**，由 10 个独立检查项组成，每项 0 或 1 分：

| # | 检查项 | 分值 | v2.1.0 关键变化 |
|---|--------|------|---------------|
| 1 | 生产环境连通性 | 1 分 | SSH 应仅白名单 IP 可达（密钥登录）|
| 2 | 容器运行状态 | 1 分 | 3 容器 Up + healthy |
| 3 | **关键端口监听** | 1 分 | **新增**：3306/6379/8080/8082 应不公网可达 |
| 4 | 前端服务可访问 | 1 分 | 32080 + 32801 双入口 200 |
| 5 | 后端 API 业务 | 1 分 | 业务接口 200/符合预期 |
| 6 | **数据库迁移一致性** | 1 分 | **强化**：Flyway 应到 V47 + security_event/ip_blacklist 表存在 |
| 7 | Redis 缓存健康 | 1 分 | ping PONG + 内存 < 80% |
| 8 | 磁盘空间 | 1 分 | 关键目录 < 80% |
| 9 | **iptables 限源 + Nginx 攻击拦截** | 1 分 | **新增**：PHASE1 规则 ≥7 + security_blocked map 存在 |
| 10 | **日志异常** | 1 分 | **强化**：识别 JNDI/Log4Shell 攻击尝试为业务拦截而非故障 |

**评级**：
- **10/10**：健康，无任何问题
- **8-9/10**：基本健康，有轻微警告（建议优化）
- **6-7/10**：存在 1-2 项关键问题（建议尽快处理）
- **≤5/10**：存在多项关键问题（必须立即处理）

**v2.1.0 安全相关新增关注点**（即使不扣分也建议关注）：
- 检查 6：security_event 表是否在累积事件（v2.1.0 早期应为空）
- 检查 9：fail2ban 未启用（按 v1.1 方案延后到 Phase 3）
- 检查 9：SSH `PermitRootLogin` 仍为 `yes`（L3 风险，需多 sessions 验证后改）|

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

### 检查 3：关键端口监听 + 暴露面收敛（1 分）

**目标**：业务端口 LISTEN + 内网服务**不公网暴露**（v2.1.0 强化）。

#### 3.1 业务端口 LISTEN

| 端口 | 用途 | 必须状态 |
|------|------|---------|
| 80 | 前端 HTTP | LISTEN |
| 443 | 前端 HTTPS | LISTEN |
| 32080 | 前端 HTTPS（统一入口）| LISTEN |
| 32801 | 前端 HTTP（内网测试）| LISTEN |
| 8080 | 后端 API | LISTEN |
| 6379 | Redis 缓存 | LISTEN |

#### 3.2 内网服务不公网可达（v2.1.0 强化）

下列端口应**不能从公网 10.7.5.175 自身地址访问**（除本机/内网）：

| 端口 | 服务 | 公网应不可达 | 原因 |
|------|------|------------|------|
| 3306 | MySQL | ✅ | iptables PHASE1-3306-DROP |
| 8082 | Harbor | ✅ | iptables PHASE1-8082-DROP |

```bash
# 业务端口 LISTEN 数量
ssh root@10.7.5.175 "ss -tln | grep -E ':80\s|:443\s|:32080\s|:32801\s|:8080\s|:6379\s' | wc -l"
# 预期：>= 6

# MySQL 3306 限源规则存在
ssh root@10.7.5.175 "iptables -L INPUT -n | grep -c 'SECURITY-HARDENING-PHASE1-3306'"
# 预期：3（ALLOW-LOCAL + ALLOW-LAN + DROP）

# Harbor 8082 限源规则存在
ssh root@10.7.5.175 "iptables -L INPUT -n | grep -c 'SECURITY-HARDENING-PHASE1-8082'"
# 预期：3

# iptables 持久化（重启不丢）
ssh root@10.7.5.175 "grep -c 'SECURITY-HARDENING-PHASE1' /etc/iptables/rules.v4"
# 预期：>= 7（v2.1.0 当前为 13）

# 公网直连 3306 应被拒（bash /dev/tcp 测试）
timeout 5 bash -c "echo > /dev/tcp/10.7.5.175/3306" 2>&1 && echo "3306 可达（异常）" || echo "3306 不可达（正确）"
timeout 5 bash -c "echo > /dev/tcp/10.7.5.175/8082" 2>&1 && echo "8082 可达（异常）" || echo "8082 不可达（正确）"
```

**通过条件**（v2.1.0）：
- 业务端口 LISTEN ≥ 6
- MySQL + Harbor PHASE1 限源规则各 3 条
- iptables 持久化文件 PHASE1 规则 ≥ 7
- 公网直连 3306/8082 不可达

**不通过常见原因**：
- 前端 nginx.conf 缺 `listen 32801;`（参考 [deploy skill §6.1](../../skills/deploy/skill.md)）
- 后端崩溃退出
- iptables 规则缺失（PHASE1 限源未配置）
- iptables 重启后丢失（未执行 `netfilter-persistent save`）
- **【DANGER】iptables 规则顺序错误**（v2.1.0 紧急修复）：
  - `iptables -I` 添加 DROP 会插到链最前，导致 ACCEPT 永远不命中
  - **正确顺序**：ACCEPT 在前，DROP 在后（用 `-I` 插 ACCEPT、`-A` 追加 DROP）
  - 验证命令：`iptables -L INPUT -n --line-numbers | grep PHASE1` 应看到 ACCEPT 在 DROP 前
  - 修复命令：
    ```bash
    # 1. 删除所有 PHASE1 规则
    for line in $(iptables -L INPUT -n --line-numbers | grep PHASE1 | awk '{print $1}' | tac); do
      iptables -D INPUT $line
    done
    # 2. 重新添加（ACCEPT 用 -A 追加到末尾或 -I 插在前面；DROP 必须 -A 追加）
    iptables -A INPUT -p tcp -s 127.0.0.0/8 --dport 3306 -j ACCEPT
    iptables -A INPUT -p tcp -s 10.7.5.0/24 --dport 3306 -j ACCEPT
    iptables -A INPUT -p tcp -s 172.16.0.0/12 --dport 3306 -j ACCEPT  # docker bridge
    iptables -A INPUT -p tcp --dport 3306 -j DROP
    netfilter-persistent save
    ```

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

### 检查 6：数据库迁移一致性（1 分，v2.1.0 强化）

**目标**：生产环境 Flyway 版本号与开发环境一致 + V47 安全表存在。

```bash
# 6.1 生产 Flyway 最新版本（v2.1.0 部署后应为 47）
DB_PWD=$(grep "^DB_PASSWORD=" .env | cut -d'=' -f2-)
DEV_VERSION=$(MYSQL_PWD="$DB_PWD" mysql -h localhost -P 3306 -u root price_management \
  -N -B -e "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1" 2>/dev/null)

PROD_VERSION=$(MYSQL_PWD="$DB_PWD" mysql -h 10.7.5.175 -P 3306 -u root price_management \
  -N -B -e "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1" 2>/dev/null)

# 6.2 V47 迁移详情（成功状态）
MYSQL_PWD="$DB_PWD" mysql -h 10.7.5.175 -P 3306 -u root price_management \
  -N -B -e "SELECT version, description, success FROM flyway_schema_history \
            WHERE version = '47' AND success = 1" 2>/dev/null

# 6.3 V47 新增表存在性
MYSQL_PWD="$DB_PWD" mysql -h 10.7.5.175 -P 3306 -u root price_management \
  -N -B -e "SELECT TABLE_NAME FROM information_schema.TABLES \
            WHERE TABLE_SCHEMA = 'price_management' \
              AND TABLE_NAME IN ('security_event', 'ip_blacklist') \
            ORDER BY TABLE_NAME" 2>/dev/null

# 6.4 operation_log 新增字段（risk_score, security_event_id）
MYSQL_PWD="$DB_PWD" mysql -h 10.7.5.175 -P 3306 -u root price_management \
  -N -B -e "SELECT COLUMN_NAME FROM information_schema.COLUMNS \
            WHERE TABLE_SCHEMA = 'price_management' \
              AND TABLE_NAME = 'operation_log' \
              AND COLUMN_NAME IN ('risk_score', 'security_event_id') \
            ORDER BY COLUMN_NAME" 2>/dev/null

# 6.5 核心业务表存在性
MYSQL_PWD="$DB_PWD" mysql -h 10.7.5.175 -P 3306 -u root price_management \
  -N -B -e "SELECT TABLE_NAME FROM information_schema.TABLES \
            WHERE TABLE_SCHEMA = 'price_management' \
              AND TABLE_NAME IN ('sys_user', 'sys_role', 'product', 'price_history', 'sys_dict') \
            ORDER BY TABLE_NAME" 2>/dev/null
```

**通过条件**（v2.1.0）：
- `dev=$DEV_VERSION == prod=$PROD_VERSION == 47`（**v2.1.0 部署后必须为 47**）
- V47 success=1
- security_event + ip_blacklist 表都存在
- operation_log 新增 risk_score + security_event_id 字段
- 核心业务表 5 个都存在

**不通过常见原因**：
- 部署了代码但 Flyway 未自动跑（检查 docker logs）
- 数据库配置错误（DB_HOST 写错）
- V47 迁移失败（检查 flyway_schema_history.error 字段）

**v2.1.0 早期监控建议**（即使通过也观察）：
- `SELECT COUNT(*) FROM security_event` —— 应为 0 或极少
- `SELECT COUNT(*) FROM ip_blacklist WHERE is_active=1` —— 应为 0 或极少

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

### 检查 9：iptables 限源 + Nginx 攻击拦截（1 分，v2.1.0 新增）

**目标**：v2.1.0 安全加固的两个核心配置（iptables + nginx）必须生效。

```bash
# 9.1 iptables PHASE1 规则数量（应 >= 13：6379/8080 × 4 + 3306 × 3 + 8082 × 3 + docker 链）
ssh root@10.7.5.175 "iptables -L INPUT -n | grep -c 'SECURITY-HARDENING-PHASE1'"
# 预期：>= 13

# 9.2 iptables 持久化（重启不丢）
ssh root@10.7.5.175 "grep -c 'SECURITY-HARDENING-PHASE1' /etc/iptables/rules.v4"
# 预期：>= 7（rules.v4 包含核心规则）

# 9.3 容器内 nginx.conf 含 security_blocked 攻击特征拦截
ssh root@10.7.5.175 "docker exec price-management-frontend grep -c security_blocked /etc/nginx/conf.d/default.conf"
# 预期：>= 5（1 个 map + 4 个 server if）

# 9.4 nginx.conf 含 limit_req_zone 限流配置
ssh root@10.7.5.175 "docker exec price-management-frontend grep -c 'limit_req_zone' /etc/nginx/conf.d/default.conf"
# 预期：>= 2（api_global + login）

# 9.5 Harbor 镜像备份（保留，但 v2.1.0 风险已降低）
CURRENT_VERSION=$(ssh root@10.7.5.175 "cd /opt/price-management-system && git describe --tags --abbrev=0 2>/dev/null")
HARBOR_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -u 'admin:Harbor@2026' \
  "http://10.7.5.175:8082/api/v2.0/projects/pricemanage/repositories/price-management-backend/artifacts" 2>/dev/null)
# 预期：HTTP 200
```

**通过条件**（v2.1.0）：
- iptables PHASE1 INPUT 链规则 ≥ 13
- rules.v4 持久化 PHASE1 规则 ≥ 7
- nginx.conf security_blocked 出现 ≥ 5 次
- nginx.conf limit_req_zone 出现 ≥ 2 次
- Harbor API 返回 200

**不通过常见原因**：
- v2.1.0 安全加固未完整应用（iptables 限源 + nginx 限流 + Harbor 备份）
- 修改 iptables 后未执行 `netfilter-persistent save`
- 修改 nginx.conf 后未重建前端镜像
- Harbor 容器退出（参考 [deploy skill §6.4](../../skills/deploy/skill.md)）

**v2.1.0 早期未实施项**（v1.1 方案延后）：
- fail2ban 安装与配置（Phase 3）
- SSH 加固（PermitRootLogin、PasswordAuthentication）

---

### 检查 10：日志异常 + 攻击识别（1 分，v2.1.0 强化）

**目标**：最近 200 行关键日志中无真实 ERROR，且**正确识别** JNDI/Log4Shell 等攻击尝试（**不**当作故障）。

```bash
# 10.1 后端真实错误（排除 JNDI 攻击尝试与 Tomcat 正常 stack trace）
REAL_ERRORS=$(ssh root@10.7.5.175 "docker logs price-management-backend --tail=200 2>&1 | \
  grep -iE 'error|exception' | \
  grep -vE 'jndi|rmi|http://[0-9]|ErrorReportValve|HHH000247' | \
  wc -l" 2>/dev/null)

# 10.2 JNDI/Log4Shell 攻击尝试（已知攻击源 IP，如 183.47.120.213）
JNDI_ATTACKS=$(ssh root@10.7.5.175 "docker logs price-management-backend --tail=200 2>&1 | \
  grep -ciE 'jndi:|rmi://|ldap://|\\\\\\$\\{hostName\\}|\\\\\\$\\{sys:'" 2>/dev/null)
# 这些是**外部攻击**，不是生产错误，应在安全事件中（v2.1.0 V47）

# 10.3 前端 nginx 5xx（用 status 字段精确匹配）
NGINX_5XX=$(ssh root@10.7.5.175 "docker logs price-management-frontend --tail=200 2>&1 | \
  grep -cE 'HTTP/1\\.[01]\" 5[0-9][0-9] '" 2>/dev/null)

# 10.4 Redis 错误（warning 可容忍）
REDIS_ERRORS=$(ssh root@10.7.5.175 "docker logs price-management-redis --tail=100 2>&1 | \
  grep -ciE '^.*error.*$'" 2>/dev/null)

# 10.5 （v2.1.0 新增）security_event 是否在累积
DB_PWD=$(grep "^DB_PASSWORD=" .env | cut -d'=' -f2-)
SECURITY_EVENT_COUNT=$(MYSQL_PWD="$DB_PWD" mysql -h 10.7.5.175 -P 3306 -u root price_management \
  -N -B -e "SELECT COUNT(*) FROM security_event WHERE created_time > DATE_SUB(NOW(), INTERVAL 24 HOUR)" 2>/dev/null)
# 预期：0（v2.1.0 早期 Phase 4.3 还没实施 Service 写入）
# 注意：0 不扣分，>0 也只作为预警
```

**通过条件**（v2.1.0）：
- 后端真实错误 = 0
- 前端 5xx = 0
- Redis 错误 = 0
- JNDI 攻击 = **任意值都通过**（属外部攻击，**已正确拒绝**）

**预警指标**（不扣分但报告）：
- JNDI 攻击 > 0：报告"检测到 N 次外部攻击尝试，**已被正确拒绝**"
- security_event 24h 计数 > 0：报告"N 个安全事件已入库（Phase 4.3 启动后）"

**不通过常见原因**：
- 数据库连接断开（HikariPool 错误）
- 业务异常未捕获（导致 5xx）
- Redis 连接错误（Lettuce 客户端日志）
- security_event 大量堆积（Phase 4.3 异常未消费）

**攻击识别规则**（v2.1.0 实战总结）：
- ❌ 不把 JNDI/Log4Shell 攻击当作 ERROR（系统已正确拒绝）
- ❌ 不把 Tomcat 拒绝非法字符的 stack trace 当作 ERROR
- ❌ 不把密码强度校验失败（"密码必须包含字母"）当作 ERROR
- ✅ 真实错误是数据库/Redis/业务未捕获异常

---

## 体检报告模板

```markdown
# 价格管理系统运维体检报告

**体检时间**：YYYY-MM-DD HH:MM
**目标环境**：生产 10.7.5.175 + 本地仓库
**当前版本**：v2.1.0-20260616 (commit <hash>)

---

## 总分：X / 10

| # | 检查项 | 状态 | 得分 |
|---|--------|------|------|
| 1 | 生产环境连通性 | ✅ 通过 / ❌ 失败 | 1/1 |
| 2 | 容器运行状态 | ✅ / ❌ | 1/1 |
| 3 | 关键端口监听 + 暴露面收敛 | ✅ / ❌ | 1/1 |
| 4 | 前端服务可访问 | ✅ / ❌ | 1/1 |
| 5 | 后端 API 业务 | ✅ / ❌ | 1/1 |
| 6 | 数据库迁移一致性（V47）| ✅ / ❌ | 1/1 |
| 7 | Redis 缓存健康 | ✅ / ❌ | 1/1 |
| 8 | 磁盘空间 | ✅ / ❌ | 1/1 |
| 9 | iptables 限源 + Nginx 攻击拦截 | ✅ / ❌ | 1/1 |
| 10 | 日志异常（识别攻击）| ✅ / ❌ | 1/1 |
| **总分** | | | **X/10** |

**评级**：X/10 - {健康 / 基本健康 / 存在关键问题 / 必须立即处理}

---

## 详细结果

### 1. 生产环境连通性
- SSH 测试：{通过/失败}
- SSH 配置：PermitRootLogin={yes/no} PasswordAuthentication={yes/no}

### 2. 容器运行状态
```
price-management-backend   Up X hours (healthy)
price-management-frontend  Up X hours
price-management-redis     Up X hours (healthy)
```

### 3. 关键端口监听 + 暴露面收敛
- 业务端口：80/443/32080/32801/8080/6379 {LISTEN 数}/6
- 内网服务不公网暴露：MySQL 3306 {可达/不可达} + Harbor 8082 {可达/不可达}
- iptables PHASE1 限源规则：{N} 条 + 持久化 {Y/N}

### 4. 前端服务可访问
- 32801: HTTP {code}（{size} bytes）
- 32080: HTTP {code}

### 5. 后端 API 业务
- captcha: HTTP {code}
- products（未登录）: HTTP {code}
- proxy: HTTP {code}

### 6. 数据库迁移一致性（V47）
- dev Flyway: V{version}
- prod Flyway: V{version}
- V47 success={0/1}
- security_event 表：{存在/缺失}
- ip_blacklist 表：{存在/缺失}
- operation_log 新字段：{齐全/缺失}

### 7. Redis 缓存健康
- PING: PONG/ERROR
- 内存：{used}/{max} = {pct}%

### 8. 磁盘空间
- /: {used}/{total} = {pct}%
- /data: {used}/{total} = {pct}%
- /opt/.../logs: {size}

### 9. iptables + Nginx 攻击拦截
- iptables PHASE1 INPUT 规则：{N} 条
- iptables 持久化：{rules.v4 存在 Y/N}
- nginx.conf security_blocked 出现：{N} 次
- nginx.conf limit_req_zone：{N} 个
- Harbor API：HTTP {code}

### 10. 日志异常 + 攻击识别
- 后端真实错误：{N} 条
- JNDI 攻击尝试：{N} 次（**已被正确拒绝**）
- 前端 5xx：{N} 条
- Redis 错误：{N} 条
- security_event 24h 累计：{N} 条

---

## v2.1.0 安全预警（即使得分 10/10 也建议关注）

| 项目 | 当前状态 | 风险等级 | 建议 |
|------|---------|---------|------|
| iptables 持久化 | {Y/N} | L0 | 每次改 iptables 后必 `netfilter-persistent save` |
| fail2ban | inactive | L1 | 后续按 v1.1 方案 Phase 3 启用 |
| SSH PermitRootLogin | yes | L3 | 多 sessions 验证后改 `prohibit-password` |
| SSH PasswordAuthentication | yes | L3 | 多 sessions 验证后改 `no` |
| security_event 24h 累计 | {N} | L0 | Phase 4.3 启动后会增长；按需查看 |
| nginx limit_req dry_run | on | L0 | 观察 48h 后改 off 启用强制 |

---

## 优化建议

（如有失败项，按优先级排序）

### 🔴 高优先级（必须处理）
- [ ] {失败项 1}：{具体问题}，{修复方案}

### 🟡 中优先级（建议尽快处理）
- [ ] ...

### 🟢 低优先级（可选优化）
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
- v2.1.0 后增加 security_event 表查询（< 100ms）

如某项检查超过 30 秒无响应，应视为网络问题（参见检查 1 失败的处理）。

---

## v1.0 → v1.1.0 升级说明

v2.1.0 安全加固后，ops-check 同步升级到 1.1.0：

| 变化 | 详情 |
|------|------|
| **检查 3 强化** | 原"5 端口 LISTEN" → "6 端口 LISTEN + 内网服务不公网暴露（iptables 验证）"|
| **检查 6 强化** | 原"Flyway V46 + 核心表" → "Flyway **V47** + security_event/ip_blacklist/operation_log 新字段"|
| **检查 9 替换** | 原"Harbor 镜像备份" → "**iptables PHASE1 规则 + nginx 攻击拦截 + Harbor 备份**" |
| **检查 10 强化** | 原"无 ERROR/Exception" → "真实错误 0 + **攻击识别为业务拦截** + security_event 累计" |
| **新增 报告 v2.1.0 预警表** | 即使 10/10 也报告 SSH/fail2ban/PHASE1 等未完成项 |
| **新增 V47 早期监控** | security_event 24h 累计 = 0 是 Phase 4.3 启动前的预期 |

---

*Skill 版本: 1.1.0*
*最后更新: 2026-06-16 — v2.1.0 安全加固后同步升级（iptables / Nginx 限流 / V47 校验）*
