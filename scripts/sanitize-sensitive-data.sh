#!/bin/bash
# sanitize-sensitive-data.sh - 脱敏脚本
# 将文档中的敏感信息替换为占位符

echo "开始脱敏处理..."

# 替换模式（实际密码 -> 占位符）
declare -A SENSITIVE_DATA=(
    ["123abc"]="【敏感-已移除】"
    ["jianlong.123"]="【敏感-已移除】"
    ["admin123"]="【敏感-已移除】"
    ["editor123"]="【敏感-已移除】"
    ["viewer123"]="【敏感-已移除】"
)

# 文档文件
DOC_FILES=(
    "IDEA部署指南.md"
    "开发指南.md"
)

for file in "${DOC_FILES[@]}"; do
    if [ -f "$file" ]; then
        echo "处理: $file"
        for key in "${!SENSITIVE_DATA[@]}"; do
            sed -i "s/$key/${SENSITIVE_DATA[$key]}/g" "$file"
        done
        echo "  ✓ 完成"
    fi
done

echo ""
echo "脱敏完成！"