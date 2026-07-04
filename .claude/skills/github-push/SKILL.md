---
name: github-push
preamble-tier: 1
version: 1.0.0
description: |
  规范化 GitHub 推送流程，解决网络封锁与权限问题。

  核心要点：
  - **不破坏 git history** —— token 必须在推送后立即从 origin URL 移除
  - **绝不写入明文 token** —— skill 文档里只用 `${GITHUB_TOKEN}` 占位符
  - **三种推送通道**：HTTPS+PAT / SSH+可写 SSH key / SSH over 443（绕过 22 端口封锁）
  - **公司网络常用配置**：Windows 系统代理 `127.0.0.1:10808` 或 SSH over 443

  使用场景：
  - "推送到 GitHub"
  - "git push 失败"
  - "网络连不上 GitHub"
  - "Permission denied to deploy key"
  - "Connection was reset"
  - "更新 origin URL"

triggers:
  - 推送GitHub
  - git push失败
  - 连接GitHub被重置
  - deploy key权限不足
  - 更新origin
  - 配置代理推送
allowed-tools:
  - Bash
  - Read
  - AskUserQuestion
---

# GitHub 推送 Skill

规范化 GitHub 推送流程，覆盖 **网络封锁**、**权限问题**、**token 安全** 三大类常见坑。

---

## ⚠️ 安全铁律（必须遵守）

> **1. 绝不把 token 写入任何文件**（包括 skill、commit message、文档）
> **2. token 推送后必须立即从 `git remote -v` 中清除**
> **3. token 不要明文出现在 bash 历史里**（用变量或 read -s）

如果发现 token 泄漏到 git history，必须立刻：
1. 在 GitHub 上 **Revoke** 旧 token
2. 重新生成新 token
3. 用 `git filter-repo` 从 history 里清除（操作前确认团队无 PR 基于此 commit）

---

## 第一部分：网络问题排查

### 问题 1：HTTPS 连接被重置

```
fatal: unable to access 'https://github.com/xxx/xxx.git/':
Recv failure: Connection was reset
```

**原因**：公司网络/防火墙拦截了 `github.com:443`，但 `api.github.com` 通常是通的。

**诊断**：

```bash
curl -sS -o /dev/null -w "github.com: %{http_code}\n" --connect-timeout 8 https://github.com
curl -sS -o /dev/null -w "api.github.com: %{http_code}\n" --connect-timeout 8 https://api.github.com
```

如果 `github.com` 返回 `000` 而 `api.github.com` 返回 `200/301`，说明主域名被封。

### 解决方案 A：Windows 系统代理

如果机器有 HTTP 代理（Windows 设置 → 代理 → 手动设置代理）：

```bash
# 1. 查代理端口（PowerShell）
powershell.exe -Command "(Get-ItemProperty 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings').ProxyServer"
# 典型输出：127.0.0.1:10808

# 2. 测连通性
curl -sS -o /dev/null -w "%{http_code}\n" --connect-timeout 8 -x http://127.0.0.1:10808 https://github.com

# 3. 配置 git 走代理
git config --global http.proxy http://127.0.0.1:10808
git config --global https.proxy http://127.0.0.1:10808
```

### 解决方案 B：SSH over 443（绕开 22 端口封锁）

GitHub 提供 `ssh.github.com:443` 作为 SSH 入口，绕过公司对 22 端口的封锁。

```bash
# 1. 测试连通性
ssh -T -p 443 -o StrictHostKeyChecking=no -o ConnectTimeout=5 ssh.github.com

# 2. 在 ~/.ssh/config 加别名
cat >> ~/.ssh/config << 'EOF'

# GitHub via 443
Host github-ssh-443
    HostName ssh.github.com
    Port 443
    User git
    IdentityFile ~/.ssh/id_ed25519
    IdentitiesOnly yes
EOF

# 3. 修改 origin URL 使用 443 别名
git remote set-url origin git@github-ssh-443:xuefeishang/price-managemnet-system.git
```

### 解决方案 C：HTTP/SOCKS 代理（curl 测试发现可用的）

```bash
# 测试常见代理端口
for port in 7890 1080 10808 10809 8888; do
    echo -n "127.0.0.1:$port → "
    curl -sS -o /dev/null -w "%{http_code}\n" --connect-timeout 3 -x http://127.0.0.1:$port https://github.com
done

# 用通的端口配置 git
git config --global http.proxy http://127.0.0.1:<端口>
```

---

## 第二部分：权限问题排查

### 问题 2：deploy key 无 master 写权限

```
ERROR: Permission to xxx/xxx.git denied to deploy key
fatal: Could not read from remote repository.
```

**原因**：本机 SSH key（`id_ed25519` / `id_rsa`）在 GitHub 上注册为 **deploy key（部署密钥）**，不是账号下的 SSH key。Deploy key 默认：
- ✅ 可读
- ❌ 默认不可写（除非创建时勾选 "Allow write access"）

**诊断**：

```bash
ssh -T -o StrictHostKeyChecking=no -o ConnectTimeout=5 git@github.com
# "Hi xxx/xxx! You've successfully authenticated, but GitHub does not provide shell access."
# 如果用户名前面是组织名/仓库名 → deploy key（只读）
# 如果用户名是个人 → SSH key（可写）
```

### 解决方案：三种选择

#### 选项 1：用 Personal Access Token（推荐）

最简单，不需要改 SSH 配置。

```bash
# 1. 用户在 https://github.com/settings/tokens 生成 token（勾选 repo 权限）

# 2. 临时设置 origin URL（带 token）
git remote set-url origin https://${GITHUB_TOKEN}@github.com/owner/repo.git

# 3. 推送
git push origin master
git push origin v{tag}

# 4. ⚠️ 推送后立即清除 token
git remote set-url origin https://github.com/owner/repo.git

# 5. 验证
git remote -v | grep -v token  # 确认无 token
```

**token 安全规范**：

```bash
# ✅ 推荐：shell 变量（不留在 history）
read -s GITHUB_TOKEN
git remote set-url origin https://${GITHUB_TOKEN}@github.com/owner/repo.git
git push origin master
git remote set-url origin https://github.com/owner/repo.git

# ✅ 推荐：环境变量
export GITHUB_TOKEN=ghp_xxx
git -c http.extraHeader="Authorization: token ${GITHUB_TOKEN}" push origin master
unset GITHUB_TOKEN

# ❌ 禁止：明文写在命令里
git remote set-url origin https://ghp_xxx@github.com/owner/repo.git  # token 会留在 bash history 和 .git/config
```

#### 选项 2：把当前 key 升级为可写 SSH key

1. 在 GitHub 上：**Settings → Developer settings → Personal access tokens → Tokens (classic)**
   或者直接在仓库：**Settings → Deploy keys → 删除当前 key**
2. 重新添加同一个公钥，但**勾选 "Allow write access"**
3. 推送：`git push origin master`

或者把这个 key 加到 **账号级 SSH keys**：

1. https://github.com/settings/keys → New SSH key
2. 粘贴公钥（`cat ~/.ssh/id_ed25519.pub`）
3. 推送：`git push origin master`

#### 选项 3：临时用另一个可写 key

```bash
# 如果有另一个有写权限的 key
GIT_SSH_COMMAND="ssh -i ~/.ssh/your_writable_key -o IdentitiesOnly=yes" \
  git push origin master
GIT_SSH_COMMAND="ssh -i ~/.ssh/your_writable_key -o IdentitiesOnly=yes" \
  git push origin v{tag}
```

---

## 第三部分：标准推送流程

### 完整流程（推荐）

```bash
# ==== 步骤 1：检查 ====
git status --short                              # 工作区状态
git branch --show-current                       # 当前分支
git log origin/master..HEAD --oneline           # 领先多少 commit
git tag --sort=-creatordate | head -3            # 最近 tag

# ==== 步骤 2：配置推送通道 ====

# 方式 A：HTTPS + PAT（推荐，简单）
read -s GITHUB_TOKEN
git remote set-url origin https://${GITHUB_TOKEN}@github.com/owner/repo.git

# 方式 B：SSH over 443（如果 SSH key 可写但 22 端口被封）
git remote set-url origin git@github-ssh-443:owner/repo.git

# 方式 C：Windows 代理（如果 HTTPS 被封）
git config --global http.proxy http://127.0.0.1:10808
# 然后走方式 A

# ==== 步骤 3：推送 commits ====
git push origin master

# ==== 步骤 4：推送 tags ====
git push origin --tags
# 或单独推某个
git push origin v{tag}

# ==== 步骤 5：清理（重要）====
# 还原 origin URL（清除 token）
git remote set-url origin https://github.com/owner/repo.git
unset GITHUB_TOKEN

# 验证无残留
git remote -v                    # 应该没有 token
git config --list | grep proxy   # 代理保留（下次复用）
```

---

## 第四部分：创建 GitHub Release

### 方式 A：用 gh CLI（需要先安装）

```bash
# 1. 认证
gh auth login

# 2. 创建 release（带 notes 文件）
gh release create v{tag} \
  --title "v{tag} - 一句话描述" \
  --notes-file docs/dev/RELEASE-v{tag}.md \
  --latest

# 3. 列出所有 release
gh release list
```

### 方式 B：用 GitHub API（无需 gh CLI）

```bash
# 1. 准备 release body JSON
cat > /tmp/release-body.json << 'JSONEOF'
{
  "tag_name": "v{tag}",
  "name": "v{tag} - 描述",
  "body": "## 变更摘要\n- 内容",
  "draft": false,
  "prerelease": false
}
JSONEOF

# 2. POST 创建
curl -sS -X POST \
  -H "Authorization: token ${GITHUB_TOKEN}" \
  -H "Accept: application/vnd.github+json" \
  -H "Content-Type: application/json" \
  -d @/tmp/release-body.json \
  https://api.github.com/repos/owner/repo/releases

# 3. 清理
rm /tmp/release-body.json
unset GITHUB_TOKEN
```

### 完整脚本模板（保存为 `scripts/release.sh`）

```bash
#!/bin/bash
set -euo pipefail

VERSION="${1:?用法: $0 v2.2.1-20260704}"
NOTES_FILE="${2:-docs/dev/RELEASE-${VERSION}.md}"

echo "📦 创建 Release ${VERSION}"
echo "   notes: ${NOTES_FILE}"

if [ ! -f "${NOTES_FILE}" ]; then
    echo "❌ Notes 文件不存在: ${NOTES_FILE}"
    exit 1
fi

# 用 jq 或 Python 构造 JSON body
BODY=$(python3 -c "
import json, sys
with open('${NOTES_FILE}', encoding='utf-8') as f:
    body = f.read()
print(json.dumps({
    'tag_name': '${VERSION}',
    'name': '${VERSION}',
    'body': body,
    'draft': False,
    'prerelease': False
}))
")

echo "${BODY}" > /tmp/release-body.json

read -s -p "GitHub Token: " GITHUB_TOKEN
echo ""

curl -sS -X POST \
  -H "Authorization: token ${GITHUB_TOKEN}" \
  -H "Accept: application/vnd.github+json" \
  -H "Content-Type: application/json" \
  -d @/tmp/release-body.json \
  https://api.github.com/repos/${GITHUB_REPO}/releases | head -10

rm /tmp/release-body.json
unset GITHUB_TOKEN
echo "✅ Release 创建完成"
```

---

## 第五部分：常见错误速查表

| 错误 | 原因 | 解决方案 |
|------|------|---------|
| `Recv failure: Connection was reset` | HTTPS 被封 | 配代理 / SSH over 443 |
| `Permission denied (publickey)` | SSH key GitHub 不认识 | 用 deploy key / 配 SSH key |
| `Permission denied to deploy key` | deploy key 无写权限 | 用 PAT / 升级 SSH key |
| `could not read from remote repository` | 网络或认证问题 | 看上面两类 |
| `Repository not found` | URL 写错 / 无权限 | 确认 owner/repo 拼写 |
| `Updates were rejected` | 远程有 commit 没拉 | `git pull --rebase` 后再 push |
| `non-fast-forward` | 本地落后于远程 | `git pull` 后再 push |
| `RPC failed; HTTP 413` | 单文件超过 100MB | 用 Git LFS |
| `fatal: unable to access` | 网络/代理/DNS | 综合排查 |

---

## 第六部分：排查流程图

```
git push 失败
    │
    ├── 网络错误（Recv failure / Connection timed out）
    │     │
    │     ├── HTTPS → 测试 github.com 可达性
    │     │     │
    │     │     ├── 可达 → 正常推送
    │     │     └── 不可达 → 用代理 / SSH over 443
    │     │
    │     └── SSH → ssh -T git@github.com 测试
    │           │
    │           ├── 22 端口被封 → 用 SSH over 443
    │           └── 443 也封 → 用 PAT + HTTPS 代理
    │
    └── 权限错误（Permission denied / denied to deploy key）
          │
          ├── deploy key（只读）→ 用 PAT 推送
          └── SSH key 可写 → 直接 push
```

---

## 第七部分：本项目特定配置

### 当前仓库

- **owner**: `xuefeishang`
- **repo**: `price-managemnet-system`（注意原仓库名拼写 `managemnet`，保持不动）
- **origin URL 模板**: `https://github.com/xuefeishang/price-managemnet-system.git`

### 本机已配代理（2026-07-04 验证可用）

```
git config --global http.proxy http://127.0.0.1:10808
git config --global https.proxy http://127.0.0.1:10808
```

### 本机 SSH key 状态

- `id_ed25519` (xuefeishang@gmail.com) → 在 GitHub 是 deploy key（只读）
- `id_rsa` (administrator@shangxuefei) → GitHub 不认识

**推荐推送方式**：用 PAT + HTTPS（走 10808 代理）

---

## 第八部分：token 安全清单

推送完毕后，**立即**确认：

- [ ] `git remote -v` 中无 token
- [ ] bash history 中无明文 token（`history | grep ghp_`）
- [ ] 临时 JSON 文件已删除（如 `/tmp/release-body.json`）
- [ ] 草稿文档（如 `scripts/.tmp-*.json`）已删除
- [ ] token 没有出现在 git log / commit message 里
- [ ] 团队其他成员没有共享 token（每个人用自己账号的 PAT）

---

## 与其他 skill 的协作

### 与 git-version 配合

```bash
# 1. /git-version（版本号、tag）
# 2. /github-push（推 master + tag）
# 3. /github-push 创建 Release（可选）
```

### 与 deploy 配合

```bash
# 1. /github-push 推到 GitHub
# 2. /deploy 从 GitHub 拉到生产服务器
```

---

## 更新记录

| 版本 | 日期 | 变更 |
|------|------|------|
| 1.0.0 | 2026-07-04 | 初版：基于 v2.2.1-20260704 推送实战经验（公司网络封锁 → SSH over 443 → Windows 代理 → PAT） |

---

*Skill 版本: 1.0.0*
*最后更新: 2026-07-04*