---
title: Git 规范
version: v2.0.0
last_updated: 2026-06-15
source: docs/dev/backup/开发指南.md
---

# Git 规范

本文件覆盖 Git 分支管理、提交规范和常用命令。完整流程请结合 [CLAUDE.md §功能变更流程](../../../CLAUDE.md) 与 [development.md](development.md) 阅读。

## Git 分支管理

| 分支模式 | 用途 | 部署环境 |
|---------|------|---------|
| `main` / `master` | 主分支，用于生产环境 | 生产 |
| `develop` | 开发分支，用于集成测试 | 测试 |
| `feature/*` | 功能分支，用于开发新功能 | 本地 |
| `hotfix/*` | 热修复分支，用于紧急修复 | 本地 → 生产 |

### 分支命名约定

```bash
# 功能分支
git checkout -b feature/dict-management
git checkout -b feature/price-maintenance

# 热修复分支
git checkout -b hotfix/login-captcha-bug
git checkout -b hotfix/security-jwt-fix

# 重构分支（可选）
git checkout -b refactor/service-layer-cleanup
```

### 分支生命周期

```
main (生产)
  │
  ├── develop (集成测试)
  │     │
  │     ├── feature/xxx (开发者 A)
  │     ├── feature/yyy (开发者 B)
  │     └── feature/zzz (开发者 C)
  │
  └── hotfix/urgent (生产紧急修复)
        │
        └── 直接合并回 main + develop
```

### 工作流原则

1. **主分支受保护**：`main` 与 `develop` 不允许直接 push，必须通过 Pull Request 合并
2. **功能分支从 develop 拉出**：开发新功能时 `git checkout develop && git pull && git checkout -b feature/xxx`
3. **小步提交**：每次提交保持单一职责，便于 code review 与回滚
4. **及时同步**：长时间开发需定期 `git rebase develop` 同步主分支变更
5. **合并前必须测试**：本地 `mvn clean install` + 前端 `npm run build` 通过后再合并

## 提交规范（Commit Message 格式）

采用 **Conventional Commits** 规范：

```
<type>(<scope>): <subject>

<body>

<footer>
```

### 格式说明

| 字段 | 必填 | 说明 |
|------|-----|------|
| `type` | ✅ | 提交类型，详见下表 |
| `scope` | ❌ | 影响范围（可选），如 `product` / `user` / `dict` / `ui` |
| `subject` | ✅ | 简短描述，不超过 50 字符，动词开头，结尾无句号 |
| `body` | ❌ | 详细说明，每行不超过 72 字符，说明 **why** 而非 what |
| `footer` | ❌ | 关联 issue、破坏性变更说明 |

### 提交类型（Type）

| 类型 | 说明 | 示例 |
|------|------|------|
| `feat` | 新功能 | `feat(dict): 新增字典批量导入功能` |
| `fix` | 修复 bug | `fix(login): 修复验证码刷新重复问题` |
| `docs` | 文档变更 | `docs(dev): 更新开发指南目录结构` |
| `style` | 格式变更（不影响代码运行） | `style(product): 修正缩进与换行` |
| `refactor` | 重构（不新增功能，不修复 bug） | `refactor(service): 抽取公共价格校验逻辑` |
| `perf` | 性能优化 | `perf(query): 产品列表查询添加索引` |
| `test` | 测试相关 | `test(user): 补充用户导入单元测试` |
| `build` | 构建配置变更 | `build(docker): 后端镜像切换 JRE 25` |
| `ci` | CI 配置变更 | `ci(github): 新增 PR 自动 lint` |
| `chore` | 其他不修改 src 或 test 的变更 | `chore(deps): 升级 axios 至 1.15.0` |
| `revert` | 回滚提交 | `revert: feat(dict) 字典批量导入功能` |

### 示例

#### 简单提交

```bash
git commit -m "feat(dict): 新增产地管理字典"
```

#### 详细提交

```bash
git commit -m "feat(product): 产品列表支持多维度筛选

- 新增 categoryId / status / keyword 组合查询
- 后端 Specification 动态拼装条件
- 前端 useProductFilter composable 封装筛选逻辑
- 单元测试覆盖 6 个筛选场景

Closes #123"
```

#### 破坏性变更

```bash
git commit -m "feat(api)!: /api/products 响应增加 category 嵌套对象

BREAKING CHANGE: 前端 Product 类型新增 category?: ProductCategory 字段
迁移指南：使用 Omit<Product, 'category'> 兼容旧调用

Refs #456"
```

### 提交规范约束

1. **subject 使用中文**（项目约定），开头动词如"新增"/"修复"/"优化"/"重构"
2. **body 使用中文**，解释 **为什么** 这样改，而非改了 **什么**
3. **一行不超过 72 字符**
4. **关联 issue**：使用 `Closes #xxx` / `Refs #xxx` / `Fixes #xxx`
5. **禁止** 在提交信息中包含敏感信息（密码、Token、密钥）

## 常用命令

### 日常操作

```bash
# 克隆项目
git clone https://github.com/your-org/price-management-system.git
cd price-management-system

# 拉取最新代码
git pull origin master

# 查看当前状态
git status

# 查看修改内容
git diff
git diff --staged

# 暂存与提交
git add .
git add backend/src/main/java/com/pricemanagement/Product.java
git commit -m "feat(product): 新增产品实体"

# 推送分支
git push origin feature/product-entity
```

### 分支操作

```bash
# 查看所有分支
git branch -a

# 创建并切换分支
git checkout -b feature/dict-management

# 切换分支
git checkout develop

# 删除已合并分支
git branch -d feature/old-feature

# 强制删除未合并分支（谨慎使用）
git branch -D feature/abandoned

# 重命名当前分支
git branch -m new-feature-name
```

### 撤销与回滚

```bash
# 撤销工作区修改（未暂存）
git checkout -- file.txt

# 撤销暂存区修改（已 add 未 commit）
git reset HEAD file.txt

# 修改最后一次提交（未推送）
git commit --amend -m "新的提交信息"

# 回退到指定提交（保留修改）
git reset --soft HEAD~1

# 回退到指定提交（丢弃修改）
git reset --hard HEAD~1

# 撤销远程推送的提交（生成反向提交）
git revert <commit-hash>
git push origin master
```

### 同步与合并

```bash
# 拉取远程 develop 并 rebase
git fetch origin
git rebase origin/develop

# 合并 develop 到当前 feature 分支
git merge develop

# 解决冲突后继续 rebase
git add .
git rebase --continue

# 放弃 rebase
git rebase --abort
```

### 标签管理

```bash
# 查看所有标签
git tag -l

# 创建轻量标签
git tag v1.6.8

# 创建带注释标签
git tag -a v1.6.8 -m "v1.6.8 发布版本"

# 推送标签
git push origin v1.6.8
git push origin --tags

# 删除标签
git tag -d v1.6.8
git push origin :refs/tags/v1.6.8
```

### 暂存工作进度

```bash
# 暂存当前修改
git stash

# 暂存并添加说明
git stash save "WIP: 产品列表重构中"

# 查看暂存列表
git stash list

# 恢复最近一次暂存
git stash pop

# 恢复指定暂存
git stash apply stash@{0}

# 删除暂存
git stash drop stash@{0}
```

### 日志与查询

```bash
# 查看简洁提交历史
git log --oneline -20

# 查看图形化分支历史
git log --graph --oneline --all

# 查询包含某关键字的提交
git log --grep="字典" --oneline

# 查询某作者提交
git log --author="zhangsan" --oneline

# 查询修改过某文件的提交
git log --follow -- backend/src/main/java/com/pricemanagement/Product.java
```

### .gitignore 规范

项目根目录 `.gitignore` 必须忽略：

```gitignore
# 后端
backend/target/
backend/.idea/
backend/*.iml
backend/*.log

# 前端
frontend/node_modules/
frontend/dist/
frontend/.env.local
frontend-uniapp/node_modules/
frontend-uniapp/dist/

# 系统
.DS_Store
Thumbs.db

# 敏感配置（绝对禁止提交）
.env
*.pem
*.key
```

## 相关文档

- [CLAUDE.md](../../../CLAUDE.md) §功能变更流程 — 功能变更三步流程
- [development.md](development.md) — 开发流程与代码规范
- [deployment.md](deployment.md) — 部署相关（含 Harbor 镜像备份）
- [docs/ops/操作手册.md](../../ops/操作手册.md) — GitHub 更新与生产部署
