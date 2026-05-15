/**
 * 应用入口文件
 * 负责创建 Vue 应用实例、注册插件、初始化主题配置
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import 'vant/lib/index.css'
import './assets/style.scss'
import { useTheme } from '@/composables/useTheme'

// 创建 Vue 应用实例
const app = createApp(App)

// 注册 Pinia 状态管理
app.use(createPinia())
// 注册 Vue Router 路由
app.use(router)

// 挂载应用到 DOM
app.mount('#app')

// 初始化主题配置（从后端加载样式设置）
const { loadThemeConfig } = useTheme()
loadThemeConfig()
