import { computed, onMounted, onUnmounted, ref } from 'vue'

const isChartAutoresizeEnabled = ref(true)

const isMinimizedLikeWindow = () => {
  if (typeof window === 'undefined' || typeof document === 'undefined') return false
  return document.visibilityState === 'hidden' ||
    window.outerWidth <= 200 ||
    window.outerHeight <= 100 ||
    window.screenX <= -30000 ||
    window.screenY <= -30000
}

const updateChartAutoresizeState = () => {
  isChartAutoresizeEnabled.value = !isMinimizedLikeWindow()
}

export function useSafeChartAutoresize() {
  onMounted(() => {
    updateChartAutoresizeState()
    window.addEventListener('resize', updateChartAutoresizeState, true)
    document.addEventListener('visibilitychange', updateChartAutoresizeState, true)
  })

  onUnmounted(() => {
    window.removeEventListener('resize', updateChartAutoresizeState, true)
    document.removeEventListener('visibilitychange', updateChartAutoresizeState, true)
  })

  return {
    chartAutoresize: computed(() => isChartAutoresizeEnabled.value)
  }
}
