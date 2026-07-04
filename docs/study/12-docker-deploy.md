# 12. Docker 部署：让代码在任何机器上跑得一样

> "在我电脑能跑啊"——这是后台开发最痛的一句话。Docker 解决：**环境一致**。

---

## 一、为什么需要 Docker？

**传统部署的痛**：

```
开发："我本地能跑"
测试："在我这里报错"
运维："线上又 OOM 了"

原因：
- JDK 版本不一致
- MySQL 版本不一致
- 配置文件路径不一样
- 操作系统不一样
- 缺少某个系统库
```

**Docker 的承诺**：

```
开发打一个镜像 → 测试拉同一个镜像 → 线上跑同一个镜像
"在我电脑能跑啊" 变成 "在哪都能跑"
```

## 二、Docker 核心概念

| 概念 | 类比 | 说明 |
|------|------|------|
| **镜像（Image）** | 安装包 (.iso) | 只读的应用模板 |
| **容器（Container）** | 运行中的系统 | 镜像的实例 |
| **Dockerfile** | 安装步骤说明书 | 怎么构建镜像 |
| **仓库（Registry）** | App Store | 存镜像的地方（Docker Hub / Harbor） |
| **卷（Volume）** | U 盘 | 持久化数据 |
| **网络（Network）** | 局域网 | 容器之间通信 |

**镜像 vs 容器**：

```
镜像 = 类    （定义）
容器 = 对象  （运行实例）

一个镜像可以启动多个容器
```

## 三、安装 Docker Desktop

见 [00 环境搭建](00-prepare.md) 的"可选安装 Docker Desktop"。

验证安装：

```bash
docker --version
docker compose version
```

## 四、本项目 Dockerfile 解读

打开 `backend/Dockerfile`（本项目结构）：

```dockerfile
# 1. 多阶段构建的第一阶段：用 Maven 构建 jar
FROM eclipse-temurin:25-jdk-jammy AS builder

WORKDIR /app

# 先复制 pom.xml，利用 Docker 缓存
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 再复制源码
COPY src ./src
RUN mvn clean package -DskipTests -B

# 2. 第二阶段：只复制 jar，运行
FROM eclipse-temurin:25-jre-jammy

WORKDIR /app

# 复制 jar
COPY --from=builder /app/target/*.jar app.jar

# 时区设为上海
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 暴露端口
EXPOSE 8080

# 启动
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

### 4.1 多阶段构建

**第一阶段**（builder）：用 JDK 完整镜像 + Maven，把源码编成 jar
**第二阶段**（运行时）：只复制 jar，用更小的 JRE 镜像运行

**好处**：最终镜像小（只含 JRE，不含 JDK 和 Maven），更安全。

### 4.2 关键指令

| 指令 | 作用 |
|------|------|
| `FROM` | 基础镜像 |
| `WORKDIR` | 工作目录（cd） |
| `COPY` | 复制文件 |
| `RUN` | 执行命令 |
| `ENV` | 设置环境变量 |
| `EXPOSE` | 暴露端口（只是文档作用） |
| `ENTRYPOINT` | 容器启动命令 |
| `CMD` | 默认参数（可被覆盖） |

### 4.3 构建镜像

在 `backend/` 目录执行：

```bash
docker build -t price-management-backend:1.0.0 .
```

**参数**：
- `-t`：打标签（名字:版本）
- `.`：Dockerfile 在当前目录

**输出**：

```
[+] Building 120.5s (15/15) FINISHED
 => [builder 1/4] FROM eclipse-temurin:25-jdk-jammy
 => [builder 2/4] WORKDIR /app
 => [builder 3/4] COPY pom.xml .
 => ...
 => [stage-1 2/3] COPY --from=builder /app/target/*.jar app.jar
 => [stage-1 3/3] ENTRYPOINT ["java", "-jar", "/app/app.jar"]
 => naming to docker.io/library/price-management-backend:1.0.0
```

### 4.4 运行容器

```bash
docker run -d \
  --name price-backend \
  -p 8080:8080 \
  -e DB_HOST=10.7.5.175 \
  -e DB_PASSWORD=your-password \
  -e REDIS_HOST=10.7.5.175 \
  -e JWT_SECRET=your-secret \
  price-management-backend:1.0.0
```

**参数**：

| 参数 | 作用 |
|------|------|
| `-d` | 后台运行 |
| `--name` | 容器名 |
| `-p 8080:8080` | 端口映射（主机:容器） |
| `-e KEY=value` | 环境变量 |
| `-v /host/path:/container/path` | 挂载卷 |

## 五、Docker Compose

**问题**：本项目有后端 + 前端 + MySQL + Redis + Nginx，一个个 `docker run` 太麻烦。

**Docker Compose** = 一个 YAML 文件定义多个容器，一条命令启动全部。

### 5.1 本项目 docker-compose.yml 解读

打开项目根目录的 `docker-compose.yml`（如果有）：

```yaml
version: '3.8'

services:
  # 后端
  backend:
    build: ./backend
    image: price-management-backend:latest
    container_name: price-backend
    restart: unless-stopped
    ports:
      - "8080:8080"
    environment:
      DB_HOST: mysql
      DB_PORT: 3306
      DB_NAME: price_management
      DB_USERNAME: root
      DB_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      JWT_SECRET: ${JWT_SECRET}
      DEFAULT_USER_PASSWORD: ${DEFAULT_USER_PASSWORD}
    depends_on:
      - mysql
      - redis
    networks:
      - app-net

  # 前端
  frontend:
    build: ./frontend
    image: price-management-frontend:latest
    container_name: price-frontend
    restart: unless-stopped
    ports:
      - "80:80"
    depends_on:
      - backend
    networks:
      - app-net

  # MySQL
  mysql:
    image: mysql:8.0
    container_name: price-mysql
    restart: unless-stopped
    ports:
      - "3306:3306"
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: price_management
    volumes:
      - mysql-data:/var/lib/mysql
      - ./backend/src/main/resources/db/migration:/docker-entrypoint-initdb.d
    networks:
      - app-net

  # Redis
  redis:
    image: redis:7-alpine
    container_name: price-redis
    restart: unless-stopped
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    networks:
      - app-net

volumes:
  mysql-data:
  redis-data:

networks:
  app-net:
    driver: bridge
```

### 5.2 docker compose 命令

```bash
# 启动（构建 + 后台）
docker compose up -d

# 看日志
docker compose logs -f backend

# 停止
docker compose down

# 停止并删卷（数据没了！）
docker compose down -v

# 重启某个服务
docker compose restart backend

# 重新构建某个服务
docker compose build backend

# 拉取最新镜像
docker compose pull

# 看运行状态
docker compose ps
```

### 5.3 服务间通信

容器在同一个 `app-net` 网络里，可以通过**服务名**访问：

```
backend  →  mysql:3306    ← 用 "mysql" 而不是 "localhost"
backend  →  redis:6379    ← 用 "redis"
```

因为容器有自己的网络命名空间，localhost 是容器自己。

## 六、生产环境部署（本项目实操）

### 6.1 服务器准备

```bash
# SSH 登录
ssh root@10.7.5.175

# 安装 Docker
curl -fsSL https://get.docker.com | sh

# 安装 Docker Compose
apt install docker-compose-plugin

# 配置 Docker 镜像加速
mkdir -p /etc/docker
cat > /etc/docker/daemon.json <<EOF
{
  "registry-mirrors": [
    "https://docker.mirrors.ustc.edu.cn"
  ]
}
EOF
systemctl restart docker
```

### 6.2 部署流程

```bash
# 1. 拉代码
cd /opt
git clone https://github.com/your-org/price-management-system.git
cd price-management-system

# 2. 配置环境变量
cat > .env <<EOF
MYSQL_ROOT_PASSWORD=YourStrongPass!
JWT_SECRET=$(openssl rand -hex 32)
DEFAULT_USER_PASSWORD=Admin@2026
EOF

# 3. 构建并启动
docker compose up -d --build

# 4. 看日志确认启动
docker compose logs -f backend

# 看到 "Started PriceManagementApplication" 表示成功
```

### 6.3 健康检查

```bash
# 后端健康检查
curl http://localhost:8080/actuator/health
# {"status":"UP"}

# 看容器状态
docker compose ps
```

### 6.4 常用运维命令

```bash
# 看容器资源占用
docker stats

# 进容器内部调试
docker exec -it price-backend bash
docker exec -it price-mysql mysql -uroot -p

# 看日志
docker logs -f price-backend
docker logs --since 1h price-backend    # 最近 1 小时

# 清理磁盘
docker system prune -a                  # 删除无用镜像
docker volume prune                     # 删除无用卷
```

## 七、镜像备份（Harbor）

**Harbor** = 企业级 Docker 镜像仓库，类似私有 Docker Hub。

**为什么要备份？**

```
线上版本出问题了
  → 之前部署的是哪个版本？
  → 找到那个版本的镜像，回滚
  → 没有镜像 → 回滚不了，灾难！
```

### 7.1 打标签

```bash
DATE=$(date +%Y%m%d)
docker tag price-management-backend:latest \
  jlmining.com/pricemanage/price-management-backend:v2.1.0-$DATE
```

### 7.2 推送到 Harbor

```bash
# 登录（内网 Harbor 不需账号密码或用 admin/Harbor12345）
docker login jlmining.com

# 推送
docker push jlmining.com/pricemanage/price-management-backend:v2.1.0-$DATE

# 更新 latest 标签
docker tag price-management-backend:latest \
  jlmining.com/pricemanage/price-management-backend:latest
docker push jlmining.com/pricemanage/price-management-backend:latest
```

### 7.3 拉取部署

```bash
# 在服务器上
docker pull jlmining.com/pricemanage/price-management-backend:v2.1.0-20260628

# 修改 docker-compose.yml 的 image 字段
# image: jlmining.com/pricemanage/price-management-backend:v2.1.0-20260628

docker compose up -d
```

## 八、回滚

```bash
# 1. 停掉当前版本
docker compose down

# 2. 改 compose 文件的镜像版本
vim docker-compose.yml
# image: jlmining.com/pricemanage/price-management-backend:v2.0.5-20260601

# 3. 启动旧版本
docker compose up -d

# 4. 验证
curl http://localhost:8080/actuator/health
```

## 九、本项目实际部署架构

```
Internet
   │
   ▼
┌──────────────────────────────────┐
│  Nginx（反向代理 + 静态文件）    │  80/443
│  - /api/* → backend:8080         │
│  - /* → frontend 静态文件         │
└──────┬───────────────────────────┘
       │
       ▼
┌──────────────────────────────────┐
│  Spring Boot Backend             │  8080（容器内）
└──────┬────────────┬──────────────┘
       │            │
       ▼            ▼
┌──────────┐  ┌──────────┐
│  MySQL   │  │  Redis   │
└──────────┘  └──────────┘
   3306          6379
```

**关键点**：
- Nginx 对外只暴露 80/443
- MySQL / Redis 不对外暴露，只在容器网络内
- 后端通过环境变量连数据库

## 十、动手试试

### 实验 1：跑一个 hello-world

```bash
docker run -d -p 80:80 --name hello nginx
# 浏览器访问 http://localhost，看到 Nginx 欢迎页
docker stop hello
docker rm hello
```

### 实验 2：构建本项目后端镜像

```bash
cd backend
docker build -t price-backend:test .
docker images | grep price-backend
```

### 实验 3：用 compose 启动全套

```bash
cd 项目根目录
docker compose up -d --build
docker compose ps
docker compose logs backend | head -30
```

## 十一、常见错误

| 错误 | 原因 | 解决 |
|------|------|------|
| `port is already allocated` | 主机端口被占 | `netstat -ano` 找进程，杀掉或换端口 |
| `no space left on device` | 磁盘满 | `docker system prune` |
| `connection refused` mysql | 数据库没起来 | `docker compose logs mysql` |
| `image not found` | 镜像没拉 | `docker pull xxx` |
| `permission denied` /var/run/docker.sock | 用户没加 docker 组 | `usermod -aG docker $USER` |
| 容器起来马上退出 | 应用启动失败 | `docker logs 容器名` 看错误 |

## 十二、Dockerfile 优化技巧

### 12.1 利用缓存

```dockerfile
# ❌ 错：每次改代码都重装依赖
COPY . .
RUN mvn package

# ✅ 对：先复制 pom.xml，依赖装一次缓存起来
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn package
```

### 12.2 多阶段构建减小镜像

```dockerfile
# 镜像大小对比
FROM eclipse-temurin:25-jdk-jammy    # 800MB+
# vs
FROM eclipse-temurin:25-jre-jammy    # 200MB-
```

### 12.3 用 alpine 镜像（更小）

```dockerfile
FROM eclipse-temurin:25-jre-alpine    # 180MB
```

**注意**：alpine 用 musl libc，某些库可能不兼容。

### 12.4 .dockerignore

新建 `.dockerignore` 减少构建上下文：

```
target/
.git/
.idea/
*.iml
*.log
.vscode/
node_modules/
```

## 十三、Docker vs 虚拟机

| 维度 | 虚拟机 | Docker |
|------|--------|--------|
| 启动速度 | 分钟级 | 秒级 |
| 镜像大小 | GB | MB |
| 性能 | 有 hypervisor 开销 | 接近原生 |
| 隔离性 | 完全隔离 | 共享内核 |
| 资源占用 | 重 | 轻 |

**结论**：Java 后台开发，**用 Docker 足够**。

---

下一步：[99 学习路径](99-learning-path.md) →

回头补课：
- [00 环境搭建](00-prepare.md)
- [04 项目分层架构](04-layered-architecture.md)