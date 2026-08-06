<template>
  <div class="cockpit-page">
    <div class="toolbar">
      <el-button @click="goBack">{{ t('cockpit.back') }}</el-button>
      <span class="title">{{ deviceName || t('cockpit.title') }}</span>
      <span class="hint">{{ status }}</span>
    </div>

    <div class="fh2-container">
      <div id="cockpit-header-container"></div>
      <div class="project-details router-cockpit">
        <div id="project-app-container"></div>
        <div id="project-middle-container"></div>
        <div id="project-right-micro-app" class="right-micro-app">
          <div class="maps-micro-app">
            <div class="cockpit-left-border-container"></div>
            <div id="project-map-app-placeholder" class="map-app-placeholder">
              <div class="cockpit-dock-live-container"></div>
              <div id="map-app-global" class="map-app-container"></div>
              <div class="cockpit-bottom-border-container"></div>
            </div>
          </div>
          <div class="project-right">
            <div id="cockpit-app-container"></div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { usePaasBootstrap } from '@/composables/usePaasBootstrap'
import { loadPaasJs } from '@/utils/loadScript'

interface CockpitFh2Api {
  initConfig: (config: {
    serverUrl: string
    wssUrl?: string
    hostUrl: string
    projectToken?: string
    prjId?: string
  }) => void
  initNotifyCenter: (wssUrl: string, auth: unknown) => void | Promise<void>
  loadMap: (serverUrl: string, auth: unknown) => void | Promise<void>
  loadCockpit: (
    domId: string,
    params: { gateway_sn: string; drone_sn: string; map?: boolean },
  ) => unknown
  destroyCockpit?: (self?: boolean) => Promise<boolean>
}

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { load, authConfig } = usePaasBootstrap()
const status = ref(t('cockpit.loading'))
const deviceName = String(route.query.name || '')
const gatewaySn = String(route.query.gatewaySn || '')
const droneSn = String(route.query.droneSn || '')

function goBack() {
  router.push({ name: 'cockpit' })
}

async function destroyCockpit() {
  const fh2 = window.FH2 as unknown as CockpitFh2Api | undefined
  if (!fh2?.destroyCockpit) return
  try {
    await Promise.resolve(fh2.destroyCockpit())
  } catch {
    // ignore
  }
}

async function boot() {
  await destroyCockpit()

  if (!gatewaySn || !droneSn) {
    status.value = t('cockpit.missingDevice')
    ElMessage.warning(status.value)
    return
  }

  const cfg = await load()
  if (!cfg?.enabled) {
    status.value = t('cockpit.paasDisabled')
    return
  }
  if (!cfg.userToken) {
    status.value = t('cockpit.noUserToken')
    ElMessage.error(status.value)
    return
  }

  status.value = t('cockpit.loadingSdk')
  await loadPaasJs(cfg.frontHost)
  await nextTick()

  const fh2 = window.FH2 as unknown as CockpitFh2Api | undefined
  if (!fh2) {
    throw new Error(t('cockpit.sdkUnavailable'))
  }

  // 对齐已通 paasVue：
  // - initConfig.serverUrl 使用带 /gateway 的地址，并传 projectToken
  // - loadMap 使用不带 /gateway 的 serverHost
  // - 通知中心走 /paas-ws 代理
  const auth = authConfig(cfg)
  const prjId = cfg.projectUuid?.trim() || ''
  fh2.initConfig({
    serverUrl: cfg.gatewayBase,
    wssUrl: cfg.wssUrl,
    hostUrl: cfg.frontHost,
    projectToken: cfg.userToken,
    ...(prjId ? { prjId } : {}),
  })
  fh2.initNotifyCenter(cfg.wssUrl, auth)
  await Promise.resolve(fh2.loadMap(cfg.serverHost, auth))
  fh2.loadCockpit('cockpit-app-container', {
    gateway_sn: gatewaySn,
    drone_sn: droneSn,
    map: true,
  })
  status.value = t('cockpit.ready')
}

onMounted(() => {
  boot().catch((error: unknown) => {
    status.value = error instanceof Error ? error.message : t('cockpit.loadFailed')
    ElMessage.error(status.value)
  })
})

onBeforeUnmount(() => {
  void destroyCockpit()
})
</script>

<style scoped lang="scss">
.cockpit-page {
  position: fixed;
  inset: 0;
  z-index: 20;
  display: flex;
  flex-direction: column;
  background: #0a0a0a;
}
.toolbar {
  height: 48px;
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 12px;
  padding: 0 16px;
  border-bottom: 1px solid #363637;
  background: #141414;
  z-index: 30;
}
.title {
  color: #e5eaf3;
  font-weight: 600;
}
.hint {
  overflow: hidden;
  color: #a3a6ad;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>

<style lang="scss">
.cockpit-page .fh2-container {
  display: flex;
  width: 100%;
  flex: 1 1 auto;
  flex-direction: column;
  min-height: 0;
  height: auto;
}

.cockpit-page .fh2-container #project-app-container > div,
.cockpit-page .fh2-container .map-app-container > div {
  width: 100%;
  height: 100%;
}

.cockpit-page .project-details {
  position: relative;
  display: flex;
  width: 100%;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.cockpit-page .right-micro-app {
  position: relative;
  display: flex;
  flex: 1;
}

.cockpit-page .maps-micro-app {
  display: flex;
  height: 100%;
  flex: 1;
}

.cockpit-page .map-app-placeholder {
  position: relative;
  display: flex;
  height: 100%;
  flex: 1;
  flex-direction: column;
}

.cockpit-page .map-app-container {
  width: 100%;
  height: 100%;
  min-height: 0;
}

.cockpit-page .project-right {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.cockpit-page #cockpit-app-container {
  height: 100%;
}

#__qiankun_microapp_wrapper_for_cockpit_app__,
#__qiankun_microapp_wrapper_for_cockpit_app__ > div {
  height: 100% !important;
}
</style>
