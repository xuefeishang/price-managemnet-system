# MenuConfig 树形表格 + 拖拽功能实现

## Context
用户希望在 MenuConfig 页面实现：
1. **清晰的层级关系**：显示三级菜单结构，支持展开/折叠
2. **完整的拖拽功能**：支持同级别排序、跨级别移动
3. **响应式菜单联动**：MenuConfig 修改后，左侧侧边栏和顶部导航栏实时响应更新

## 已确认的设计决策

| 问题 | 选择 |
|------|------|
| 展开/折叠模式 | 非手风琴（多个父节点可同时展开） |
| 移动端拖拽 | 不支持，通过 sortOrder 字段调整 |
| 保存策略 | 实时保存 |
| 撤销功能 | 需要撤销按钮（撤销最近一次操作） |
| 菜单联动 | MenuConfig 修改后通过共享 Store 通知 Layout 实时更新 |

## 技术方案

### 组件选择
- **拖拽库**：`vue-draggable-plus` (Vue 3 兼容，支持嵌套拖拽)
- **表格**：自定义树形表格，保留现有 PC/移动端响应式布局
- **状态管理**：Pinia (`useMenuStore`) 共享菜单状态，实现跨组件联动

### 层级展示
- **非手风琴模式**：多个父节点可同时展开各自的子级，互不影响
- 点击一级菜单行展开/折叠其所有二级，点击二级行展开/折叠其所有三级
- **视觉层次**：
  - 一级菜单：白色背景，无缩进
  - 二级菜单：浅灰背景 `#F8FAFC`，左侧有连接线
  - 三级菜单：更浅灰背景 `#F1F5F9`，更大缩进
- **展开箭头位置**：`>` 符号放置在菜单名称文字之后（名称 → 级别标签 → 展开箭头）

### 拖拽交互设计

#### 三级拖拽规则

| 层级 | group 配置 | 允许操作 |
|------|-----------|----------|
| 一级菜单 | `top-level` | 同层排序 |
| 二级菜单 | `sub-level` | 同层排序 + 跨父级移动（拖到其他一级菜单下） |
| 三级菜单 | `leaf-level` | 同层排序 + 跨父级移动（拖到其他二级菜单下） |

#### 拖拽细节
1. **拖拽排序**：在同一父菜单下拖拽子菜单可排序
2. **跨级移动**：二级/三级菜单可拖到不同父级下，自动更新 `parentId`
3. **拖拽指示器**：拖拽时显示放置位置的高亮指示线和背景色
4. **仅 PC 端支持拖拽**，移动端通过 sortOrder 字段调整顺序
5. **撤销功能**：显示"撤销"按钮，撤销最近一次拖拽操作（仅撤销一次）
6. **拖拽时自动展开**：`onDragStart` 时调用 `initExpanded()` 展开所有折叠菜单，确保放置区域可见
7. **防抖保存**：拖拽结束后 200ms 防抖，遍历整棵树收集 `{ id, parentId, sortOrder }` 调用批量保存 API

#### 拖拽数据流
```
VueDraggable 拖拽 → v-model 自动更新 menus 数组结构
                   → onDragEnd → 200ms 防抖
                   → collectSortItems 递归遍历树收集排序数据
                   → batchUpdateMenuSort API 批量保存
                   → menuStore.notifyMenuChanged() 通知 Layout 更新
```

### 菜单响应式联动

#### 架构设计
```
MenuConfig 操作（增/删/改/排序/可见性切换）
    → API 保存成功
    → menuStore.notifyMenuChanged()
        → loadVisibleMenus() 重新加载 getVisibleMenus
        → version++ 触发响应式
        → Layout watch(version) → 更新侧边栏/顶部栏菜单
```

#### useMenuStore（Pinia Store）
- `visibleMenus`: `ref<MenuItem[]>` — Layout 侧边栏/顶部栏使用的菜单数据
- `version`: `ref<number>` — 版本号，每次菜单变更 +1，用于触发响应式更新
- `loadVisibleMenus()`: 加载可见菜单（根据当前用户角色）
- `notifyMenuChanged()`: 通知菜单已变更，供 MenuConfig 调用后触发 Layout 重载

#### Layout.vue 改动
- 菜单数据从本地 `ref` 改为 `computed(() => menuStore.visibleMenus)`
- 移除本地 `getVisibleMenus` API 调用
- 添加 `watch(() => menuStore.version)` 监听菜单数据变更，自动更新路由选中状态
- 修复菜单展开/折叠 toggle 逻辑（点击已展开菜单应收起，而非重新赋值）

#### MenuConfig.vue 联动触发点
| 操作 | 触发位置 |
|------|---------|
| 拖拽排序保存成功 | `onDragEnd` |
| 撤销操作成功 | `handleUndo` |
| 新增/编辑菜单 | `handleSave` |
| 删除菜单 | `handleDelete` |
| 切换可见性 | `toggleVisible` |

## 实现步骤

### 1. 安装依赖 ✅
```bash
npm install vue-draggable-plus
```

### 2. 重构数据结构 ✅
将菜单数据扁平化为可拖拽的树形结构：
- 每行包含 level 信息用于渲染样式
- 支持展开/折叠状态管理

### 3. 重构模板结构 ✅
```
一级行 (可点击展开)
  └─ 展开的二级列表 (VueDraggable v-show)
       └─ 三级列表 (VueDraggable v-if)
```

### 4. 集成 vue-draggable-plus ✅
- 使用 `<VueDraggable>` 包装可拖拽的列表
- 配置 `group` 属性实现同层排序和跨级移动
- **拖拽结束后实时调用 API 保存**（200ms 防抖 + 立即更新菜单顺序和父级）
- 拖拽时自动展开所有折叠菜单

### 5. 添加撤销功能 ✅
- 拖拽前 `deepCloneMenus` 深拷贝当前菜单状态
- 拖拽失败时自动回滚到 `previousMenus`
- 点击"撤销"按钮恢复上次状态并调用批量保存 API

### 6. 修复 Layout 菜单展开/折叠 ✅
- `selectParentMenu` 添加 toggle 逻辑：点击已展开菜单时收起，而非重新赋值
- 清理遗留的 `console.log` 调试代码

### 7. 创建 useMenuStore 实现菜单联动 ✅
- 新建 `store/useMenuStore.ts`（Pinia store）
- Layout.vue 接入 store，从 `menuStore.visibleMenus` 读取菜单
- MenuConfig.vue 在所有变更操作成功后调用 `menuStore.notifyMenuChanged()`

### 8. 关键 CSS ✅
- `.level-1`, `.level-2`, `.level-3` 控制缩进和背景
- `.children-section`, `.grandchildren-section` 子级容器样式
- `.drag-handle` 拖拽手柄样式
- `.sortable-ghost` 拖拽占位符样式（含跨父级高亮边框）
- `.sortable-chosen` / `.sortable-drag` 拖拽中样式

## 关键文件

| 文件 | 职责 |
|------|------|
| `frontend/src/views/MenuConfig.vue` | 主组件：树形表格、拖拽、CRUD、撤销 |
| `frontend/src/api/menu.ts` | 菜单 API（getAllMenus、batchUpdateMenuSort 等） |
| `frontend/src/store/useMenuStore.ts` | Pinia 共享菜单状态，实现 Layout 与 MenuConfig 联动 |
| `frontend/src/components/Layout.vue` | 侧边栏/顶部栏导航，接入 menuStore |
| `backend/src/main/java/com/pricemanagement/dto/MenuSortDTO.java` | 批量排序 DTO |

## 验证方式
1. 刷新页面，展开/折叠功能正常（点击已展开菜单可收起）
2. 拖拽同一级别菜单，顺序变化且实时保存
3. 拖拽二级菜单到其他一级菜单下，父级改变
4. 拖拽三级菜单到其他二级菜单下，父级改变
5. 点击"撤销"按钮，菜单恢复到拖拽前的状态
6. 拖拽后刷新页面，数据持久化正确
7. 在 MenuConfig 中新增/删除/编辑/排序/切换可见性后，左侧侧边栏和顶部导航栏实时更新
8. 移动端不显示拖拽功能，通过 sortOrder 字段调整顺序
