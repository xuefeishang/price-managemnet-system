#!/bin/bash
# =====================================================
# 价格管理系统 - Docker 部署脚本
# 用于 10.7.5.175 服务器
# =====================================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 配置
APP_DIR="/opt/price-management"
BACKEND_PORT="8080"
FRONTEND_PORT="80"

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  价格管理系统 - Docker 部署脚本${NC}"
echo -e "${GREEN}========================================${NC}"

# 检查 Docker 和 Docker Compose
echo -e "\n${YELLOW}[1/6] 检查环境...${NC}"
if ! command -v docker &> /dev/null; then
    echo -e "${RED}错误: Docker 未安装${NC}"
    exit 1
fi

if ! command -v docker compose &> /dev/null; then
    echo -e "${RED}错误: Docker Compose 未安装${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Docker 和 Docker Compose 已安装${NC}"

# 创建应用目录
echo -e "\n${YELLOW}[2/6] 创建应用目录...${NC}"
sudo mkdir -p ${APP_DIR}
cd ${APP_DIR}
echo -e "${GREEN}✓ 应用目录: ${APP_DIR}${NC}"

# 创建 .env 文件（如果不存在）
if [ ! -f "${APP_DIR}/.env" ]; then
    echo -e "\n${YELLOW}请提供以下配置信息：${NC}"
    read -p "MySQL 密码: " DB_PASSWORD
    read -p "Redis 密码: " REDIS_PASSWORD
    if [ -z "${REDIS_PASSWORD}" ]; then
        echo -e "${RED}错误: Redis 密码不能为空${NC}"
        exit 1
    fi
    read -p "JWT 密钥 [默认: 自动生成]: " JWT_SECRET
    JWT_SECRET=${JWT_SECRET:-$(openssl rand -hex 32)}
    read -p "默认用户初始密码: " DEFAULT_USER_PASSWORD
    if [ -z "${DEFAULT_USER_PASSWORD}" ]; then
        echo -e "${RED}错误: 默认用户初始密码不能为空${NC}"
        exit 1
    fi

    cat > ${APP_DIR}/.env << EOF
# 数据库配置
DB_PASSWORD=${DB_PASSWORD}

# Redis配置
REDIS_PASSWORD=${REDIS_PASSWORD}

# 安全配置
JWT_SECRET=${JWT_SECRET}
DEFAULT_USER_PASSWORD=${DEFAULT_USER_PASSWORD}
RESET_PASSWORD_ON_STARTUP=false
API_KEY_ENABLED=false
IP_BLACKLIST_ENABLED=true
IP_BLACKLIST_OBSERVATION_MODE=true
IP_BLACKLIST_CACHE_TTL_SECONDS=30
IP_BLACKLIST_NEGATIVE_CACHE_TTL_SECONDS=0
IP_BLACKLIST_TRUSTED_PROXIES=127.0.0.1,::1,0:0:0:0:0:0:0:1
IP_BLACKLIST_BYPASS_SOURCES=127.0.0.1,::1,0:0:0:0:0:0:0:1
EOF
    echo -e "${GREEN}✓ 配置文件已创建${NC}"
else
    echo -e "${GREEN}✓ 配置文件已存在${NC}"
fi

# 创建日志目录
echo -e "\n${YELLOW}[3/6] 创建日志目录...${NC}"
sudo mkdir -p ${APP_DIR}/logs
sudo chmod 777 ${APP_DIR}/logs
echo -e "${GREEN}✓ 日志目录已创建${NC}"

# 拉取代码（如果有git）
echo -e "\n${YELLOW}[4/6] 同步代码...${NC}"
if [ -d ".git" ]; then
    git pull
    echo -e "${GREEN}✓ 代码已更新${NC}"
else
    echo -e "${YELLOW}请手动复制代码到 ${APP_DIR}${NC}"
fi

# 构建 Docker 镜像
echo -e "\n${YELLOW}[5/6] 构建 Docker 镜像...${NC}"
echo -e "${YELLOW}这可能需要几分钟，请耐心等待...${NC}"
docker compose build --no-cache

echo -e "${GREEN}✓ 镜像构建完成${NC}"

# 启动服务
echo -e "\n${YELLOW}[6/6] 启动服务...${NC}"
docker compose up -d

# 等待服务启动
echo -e "${YELLOW}等待服务启动...${NC}"
sleep 10

# 检查服务状态
echo -e "\n${GREEN}========================================${NC}"
echo -e "${GREEN}  部署完成！${NC}"
echo -e "${GREEN}========================================${NC}"
echo ""
echo -e "前端地址: ${GREEN}http://10.7.5.175${NC}"
echo -e "后端API:  ${GREEN}http://10.7.5.175/api${NC}"
echo ""
echo -e "查看日志: ${YELLOW}docker compose -f ${APP_DIR}/docker-compose.yml logs -f${NC}"
echo -e "停止服务: ${YELLOW}docker compose -f ${APP_DIR}/docker-compose.yml down${NC}"
echo ""
