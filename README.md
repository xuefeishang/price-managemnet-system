# 矿产品价格管理系统

企业级前后端分离的矿产品价格展示与管理系统，面向企业内部员工使用。

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3 + TypeScript + Vant UI + Pinia + ECharts 5 |
| 后端 | Spring Boot 3.2.0 + Java 17 |
| 数据库 | MySQL 8.4 |
| 认证 | JWT |

## 主要功能

- 用户认证与权限管理（JWT + 三种角色）
- 首页日期选择，查看历史价格
- 产品分类管理
- 产品与价格管理
- 价格历史记录 + **ECharts 折线图可视化**
- Excel导入导出
- 响应式设计（PC/移动端适配）
- **操作日志管理（仅管理员）**
  - 日志列表查询与筛选
  - **统计分析（趋势图、饼图、柱状图）**
  - 月度报表
  - 年度报表 + 用户排行
- **审批流程管理（仅管理员）**
  - 审批工作流配置（创建、编辑、删除、激活/停用）
  - 审批节点配置（节点类型、审批角色、顺序）
  - 审批请求管理（待审批、我的申请）

## 快速启动

### 1. 初始化数据库

```sql
CREATE DATABASE IF NOT EXISTS price_management DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE price_management;
source backend/src/main/resources/init.sql;
```

### 2. 启动后端

```bash
cd backend
mvnw spring-boot:run
```

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

### 4. 访问应用

- 前端：http://localhost:5173
- 后端：http://localhost:8080

## 默认用户

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | 管理员 |
| editor | editor123 | 编辑者 |
| viewer | viewer123 | 查看者 |

## 数据库脚本

| 文件 | 说明 |
|------|------|
| `backend/src/main/resources/init.sql` | 数据初始化（表结构+数据） |

## 项目文档

- [项目设计文档](项目设计文档.md) - 技术设计文档
- [项目完成总结](项目完成总结.md) - 完成情况汇总
- [IDEA部署指南](IDEA部署指南.md) - 部署教程（本地+生产）
