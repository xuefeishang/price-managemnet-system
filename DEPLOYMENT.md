# 价格管理系统 - 生产环境部署说明

## 服务器信息
- IP: 10.7.5.175
- 已安装: MySQL 8.0.36, Docker

## 部署文件清单

```
price-management-system/
├── Dockerfile.backend      # 后端镜像构建
├── Dockerfile.frontend    # 前端镜像构建
├── nginx.conf             # Nginx配置
├── docker-compose.yml     # 服务编排
├── .env.production         # 生产环境配置
├── scripts/
│   ├── deploy.sh          # 一键部署脚本
│   └── init-db.sh         # 数据库初始化脚本
└── backend/
    └── src/main/resources/
        └── init.sql       # 数据库初始化脚本
```

## 部署步骤

### 方式一：自动化部署

1. 上传项目到服务器：
```bash
scp -r price-management-system root@10.7.5.175:/opt/
```

2. SSH 到服务器，执行部署：
```bash
ssh root@10.7.5.175
cd /opt/price-management-system

# 设置脚本执行权限
chmod +x scripts/deploy.sh scripts/init-db.sh

# 初始化数据库（需要输入MySQL密码）
./scripts/init-db.sh

# 部署应用
./scripts/deploy.sh
```

### 方式二：手动部署

1. 初始化数据库（登录MySQL执行）：
```sql
CREATE DATABASE IF NOT EXISTS price_management DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE price_management;
source backend/src/main/resources/init.sql;
```

2. 复制代码到服务器后执行：
```bash
cd /opt/price-management-system
docker compose up -d
```

## 验证部署

- 前端访问: http://10.7.5.175
- 后端API: http://10.7.5.175/api

## 常用命令

```bash
# 查看服务状态
docker compose ps

# 查看日志
docker compose logs -f

# 重启服务
docker compose restart

# 停止服务
docker compose down

# 重新构建
docker compose build --no-cache && docker compose up -d
```

## 默认用户

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | 管理员 |
| editor | admin123 | 编辑者 |
| viewer | admin123 | 查看者 |

## 目录结构

```
/opt/price-management-system/   # 应用目录
├── logs/                        # 日志目录
├── .env                         # 环境配置
├── docker-compose.yml           # 编排文件
└── ...                          # 代码文件
```