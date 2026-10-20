<template>
  <section v-show="paasEnabled" class="dw-panel">
    <header class="dw-hd">
      <strong>{{ t('home.deviceWindow') }}</strong>
      <el-select
        v-model="selectedDeviceSn"
        class="dw-select"
        size="small"
        filterable
        :placeholder="t('home.deviceWindowSelect')"
        :loading="devicesLoading"
        :disabled="!windowReady"
        @change="onDeviceChange"
      >
        <el-option
          v-for="item in deviceOptions"
          :key="item.value"
          :value="item.value"
          :label="item.label"
        />
      </el-select>
    </header>

    <div class="dw-body">
      <div :key="skeletonKey" ref="rootEl" class="dw-fh2-container">
        <div id="map-app-global" class="dw-map"></div>
        <div id="project-app-container" class="dw-project"></div>
        <div id="real-time-app-container" class="dw-realtime"></div>
      </div>

      <div v-if="phase === 'loading'" class="dw-mask">
        <span class="dw-spinner"></span>
        <span class="dw-mask-text">{{ t('home.deviceWindowLoading') }}</span>
      </div>
      <div v-else-if="phase === 'error'" class="dw-mask">
        <span class="dw-mask-text">{{ t('home.deviceWindowFailed') }}</span>
        <el-button size="small" @click="boot">{{ t('common.retry') }}</el-button>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { usePaasBootstrap } from '@/composables/usePaasBootstrap'
import { loadPaasJs } from '@/utils/loadScript'
import { resetFh2SharedMapContext, enqueueSdkLifecycle, enqueuePageTeardown, purgeOrphanSdkContainers, waitForFh2Mounts } from '@/utils/sdkContainers'
import { fetchCockpitDevices } from '@/api/paas'

/**
 * 司空设备小窗（FH2.loadDeviceWindow）：纯小窗浮层模式。
 * loadMap 仍是硬前置：project-app 数据中转层依赖地图态，缺省会触发 SDK
 * 内部路由纠正导致整页跳转循环。因此地图容器保留但视觉隐藏（1px），
 * loadDeviceWindow 传 map: false 不展示地图。
 * 与驾驶舱共享 window.FH2 / mapAppState 全局单例，因此离开本页必须 destroy。
 */
interface DeviceWindowInstance {
  setTask?: (task: { status: string; label?: string }) => void
  setHmsUrl?: (url?: string) => void
  setCockpitUrl?: (url?: string) => void
  setOnlineWeather?: (data: Record<string, unknown>) => void
}

interface DeviceWindowFh2Api {
  initConfig: (config: {
    serverUrl: string
    wssUrl?: string
    hostUrl: string
    projectToken?: string
    prjId?: string
  }) => void
  initNotifyCenter: (wssUrl: string, auth: unknown) => void | Promise<void>
  loadMap: (serverUrl: string, auth: unknown, wssUrl?: string) => void | Promise<void>
  loadDeviceWindow: (
    domId: string,
    params?: { devices?: string; map?: boolean },
  ) => Promise<DeviceWindowInstance[]>
  updateDeviceWindow: (params: { devices: string }) => DeviceWindowInstance[]
  destroyDeviceWindow?: (self?: boolean) => Promise<void>
}

interface DeviceOption {
  value: string
  label: string
  droneSn: string
  name: string
}

const { t } = useI18n()
const router = useRouter()
const { load: loadPaasConfig, authConfig } = usePaasBootstrap()

const paasEnabled = ref(false)
const phase = ref<'idle' | 'loading' | 'ready' | 'error'>('idle')
const devicesLoading = ref(false)
const deviceOptions = ref<DeviceOption[]>([])
const selectedDeviceSn = ref('')
const windowReady = ref(false)
const rootEl = ref<HTMLElement | null>(null)
const skeletonKey = ref(0)

let alive = true
let bootToken = 0
let deviceWindows: DeviceWindowInstance[] = []

function fh2Api(): DeviceWindowFh2Api | undefined {
  return (window as Window & { FH2?: DeviceWindowFh2Api }).FH2
}

/** 机场主记录即一架无人机（舱内机）；与驾驶舱列表的过滤口径一致 */
async function loadDeviceOptions() {
  devicesLoading.value = true
  try {
    const list = await fetchCockpitDevices()
    deviceOptions.value = list
      .filter(
        (device) =>
          (device.gatewaySn || device.sn) && device.deviceType.toLowerCase() !== 'aircraft',
      )
      .map((device) => ({
        value: device.gatewaySn || device.sn,
        label: device.name || device.sn,
        droneSn: device.droneSn || '',
        name: device.name || device.sn,
      }))
    // 只有一台时默认展示第一台
    if (deviceOptions.value.length && !selectedDeviceSn.value) {
      selectedDeviceSn.value = deviceOptions.value[0].value
    }
  } catch {
    deviceOptions.value = []
  } finally {
    devicesLoading.value = false
  }
}

/** 小窗上的驾驶舱入口跳转到本项目驾驶舱详情页 */
function applyInstanceConfig() {
  const option = deviceOptions.value.find((item) => item.value === selectedDeviceSn.value)
  const cockpitUrl = option?.droneSn
    ? window.location.origin +
      router
        .resolve({
          name: 'cockpit-detail',
          query: { gatewaySn: option.value, droneSn: option.droneSn, name: option.name },
        })
        .href
    : ''
  for (const instance of deviceWindows) {
    try {
      instance.setCockpitUrl?.(cockpitUrl)
    } catch {
      // 实例随设备列表 diff 失效时忽略
    }
  }
}

/** 切换设备优先 updateDeviceWindow（SDK 内部 diff），避免卸载重挂载 */
function onDeviceChange(sn: string) {
  const fh2 = fh2Api()
  if (!fh2?.updateDeviceWindow || !windowReady.value || !sn) return
  try {
    // 必须用返回值替换旧数组，否则对旧实例的操作失效
    deviceWindows = fh2.updateDeviceWindow({ devices: sn }) || []
    applyInstanceConfig()
  } catch (error) {
    console.error('[DeviceWindowPane] updateDeviceWindow failed', error)
  }
}

async function boot() {
  const token = ++bootToken
  phase.value = 'loading'
  windowReady.value = false
  try {
    const cfg = await loadPaasConfig()
    if (!alive || token !== bootToken) return
    if (!cfg?.enabled) {
      paasEnabled.value = false
      phase.value = 'idle'
      return
    }
    paasEnabled.value = true

    // 设备下拉与 SDK 加载并行，节省首屏时间
    await Promise.all([loadPaasJs(cfg.frontHost), loadDeviceOptions()])
    if (!alive || token !== bootToken) return

    // 从驾驶舱等页面返回时 mapAppState 可能残留，先重置共享上下文（保留本组件骨架）
    await resetFh2SharedMapContext(rootEl.value)
    await nextTick()
    if (!alive || token !== bootToken) return
    if (!document.getElementById('real-time-app-container')) {
      skeletonKey.value += 1
      await nextTick()
    }

    const fh2 = fh2Api()
    if (!fh2?.loadDeviceWindow) {
      throw new Error('FH2.loadDeviceWindow unavailable')
    }

    await enqueueSdkLifecycle(async () => {
      if (!alive || token !== bootToken) return
      const auth = authConfig(cfg)
      const prjId = cfg.projectUuid?.trim() || ''
      fh2.initConfig({
        serverUrl: cfg.gatewayBase,
        wssUrl: cfg.wssUrl,
        hostUrl: cfg.frontHost,
        projectToken: cfg.userToken || '',
        ...(prjId ? { prjId } : {}),
      })
      fh2.initNotifyCenter(cfg.wssUrl, auth)
      // loadMap 不能省：project-app 挂载后依赖地图态，缺失会触发 SDK 内部路由纠正（整页跳转循环）。
      // 地图容器已在 CSS 中视觉隐藏，不会展示。
      await Promise.resolve(fh2.loadMap(cfg.serverHost, auth, cfg.wssUrl))
      if (!alive || token !== bootToken) {
        // 组件已卸载（onBeforeUnmount 已负责销毁）或 boot 重入（新 boot 会重置共享上下文），直接遇出
        return
      }

      deviceWindows =
        (await fh2.loadDeviceWindow('real-time-app-container', {
          devices: selectedDeviceSn.value,
          map: false,
        })) || []
      await waitForFh2Mounts()
      if (!alive || token !== bootToken) return
      windowReady.value = true
      applyInstanceConfig()
      phase.value = 'ready'
    })
  } catch (error) {
    if (!alive || token !== bootToken) return
    phase.value = 'error'
    console.error('[DeviceWindowPane] boot failed', error)
  }
}

onMounted(() => {
  alive = true
  void boot()
})

onBeforeUnmount(() => {
  alive = false
  bootToken += 1
  deviceWindows = []
  // 同步阶段 purge 游离容器（Vue 卸载时下一页容器尚未挂载，不会误删）
  purgeOrphanSdkContainers()
  // 异步串行销毁：destroyDeviceWindow 必须先完整执行（内部按 小窗→地图→project-app
  // 顺序拆），若与 destroyMap 并发，地图实例先置空会引发小窗回调 "map instance is null"。
  // 必须在卸载的同步阶段整体入队：晚于下一页 boot 入队时，迟到执行的 destroy
  // 会与驾驶舱 loadMap 并发互踩（SDK 报 map-app mount failed）。
  const fh2 = fh2Api()
  void enqueuePageTeardown(() => fh2?.destroyDeviceWindow?.())
})
</script>

<style scoped lang="scss">
.dw-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  color: #e5eaf3;
}

.dw-hd {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;

  strong {
    font-size: 14px;
  }
}

.dw-select {
  width: 170px;

  :deep(.el-select__wrapper) {
    background: rgba(255, 255, 255, 0.06);
    box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.12) inset;
  }
}

.dw-body {
  position: relative;
  flex: 1;
  min-height: 0;
  border-radius: 8px;
  overflow: hidden;
  background: #0d1117;
}

.dw-fh2-container {
  position: relative;
  width: 100%;
  height: 100%;
  /* contain 锚定 SDK 内部 fixed 定位的浮层，防止小窗跑出本卡片 */
  contain: layout paint;
}

/* SDK 地图容器：纯小窗模式下仅作为 loadMap 挂载点（project-app 依赖），视觉隐藏 */
.dw-map {
  position: absolute;
  left: 0;
  top: 0;
  width: 1px;
  height: 1px;
  overflow: hidden;
  opacity: 0;
  pointer-events: none;
}

/* project-app 是数据中转层（复用驾驶舱路由），必须存在但必须隐藏 */
.dw-project {
  display: none;
}

/* pointer-events: none 让地图操作穿透空白区域，小窗本身恢复响应 */
.dw-realtime {
  position: absolute;
  inset: 0;
  z-index: 10;
  pointer-events: none;
}

.dw-mask {
  position: absolute;
  inset: 0;
  z-index: 20;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background: rgba(13, 17, 23, 0.88);
}

.dw-mask-text {
  font-size: 12px;
  color: #a3a6ad;
}

.dw-spinner {
  width: 26px;
  height: 26px;
  border: 2px solid rgba(255, 255, 255, 0.15);
  border-top-color: #409eff;
  border-radius: 50%;
  animation: dw-spin 0.8s linear infinite;
}

@keyframes dw-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>

<!-- qiankun 微应用容器由 SDK 运行时创建（无 data-v 属性），必须用全局选择器；
     加 .dw-fh2-container 前缀避免污染其他页面 -->
<style lang="scss">
.dw-fh2-container #project-app-container > div,
.dw-fh2-container #map-app-global > div {
  width: 100%;
  height: 100%;
}

/* qiankun 微应用容器高度修复 —— 不加小窗会塌陷为 0 高度 */
.dw-fh2-container #__qiankun_microapp_wrapper_for_real_time_app__,
.dw-fh2-container #__qiankun_microapp_wrapper_for_real_time_app__ > div {
  height: 100% !important;
  pointer-events: none;
}

.dw-fh2-container #__qiankun_microapp_wrapper_for_real_time_app__ .real-time-wrapper {
  pointer-events: auto;
  /* SDK 默认 padding-bottom:40px 是全屏模式下给地图底部工具条预留的空隙，
     纯小窗卡片场景改用 8px（微应用内联样式需 !important） */
  padding-bottom: 8px !important;
}
</style>
