#!/bin/bash
# =====================================================
# 价格管理系统 - 用户角色管理功能迁移脚本
# 执行 V3 迁移：验证码、工号、角色权限体系
# =====================================================

set -e

# 配置
MYSQL_HOST="10.7.5.175"
MYSQL_PORT="3306"
MYSQL_USER="root"
DB_NAME="price_management"

echo "========================================"
echo "  用户角色管理功能迁移"
echo "========================================"

# 读取密码
read -s -p "MySQL root 密码: " MYSQL_PASSWORD
echo ""

# 执行迁移
echo "执行迁移脚本..."
mysql -h ${MYSQL_HOST} -P ${MYSQL_PORT} -u ${MYSQL_USER} -p${MYSQL_PASSWORD} ${DB_NAME} < backend/src/main/resources/db/migration/V3__user_role_management_enhancement.sql

echo "========================================"
echo "  迁移完成！"
echo "========================================"
echo ""
echo "新增功能："
echo "  - 验证码登录"
echo "  - 工号登录（6位数字）"
echo "  - 用户锁定/解锁"
echo "  - 角色权限体系"
