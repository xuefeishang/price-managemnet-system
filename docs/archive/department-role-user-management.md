# 部门-角色-用户三层管理体系规划

## 一、需求分析

### 1.1 核心需求

| 模块 | 需求描述 |
|------|---------|
| **部门管理** | 树状结构展示、拖拽调整层级、支持总部/子公司/孙公司等多级管理 |
| **角色管理** | 优化现有页面展示、角色与权限绑定、支持自定义角色 |
| **用户管理** | 用户关联部门、用户绑定角色、通过角色获取权限 |

### 1.2 现状分析

**已有基础设施：**
- `sys_user` 表：有 `department` 字段（VARCHAR 字符串，无外键关联）
- `sys_role` 表：角色基础信息（role_code, role_name, is_system）
- `sys_permission` 表：权限定义（20个预设权限）
- `sys_user_role` 表：用户-角色多对多关联
- `sys_role_permission` 表：角色-权限多对多关联

**存在问题：**
1. User 实体使用枚举 `Role`，与 `sys_role` 表脱节
2. `department` 字段是自由文本，无结构化管理
3. 权限系统已建表但未真正使用（前端硬编码权限判断）
4. 角色管理页面展示混乱

---

## 二、数据库设计

### 2.1 新增：部门表 `sys_department`

```sql
CREATE TABLE sys_department (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '部门ID',
    parent_id BIGINT COMMENT '父部门ID（NULL表示顶级）',
    dept_code VARCHAR(50) NOT NULL UNIQUE COMMENT '部门编码',
    dept_name VARCHAR(100) NOT NULL COMMENT '部门名称',
    dept_type VARCHAR(20) NOT NULL DEFAULT 'DEPARTMENT' COMMENT '类型：HEADQUARTERS总部/COMPANY公司/DEPARTMENT部门',
    leader_id BIGINT COMMENT '部门负责人ID',
    sort_order INT DEFAULT 0 COMMENT '排序',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    path VARCHAR(500) COMMENT '层级路径（如：1/2/3）',
    level INT DEFAULT 1 COMMENT '层级深度',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_parent_id (parent_id),
    INDEX idx_dept_code (dept_code),
    INDEX idx_path (path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门组织表';
```

### 2.2 修改：用户表 `sys_user`

```sql
-- 新增字段
ALTER TABLE sys_user ADD COLUMN dept_id BIGINT COMMENT '部门ID';
ALTER TABLE sys_user ADD INDEX idx_dept_id (dept_id);

-- 删除旧字段（迁移后）
-- ALTER TABLE sys_user DROP COLUMN department;
```

### 2.3 修改：角色表 `sys_role`

```sql
-- 新增字段
ALTER TABLE sys_role ADD COLUMN dept_id BIGINT COMMENT '所属部门（NULL表示全局角色）';
ALTER TABLE sys_role ADD INDEX idx_dept_id (dept_id);
```

### 2.4 权限表保持不变

`sys_permission` 已有20个权限，结构合理，无需修改。

### 2.5 ER 关系图

```
sys_department (树状自关联)
    ↑
    │ 1:N
    │
sys_user ──M:N── sys_role ──M:N── sys_permission
    │
    └── dept_id 外键

sys_role.dept_id → sys_department.id (部门专属角色)
```

---

## 三、后端实现

### 3.1 新增实体

**Department.java**
```java
@Entity
@Table(name = "sys_department")
public class Department {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long parentId;
    private String deptCode;
    private String deptName;
    private String deptType; // HEADQUARTERS, COMPANY, DEPARTMENT
    private Long leaderId;
    private Integer sortOrder;
    private String status;
    private String path;     // 层级路径：1/2/3
    private Integer level;   // 层级深度

    @Transient
    private List<Department> children; // 子部门（树状展示用）
}
```

### 3.2 修改实体

**User.java**
```java
// 移除枚举 Role，改为关联
@Column(name = "dept_id")
private Long deptId;

// 保留 role 字段作为主角色（兼容），但实际权限从 sys_user_role 获取
```

### 3.3 新增 API 接口

**DepartmentController.java**
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/departments/tree` | 获取部门树 |
| GET | `/api/departments` | 获取部门列表（扁平） |
| POST | `/api/departments` | 创建部门 |
| PUT | `/api/departments/{id}` | 更新部门 |
| DELETE | `/api/departments/{id}` | 删除部门 |
| PUT | `/api/departments/{id}/move` | 移动部门（拖拽） |
| PUT | `/api/departments/sort` | 批量排序 |

**RoleController 增强**
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/roles/{id}/permissions` | 获取角色权限 |
| PUT | `/api/roles/{id}/permissions` | 更新角色权限 |

**UserController 增强**
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/users/{id}/roles` | 获取用户角色 |
| PUT | `/api/users/{id}/roles` | 分配用户角色 |
| GET | `/api/users/{id}/permissions` | 获取用户所有权限 |

### 3.4 权限服务增强

**PermissionService.java**
```java
// 获取用户所有权限（通过角色）
public Set<String> getUserPermissions(Long userId) {
    // 1. 查询用户角色
    // 2. 查询角色权限
    // 3. 合并去重返回
}

// 检查用户是否有某权限
public boolean hasPermission(Long userId, String permissionCode) {
    return getUserPermissions(userId).contains(permissionCode);
}
```

---

## 四、前端实现

### 4.1 新增页面

**DepartmentManagement.vue**
- 左侧：部门树（可折叠、拖拽）
- 右侧：部门详情编辑
- 拖拽组件：使用 `vuedraggable` 或自实现

**RoleManagement.vue（重构）**
- 角色列表（卡片或表格）
- 权限配置弹窗（树状勾选）

### 4.2 修改页面

**UserManagement.vue**
- 部门字段改为下拉选择（树状选择器）
- 角色字段改为多选（支持多角色）

### 4.3 新增组件

**DeptTreeSelect.vue**
- 树状下拉选择器
- 支持搜索、多选

**PermissionTree.vue**
- 权限树状勾选组件
- 支持全选/半选状态

### 4.4 类型定义

**types/index.ts 新增**
```typescript
// 部门类型
export type DeptType = 'HEADQUARTERS' | 'COMPANY' | 'DEPARTMENT'

export interface Department {
  id: number
  parentId: number | null
  deptCode: string
  deptName: string
  deptType: DeptType
  leaderId?: number
  sortOrder: number
  status: string
  path?: string
  level?: number
  children?: Department[]
  createdTime: string
  updatedTime: string
}

// 角色权限
export interface RolePermission {
  roleId: number
  permissionIds: number[]
}
```

---

## 五、权限体系设计

### 5.1 权限类型

| 类型 | 说明 | 示例 |
|------|------|------|
| MENU | 菜单权限 | home:view, product:view |
| BUTTON | 按钮权限 | product:create, user:delete |
| API | 接口权限（预留） | api:product:write |

### 5.2 权限继承规则

```
ADMIN → 所有权限
EDITOR → 产品、价格、分类、产地、客户相关
VIEWER → 所有 :view 权限
自定义角色 → 按需分配
```

### 5.3 前端权限判断

**当前方式（硬编码）：**
```typescript
const { hasPermission } = usePermission()
if (hasPermission(Permission.USER_CREATE)) { ... }
```

**改进方式（动态）：**
```typescript
// 从后端获取用户权限列表
const userPermissions = ref<Set<string>>(new Set())

const hasPermission = (code: string) => userPermissions.value.has(code)

// 或使用指令
<button v-permission="'user:create'">创建用户</button>
```

---

## 六、实施步骤

### Phase 1：部门管理（2-3天）

1. 创建 `sys_department` 表
2. 后端：Department 实体、Repository、Service、Controller
3. 前端：DepartmentManagement.vue（树状展示 + 拖拽）
4. 数据迁移：将现有 department 字符串迁移到新表

### Phase 2：角色权限增强（1-2天）

1. 增强 SysRoleController（权限分配接口）
2. 创建 PermissionService
3. 前端：RoleManagement.vue 重构
4. 前端：PermissionTree 组件

### Phase 3：用户管理增强（1-2天）

1. 修改 User 实体（添加 dept_id）
2. 增强 UserController（角色分配接口）
3. 前端：UserManagement.vue 优化
4. 前端：DeptTreeSelect 组件

### Phase 4：权限系统集成（1天）

1. 登录时加载用户权限
2. 前端权限判断改为动态
3. 菜单根据权限动态显示
4. 测试验证

---

## 七、二次评审与优化

### 7.1 现有权限系统深度分析

**发现关键问题：**

前端 `usePermission.ts` 中存在**硬编码的角色-权限映射**（52-118行）：
```typescript
const rolePermissions: Record<string, string[]> = {
  ADMIN: [...],  // 硬编码所有权限
  EDITOR: [...], // 硬编码部分权限
  VIEWER: [...], // 硬编码查看权限
}
```

**问题影响：**
1. 权限判断完全依赖前端硬编码，与后端 `sys_permission` / `sys_role_permission` 表脱节
2. 新增角色或修改权限需要修改前端代码并重新部署
3. 自定义角色无法生效

**解决方案：**
- 登录时从后端获取用户实际权限列表
- 存入 userStore，`hasPermission` 从 store 中读取
- 保留 `Permission` 常量作为权限码定义（与后端 `permission_code` 对齐）

### 7.2 风险点

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 数据迁移 | 现有 department 字符串丢失 | 先备份，迁移后保留原字段一段时间 |
| 权限判断变更 | 可能影响现有功能 | **保留 hasPermission 接口签名不变，内部实现改为动态读取** |
| 拖拽实现复杂 | 前端开发周期长 | 可先实现基础树状展示，拖拽作为增强功能 |
| 权限码不一致 | 前后端 Permission 常量不同 | 统一权限码命名规范，前端常量与后端 init.sql 对齐 |

### 7.3 优化建议（评审后调整）

1. **部门类型简化**：评审后决定保留三种类型（HEADQUARTERS/COMPANY/DEPARTMENT），便于区分不同层级实体

2. **权限码对齐**：
   - 前端 `Permission` 常量已定义 20+ 权限码
   - 后端 `init.sql` 定义 20 个权限码
   - **需要对齐**：前端新增的权限码（如 `product:import`, `approval:process`）需补充到后端

3. **角色继承**：暂不实现角色继承，保持简单。自定义角色直接分配权限即可。

4. **数据权限**：预留 `sys_department` 表的 `data_scope` 字段，后续扩展数据权限（用户只能看本部门数据）

5. **缓存优化**：用户权限缓存到 Redis（已有 Redis 缓存方案），减少数据库查询

6. **兼容性设计**：
   - 保留 User.role 枚举字段作为"主角色"，便于快速判断
   - 权限判断优先使用动态获取的权限列表
   - 前端 `usePermission` 接口签名保持不变

### 7.4 权限码对齐清单

| 前端定义 | 后端定义 | 状态 |
|---------|---------|------|
| product:view | 存在 | 对齐 |
| product:create | 存在 | 对齐 |
| product:edit | 存在 | 对齐 |
| product:delete | 存在 | 对齐 |
| product:import | 缺失 | 需补充 |
| product:export | 缺失 | 需补充 |
| price:view | 存在 | 对齐 |
| price:edit | 存在 | 对齐 |
| price:approve | 缺失 | 需补充 |
| user:view | 存在 | 对齐 |
| user:create | 存在 | 对齐 |
| user:edit | 存在 | 对齐 |
| user:delete | 存在 | 对齐 |
| user:password:reset | 缺失 | 需补充 |
| approval:view | 缺失 | 需补充 |
| approval:create | 缺失 | 需补充 |
| approval:process | 缺失 | 需补充 |
| log:view | 存在 | 对齐 |
| log:export | 缺失 | 需补充 |
| system:setting | 缺失 | 需补充 |

需补充 10 个权限码到后端 init.sql

---

## 八、关键参考文件

| 文件 | 用途 |
|------|------|
| `backend/entity/User.java` | 用户实体，需添加 deptId |
| `backend/entity/SysRole.java` | 角色实体，已完善 |
| `backend/entity/SysPermission.java` | 权限实体，已完善 |
| `backend/service/SysRoleService.java` | 角色服务，需增强权限分配 |
| `frontend/views/UserManagement.vue` | 用户管理页面，需优化 |
| `frontend/composables/usePermission.ts` | 权限判断，需改为动态 |
| `backend/resources/init.sql` | 数据库初始化，需添加部门表 |

---

## 十、待完成工作

### 10.1 高优先级（P1）

| 任务 | 说明 | 预估时间 |
|------|------|---------|
| 用户多角色支持 | 前端角色选择改为多选，支持一个用户绑定多个角色 | 1天 |
| 部门负责人选择 | 表单中 leaderId 改为用户下拉选择组件 | 0.5天 |
| 权限配置页面 | 角色管理页面添加权限树状勾选功能 | 1天 |
| 部门树递归组件 | 当前硬编码3级，改为递归组件支持无限层级 | 1天 |

### 10.2 中优先级（P2）

| 任务 | 说明 | 预估时间 |
|------|------|---------|
| 权限缓存 | 用户权限缓存到 Redis，减少数据库查询 | 1天 |
| 数据权限 | 部门数据权限（用户只能看本部门数据） | 2天 |
| 角色继承 | 支持角色继承，减少配置工作量 | 1天 |

### 10.3 数据库补充 SQL

现有数据库需执行以下 SQL：

```sql
-- 添加部门类型字典
INSERT INTO sys_dict (category, dict_key, dict_value, extra_value, sort_order, status, remark, created_time, updated_time)
SELECT * FROM (
    SELECT 'dept_type' AS category, 'HEADQUARTERS' AS dict_key, '总部' AS dict_value, '#6366f1' AS extra_value, 1 AS sort_order, 'ACTIVE' AS status, '总部/集团' AS remark, NOW() AS created_time, NOW() AS updated_time
    UNION ALL SELECT 'dept_type', 'COMPANY', '子公司', '#f59e0b', 2, 'ACTIVE', '子公司/分公司', NOW(), NOW()
    UNION ALL SELECT 'dept_type', 'DEPARTMENT', '部门', '#10b981', 3, 'ACTIVE', '普通部门', NOW(), NOW()
) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM sys_dict WHERE category = 'dept_type');

-- 添加部门表
CREATE TABLE IF NOT EXISTS sys_department (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    parent_id BIGINT,
    dept_code VARCHAR(50) NOT NULL UNIQUE,
    dept_name VARCHAR(100) NOT NULL,
    dept_type VARCHAR(20) NOT NULL DEFAULT 'DEPARTMENT',
    leader_id BIGINT,
    sort_order INT DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    path VARCHAR(500),
    level INT DEFAULT 1,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_dept_parent (parent_id),
    INDEX idx_dept_code (dept_code),
    INDEX idx_dept_path (path)
);

-- 初始化总部部门
INSERT INTO sys_department (id, parent_id, dept_code, dept_name, dept_type, sort_order, status, path, level, created_time, updated_time)
SELECT 1, NULL, 'HQ', '总部', 'HEADQUARTERS', 1, 'ACTIVE', '1', 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_department WHERE id = 1);

-- 用户表添加部门ID
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS dept_id BIGINT;
ALTER TABLE sys_user ADD INDEX IF NOT EXISTS idx_user_dept (dept_id);

-- 角色表添加部门ID
ALTER TABLE sys_role ADD COLUMN IF NOT EXISTS dept_id BIGINT;

-- 补充缺失权限码
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, resource_url, sort_order, status, created_time, updated_time)
SELECT 'product:import', '产品导入', 'BUTTON', 2, NULL, 14, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'product:import');

INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, resource_url, sort_order, status, created_time, updated_time)
SELECT 'product:export', '产品导出', 'BUTTON', 2, NULL, 15, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'product:export');

-- ... 其他权限码类似
```

---

## 九、验收标准

1. ✅ 部门树状展示，支持多级展开/折叠
2. ✅ 部门拖拽调整层级
3. ✅ 用户创建时可选择所属部门
4. ✅ 用户可分配多个角色（已完成）
5. ✅ 角色可配置权限（树状勾选）（已完成）
6. ✅ 前端权限判断基于后端数据
7. ✅ 菜单根据权限动态显示/隐藏
8. ✅ 部门树递归组件支持无限层级（已完成）
9. ✅ 部门负责人选择功能（已完成）

## 十、实现完成记录（2026-05-17）

### 已完成功能

| 功能 | 说明 | 实现文件 |
|------|------|---------|
| 用户多角色支持 | 前端角色选择改为多选，支持一个用户绑定多个角色 | UserManagement.vue, roles.ts, UserController.java |
| 部门树递归组件 | 支持无限层级展开/折叠，拖拽功能保持 | DeptTreeNode.vue, DepartmentManagement.vue |
| 部门负责人选择 | 表单中leaderId改为用户下拉选择 | DepartmentManagement.vue |
| 角色权限配置页面 | RoleManagement.vue页面，权限树状勾选 | RoleManagement.vue, roles.ts |

### 新增文件

- `frontend/src/components/DeptTreeNode.vue` - 部门树递归组件
- `frontend/src/views/RoleManagement.vue` - 角色管理页面
- `frontend/src/api/roles.ts` - 角色API接口

### 修改文件

- `frontend/src/views/UserManagement.vue` - 添加多角色选择、角色分配模态框
- `frontend/src/views/DepartmentManagement.vue` - 使用递归组件、添加负责人选择
- `frontend/src/api/users.ts` - 添加getUserRoles、assignUserRoles接口
- `frontend/src/api/permissions.ts` - 规范API路径
- `frontend/src/types/index.ts` - 添加SysRole、SysPermission类型
- `frontend/src/router/index.ts` - 添加角色管理路由
- `backend/src/main/java/com/pricemanagement/controller/UserController.java` - 添加getUserRoles接口
- `backend/src/main/java/com/pricemanagement/service/PermissionService.java` - 添加getUserRoleIds方法
