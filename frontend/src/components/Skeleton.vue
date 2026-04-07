<script setup lang="ts">
interface Props {
  type?: 'card' | 'list' | 'table' | 'profile' | 'product'
  count?: number
}

withDefaults(defineProps<Props>(), {
  type: 'card',
  count: 1
})
</script>

<template>
  <div class="skeleton-container">
    <!-- 卡片骨架屏 -->
    <template v-if="type === 'card'">
      <div v-for="i in count" :key="i" class="skeleton-card">
        <div class="skeleton-image"></div>
        <div class="skeleton-content">
          <div class="skeleton-line title"></div>
          <div class="skeleton-line short"></div>
          <div class="skeleton-line medium"></div>
        </div>
      </div>
    </template>

    <!-- 列表骨架屏 -->
    <template v-else-if="type === 'list'">
      <div v-for="i in count" :key="i" class="skeleton-list-item">
        <div class="skeleton-avatar"></div>
        <div class="skeleton-list-content">
          <div class="skeleton-line title"></div>
          <div class="skeleton-line short"></div>
        </div>
      </div>
    </template>

    <!-- 表格骨架屏 -->
    <template v-else-if="type === 'table'">
      <div class="skeleton-table">
        <div class="skeleton-table-header">
          <div class="skeleton-line header-cell"></div>
          <div class="skeleton-line header-cell"></div>
          <div class="skeleton-line header-cell"></div>
          <div class="skeleton-line header-cell"></div>
        </div>
        <div v-for="i in count" :key="i" class="skeleton-table-row">
          <div class="skeleton-line cell"></div>
          <div class="skeleton-line cell"></div>
          <div class="skeleton-line cell"></div>
          <div class="skeleton-line cell"></div>
        </div>
      </div>
    </template>

    <!-- 个人信息骨架屏 -->
    <template v-else-if="type === 'profile'">
      <div class="skeleton-profile">
        <div class="skeleton-avatar-large"></div>
        <div class="skeleton-line title"></div>
        <div class="skeleton-line short"></div>
      </div>
    </template>

    <!-- 产品卡片骨架屏 -->
    <template v-else-if="type === 'product'">
      <div v-for="i in count" :key="i" class="skeleton-product">
        <div class="skeleton-product-image"></div>
        <div class="skeleton-product-info">
          <div class="skeleton-line title"></div>
          <div class="skeleton-line medium"></div>
          <div class="skeleton-line short"></div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.skeleton-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 骨架屏动画 */
.skeleton-line,
.skeleton-image,
.skeleton-avatar,
.skeleton-avatar-large,
.skeleton-product-image {
  background: linear-gradient(90deg, #f0f0f0 25%, #e0e0e0 50%, #f0f0f0 75%);
  background-size: 200% 100%;
  animation: skeleton-loading 1.5s ease-in-out infinite;
  border-radius: 4px;
}

@keyframes skeleton-loading {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

/* 卡片骨架屏 */
.skeleton-card {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 16px;
  border: 1px solid #E5E5E5;
}

.skeleton-image {
  width: 100%;
  height: 160px;
  border-radius: 8px;
  margin-bottom: 16px;
}

.skeleton-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.skeleton-line {
  height: 14px;
}

.skeleton-line.title {
  width: 70%;
  height: 18px;
}

.skeleton-line.medium {
  width: 90%;
}

.skeleton-line.short {
  width: 40%;
}

/* 列表骨架屏 */
.skeleton-list-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: #FFFFFF;
  border-radius: 8px;
}

.skeleton-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  flex-shrink: 0;
}

.skeleton-list-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* 表格骨架屏 */
.skeleton-table {
  background: #FFFFFF;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #E5E5E5;
}

.skeleton-table-header {
  display: flex;
  gap: 16px;
  padding: 12px 16px;
  background: #FAFAFA;
  border-bottom: 1px solid #E5E5E5;
}

.skeleton-table-header .skeleton-line {
  flex: 1;
  height: 14px;
}

.skeleton-table-row {
  display: flex;
  gap: 16px;
  padding: 16px;
  border-bottom: 1px solid #F5F5F5;
}

.skeleton-table-row:last-child {
  border-bottom: none;
}

.skeleton-line.cell {
  flex: 1;
  height: 14px;
}

/* 个人信息骨架屏 */
.skeleton-profile {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 32px;
  background: #FFFFFF;
  border-radius: 12px;
  border: 1px solid #E5E5E5;
}

.skeleton-avatar-large {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  margin-bottom: 16px;
}

/* 产品骨架屏 */
.skeleton-product {
  background: #FFFFFF;
  border-radius: 12px;
  padding: 16px;
  border: 1px solid #E5E5E5;
}

.skeleton-product-image {
  width: 100%;
  height: 120px;
  border-radius: 8px;
  margin-bottom: 12px;
}

.skeleton-product-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
</style>
