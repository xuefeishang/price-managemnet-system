---
title: 工作流总览
version: v2.0.0
last_updated: 2026-06-15
source: docs/dev/backup/开发指南.md
---

# 开发工作流总览

本目录将原 `开发指南.md`（v1.6.8）按职责拆分为 5 个独立文件，便于按场景查阅与维护。

## 文件索引

| 文件 | 职责 | 来源章节 | 受众 |
|------|------|---------|------|
| [README.md](README.md) | 工作流总览与文档对应关系 | 本文件 | 全部成员 |
| [development.md](development.md) | 开发流程：环境准备、快速开始、代码规范、PC/移动端适配、按钮设计、菜单规范 | §开发环境准备 / §快速开始 / §开发流程 / §后端启动前置条件 / §mvn 命令详解 / §3 个常见错误 | 开发者 |
| [git.md](git.md) | Git 分支管理、提交规范、常用命令 | §Git 分支管理 / §提交规范 / §常用命令 | 开发者 |
| [deployment.md](deployment.md) | Docker 部署、端口架构、Harbor 镜像备份、环境变量、生产部署检查清单、回滚方案 | §部署说明 + 技术栈简明说明 §三/§八 | 运维/开发者 |
| [learning-path.md](learning-path.md) | Spring Boot / 前端 / 部署学习路径建议 | backup/学习路径.md（完整复制） | 新成员 |

## 工作流与项目文档对应关系

开发工作流与本项目其他文档互为补充：

| 工作流场景 | 主文档 | 辅助文档 |
|----------|--------|---------|
| 新成员上手 | [learning-path.md](learning-path.md) | [README.md](../../../README.md) → [docs/ops/IDEA部署指南.md](../../ops/IDEA部署指南.md) |
| 日常开发 | [development.md](development.md) | [CLAUDE.md](../../../CLAUDE.md) → [design/architecture.md](../design/architecture.md) |
| 提交代码 | [git.md](git.md) | [CLAUDE.md](../../../CLAUDE.md) §功能变更流程 |
| 部署上线 | [deployment.md](deployment.md) | [docs/ops/IDEA部署指南.md](../../ops/IDEA部署指南.md) → [docs/ops/操作手册.md](../../ops/操作手册.md) |
| 查 API | — | [api/README.md](../api/README.md) |
| 设计规范 | — | [design/specifications.md](../design/specifications.md) → [design/ui.md](../design/ui.md) |
| 功能完成汇报 | — | [docs/archive/项目完成总结.md](../../archive/项目完成总结.md) |

## 文档维护原则

1. **变更驱动更新**：本目录文件只在对应职责内容变更时更新
2. **引用替代重复**：其他文档需要描述相同内容时，使用"参考 workflow/xxx.md"链接
3. **单一职责**：每个文件只描述其职责范围内的内容
4. **不修改 backup**：原 `docs/dev/backup/` 仅作为历史归档保留，不再更新

## 版本与来源

- 当前版本：v2.0.0
- 重构日期：2026-06-15
- 重构来源：
  - `docs/dev/backup/开发指南.md`（v1.6.8）
  - `docs/dev/backup/技术栈简明说明.md`（§三 Docker 部署 / §八 环境变量）
  - `docs/dev/backup/学习路径.md`（完整复制）

## 相关文档

- [CLAUDE.md](../../../CLAUDE.md) — 项目规范（永久文档）
- [docs/dev/README.md](../README.md) — v2.0 开发文档总览
- [design/architecture.md](../design/architecture.md) — 技术选型、模块与架构设计
- [docs/ops/IDEA部署指南.md](../../ops/IDEA部署指南.md) — 本地/生产部署详细步骤
