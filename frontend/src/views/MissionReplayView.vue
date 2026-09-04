<template>
  <div ref="replayPageEl" class="replay-page">
    <div class="replay-toolbar">
      <el-button @click="goBack">{{ t('missions.replayBack') }}</el-button>
      <strong>{{ t('missions.replayTitle') }}</strong>
      <span v-if="statusText" class="status">{{ statusText }}</span>
    </div>
    <div v-if="errorText" class="replay-error">
      <el-alert :title="errorText" type="error" show-icon :closable="false" />
      <el-button class="retry" type="primary" @click="initialize">重试</el-button>
    </div>
    <div :key="domKey" class="fh2-container replay-root">
      <div id="wayline-header"></div>
      <div class="project-details">
        <div id="project-middle-container"></div>
        <div id="project-right-micro-app" class="right-micro-app">
          <div class="maps-micro-app">
            <div id="project-map-app-placeholder" class="map-app-placeholder">
              <div id="map-app-global" class="map-app-container"></div>
            </div>
          </div>
          <div id="fly-data-app-container" ref="mountEl"></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { usePaasBootstrap } from '@/composables/usePaasBootstrap'
import { loadScript, loadStylesheet, reloadScript } from '@/utils/loadScript'
import {
  purgeFh2DepScripts,
  resetFh2SharedMapContext,
  teardownSdkOnRouteLeave,
} from '@/utils/sdkContainers'

declare global {
  interface Window {
    FlyDataReplay?: new (opts: Record<string, unknown>) => FlyDataReplayInstance
    CESIUM_BASE_URL?: string
    process?: { env: Record<string, string> }
  }
}

interface FlyDataReplayInstance {
  loadMap: () => Promise<void>
  mount: (el: HTMLElement) => void
  destroy: () => void
  on: (event: string, handler: (...args: unknown[]) => void) => void
}

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { load, authConfig } = usePaasBootstrap()

const statusText = ref(t('missions.replayLoading'))
const errorText = ref('')
const mountEl = ref<HTMLElement | null>(null)
const replayPageEl = ref<HTMLElement | null>(null)
const domKey = ref(0)
let player: FlyDataReplayInstance | null = null
let alive = true
let initSeq = 0
/** loadMap 超时：SDK 依赖加载（data-fh2-dep 死标签）可能永挂且无报错，需外层兑底 */
const LOAD_MAP_TIMEOUT_MS = 45_000
/** 会话级自愈标记：SDK 重载只做一次，避免循环 */
let sdkSelfHealed = false

function loadMapWithTimeout(instance: FlyDataReplayInstance): Promise<void> {
  return Promise.race([
    instance.loadMap(),
    new Promise<never>((_, reject) => {
      window.setTimeout(() => reject(new Error('loadMap timeout')), LOAD_MAP_TIMEOUT_MS)
    }),
  ])
}

function taskIdOf(): string {
  const q = String(route.query.taskId || '').trim()
  if (q) return q
  return String(route.query.fh2TaskUuid || '').trim()
}

function planIdOf(): number | null {
  const n = Number(route.params.planId)
  return Number.isFinite(n) ? n : null
}

function goBack() {
  const planId = planIdOf()
  if (planId != null) {
    router.push({ name: 'mission-detail', params: { id: planId } })
    return
  }
  router.push({ name: 'missions' })
}

function destroyPlayer() {
  try {
    player?.destroy()
  } catch {
    // ignore
  }
  player = null
}

function remountDom() {
  destroyPlayer()
  domKey.value += 1
}

async function initialize() {
  const seq = ++initSeq
  remountDom()
  await nextTick()
  if (!alive || seq !== initSeq) return

  errorText.value = ''
  statusText.value = t('missions.replayLoading')

  const taskId = taskIdOf()
  if (!taskId) {
    errorText.value = t('missions.replayMissingTask')
    statusText.value = ''
    return
  }

  let sdkJsUrl = ''
  let instance: FlyDataReplayInstance | null = null
  try {
    const cfg = await load()
    if (!alive || seq !== initSeq) return
    if (!cfg.enabled) {
      throw new Error(t('cockpit.paasDisabled'))
    }
    if (!cfg.frontHost) {
      throw new Error('frontHost 未配置')
    }

    // 新版 FlyDataReplay 自包含（internal 模式自建 Cesium），不依赖 paas.js。
    // 但先经航线/驾驶舱页时，window.FH2.mapAppState 会残留已 destroy 置空的 'global'
    // map 实例，SDK 自建地图命中该空条目即报 "map instance is null"，挂载前先拆净共享上下文。
    await resetFh2SharedMapContext(replayPageEl.value)
    if (!alive || seq !== initSeq) return

    // wayline-edit-sdk 与本 SDK 的依赖表完全相同，其 data-fh2-dep 查重只比路径不比 src：
    // 航线页残留的失败/未完成依赖标签会让本页依赖加载永挂（loadMap 无超时），进入前先清空
    purgeFh2DepScripts()

    const base = cfg.frontHost.replace(/\/+$/, '')
    const runtimeBase = `${base}/paasjs-demo/sdk-runtime`
    // 新版 fly-data-sdk 自包含架构：SDK 启动时读取 window.Cesium，
    // 必须先加载 Cesium 并设置 CESIUM_BASE_URL，否则 loadMap() 抛 6002（MAP_INIT_ERROR）
    window.CESIUM_BASE_URL = `${runtimeBase}/cesium/`
    window.process = window.process || { env: { NODE_ENV: 'production' } }
    await loadStylesheet(`${runtimeBase}/cesium/Widgets/widgets.css`, 'fh2-cesium-widgets-css')
    await loadScript(`${runtimeBase}/cesium/Cesium.js`)
    if (!alive || seq !== initSeq) return

    sdkJsUrl = cfg.replaySdkJs || `${base}/paasjs-demo/fly-data-sdk/index.js`
    const css = cfg.replaySdkCss || `${base}/paasjs-demo/fly-data-sdk/index.css`
    await loadStylesheet(css, 'fh2-replay-sdk-css')
    await loadScript(sdkJsUrl)
    if (!alive || seq !== initSeq) return
    if (!window.FlyDataReplay) {
      throw new Error(t('missions.replaySdkMissing'))
    }

    await nextTick()
    const container = mountEl.value || document.getElementById('fly-data-app-container')
    if (!container) {
      throw new Error(t('missions.replayDomMissing'))
    }

    instance = new window.FlyDataReplay({
      taskId,
      api: { baseUrl: cfg.gatewayBase, auth: authConfig(cfg) },
      // hostUrl 已废弃：改用 runtimeDepsBasePath + map.env.cesiumBaseUrl（新版 SDK 规范）
      runtimeDepsBasePath: runtimeBase,
      map: {
        mode: 'internal',
        container: '#map-app-global',
        env: {
          cesiumBaseUrl: `${runtimeBase}/cesium/`,
          terrainKey: '',
          featureGateway: [],
        },
      },
      locale: { language: 'zh' },
      features: {
        playerBarBtn: { params: true, media: true, sdr: false, fov: true, wayline: true },
        playerTrack: { flight_status: true, device_warning: true },
      },
    })
    instance.on('error', (event: unknown) => {
      const e = event as { code?: number; message?: string }
      errorText.value = `${t('missions.replayLoadFailed')} ${e.code || ''}：${e.message || ''}`
    })
    instance.on('back', () => {
      goBack()
    })

    await loadMapWithTimeout(instance)
    if (!alive || seq !== initSeq) {
      try {
        instance.destroy()
      } catch {
        // ignore
      }
      return
    }
    instance.mount(container)
    player = instance
    statusText.value = t('missions.replayReady')
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : String(e)
    // loadMap 永挂自愈：SDK 依赖 Promise 是模块级单例，一旦挂起后续实例全部复用挂起态。
    // 重载 SDK（UMD 重执行重置模块级单例）+ 清理死标签后重试一次即可恢复
    if (msg.includes('loadMap timeout') && sdkJsUrl && !sdkSelfHealed) {
      sdkSelfHealed = true
      try {
        instance?.destroy()
      } catch {
        // ignore
      }
      try {
        purgeFh2DepScripts()
        await reloadScript(sdkJsUrl)
      } catch {
        // 重载失败走正常错误提示
      }
      if (alive && seq === initSeq) void initialize()
      return
    }
    if (!alive || seq !== initSeq) return
    errorText.value = e instanceof Error ? e.message : t('missions.replayLoadFailed')
    statusText.value = ''
  }
}

watch(
  () => [route.params.executionId, route.query.taskId, route.query.fh2TaskUuid],
  () => {
    void initialize()
  },
)

onMounted(() => {
  alive = true
  void initialize()
})

onBeforeUnmount(() => {
  alive = false
  initSeq += 1
  destroyPlayer()
  // 与航线页/驾驶舱页对齐：同步清游离容器、异步拆 FH2 共享上下文，避免污染下一页
  teardownSdkOnRouteLeave()
})
</script>

<style scoped lang="scss">
.replay-page {
  position: relative;
  display: flex;
  flex-direction: column;
  height: calc(100vh - 56px);
  min-height: 0;
  background: #0a0a0a;
  overflow: hidden;
}

.replay-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
  padding: 8px 12px;
  border-bottom: 1px solid #2b2b2c;
  background: #141414;
  z-index: 3;

  .status {
    margin-left: auto;
    color: #8b919a;
    font-size: 12px;
  }
}

.replay-error {
  position: absolute;
  top: 64px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 4;
  width: min(520px, 90%);
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: stretch;

  .retry {
    align-self: center;
  }
}

.replay-root.fh2-container {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  width: 100%;
  height: 100%;
  padding-top: 0;
}
</style>

<style lang="scss">
.replay-root .map-app-container > div {
  width: 100%;
  height: 100%;
}

.replay-root .project-details {
  position: relative;
  width: 100%;
  display: flex;
  flex: 1;
  overflow: hidden;
  min-height: 0;
}

.replay-root .project-details .right-micro-app {
  position: relative;
  display: flex;
  flex: 1;
}

.replay-root .project-details .right-micro-app .maps-micro-app {
  display: flex;
  flex: 1;
  height: 100%;
}

.replay-root .project-details .right-micro-app .map-app-placeholder {
  flex: 1;
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.replay-root .project-details .right-micro-app .map-app-container {
  height: 100%;
  min-height: 0;
}

.replay-root .project-details .right-micro-app #fly-data-app-container {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.replay-root .project-details .right-micro-app > .fly-data-root {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
  background: transparent;
}
</style>
