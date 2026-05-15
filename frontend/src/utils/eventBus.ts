/**
 * 事件总线工具
 * 提供组件间通信的发布-订阅模式实现
 *
 * 使用示例：
 * // 订阅事件
 * const unsubscribe = eventBus.on('user-updated', (user) => console.log(user))
 * // 发布事件
 * eventBus.emit('user-updated', { name: 'test' })
 * // 取消订阅
 * unsubscribe()
 */

type EventCallback = (...args: any[]) => void

/**
 * 事件总线类
 * 实现简单的发布-订阅模式
 */
class EventBus {
  /** 事件监听器映射表 */
  private events: Map<string, Set<EventCallback>> = new Map()

  /**
   * 订阅事件
   * @param event 事件名称
   * @param callback 回调函数
   * @returns 取消订阅函数
   */
  on(event: string, callback: EventCallback): () => void {
    if (!this.events.has(event)) {
      this.events.set(event, new Set())
    }
    this.events.get(event)!.add(callback)

    return () => this.off(event, callback)
  }

  /**
   * 取消订阅事件
   * @param event 事件名称
   * @param callback 回调函数
   */
  off(event: string, callback: EventCallback): void {
    this.events.get(event)?.delete(callback)
  }

  /**
   * 发布事件
   * @param event 事件名称
   * @param args 传递给回调函数的参数
   */
  emit(event: string, ...args: any[]): void {
    this.events.get(event)?.forEach(cb => cb(...args))
  }
}

/** 全局事件总线实例 */
export const eventBus = new EventBus()
