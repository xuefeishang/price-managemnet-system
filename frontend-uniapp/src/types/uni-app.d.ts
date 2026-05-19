/// <reference types="@dcloudio/types" />

declare module '*.vue' {
  import { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}

// 扩展 uni 类型
declare namespace UniNamespace {
  interface Uni {
    $emit(event: string, ...args: any[]): void
    $on(event: string, callback: (...args: any[]) => void): void
    $off(event: string, callback?: (...args: any[]) => void): void
    $once(event: string, callback: (...args: any[]) => void): void
  }
}