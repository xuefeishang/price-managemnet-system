---
name: git-version
preamble-tier: 1
version: 1.1.0
description: |
  规范化 Git 版本管理流程，确保每次发布都有对应的 tag 和 release 记录。

  v1.1.0 变更：
  - ✅ 新增"特殊情况处理"章节，涵盖公司网络封锁 / deploy key 权限不足 / token 安全三大常见坑
  - ✅ 与 /github-push skill 协作：版本号确定后调用 /github-push 完成推送和 Release 创建

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
  - Skill
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

> **重要**：推送遇到网络/权限问题时，**立即转交** `/github-push` skill 处理。

**正常情况**：

```bash
# 推送 commit
git push origin master

# 推送 tag
git push origin v{版本号}
```

**异常情况速查**：

| 错误 | 含义 | 解决方案 |
|------|------|---------|
| `Recv failure: Connection was reset` | HTTPS 端口被封 | 转 `/github-push` 配代理或 SSH over 443 |
| `Permission denied to deploy key` | deploy key 无写权限 | 转 `/github-push` 用 PAT |
| `Connection timed out` (10.7.5.175) | 内网服务器不通 | 检查网络或改天再推 |
| `Updates were rejected` | 远程有更新 | `git pull --rebase` 后再 push |

完整推送流程（含网络/权限处理）见：**`/github-push` skill**。

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

## 特殊情况处理（v1.1.0 新增）

> 详细处理流程见 **`/github-push` skill**。本章节只是速查，遇到问题立即转交。

### 场景 1：HTTPS 推送失败（Connection was reset）

```bash
# 现象
fatal: unable to access 'https://github.com/xxx.git/':
Recv failure: Connection was reset

# 速查
curl -sS -o /dev/null -w "%{http_code}\n" --connect-timeout 8 https://github.com
# → 000 表示主域名被封
```

**3 种解决方案**（详见 `/github-push` 第一部分）：

1. **Windows 系统代理**（推荐）：`git config --global http.proxy http://127.0.0.1:10808`
2. **SSH over 443**：把 origin 改成 `git@github-ssh-443:owner/repo.git`
3. **HTTP 代理**：测试 `127.0.0.1:7890 / 1080 / 10808` 哪个通

### 场景 2：Permission denied to deploy key

```bash
# 现象
ERROR: Permission to xxx/xxx.git denied to deploy key
```

**说明**：本机 SSH key 是 GitHub 上的 deploy key（只读）。

**3 种解决方案**（详见 `/github-push` 第二部分）：

1. **Personal Access Token**（推荐，最快）
2. 升级 SSH key 为可写（GitHub Settings → Deploy keys → 重新添加并勾选 Allow write access）
3. 用另一个可写 key：`GIT_SSH_COMMAND="ssh -i ~/.ssh/other_key ..." git push`

### 场景 3：token 安全

**铁律**（详见 `/github-push` 第八部分）：

1. **绝不写入明文 token** 到任何文件（包括 skill、commit message）
2. **推送后立即清除** `git remote -v` 中的 token
3. **不留在 bash history**（用 `read -s` 或环境变量）

```bash
# ✅ 安全流程
read -s GITHUB_TOKEN
git remote set-url origin https://${GITHUB_TOKEN}@github.com/owner/repo.git
git push origin master
git remote set-url origin https://github.com/owner/repo.git
unset GITHUB_TOKEN
```

### 与 /github-push skill 协作流程

```bash
# 1. 本 skill（git-version）：版本号、tag、VERSIONS.md
/git-version

# 2. /github-push：处理推送（自动处理网络/权限/token）
/github-push

# 3. /github-push 创建 Release（可选）
/github-push --create-release
```

---

## 更新记录

| 版本 | 日期 | 变更 |
|------|------|------|
| 1.1.0 | 2026-07-04 | 新增"特殊情况处理"章节；与 `/github-push` skill 协作 |
| 1.0.0 | 2026-06-04 | 初版：版本号规范 + 完整发布流程 |

---

*Skill 版本: 1.1.0*
*最后更新: 2026-07-04*
