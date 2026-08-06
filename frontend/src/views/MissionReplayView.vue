<template>
  <div class="replay-page">
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
import { loadPaasJs, loadScript, loadStylesheet } from '@/utils/loadScript'

declare global {
  interface Window {
    FlyDataReplay?: new (opts: Record<string, unknown>) => FlyDataReplayInstance
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
const domKey = ref(0)
let player: FlyDataReplayInstance | null = null
let alive = true
let initSeq = 0

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

  try {
    const cfg = await load()
    if (!alive || seq !== initSeq) return
    if (!cfg.enabled) {
      throw new Error(t('cockpit.paasDisabled'))
    }
    if (!cfg.frontHost) {
      throw new Error('frontHost 未配置')
    }

    await loadPaasJs(cfg.frontHost)
    if (!alive || seq !== initSeq) return

    const css = cfg.replaySdkCss || `${cfg.frontHost.replace(/\/+$/, '')}/paasjs-demo/fly-data-sdk/index.css`
    const js = cfg.replaySdkJs || `${cfg.frontHost.replace(/\/+$/, '')}/paasjs-demo/fly-data-sdk/index.js`
    await loadStylesheet(css, 'fh2-replay-sdk-css')
    await loadScript(js)
    if (!alive || seq !== initSeq) return
    if (!window.FlyDataReplay) {
      throw new Error(t('missions.replaySdkMissing'))
    }

    await nextTick()
    const container = mountEl.value || document.getElementById('fly-data-app-container')
    if (!container) {
      throw new Error(t('missions.replayDomMissing'))
    }

    const instance = new window.FlyDataReplay({
      taskId,
      hostUrl: cfg.frontHost,
      api: { baseUrl: cfg.gatewayBase, auth: authConfig(cfg) },
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

    await instance.loadMap()
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
