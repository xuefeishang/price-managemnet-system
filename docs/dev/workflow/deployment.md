---
title: 部署指南
version: v2.0.0
last_updated: 2026-06-15
source: docs/dev/backup/开发指南.md + docs/dev/backup/技术栈简明说明.md
---

# 部署指南

本文件覆盖 Docker 部署、端口架构、Harbor 镜像备份、环境变量、生产部署检查清单与回滚方案。

> 详细本地开发与 GitHub 更新流程见 [docs/ops/IDEA部署指南.md](../../ops/IDEA部署指南.md) 与 [docs/ops/操作手册.md](../../ops/操作手册.md)。

## 部署模式

| 模式 | 文档 | 适用场景 |
|------|------|---------|
| 开发模式（IDEA + npm） | [docs/ops/IDEA部署指南.md](../../ops/IDEA部署指南.md) | 本地开发、断点调试 |
| Docker Compose | 本文件 + [docs/ops/操作手册.md](../../ops/操作手册.md) | 生产部署、测试环境 |

## 生产部署拓扑

```
服务器 (10.7.5.175)
├── Docker: price-management-frontend  (端口 80, 32080, 32801)
│   │
│   │  端口映射原理：
│   │  ┌─────────────────────────────────────────────┐
│   │  │  用户请求 :32080                             │
│   │  │       ↓                                      │
│   │  │  服务器网卡 (监听 32080)                      │
│   │  │       ↓                                      │
│   │  │  iptables 防火墙规则 (允许 32080)             │
│   │  │       ↓                                      │
│   │  │  docker-proxy 进程 (端口转发)                │
│   │  │       ↓                                      │
│   │  │  Nginx 容器 (监听容器内 32080)               │
│   │  │       ├── /api/* → 后端 8080                │
│   │  │       └── 其他 → 前端静态资源                 │
│   │  └─────────────────────────────────────────────┘
│   │
│   └── Nginx (统一入口 32080)
│       ├── 静态文件服务 (Vue 编译产物)
│       ├── SPA 路由支持 (try_files $uri /index.html)
│       └── /api/* → proxy_pass http://host.docker.internal:8080
│
├── Docker: price-management-backend   (端口 8080, host 网络模式)
│   └── Spring Boot (JRE 25, 非root用户 appuser)
│       ├── 连接 MySQL (10.7.5.175:3306, 外部服务)
│       └── 连接 Redis (10.7.5.175:6379, 外部服务)
│
├── MySQL 8.0 (非 Docker, 系统服务, 端口 3306)
└── Redis 7.x (非 Docker, 系统服务, 端口 6379, AOF 持久化)
```

## 端口架构

### 端口分配完整表

| 服务 | dev 端口 | 容器端口 | 网络模式 | 用途 |
|------|---------|---------|---------|------|
| frontend（H5 dev） | 5173（Vite 默认） | — | — | 本地开发 |
| frontend-uniapp（H5 dev） | 8080（uni h5 模式） | — | — | 本地 uni-app 开发 |
| backend | 8080 | 8080 | host | 后端 API |
| redis | 6379 | 6379 | host | 缓存 |
| frontend（Docker） | — | 80 | bridge | 可选 HTTP |
| frontend（Docker） | — | 32080 | bridge | **统一 HTTPS 入口**（PC 端 + 微信小程序） |
| frontend（Docker） | — | 32801 | bridge | **内网正式 HTTP**（免证书快速访问） |

### 客户端访问入口

| 客户端 | 内网访问 | 外网访问 |
|--------|---------|---------|
| **PC端 (H5)** | `http://10.7.5.175:32801` | `https://price.jlmining.com:32080` |
| **微信小程序** | `http://10.7.5.175:32801/api/*`（仅真机调试） | `https://price.jlmining.com:32080/api/*` |

公网正式微信小程序使用 `https://price.jlmining.com:32080`；公司内网真机调试可使用独立 HTTP 入口 `http://10.7.5.175:32801`。内网 HTTP/IP 不能配置为微信 request 合法域名，只能在真机调试开启"不校验合法域名"时使用。

### 端口架构原则

1. **统一端口**：PC 端和微信小程序共用 32080 端口，避免多入口维护成本
2. **内外网分离**：内网 32801（HTTP，免证书）+ 外网 32080（HTTPS，证书统一）
3. **小程序真机调试**：内网 32801 仅用于真机调试，生产必须使用 32080
4. **Host 网络后端**：后端容器使用 `network_mode: host`，避免 docker-proxy 性能损耗

## Docker 网络模式对比

| 网络模式 | 工作原理 | 性能 | 适用场景 |
|---------|---------|------|---------|
| **bridge + 端口映射** | 容器独立网络，通过 `docker-proxy` 转发端口 | 多一层转发，略有损耗 | 多实例部署、需要网络隔离 |
| **host 网络** | 容器直接使用宿主机网络，无需端口映射 | 性能最优，无转发损耗 | 单实例、性能敏感服务 |

当前项目配置：

```yaml
# docker-compose.yml
services:
  backend:
    network_mode: host    # ✅ Host 网络：直接监听宿主机 8080，无 docker-proxy

  frontend:
    # 默认 bridge 网络
    ports:
      - "80:80"           # 可选端口
      - "32080:32080"     # ✅ Bridge + 端口映射：通过 docker-proxy 转发
      - "32801:32801"     # ✅ 内网 HTTP 入口
```

## docker-compose.yml 部署

### 基础结构

```yaml
version: '3.8'

services:
  backend:
    image: price-management-backend:latest
    container_name: price-management-backend
    network_mode: host  # 直接监听宿主机 8080
    restart: unless-stopped
    environment:
      # 数据库
      DB_HOST: ${DB_HOST:-localhost}
      DB_PORT: ${DB_PORT:-3306}
      DB_NAME: ${DB_NAME:-price_management}
      DB_USERNAME: ${DB_USERNAME:-root}
      DB_PASSWORD: ${DB_PASSWORD}              # 必须通过环境变量注入
      # Redis
      REDIS_HOST: ${REDIS_HOST:-10.7.5.175}
      REDIS_PORT: ${REDIS_PORT:-6379}
      REDIS_PASSWORD: ${REDIS_PASSWORD}       # 必须通过环境变量注入
      # JWT
      JWT_SECRET: ${JWT_SECRET}               # 必须通过环境变量注入
      JWT_EXPIRATION: ${JWT_EXPIRATION:-86400000}
      # 用户
      DEFAULT_USER_PASSWORD: ${DEFAULT_USER_PASSWORD}
      # 外部 API
      API_KEY_ENABLED: ${API_KEY_ENABLED:-false}
      API_KEY_ENCRYPTION_KEY: ${API_KEY_ENCRYPTION_KEY}
      # 告警
      ALERT_ENABLED: ${ALERT_ENABLED:-false}
      ALERT_DINGTALK_ENABLED: ${ALERT_DINGTALK_ENABLED:-false}
      ALERT_DINGTALK_WEBHOOK: ${ALERT_DINGTALK_WEBHOOK}
      # Spring Profile
      SPRING_PROFILES_ACTIVE: prod
      # Tomcat 临时目录
      TOMCAT_BASEDIR: ${TOMCAT_BASEDIR:-./target/tomcat}
    volumes:
      - ./logs/backend:/app/logs
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  frontend:
    image: price-management-frontend:latest
    container_name: price-management-frontend
    ports:
      - "80:80"
      - "32080:32080"
      - "32801:32801"
    restart: unless-stopped
    depends_on:
      - backend
    healthcheck:
      test: ["CMD", "wget", "--spider", "-q", "http://localhost:32080/"]
      interval: 30s
      timeout: 10s
      retries: 3
```

### 常用命令

```bash
# 拉取最新代码
git pull origin master

# 检查/更新环境变量
vi .env

# 重新构建并启动
docker compose build --no-cache backend
docker compose up -d

# 查看服务状态
docker compose ps

# 查看日志
docker compose logs -f backend
docker compose logs --tail=100 frontend

# 重启单个服务
docker compose restart backend

# 停止所有服务
docker compose down

# 停止并删除卷
docker compose down -v
```

### Docker 多阶段构建

**Dockerfile.backend（开发版 — 含 Maven 编译）：**

```dockerfile
# 阶段1: Maven 编译
FROM maven:3.9-eclipse-temurin-25-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline          # 依赖预下载 (利用 Docker 缓存层)
COPY src ./src
RUN mvn clean package -DskipTests

# 阶段2: JRE 运行
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup  # 非 root 用户
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

**Dockerfile.backend.prod（生产版 — 跳过编译，直接使用预编译 JAR）：**

```dockerfile
FROM eclipse-temurin:25-jre-alpine
COPY target/price-management-backend-1.0.0.jar app.jar
# ... 其余同上
```

### Nginx 配置要点

```nginx
# 统一端口架构：PC端和小程序共用 32080 端口
server {
    listen 32080;
    server_name localhost;

    # 前端静态资源
    root /usr/share/nginx/html;
    index index.html;

    # 安全头
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;

    # Gzip 压缩
    gzip on;
    gzip_types text/css application/javascript application/json;

    # API 代理 — 转发到后端容器
    location ^~ /api/ {
        proxy_pass http://host.docker.internal:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;

        # CORS 配置（小程序需要）
        add_header Access-Control-Allow-Origin * always;
        add_header Access-Control-Allow-Methods "GET, POST, PUT, DELETE, OPTIONS" always;
        add_header Access-Control-Allow-Headers "Authorization, Content-Type" always;
    }

    # SPA 路由支持 — 所有路径回退到 index.html
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 静态资源长缓存 (文件名含 hash)
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2)$ {
        expires 30d;
        add_header Cache-Control "public, immutable";
    }
}
```

## 环境变量配置

### 必需变量（生产环境必须设置）

| 环境变量 | 说明 | 默认值 | 生产建议 |
|---------|------|--------|---------|
| `DB_HOST` | 数据库地址 | `localhost` | 实际 IP |
| `DB_PORT` | 数据库端口 | `3306` | — |
| `DB_NAME` | 数据库名称 | `price_management` | — |
| `DB_USERNAME` | 数据库用户名 | `root` | 专用账号 |
| `DB_PASSWORD` | 数据库密码 | **无默认值** | **强密码**（必须配置） |
| `DB_USE_SSL` | 是否使用 SSL | `true` | 生产环境保持 true |
| `REDIS_HOST` | Redis 地址 | `10.7.5.175` | 实际 IP |
| `REDIS_PORT` | Redis 端口 | `6379` | — |
| `REDIS_USERNAME` | Redis 用户名 | `default` | — |
| `REDIS_PASSWORD` | Redis 密码 | **无默认值** | **强密码**（必须配置） |
| `JWT_SECRET` | JWT 密钥 | **无默认值** | **随机 256 位密钥**（必须配置） |
| `JWT_EXPIRATION` | JWT 过期时间 (毫秒) | `86400000` (24小时) | — |
| `DEFAULT_USER_PASSWORD` | 默认用户密码 | **无默认值** | **强密码**（必须配置） |

### 外部 API 变量

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| `API_KEY_ENABLED` | 外部 API 开关 | `false` |
| `API_KEY_ENCRYPTION_KEY` | API Secret 加密主密钥（Base64 32字节） | 必须配置 |
| `API_KEY_ENCRYPTION_KEY_VERSION` | 主密钥版本 | 必须配置 |
| `API_KEY_TIMESTAMP_WINDOW_SECONDS` | 时间戳窗口 | `300` |
| `API_KEY_NONCE_TTL_SECONDS` | Nonce TTL | `600` |
| `API_KEY_CACHE_TTL_SECONDS` | 缓存 TTL（预留） | `60` |
| `API_KEY_LOG_RETENTION_DAYS` | 日志保留天数 | `90` |

> `API_KEY_ENCRYPTION_KEY` 必须是 Base64 编码的 32 字节密钥。开发环境 `application-dev.yml` 提供兜底 key，便于本地直接创建 API Key；生产 Docker 默认使用 `prod` profile，必须通过环境变量注入独立随机 key。生产环境缺少该密钥或使用开发示例 key 时，应在启用外部 API 或创建 API Key 前被拦截。

### 通知 Webhook 变量

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| `NOTIFICATION_WEBHOOK_ENABLED` | Webhook 开关 | `false` |
| `NOTIFICATION_WEBHOOK_URL` | Webhook 地址 | 空 |
| `NOTIFICATION_WEBHOOK_SECRET` | 签名密钥 | 空 |
| `NOTIFICATION_WEBHOOK_TIMEOUT_MS` | 超时时间（毫秒） | `5000` |

### 告警变量

| 环境变量 | 说明 | 默认值 | 生产建议 |
|---------|------|--------|---------|
| `ALERT_ENABLED` | 是否启用告警 | `false` | 生产环境建议 true |
| `ALERT_DINGTALK_ENABLED` | 启用钉钉告警 | `false` | 按需配置 |
| `ALERT_DINGTALK_WEBHOOK` | 钉钉 Webhook | 空 | 钉钉群机器人地址 |
| `ALERT_DINGTALK_SECRET` | 钉钉签名密钥 | 空 | 钉钉机器人安全设置 |
| `ALERT_WECHAT_ENABLED` | 启用企业微信告警 | `false` | 按需配置 |
| `ALERT_WECHAT_WEBHOOK` | 企业微信 Webhook | 空 | 企业微信群机器人地址 |
| `ALERT_THRESHOLD_MEMORY` | 内存告警阈值(%) | `90.0` | 按需调整 |
| `ALERT_THRESHOLD_CPU` | CPU 告警阈值(%) | `80.0` | 按需调整 |

### Tomcat 临时目录

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| `TOMCAT_BASEDIR` | Tomcat 临时目录 | `./target/tomcat`（dev）/ `${java.io.tmpdir}/tomcat`（prod） |

如果后端启动报 `Existing directory ... Temp ... is not owned by ...`，优先使用 `dev` 配置中的 `server.tomcat.basedir=./target/tomcat`。如需指定独立目录，可设置环境变量 `TOMCAT_BASEDIR`。

### 环境变量优先级

1. 系统环境变量（最高）
2. `.env` 文件（docker-compose 读取）
3. `application-prod.yml` 中的默认值（兜底）

**安全提醒：** 生产环境必须修改所有默认密码和密钥，通过环境变量注入，禁止在代码中硬编码。

## Harbor 镜像备份规范

### 镜像命名规则

| 服务 | 基础名称 |
|------|---------|
| 后端 | `jlmining.com/pricemanage/price-management-backend` |
| 前端 | `jlmining.com/pricemanage/price-management-frontend` |

### 版本标签格式

- **日期版本**：`v{主版本}.{次版本}.{补丁版本}-{YYYYMMDD}`
- **示例**：`v1.4.0-20260525`
- **latest 标签**：始终指向最新版本

### 备份命令

```bash
# 打标签
DATE=$(date +%Y%m%d)
docker tag price-management-backend:latest jlmining.com/pricemanage/price-management-backend:v1.4.0-$DATE
docker tag price-management-frontend:latest jlmining.com/pricemanage/price-management-frontend:v1.4.0-$DATE

# 推送到 Harbor
docker push jlmining.com/pricemanage/price-management-backend:v1.4.0-$DATE
docker push jlmining.com/pricemanage/price-management-frontend:v1.4.0-$DATE

# 更新 latest
docker tag price-management-backend:latest jlmining.com/pricemanage/price-management-backend:latest
docker tag price-management-frontend:latest jlmining.com/pricemanage/price-management-frontend:latest
docker push jlmining.com/pricemanage/price-management-backend:latest
docker push jlmining.com/pricemanage/price-management-frontend:latest
```

### 备份时机

每次生产部署后必须备份镜像到 Harbor，记录版本号便于回滚。

## 生产部署检查清单

### 部署前

- [ ] 确认 `price.jlmining.com` DNS 已指向公司 443 网关
- [ ] 确认域名已加入微信公众平台"request 合法域名"
- [ ] 确认所有环境变量已在 `.env` 中配置（DB_PASSWORD / REDIS_PASSWORD / JWT_SECRET 等）
- [ ] 确认 JWT_SECRET 使用随机 256 位密钥（非默认值）
- [ ] 确认 DEFAULT_USER_PASSWORD 已修改为强密码
- [ ] 确认 MySQL 数据库已创建（`price_management`）
- [ ] 确认 Redis 服务可连接
- [ ] 确认 Flyway 迁移脚本已合并
- [ ] 确认后端 JAR 已通过 `mvn clean package` 构建
- [ ] 确认前端 dist 已通过 `npm run build` 构建

### 部署中

- [ ] 拉取最新代码：`git pull origin master`
- [ ] 备份当前运行的镜像版本
- [ ] 构建后端镜像：`docker compose build --no-cache backend`
- [ ] 构建前端镜像：`docker compose build --no-cache frontend`
- [ ] 启动服务：`docker compose up -d`
- [ ] 等待健康检查通过：`docker compose ps`

### 部署后

- [ ] 验证后端健康：`curl http://localhost:8080/actuator/health`
- [ ] 验证前端可访问：`curl http://localhost:32080/`
- [ ] 验证 API 代理：`curl http://localhost:32080/api/dict/categories`
- [ ] 测试登录功能（admin/editor/viewer）
- [ ] 测试关键业务（产品列表、价格维护、价格查询）
- [ ] 验证 Redis 缓存：`redis-cli ping`
- [ ] 验证数据库连接：检查后端日志无 DB 异常
- [ ] 验证操作日志正常写入
- [ ] 验证告警通道（钉钉/企业微信）已启用
- [ ] 备份镜像到 Harbor 并打版本标签
- [ ] 更新 CHANGELOG / 版本号

## 回滚方案

### 应用回滚（最常用）

```bash
# 1. 查看历史镜像
docker images | grep price-management

# 2. 停止当前服务
docker compose down

# 3. 拉取历史版本镜像
docker pull jlmining.com/pricemanage/price-management-backend:v1.5.0-20260601
docker pull jlmining.com/pricemanage/price-management-frontend:v1.5.0-20260601

# 4. 修改 docker-compose.yml 指定镜像版本（或使用环境变量）
# IMAGE_TAG=v1.5.0-20260601 docker compose up -d

# 5. 验证服务
docker compose ps
curl http://localhost:8080/actuator/health
```

### 数据库回滚

```bash
# 1. 停止应用（避免新数据写入）
docker compose stop backend frontend

# 2. 备份当前数据库
mysqldump -u root -p price_management > backup_before_rollback_$(date +%Y%m%d_%H%M%S).sql

# 3. 恢复备份
mysql -u root -p price_management < backup_20260601_020000.sql

# 4. 启动应用
docker compose up -d
```

### 紧急回滚（保留数据）

如果仅需回滚应用代码而保留数据库变更：

1. 使用 Harbor 历史镜像版本（不执行 Flyway 迁移回滚）
2. 临时禁用 Flyway：`SPRING_FLYWAY_ENABLED=false`（不推荐）
3. 优先使用 `git revert` 生成反向提交，再走正常部署流程

### Flyway 校验失败处理

如果后端启动报 `Migration checksum mismatch`，说明某个已执行过的迁移文件被修改：

1. **开发环境**：执行 `mvn flyway:repair` 修复 checksum
2. **生产环境**：
   - 立即停止部署
   - 确认迁移文件来源（Git 历史 / 备份）
   - 恢复迁移文件或新增 `Vxx__fix_migration.sql` 补偿脚本
   - **生产环境禁止执行 repair**

## 相关文档

- [CLAUDE.md](../../../CLAUDE.md) §Harbor 镜像备份规范
- [docs/ops/IDEA部署指南.md](../../ops/IDEA部署指南.md) — 本地/生产部署详细步骤
- [docs/ops/操作手册.md](../../ops/操作手册.md) — 本地开发 / GitHub 更新 / 生产部署
- [development.md](development.md) — 开发流程与启动命令
- [git.md](git.md) — Git 提交规范
