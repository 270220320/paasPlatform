<template>
  <div ref="rootEl" class="editor-pane">
    <div v-if="statusText" class="editor-status">{{ statusText }}</div>
    <div :key="domKey" class="fh2-container wayline-editor-root">
      <div id="wayline-header"></div>
      <div class="project-details">
        <div id="project-middle-container"></div>
        <div id="project-right-micro-app" class="right-micro-app">
          <div class="maps-micro-app">
            <div id="project-map-app-placeholder" class="map-app-placeholder">
              <div id="map-app-global" class="map-app-container"></div>
            </div>
          </div>
          <div id="wayline-app-container" ref="mountEl"></div>
        </div>
      </div>
    </div>
    <div v-if="!activeWaylineId && mode !== 'create'" class="editor-empty">
      {{ t('waylines.selectWaylineHint') }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { upsertFh2Wayline } from '@/api/paas'
import { usePaasBootstrap } from '@/composables/usePaasBootstrap'
import { loadPaasJs, loadScript, loadStylesheet } from '@/utils/loadScript'
import { purgeOrphanSdkContainers, purgeWaylineSdkOverlays, resetFh2SharedMapContext } from '@/utils/sdkContainers'
import { installWaylineRenameWatcher } from '@/utils/waylineNameSync'

declare global {
  interface Window {
    WaylineEditor?: new (opts: Record<string, unknown>) => WaylineEditorInstance
    URANUS_LANGUAGE?: unknown
  }
}

interface WaylineEditorInstance {
  mount: (el: HTMLElement) => void
  loadMap: () => Promise<void>
  render: (mode: string, opts?: Record<string, unknown>) => void
  create: (opts?: Record<string, unknown>) => Promise<{ waylineId: string; id?: string; name?: string }>
  destroy: () => void
  on: (event: string, handler: (...args: unknown[]) => void) => void
  save?: () => Promise<void>
}

const props = withDefaults(
  defineProps<{
    mode?: 'idle' | 'edit' | 'create'
    waylineId?: string
    waylineName?: string
    folderId?: number
  }>(),
  {
    mode: 'idle',
    waylineId: '',
    waylineName: '',
    folderId: undefined,
  },
)

const emit = defineEmits<{
  saved: [payload: { waylineId: string; name: string }]
  created: [payload: { waylineId: string; name: string }]
  /** 编辑器内改名（司空已生效）后本地同步完成 */
  renamed: [payload: { waylineId: string; name: string }]
}>()

const { t } = useI18n()
const { load, authConfig } = usePaasBootstrap()

/** 新建航线默认地图中心（天津演示区） */
const DEFAULT_WAYLINE_CENTER = {
  latitude: 39.06567939267821,
  longitude: 117.2186069043915,
  /** Cesium 相机高度（米），与 SDK 文档 defaultPosition 示例量级一致 */
  height: 1500,
}

const statusText = ref(t('waylineEditor.loadingEditor'))
const activeWaylineId = ref('')
const busy = ref(false)
const mountEl = ref<HTMLElement | null>(null)
const rootEl = ref<HTMLElement | null>(null)
const domKey = ref(0)
let editor: WaylineEditorInstance | null = null
let booted = false
let mapLoaded = false
let alive = true
let bootToken = 0
let editSession = 0
let resizeObserver: ResizeObserver | null = null
let resizeRaf = 0
let lastResizeKey = ''
let uninstallRenameWatcher: (() => void) | null = null
let lastSyncedName = ''
let renameSyncTimer: ReturnType<typeof setTimeout> | null = null

type CesiumLike = {
  resize?: () => void
  cesiumWidget?: { resize?: () => void }
  scene?: { requestRender?: () => void }
}

function getFh2Bridge() {
  return (window as unknown as {
    FH2?: {
      cesiumViewer?: Record<string, CesiumLike | undefined>
      mapAppState?: {
        getMapAppUntilReady?: (key?: string) => Promise<{
          resize?: () => void
          viewer?: CesiumLike
          recenterMap?: (
            positions: Array<{ longitude: number; latitude: number; height?: number }>,
            duration?: number,
          ) => Promise<unknown> | unknown
          initView?: (positions: Array<{ longitude: number; latitude: number; height?: number }>) => boolean
        } | null>
      }
    }
  }).FH2
}

/** 窗口/侧栏尺寸变化后，强制地图与编辑层按新容器重算 */
function notifyEditorResize() {
  if (!alive || !booted) return
  const root = rootEl.value
  if (!root) return
  const w = root.clientWidth
  const h = root.clientHeight
  if (w <= 0 || h <= 0) return
  const key = `${w}x${h}`
  if (key === lastResizeKey) return
  lastResizeKey = key

  try {
    const fh2 = getFh2Bridge()
    const viewers = fh2?.cesiumViewer
    if (viewers && typeof viewers === 'object') {
      for (const viewer of Object.values(viewers)) {
        viewer?.resize?.()
        viewer?.cesiumWidget?.resize?.()
        viewer?.scene?.requestRender?.()
      }
    }
    void fh2?.mapAppState?.getMapAppUntilReady?.('global')?.then((map) => {
      if (!alive || !map) return
      try {
        map.resize?.()
        map.viewer?.resize?.()
        map.viewer?.cesiumWidget?.resize?.()
        map.viewer?.scene?.requestRender?.()
      } catch {
        // ignore
      }
    })
  } catch {
    // ignore
  }
}

function scheduleEditorResize() {
  if (!alive) return
  if (resizeRaf) cancelAnimationFrame(resizeRaf)
  resizeRaf = requestAnimationFrame(() => {
    resizeRaf = 0
    // 再等一帧，让 flex 布局把新宽高写进 DOM
    requestAnimationFrame(() => {
      if (alive) notifyEditorResize()
    })
  })
}

function bindResizeObservers() {
  resizeObserver?.disconnect()
  resizeObserver = new ResizeObserver(() => scheduleEditorResize())
  if (rootEl.value) resizeObserver.observe(rootEl.value)
  const main = document.querySelector<HTMLElement>('.layout > .el-main.main')
  if (main) resizeObserver.observe(main)
  window.addEventListener('resize', scheduleEditorResize)
}

function unbindResizeObservers() {
  window.removeEventListener('resize', scheduleEditorResize)
  resizeObserver?.disconnect()
  resizeObserver = null
  if (resizeRaf) {
    cancelAnimationFrame(resizeRaf)
    resizeRaf = 0
  }
  lastResizeKey = ''
}

function labelOf(id: string, name?: string) {
  const n = name?.trim() || props.waylineName?.trim()
  if (n && n !== id) return n
  return n || t('waylines.unnamed')
}

function defaultMapPosition() {
  return {
    latitude: DEFAULT_WAYLINE_CENTER.latitude,
    longitude: DEFAULT_WAYLINE_CENTER.longitude,
    height: DEFAULT_WAYLINE_CENTER.height,
  }
}

/** centerPoint 只算默认飞行高度；地图视野需另行 recenter / defaultPosition */
async function flyMapToDefaultCenter() {
  const fh2 = getFh2Bridge()
  const mapAppState = fh2?.mapAppState
  if (!mapAppState?.getMapAppUntilReady) return

  const position = {
    longitude: DEFAULT_WAYLINE_CENTER.longitude,
    latitude: DEFAULT_WAYLINE_CENTER.latitude,
    height: DEFAULT_WAYLINE_CENTER.height,
  }

  for (let i = 0; i < 20; i += 1) {
    if (!alive) return
    try {
      const map = await mapAppState.getMapAppUntilReady('global')
      if (map?.recenterMap) {
        await map.recenterMap([position], 0)
        return
      }
      if (map?.initView) {
        map.initView([position])
        return
      }
    } catch {
      // 地图尚未就绪，短暂重试
    }
    await new Promise((r) => setTimeout(r, 150))
  }
}

function destroyEditor() {
  try {
    editor?.destroy()
  } catch {
    // ignore
  }
  editor = null
  booted = false
  // dom 骨架会重建，旧 map-app-global 失效，必须重新 loadMap
  mapLoaded = false
}

/** FH2 destroy 可能拆掉 Vue 管理的节点，递增 key 强制重建挂载骨架 */
async function rebuildMountDom() {
  destroyEditor()
  domKey.value += 1
  await nextTick()
  for (let i = 0; i < 30; i += 1) {
    if (!alive) return null
    if (mountEl.value) return mountEl.value
    await nextTick()
    await new Promise((r) => setTimeout(r, 30))
  }
  return mountEl.value
}

/**
 * 回写本地航线镜像。
 * 有新名称则直写；无名称则不传 name，由后端从司空拉取，避免用 props 旧名覆盖。
 */
async function persistWayline(id: string, name?: string) {
  if (!id) return null
  const fresh = name?.trim()
  const { data } = await upsertFh2Wayline({
    waylineUuid: id,
    name: fresh || undefined,
    folderId: props.folderId,
  })
  const synced = data.data?.name?.trim() || fresh || ''
  if (synced) lastSyncedName = synced
  return data.data || null
}

async function syncRenameFromEditor(waylineUuid: string, name: string) {
  if (!alive || !waylineUuid || !name.trim()) return
  if (activeWaylineId.value && activeWaylineId.value !== waylineUuid) return
  if (lastSyncedName === name.trim()) return
  try {
    const row = await persistWayline(waylineUuid, name)
    if (!alive || !row) return
    const synced = row.name?.trim() || name.trim()
    emit('renamed', { waylineId: waylineUuid, name: synced })
  } catch (e) {
    console.warn('[WaylineEditor] rename local sync failed', e)
  }
}

function onEditorError(e: unknown) {
  console.error('[WaylineEditor]', e)
  const err = e as { code?: number; message?: string }
  const map: Record<number, string> = {
    4001: t('waylineEditor.wsAuthFailed'),
    4002: t('waylineEditor.wsDisconnected'),
    5000: t('waylineEditor.apiRequestFailed'),
    5001: t('waylineEditor.apiAuthFailed'),
    6000: t('waylineEditor.waylineParseFailed'),
    6001: t('waylineEditor.wasmLoadFailed'),
    6002: t('waylineEditor.mapInitFailed'),
    7000: t('waylineEditor.ossUploadFailed'),
  }
  const label = err?.code != null ? map[err.code] || '' : ''
  statusText.value = `${label || t('waylineEditor.error')} ${err?.code ?? ''}：${err?.message || ''}`
  ElMessage.error(statusText.value)
}

function bindEditorEvents(instance: WaylineEditorInstance) {
  instance.on('error', onEditorError)
  instance.on('wayline-save', async (...args: unknown[]) => {
    if (!alive) return
    const payload = args[0] as { waylineId?: string; id?: string; name?: string } | string
    const id =
      typeof payload === 'object' && payload
        ? payload.waylineId || payload.id || activeWaylineId.value
        : String(payload || activeWaylineId.value)
    // 官方事件多为纯 id 字符串，勿回退 props 旧名
    const name = typeof payload === 'object' && payload ? payload.name : undefined
    activeWaylineId.value = id
    try {
      const row = await persistWayline(id, name)
      const syncedName = row?.name?.trim() || name?.trim() || lastSyncedName || props.waylineName
      statusText.value = t('waylineEditor.saved', { name: labelOf(id, syncedName) })
      ElMessage.success(statusText.value)
      emit('saved', { waylineId: id, name: labelOf(id, syncedName) })
    } catch {
      ElMessage.warning(t('waylineEditor.savedLocalSyncFailed'))
    }
  })
}

/** 按官方建议：切航线时销毁并重建编辑器实例 */
async function recreateEditor() {
  const cfg = await load()
  if (!alive || !cfg?.enabled || !window.WaylineEditor) return false

  // 驾驶舱/设备管理离开后可能残留 mapAppState 与游离 #map-app-global
  await resetFh2SharedMapContext(rootEl.value)
  if (!alive) return false

  const container = await rebuildMountDom()
  if (!alive || !container) return false

  purgeOrphanSdkContainers(rootEl.value)

  const auth = authConfig(cfg)
  const instance = new window.WaylineEditor({
    hostUrl: cfg.frontHost,
    api: {
      baseUrl: cfg.gatewayBase,
      auth,
    },
    ws: {
      url: cfg.wssUrl,
    },
    locale: { language: 'zh', messages: window.URANUS_LANGUAGE },
  })
  editor = instance
  bindEditorEvents(instance)

  const mountTarget = mountEl.value || container
  instance.mount(mountTarget)
  booted = true
  mapLoaded = false
  await ensureMap()
  lastResizeKey = ''
  scheduleEditorResize()
  return true
}

async function ensureMap() {
  if (!editor || mapLoaded) return
  statusText.value = t('waylineEditor.loadingMap')
  await editor.loadMap()
  mapLoaded = true
}

function getSdkEditorModeRef(): { value: string | null } | null {
  const modeRef = (editor as WaylineEditorInstance & { editorMode?: { value: unknown } } | null)?.editorMode
  if (!modeRef || typeof modeRef !== 'object' || !('value' in modeRef)) return null
  return modeRef as { value: string | null }
}

/** SDK Edit 仅在 mode 切入 edit 时挂载，且 setup 只读一次 waylineId，切航线必须先卸再挂 */
async function remountEditSurface() {
  const modeRef = getSdkEditorModeRef()
  if (modeRef) modeRef.value = null
  activeWaylineId.value = ''
  await nextTick()
  await nextTick()
}

async function enterEditMode(id: string, name?: string, withDefaultCenter = false) {
  if (!id) return
  const session = ++editSession
  const switching = Boolean(activeWaylineId.value && activeWaylineId.value !== id)

  // 切到其它航线：销毁重建（SDK 文档要求），否则 Edit 不重载、地图仍是旧航线
  if (switching) {
    statusText.value = t('waylineEditor.loadingEditor')
    const ok = await recreateEditor()
    if (!alive || session !== editSession || !ok || !editor) return
  } else {
    if (!editor) return
    await ensureMap()
    if (!alive || session !== editSession) return
    if (getSdkEditorModeRef()?.value === 'edit') {
      await remountEditSurface()
      if (!alive || session !== editSession || !editor) return
    }
  }

  activeWaylineId.value = id
  lastSyncedName = name?.trim() || props.waylineName?.trim() || lastSyncedName
  editor.render('edit', {
    waylineId: id,
    ...(withDefaultCenter ? { defaultPosition: defaultMapPosition() } : {}),
  })
  if (!alive || session !== editSession) return
  statusText.value = t('waylineEditor.editing', { name: labelOf(id, name) })
  lastResizeKey = ''
  scheduleEditorResize()
}

async function startCreate() {
  if (!editor || !booted) {
    ElMessage.warning(t('waylineEditor.editorNotReady'))
    return
  }
  if (busy.value) return
  busy.value = true
  try {
    await ensureMap()
    // create() 同步切到 create 模式；centerPoint 只影响默认飞行高度，不负责地图居中
    const createPromise = editor.create({
      centerPoint: {
        latitude: DEFAULT_WAYLINE_CENTER.latitude,
        longitude: DEFAULT_WAYLINE_CENTER.longitude,
      },
    })
    await nextTick()
    await flyMapToDefaultCenter()
    // 创建弹层/地图初始化可能稍后覆盖视角，再补一次
    window.setTimeout(() => {
      if (alive) void flyMapToDefaultCenter()
    }, 600)

    const res = await createPromise
    const id = res.waylineId || res.id || ''
    const name = res.name || ''
    activeWaylineId.value = id
    try {
      await persistWayline(id, name)
      ElMessage.success(t('waylineEditor.created', { name: labelOf(id, name) }))
    } catch {
      ElMessage.warning(t('waylineEditor.createdLocalSyncFailed'))
    }
    emit('created', { waylineId: id, name: labelOf(id, name) })
    await enterEditMode(id, name, true)
    await nextTick()
    await flyMapToDefaultCenter()
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : String(e)
    if (msg === 'User cancelled') {
      statusText.value = t('waylineEditor.createCancelled')
      return
    }
    console.error('[create]', e)
    ElMessage.error(msg || t('waylineEditor.createFailed'))
    statusText.value = msg || t('waylineEditor.createFailed')
  } finally {
    busy.value = false
  }
}

async function applyTarget() {
  if (!booted || !editor || !alive) return
  if (props.mode === 'create') {
    await startCreate()
    return
  }
  if (props.mode === 'edit' && props.waylineId) {
    if (activeWaylineId.value === props.waylineId && getSdkEditorModeRef()?.value === 'edit') return
    await enterEditMode(props.waylineId, props.waylineName)
    return
  }
  // idle：卸掉 SDK 编辑态，避免右侧仍停在上一条航线
  editSession += 1
  await remountEditSurface()
  statusText.value = t('waylines.selectWaylineHint')
}

async function boot() {
  const token = ++bootToken
  // 先进驾驶舱再进航线库时，必须先卸掉共享地图；保留本面板内骨架节点
  await resetFh2SharedMapContext(rootEl.value)
  if (!alive || token !== bootToken) return

  const cfg = await load()
  if (!alive || token !== bootToken) return
  if (!cfg?.enabled) {
    statusText.value = t('waylineEditor.paasDisabled')
    return
  }
  if (!cfg.userToken) {
    statusText.value = t('waylineEditor.noUserToken')
    ElMessage.error(statusText.value)
    return
  }

  statusText.value = t('waylineEditor.loadingPaas')
  await loadPaasJs(cfg.frontHost)
  if (!alive || token !== bootToken) return
  await loadStylesheet(cfg.waylineSdkCss, 'wayline-edit-sdk-css')
  await loadScript(cfg.waylineSdkJs)
  if (!alive || token !== bootToken) return

  if (!window.WaylineEditor) {
    statusText.value = t('waylineEditor.waylineEditorMissing')
    ElMessage.error(statusText.value)
    return
  }

  const ok = await recreateEditor()
  if (!alive || token !== bootToken) return
  if (!ok) {
    statusText.value = t('waylineEditor.mountMissing')
    ElMessage.error(statusText.value)
    return
  }

  statusText.value = t('waylines.selectWaylineHint')
  await applyTarget()
}

watch(
  () => [props.mode, props.waylineId, props.folderId] as const,
  () => {
    applyTarget().catch((e: unknown) => {
      console.error(e)
      statusText.value = e instanceof Error ? e.message : t('waylineEditor.loadFailed')
    })
  },
)

watch(
  () => props.mode,
  (mode) => {
    if (mode !== 'edit' && mode !== 'create') {
      activeWaylineId.value = ''
    }
  },
)

onMounted(() => {
  alive = true
  lastSyncedName = props.waylineName?.trim() || ''
  uninstallRenameWatcher = installWaylineRenameWatcher(({ waylineUuid, name }) => {
    if (renameSyncTimer) clearTimeout(renameSyncTimer)
    renameSyncTimer = setTimeout(() => {
      void syncRenameFromEditor(waylineUuid, name)
    }, 80)
  })
  bindResizeObservers()
  boot().catch((e: unknown) => {
    if (!alive) return
    console.error(e)
    statusText.value = e instanceof Error ? e.message : t('waylineEditor.loadFailed')
    ElMessage.error(statusText.value)
  })
})

onBeforeUnmount(() => {
  alive = false
  bootToken += 1
  editSession += 1
  if (renameSyncTimer) {
    clearTimeout(renameSyncTimer)
    renameSyncTimer = null
  }
  uninstallRenameWatcher?.()
  uninstallRenameWatcher = null
  unbindResizeObservers()
  destroyEditor()
  void resetFh2SharedMapContext()
})

defineExpose({ startCreate, enterEditMode })
</script>

<style scoped lang="scss">
.editor-pane {
  position: relative;
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #0a0a0a;
  overflow: hidden;
}

.editor-status {
  position: absolute;
  top: 10px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 5;
  max-width: 70%;
  padding: 6px 12px;
  border-radius: 6px;
  background: rgba(20, 20, 20, 0.82);
  color: #a3a6ad;
  font-size: 12px;
  pointer-events: none;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.editor-empty {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: grid;
  place-items: center;
  color: #8b919a;
  font-size: 14px;
  background: rgba(10, 10, 10, 0.35);
  pointer-events: none;
}

.wayline-editor-root.fh2-container {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  width: 100%;
  height: 100%;
}
</style>

<style lang="scss">
.wayline-editor-root .map-app-container > div {
  width: 100%;
  height: 100%;
}

.wayline-editor-root .project-details {
  position: relative;
  width: 100%;
  display: flex;
  flex: 1;
  overflow: hidden;
  min-height: 0;
}

.wayline-editor-root .project-details .right-micro-app {
  position: relative;
  display: flex;
  flex: 1;
}

.wayline-editor-root .project-details .right-micro-app .maps-micro-app {
  display: flex;
  flex: 1;
  height: 100%;
}

.wayline-editor-root .project-details .right-micro-app .map-app-placeholder {
  flex: 1;
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100%;
}

.wayline-editor-root .project-details .right-micro-app .map-app-container {
  height: 100%;
  min-height: 0;
}

.wayline-editor-root .project-details .right-micro-app #wayline-app-container {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

/* Cesium 默认 canvas 尺寸不会随 flex 容器变化，强制铺满后配合 resize() */
#map-app-global,
#map-app-global .cesium-viewer,
#map-app-global .cesium-viewer-cesiumWidgetContainer,
#map-app-global .cesium-widget,
#map-app-global .cesium-widget canvas {
  width: 100% !important;
  height: 100% !important;
  max-width: 100%;
  max-height: 100%;
}
</style>
