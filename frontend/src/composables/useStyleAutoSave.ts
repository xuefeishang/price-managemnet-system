/**
 * 样式自动保存
 * 实现 debounce 保存、失败回滚、保存状态管理
 */

import { ref, onUnmounted } from 'vue'
import type { StyleConfig } from '@/types/theme'
import { updateStyleConfig } from '@/api/style'

export interface AutoSaveOptions {
  debounceMs?: number      // 防抖延迟（毫秒）
  onSuccess?: () => void   // 保存成功回调
  onError?: (error: Error) => void  // 保存失败回调
}

/**
 * 创建自动保存器
 */
export function useStyleAutoSave(options: AutoSaveOptions = {}) {
  const {
    debounceMs = 500,
    onSuccess,
    onError
  } = options

  const saveStatus = ref<'idle' | 'saving' | 'saved' | 'failed'>('idle')
  const lastSavedAt = ref<Date | null>(null)
  const lastError = ref<string | null>(null)

  // 待处理的保存任务
  let pendingSave: ReturnType<typeof setTimeout> | null = null
  let lastConfig: Partial<StyleConfig> | null = null

  /**
   * 取消待处理的保存
   */
  const cancelPending = () => {
    if (pendingSave) {
      clearTimeout(pendingSave)
      pendingSave = null
    }
  }

  /**
   * 执行保存
   */
  const doSave = async (config: Partial<StyleConfig>) => {
    saveStatus.value = 'saving'

    try {
      await updateStyleConfig(config)
      saveStatus.value = 'saved'
      lastSavedAt.value = new Date()
      lastError.value = null
      onSuccess?.()
    } catch (error) {
      saveStatus.value = 'failed'
      lastError.value = error instanceof Error ? error.message : '保存失败'
      onError?.(error instanceof Error ? error : new Error(String(error)))
    }
  }

  /**
   * 触发保存（带防抖）
   */
  const triggerSave = (config: Partial<StyleConfig>) => {
    cancelPending()
    lastConfig = config
    saveStatus.value = 'idle'

    pendingSave = setTimeout(() => {
      if (lastConfig) {
        doSave(lastConfig)
      }
    }, debounceMs)
  }

  /**
   * 立即保存（跳过防抖）
   */
  const saveImmediately = async (config: Partial<StyleConfig>) => {
    cancelPending()
    await doSave(config)
  }

  /**
   * 清理
   */
  onUnmounted(() => {
    cancelPending()
  })

  return {
    saveStatus,
    lastSavedAt,
    lastError,
    triggerSave,
    saveImmediately,
    cancelPending
  }
}

/**
 * 创建带版本追踪的自动保存器
 */
export function useVersionedAutoSave(options: AutoSaveOptions = {}) {
  const autoSave = useStyleAutoSave(options)
  const versionCount = ref(0)

  return {
    ...autoSave,
    versionCount,
    triggerSave: (config: Partial<StyleConfig>) => {
      autoSave.triggerSave(config)
      versionCount.value++
    }
  }
}

/**
 * Debounce 工具函数
 */
export function debounce<T extends (...args: any[]) => any>(
  fn: T,
  delay: number
): (...args: Parameters<T>) => void {
  let timeoutId: ReturnType<typeof setTimeout> | null = null

  return (...args: Parameters<T>) => {
    if (timeoutId) {
      clearTimeout(timeoutId)
    }
    timeoutId = setTimeout(() => {
      fn(...args)
      timeoutId = null
    }, delay)
  }
}

/**
 * Throttle 工具函数
 */
export function throttle<T extends (...args: any[]) => any>(
  fn: T,
  limit: number
): (...args: Parameters<T>) => void {
  let inThrottle = false

  return (...args: Parameters<T>) => {
    if (!inThrottle) {
      fn(...args)
      inThrottle = true
      setTimeout(() => {
        inThrottle = false
      }, limit)
    }
  }
}
