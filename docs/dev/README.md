---
title: 矿产品价格管理系统 - 开发文档总览
version: v2.0.0
last_updated: 2026-06-15
---

# 📚 矿产品价格管理系统 - 开发文档

> 按 **CLAUDE.md 单一职责原则** 重组的 v2.0 文档结构。
> 原 v1.6.11 大文件保留在 [`backup/`](backup/) 子目录作为历史快照。

---

## 🎯 文档职责划分

| 类别 | 受众 | 何时更新 |
|------|------|----------|
| `stack.md` | 开发者/架构师 | 依赖变更时 |
| `coding-standards.md` | 开发者 | 规范变更时 |
| `api/` | 前端/后端/外部对接方 | API 变更时 |
| `design/` | 架构师/设计师 | 技术/UI 变更时 |
| `workflow/` | 开发者/运维 | 流程变更时 |

## 📁 目录结构

```
docs/dev/
├── README.md                       # 本文件（索引）
├── stack.md                        # 前后端技术栈
├── coding-standards.md             # 代码规范
│
├── api/                            # API 文档
│   ├── README.md                   #   API 总览
│   ├── specs.md                    #   通用规范（响应/分页/错误码）
│   ├── auth.md                     #   认证（JWT + API Key 签名）
│   ├── internal.md                 #   内部 API 速查（/api/**）
│   └── external.md                 #   外部 API 详细（/api/external/v1/**）
│
├── design/                         # 设计文档
│   ├── README.md                   #   设计文档总览
│   ├── architecture.md             #   架构 + 模块说明
│   ├── database.md                 #   数据库表结构 + ER 图
│   ├── api-design.md               #   API 设计文档
│   ├── specifications.md           #   设计规范（字典/颜色/字体/组件）
│   └── ui.md                       #   UI 设计说明
│
├── workflow/                       # 工作流
│   ├── README.md                   #   工作流总览
│   ├── development.md              #   开发流程
│   ├── git.md                      #   Git 规范
│   ├── deployment.md               #   部署指南
│   └── learning-path.md            #   学习路径
│
└── backup/                         # v1.6.11 历史快照（只读，不修改）
    ├── API调用手册.md              #   已废弃，迁移至 api/
    ├── UI设计说明.md               #   已废弃，迁移至 design/ui.md
    ├── 技术栈简明说明.md           #   已废弃，迁移至 stack.md
    ├── 开发指南.md                 #   已废弃，迁移至 workflow/development.md
    ├── 项目设计规范.md             #   已废弃，迁移至 design/specifications.md
    ├── 项目设计文档.md             #   已废弃，迁移至 design/{architecture,database,api-design}.md
    └── 学习路径.md                 #   已废弃，迁移至 workflow/learning-path.md
```

## 🧭 文档应用场景

| 场景 | 起点文档 |
|------|----------|
| 新成员加入 | [本文件](README.md) → [workflow/development.md](workflow/development.md) |
| 开发新功能 | [CLAUDE.md](../../CLAUDE.md) → [design/architecture.md](design/architecture.md) → [coding-standards.md](coding-standards.md) |
| API 联调 | [api/README.md](api/README.md) |
| 部署生产 | [workflow/deployment.md](workflow/deployment.md) |
| UI 设计 | [design/ui.md](design/ui.md) |
| 学习入门 | [workflow/learning-path.md](workflow/learning-path.md) |

## 🔗 跨文档引用规范

本文档使用以下引用格式：

- 同目录：`[文件名](文件名.md)`
- 子目录：`[文件名](子目录/文件名.md)`
- 父目录：`[文件名](../文件名.md)` 或 `[CLAUDE.md](../../CLAUDE.md)`

如遇链接断开（移动文件后未更新），请：
1. 在 IDE 中 `Ctrl+Shift+F` 搜索旧路径
2. 替换为新路径
3. 提交到 master 分支

## 📦 历史归档（backup/）

`backup/` 子目录是 **v1.6.11 的完整快照**，包含 v2.0 拆分前的内容。

**规则**：
- ✅ **可以读取**：查找历史细节、追溯决策
- ❌ **不要修改**：保留作为"重命名前"的可比对快照
- ❌ **不要新增引用**：新功能/新文档只引用 v2.0 结构

## 🔄 从 v1.x 迁移

| v1.x 文件 | v2.0 对应文件 |
|-----------|---------------|
| `开发指南.md` | `workflow/development.md` + `workflow/git.md` + `workflow/deployment.md` + `coding-standards.md`（部分）|
| `技术栈简明说明.md` | `stack.md` |
| `项目设计文档.md` | `design/architecture.md` + `design/database.md` + `design/api-design.md` |
| `项目设计规范.md` | `design/specifications.md` + `coding-standards.md`（部分）|
| `UI设计说明.md` | `design/ui.md` |
| `API调用手册.md` | `api/README.md` + `api/specs.md` + `api/auth.md` + `api/internal.md` + `api/external.md` |
| `学习路径.md` | `workflow/learning-path.md` |

## 📝 文档维护

### 添加新功能时的文档同步

按 **CLAUDE.md 功能变更三步流程**：

1. **代码变更**：API/DB/Entity 改动
2. **同步更新对应文档**（按职责）：
   - 新增端点 → `api/internal.md` 或 `api/external.md`
   - 新增表 → `design/database.md`
   - 新增模块 → `design/architecture.md`
   - 新规范 → `coding-standards.md`
   - 新依赖 → `stack.md`
3. **更新数据字典**：如果是数据库变更

### 文档修改原则

- **单一职责**：一个文件只描述一类内容
- **引用替代重复**：跨文件用 markdown 链接引用
- **frontmatter**：每个文件顶部必须有 title / version / last_updated / source

## 📌 版本历史

- **v2.0.0** (2026-06-15)：按 CLAUDE.md 单一职责原则重组，6 个大文件拆分为 17 个职责化子文件
- **v1.6.11** (2026-06-14)：最后一次"大文件"结构，剩余 14 条偏差清理
- **v1.6.0 ~ v1.6.10**：功能迭代同步的文档增量

详细版本记录见 [`../VERSIONS.md`](../VERSIONS.md)。
