<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import ZYWLGISMAP from 'zywlgis'
import 'zywlgis/release/zywlgis.css'
import {
  annotationToSaveBody,
  annotationVoToRecord,
  createAnnotation,
  deleteAnnotation,
  fetchAnnotations,
  updateAnnotation,
} from '@/api/map'
import { usePaasBootstrap } from '@/composables/usePaasBootstrap'
import { ensureGisDependencies, ensureGisI18n } from '@/utils/loadGisDeps'
import { pruneAnnotationGraphics } from '@/utils/annotationGraphics'
import {
  createAnnotationRecord,
  endAnnotationEdit,
  loadAnnotationsLocal,
  saveAnnotationsLocal,
  startAnnotationEdit,
  type AnnotationGisType,
  type AnnotationRecord,
} from '@/utils/annotationStorage'
import { useUserStore } from '@/stores/user'

const MAP_ID = 'map-annotation-gis'
const { load: loadPaasBootstrap } = usePaasBootstrap()
const userStore = useUserStore()
const canWrite = computed(() => !!userStore.user?.permissions?.includes('map:write'))

const gisRef = ref<{
  setMapEngineAction?: () => void
  _maptalksEventsInitialized?: boolean
} | null>(null)

const gisReady = ref(false)
const gisError = ref('')
const mapReady = ref(false)
const listLoading = ref(false)
const editingId = ref('')
const annotations = ref<AnnotationRecord[]>([])
const savedSnapshot = ref<AnnotationRecord[]>([])
/** 新建多边形/圆形时的填充与描边颜色 */
const drawColor = ref('#2F80ED')
const DRAW_COLOR_PRESETS = ['#2F80ED', '#E23C39', '#67C23A', '#E6A23C', '#909399', '#9B59B6', '#00BCD4', '#FFFFFF']

const center = {
  longitude: 117.218304823,
  latitude: 39.06580547,
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
  zoom: { current: 14, max: 20, min: 1 },
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
  layerManagementVisible: true,
  videoProjectionVisible: false,
  pointOfInterestVisible: false,
  zoomOutVisible: true,
  zoomInVisible: true,
  mapModeVisible: false,
  rangingToolVisible: true,
  compassNeedleVisible: true,
  stateBarVisible: true,
  tileLoadProgressVisible: false,
  keyboardTipsVisible: false,
  style: { right: 12, left: 12 },
})

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
const Annotations = computed(() => annotations.value)
const editingItem = computed(() => annotations.value.find((z) => z.ID === editingId.value) || null)
const isEditing = computed(() => Boolean(editingId.value))

/** 计算标注的几何中心点（圆形取圆心，其余取坐标均值） */
function calcAnnotationCenter(item: AnnotationRecord): { longitude: number; latitude: number } | null {
  const coords = item.coordinates || []
  if (!coords.length) return null
  if (item.type === 4) {
    const c = coords[0]
    if (!Number.isFinite(c.longitude) || !Number.isFinite(c.latitude)) return null
    return { longitude: c.longitude, latitude: c.latitude }
  }
  let lngSum = 0
  let latSum = 0
  let count = 0
  for (const c of coords) {
    if (Number.isFinite(c.longitude) && Number.isFinite(c.latitude)) {
      lngSum += c.longitude
      latSum += c.latitude
      count++
    }
  }
  if (!count) return null
  return { longitude: lngSum / count, latitude: latSum / count }
}

/** 点击回中：将地图平滑移动到标注中心 */
function locateAnnotation(item: AnnotationRecord) {
  const map = (window as unknown as {
    mapEngines?: Map<string, { animateTo?: (view: Record<string, unknown>, opts?: Record<string, unknown>) => void }>
  }).mapEngines?.get(`${MAP_ID}-maptalks`)
  if (!map?.animateTo) {
    ElMessage.warning('地图引擎未就绪')
    return
  }
  const c = calcAnnotationCenter(item)
  if (!c) {
    ElMessage.warning('该标注暂无坐标，无法定位')
    return
  }
  map.animateTo({ center: { x: c.longitude, y: c.latitude } }, { duration: 600 })
}

/** 切换标注显隐 */
function toggleVisible(item: AnnotationRecord) {
  replaceItem(item.ID, { visible: !item.visible })
  cacheLocally()
}

function typeLabel(type: AnnotationGisType) {
  if (type === 4) return '圆形'
  if (type === 2) return '折线'
  return '多边形'
}

function applyColor(item: AnnotationRecord, color: string): AnnotationRecord {
  const fill = color || '#2F80ED'
  return {
    ...item,
    symbol: {
      ...item.symbol,
      lineColor: fill,
      polygonColor: fill,
    },
  }
}

function updateEditingColor(color: string | null) {
  const item = editingItem.value
  if (!item || !color) return
  drawColor.value = color
  replaceItem(item.ID, (cur) => applyColor(cur, color))
}

function cacheLocally() {
  saveAnnotationsLocal(annotations.value.map((z) => (z.ID === editingId.value ? z : endAnnotationEdit(z))))
}

function replaceItem(id: string, patch: Partial<AnnotationRecord> | ((item: AnnotationRecord) => AnnotationRecord)) {
  const index = annotations.value.findIndex((z) => z.ID === id)
  if (index < 0) return
  const current = annotations.value[index]
  const next = typeof patch === 'function' ? patch(current) : { ...current, ...patch }
  const list = annotations.value.slice()
  list[index] = next
  annotations.value = list
}

function isDraft(item: AnnotationRecord) {
  return !savedSnapshot.value.some((s) => s.ID === item.ID)
}

async function loadList() {
  listLoading.value = true
  try {
    const { data } = await fetchAnnotations()
    const mapped = (data.data || [])
      .map(annotationVoToRecord)
      .filter((item): item is AnnotationRecord => Boolean(item))
    annotations.value = mapped
    savedSnapshot.value = mapped.map((z) =>
      endAnnotationEdit({ ...z, coordinates: [...(z.coordinates || [])] }),
    )
    cacheLocally()
  } catch (error: unknown) {
    const cached = loadAnnotationsLocal()
    if (cached.length) {
      annotations.value = cached
      savedSnapshot.value = cached.map((z) => endAnnotationEdit({ ...z }))
      ElMessage.warning((error as Error)?.message || '标注列表加载失败，已使用本地缓存')
    } else {
      annotations.value = []
      savedSnapshot.value = []
      ElMessage.error((error as Error)?.message || '标注列表加载失败')
    }
  } finally {
    listLoading.value = false
  }
}

function beginCreate(type: 3 | 4) {
  if (!canWrite.value) {
    ElMessage.warning('无地图标注写权限')
    return
  }
  if (isEditing.value) {
    ElMessage.warning('请先完成当前标注的保存或取消')
    return
  }
  const namePrefix = type === 4 ? '圆形' : '多边形'
  const item = startAnnotationEdit(
    createAnnotationRecord(type, `${namePrefix}_${annotations.value.length + 1}`, 'other', drawColor.value),
  )
  annotations.value = [...annotations.value, item]
  editingId.value = item.ID
  ElMessage.success(type === 4 ? '请在地图上点击圆心并拖出半径' : '请在地图上依次点击绘制多边形边界')
}

function beginEdit(item: AnnotationRecord) {
  if (!canWrite.value) return
  if (editingId.value === item.ID) return
  if (isEditing.value) {
    ElMessage.warning('请先完成当前标注的保存或取消')
    return
  }
  replaceItem(item.ID, startAnnotationEdit(item))
  editingId.value = item.ID
  ElMessage.info('已进入编辑模式，可拖动节点调整')
}

function validateItem(item: AnnotationRecord): string | null {
  const coords = item.coordinates || []
  if (item.type === 3 && coords.length < 3) return '多边形至少需要 3 个点'
  if (item.type === 4 && coords.length < 1) return '圆形至少需要 1 个中心点'
  if (!item.customLabel?.content?.trim() && !item.name?.trim()) return '请填写名称'
  return null
}

async function saveEditing() {
  const item = editingItem.value
  if (!item) return
  const error = validateItem(item)
  if (error) {
    ElMessage.warning(error)
    return
  }
  const name = item.customLabel?.content?.trim() || item.name
  const prepared = endAnnotationEdit({
    ...item,
    name,
    customLabel: { ...item.customLabel, content: name, show: true },
  })
  try {
    if (isDraft(item)) {
      const { data } = await createAnnotation(annotationToSaveBody(prepared))
      const id = data.data?.id
      if (id == null) throw new Error(data.message || '创建成功但未返回 id')
      replaceItem(item.ID, { ...prepared, ID: String(id) })
    } else {
      await updateAnnotation(Number(item.ID), annotationToSaveBody(prepared))
      replaceItem(item.ID, prepared)
    }
    editingId.value = ''
    savedSnapshot.value = annotations.value.map((z) =>
      endAnnotationEdit({
        ...z,
        coordinates: [...(z.coordinates || [])],
        symbol: { ...z.symbol },
        customLabel: { ...z.customLabel },
      }),
    )
    cacheLocally()
    ElMessage.success('标注已保存')
  } catch (err: unknown) {
    ElMessage.error((err as Error)?.message || '保存失败')
  }
}

function cancelEditing() {
  const item = editingItem.value
  if (!item) return
  if (isDraft(item)) {
    annotations.value = annotations.value.filter((z) => z.ID !== item.ID)
  } else {
    const saved = savedSnapshot.value.find((z) => z.ID === item.ID)
    if (saved) {
      replaceItem(item.ID, endAnnotationEdit({ ...saved, coordinates: [...(saved.coordinates || [])] }))
    } else {
      replaceItem(item.ID, endAnnotationEdit(item))
    }
  }
  editingId.value = ''
  cacheLocally()
}

function removeItem(item: AnnotationRecord) {
  if (!canWrite.value) return
  ElMessageBox.confirm(`确定删除「${item.customLabel?.content || item.name}」吗？`, '删除标注', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
    .then(async () => {
      try {
        if (!isDraft(item)) await deleteAnnotation(Number(item.ID))
        if (editingId.value === item.ID) editingId.value = ''
        annotations.value = annotations.value.filter((z) => z.ID !== item.ID)
        savedSnapshot.value = savedSnapshot.value.filter((z) => z.ID !== item.ID)
        cacheLocally()
        ElMessage.success('已删除')
      } catch (err: unknown) {
        ElMessage.error((err as Error)?.message || '删除失败')
      }
    })
    .catch(() => {})
}

function updateEditingName(name: string) {
  const item = editingItem.value
  if (!item) return
  item.name = name
  item.customLabel = { ...item.customLabel, content: name, show: true }
}

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
    console.warn('[map] setMapEngineAction failed', error)
  }
}

function ensureBaseLayers() {
  const map = (window as unknown as {
    mapEngines?: Map<string, { getLayer?: (id: string) => unknown; checkSize?: () => void }>
  }).mapEngines?.get(`${MAP_ID}-maptalks`)
  if (!map) return
  if (map.getLayer?.('group-tiles')) {
    map.checkSize?.()
    return
  }
  if (!MapLayers.length) return
  const next = MapLayers.map((layer) => ({ ...layer }))
  MapLayers.splice(0, MapLayers.length, ...next)
  map.checkSize?.()
}

function onMapInitCompleted() {
  mapReady.value = true
  nextTick(() => {
    ensureGisI18n()
    ensureBaseLayers()
    bindMapInteractionsIfNeeded()
  })
}

function onGraphClick(payload: { ID?: string; class?: string; data?: AnnotationRecord }) {
  if (payload?.class && payload.class !== 'Annotations') return
  const id = payload.ID || payload.data?.ID
  if (!id) return
  const item = annotations.value.find((z) => z.ID === id)
  if (!item) return
  beginEdit(item)
}

function onGraphChanged(payload: {
  ID?: string
  class?: string
  coordinates?: AnnotationRecord['coordinates']
  data?: AnnotationRecord
}) {
  if (payload?.class && payload.class !== 'Annotations') return
  const id = payload.ID
  if (!id) return
  replaceItem(id, (item) => {
    const coordinates = payload.coordinates || payload.data?.coordinates || item.coordinates
    const radius = payload.data?.symbol?.radius
    return {
      ...item,
      coordinates: coordinates || [],
      symbol: {
        ...item.symbol,
        ...(radius !== undefined ? { radius } : {}),
      },
    }
  })
}

function onGraphEdited(payload: { ID?: string; class?: string; data?: AnnotationRecord }) {
  if (payload?.class && payload.class !== 'Annotations') return
  const id = payload.ID
  if (!id || !payload.data) return
  replaceItem(id, (item) => ({
    ...item,
    ...payload.data,
    ID: id,
    customLabel: {
      ...item.customLabel,
      ...(payload.data?.customLabel || {}),
      content: payload.data?.customLabel?.content || item.customLabel.content,
    },
  }))
}

async function onGraphRemoved(payload: { ID?: string; class?: string }) {
  if (payload?.class && payload.class !== 'Annotations') return
  const id = payload.ID
  if (!id) return
  const item = annotations.value.find((z) => z.ID === id)
  if (editingId.value === id) editingId.value = ''
  try {
    if (item && !isDraft(item)) await deleteAnnotation(Number(id))
  } catch (err: unknown) {
    ElMessage.error((err as Error)?.message || '后台删除失败')
    await loadList()
    return
  }
  annotations.value = annotations.value.filter((z) => z.ID !== id)
  savedSnapshot.value = savedSnapshot.value.filter((z) => z.ID !== id)
  cacheLocally()
}

// Run before the child GIS watcher removes its registry entries, while drawing
// listeners can still be detached. Also covers cancelled drafts and saved IDs.
watch(
  () => annotations.value.map((item) => item.ID),
  (ids) => pruneAnnotationGraphics(MAP_ID, ids),
)

onMounted(async () => {
  try {
    await loadPaasBootstrap()
  } catch {
    // ignore
  }
  try {
    await ensureGisDependencies()
    gisReady.value = true
  } catch (error: unknown) {
    gisError.value = (error as Error)?.message || 'Cesium 依赖加载失败'
    ElMessage.error(gisError.value)
  }
  await loadList()
})

onBeforeUnmount(() => {
  if (editingId.value) cancelEditing()
})
</script>

<template>
  <section class="component-page map-page">
    <div class="page-toolbar">
      <div>
        <strong>地图标注</strong>
        <small>绘制多边形 / 圆形标注；支持测距与图层管理</small>
      </div>
      <div class="map-actions">
        <div class="map-color-picker">
          <span>颜色</span>
          <el-color-picker
            v-model="drawColor"
            size="small"
            :predefine="DRAW_COLOR_PRESETS"
            :disabled="!canWrite"
            @change="(c: string | null) => editingItem && c && updateEditingColor(c)"
          />
        </div>
        <el-button
          type="primary"
          plain
          :disabled="!canWrite || isEditing || !mapReady || listLoading"
          @click="beginCreate(3)"
        >
          新增多边形
        </el-button>
        <el-button
          type="primary"
          plain
          :disabled="!canWrite || isEditing || !mapReady || listLoading"
          @click="beginCreate(4)"
        >
          新增圆形
        </el-button>
        <el-button :loading="listLoading" @click="loadList">刷新列表</el-button>
      </div>
    </div>

    <div class="map-body">
      <aside class="map-side">
        <div class="map-side-title">标注列表</div>
        <div v-if="listLoading && !annotations.length" class="empty-state">正在加载标注…</div>
        <div v-else-if="!annotations.length" class="empty-state">暂无标注，点击上方按钮开始绘制。</div>
        <article
          v-for="item in annotations"
          :key="item.ID"
          class="map-item"
          :class="{ active: item.ID === editingId }"
        >
          <div class="map-item-meta">
            <strong>
              <i class="map-item-swatch" :style="{ background: item.symbol?.polygonColor || item.symbol?.lineColor || '#2F80ED' }" />
              {{ item.customLabel?.content || item.name }}
            </strong>
            <small>
              {{ typeLabel(item.type) }}
              · {{ item.coordinates?.length || 0 }} 点
            </small>
          </div>
          <div class="map-item-btns">
            <el-button
              size="small"
              :type="item.visible ? 'primary' : 'info'"
              plain
              :disabled="isEditing && editingId !== item.ID"
              @click="toggleVisible(item)"
            >
              {{ item.visible ? '隐藏' : '显示' }}
            </el-button>
            <el-button
              size="small"
              :disabled="!item.coordinates?.length"
              @click="locateAnnotation(item)"
            >
              回中
            </el-button>
            <el-button
              size="small"
              :disabled="!canWrite || (isEditing && editingId !== item.ID)"
              @click="beginEdit(item)"
            >
              编辑
            </el-button>
            <el-button
              size="small"
              type="danger"
              plain
              :disabled="!canWrite || (isEditing && editingId !== item.ID)"
              @click="removeItem(item)"
            >
              删除
            </el-button>
          </div>
        </article>

        <div v-if="editingItem" class="map-edit-panel">
          <div class="map-side-title">正在编辑</div>
          <el-form label-position="top" size="small">
            <el-form-item label="名称">
              <el-input
                :model-value="editingItem.customLabel?.content || editingItem.name"
                maxlength="30"
                @update:model-value="updateEditingName"
              />
            </el-form-item>
            <el-form-item label="颜色">
              <el-color-picker
                :model-value="editingItem.symbol?.polygonColor || editingItem.symbol?.lineColor || drawColor"
                size="small"
                :predefine="DRAW_COLOR_PRESETS"
                @change="updateEditingColor"
              />
            </el-form-item>
            <div class="map-edit-actions">
              <el-button type="primary" @click="saveEditing">保存</el-button>
              <el-button @click="cancelEditing">取消</el-button>
            </div>
          </el-form>
        </div>
      </aside>

      <div class="map-wrap">
        <div v-if="gisError" class="empty-state">{{ gisError }}</div>
        <template v-else-if="gisReady">
          <div :id="MAP_ID" class="map-host" />
          <ZYWLGISMAP
            ref="gisRef"
            Language="zh"
            :MapEngine="MapEngine"
            :MapConfiguration="MapConfiguration"
            :MapPlugins="MapPlugins"
            :MapLayers="MapLayers"
            :ActiveContainerIDs="ActiveContainerIDs"
            :Annotations="Annotations"
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
  </section>
</template>

<style scoped lang="scss">
.map-page {
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

.map-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.map-color-picker {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  margin-right: 4px;
  color: var(--muted);
  font-size: 13px;
}

.map-body {
  display: flex;
  flex: 1 1 auto;
  min-height: 0;
  gap: 12px;
}

.map-side {
  width: 320px;
  flex-shrink: 0;
  overflow: auto;
  padding: 12px;
  border: 1px solid var(--line);
  background: var(--panel);
  border-radius: 8px;
}

.map-side-title {
  margin-bottom: 10px;
  font-weight: 600;
}

.map-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px;
  margin-bottom: 8px;
  border: 1px solid var(--line);
  border-radius: 6px;

  &.active {
    border-color: #409eff;
    background: rgba(64, 158, 255, 0.08);
  }
}

.map-item-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;

  strong {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  small {
    color: var(--muted);
  }
}

.map-item-swatch {
  display: inline-block;
  width: 10px;
  height: 10px;
  flex-shrink: 0;
  border-radius: 2px;
  border: 1px solid rgba(255, 255, 255, 0.35);
}

.map-item-btns {
  display: flex;
  gap: 4px;

  .el-button {
    flex: 1;
  }
}

.map-edit-panel {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}

.map-edit-actions {
  display: flex;
  gap: 8px;
}

.map-wrap {
  position: relative;
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #111;
}

.map-host {
  position: absolute;
  inset: 0;
}

.empty-state {
  padding: 24px 12px;
  color: var(--muted);
  text-align: center;
}

:deep(.zywlgis),
:deep(.zywlgis > div) {
  width: 100%;
  height: 100%;
}
</style>
