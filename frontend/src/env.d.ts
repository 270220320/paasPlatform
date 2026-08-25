/// <reference types="vite/client" />

interface Window {
  /** 司空 paas.js 注入的全局对象（驾驶舱/设备小窗/航线等组件入口） */
  FH2?: {
    initConfig: (opts: Record<string, unknown>) => void
    loadCockpit?: (domId: string, params?: Record<string, unknown>) => void
    loadDeviceWindow?: (domId: string, params?: Record<string, unknown>) => void
    destroyDeviceWindow?: () => Promise<void> | void
    loadMap?: (host: string, auth?: Record<string, unknown>) => unknown
    initNotifyCenter?: (wssUrl: string, auth?: Record<string, unknown>) => void
    [key: string]: unknown
  }
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}

declare module 'zywlgis' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, unknown>
  export default component
}
