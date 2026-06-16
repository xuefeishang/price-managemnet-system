---
name: deploy
preamble-tier: 1
version: 1.4.0
description: |
  将价格管理系统部署到生产环境（10.7.5.175）。包含代码提交、推送、
  生产环境同步、Docker 镜像构建、容器启动、Harbor 备份的完整流程。
  融合 2026-06-16 v2.1.0 部署的实战经验（未跟踪文件监控、Harbor 内网
  推送路径、Docker daemon 损坏恢复、Harbor 容器手动恢复等）。

  **重要**：每次部署前会先调用 git-version skill 确保版本规范。

  使用场景：
  - "部署到生产环境"
  - "更新生产系统"
  - "推送并部署"
  - "重新构建前端"
  - "docker compose 部署"
triggers:
  - 部署到生产
  - 推送到生产
  - 更新生产环境
  - docker部署
  - 重新部署
allowed-tools:
  - Bash
  - Read
  - Write
  - Skill
  - AskUserQuestion
---

# 价格管理系统生产部署 Skill

将本地代码部署到生产服务器 `10.7.5.175`，通过 Docker Compose 重新构建前后端镜像，并备份到 Harbor。

---

## 前置检查（必须按顺序执行）

### 0.0 部署前必做清单（v2.1.0 新增）

| 步骤 | 命令 | 说明 |
|------|------|------|
| ✅ 本地 mvn test | `cd backend && mvn -B test` | **必须 0 失败 0 错误** |
| ✅ 数据库一致性 | 见 §0.4 | 确认 dev/prod Flyway 版本号一致 |
| ✅ 未跟踪文件检查 | `git status --short \| grep '^??'` | **必须为空**（否则前端 build 会失败）|
| ✅ docker 可用性 | `docker --version` | 本机无 Docker 需切到生产服务器构建 |

**任何一项不通过都禁止进入正式部署流程。**

### 0.1 版本规范检查（必须）

**在执行任何 Git 操作前，必须先调用 git-version skill：**

```
/git-version
```

git-version skill 会：
1. 检查当前分支是否为 master
2. 检查工作区是否干净
3. 确认是否需要创建新版本 tag
4. 更新 `docs/VERSIONS.md` 版本记录

**如果 git-version skill 返回需要创建新版本，必须先完成版本发布流程再继续部署。**

### 0.2 确认版本状态

```bash
# 检查当前分支和修改状态
git branch --show-current
git status --short

# 检查最新 tag
git tag --sort=-creatordate | head -3
```

如果输出显示有未提交修改，**必须先提交**再继续部署流程。

### 0.3 未跟踪文件检查（v2.1.0 闭坑 ⭐⭐⭐）

**真实事故**：v2.1.0 部署时，frontend/src/utils/apiError.ts 没有随 C2 提交，但 http.ts 和 UserManagement.vue 引用了它。生产环境 vue-tsc 编译失败，build 中断。

```bash
# 必须显式列出所有未跟踪文件
git status --short | grep '^??'
```

**如果有输出**：
1. 询问用户如何处理（提交 / 丢弃 / 暂存后单独 commit）
2. **绝不能**带着未跟踪文件进入部署流程
3. 提交新文件时使用独立 commit（fix: 补提 xxx），便于追溯

**预防机制**：
```bash
# 部署前强制 sanity check
test -z "$(git status --short | grep '^??')" && echo "✅ 无未跟踪文件" || echo "❌ 发现未跟踪文件"
```

### 0.4 数据库一致性检查（v2.1.0 新增 ⭐⭐⭐）

**背景**：部署前后必须确认开发与生产数据库结构一致，否则新版代码可能因字段缺失/类型不匹配而崩溃。

**快速检查脚本**（仅 Flyway 版本号 + 核心表 schema）：

```bash
# 开发环境 Flyway 版本
DB_PWD=$(grep "^DB_PASSWORD=" .env | cut -d'=' -f2-)
MYSQL_PWD="$DB_PWD" mysql -h localhost -P 3306 -u root price_management \
  -e "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3"

# 生产环境 Flyway 版本
MYSQL_PWD="$DB_PWD" mysql -h 10.7.5.175 -P 3306 -u root price_management \
  -e "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3"
```

**预期**：两端 `version` 列最后一行必须相同。

**核心表行数对比**（仅辅助判断，不强制）：

```sql
SELECT TABLE_NAME, TABLE_ROWS FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'price_management'
  AND TABLE_NAME IN ('sys_user', 'sys_role', 'sys_menu', 'product', 'price_history', 'sys_dict')
ORDER BY TABLE_NAME;
```

**判定标准**：
- Flyway 版本号完全一致 → 通过
- 核心表行数差异 < 20% → 正常（业务数据累积）
- 行数差异 > 20% → 进一步人工核查

### 0.5 实体变更检查（必须）

```bash
git diff HEAD -- backend/src/main/java/com/pricemanagement/entity/
```

**如果输出非空**：
- 检查 Entity 字段变更是否需要新 Flyway 迁移
- 必须在 `backend/src/main/resources/db/migration/` 添加 V{N+1}__xxx.sql
- 按 [db-migration skill](../db-migration/SKILL.md) 规范生成

**如果输出为空**：本次部署不涉及数据库结构变更，无需新迁移。

---

## 步骤 1：本地代码提交

### 1.1 检查修改内容

```bash
git status --short
git diff --stat
```

### 1.2 提交所有修改

**按职责拆分多个 commit**（推荐），便于 bisect 和回滚：

```bash
# 示例：拆分 4 个 commit
git add backend/...                                # C1: 后端重构
git commit -m "feat(security): 后端安全加固"

git add frontend/src/...                           # C2: 前端 H5
git commit -m "feat(frontend): 前端 H5 同步"

git add frontend-uniapp/...                        # C3: uniapp
git commit -m "feat(uniapp): 多端同步"

git add docs/                                       # C4: 文档
git commit -m "docs(v2.x.x): 文档同步更新"
```

**提交类型说明**（[git.md](../../docs/dev/workflow/git.md) 规范）：
- `feat` - 新功能
- `fix` - 修复bug
- `docs` - 文档更新
- `refactor` - 重构
- `style` - 样式调整

### 1.3 推送到 GitHub

```bash
git push origin master
```

> **注意**：如果 GitHub 连接超时，等待 30 秒后重试。最多重试 3 次。
> 首次 push 大概率超时（300s），立即重试通常会秒成功（GitHub 已识别）。

### 1.4 推送 tag

```bash
git push origin <TAG_NAME>
```

---

## 步骤 2：生产环境同步代码

### 2.1 SSH 连接并强制同步

```bash
ssh root@10.7.5.175 "cd /opt/price-management-system && git checkout -- . && git clean -fd && git pull origin master"
```

**命令说明**：
- `git checkout -- .` - 丢弃本地未提交修改
- `git clean -fd` - 删除未跟踪文件和目录
- `git pull origin master` - 拉取远程最新代码

### 2.2 验证同步结果

```bash
ssh root@10.7.5.175 "cd /opt/price-management-system && git log --oneline -3"
```

确认最新 commit 已同步到生产环境。

---

## 步骤 3：Docker 部署

### 3.0 部署模式选择（v2.1.0 调整 ⭐）

> **重要变更**：v2.1.0 之前，部署可在本机 Docker Desktop 完成。
> 但**本机通常无 Docker**（仅生产服务器有），因此标准流程是：
>
> **在生产服务器上构建 + 启动 + 备份 Harbor**

通过 AskUserQuestion 询问部署范围：

| 选项 | 说明 | 耗时 |
|------|------|------|
| A) 全部重建（推荐） | 同时重建前后端镜像 | 5-10 分钟 |
| B) 仅前端 | 只重建前端镜像 | 2-3 分钟 |
| C) 仅后端 | 只重建后端镜像 | 3-5 分钟（含 mvn 编译）|
| D) 快速重启 | 不重建镜像，只重启容器 | 30 秒 |

**推荐选项 A（全部重建）**，确保代码完全同步。

### 3.1 全部重建部署

```bash
ssh root@10.7.5.175 "cd /opt/price-management-system && docker compose down && docker compose build --no-cache && docker compose up -d"
```

### 3.2 仅前端重建

```bash
ssh root@10.7.5.175 "cd /opt/price-management-system && docker compose build --no-cache frontend && docker compose up -d frontend"
```

### 3.3 仅后端重建

```bash
ssh root@10.7.5.175 "cd /opt/price-management-system && docker compose build --no-cache backend && docker compose up -d backend"
```

### 3.4 快速重启（不重建）

```bash
ssh root@10.7.5.175 "cd /opt/price-management-system && docker compose restart"
```

---

## 步骤 4：验证部署结果

### 4.1 检查容器状态

```bash
ssh root@10.7.5.175 "docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' | grep price-management"
```

**预期结果**：
- `price-management-backend` 状态为 `(healthy)`
- `price-management-frontend` 状态为 `Up`
- 端口：前端 443（域名 HTTPS）、**32080（统一 HTTPS 入口）**、**32801（内网正式 HTTP 入口）**、80（保留），后端 8080

### 4.1.1 关键端口说明（来自当前生产 nginx.conf）

生产环境 nginx.conf 当前配置为四个 server 块，对应四种入口：

| 端口 | 用途 | 客户端 | 配置特点 |
|------|------|--------|----------|
| **443** | 正式 HTTPS 入口（域名） | PC / 小程序（生产） | `server_name price.jlmining.com`，需 SSL 证书 |
| **32080** | **统一 HTTPS 入口**（PC 端 + 微信小程序） | PC / 小程序 | `server_name price.jlmining.com`，SSL 终止，小程序 request 合法域名 |
| **32801** | **内网正式 HTTP 入口** | 内网 PC / 内网联调 | HTTP 入口，**小程序内网测试强制要求** |
| 80 | 保留 HTTP | 内网 PC | 当前未启用重定向，独立提供服务 |
| 8080 | 后端 API（host 网络） | nginx 代理目标 | 容器直接监听宿主机，无 docker-proxy |

**⚠️ 重要：小程序内网测试强制要求监听 32801 端口**

- 微信小程序开发工具在内网环境联调时，使用 `http://10.7.5.175:32801` 作为后端 API 入口
- **32801 必须监听**，否则小程序内网测试无法访问后端
- 部署后必须验证 32801 端口是否正常响应（详见 4.3.1）
- **任何一次前端镜像重建后，都必须重新验证 32801 端口的连通性**

### 4.1.2 nginx.conf 实际结构（来源：生产环境 10.7.5.175）

当前生产 `nginx.conf` 包含四个 server 块：

```nginx
# 1. 默认 server：拒绝 IP/未配置域名访问 HTTPS
server {
    listen 443 ssl default_server;
    server_name _;
    ssl_certificate /etc/nginx/certs/price.jlmining.com.pem;
    ssl_certificate_key /etc/nginx/certs/price.jlmining.com.key;
    return 444;
}

# 2. 正式 HTTPS 入口
server {
    listen 443 ssl;
    server_name price.jlmining.com;
    ssl_certificate /etc/nginx/certs/price.jlmining.com.pem;
    ssl_certificate_key /etc/nginx/certs/price.jlmining.com.key;
    # 安全头 + Gzip + SPA 路由 + API 代理
}

# 3. 主服务器 - 统一 HTTPS 入口（端口 32080）
server {
    listen 32080 ssl;
    server_name price.jlmining.com;
    # 与正式 HTTPS 相同的代理配置 + CORS 跨域头
    # 微信小程序 request 合法域名使用 https://price.jlmining.com:32080
}

# 4. 内网正式 HTTP 入口：80 / 32801
server {
    listen 80;
    listen 32801;
    server_name localhost;
    # 独立服务，不重定向
    # 小程序内网测试走 32801
}
```

**修复方法（如果端口丢失）**：
1. 编辑 `/opt/price-management-system/nginx.conf`，添加对应 `listen` 指令
2. 重新构建前端镜像：`docker compose build --no-cache frontend`
3. 重新创建前端容器：`docker compose up -d frontend`

### 4.2 等待健康检查通过

后端启动需要 60-90 秒，前端立即启动。

```bash
# 等待后端健康检查
ssh root@10.7.5.175 "sleep 90 && docker ps --format '{{.Names}}\t{{.Status}}' | grep price-management-backend"
```

### 4.3 API 功能验证

```bash
# 验证统一 HTTPS 入口（32080）
ssh root@10.7.5.175 "curl -s -k https://localhost:32080/api/auth/captcha | head -c 200"

# 验证小程序内网测试 HTTP 入口（32801）
ssh root@10.7.5.175 "curl -s http://localhost:32801/api/auth/captcha | head -c 200"

# 验证后端直接访问
ssh root@10.7.5.175 "curl -s http://localhost:8080/api/products?page=0&size=1"
```

**预期结果**：
- 验证码接口返回 JSON 格式数据
- 产品接口返回 JSON（可能 401 未登录）

**⚠️ 32801 端口必须返回 200**，否则小程序内网测试不可用

### 4.3.1 端口监听验证脚本

```bash
# 检查所有关键端口是否都在监听
ssh root@10.7.5.175 "netstat -tlnp 2>/dev/null | grep -E ':443|:32080|:32801|:80|:8080' || ss -tlnp | grep -E ':443|:32080|:32801|:80|:8080'"
```

**预期结果**：所有关键端口都在 LISTEN 状态

### 4.3.2 HTTPS 443 验证（域名证书）

```bash
# 验证域名证书
ssh root@10.7.5.175 "curl -s -k https://price.jlmining.com:443/api/auth/captcha | head -c 200"
```

**预期**：返回 JSON 验证码数据

### 4.3.3 ⚠️ /actuator/health 返回 500 不是错误（v2.1.0 发现）

按 [jwt_public_path_auth_fix](../../memory/jwt_public_path_auth_fix.md) 规范，所有请求都需 JWT 认证。`/actuator/health` 不在公开白名单中，无 token 访问会触发 500。

**这不是真实错误**：
- 真正的健康检查走 TCP 端口（`nc -z localhost 8080`），在 compose.yml 已配置
- 容器状态显示 `(healthy)` 即表示健康检查通过
- 如需 HTTP 验证 health，需先把端点加入 `SystemConstants.PUBLIC_PATHS`

---

## 步骤 5：Harbor 镜像备份（v2.1.0 强化 ⭐⭐⭐）

> **CLAUDE.md §Harbor 镜像备份规范**：每次生产部署后必须备份镜像到 Harbor。
> v2.1.0 暴露了多个 Harbor 推送陷阱，本节详述。

### 5.1 关键 Harbor 信息（必记）

| 配置项 | 值 | 备注 |
|--------|-----|------|
| Harbor 项目 | `pricemanage` | 已存在 |
| **内网地址** | `10.7.5.175:8082` | **生产服务器内部访问用** |
| 外网地址 | `https://jlmining.com` | **仅公网用户用，生产服务器无法访问**（DNS 不解析）|
| 用户名 | `admin` | 凭据在 `~/.docker/config.json` |
| 密码 | `Harbor@2026` | 同上（base64 编码后存储）|
| 镜像命名 | `pricemanage/price-management-backend` / `pricemanage/price-management-frontend` | 路径前缀固定 |

**重要**：生产服务器**不能**通过 `https://jlmining.com` 访问 Harbor（DNS 不解析），必须用 `10.7.5.175:8082`（HTTP）！

### 5.2 daemon.json 配置（v2.1.0 必加 ⭐⭐⭐）

Harbor 走 HTTP 而非 HTTPS，必须在 `/etc/docker/daemon.json` 中加入 insecure-registries：

```json
{
  "data-root": "/data/docker",
  "registry-mirrors": [
    "https://docker.m.daocloud.io",
    "https://docker.1panel.live"
  ],
  "insecure-registries": [
    "10.7.5.175",
    "10.7.5.175:8082"
  ]
}
```

**陷阱**：
- `insecure-registries: ["10.7.5.175"]` **不包含 8082 端口**，推送时会因协议不匹配失败
- 必须**显式**列出 `10.7.5.175:8082`
- **修改后必须重启 Docker daemon**（见 §6.3）

### 5.3 推送命令

```bash
# 1. 打日期版本标签
DATE=$(date +%Y%m%d)
docker tag price-management-system-backend:latest \
  10.7.5.175:8082/pricemanage/price-management-backend:v${MAJOR}.${MINOR}.${PATCH}-${DATE}
docker tag price-management-system-frontend:latest \
  10.7.5.175:8082/pricemanage/price-management-frontend:v${MAJOR}.${MINOR}.${PATCH}-${DATE}

# 2. 打 latest 标签
docker tag price-management-system-backend:latest \
  10.7.5.175:8082/pricemanage/price-management-backend:latest
docker tag price-management-system-frontend:latest \
  10.7.5.175:8082/pricemanage/price-management-frontend:latest

# 3. 推送
docker push 10.7.5.175:8082/pricemanage/price-management-backend:v${VERSION}
docker push 10.7.5.175:8082/pricemanage/price-management-backend:latest
docker push 10.7.5.175:8082/pricemanage/price-management-frontend:v${VERSION}
docker push 10.7.5.175:8082/pricemanage/price-management-frontend:latest
```

### 5.4 验证推送成功

```bash
# 列出项目下所有仓库
curl -s -u 'admin:Harbor@2026' \
  'http://10.7.5.175:8082/api/v2.0/projects/pricemanage/repositories' | \
  python3 -c "
import json, sys
for r in json.load(sys.stdin):
    print(f\"{r['name']}: artifact_count={r['artifact_count']}\")
"

# 列出后端镜像所有 tag
curl -s -u 'admin:Harbor@2026' \
  'http://10.7.5.175:8082/api/v2.0/projects/pricemanage/repositories/price-management-backend/artifacts' | \
  python3 -c "
import json, sys
for a in json.load(sys.stdin):
    tags = [t['name'] for t in a.get('tags', [])]
    print(f\"  digest={a['digest'][:16]}... tags={tags}\")
"
```

**预期**：刚推送的 tag 出现在列表中，artifact_count 增加。

### 5.5 Harbor 容器路径（v2.1.0 闭坑 ⭐⭐⭐）

Harbor 不是在 price-management-system 目录下，而是独立项目：

```bash
# 找 Harbor compose 文件
docker inspect nginx --format='{{index .Config.Labels "com.docker.compose.project.config_files"}}'
# 输出: /data/harbor/docker-compose.yml
```

如 Harbor 容器退出需手动恢复：
```bash
cd /data/harbor && docker compose up -d
```

---

## 步骤 6：故障排查（v2.1.0 大幅扩充 ⭐⭐⭐）

### 6.1 容器级问题

| 问题 | 症状 | 解决方案 |
|------|------|---------|
| 后端启动失败 | 容器不断重启 | `docker logs price-management-backend --tail=100` |
| 前端 502 | nginx 代理配置错误 | 检查 nginx.conf 使用 `host.docker.internal` |
| 健康检查失败 | 后端启动异常 | 查看后端日志定位问题 |
| **小程序内网测试失败（32801 不可达）** | nginx.conf 缺少 `listen 32801;` | 编辑 nginx.conf 添加 32801 监听，重新构建前端 |
| **32801 端口返回 404/502** | 前端镜像未重建 | `docker compose build --no-cache frontend && docker compose up -d frontend` |
| **SSL 证书加载失败** | nginx.conf 引用不存在的证书文件 | 从 git 历史回退 nginx.conf 到稳定版本 |

### 6.2 Git 推送问题

| 问题 | 症状 | 解决方案 |
|------|------|---------|
| GitHub 推送超时 | `Connection timed out after 300034 ms` | **立即重试 1 次**通常成功（首次 push 会建立连接）|
| Git 拉取冲突 | 生产环境有本地修改 | `git reset --hard origin/master && git clean -fd` |
| 容器名称冲突 | 旧容器未删除 | `docker rm -f price-management-frontend price-management-backend` |

### 6.3 Docker daemon 损坏（v2.1.0 严重事故 ⭐⭐⭐）

**真实事故**：用 SSH heredoc 写 `/etc/docker/daemon.json` 时，shell 把引号剥离，导致 JSON 损坏。dockerd 启动失败 → 所有容器停止。

**症状**：
```
unable to configure the Docker daemon with file /etc/docker/daemon.json:
  invalid character 'd' looking for beginning of object key string
```

**恢复流程**（按顺序执行）：

```bash
# 1. 立即恢复备份文件
cp /etc/docker/daemon.json.bak /etc/docker/daemon.json

# 2. 用 python 重写（避免 heredoc 引号问题）
python3 -c "
import json
with open('/etc/docker/daemon.json', 'r') as f:
    data = json.load(f)
data['insecure-registries'] = ['10.7.5.175', '10.7.5.175:8082']
with open('/etc/docker/daemon.json', 'w') as f:
    json.dump(data, f, indent=2)
"

# 3. 验证 JSON 格式
cat /etc/docker/daemon.json  # 必须以 { "key": "value" 格式输出

# 4. 重置 systemd 失败计数（关键！）
systemctl reset-failed docker

# 5. 启动 Docker
systemctl start docker

# 6. 验证
systemctl is-active docker  # 应输出 active
docker info | head -3
```

**为什么用 `reset-failed`**：systemd 在连续失败 5 次后会自动放弃重启，进入 `failed` 状态，此时 `restart` 也不会启动。必须先 `reset-failed`。

**预防**：
- 改 daemon.json **永远不要用 heredoc**（引号易丢失）
- 用 `python3 -c` 写 JSON 或手动 `vi` 编辑
- **修改前必备份**：`cp daemon.json daemon.json.bak`

### 6.4 Harbor 容器不自动恢复（v2.1.0 闭坑 ⭐⭐⭐）

**真实事故**：Docker daemon 重启后，价格系统的 3 个容器（`restart: unless-stopped`）自动恢复，**但 Harbor 容器不会**（其 compose 用的是 `restart: on-failure` 或类似策略，且依赖独立的 compose 项目）。

**症状**：
```
docker ps -a | grep harbor
harbor-jobservice    Exited (128) 2 minutes ago
harbor-core          Exited (128) 2 minutes ago
...
```

端口 8082 也不监听：`ss -tln | grep 8082` 无输出。

**恢复流程**：

```bash
# 1. 找 Harbor compose 目录
docker inspect nginx --format='{{index .Config.Labels "com.docker.compose.project.config_files"}}'
# 输出: /data/harbor/docker-compose.yml

# 2. 启动 Harbor
cd /data/harbor && docker compose up -d

# 3. 等待 30s 并验证
sleep 30
docker ps --format 'table {{.Names}}\t{{.Status}}' | grep -iE 'harbor|registry|nginx'
ss -tln | grep 8082  # 应有 LISTEN
```

**预防**：
- 给 Harbor compose 加上 `restart: unless-stopped`（需要单独操作 Harbor 的 yml）

### 6.5 Harbor 推送鉴权失败（v2.1.0 闭坑 ⭐）

**真实事故**：把 `10.7.5.175:8082` 加入 `insecure-registries` 后，docker daemon 重启，但 `~/.docker/config.json` 不会自动添加新地址的 auth 凭据。

**症状**：
```
failed to do request: Head "https://10.7.5.175:8082/v2/.../blobs/...":
  authorization failed: no basic auth credentials
```

**恢复流程**：

```bash
# 重新登录 Harbor
echo 'Harbor@2026' | docker login 10.7.5.175:8082 -u admin --password-stdin
# 输出: Login Succeeded

# 验证 config.json 已添加
cat ~/.docker/config.json
# 应包含 "10.7.5.175:8082" 节点
```

### 6.6 强制重置生产环境

当常规同步失败时使用：

```bash
ssh root@10.7.5.175 "cd /opt/price-management-system && git fetch origin && git reset --hard origin/master && git clean -fd && docker compose down && docker compose up -d"
```

---

## 一键部署命令

完整流程（包含版本规范检查）：

### 步骤 1：版本规范检查

```
/git-version
```

### 步骤 2：本地提交推送

```bash
git add . && git commit -m "<提交信息>" && git push origin master

# 如果创建了新 tag，推送 tag
git push origin v<版本号>
```

### 步骤 3：生产部署

```bash
ssh root@10.7.5.175 "cd /opt/price-management-system && git checkout -- . && git clean -fd && git pull origin master && docker compose down && docker compose build --no-cache && docker compose up -d"
```

### 步骤 4：Harbor 备份

```bash
ssh root@10.7.5.175 "cd /opt/price-management-system && \
  docker tag price-management-system-backend:latest 10.7.5.175:8082/pricemanage/price-management-backend:v<版本号> && \
  docker tag price-management-system-frontend:latest 10.7.5.175:8082/pricemanage/price-management-frontend:v<版本号> && \
  docker push 10.7.5.175:8082/pricemanage/price-management-backend:v<版本号> && \
  docker push 10.7.5.175:8082/pricemanage/price-management-frontend:v<版本号>"
```

---

## 验证清单

部署完成后，按以下清单验证：

| 检查项 | 命令 | 预期 |
|--------|------|------|
| 容器状态 | `docker ps` | backend (healthy), frontend Up |
| **443 端口**（正式 HTTPS） | `curl -k https://price.jlmining.com:443/api/auth/captcha` | JSON 响应 |
| **32080 端口**（统一 HTTPS 入口） | `curl -k https://localhost:32080/api/auth/captcha` | JSON 响应 |
| **32801 端口**（小程序内网测试） | `curl http://localhost:32801/api/auth/captcha` | JSON 响应 |
| 登录页面 | 浏览器访问 `https://price.jlmining.com:32080` | 显示登录界面 |
| 小程序内网测试 | 微信开发者工具访问 `http://10.7.5.175:32801` | 正常加载小程序 |
| **数据库 Flyway 版本** | `mysql ... flyway_schema_history` | 与 dev 一致 |
| **Harbor 镜像备份** | 见 §5.4 | 新 tag 已 push，artifact_count+1 |

---

## 部署完成报告

部署成功后，输出以下格式的完成报告：

```
✅ 部署完成

版本: v<版本号>
Commit: <commit hash> - <提交信息>
Tag: v<版本号> 已推送

前端镜像: price-management-frontend:latest (重建)
后端镜像: price-management-backend:latest (重建)
Harbor 备份: pricemanage/price-management-{backend,frontend}:v<版本号> + latest 已推送

容器状态:
- price-management-backend: Up (healthy)
- price-management-frontend: Up
- price-management-redis: Up (healthy)

数据库一致性:
- 开发 Flyway: V<n>
- 生产 Flyway: V<n>
- ✅ 一致

访问地址:
- 正式域名 HTTPS: https://price.jlmining.com:443
- PC 端 / 小程序主入口: https://price.jlmining.com:32080
- 小程序内网测试专用: http://10.7.5.175:32801（**必须监听**）

版本记录: docs/VERSIONS.md 已更新
```

---

## 项目配置文件

部署涉及的关键配置文件：

| 文件 | 路径 | 说明 |
|------|------|------|
| docker-compose.yml | `/opt/price-management-system/` | Docker Compose 配置 |
| Dockerfile.backend | `/opt/price-management-system/` | 后端镜像构建 |
| Dockerfile.frontend | `/opt/price-management-system/` | 前端镜像构建 |
| nginx.conf | `/opt/price-management-system/` | 前端 nginx 配置 |
| **daemon.json** | `/etc/docker/daemon.json` | Docker daemon 配置（**v2.1.0 必加 10.7.5.175:8082 到 insecure-registries**）|
| .env | `/opt/price-management-system/` | 环境变量（敏感信息） |
| VERSIONS.md | `docs/` | 版本发布记录 |
| **Harbor compose** | `/data/harbor/docker-compose.yml` | **v2.1.0 新增：Harbor 独立部署位置** |
| **~/.docker/config.json** | 生产服务器 root home | Docker 登录 Harbor 凭据 |

---

## 与 git-version skill 协作

**调用顺序**：

```
1. /git-version  → 版本规范检查、创建 tag
2. /deploy       → 生产环境部署
```

**git-version skill 职责**：
- 版本号计算和命名
- 创建 Git tag
- 更新 VERSIONS.md

**deploy skill 职责**：
- 代码推送
- 生产环境同步
- Docker 部署
- Harbor 备份

---

## v2.1.0 部署实战总结（changelog）

| 类别 | 关键变更 |
|------|---------|
| 新增检查 | §0.0 部署前 4 项必做清单（mvn test / 数据库一致性 / 未跟踪文件 / docker 可用性）|
| 新增检查 | §0.3 未跟踪文件检查（闭坑：apiError.ts 缺失导致 build 失败）|
| 新增检查 | §0.4 数据库一致性检查（Flyway 版本号 + 核心表行数）|
| 新增检查 | §0.5 实体变更检查（是否需要新 Flyway 迁移）|
| 新增流程 | §5 Harbor 镜像备份（v2.1.0 之前缺失，违反 CLAUDE.md 规范）|
| 强化排错 | §6.3 Docker daemon 损坏恢复（heredoc 引号剥离）|
| 强化排错 | §6.4 Harbor 容器不自动恢复（独立 compose 项目）|
| 强化排错 | §6.5 Harbor 鉴权失效（config.json 不自动同步）|
| 补充知识 | §5.1 Harbor 关键信息（**内网 10.7.5.175:8082，非 jlmining.com**）|
| 补充知识 | §5.2 daemon.json insecure-registries 必须含端口 |
| 补充知识 | §4.3.3 /actuator/health 500 不是真实错误 |

---

*Skill 版本: 1.4.0*
*最后更新: 2026-06-16 — 融合 v2.1.0 部署实战经验（Harbor 路径、daemon.json 损坏、未跟踪文件闭坑、Harbor 容器恢复等）*
