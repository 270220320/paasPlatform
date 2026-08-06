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
import { purgeOrphanSdkContainers } from '@/utils/sdkContainers'

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
  const fh2 = (window as unknown as {
    FH2?: {
      mapAppState?: {
        getMapAppUntilReady?: (key?: string) => Promise<{
          recenterMap?: (positions: Array<{ longitude: number; latitude: number; height?: number }>, duration?: number) => Promise<unknown> | unknown
          initView?: (positions: Array<{ longitude: number; latitude: number; height?: number }>) => boolean
        } | null>
      }
    }
  }).FH2
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

async function persistWayline(id: string, name?: string) {
  if (!id) return
  await upsertFh2Wayline({
    waylineUuid: id,
    name: name?.trim() || props.waylineName || undefined,
    folderId: props.folderId,
  })
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
    const name = typeof payload === 'object' ? payload?.name : undefined
    activeWaylineId.value = id
    try {
      await persistWayline(id, name)
      statusText.value = t('waylineEditor.saved', { name: labelOf(id, name) })
      ElMessage.success(statusText.value)
      emit('saved', { waylineId: id, name: labelOf(id, name) })
    } catch {
      ElMessage.warning(t('waylineEditor.savedLocalSyncFailed'))
    }
  })
}

/** 按官方建议：切航线时销毁并重建编辑器实例 */
async function recreateEditor() {
  const cfg = await load()
  if (!alive || !cfg?.enabled || !window.WaylineEditor) return false

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
  await ensureMap()
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
  editor.render('edit', {
    waylineId: id,
    ...(withDefaultCenter ? { defaultPosition: defaultMapPosition() } : {}),
  })
  if (!alive || session !== editSession) return
  statusText.value = t('waylineEditor.editing', { name: labelOf(id, name) })
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
  destroyEditor()
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
</style>
