<template>
  <div class="preview-pane">
    <div v-if="!waylineUuid" class="preview-empty">
      {{ t('missions.selectWaylineHint') }}
    </div>
    <template v-else>
      <div v-if="statusText" class="preview-status">{{ statusText }}</div>
      <div v-if="busy" class="preview-loading">{{ t('missions.waylineLoading') }}</div>
      <div class="fh2-container wayline-preview-root">
        <div class="project-details">
          <div id="project-app-container"></div>
          <div id="project-middle-container"></div>
          <div id="project-right-micro-app" class="right-micro-app">
            <div class="maps-micro-app">
              <div id="project-map-app-placeholder" class="map-app-placeholder">
                <div id="map-app-global" class="map-app-container"></div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePaasBootstrap } from '@/composables/usePaasBootstrap'
import { loadPaasJs } from '@/utils/loadScript'

interface AuthConfig {
  authKeys: string[]
  headers: () => Record<string, string>
}

interface Fh2PreviewApi {
  initConfig: (config: {
    serverUrl: string
    hostUrl: string
    wssUrl?: string
    projectToken?: string
    prjId?: string
  }) => void
  loadMap: (serverUrl: string, auth: AuthConfig) => void | Promise<void>
  loadWaylineView: (
    domId: string,
    params: {
      waylineIdArray: string[]
      authConfig: { baseUrl: string; auth: AuthConfig }
    },
  ) => void
  destroyPaasView?: () => void | Promise<void>
  destroyMap?: () => void | Promise<void>
}

const props = defineProps<{
  waylineUuid?: string
}>()

const { t } = useI18n()
const { load, authConfig } = usePaasBootstrap()

const busy = ref(false)
const statusText = ref('')
let viewLoaded = false
let loadSeq = 0

function fh2(): Fh2PreviewApi | undefined {
  return window.FH2 as unknown as Fh2PreviewApi | undefined
}

async function waitForDom() {
  for (let i = 0; i < 30; i += 1) {
    await nextTick()
    if (
      document.getElementById('project-app-container') &&
      document.getElementById('project-middle-container') &&
      document.getElementById('map-app-global')
    ) {
      return
    }
    await new Promise((r) => setTimeout(r, 50))
  }
  throw new Error(t('missions.previewDomNotReady'))
}

function clearPreviewContainers() {
  for (const id of ['project-app-container', 'map-app-global', 'project-middle-container']) {
    document.getElementById(id)?.replaceChildren()
  }
}

async function resetPreview() {
  const api = fh2()
  if (!api) return
  try {
    await Promise.resolve(api.destroyPaasView?.())
  } catch {
    // ignore
  }
  try {
    await Promise.resolve(api.destroyMap?.())
  } catch {
    // ignore
  }
  viewLoaded = false
}

async function showWayline(waylineUuid: string) {
  const seq = ++loadSeq
  busy.value = true
  statusText.value = ''
  try {
    const cfg = await load()
    if (!cfg?.enabled) {
      statusText.value = t('waylineEditor.paasDisabled')
      return
    }

    await loadPaasJs(cfg.frontHost)
    await waitForDom()
    if (seq !== loadSeq) return

    const api = fh2()
    if (!api) throw new Error(t('cockpit.fh2NotReady'))

    const auth = authConfig(cfg)
    const prjId = cfg.projectUuid?.trim() || ''
    api.initConfig({
      serverUrl: cfg.gatewayBase,
      hostUrl: cfg.frontHost,
      projectToken: cfg.userToken,
      ...(prjId ? { prjId } : {}),
    })

    const params = {
      waylineIdArray: [waylineUuid],
      authConfig: {
        baseUrl: cfg.gatewayBase,
        auth,
      },
    }

    if (viewLoaded) {
      api.loadWaylineView('project-app-container', params)
      return
    }

    await resetPreview()
    clearPreviewContainers()
    if (seq !== loadSeq) return

    await Promise.resolve(api.loadMap(cfg.serverHost, auth))
    if (seq !== loadSeq) return

    api.loadWaylineView('project-app-container', params)
    viewLoaded = true
  } catch (e: unknown) {
    if (seq !== loadSeq) return
    statusText.value = e instanceof Error ? e.message : t('missions.waylineLoadFailed')
  } finally {
    if (seq === loadSeq) busy.value = false
  }
}

watch(
  () => props.waylineUuid,
  (uuid) => {
    if (!uuid) {
      loadSeq += 1
      void resetPreview()
      statusText.value = ''
      return
    }
    void showWayline(uuid)
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  loadSeq += 1
  void resetPreview()
})
</script>

<style scoped lang="scss">
.preview-pane {
  position: relative;
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #0a0a0a;
  overflow: hidden;
}

.preview-empty {
  flex: 1;
  display: grid;
  place-items: center;
  color: #8b919a;
  font-size: 14px;
}

.preview-status {
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 5;
  max-width: 80%;
  padding: 6px 12px;
  border-radius: 6px;
  background: rgba(20, 20, 20, 0.85);
  color: #f56c6c;
  font-size: 12px;
}

.preview-loading {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 5;
  padding: 6px 10px;
  border-radius: 6px;
  background: rgba(20, 20, 20, 0.85);
  color: #a3a6ad;
  font-size: 12px;
}

.wayline-preview-root.fh2-container {
  flex: 1;
  min-height: 0;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  padding-top: 0;
}
</style>

<style lang="scss">
.wayline-preview-root .map-app-container > div {
  width: 100%;
  height: 100%;
}

.wayline-preview-root .project-details {
  position: relative;
  width: 100%;
  display: flex;
  flex: 1;
  overflow: hidden;
  min-height: 0;
}

.wayline-preview-root .project-details .right-micro-app {
  position: relative;
  display: flex;
  flex: 1;
}

.wayline-preview-root .project-details .right-micro-app .maps-micro-app {
  display: flex;
  flex: 1;
  height: 100%;
}

.wayline-preview-root .project-details .right-micro-app .map-app-placeholder {
  flex: 1;
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.wayline-preview-root .project-details .right-micro-app .map-app-container {
  height: 100%;
  min-height: 0;
}
</style>
