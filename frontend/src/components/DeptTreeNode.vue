<script setup lang="ts">
import { computed } from 'vue'
import { getDeptTypeLabel } from '@/composables/useDict'
import type { Department } from '@/types'

const props = defineProps<{
  dept: Department
  level: number
  selectedId: number | null
  expandedIds: Set<number>
  dragOverId: number | null
  hasPermission: boolean
}>()

const emit = defineEmits<{
  (e: 'select', dept: Department): void
  (e: 'toggle-expand', dept: Department): void
  (e: 'create', parentDept: Department): void
  (e: 'edit', dept: Department): void
  (e: 'delete', dept: Department): void
  (e: 'drag-start', dept: Department): void
  (e: 'drag-over', event: DragEvent, dept: Department): void
  (e: 'drop', event: DragEvent, dept: Department): void
}>()

const isExpanded = computed(() => props.expandedIds.has(props.dept.id))
const isSelected = computed(() => props.selectedId === props.dept.id)
const isDragOver = computed(() => props.dragOverId === props.dept.id)
const hasChildren = computed(() => props.dept.children && props.dept.children.length > 0)

const getDeptTypeClass = (type: string) => {
  const map: Record<string, string> = {
    HEADQUARTERS: 'hq',
    COMPANY: 'company',
    DEPARTMENT: 'dept'
  }
  return map[type] || 'dept'
}

const handleSelect = () => emit('select', props.dept)
const handleToggleExpand = () => emit('toggle-expand', props.dept)
const handleCreate = () => emit('create', props.dept)
const handleEdit = () => emit('edit', props.dept)
const handleDelete = () => emit('delete', props.dept)
const handleDragStart = () => emit('drag-start', props.dept)
const handleDragOver = (e: DragEvent) => emit('drag-over', e, props.dept)
const handleDrop = (e: DragEvent) => emit('drop', e, props.dept)
</script>

<template>
  <div class="dept-tree-node-wrapper">
    <div
      class="tree-node"
      :class="{
        selected: isSelected,
        expanded: isExpanded,
        'drag-over': isDragOver
      }"
      :style="{ paddingLeft: `${level * 24 + 12}px` }"
      draggable="true"
      @click="handleSelect"
      @dragstart="handleDragStart"
      @dragover="handleDragOver"
      @drop="handleDrop"
    >
      <div class="node-content">
        <span class="expand-icon" @click.stop="handleToggleExpand">
          <template v-if="hasChildren">
            {{ isExpanded ? '▼' : '▶' }}
          </template>
          <template v-else>•</template>
        </span>
        <span class="dept-type-badge" :class="getDeptTypeClass(dept.deptType)">
          {{ getDeptTypeLabel(dept.deptType) }}
        </span>
        <span class="dept-name">{{ dept.deptName }}</span>
        <span class="dept-code">({{ dept.deptCode }})</span>
        <span v-if="dept.userCount" class="user-count">{{ dept.userCount }}人</span>
      </div>
      <div class="node-actions" v-if="hasPermission">
        <button class="action-btn add" @click.stop="handleCreate" title="添加子部门">+</button>
        <button class="action-btn edit" @click.stop="handleEdit" title="编辑">✎</button>
        <button class="action-btn delete" @click.stop="handleDelete" title="删除">×</button>
      </div>
    </div>

    <!-- 递归渲染子节点 -->
    <template v-if="hasChildren && isExpanded">
      <DeptTreeNode
        v-for="child in dept.children"
        :key="child.id"
        :dept="child"
        :level="level + 1"
        :selected-id="selectedId"
        :expanded-ids="expandedIds"
        :drag-over-id="dragOverId"
        :has-permission="hasPermission"
        @select="(d) => emit('select', d)"
        @toggle-expand="(d) => emit('toggle-expand', d)"
        @create="(d) => emit('create', d)"
        @edit="(d) => emit('edit', d)"
        @delete="(d) => emit('delete', d)"
        @drag-start="(d) => emit('drag-start', d)"
        @drag-over="(e, d) => emit('drag-over', e, d)"
        @drop="(e, d) => emit('drop', e, d)"
      />
    </template>
  </div>
</template>

<style scoped>
.tree-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.75rem 1rem;
  border-radius: var(--radius);
  cursor: pointer;
  transition: all var(--transition-fast);
  border: 1px solid transparent;
}

.tree-node:hover {
  background: var(--gray-50);
}

.tree-node.selected {
  background: rgba(99, 102, 241, 0.1);
  border-color: var(--primary-color);
}

.tree-node.drag-over {
  background: rgba(99, 102, 241, 0.2);
  border-color: var(--primary-color);
  border-style: dashed;
}

.node-content {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.expand-icon {
  width: 16px;
  text-align: center;
  color: var(--gray-400);
  cursor: pointer;
}

.dept-type-badge {
  padding: 0.125rem 0.5rem;
  border-radius: 9999px;
  font-size: 0.625rem;
  font-weight: 600;
}

.dept-type-badge.hq {
  background: rgba(99, 102, 241, 0.15);
  color: var(--primary-color);
}

.dept-type-badge.company {
  background: rgba(245, 158, 11, 0.15);
  color: var(--warning-color);
}

.dept-type-badge.dept {
  background: rgba(16, 185, 129, 0.15);
  color: var(--success-color);
}

.dept-name {
  font-weight: 600;
  color: var(--gray-900);
}

.dept-code {
  font-size: 0.75rem;
  color: var(--gray-500);
}

.user-count {
  font-size: 0.75rem;
  color: var(--gray-400);
  margin-left: 0.5rem;
}

.node-actions {
  display: flex;
  gap: 0.25rem;
  opacity: 0;
  transition: opacity var(--transition-fast);
}

.tree-node:hover .node-actions {
  opacity: 1;
}

.action-btn {
  width: 24px;
  height: 24px;
  border: none;
  border-radius: var(--radius);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.875rem;
  transition: all var(--transition-fast);
}

.action-btn.add {
  background: rgba(16, 185, 129, 0.1);
  color: var(--success-color);
}

.action-btn.edit {
  background: rgba(99, 102, 241, 0.1);
  color: var(--primary-color);
}

.action-btn.delete {
  background: rgba(239, 68, 68, 0.1);
  color: var(--error-color);
}

.action-btn:hover {
  transform: scale(1.1);
}
</style>
