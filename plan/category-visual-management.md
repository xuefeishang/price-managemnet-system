# 分类视觉配置管理界面 - 方案二

**目标：为分类视觉配置提供可视化管理界面**

---

## 一、背景

当前方案一已通过字典数据实现分类视觉配置，但存在以下限制：
- JSON配置不直观，需手动编辑数据库
- 无颜色选择器，易出错
- 图标仅限已定义的6种内置SVG

方案二将提供独立配置表和管理界面，解决上述问题。

---

## 二、数据库设计

### 2.1 新建配置表

```sql
CREATE TABLE IF NOT EXISTS category_visual (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '配置ID',
    category_id BIGINT NOT NULL COMMENT '关联产品分类ID',
    category_code VARCHAR(50) NOT NULL COMMENT '分类编码',
    primary_color VARCHAR(20) NOT NULL COMMENT '主色调',
    secondary_color VARCHAR(20) COMMENT '辅助色',
    text_color VARCHAR(20) COMMENT '文字颜色',
    border_color VARCHAR(20) COMMENT '边框颜色',
    glow_color VARCHAR(50) COMMENT '发光颜色(rgba)',
    icon_name VARCHAR(50) NOT NULL COMMENT 'SVG图标名称',
    icon_svg TEXT COMMENT '自定义SVG代码（可选）',
    dark_primary_color VARCHAR(20) COMMENT '深色模式主色',
    dark_text_color VARCHAR(20) COMMENT '深色模式文字色',
    dark_border_color VARCHAR(20) COMMENT '深色模式边框色',
    dark_glow_color VARCHAR(50) COMMENT '深色模式发光色',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_category_id (category_id),
    UNIQUE KEY uk_category_code (category_code),
    FOREIGN KEY (category_id) REFERENCES product_category(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分类视觉配置表';
```

### 2.2 数据迁移

从 `sys_dict` 迁移现有配置到新表：

```sql
INSERT INTO category_visual (category_id, category_code, primary_color, secondary_color, text_color, border_color, glow_color, icon_name, status)
SELECT 
    pc.id,
    pc.code,
    JSON_EXTRACT(sd.extra_value, '$.primaryColor'),
    JSON_EXTRACT(sd.extra_value, '$.secondaryColor'),
    JSON_EXTRACT(sd.extra_value, '$.textColor'),
    JSON_EXTRACT(sd.extra_value, '$.borderColor'),
    JSON_EXTRACT(sd.extra_value, '$.glowColor'),
    JSON_EXTRACT(sd.extra_value, '$.icon'),
    'ACTIVE'
FROM sys_dict sd
JOIN product_category pc ON pc.code = sd.dict_key
WHERE sd.category = 'category_visual_config';
```

---

## 三、后端实现

### 3.1 Entity

```java
// backend/src/main/java/com/pricemanagement/entity/CategoryVisual.java
@Entity
@Table(name = "category_visual")
public class CategoryVisual {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_id", nullable = false, unique = true)
    private Long categoryId;

    @Column(name = "category_code", nullable = false, unique = true, length = 50)
    private String categoryCode;

    @Column(name = "primary_color", nullable = false, length = 20)
    private String primaryColor;

    @Column(name = "secondary_color", length = 20)
    private String secondaryColor;

    @Column(name = "text_color", length = 20)
    private String textColor;

    @Column(name = "border_color", length = 20)
    private String borderColor;

    @Column(name = "glow_color", length = 50)
    private String glowColor;

    @Column(name = "icon_name", nullable = false, length = 50)
    private String iconName;

    @Column(name = "icon_svg", columnDefinition = "TEXT")
    private String iconSvg;

    // 深色模式字段...
    
    @Column(name = "status", length = 20)
    private String status = "ACTIVE";

    @CreatedTime
    private LocalDateTime createdTime;

    @UpdatedTime
    private LocalDateTime updatedTime;
}
```

### 3.2 DTO

```java
// backend/src/main/java/com/pricemanagement/dto/CategoryVisualDTO.java
public class CategoryVisualDTO {
    private Long id;
    private Long categoryId;
    private String categoryCode;
    private String categoryName;  // 关联查询分类名称
    
    private String primaryColor;
    private String secondaryColor;
    private String textColor;
    private String borderColor;
    private String glowColor;
    
    private String iconName;
    private String iconSvg;
    
    // 深色模式配置
    private DarkModeConfig darkMode;
    
    private String status;
    
    public static class DarkModeConfig {
        private String primaryColor;
        private String textColor;
        private String borderColor;
        private String glowColor;
    }
}
```

### 3.3 Controller

```java
// backend/src/main/java/com/pricemanagement/controller/CategoryVisualController.java
@RestController
@RequestMapping("/api/category-visual")
public class CategoryVisualController {
    
    @GetMapping
    public Result<List<CategoryVisualDTO>> getAll() { ... }
    
    @GetMapping("/{categoryId}")
    public Result<CategoryVisualDTO> getByCategoryId(@PathVariable Long categoryId) { ... }
    
    @PostMapping
    @OperationLog(type = "CREATE", module = "分类视觉配置")
    public Result<CategoryVisualDTO> create(@RequestBody CategoryVisualDTO dto) { ... }
    
    @PutMapping("/{id}")
    @OperationLog(type = "UPDATE", module = "分类视觉配置")
    public Result<CategoryVisualDTO> update(@PathVariable Long id, @RequestBody CategoryVisualDTO dto) { ... }
    
    @DeleteMapping("/{id}")
    @OperationLog(type = "DELETE", module = "分类视觉配置")
    public Result<Void> delete(@PathVariable Long id) { ... }
}
```

---

## 四、前端实现

### 4.1 API

```typescript
// frontend/src/api/categoryVisual.ts
import request from '@/utils/request'
import type { CategoryVisualDTO } from '@/types'

export const getCategoryVisuals = () => 
    request.get<CategoryVisualDTO[]>('/api/category-visual')

export const getCategoryVisualByCategoryId = (categoryId: number) =>
    request.get<CategoryVisualDTO>(`/api/category-visual/${categoryId}`)

export const createCategoryVisual = (data: CategoryVisualDTO) =>
    request.post<CategoryVisualDTO>('/api/category-visual', data)

export const updateCategoryVisual = (id: number, data: CategoryVisualDTO) =>
    request.put<CategoryVisualDTO>(`/api/category-visual/${id}`, data)

export const deleteCategoryVisual = (id: number) =>
    request.delete(`/api/category-visual/${id}`)
```

### 4.2 管理页面

```vue
<!-- frontend/src/views/CategoryVisualManagement.vue -->
<template>
  <div class="category-visual-page">
    <header class="page-header">
      <h1>分类视觉配置</h1>
      <button class="btn-primary" @click="showAddModal">新增配置</button>
    </header>

    <!-- 配置列表 -->
    <div class="config-list">
      <div v-for="config in configs" :key="config.id" class="config-card">
        <div class="card-preview" :style="getPreviewStyle(config)">
          <CategoryIcons :icon="config.iconName" :color="config.primaryColor" />
          <span class="preview-name" :style="{ color: config.primaryColor }">
            {{ config.categoryName }}
          </span>
        </div>
        
        <div class="card-info">
          <div class="color-row">
            <label>主色</label>
            <div class="color-preview" :style="{ background: config.primaryColor }"></div>
            <span>{{ config.primaryColor }}</span>
          </div>
          <div class="color-row">
            <label>辅助色</label>
            <div class="color-preview" :style="{ background: config.secondaryColor }"></div>
            <span>{{ config.secondaryColor }}</span>
          </div>
        </div>
        
        <div class="card-actions">
          <button @click="editConfig(config)">编辑</button>
          <button @click="deleteConfig(config.id)">删除</button>
        </div>
      </div>
    </div>

    <!-- 编辑弹窗 -->
    <Modal v-if="showModal" @close="showModal = false">
      <div class="edit-form">
        <div class="form-row">
          <label>分类</label>
          <select v-model="editingConfig.categoryId">
            <option v-for="cat in categories" :key="cat.id" :value="cat.id">
              {{ cat.name }}
            </option>
          </select>
        </div>
        
        <div class="form-row">
          <label>主色调</label>
          <input type="color" v-model="editingConfig.primaryColor" />
          <input type="text" v-model="editingConfig.primaryColor" />
        </div>
        
        <div class="form-row">
          <label>辅助色</label>
          <input type="color" v-model="editingConfig.secondaryColor" />
          <input type="text" v-model="editingConfig.secondaryColor" />
        </div>
        
        <div class="form-row">
          <label>文字色</label>
          <input type="color" v-model="editingConfig.textColor" />
        </div>
        
        <div class="form-row">
          <label>边框色</label>
          <input type="color" v-model="editingConfig.borderColor" />
        </div>
        
        <div class="form-row">
          <label>图标</label>
          <div class="icon-selector">
            <div v-for="icon in availableIcons" :key="icon"
                 class="icon-option"
                 :class="{ selected: editingConfig.iconName === icon }"
                 @click="editingConfig.iconName = icon">
              <CategoryIcons :icon="icon" :size="32" />
            </div>
          </div>
        </div>
        
        <!-- 深色模式配置 -->
        <div class="form-section">
          <h3>深色模式</h3>
          <div class="form-row">
            <label>主色</label>
            <input type="color" v-model="editingConfig.darkMode.primaryColor" />
          </div>
          <div class="form-row">
            <label>文字色</label>
            <input type="color" v-model="editingConfig.darkMode.textColor" />
          </div>
        </div>
        
        <!-- 实时预览 -->
        <div class="preview-section">
          <h3>预览效果</h3>
          <div class="preview-card" :style="getPreviewStyle(editingConfig)">
            <CategoryIcons :icon="editingConfig.iconName" :color="editingConfig.primaryColor" />
            <span :style="{ color: editingConfig.primaryColor }">产品名称示例</span>
          </div>
        </div>
        
        <div class="form-actions">
          <button class="btn-primary" @click="saveConfig">保存</button>
          <button @click="showModal = false">取消</button>
        </div>
      </div>
    </Modal>
  </div>
</template>
```

### 4.3 修改 useCategoryVisual

```typescript
// frontend/src/composables/useCategoryVisual.ts
// 新增：从API获取配置（替代字典）

import { getCategoryVisuals } from '@/api/categoryVisual'

const apiCache = new Map<number, CategoryVisualConfig>()

export const loadCategoryVisualsFromAPI = async () => {
  const res = await getCategoryVisuals()
  res.data.forEach(config => {
    apiCache.set(config.categoryId, {
      categoryCode: config.categoryCode,
      primaryColor: config.primaryColor,
      secondaryColor: config.secondaryColor,
      textColor: config.textColor,
      borderColor: config.borderColor,
      glowColor: config.glowColor,
      icon: config.iconName,
      iconType: 'builtin',
      darkMode: config.darkMode
    })
  })
}

// 修改 getCategoryVisual：优先从API缓存获取
export const getCategoryVisual = (categoryId: number | undefined, isDarkMode = false) => {
  if (!categoryId) return applyDarkMode(DEFAULT_VISUAL, isDarkMode)
  
  // 优先从API缓存
  if (apiCache.has(categoryId)) {
    return applyDarkMode(apiCache.get(categoryId)!, isDarkMode)
  }
  
  // 降级到字典缓存
  const dictConfig = parseCategoryVisualConfig(categoryId)
  if (dictConfig) {
    return applyDarkMode(dictConfig, isDarkMode)
  }
  
  return applyDarkMode(DEFAULT_VISUAL, isDarkMode)
}
```

---

## 五、图标扩展

### 5.1 新增图标

在 `CategoryIcons.vue` 中新增更多SVG图标：

```vue
<!-- 化工产品 -->
<svg v-else-if="icon === 'chemical'" ...>
  <!-- 烧瓶图标 -->
</svg>

<!-- 煤炭 -->
<svg v-else-if="icon === 'coal'" ...>
  <!-- 煤块图标 -->
</svg>

<!-- 铝材 -->
<svg v-else-if="icon === 'aluminum'" ...>
  <!-- 铝型材图标 -->
</svg>
```

### 5.2 Lucide图标集成

```vue
<script setup>
import { 
  Anvil, Hexagon, Gem, FlaskConical, Flame, Mountain, 
  Coins, Diamond, Atom, Cube 
} from 'lucide-vue-next'

const lucideIcons = {
  anvil: Anvil,
  hexagon: Hexagon,
  gem: Gem,
  flask: FlaskConical,
  flame: Flame,
  mountain: Mountain,
  coins: Coins,
  diamond: Diamond,
  atom: Atom,
  cube: Cube
}
</script>

<template>
  <!-- Lucide图标 -->
  <component v-if="lucideIcons[icon]" 
             :is="lucideIcons[icon]" 
             :size="iconSize" 
             :color="iconColor" />
  
  <!-- 内置SVG图标 -->
  <svg v-else-if="icon === 'gold_ingot'" ...>
</template>
```

---

## 六、菜单配置

在系统管理菜单下新增入口：

```sql
INSERT INTO sys_menu (parent_id, menu_code, menu_name, menu_path, menu_icon, sort_order, status)
VALUES (系统管理ID, 'category_visual', '分类视觉配置', '/category-visual', 'palette', 50, 'ACTIVE');
```

---

## 七、实施步骤

### 阶段一：数据库（30分钟）
1. 创建 `category_visual` 表
2. 迁移现有字典数据
3. 测试数据完整性

### 阶段二：后端（1小时）
1. 创建 Entity/DTO
2. 创建 Repository/Service
3. 创建 Controller
4. 测试API

### 阶段三：前端管理界面（1.5小时）
1. 创建 API 接口
2. 创建管理页面
3. 集成颜色选择器
4. 集成图标选择器
5. 实现实时预览

### 阶段四：前端集成（30分钟）
1. 修改 `useCategoryVisual.ts`
2. 修改 `Home.vue` 调用逻辑
3. 测试颜色显示

### 阶段五：测试验证（30分钟）
1. 功能测试
2. 深色模式测试
3. 响应式测试

**总工时：约3.5小时**

---

## 八、验收标准

| 功能 | 验收标准 |
|------|----------|
| 配置列表 | 显示所有分类视觉配置 |
| 新增配置 | 选择分类、颜色、图标后保存成功 |
| 编辑配置 | 颜色选择器实时预览，保存后立即生效 |
| 删除配置 | 删除后Home页面显示默认颜色 |
| 深色模式 | 切换深色模式后颜色正确变化 |
| 图标显示 | 内置SVG和Lucide图标正确渲染 |

---

## 九、风险与降级

### 风险
- 数据迁移可能遗漏配置
- API调用失败时颜色不显示

### 降级方案
- API失败时自动降级到字典缓存
- 保留字典配置作为备份

---

*计划创建日期：2026-05-19*