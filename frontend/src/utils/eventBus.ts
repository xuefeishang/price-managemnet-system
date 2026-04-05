type EventCallback = (...args: any[]) => void

class EventBus {
  private events: Map<string, Set<EventCallback>> = new Map()

  on(event: string, callback: EventCallback): () => void {
    if (!this.events.has(event)) {
      this.events.set(event, new Set())
    }
    this.events.get(event)!.add(callback)

    return () => this.off(event, callback)
  }

  off(event: string, callback: EventCallback): void {
    this.events.get(event)?.delete(callback)
  }

  emit(event: string, ...args: any[]): void {
    this.events.get(event)?.forEach(cb => cb(...args))
  }
}

export const eventBus = new EventBus()
