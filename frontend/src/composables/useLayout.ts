import { ref, computed, onMounted, onUnmounted } from 'vue'

const windowWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1024)

const updateWindowWidth = () => {
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