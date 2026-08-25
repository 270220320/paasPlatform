<template>
  <div ref="rootEl" class="cockpit-page">
    <div class="toolbar">
      <el-button @click="goBack">{{ t('cockpit.back') }}</el-button>
      <span class="title">{{ deviceName || t('cockpit.title') }}</span>
      <span class="hint">{{ status }}</span>
    </div>

    <div :key="skeletonKey" class="fh2-container">
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
import { resetFh2SharedMapContext } from '@/utils/sdkContainers'

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
const rootEl = ref<HTMLElement | null>(null)
const skeletonKey = ref(0)
let alive = true
let bootToken = 0

async function goBack() {
  await teardown()
  router.push({ name: 'cockpit' })
}

/** 切页前必须卸驾驶舱+地图，并清掉游离全局容器，否则航线库地图会挂到 0 尺寸节点 */
async function teardown() {
  alive = false
  bootToken += 1
  await resetFh2SharedMapContext()
}

/**
 * 卸掉旧 FH2 地图态后，保留本页骨架；若 #map-app-global 仍缺失则强制重建。
 * 注意：reset 时绝不能无 keepWithin，否则会删掉当前页容器 → map-app mount failed。
 */
async function prepareMountSurface() {
  await resetFh2SharedMapContext(rootEl.value)
  await nextTick()
  if (!document.getElementById('map-app-global') || !document.getElementById('cockpit-app-container')) {
    skeletonKey.value += 1
    await nextTick()
  }
}

async function boot() {
  const token = ++bootToken
  await prepareMountSurface()
  if (!alive || token !== bootToken) return

  if (!gatewaySn || !droneSn) {
    status.value = t('cockpit.missingDevice')
    ElMessage.warning(status.value)
    return
  }

  const cfg = await load()
  if (!alive || token !== bootToken) return
  if (!cfg?.enabled) {
    status.value = t('cockpit.paasDisabled')
    return
  }

  status.value = t('cockpit.loadingSdk')
  await loadPaasJs(cfg.frontHost)
  await nextTick()
  if (!alive || token !== bootToken) return

  const fh2 = window.FH2 as unknown as CockpitFh2Api | undefined
  if (!fh2) {
    throw new Error(t('cockpit.sdkUnavailable'))
  }

  if (!document.getElementById('map-app-global')) {
    throw new Error('map-app mount failed!: #map-app-global missing')
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
    projectToken: cfg.userToken || '',
    ...(prjId ? { prjId } : {}),
  })
  fh2.initNotifyCenter(cfg.wssUrl, auth)
  await Promise.resolve(fh2.loadMap(cfg.serverHost, auth))
  if (!alive || token !== bootToken) {
    await resetFh2SharedMapContext()
    return
  }
  fh2.loadCockpit('cockpit-app-container', {
    gateway_sn: gatewaySn,
    drone_sn: droneSn,
    map: true,
  })
  status.value = t('cockpit.ready')
}

onMounted(() => {
  alive = true
  boot().catch((error: unknown) => {
    if (!alive) return
    status.value = error instanceof Error ? error.message : t('cockpit.loadFailed')
    ElMessage.error(status.value)
  })
})

onBeforeUnmount(() => {
  void teardown()
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
