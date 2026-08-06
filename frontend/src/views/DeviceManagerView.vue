<template>
  <div ref="rootEl" class="dm-page">
    <div v-if="statusText !== '设备管理已就绪'" class="status">{{ statusText }}</div>
    <div ref="sdkHostRef" class="dm-host">
      <div :key="sdkMountKey" id="uranus-device-manager-app" class="dm-root"></div>
    </div>
    <div id="device-manager-app-container" class="dm-root hidden"></div>
    <div id="map-app-global" class="map-hidden"></div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { usePaasBootstrap } from '@/composables/usePaasBootstrap'
import { loadPaasJs, loadScript, loadStylesheet } from '@/utils/loadScript'
import { purgeOrphanSdkContainers } from '@/utils/sdkContainers'

declare global {
  interface Window {
    UranusDeviceManagerApp?: {
      init: (opts: Record<string, unknown>) => Promise<void>
      unmount?: () => Promise<void> | void
      applyTheme?: (el: HTMLElement, theme: string) => void
    }
    FH2?: {
      initConfig: (opts: Record<string, unknown>) => void
      loadDevice: (domId: string, params?: Record<string, unknown>) => void
      destroyDevice: () => Promise<void>
    }
  }
}

const { load, authConfig } = usePaasBootstrap()
const statusText = ref('正在加载设备管理…')
const mode = ref<'sdk' | 'paas'>('sdk')
const sdkHostRef = ref<HTMLElement | null>(null)
const rootEl = ref<HTMLElement | null>(null)
const sdkMountKey = ref(0)
let sdkMounted = false
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
  const sdk = window.UranusDeviceManagerApp
  try {
    if (mode.value === 'sdk' && sdkMounted && sdk?.unmount) {
      await Promise.resolve(sdk.unmount()).catch(() => {})
    }
    if (mode.value === 'paas' && window.FH2?.destroyDevice) {
      await Promise.resolve(window.FH2.destroyDevice()).catch(() => {})
    }
  } catch {
    // ignore
  }
  if (mode.value === 'sdk') {
    if (sdkMounted) {
      sdkMountKey.value += 1
      sdkMounted = false
    }
    // 对齐 paasVue：unmount 之后还原全局主题，清掉 light 主题残留
    restoreGlobalTheme(sdk)
  } else if (sdkMounted) {
    sdkMounted = false
  }
  // 清理 SDK 可能挪到 body 的游离全局容器
  purgeOrphanSdkContainers(rootEl.value)
}

async function bootSdkMode(cfg: NonNullable<Awaited<ReturnType<typeof load>>>) {
  await loadStylesheet(cfg.deviceSdkCss, 'device-manager-sdk-css')
  await loadScript(cfg.deviceSdkJs)
  if (!window.UranusDeviceManagerApp) {
    throw new Error('UranusDeviceManagerApp 未加载，尝试 paas.js 模式')
  }
  mode.value = 'sdk'
  syncHostSize()
  await nextTick()
  if (!document.getElementById('uranus-device-manager-app')) {
    throw new Error('设备管理挂载点未就绪')
  }
  // init 前快照，供离开时还原被 light 主题改写的全局 CSS 变量
  themeSnapshot = captureGlobalTheme(window.UranusDeviceManagerApp)
  await window.UranusDeviceManagerApp.init({
    containerId: '#uranus-device-manager-app',
    serverUrl: cfg.serverHost,
    wsUrl: cfg.wssUrl,
    auth: authConfig(cfg),
    locale: 'zh',
    theme: 'light',
    callbacks: {
      onError: (err: unknown) => {
        console.error('[DeviceManager]', err)
        ElMessage.error('设备管理组件错误')
      },
    },
  })
  sdkMounted = true
  bindObservers()
  await nextTick()
  syncHostSize()
  statusText.value = '设备管理已就绪'
}

async function bootPaasMode(cfg: NonNullable<Awaited<ReturnType<typeof load>>>) {
  await loadPaasJs(cfg.frontHost)
  if (!window.FH2) {
    throw new Error('window.FH2 未就绪')
  }
  mode.value = 'paas'
  window.FH2.initConfig({
    serverUrl: cfg.serverHost,
    wssUrl: cfg.wssUrl,
    hostUrl: cfg.frontHost,
  })
  document.querySelector('.dm-host')?.classList.add('hidden')
  document.getElementById('device-manager-app-container')?.classList.remove('hidden')
  window.FH2.loadDevice('device-manager-app-container', { deviceType: 'airport' })
  statusText.value = '设备管理已就绪'
}

async function reload() {
  await destroy()
  const cfg = await load()
  if (!cfg?.enabled) {
    statusText.value = 'PaaS 未启用'
    return
  }
  if (!cfg.userToken) {
    ElMessage.error('未配置 userToken')
    statusText.value = '缺少 userToken'
    return
  }
  try {
    await bootSdkMode(cfg)
  } catch (e) {
    console.warn(e)
    try {
      await bootPaasMode(cfg)
    } catch (e2: unknown) {
      const msg = e2 instanceof Error ? e2.message : String(e2)
      statusText.value = msg
      ElMessage.error(msg)
    }
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
.hidden {
  display: none !important;
}
.map-hidden {
  width: 0;
  height: 0;
  overflow: hidden;
}
</style>
