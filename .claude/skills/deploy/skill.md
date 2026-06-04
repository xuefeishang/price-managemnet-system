---
name: deploy
preamble-tier: 1
version: 1.2.0
description: |
  将价格管理系统部署到生产环境（10.7.5.175）。包含代码提交、推送、
  生产环境同步、Docker镜像构建、容器启动的完整流程。
  
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

将本地代码部署到生产服务器 `10.7.5.175`，通过 Docker Compose 重新构建前后端镜像。

---

## 前置检查

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

---

## 步骤 1：本地代码提交

### 1.1 检查修改内容

```bash
git status --short
git diff --stat
```

### 1.2 提交所有修改

```bash
git add .

# 使用规范的提交信息格式
git commit -m "$(cat <<'EOF'
<type>: <简短描述>

主要变更：
- 变更点1
- 变更点2

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"
```

**提交类型说明：**
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

---

## 步骤 2：生产环境同步代码

### 2.1 SSH 连接并强制同步

```bash
ssh root@10.7.5.175 "cd /opt/price-management-system && git checkout -- . && git clean -fd && git pull origin master"
```

**命令说明：**
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

### 3.1 选择部署模式

通过 AskUserQuestion 询问部署范围：

| 选项 | 说明 |
|------|------|
| A) 全部重建 | 同时重建前后端镜像，耗时约 5-10 分钟 |
| B) 仅前端 | 只重建前端镜像，适合纯前端修改 |
| C) 仅后端 | 只重建后端镜像，适合纯后端修改 |
| D) 快速重启 | 不重建镜像，只重启容器 |

**推荐选项 A（全部重建）**，确保代码完全同步。

### 3.2 全部重建部署

```bash
ssh root@10.7.5.175 "cd /opt/price-management-system && docker compose down && docker compose build --no-cache && docker compose up -d"
```

### 3.3 仅前端重建

```bash
ssh root@10.7.5.175 "cd /opt/price-management-system && docker compose build --no-cache frontend && docker compose up -d frontend"
```

### 3.4 仅后端重建

```bash
ssh root@10.7.5.175 "cd /opt/price-management-system && docker compose build --no-cache backend && docker compose up -d backend"
```

### 3.5 快速重启（不重建）

```bash
ssh root@10.7.5.175 "cd /opt/price-management-system && docker compose restart"
```

---

## 步骤 4：验证部署结果

### 4.1 检查容器状态

```bash
ssh root@10.7.5.175 "docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' | grep price-management"
```

**预期结果：**
- `price-management-backend` 状态为 `(healthy)`
- `price-management-frontend` 状态为 `Up`
- 端口：前端 80（可选）、**32080（统一入口）**，后端 8080

### 4.2 等待健康检查通过

后端启动需要 60-90 秒，前端立即启动。

```bash
# 等待后端健康检查
ssh root@10.7.5.175 "sleep 90 && docker ps --format '{{.Names}}\t{{.Status}}' | grep price-management-backend"
```

### 4.3 API 功能验证

```bash
# 验证统一入口（32080）
ssh root@10.7.5.175 "curl -s http://localhost:32080/api/auth/captcha | head -c 200"

# 验证后端直接访问
ssh root@10.7.5.175 "curl -s http://localhost:8080/api/products?page=0&size=1"
```

**预期结果：**
- 验证码接口返回 JSON 格式数据
- 产品接口返回 JSON（可能 401 未登录）

---

## 步骤 5：故障排查

### 5.1 查看容器日志

```bash
# 后端日志（最近100行）
ssh root@10.7.5.175 "docker logs price-management-backend --tail=100"

# 前端日志
ssh root@10.7.5.175 "docker logs price-management-frontend --tail=50"

# 筛选错误日志
ssh root@10.7.5.175 "docker logs price-management-backend 2>&1 | grep -i 'error\|exception' | tail -30"
```

### 5.2 常见问题处理

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| GitHub 推送超时 | 网络不稳定 | 重试 `git push` |
| Git 拉取冲突 | 生产环境有本地修改 | 使用 `git reset --hard origin/master` |
| 容器名称冲突 | 旧容器未删除 | `docker rm -f price-management-frontend price-management-backend` |
| 前端 502 | nginx 代理配置错误 | 检查 nginx.conf 使用 `host.docker.internal` |
| 健康检查失败 | 后端启动异常 | 查看后端日志定位问题 |

### 5.3 强制重置生产环境

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

---

## 验证清单

部署完成后，按以下清单验证：

| 检查项 | 命令 | 预期 |
|--------|------|------|
| 容器状态 | `docker ps` | backend (healthy), frontend Up |
| 统一入口 | `curl localhost:32080/` | HTTP 200 |
| 验证码 API | `curl localhost:32080/api/auth/captcha` | JSON 响应 |
| 登录页面 | 浏览器访问 `http://10.7.5.175:32080` | 显示登录界面 |

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

容器状态:
- price-management-backend: Up (healthy)
- price-management-frontend: Up

访问地址: http://10.7.5.175:32080（内网） / http://101.254.159.153:32080（外网）

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
| .env | `/opt/price-management-system/` | 环境变量（敏感信息） |
| VERSIONS.md | `docs/` | 版本发布记录 |

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

---

*Skill 版本: 1.2.0*
*最后更新: 2026-06-04 — 增加 git-version skill 前置调用，规范化版本发布流程*