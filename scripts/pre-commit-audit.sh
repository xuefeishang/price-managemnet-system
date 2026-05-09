#!/bin/bash
# pre-commit-audit.sh - Git提交前敏感信息检查脚本
# 使用方法: ./pre-commit-audit.sh
# 或在 .git/hooks/pre-commit 中调用

echo "========================================"
echo "敏感信息审计开始..."
echo "========================================"

# 敏感信息模式
SENSITIVE_PATTERNS=(
    "password\s*[:=]\s*['\"]?[a-zA-Z0-9_@#$%^&*]{6,}"
    "secret\s*[:=]\s*['\"]?[a-zA-Z0-9_@#$%^&*]{10,}"
    "api[_-]?key\s*[:=]"
    "access[_-]?token"
    "redis[_-]?password"
    "db[_-]?password"
)

# 需要检查的文件扩展名
CHECK_EXTENSIONS=(
    "yml"
    "yaml"
    "properties"
    "md"
    "txt"
    "json"
    "xml"
    "sql"
    "env"
)

FOUND_ISSUES=0

echo ""
echo "[1] 检查 .md 文件中的敏感信息..."
echo "----------------------------------------"

# 检查 .md 文件
while IFS= read -r file; do
    for pattern in "${SENSITIVE_PATTERNS[@]}"; do
        if grep -E -i "$pattern" "$file" 2>/dev/null; then
            echo "  ❌ 发现敏感信息: $file"
            echo "     匹配模式: $pattern"
            FOUND_ISSUES=1
        fi
    done
done < <(find . -type f \( -name "*.md" -o -name "*.yml" -o -name "*.yaml" \) -not -path "*/node_modules/*" -not -path "*/.git/*" 2>/dev/null)

if [ $FOUND_ISSUES -eq 0 ]; then
    echo "  ✅ .md 和 .yml 文件检查通过"
fi

echo ""
echo "[2] 检查包含实际密码的配置文件..."
echo "----------------------------------------"

# 检查 application.yml 是否包含明文密码（排除示例文件）
if [ -f "backend/src/main/resources/application.yml" ]; then
    if grep -E "password:\s+[a-zA-Z0-9_@#$%^&*]{6,}" "backend/src/main/resources/application.yml" 2>/dev/null | grep -v "example\|sample\|placeholder\|your_" > /dev/null; then
        echo "  ❌ application.yml 包含明文密码!"
        echo "     请使用环境变量或创建 application.yml.example"
        FOUND_ISSUES=1
    else
        echo "  ✅ application.yml 检查通过"
    fi
fi

echo ""
echo "[3] 检查 .env 文件是否被忽略..."
echo "----------------------------------------"

if [ -f ".env" ] && [ -f ".gitignore" ]; then
    if ! grep -q "\.env" .gitignore 2>/dev/null; then
        echo "  ⚠️  警告: .env 文件存在但未被 .gitignore 忽略"
        FOUND_ISSUES=1
    else
        echo "  ✅ .env 文件已在 .gitignore 中"
    fi
fi

echo ""
echo "========================================"
if [ $FOUND_ISSUES -eq 1 ]; then
    echo "审计失败: 发现敏感信息问题"
    echo "请修复后再提交"
    echo "========================================"
    exit 1
else
    echo "审计通过: 未发现敏感信息问题"
    echo "========================================"
    exit 0
fi