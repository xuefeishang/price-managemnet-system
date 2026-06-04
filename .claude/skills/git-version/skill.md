---
name: git-version
preamble-tier: 1
version: 1.0.0
description: |
  规范化 Git 版本管理流程，确保每次发布都有对应的 tag 和 release 记录。
  
  使用场景：
  - "发布新版本"
  - "打 tag"
  - "创建 release"
  - "版本发布"
  - "准备上线"
  
triggers:
  - 发布版本
  - 打tag
  - 创建release
  - 版本发布
  - 准备上线
allowed-tools:
  - Bash
  - Read
  - Write
  - AskUserQuestion
---

# Git 版本管理 Skill

规范化版本发布流程，确保每次生产部署都有对应的 Git tag 和 release 记录。

---

## 版本命名规范

### 格式

```
v{主版本}.{次版本}.{补丁版本}-{YYYYMMDD}
```

**示例**：`v1.5.0-20260604`

### 版本号递增规则

| 类型 | 递增规则 | 示例 |
|------|---------|------|
| **主版本 (Major)** | 架构重大变更、不兼容更新 | 1.x.x → 2.0.0 |
| **次版本 (Minor)** | 新增功能、向后兼容 | 1.4.x → 1.5.0 |
| **补丁版本 (Patch)** | Bug 修复、小优化 | 1.4.0 → 1.4.1 |

### 日期后缀

使用发布当天的日期（YYYYMMDD），便于追溯和回滚。

---

## 版本记录文件

### 位置

```
/docs/VERSIONS.md
```

### 格式

```markdown
# 版本发布记录

## v1.5.0-20260604 (2026-06-04)

### 新增功能
- 功能描述1
- 功能描述2

### 修复问题
- 问题描述1

### 技术改进
- 改进描述1

### 提交记录
- `abc1234` - commit message 1
- `def5678` - commit message 2

---

## v1.4.0-20260525 (2026-05-25)

...
```

---

## 发布流程

### 步骤 1：检查当前状态

```bash
# 检查分支
git branch --show-current

# 检查未提交修改
git status --short

# 查看最近的 tag
git tag --sort=-creatordate | head -5
```

**要求**：
- 必须在 `master` 分支
- 工作区必须干净（无未提交修改）

---

### 步骤 2：确定版本号

通过 AskUserQuestion 询问发布类型：

| 选项 | 版本号变化 | 说明 |
|------|-----------|------|
| 主版本发布 | Major + 1 | 架构重大变更 |
| 次版本发布 | Minor + 1 | 新增功能（推荐） |
| 补丁版本 | Patch + 1 | Bug 修复 |

**自动计算逻辑**：

```bash
# 获取最新 tag
LATEST_TAG=$(git tag --sort=-creatordate | head -1)

# 解析版本号（示例：v1.4.0-20260525）
# 根据发布类型递增对应位置
# 添加当天日期后缀
```

---

### 步骤 3：更新版本记录文件

在 `docs/VERSIONS.md` 中新增版本记录：

```markdown
## v{版本号} ({日期})

### 新增功能
- {功能列表}

### 修复问题
- {修复列表}

### 提交记录
{自上个版本以来的 commit 列表}
```

---

### 步骤 4：提交版本更新

```bash
# 添加版本记录文件
git add docs/VERSIONS.md

# 提交
git commit -m "chore: 发布 v{版本号}

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### 步骤 5：创建 Git Tag

```bash
# 创建 annotated tag
git tag -a v{版本号} -m "Release v{版本号}

主要变更：
- 变更1
- 变更2

发布日期：{YYYY-MM-DD}"
```

---

### 步骤 6：推送到远程

```bash
# 推送 commit
git push origin master

# 推送 tag
git push origin v{版本号}
```

---

### 步骤 7：创建 GitHub Release（可选）

```bash
# 使用 gh CLI 创建 release
gh release create v{版本号} \
  --title "v{版本号}" \
  --notes-file docs/release-notes/v{版本号}.md \
  --latest
```

---

## 一键发布命令

完整流程合并：

```bash
# 1. 检查状态
git status --short && git branch --show-current

# 2. 更新版本记录（手动编辑 docs/VERSIONS.md）

# 3. 提交并打 tag
VERSION="v1.5.0-$(date +%Y%m%d)"
git add docs/VERSIONS.md
git commit -m "chore: 发布 ${VERSION}"
git tag -a "${VERSION}" -m "Release ${VERSION}"

# 4. 推送
git push origin master
git push origin "${VERSION}"
```

---

## 版本历史查询

### 查看所有版本

```bash
git tag --sort=-creatordate
```

### 查看版本详情

```bash
git show v1.4.0-20260525
```

### 查看两个版本之间的变更

```bash
git log v1.4.0-20260525..v1.5.0-20260604 --oneline
```

### 比较版本差异

```bash
git diff v1.4.0-20260525 v1.5.0-20260604 --stat
```

---

## 回滚操作

### 回滚到指定版本

```bash
# 检出指定版本的代码
git checkout v1.4.0-20260525

# 或重置当前分支到指定版本
git reset --hard v1.4.0-20260525
```

### 删除错误的 tag

```bash
# 删除本地 tag
git tag -d v1.5.0-20260604

# 删除远程 tag
git push origin --delete v1.5.0-20260604
```

---

## 版本号自动计算示例

```bash
#!/bin/bash
# 当前最新版本：v1.4.0-20260525
# 发布类型：次版本发布

# 解析
CURRENT="v1.4.0-20260525"
MAJOR=1
MINOR=4
PATCH=0

# 次版本发布：Minor + 1, Patch = 0
NEW_MAJOR=1
NEW_MINOR=5
NEW_PATCH=0

# 添加当天日期
DATE=$(date +%Y%m%d)  # 20260604

# 新版本号
NEW_VERSION="v${NEW_MAJOR}.${NEW_MINOR}.${NEW_PATCH}-${DATE}"
# 结果：v1.5.0-20260604
```

---

## 检查清单

发布前确认：

- [ ] 当前在 `master` 分支
- [ ] 工作区干净，无未提交修改
- [ ] 版本号已正确递增
- [ ] `docs/VERSIONS.md` 已更新
- [ ] commit 信息规范
- [ ] tag 已创建并推送
- [ ] GitHub Release 已创建（可选）

---

## 与 deploy skill 协作

发布流程通常与 deploy skill 配合使用：

```bash
# 1. 先执行版本发布（本 skill）
/git-version

# 2. 再执行生产部署
/deploy
```

---

*Skill 版本: 1.0.0*
*最后更新: 2026-06-04*
