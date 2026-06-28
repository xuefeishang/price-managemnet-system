#!/bin/bash
# =====================================================
# 价格管理系统 - 数据库初始化脚本
# 用于 MySQL 8.0
# =====================================================

set -e

# 配置
MYSQL_HOST="10.7.5.175"
MYSQL_PORT="3306"
MYSQL_USER="root"
DB_NAME="price_management"

echo "========================================"
echo "  价格管理系统 - 数据库初始化"
echo "========================================"

# 读取密码
read -s -p "MySQL root 密码: " MYSQL_PASSWORD
echo ""

# 创建数据库
echo "创建数据库..."
mysql -h ${MYSQL_HOST} -P ${MYSQL_PORT} -u ${MYSQL_USER} -p${MYSQL_PASSWORD} -e \
    "CREATE DATABASE IF NOT EXISTS ${DB_NAME} DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

echo "导入初始化数据..."
mysql -h ${MYSQL_HOST} -P ${MYSQL_PORT} -u ${MYSQL_USER} -p${MYSQL_PASSWORD} ${DB_NAME} < backend/src/main/resources/init.sql

echo "========================================"
echo "  数据库初始化完成！"
echo "========================================"
echo ""
echo "默认用户:"
echo "  admin  / \${DEFAULT_USER_PASSWORD} (管理员)"
echo "  editor / \${DEFAULT_USER_PASSWORD} (编辑者)"
echo "  viewer / \${DEFAULT_USER_PASSWORD} (查看者)"
