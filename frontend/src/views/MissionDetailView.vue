<template>
  <div v-loading="loading" class="mission-detail">
    <div class="toolbar">
      <el-button @click="goBack">{{ t('missions.backToList') }}</el-button>
      <strong class="title">{{ detail?.plan?.name || t('missions.detailTitle') }}</strong>
      <el-tag v-if="detail?.plan?.status" class="status-tag">{{ statusLabel(detail.plan.status) }}</el-tag>
    </div>

    <template v-if="detail?.plan">
      <el-row :gutter="16" class="summary-row">
        <el-col :span="4"><div class="stat-card"><div class="num">{{ detail.totalCount }}</div><div class="label">{{ t('missions.execTotal') }}</div></div></el-col>
        <el-col :span="4"><div class="stat-card pending"><div class="num">{{ detail.pendingCount }}</div><div class="label">{{ t('missions.execPending') }}</div></div></el-col>
        <el-col :span="4"><div class="stat-card running"><div class="num">{{ detail.runningCount }}</div><div class="label">{{ t('missions.execRunning') }}</div></div></el-col>
        <el-col :span="4"><div class="stat-card done"><div class="num">{{ detail.completedCount }}</div><div class="label">{{ t('missions.execCompleted') }}</div></div></el-col>
        <el-col :span="4"><div class="stat-card fail"><div class="num">{{ detail.failedCount }}</div><div class="label">{{ t('missions.execFailed') }}</div></div></el-col>
        <el-col :span="4"><div class="stat-card cancel"><div class="num">{{ detail.cancelledCount }}</div><div class="label">{{ t('missions.execCancelled') }}</div></div></el-col>
      </el-row>

      <el-descriptions :title="t('missions.planInfo')" :column="3" border class="block">
        <el-descriptions-item :label="t('missions.planName')">{{ detail.plan.name }}</el-descriptions-item>
        <el-descriptions-item :label="t('missions.taskType')">{{ taskTypeLabel(detail.plan.taskType) }}</el-descriptions-item>
        <el-descriptions-item :label="t('missions.strategy')">{{ strategyLabel(detail.plan.strategy) }}</el-descriptions-item>
        <el-descriptions-item :label="t('missions.device')">{{ detail.plan.deviceName || detail.plan.deviceId }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.plan.taskType === 'leapfrog'" :label="t('missions.landingDevice')">{{ detail.plan.landingDeviceName || detail.plan.landingDeviceId || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('missions.wayline')">{{ detail.plan.waylineName || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('common.status')">{{ statusLabel(detail.plan.status) }}</el-descriptions-item>
        <el-descriptions-item :label="t('missions.accuracyMode')">{{ accuracyLabel(detail.plan.accuracyMode) }}</el-descriptions-item>
        <el-descriptions-item :label="t('missions.minFlightHeight')">{{ detail.plan.minFlightHeightM ?? '-' }} m</el-descriptions-item>
        <el-descriptions-item :label="t('missions.signalLostAction')">{{ signalLostLabel(detail.plan.signalLostAction) }}</el-descriptions-item>
        <el-descriptions-item :label="t('missions.scheduleAt')">{{ detail.plan.scheduleAt || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('missions.repeatFrequency')">{{ detail.plan.cronExpr || '-' }}</el-descriptions-item>
        <el-descriptions-item :label="t('missions.createdAt')">{{ detail.plan.createdAt || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div class="block">
        <div class="block-title">{{ t('missions.executionList') }}</div>
        <el-table class="exec-table" :data="detail.executions || []" border stripe>
          <el-table-column type="index" width="60" :label="t('missions.execIndex')" />
          <el-table-column :label="t('common.status')" width="100">
            <template #default="{ row }">
              <el-tag :type="execTagType(row.status)" size="small">{{ execStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="actualStartAt" :label="t('missions.actualStartAt')" min-width="160">
            <template #default="{ row }">{{ row.actualStartAt || '-' }}</template>
          </el-table-column>
          <el-table-column prop="actualEndAt" :label="t('missions.actualEndAt')" min-width="160">
            <template #default="{ row }">{{ row.actualEndAt || '-' }}</template>
          </el-table-column>
          <el-table-column :label="t('missions.flightDuration')" width="100">
            <template #default="{ row }">{{ row.flightDurationSec != null ? `${row.flightDurationSec}s` : '-' }}</template>
          </el-table-column>
          <el-table-column :label="t('missions.collectedMedia')" min-width="320">
            <template #default="{ row }">
              <div v-if="(row.media || []).length" class="media-cell">
                <div
                  v-for="(item, idx) in row.media || []"
                  :key="`${row.id}-m-${idx}-${item.uuid || item.id || item.name || 'x'}`"
                  class="thumb-wrap"
                  :ref="(el) => bindMediaThumb(el, item)"
                >
                  <el-image
                    v-if="isImage(item) && mediaThumb(item)"
                    class="thumb"
                    :src="mediaThumb(item)"
                    fit="cover"
                    lazy
                    :title="item.name"
                    @click="openImage(item)"
                  />
                  <div
                    v-else-if="isVideo(item)"
                    class="thumb video"
                    :title="item.name"
                    @click="openVideo(item)"
                  >
                    <span>▶</span>
                  </div>
                  <div
                    v-else-if="isImage(item)"
                    class="thumb placeholder"
                    :class="{ busy: !!loadingUrlKeys[mediaKey(item)] }"
                    :title="item.name"
                    @click="openImage(item)"
                  >
                    <span v-if="loadingUrlKeys[mediaKey(item)]" class="dot" />
                    <span v-else>图</span>
                  </div>
                  <div v-else class="thumb placeholder" :title="item.name || String(idx + 1)">媒</div>
                  <button
                    v-if="item.id && row.id"
                    type="button"
                    class="thumb-del"
                    :title="t('common.delete')"
                    :disabled="deletingMediaKey === mediaKey(item)"
                    @click.stop.prevent="removeMedia(row, item)"
                  >
                    ×
                  </button>
                </div>
              </div>
              <span v-else class="muted">{{ t('missions.noMedia') }}</span>
            </template>
          </el-table-column>
          <el-table-column v-if="detail?.plan?.areaWayline" :label="t('missions.reconstruct')" width="120" fixed="right">
            <template #default="{ row }">
              <div v-if="row.recordType === 'execution' && row.id" class="recon-cell">
                <template v-if="row.reconstruct?.status === 'success'">
                  <el-image
                    v-if="row.reconstruct.previewUrl"
                    class="thumb"
                    :src="row.reconstruct.previewUrl"
                    fit="cover"
                    :title="t('missions.reconstructPreview')"
                    @click="openModel(row)"
                  />
                  <el-button v-else link type="primary" @click="openModel(row)">
                    {{ t('missions.reconstructPreview') }}
                  </el-button>
                  <el-button
                    link
                    type="primary"
                    :loading="downloadingModelId === row.id"
                    @click="downloadModel(row)"
                  >
                    {{ t('missions.reconstructDownloadZip') }}
                  </el-button>
                  <el-tag size="small" type="success">{{ t('missions.reconstructSuccess') }}</el-tag>
                </template>
                <template v-else-if="row.reconstruct?.status === 'pending' || row.reconstruct?.status === 'running'">
                  <el-tag size="small" type="warning">
                    {{
                      row.reconstruct.status === 'pending'
                        ? t('missions.reconstructPending')
                        : t('missions.reconstructRunning')
                    }}
                  </el-tag>
                </template>
                <template v-else-if="row.reconstruct?.status === 'failed'">
                  <el-tooltip :content="row.reconstruct.errorMessage || t('missions.reconstructStatusFailed')" placement="top">
                    <el-tag size="small" type="danger">{{ t('missions.reconstructStatusFailed') }}</el-tag>
                  </el-tooltip>
                  <el-button
                    v-if="row.reconstruct?.available"
                    link
                    type="primary"
                    :loading="reconstructingId === row.id"
                    @click="startReconstruct(row)"
                  >
                    {{ t('missions.reconstructRetry') }}
                  </el-button>
                </template>
                <el-button
                  v-else-if="row.reconstruct?.available"
                  link
                  type="primary"
                  :loading="reconstructingId === row.id"
                  @click="startReconstruct(row)"
                >
                  {{ t('missions.reconstruct') }}
                </el-button>
                <!-- 面状但无采集媒体：置灰按钮，采集后自动恢复 -->
                <el-tooltip
                  v-else-if="row.reconstruct?.unavailableReason === 'no_media'"
                  :content="t('missions.reconstructNoMedia')"
                  placement="top"
                >
                  <span>
                    <el-button link type="primary" disabled>{{ t('missions.reconstruct') }}</el-button>
                  </span>
                </el-tooltip>
                <el-tooltip v-else :content="reconstructDisabledTip(row.reconstruct?.unavailableReason)" placement="top">
                  <span class="muted">-</span>
                </el-tooltip>
              </div>
              <span v-else class="muted">-</span>
            </template>
          </el-table-column>
          <el-table-column :label="t('missions.replay')" width="100" fixed="right">
            <template #default="{ row }">
              <el-tooltip
                v-if="row.recordType !== 'planned' && row.id && !row.fh2TaskUuid"
                :content="t('missions.replayNeedTask')"
                placement="top"
              >
                <span>
                  <el-button link type="primary" disabled>{{ t('missions.replay') }}</el-button>
                </span>
              </el-tooltip>
              <el-button
                v-else-if="row.recordType !== 'planned' && row.id && row.fh2TaskUuid"
                link
                type="primary"
                @click="openReplay(row)"
              >
                {{ t('missions.replay') }}
              </el-button>
              <span v-else class="muted">-</span>
            </template>
          </el-table-column>
          <el-table-column prop="failReason" :label="t('missions.failReason')" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.failReason || '-' }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!(detail.executions || []).length" :description="t('missions.noExecutions')" />
      </div>
    </template>

    <el-dialog v-model="videoVisible" :title="playing?.name || t('library.videoPlay')" width="720px" destroy-on-close>
      <video v-if="playingUrl" class="player" :src="playingUrl" controls autoplay />
    </el-dialog>
    <el-dialog v-model="imageVisible" :title="imagePreviewName || t('library.image')" width="720px" destroy-on-close>
      <img v-if="imagePreviewUrl" class="player" :src="imagePreviewUrl" alt="" />
    </el-dialog>
    <ModelPreviewDialog
      v-model:visible="modelVisible"
      :title="modelTitle"
      :preview-url="modelPreviewUrl"
      :model-url="modelUrl"
    />
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import ModelPreviewDialog from '@/components/ModelPreviewDialog.vue'
import { fetchMediaDownloadUrl } from '@/api/library'
import {
  deleteExecutionMedia,
  fetchMissionDetail,
  fetchReconstructModelUrl,
  startMissionReconstruct,
  type ExecutionMediaVO,
  type MissionDetailVO,
  type MissionExecutionVO,
} from '@/api/mission'

/** 执行记录轮询间隔：后端约 20s 回写司空状态，前端 5s 拉取以便及时展示 */
const EXECUTION_POLL_MS = 5000

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const detail = ref<MissionDetailVO | null>(null)
const previewMap = reactive<Record<string, string>>({})
/** 正在拉取预览/下载地址的媒体 key，用于占位态 */
const loadingUrlKeys = reactive<Record<string, boolean>>({})
const videoVisible = ref(false)
const playing = ref<ExecutionMediaVO | null>(null)
const playingUrl = ref('')
const imageVisible = ref(false)
const imagePreviewUrl = ref('')
const imagePreviewName = ref('')
const modelVisible = ref(false)
const modelTitle = ref('')
const modelPreviewUrl = ref('')
const modelUrl = ref('')
const reconstructingId = ref<number | null>(null)
const downloadingModelId = ref<number | null>(null)
const deletingMediaKey = ref<string | null>(null)
let pollTimer: ReturnType<typeof setInterval> | null = null
let loadSeq = 0
/** 视口内懒加载缩略图：并发上限，避免一次打爆司空 */
const THUMB_FETCH_CONCURRENCY = 4
let thumbObserver: IntersectionObserver | null = null
const observedThumbEls = new WeakSet<Element>()
const thumbItemByKey = new Map<string, ExecutionMediaVO>()
let thumbActiveFetches = 0
const thumbWaitQueue: Array<() => void> = []

function goBack() {
  router.push({ name: 'missions' })
}

function mediaKey(item: ExecutionMediaVO) {
  return String(item.uuid || item.id || item.name || item.previewUrl || item.downloadUrl || '')
}

function mediaThumb(item: ExecutionMediaVO) {
  return previewMap[mediaKey(item)] || item.previewUrl || item.downloadUrl || ''
}

function isImage(item: ExecutionMediaVO) {
  const tpe = (item.mediaType || '').toLowerCase()
  if (tpe.includes('video')) return false
  if (tpe.includes('image') || tpe.includes('photo') || tpe.includes('jpeg') || tpe.includes('jpg') || tpe.includes('png') || tpe.includes('dng')) {
    return true
  }
  // 有预览/下载地址但类型未知时，按图片展示，避免被吞掉
  return !tpe && !!(item.previewUrl || item.downloadUrl)
}

function isVideo(item: ExecutionMediaVO) {
  return (item.mediaType || '').toLowerCase().includes('video')
}

function ensureThumbObserver() {
  if (thumbObserver || typeof IntersectionObserver === 'undefined') return thumbObserver
  thumbObserver = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (!entry.isIntersecting) continue
        const el = entry.target as HTMLElement
        const key = el.dataset.mediaKey
        if (!key) continue
        thumbObserver?.unobserve(el)
        const item = thumbItemByKey.get(key)
        if (item) void loadThumbUrl(item)
      }
    },
    { root: null, rootMargin: '120px 0px', threshold: 0.01 },
  )
  return thumbObserver
}

/** 仅观察尚无地址的图片占位；进入视口后再请求下载 URL 作为缩略图 */
function bindMediaThumb(el: unknown, item: ExecutionMediaVO) {
  if (!(el instanceof Element)) return
  if (!isImage(item) || mediaThumb(item)) return
  if (observedThumbEls.has(el)) return
  const key = mediaKey(item)
  if (!item.id && !item.previewUrl && !item.downloadUrl) return
  observedThumbEls.add(el)
  thumbItemByKey.set(key, item)
  ;(el as HTMLElement).dataset.mediaKey = key
  const observer = ensureThumbObserver()
  if (observer) observer.observe(el)
  else void loadThumbUrl(item)
}

function releaseThumbSlot() {
  thumbActiveFetches = Math.max(0, thumbActiveFetches - 1)
  const next = thumbWaitQueue.shift()
  if (next) next()
}

async function acquireThumbSlot() {
  if (thumbActiveFetches < THUMB_FETCH_CONCURRENCY) {
    thumbActiveFetches += 1
    return
  }
  await new Promise<void>((resolve) => thumbWaitQueue.push(resolve))
  thumbActiveFetches += 1
}

async function loadThumbUrl(item: ExecutionMediaVO) {
  const key = mediaKey(item)
  if (mediaThumb(item) || loadingUrlKeys[key]) return
  if (!item.id && !(item.previewUrl || item.downloadUrl)) return
  loadingUrlKeys[key] = true
  try {
    await acquireThumbSlot()
    try {
      await resolveMediaUrl(item)
    } finally {
      releaseThumbSlot()
    }
  } catch {
    // 懒加载失败保留占位，点击大图时再提示
  } finally {
    loadingUrlKeys[key] = false
  }
}

async function resolveMediaUrl(item: ExecutionMediaVO) {
  const cached = mediaThumb(item)
  if (cached) return cached
  if (!item.id) return ''
  const { data } = await fetchMediaDownloadUrl(item.id)
  const url = data.data?.downloadUrl || ''
  if (url) previewMap[mediaKey(item)] = url
  return url
}

async function openImage(item: ExecutionMediaVO) {
  try {
    loadingUrlKeys[mediaKey(item)] = true
    const url = await resolveMediaUrl(item)
    if (!url) {
      ElMessage.warning(t('library.loadFailed'))
      return
    }
    imagePreviewUrl.value = url
    imagePreviewName.value = item.name || ''
    imageVisible.value = true
  } catch {
    ElMessage.error(t('library.loadFailed'))
  } finally {
    loadingUrlKeys[mediaKey(item)] = false
  }
}

async function removeMedia(row: MissionExecutionVO, item: ExecutionMediaVO) {
  const planId = detail.value?.plan?.id
  if (!planId || !row.id || !item.id) {
    ElMessage.warning(t('missions.mediaDeleteNeedId'))
    return
  }
  try {
    await ElMessageBox.confirm(t('missions.mediaDeleteConfirm'), t('common.confirmTitle'), {
      type: 'warning',
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
    })
  } catch {
    return
  }
  const key = mediaKey(item)
  deletingMediaKey.value = key
  try {
    await deleteExecutionMedia(planId, row.id, item.id)
    row.media = (row.media || []).filter((m) => mediaKey(m) !== key)
    delete previewMap[key]
    ElMessage.success(t('missions.mediaDeleteOk'))
  } catch {
    ElMessage.error(t('missions.mediaDeleteFailed'))
  } finally {
    deletingMediaKey.value = null
  }
}

async function openVideo(item: ExecutionMediaVO) {
  playing.value = item
  playingUrl.value = ''
  try {
    const url = await resolveMediaUrl(item)
    if (!url) {
      ElMessage.warning(t('library.noVideoUrl'))
      return
    }
    playingUrl.value = url
    videoVisible.value = true
  } catch {
    ElMessage.error(t('library.videoLoadFailed'))
  }
}

function openReplay(row: MissionExecutionVO) {
  const planId = detail.value?.plan?.id
  if (!planId || !row.id || !row.fh2TaskUuid) return
  router.push({
    name: 'mission-replay',
    params: { planId, executionId: row.id },
    query: { taskId: row.fh2TaskUuid },
  })
}

/** 重建按钮不可用原因 → tooltip 文案（后端 unavailableReason 口径） */
function reconstructDisabledTip(reason?: string) {
  const map: Record<string, string> = {
    no_task: t('missions.reconstructNeedTask'),
    no_media: t('missions.reconstructNoMedia'),
    not_area_wayline: t('missions.reconstructNeedArea'),
    active_job: t('missions.reconstructActiveJob'),
  }
  return (reason && map[reason]) || t('missions.reconstructNeedMedia')
}

async function startReconstruct(row: MissionExecutionVO) {
  const planId = detail.value?.plan?.id
  if (!planId || !row.id) return
  reconstructingId.value = row.id
  try {
    await startMissionReconstruct(planId, row.id)
    ElMessage.success(t('missions.reconstructStarted'))
    await load()
  } catch {
    // 错误提示由 http 拦截器统一弹出
  } finally {
    reconstructingId.value = null
  }
}

async function resolveModelZipUrl(row: MissionExecutionVO): Promise<string> {
  const planId = detail.value?.plan?.id
  const r = row.reconstruct
  if (!planId || !row.id || !r) return ''
  const { data } = await fetchReconstructModelUrl(planId, row.id)
  const url = data.data?.downloadUrl || ''
  if (url && row.reconstruct) {
    row.reconstruct.modelUrl = url
  }
  return url
}

async function downloadModel(row: MissionExecutionVO) {
  if (!row.id) return
  downloadingModelId.value = row.id
  try {
    const url = await resolveModelZipUrl(row)
    if (!url) {
      ElMessage.warning(t('missions.reconstructDownloadFailed'))
      return
    }
    window.open(url, '_blank', 'noopener')
  } catch {
    ElMessage.error(t('missions.reconstructDownloadFailed'))
  } finally {
    downloadingModelId.value = null
  }
}

async function openModel(row: MissionExecutionVO) {
  const r = row.reconstruct
  if (!r) return
  modelTitle.value = t('missions.reconstructPreview')
  modelPreviewUrl.value = r.previewUrl || ''
  modelUrl.value = r.modelUrl || ''
  modelVisible.value = true
  if (!modelUrl.value && row.id) {
    try {
      modelUrl.value = await resolveModelZipUrl(row)
    } catch {
      // 弹窗内已有「暂无地址」提示
    }
  }
}

function statusLabel(status?: string) {
  const map: Record<string, string> = {
    pending: t('missions.pending'),
    running: t('missions.running'),
    paused: t('missions.paused'),
    completed: t('missions.completed'),
    cancelled: t('missions.cancelled'),
    failed: t('missions.failed'),
    ended: t('missions.cancelled'),
  }
  return status ? map[status] || status : '-'
}

function execStatusLabel(status?: string) {
  const map: Record<string, string> = {
    pending: t('missions.execPending'),
    scheduled: t('missions.execScheduled'),
    running: t('missions.execRunning'),
    completed: t('missions.execCompleted'),
    success: t('missions.execCompleted'),
    failed: t('missions.execFailed'),
    cancelled: t('missions.execCancelled'),
    canceled: t('missions.execCancelled'),
    ended: t('missions.execCancelled'),
  }
  return status ? map[status] || status : '-'
}

function execTagType(status?: string) {
  switch (status) {
    case 'running':
      return 'warning'
    case 'completed':
    case 'success':
      return 'success'
    case 'failed':
      return 'danger'
    case 'scheduled':
    case 'pending':
      return 'info'
    default:
      return ''
  }
}

function taskTypeLabel(v?: string) {
  const map: Record<string, string> = {
    normal: t('missions.taskNormal'),
    wayline: t('missions.taskWayline'),
    leapfrog: t('missions.taskLeapfrog'),
    manual: t('missions.taskManual'),
  }
  return v ? map[v] || v : '-'
}

function strategyLabel(v?: string) {
  const map: Record<string, string> = {
    immediate: t('missions.immediate'),
    once: t('missions.once'),
    recurring: t('missions.recurring'),
    continuous: t('missions.continuous'),
  }
  return v ? map[v] || v : '-'
}

function accuracyLabel(v?: string) {
  if (v === 'high' || v === 'rtk') return t('missions.accuracyRtk')
  if (v === 'standard' || v === 'gnss') return t('missions.accuracyGnss')
  return v || '-'
}

function signalLostLabel(v?: string) {
  if (v === 'rth') return t('missions.rth')
  if (v === 'continue') return t('missions.continueExec')
  return v || '-'
}

function needsExecutionPoll(data: MissionDetailVO | null) {
  if (!data?.plan) return false
  const planStatus = (data.plan.status || '').toLowerCase()
  if (['pending', 'running', 'paused'].includes(planStatus)) return true
  if ((data.pendingCount || 0) > 0 || (data.runningCount || 0) > 0) return true
  return (data.executions || []).some((row) => {
    if (['pending', 'scheduled', 'running'].includes((row.status || '').toLowerCase())) return true
    const rs = (row.reconstruct?.status || '').toLowerCase()
    return rs === 'pending' || rs === 'running'
  })
}

async function load(options?: { silent?: boolean }) {
  const id = Number(route.params.id)
  if (!Number.isFinite(id)) return
  const silent = Boolean(options?.silent)
  const seq = ++loadSeq
  if (!silent) loading.value = true
  try {
    const { data } = await fetchMissionDetail(id)
    if (seq !== loadSeq) return
    detail.value = data.data
    if (needsExecutionPoll(data.data)) startPolling()
    else stopPolling()
  } finally {
    if (!silent && seq === loadSeq) loading.value = false
  }
}

function startPolling() {
  if (pollTimer) return
  pollTimer = setInterval(() => {
    if (document.visibilityState === 'hidden') return
    void load({ silent: true })
  }, EXECUTION_POLL_MS)
}

function stopPolling() {
  if (!pollTimer) return
  clearInterval(pollTimer)
  pollTimer = null
}

function onVisibilityChange() {
  if (document.visibilityState === 'visible' && needsExecutionPoll(detail.value)) {
    void load({ silent: true })
  }
}

watch(
  () => route.params.id,
  () => {
    stopPolling()
    void load()
  },
)

onMounted(() => {
  document.addEventListener('visibilitychange', onVisibilityChange)
  void load()
})

onBeforeUnmount(() => {
  stopPolling()
  document.removeEventListener('visibilitychange', onVisibilityChange)
  thumbObserver?.disconnect()
  thumbObserver = null
  thumbItemByKey.clear()
  thumbWaitQueue.length = 0
  thumbActiveFetches = 0
})
</script>

<style scoped lang="scss">
.mission-detail {
  padding-bottom: 24px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.title {
  font-size: 16px;
}

.summary-row {
  margin-bottom: 16px;
}

.stat-card {
  background: #141414;
  border: 1px solid #2b2b2c;
  border-radius: 8px;
  padding: 14px 12px;
  text-align: center;

  .num {
    font-size: 22px;
    font-weight: 700;
    color: #e5eaf3;
  }

  .label {
    margin-top: 4px;
    font-size: 12px;
    color: #8b919a;
  }

  &.pending .num { color: #909399; }
  &.running .num { color: #e6a23c; }
  &.done .num { color: #67c23a; }
  &.fail .num { color: #f56c6c; }
  &.cancel .num { color: #a3a6ad; }
}

.block {
  margin-top: 16px;
}

.block-title {
  font-weight: 600;
  margin-bottom: 10px;
}

/* 行高对齐采集媒体约 3 行缩略图（40px + gap 8px），顶部留白避免删除叉号被裁切 */
$media-row-pad-top: 8px;
$media-row-h: calc(3 * 40px + 2 * 8px + #{$media-row-pad-top} + 4px);

.exec-table {
  :deep(.el-table__body .el-table__cell) {
    vertical-align: middle;
    height: $media-row-h;
    padding-top: 6px;
    padding-bottom: 6px;
  }

  :deep(.el-table__fixed-right .el-table__body .el-table__cell),
  :deep(.el-table__fixed .el-table__body .el-table__cell) {
    height: $media-row-h;
  }
}

.media-cell {
  display: flex;
  align-items: flex-start;
  align-content: flex-start;
  gap: 8px;
  flex-wrap: wrap;
  height: $media-row-h;
  max-height: $media-row-h;
  overflow-x: hidden;
  overflow-y: auto;
  padding: $media-row-pad-top 8px 0 0;
  box-sizing: border-box;
}

.thumb-wrap {
  position: relative;
  display: inline-flex;
  flex: 0 0 auto;
}

.thumb-del {
  position: absolute;
  top: -6px;
  right: -6px;
  z-index: 2;
  width: 16px;
  height: 16px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: #f56c6c;
  color: #fff;
  font-size: 12px;
  line-height: 16px;
  text-align: center;
  cursor: pointer;
  box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.25);

  &:hover:not(:disabled) {
    background: #e04545;
  }

  &:disabled {
    opacity: 0.55;
    cursor: wait;
  }
}

.recon-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  max-width: 100%;
}

.thumb {
  width: 40px;
  height: 40px;
  border-radius: 4px;
  overflow: hidden;
  background: #1f1f1f;
  border: 1px solid #2b2b2c;
  display: grid;
  place-items: center;
  color: #cfd3dc;
  font-size: 12px;
  cursor: pointer;

  &.video {
    background: #243044;
  }

  &.placeholder.busy {
    color: #8b919a;
  }

  .dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: #8b919a;
    animation: media-pulse 0.9s ease-in-out infinite;
  }
}

@keyframes media-pulse {
  0%,
  100% {
    opacity: 0.35;
    transform: scale(0.85);
  }
  50% {
    opacity: 1;
    transform: scale(1);
  }
}

.more {
  color: #8b919a;
  font-size: 12px;
}

.muted {
  color: #8b919a;
}

.player {
  width: 100%;
  max-height: 70vh;
  background: #000;
}
</style>
