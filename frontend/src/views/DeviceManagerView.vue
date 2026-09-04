<template>
  <div ref="rootEl" class="dm-page">
    <div v-if="statusText !== '设备管理已就绪'" class="status">{{ statusText }}</div>
    <div ref="sdkHostRef" class="dm-host">
      <div :key="sdkMountKey" id="uranus-device-manager-app" class="dm-root"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { usePaasBootstrap } from '@/composables/usePaasBootstrap'
import { loadScript, loadStylesheet } from '@/utils/loadScript'
import { purgeOrphanSdkContainers } from '@/utils/sdkContainers'

declare global {
  interface Window {
    /** KOC3+ 设备管理 SDK：setupDeviceManager 全局初始化 + mountXxx 模块挂载 */
    UranusDeviceManagerApp?: {
      setupDeviceManager: (conf: Record<string, unknown>) => Promise<void>
      mountDeviceList: (opts: {
        container: string | HTMLElement
        props?: Record<string, unknown>
        on?: Record<string, unknown>
      }) => { unmount: () => void }
      applyTheme?: (el: HTMLElement, theme: string) => void
    }
  }
}

const { load, authConfig } = usePaasBootstrap()
const statusText = ref('正在加载设备管理…')
const sdkHostRef = ref<HTMLElement | null>(null)
const rootEl = ref<HTMLElement | null>(null)
const sdkMountKey = ref(0)
let sdkMounted = false
/** 两层架构：mountDeviceList 返回的模块句柄，卸载时调 unmount */
let deviceListHandle: { unmount: () => void } | null = null
let resizeObserver: ResizeObserver | null = null
/** 对齐 paasVue DeviceView：设备 SDK light 主题会写全局 CSS 变量，离开前需还原 */
let themeSnapshot: ReturnType<typeof captureGlobalTheme> = null
let themeSink: HTMLElement | null = null

type DeviceSdk = NonNullable<Window['UranusDeviceManagerApp']>

/**
 * 记录 init 前文档上与 Uranus 主题相关的 inline CSS 变量。
 * 参考 D:/project/paasVue/src/views/DeviceView.vue
 */
function captureGlobalTheme(sdk: DeviceSdk | undefined) {
  if (!sdk?.applyTheme) return null

  const probe = document.createElement('div')
  sdk.applyTheme(probe, 'dark')
  const names = [...probe.style].filter((name) => name.startsWith('--'))
  const elements = [document.documentElement, ...document.querySelectorAll<HTMLElement>('*')]

  return {
    names,
    elements: elements.map((element): [HTMLElement, Map<string, { value: string; priority: string }>] => {
      const values = new Map<string, { value: string; priority: string }>()
      const inlineNames = new Set([...element.style])
      for (const name of names) {
        if (inlineNames.has(name)) {
          values.set(name, {
            value: element.style.getPropertyValue(name),
            priority: element.style.getPropertyPriority(name),
          })
        }
      }
      return [element, values]
    }),
  }
}

/** unmount 后还原全局主题变量，避免航线编辑等页面被刷成白色 */
function restoreGlobalTheme(sdk: DeviceSdk | undefined) {
  if (!themeSnapshot || !sdk?.applyTheme) return

  themeSink ||= document.createElement('div')
  sdk.applyTheme(themeSink, 'dark')
  for (const name of themeSnapshot.names) themeSink.style.removeProperty(name)

  const original = new Map<HTMLElement, Map<string, { value: string; priority: string }>>(
    themeSnapshot.elements,
  )
  const currentElements = [document.documentElement, ...document.querySelectorAll<HTMLElement>('*')]
  for (const element of currentElements) {
    const saved = original.get(element)
    for (const name of themeSnapshot.names) {
      const item = saved?.get(name)
      if (item) element.style.setProperty(name, item.value, item.priority)
      else element.style.removeProperty(name)
    }
  }
  themeSnapshot = null
}

/**
 * setupDeviceManager 为幂等初始化：二次进入设备管理时不会重放 theme: 'light'，
 * 而离开时的 restoreGlobalTheme 已清掉根元素上的 light 变量，html.dark 会把组件刷黑。
 * 挂载时手动把 light 主题变量写到根元素，确保从空域安全等暗色页面切换过来仍是白底。
 */
function forceLightTheme(sdk: DeviceSdk) {
  sdk.applyTheme?.(document.documentElement, 'light')
}

function getMainEl() {
  return document.querySelector<HTMLElement>('.layout > .el-main.main')
}

function syncHostSize() {
  const host = sdkHostRef.value
  if (!host) return
  const main = getMainEl()
  const height = main?.clientHeight || Math.max(window.innerHeight - 60, 320)
  host.style.width = '100%'
  host.style.height = `${height}px`
  host.style.minHeight = `${height}px`
}

function bindObservers() {
  resizeObserver?.disconnect()
  resizeObserver = new ResizeObserver(() => syncHostSize())
  const main = getMainEl()
  if (main) resizeObserver.observe(main)
  resizeObserver.observe(document.documentElement)
}

async function destroy() {
  resizeObserver?.disconnect()
  resizeObserver = null
  try {
    // 两层架构：卸载模块句柄，清空容器 DOM
    if (deviceListHandle) {
      try {
        deviceListHandle.unmount()
      } catch {
        // ignore
      }
      deviceListHandle = null
    }
  } catch {
    // ignore
  }
  if (sdkMounted) {
    sdkMountKey.value += 1
    sdkMounted = false
  }
  // 对齐 paasVue：unmount 之后还原全局主题，清掉 light 主题残留
  restoreGlobalTheme(window.UranusDeviceManagerApp)
  // 清理 SDK 可能挪到 body 的游离全局容器
  purgeOrphanSdkContainers(rootEl.value)
}

async function bootSdkMode(cfg: NonNullable<Awaited<ReturnType<typeof load>>>) {
  await loadStylesheet(cfg.deviceSdkCss, 'device-manager-sdk-css')
  await loadScript(cfg.deviceSdkJs)
  const sdk = window.UranusDeviceManagerApp
  if (!sdk?.setupDeviceManager || !sdk.mountDeviceList) {
    throw new Error('UranusDeviceManagerApp 未加载或缺少 setupDeviceManager/mountDeviceList 入口')
  }
  syncHostSize()
  await nextTick()
  if (!document.getElementById('uranus-device-manager-app')) {
    throw new Error('设备管理挂载点未就绪')
  }
  // 初始化前快照，供离开时还原被 light 主题改写的全局 CSS 变量
  themeSnapshot = captureGlobalTheme(sdk)

  // 两层架构：全局初始化（幂等）+ 模块挂载；鉴权走私有化 user_id 模式
  await sdk.setupDeviceManager({
    serverUrl: cfg.serverHost,
    wsUrl: cfg.wssUrl,
    auth: authConfig(cfg),
    locale: 'zh',
    theme: 'light',
    antThemeBaseUrl: `${cfg.frontHost.replace(/\/+$/, '')}/paasjs-demo/device-manager-sdk-v2/ant-theme`,
    callbacks: {
      onError: (err: unknown) => {
        console.error('[DeviceManager]', err)
        ElMessage.error('设备管理组件错误')
      },
    },
  })
  // 幂等初始化不重放 theme: 'light'，手动写入根元素，避免二次进入被刷黑
  forceLightTheme(sdk)
  deviceListHandle = sdk.mountDeviceList({
    container: '#uranus-device-manager-app',
    props: { deviceType: 'airport' },
  })
  // 挂载过程可能按 SDK 内部 dark 状态重写主题变量，补一次确保白底
  forceLightTheme(sdk)
  sdkMounted = true
  bindObservers()
  await nextTick()
  syncHostSize()
  statusText.value = '设备管理已就绪'
}

async function reload() {
  await destroy()
  const cfg = await load()
  if (!cfg?.enabled) {
    statusText.value = 'PaaS 未启用'
    return
  }
  try {
    await bootSdkMode(cfg)
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : String(e)
    statusText.value = msg
    ElMessage.error(msg)
  }
}

onMounted(() => {
  syncHostSize()
  window.addEventListener('resize', syncHostSize)
  reload().catch((e) => {
    statusText.value = e?.message || '加载失败'
    ElMessage.error(statusText.value)
  })
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncHostSize)
  destroy()
})
</script>

<style scoped lang="scss">
.dm-page {
  height: 100%;
  min-height: 0;
  background: #fff;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
}
.status {
  position: absolute;
  top: 8px;
  left: 12px;
  z-index: 2;
  color: #86909c;
  font-size: 13px;
  pointer-events: none;
}
.dm-host,
.dm-root {
  flex: 1 1 auto;
  width: 100%;
  min-height: 0;
  background: #fff;
}
</style>
