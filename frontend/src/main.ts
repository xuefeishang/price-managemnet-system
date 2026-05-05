
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import 'vant/lib/index.css'
import './assets/style.scss'
import { useTheme } from '@/composables/useTheme'

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.mount('#app')

// 初始化主题配置
const { loadThemeConfig } = useTheme()
loadThemeConfig()
