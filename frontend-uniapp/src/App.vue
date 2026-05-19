<script setup lang="ts">
import { onLaunch, onShow, onHide } from '@dcloudio/uni-app'
import { createPinia } from 'pinia'

onLaunch(() => {
  console.log('App Launch')
})

onShow(() => {
  console.log('App Show')
})

onHide(() => {
  console.log('App Hide')
})
</script>

<style>
/* 全局样式 */
page {
  background-color: #F5F5F5;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

/* 主题色 */
:root {
  --primary-color: #0D6E6E;
  --primary-light: #0D8A8A;
  --text-color: #1A1A1A;
  --text-secondary: #666666;
  --border-color: #E5E5E5;
  --bg-color: #F5F5F5;
}
</style>