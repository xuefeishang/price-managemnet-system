#!/bin/bash
# start-backend.sh - 启动后端应用（自动加载环境变量）

# 获取脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# 检查 .env 文件是否存在
if [ -f ".env" ]; then
    echo "读取 .env 文件..."
    # 加载 .env 文件中的环境变量
    set -a
    source .env
    set +a
else
    echo "警告: .env 文件不存在，使用默认值启动"
fi

# 启动后端
echo "启动后端服务..."
cd "$SCRIPT_DIR/backend"
mvn spring-boot:run