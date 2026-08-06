<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import ZYWLGISMAP from 'zywlgis'
import 'zywlgis/release/zywlgis.css'
import { fetchCockpitDevices } from '@/api/paas'
import {
  createSafetyArea,
  deleteSafetyArea,
  fetchSafetySyncStatus,
  getSyncStatusDeviceSn,
  listSafetyAreas,
  mapSafetySyncStatus,
  safetyAreaToZone,
  syncSafetyAreas,
  updateSafetyArea,
  zoneToSaveBody,
} from '@/api/safety'
import { usePaasBootstrap } from '@/composables/usePaasBootstrap'
import { ensureGisDependencies, ensureGisI18n } from '@/utils/loadGisDeps'
import {
  computeZoneHash,
  resolveDeviceSyncList,
  SYNC_STATUS_LABEL,
  SYNC_STATUS_TAG_TYPE,
  upsertDeviceSync,
  type DeviceFlightAreaSync,
} from '@/utils/noflySyncStorage'
import {
  createNoFlyZone,
  endZoneEdit,
  loadNoFlyZones,
  saveNoFlyZones,
  startZoneEdit,
  type NoFlyZoneRecord,
  type NoFlyZoneType,
} from '@/utils/noflyZoneStorage'

const MAP_ID = 'airspace-nofly-map'
const { load: loadPaasBootstrap } = usePaasBootstrap()

/** zywlgis 组件实例（Options API），用于兜底调用 setMapEngineAction */
const gisRef = ref<{
  setMapEngineAction?: () => void
  _maptalksEventsInitialized?: boolean
} | null>(null)

const gisReady = ref(false)
const gisError = ref('')
const mapReady = ref(false)
const editingId = ref('')
const zonesLoading = ref(false)
const zones = ref<NoFlyZoneRecord[]>([])
/** 最近一次从后台拉到的已保存版本，用于取消编辑还原 */
const savedZonesSnapshot = ref<NoFlyZoneRecord[]>([])

const devicesLoading = ref(false)
const syncing = ref(false)
const syncDialogVisible = ref(false)
const selectedDeviceSns = ref<string[]>([])
const deviceSyncList = ref<DeviceFlightAreaSync[]>([])
const deviceOptions = ref<Array<{ sn: string; name: string }>>([])
/** 是否已成功用 /safety/syncstatus 刷新过状态 */
const hasServerSyncStatus = ref(false)

let statusPollTimer: ReturnType<typeof setInterval> | null = null

const center = {
  longitude: 117.218304823,
  latitude: 39.065805470,
}

const MapEngine = reactive({
  containerID: MAP_ID,
  force2D: true,
  isCesium: false,
  isOpenSlider: false,
  trackStats: false,
  center: {
    longitude: center.longitude,
    latitude: center.latitude,
    altitude: 1200,
  },
  zoom: {
    current: 14,
    max: 20,
    min: 1,
  },
  camera: { heading: 0, pitch: -90 },
  headers: {},
})

const MapConfiguration = reactive({
  isAR: false,
  isFirstLoadAnimation: false,
  isDrag: true,
  isZoomInCenter: false,
})

const MapPlugins = reactive({
  iconType: 'default',
  iconScale: 0.8,
  stateBarScale: 0.9,
  layerManagementVisible: false,
  videoProjectionVisible: false,
  pointOfInterestVisible: false,
  zoomOutVisible: true,
  zoomInVisible: true,
  mapModeVisible: false,
  rangingToolVisible: true,
  compassNeedleVisible: false,
  stateBarVisible: true,
  tileLoadProgressVisible: false,
  keyboardTipsVisible: false,
  style: { right: 12, left: 12 },
})

/** 卫星影像底图（与首页一致；WGS84，勿用高德 GCJ-02，会与大疆坐标偏移） */
const tileUrl =
  'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}'

const MapLayers = reactive([
  {
    ID: 'BaseLayer',
    mapMode: 0,
    urlTemplate: tileUrl,
    visible: true,
    zIndex: 1,
    max: 20,
    min: 1,
    opacity: 1,
  },
])

const ActiveContainerIDs = reactive([MAP_ID])

const CustomNoFlyZones = computed(() => zones.value)

const editingZone = computed(() => zones.value.find((z) => z.ID === editingId.value) || null)
const isEditing = computed(() => Boolean(editingId.value))

const syncSummary = computed(() => {
  const list = deviceSyncList.value
  if (!list.length) return '暂无设备'
  const synced = list.filter((d) => d.syncStatus === 'synchronized').length
  const pending = list.filter((d) => d.syncStatus === 'wait_sync' || d.syncStatus === 'fail').length
  const running = list.filter((d) => d.syncStatus === 'synchronizing').length
  if (running) return `${running} 台同步中`
  if (pending) return `${pending} 台待同步 · ${synced} 台已同步`
  return `${synced} 台已同步`
})

const canOpenSync = computed(() => !isEditing.value && deviceOptions.value.length > 0)

/** 刷新侧边设备同步状态列表 */
function refreshDeviceSyncList() {
  deviceSyncList.value = resolveDeviceSyncList(deviceOptions.value, zones.value, {
    preferServer: hasServerSyncStatus.value,
  })
}

/** 缓存到 localStorage，并刷新同步状态展示 */
function cacheZonesLocally() {
  saveNoFlyZones(zones.value.map((z) => (z.ID === editingId.value ? z : endZoneEdit(z))))
  refreshDeviceSyncList()
}

/** 按 ID 替换或合并更新一条限飞区记录 */
function replaceZone(id: string, patch: Partial<NoFlyZoneRecord> | ((zone: NoFlyZoneRecord) => NoFlyZoneRecord)) {
  const index = zones.value.findIndex((z) => z.ID === id)
  if (index < 0) return
  const current = zones.value[index]
  const next = typeof patch === 'function' ? patch(current) : { ...current, ...patch }
  const list = zones.value.slice()
  list[index] = next
  zones.value = list
}

/** 判断是否为尚未落库的草稿（本地临时 ID） */
function isDraftZone(zone: NoFlyZoneRecord): boolean {
  return !savedZonesSnapshot.value.some((item) => item.ID === zone.ID)
}

/** 从本地 Java 服务拉取限飞区并渲染到地图 */
async function loadZones() {
  zonesLoading.value = true
  try {
    const result = await listSafetyAreas()
    const mapped = (result.list || [])
      .map(safetyAreaToZone)
      .filter((item): item is NoFlyZoneRecord => Boolean(item))
    zones.value = mapped
    savedZonesSnapshot.value = mapped.map((z) => endZoneEdit({ ...z, coordinates: [...(z.coordinates || [])] }))
    cacheZonesLocally()
  } catch (error: unknown) {
    const cached = loadNoFlyZones()
    if (cached.length) {
      zones.value = cached
      savedZonesSnapshot.value = cached.map((z) => endZoneEdit({ ...z }))
      ElMessage.warning((error as Error)?.message || '限飞区列表加载失败，已使用本地缓存')
    } else {
      zones.value = []
      savedZonesSnapshot.value = []
      ElMessage.error((error as Error)?.message || '限飞区列表加载失败')
    }
  } finally {
    zonesLoading.value = false
    refreshDeviceSyncList()
  }
}

/** 新建限飞区并进入地图绘制/编辑态；type=3 多边形，type=4 圆形 */
function beginCreate(type: NoFlyZoneType) {
  if (isEditing.value) {
    ElMessage.warning('请先完成当前限飞区的保存或取消')
    return
  }
  const zone = startZoneEdit(createNoFlyZone(type, `${type === 4 ? '圆形' : '多边形'}限飞区_${zones.value.length + 1}`))
  zones.value = [...zones.value, zone]
  editingId.value = zone.ID
  ElMessage.success(type === 4 ? '请在地图上点击圆心并拖出半径' : '请在地图上依次点击绘制多边形边界')
}

/** 进入已有限飞区的编辑模式 */
function beginEdit(zone: NoFlyZoneRecord) {
  if (editingId.value === zone.ID) return
  if (isEditing.value) {
    ElMessage.warning('请先完成当前限飞区的保存或取消')
    return
  }
  replaceZone(zone.ID, startZoneEdit(zone))
  editingId.value = zone.ID
  ElMessage.info('已进入编辑模式，可拖动节点调整范围')
}

/** 校验限飞区几何与名称，通过返回 null，失败返回错误文案 */
function validateZone(zone: NoFlyZoneRecord): string | null {
  const coords = zone.coordinates || []
  if (zone.type === 3 && coords.length < 3) return '多边形限飞区至少需要 3 个点'
  if (zone.type === 4 && coords.length < 1) return '圆形限飞区至少需要 1 个中心点'
  if (!zone.customLabel?.content?.trim() && !zone.name?.trim()) return '请填写限飞区名称'
  return null
}

/** 校验并保存当前编辑中的限飞区（新建走 create，已有走 update） */
async function saveEditing() {
  const zone = editingZone.value
  if (!zone) return
  const error = validateZone(zone)
  if (error) {
    ElMessage.warning(error)
    return
  }

  const name = zone.customLabel?.content?.trim() || zone.name
  const prepared = endZoneEdit({
    ...zone,
    name,
    customLabel: { ...zone.customLabel, content: name, show: true },
  })

  try {
    if (isDraftZone(zone)) {
      const flightAreaId = await createSafetyArea(zoneToSaveBody(prepared))
      replaceZone(zone.ID, { ...prepared, ID: flightAreaId })
    } else {
      await updateSafetyArea(zone.ID, zoneToSaveBody(prepared))
      replaceZone(zone.ID, prepared)
    }
    editingId.value = ''
    savedZonesSnapshot.value = zones.value.map((z) => endZoneEdit({
      ...z,
      coordinates: [...(z.coordinates || [])],
      symbol: { ...z.symbol },
      customLabel: { ...z.customLabel },
    }))
    cacheZonesLocally()
    hasServerSyncStatus.value = false
    refreshDeviceSyncList()
    ElMessage.success('限飞区已保存')
  } catch (err: unknown) {
    ElMessage.error((err as Error)?.message || '保存失败')
  }
}

/** 取消编辑：草稿直接删除，已有项恢复为快照版本 */
function cancelEditing() {
  const zone = editingZone.value
  if (!zone) return
  if (isDraftZone(zone)) {
    zones.value = zones.value.filter((z) => z.ID !== zone.ID)
  } else {
    const saved = savedZonesSnapshot.value.find((z) => z.ID === zone.ID)
    if (saved) replaceZone(zone.ID, endZoneEdit({ ...saved, coordinates: [...(saved.coordinates || [])] }))
    else replaceZone(zone.ID, endZoneEdit(zone))
  }
  editingId.value = ''
  cacheZonesLocally()
}

/** 确认后删除指定限飞区（已落库则调后台删除） */
function removeZone(zone: NoFlyZoneRecord) {
  ElMessageBox.confirm(`确定删除「${zone.customLabel?.content || zone.name}」吗？`, '删除限飞区', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  }).then(async () => {
    try {
      if (!isDraftZone(zone)) {
        await deleteSafetyArea(zone.ID)
      }
      if (editingId.value === zone.ID) editingId.value = ''
      zones.value = zones.value.filter((z) => z.ID !== zone.ID)
      savedZonesSnapshot.value = savedZonesSnapshot.value.filter((z) => z.ID !== zone.ID)
      cacheZonesLocally()
      hasServerSyncStatus.value = false
      refreshDeviceSyncList()
      ElMessage.success('已删除')
    } catch (err: unknown) {
      ElMessage.error((err as Error)?.message || '删除失败')
    }
  }).catch(() => {})
}

/** 仅在引擎未绑定时兜底补绑（对齐 paasVue；避免重复 on('click') 导致绘制错乱） */
function bindMapInteractionsIfNeeded() {
  ensureGisI18n()
  const map = (window as unknown as {
    mapEngines?: Map<string, { _eventMap?: { click?: unknown[] } }>
  }).mapEngines?.get(`${MAP_ID}-maptalks`)
  if (map?._eventMap?.click?.length) return

  const gis = gisRef.value
  if (!gis?.setMapEngineAction) return
  gis._maptalksEventsInitialized = false
  try {
    gis.setMapEngineAction()
  } catch (error) {
    console.warn('[airspace] setMapEngineAction failed', error)
  }
}

function ensureBaseLayers() {
  const map = (window as unknown as {
    mapEngines?: Map<string, { getLayer?: (id: string) => unknown; checkSize?: () => void }>
  }).mapEngines?.get(`${MAP_ID}-maptalks`)
  if (!map) return
  // 已有底图则不再触发 MapLayers 刷新，避免干扰绘制中的图形
  if (map.getLayer?.('group-tiles')) {
    map.checkSize?.()
    return
  }
  if (!MapLayers.length) return
  const next = MapLayers.map((layer) => ({ ...layer }))
  MapLayers.splice(0, MapLayers.length, ...next)
  map.checkSize?.()
}

/** 地图初始化完成回调（对齐 paasVue，并兜底底图/交互） */
function onMapInitCompleted() {
  mapReady.value = true
  nextTick(() => {
    ensureGisI18n()
    ensureBaseLayers()
    bindMapInteractionsIfNeeded()
  })
}

/** 点击地图上限飞区时进入对应编辑态（引擎仅在非编辑态图形上触发） */
function onGraphClick(payload: { ID?: string; class?: string; data?: NoFlyZoneRecord }) {
  if (payload?.class && payload.class !== 'CustomNoFlyZones') return
  const id = payload.ID || payload.data?.ID
  if (!id) return
  const zone = zones.value.find((z) => z.ID === id)
  if (!zone) return
  beginEdit(zone)
}

/** 同步地图图形坐标/半径变更到本地限飞区数据 */
function onGraphChanged(payload: { ID?: string; class?: string; coordinates?: NoFlyZoneRecord['coordinates']; data?: NoFlyZoneRecord }) {
  if (payload?.class && payload.class !== 'CustomNoFlyZones') return
  const id = payload.ID
  if (!id) return
  replaceZone(id, (zone) => {
    const coordinates = payload.coordinates || payload.data?.coordinates || zone.coordinates
    const radius = (payload.data as { symbol?: { radius?: number } } | undefined)?.symbol?.radius
    return {
      ...zone,
      coordinates: coordinates || [],
      symbol: {
        ...zone.symbol,
        ...(radius !== undefined ? { radius } : {}),
      },
    }
  })
}

/** 同步地图图形整体编辑结果（含标签等字段） */
function onGraphEdited(payload: { ID?: string; class?: string; data?: NoFlyZoneRecord }) {
  if (payload?.class && payload.class !== 'CustomNoFlyZones') return
  const id = payload.ID
  if (!id || !payload.data) return
  replaceZone(id, (zone) => ({
    ...zone,
    ...payload.data,
    ID: id,
    customLabel: {
      ...zone.customLabel,
      ...(payload.data?.customLabel || {}),
      content: payload.data?.customLabel?.content || zone.customLabel.content,
    },
  }))
}

/** 地图上删除图形时同步移除本地记录（已落库则调后台） */
async function onGraphRemoved(payload: { ID?: string; class?: string }) {
  if (payload?.class && payload.class !== 'CustomNoFlyZones') return
  const id = payload.ID
  if (!id) return
  const zone = zones.value.find((z) => z.ID === id)
  if (editingId.value === id) editingId.value = ''
  try {
    if (zone && !isDraftZone(zone)) await deleteSafetyArea(id)
  } catch (err: unknown) {
    ElMessage.error((err as Error)?.message || '后台删除失败')
    await loadZones()
    return
  }
  zones.value = zones.value.filter((z) => z.ID !== id)
  savedZonesSnapshot.value = savedZonesSnapshot.value.filter((z) => z.ID !== id)
  cacheZonesLocally()
}

/** 更新当前编辑限飞区的显示名称 */
function updateEditingName(name: string) {
  const zone = editingZone.value
  if (!zone) return
  zone.name = name
  zone.customLabel = { ...zone.customLabel, content: name, show: true }
}

/** 用 /safety/syncstatus 刷新各设备同步状态 */
async function pullSafetySyncStatus() {
  const sns = deviceOptions.value.map((d) => d.sn).filter(Boolean)
  if (!sns.length) {
    refreshDeviceSyncList()
    return
  }
  try {
    const list = await fetchSafetySyncStatus(sns)
    hasServerSyncStatus.value = true
    const hash = computeZoneHash(zones.value)
    for (const item of list) {
      const sn = getSyncStatusDeviceSn(item)
      if (!sn) continue
      const syncStatus = mapSafetySyncStatus(item.syncStatus)
      const device = deviceOptions.value.find((d) => d.sn === sn)
      upsertDeviceSync({
        deviceSn: sn,
        nickname: device?.name || item.nickname || sn,
        syncStatus,
        syncMsg: item.syncMsg || '',
        zoneHash: syncStatus === 'synchronized' ? hash : undefined,
      })
    }
    refreshDeviceSyncList()
  } catch (error: unknown) {
    hasServerSyncStatus.value = false
    refreshDeviceSyncList()
    // 首次失败不打断；手动刷新时可提示
    console.warn('[airspace] syncstatus failed', error)
  }
}

/** 加载设备管理列表，并查询同步状态 */
async function loadDevices() {
  devicesLoading.value = true
  try {
    const list = await fetchCockpitDevices()
    deviceOptions.value = list
      .map((item) => ({
        sn: item.gatewaySn || item.sn || '',
        name: item.name || item.gatewaySn || item.sn || '',
      }))
      .filter((item) => item.sn)
    refreshDeviceSyncList()
    await pullSafetySyncStatus()
  } catch (error: unknown) {
    ElMessage.error((error as Error)?.message || '设备列表加载失败')
    deviceOptions.value = []
    deviceSyncList.value = []
  } finally {
    devicesLoading.value = false
  }
}

/** 打开「同步到设备」对话框 */
function openSyncDialog() {
  if (isEditing.value) {
    ElMessage.warning('请先完成当前限飞区的保存或取消')
    return
  }
  if (!deviceOptions.value.length) {
    ElMessage.warning('暂无可用设备，请先在设备管理中绑定机场')
    return
  }
  const pending = deviceSyncList.value
    .filter((d) => d.syncStatus !== 'synchronized' && d.syncStatus !== 'synchronizing')
    .map((d) => d.deviceSn)
  selectedDeviceSns.value = pending.length ? pending : deviceOptions.value.map((d) => d.sn)
  syncDialogVisible.value = true
}

/** 确认将当前限飞区同步到勾选设备（/safety/sync） */
async function confirmSyncToDevices() {
  const selected = deviceOptions.value.filter((d) => selectedDeviceSns.value.includes(d.sn))
  if (!selected.length) {
    ElMessage.warning('请选择需要同步的设备')
    return
  }
  syncing.value = true
  const hash = computeZoneHash(zones.value)
  for (const device of selected) {
    upsertDeviceSync({
      deviceSn: device.sn,
      nickname: device.name,
      syncStatus: 'synchronizing',
      syncMsg: '正在下发到设备…',
    })
  }
  refreshDeviceSyncList()

  try {
    const results = await syncSafetyAreas(
      selected.map((d) => d.sn),
      Object.fromEntries(selected.map((d) => [d.sn, d.name])),
    )
    for (const item of results) {
      const sn = getSyncStatusDeviceSn(item)
      if (!sn) continue
      const mapped = mapSafetySyncStatus(item.syncStatus)
      upsertDeviceSync({
        deviceSn: sn,
        nickname: selected.find((d) => d.sn === sn)?.name || sn,
        syncStatus: mapped === 'unknown' ? 'wait_sync' : mapped,
        syncMsg: item.syncMsg || (mapped === 'synchronized' ? '同步成功' : '已下发'),
        zoneHash: mapped === 'synchronized' ? hash : undefined,
      })
    }
    for (const device of selected) {
      const current = deviceSyncList.value.find((d) => d.deviceSn === device.sn)
      if (!current || current.syncStatus === 'synchronizing') {
        upsertDeviceSync({
          deviceSn: device.sn,
          nickname: device.name,
          syncStatus: 'wait_sync',
          syncMsg: '已下发，等待确认',
        })
      }
    }
    refreshDeviceSyncList()
    syncDialogVisible.value = false
    ElMessage.success('已下发同步指令，请关注设备同步状态')
    await pullSafetySyncStatus()
    startStatusPolling()
  } catch (error: unknown) {
    for (const device of selected) {
      upsertDeviceSync({
        deviceSn: device.sn,
        nickname: device.name,
        syncStatus: 'fail',
        syncMsg: (error as Error)?.message || '同步失败',
      })
    }
    refreshDeviceSyncList()
    ElMessage.error((error as Error)?.message || '同步失败')
  } finally {
    syncing.value = false
  }
}

/** 同步进行中时轮询设备同步状态 */
function startStatusPolling() {
  stopStatusPolling()
  statusPollTimer = setInterval(async () => {
    await pullSafetySyncStatus()
    const running = deviceSyncList.value.some(
      (d) => d.syncStatus === 'synchronizing' || d.syncStatus === 'wait_sync',
    )
    if (!running) stopStatusPolling()
  }, 3000)
}

/** 停止设备同步状态轮询 */
function stopStatusPolling() {
  if (statusPollTimer) {
    clearInterval(statusPollTimer)
    statusPollTimer = null
  }
}

watch(editingId, async () => {
  await nextTick()
})

onMounted(async () => {
  try {
    await loadPaasBootstrap()
  } catch {
    // token 可能已在别处写入
  }
  try {
    await ensureGisDependencies()
    gisReady.value = true
  } catch (error: unknown) {
    gisError.value = (error as Error)?.message || 'Cesium 依赖加载失败'
    ElMessage.error(gisError.value)
  }
  await loadZones()
  loadDevices()
})

onBeforeUnmount(() => {
  stopStatusPolling()
  if (editingId.value) cancelEditing()
})
</script>

<template>
  <section class="component-page airspace-page">
    <div class="page-toolbar">
      <div>
        <strong>空域安全</strong>
        <small>限飞区存本地库；同步时推送司空并下发机场</small>
      </div>
      <div class="airspace-actions">
        <el-button type="danger" plain :disabled="isEditing || !mapReady || zonesLoading" @click="beginCreate(3)">
          新增多边形限飞区
        </el-button>
        <el-button type="danger" plain :disabled="isEditing || !mapReady || zonesLoading" @click="beginCreate(4)">
          新增圆形限飞区
        </el-button>
        <el-button type="primary" :disabled="!canOpenSync || syncing" @click="openSyncDialog">
          同步到设备
        </el-button>
        <el-button :loading="zonesLoading" @click="loadZones">刷新列表</el-button>
      </div>
    </div>

    <div class="airspace-body">
      <aside class="airspace-side">
        <div class="airspace-side-title">限飞区列表</div>
        <div v-if="zonesLoading && !zones.length" class="empty-state">正在加载限飞区…</div>
        <div v-else-if="!zones.length" class="empty-state">暂无限飞区，点击上方按钮开始绘制。</div>
        <article
          v-for="zone in zones"
          :key="zone.ID"
          class="airspace-zone-item"
          :class="{ active: zone.ID === editingId }"
        >
          <div class="airspace-zone-meta">
            <strong>{{ zone.customLabel?.content || zone.name }}</strong>
            <small>{{ zone.type === 4 ? '圆形' : '多边形' }} · {{ zone.coordinates?.length || 0 }} 点</small>
          </div>
          <div class="airspace-zone-btns">
            <el-button size="small" :disabled="isEditing && editingId !== zone.ID" @click="beginEdit(zone)">编辑</el-button>
            <el-button size="small" type="danger" plain :disabled="isEditing && editingId !== zone.ID" @click="removeZone(zone)">删除</el-button>
          </div>
        </article>

        <div v-if="editingZone" class="airspace-edit-panel">
          <div class="airspace-side-title">正在编辑</div>
          <el-form label-position="top" size="small">
            <el-form-item label="名称">
              <el-input
                :model-value="editingZone.customLabel?.content || editingZone.name"
                maxlength="30"
                @update:model-value="updateEditingName"
              />
            </el-form-item>
            <div class="airspace-edit-actions">
              <el-button type="primary" @click="saveEditing">保存</el-button>
              <el-button @click="cancelEditing">取消</el-button>
            </div>
          </el-form>
        </div>

        <div class="airspace-sync-panel">
          <div class="airspace-side-title airspace-sync-head">
            <span>设备同步状态</span>
            <el-button size="small" text :loading="devicesLoading" @click="loadDevices">刷新</el-button>
          </div>
          <small class="airspace-sync-summary">{{ syncSummary }}</small>
          <div v-if="devicesLoading && !deviceSyncList.length" class="empty-state">正在加载设备…</div>
          <div v-else-if="!deviceSyncList.length" class="empty-state">暂无设备，请先在设备管理中绑定机场。</div>
          <article
            v-for="device in deviceSyncList"
            :key="device.deviceSn"
            class="airspace-sync-item"
          >
            <div class="airspace-sync-meta">
              <strong>{{ device.nickname || device.deviceSn }}</strong>
              <small>{{ device.deviceSn }}</small>
              <small v-if="device.syncMsg" class="airspace-sync-msg">{{ device.syncMsg }}</small>
            </div>
            <el-tag
              size="small"
              :type="SYNC_STATUS_TAG_TYPE[device.syncStatus]"
              :effect="device.syncStatus === 'synchronizing' ? 'plain' : 'light'"
            >
              {{ SYNC_STATUS_LABEL[device.syncStatus] }}
            </el-tag>
          </article>
        </div>
      </aside>

      <div class="airspace-map-wrap">
        <div v-if="gisError" class="empty-state">{{ gisError }}</div>
        <template v-else-if="gisReady">
          <div :id="MAP_ID" class="airspace-map-host" />
          <ZYWLGISMAP
            ref="gisRef"
            Language="zh"
            :MapEngine="MapEngine"
            :MapConfiguration="MapConfiguration"
            :MapPlugins="MapPlugins"
            :MapLayers="MapLayers"
            :ActiveContainerIDs="ActiveContainerIDs"
            :CustomNoFlyZones="CustomNoFlyZones"
            @onMapInitCompleted="onMapInitCompleted"
            @onGraphClick="onGraphClick"
            @onGraphChanged="onGraphChanged"
            @onGraphEdited="onGraphEdited"
            @onGraphRemoved="onGraphRemoved"
          />
        </template>
        <div v-else class="empty-state">正在加载地图引擎…</div>
      </div>
    </div>

    <el-dialog
      v-model="syncDialogVisible"
      title="同步到设备"
      width="480px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <p class="airspace-sync-dialog-tip">
        {{ zones.length
          ? `将当前 ${zones.length} 个限飞区推送到司空后，下发到选中机场设备。`
          : '当前暂无限飞区，同步将按司空侧现有安全区状态下发到选中机场。' }}
      </p>
      <el-checkbox-group v-model="selectedDeviceSns" class="airspace-sync-check-group">
        <el-checkbox
          v-for="device in deviceSyncList"
          :key="device.deviceSn"
          :value="device.deviceSn"
          :disabled="device.syncStatus === 'synchronizing'"
        >
          <span class="airspace-sync-check-label">
            <span>{{ device.nickname || device.deviceSn }}</span>
            <el-tag size="small" :type="SYNC_STATUS_TAG_TYPE[device.syncStatus]">
              {{ SYNC_STATUS_LABEL[device.syncStatus] }}
            </el-tag>
          </span>
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="syncDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="syncing" @click="confirmSyncToDevices">开始同步</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
.airspace-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 60px);
  min-height: 0;
  padding: 12px 14px;
  background: #0a0a0a;
  color: #e5eaf3;
  --line: #2b2b2c;
  --muted: #8b919a;
  --panel: #141414;
}

.page-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-shrink: 0;
  margin-bottom: 12px;
  padding: 12px 16px;
  border: 1px solid var(--line);
  background: var(--panel);
  border-radius: 8px;

  > div:first-child {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  small {
    color: var(--muted);
  }
}

.airspace-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.airspace-body {
  display: flex;
  flex: 1 1 auto;
  min-height: 0;
  gap: 12px;
}

.airspace-side {
  width: 300px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--line);
  background: var(--panel);
  border-radius: 8px;
  overflow: auto;
}

.airspace-side-title {
  font-size: 13px;
  font-weight: 600;
}

.airspace-zone-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px;
  border: 1px solid var(--line);
  background: #1a1a1a;
  border-radius: 6px;

  &.active {
    border-color: #f56c6c;
  }
}

.airspace-zone-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;

  strong {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  small {
    color: var(--muted);
  }
}

.airspace-zone-btns {
  display: flex;
  gap: 8px;
}

.airspace-edit-panel {
  margin-top: 8px;
  padding-top: 12px;
  border-top: 1px dashed var(--line);
}

.airspace-edit-actions {
  display: flex;
  gap: 8px;
}

.airspace-sync-panel {
  margin-top: 8px;
  padding-top: 12px;
  border-top: 1px dashed var(--line);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.airspace-sync-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.airspace-sync-summary {
  color: var(--muted);
  margin-top: -4px;
}

.airspace-sync-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  padding: 10px;
  border: 1px solid var(--line);
  background: #1a1a1a;
  border-radius: 6px;
}

.airspace-sync-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;

  strong {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  small {
    color: var(--muted);
    word-break: break-all;
  }
}

.airspace-sync-msg {
  color: #e6a23c !important;
}

.airspace-sync-dialog-tip {
  margin: 0 0 12px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.5;
}

.airspace-sync-check-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 320px;
  overflow: auto;
}

.airspace-sync-check-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.airspace-map-wrap {
  position: relative;
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  border: 1px solid var(--line);
  border-radius: 8px;
  overflow: hidden;
  background: #111;
}

.airspace-map-host {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

/* maptalks 绝对定位图层默认无宽高会塌成 0，需拉满容器否则底图不渲染 */
.airspace-map-host :deep(.maptalks-wrapper),
.airspace-map-host :deep(.maptalks-all-layers),
.airspace-map-host :deep(.maptalks-back),
.airspace-map-host :deep(.maptalks-front),
.airspace-map-host :deep(.maptalks-back-layer),
.airspace-map-host :deep(.maptalks-front-layer),
.airspace-map-host :deep(.maptalks-canvas-layer) {
  width: 100%;
  height: 100%;
}

.empty-state {
  color: var(--muted);
  font-size: 13px;
  padding: 8px 0;
}

@media (max-width: 960px) {
  .airspace-body {
    flex-direction: column;
  }

  .airspace-side {
    width: 100%;
    max-height: 280px;
  }

  .airspace-map-wrap {
    min-height: 420px;
  }
}
</style>
