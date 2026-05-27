import { ref, computed, onMounted, onUnmounted } from 'vue'

const windowWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1024)

const isWindowMinimized = () => {
  if (typeof window === 'undefined' || typeof document === 'undefined') return false
  return document.visibilityState === 'hidden' ||
    window.outerWidth <= 200 ||
    window.outerHeight <= 100 ||
    window.screenX <= -30000 ||
    window.screenY <= -30000
}

const updateWindowWidth = () => {
  if (isWindowMinimized()) return
  windowWidth.value = window.innerWidth
}

export function useLayout() {
  const isPCLayout = computed(() => windowWidth.value >= 1024)

  onMounted(() => {
    window.addEventListener('resize', updateWindowWidth)
  })

  onUnmounted(() => {
    window.removeEventListener('resize', updateWindowWidth)
  })

  return {
    windowWidth,
    isPCLayout
  }
}
