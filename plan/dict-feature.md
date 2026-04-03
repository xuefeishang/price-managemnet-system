# 计划：产品编辑页面添加产地和客户多选功能

## Context
用户需要在产品编辑页面（ProductEdit.vue）中添加"产地"和"客户"字段，支持多选。这两个字段需要能够独立维护（启用/停用），因此需要创建统一的数据管理机制。

## 实现方案

### 一、数据库设计

**新增表：**
- `origin` - 产地表（id, name, code, sort_order, status, remark, created_time, updated_time）
- `customer` - 客户表（id, name, code, contact, phone, address, sort_order, status, remark, created_time, updated_time）

**产品表变更：**
- 添加 `origin_ids` VARCHAR(500) - 产地ID列表（JSON数组，如 `["1","2","3"]`）
- 添加 `customer_ids` VARCHAR(500) - 客户ID列表（JSON数组）

**存储模式：** 与 MenuItem.roles 字段一致，使用 VARCHAR(500) 存储 JSON 数组

---

### 二、后端实现

**新增文件（14个）：**

| 文件 | 路径 |
|------|------|
| Origin.java | `backend/.../entity/Origin.java` |
| Customer.java | `backend/.../entity/Customer.java` |
| OriginRepository.java | `backend/.../repository/OriginRepository.java` |
| CustomerRepository.java | `backend/.../repository/CustomerRepository.java` |
| OriginService.java | `backend/.../service/OriginService.java` |
| CustomerService.java | `backend/.../service/CustomerService.java` |
| OriginController.java | `backend/.../controller/OriginController.java` |
| CustomerController.java | `backend/.../controller/CustomerController.java` |

**修改文件（3个）：**

| 文件 | 修改内容 |
|------|---------|
| Product.java | 添加 originIds, customerIds 字段 |
| ProductService.java | 处理 JSON 序列化/反序列化 |
| init.sql | 添加 origin/customer 表创建语句 |

**JSON 处理参考（MenuItemService.java:75-78）：**
```java
// 读取时反序列化
List<String> roles = objectMapper.readValue(menu.getRoles(), new TypeReference<List<String>>() {});
// 保存时序列化
menu.setRoles(objectMapper.writeValueAsString(dto.getRoles()));
```

---

### 三、前端实现

**新增文件（4个）：**

| 文件 | 路径 |
|------|------|
| Origins.vue | `frontend/src/views/Origins.vue` |
| OriginEdit.vue | `frontend/src/views/OriginEdit.vue` |
| Customers.vue | `frontend/src/views/Customers.vue` |
| CustomerEdit.vue | `frontend/src/views/CustomerEdit.vue` |

**API 模块（2个）：**
- `frontend/src/api/origins.ts`
- `frontend/src/api/customers.ts`

**类型定义（frontend/src/types/index.ts）：**
- 添加 `Origin` 接口
- 添加 `Customer` 接口

**修改文件（1个）：**
- `ProductEdit.vue` - 添加产地/客户多选组件

---

### 四、ProductEdit.vue 多选组件实现

参考 MenuConfig.vue 中 roles 多选模式（lines 404-412 使用原生 checkbox）：

```vue
<!-- 产地多选 -->
<div class="form-group">
  <label class="form-label">产地</label>
  <div class="multi-select-box">
    <van-checkbox-group v-model="selectedOriginIds" direction="horizontal">
      <van-checkbox v-for="origin in origins" :key="origin.id" :name="origin.id" shape="square">
        {{ origin.name }}
      </van-checkbox>
    </van-checkbox-group>
  </div>
</div>
```

保存时序列化：
```typescript
originIds: JSON.stringify(selectedOriginIds.value)
customerIds: JSON.stringify(selectedCustomerIds.value)
```

---

### 五、菜单配置

在 `MenuItemService.java` 的 `initializeDefaultMenus()` 方法中添加：

```java
// 字典管理子菜单（在基础运维下新增）
MenuItem dictMgmt = createMenuItem(basicMgmt, "字典管理", null, "dict", 5, true, "[\"ADMIN\",\"EDITOR\"]");
createMenuItem(dictMgmt, "产地管理", "/origins", null, 1, true, "[\"ADMIN\",\"EDITOR\"]");
createMenuItem(dictMgmt, "客户管理", "/customers", null, 2, true, "[\"ADMIN\",\"EDITOR\"]");
```

**路由配置（frontend/src/router/index.ts）：**
```typescript
{ path: 'origins', name: 'Origins', component: () => import('../views/Origins.vue') },
{ path: 'origin-edit/:id?', name: 'OriginEdit', component: () => import('../views/OriginEdit.vue') },
{ path: 'customers', name: 'Customers', component: () => import('../views/Customers.vue') },
{ path: 'customer-edit/:id?', name: 'CustomerEdit', component: () => import('../views/CustomerEdit.vue') },
```

---

### 六、关键参考文件

| 文件 | 用途 |
|------|------|
| `frontend/src/views/Categories.vue` | Origins/Customers 列表页模板 |
| `frontend/src/views/CategoryEdit.vue` | OriginEdit/CustomerEdit 编辑页模板 |
| `frontend/src/views/MenuConfig.vue` | roles 多选模式参考 |
| `backend/.../service/MenuItemService.java` | JSON 序列化/反序列化参考 |
| `backend/.../entity/ProductCategory.java` | Origin/Customer 实体参考 |

---

### 七、实现步骤

1. **数据库** - 创建 origin/customer 表，修改 product 表
2. **后端实体** - 创建 Origin.java, Customer.java
3. **后端Repository** - 创建 OriginRepository.java, CustomerRepository.java
4. **后端Service** - 创建 OriginService.java, CustomerService.java
5. **后端Controller** - 创建 OriginController.java, CustomerController.java
6. **后端Product修改** - Product.java 添加字段，ProductService.java 处理序列化
7. **前端API** - 创建 origins.ts, customers.ts
8. **前端类型** - types/index.ts 添加 Origin, Customer 接口
9. **前端页面** - 创建 Origins.vue, OriginEdit.vue, Customers.vue, CustomerEdit.vue
10. **ProductEdit.vue** - 添加产地/客户多选组件
11. **菜单配置** - MenuItemService.java 添加菜单项
12. **路由配置** - router/index.ts 添加路由
13. **文档更新** - 更新 init.sql（数据初始化内容）

---

### 八、验证方式

1. 启动后端，访问 `/api/origins` 和 `/api/customers` 验证 CRUD
2. 启动前端，访问产地管理和客户管理页面
3. 编辑产品，确认产地/客户多选组件正常工作
4. 保存产品后重新打开，确认产地/客户数据正确回显
