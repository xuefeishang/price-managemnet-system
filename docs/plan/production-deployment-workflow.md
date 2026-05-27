# 生产环境 Docker 部署流程

## 目的

将本地开发环境的代码同步到生产服务器 `10.7.5.175`，通过 Docker Compose 重新构建前后端镜像并部署运行。

适用场景：
- 功能开发完成后部署到生产环境
- 紧急修复需要快速上线
- 版本回滚或重新部署

---

## 前置条件

1. 本地代码已提交到 Git
2. 生产服务器可 SSH 访问：`ssh root@10.7.5.175`
3. 生产服务器已安装 Docker 和 Docker Compose
4. 项目目录位于生产服务器 `/opt/price-management`
5. 环境变量配置文件 `.env` 已存在

---

## 操作步骤

### 步骤 1：本地代码提交并推送

```bash
# 查看当前修改状态
git status --short

# 添加所有修改
git add .

# 提交（附带规范的提交信息）
git commit -m "$(cat <<'EOF'
feat: 功能描述

主要变更：
- 变更点1
- 变更点2

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>
EOF
)"

# 推送到远程仓库
git push origin master
```

**注意**：如果 GitHub 连接超时，可多次重试或检查网络代理配置。

---

### 步骤 2：生产环境拉取最新代码

```bash
# SSH 连接生产服务器，强制同步远程代码
ssh root@10.7.5.175 "cd /opt/price-management && git checkout -- . && git clean -fd && git pull origin master"

# 查看拉取结果
ssh root@10.7.5.175 "cd /opt/price-management && git log --oneline -3"
```

**说明**：
- `git checkout -- .` 丢弃本地未提交修改
- `git clean -fd` 删除未跟踪的文件和目录
- 这样可避免拉取时的冲突问题

---

### 步骤 3：停止并重建 Docker 容器

```bash
# 停止并删除现有容器
ssh root@10.7.5.175 "cd /opt/price-management && docker compose down"

# 无缓存重新构建镜像（确保使用最新代码）
ssh root@10.7.5.175 "cd /opt/price-management && docker compose build --no-cache"

# 启动容器
ssh root@10.7.5.175 "cd /opt/price-management && docker compose up -d"
```

**说明**：
- `--no-cache` 确保不使用旧的缓存层，完全重新构建
- `-d` 后台运行模式

---

### 步骤 4：等待并验证健康检查

```bash
# 等待后端启动（约60-90秒）
ssh root@10.7.5.175 "sleep 90 && docker ps --format 'table {{.Names}}\t{{.Status}}' | grep price-management"

# 验证服务可用性
ssh root@10.7.5.175 "curl -s http://localhost:80/api/auth/captcha | head -c 200"
```

**预期结果**：
- `price-management-backend` 状态为 `(healthy)`
- `price-management-frontend` 状态为 `Up`
- API 返回 JSON 格式的验证码数据

---

### 步骤 5：查看日志排查问题

如果服务异常，查看容器日志：

```bash
# 后端日志
ssh root@10.7.5.175 "docker logs price-management-backend --tail=100"

# 前端日志
ssh root@10.7.5.175 "docker logs price-management-frontend --tail=50"

# 筛选错误日志
ssh root@10.7.5.175 "docker logs price-management-backend 2>&1 | grep -i 'error\|exception' | tail -30"
```

---

## 一键部署命令

将以上步骤合并为一条命令：

```bash
# 本地提交
git add . && git commit -m "feat: 功能描述" && git push origin master

# 生产部署（等待GitHub同步后执行）
ssh root@10.7.5.175 "cd /opt/price-management && git checkout -- . && git clean -fd && git pull origin master && docker compose down && docker compose build --no-cache && docker compose up -d"
```

---

## 常见问题处理

### 问题 1：GitHub 推送超时

```bash
# 多次重试
git push origin master

# 或检查代理配置
git config --global http.proxy
```

### 问题 2：Git 拉取冲突

```bash
# 强制同步远程版本
ssh root@10.7.5.175 "cd /opt/price-management && git fetch origin && git reset --hard origin/master && git clean -fd"
```

### 问题 3：容器名称冲突

```bash
# 强制删除旧容器
ssh root@10.7.5.175 "docker rm -f price-management-frontend price-management-backend"
```

### 问题 4：健康检查失败

当前项目使用 TCP 端口检查：

```yaml
# docker-compose.yml 中的健康检查配置
healthcheck:
  test: ["CMD", "nc", "-z", "localhost", "8080"]
  interval: 30s
  timeout: 10s
  retries: 3
  start_period: 60s
```

如需 HTTP 检查，需添加 `spring-boot-starter-actuator` 依赖。

### 问题 5：前端 502 Bad Gateway

检查 nginx 代理配置：

```nginx
# nginx.conf - 使用 host.docker.internal 访问宿主机端口
location ^~ /api/ {
    proxy_pass http://host.docker.internal:8080/api/;
    # ...
}
```

**原因**：后端使用 `network_mode: host`，前端使用 bridge 网络，两者网络隔离。前端容器内的 `127.0.0.1` 指向容器自身而非宿主机。

---

## 文件同步替代方案

如果 GitHub 不可用，可直接 SCP 文件：

```bash
# 同步单个修改文件
scp "本地路径/文件.java" root@10.7.5.175:/opt/price-management/backend/src/.../文件.java

# 同步 Dockerfile 或配置文件
scp Dockerfile.backend root@10.7.5.175:/opt/price-management/Dockerfile.backend
scp docker-compose.yml root@10.7.5.175:/opt/price-management/docker-compose.yml
scp nginx.conf root@10.7.5.175:/opt/price-management/nginx.conf
```

---

## Harbor 镜像备份（可选）

部署完成后备份镜像到 Harbor：

```bash
# 打标签
DATE=$(date +%Y%m%d)
docker tag price-management-backend:latest jlmining.com/pricemanage/price-management-backend:v1.4.0-$DATE
docker tag price-management-frontend:latest jlmining.com/pricemanage/price-management-frontend:v1.4.0-$DATE

# 推送
docker push jlmining.com/pricemanage/price-management-backend:v1.4.0-$DATE
docker push jlmining.com/pricemanage/price-management-frontend:v1.4.0-$DATE
```

---

## 验证清单

| 检查项 | 命令 | 预期结果 |
|--------|------|----------|
| 容器状态 | `docker ps` | backend (healthy), frontend Up |
| 前端首页 | `curl localhost:80/` | HTTP 200 |
| API 接口 | `curl localhost:80/api/auth/captcha` | JSON 响应 |
| 后端直接访问 | `curl localhost:8080/api/products` | JSON 响应（可能 401） |

---

## 相关文件

- `docker-compose.yml` - Docker Compose 配置
- `Dockerfile.backend` - 后端镜像构建
- `Dockerfile.frontend` - 前端镜像构建
- `nginx.conf` - 前端 nginx 配置
- `.env` - 环境变量配置

---

*文档创建时间：2026-05-27*