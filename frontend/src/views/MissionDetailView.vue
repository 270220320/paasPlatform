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
        <el-table :data="detail.executions || []" border stripe>
          <el-table-column type="index" width="60" :label="t('missions.execIndex')" />
          <el-table-column :label="t('missions.execRecordType')" width="110">
            <template #default="{ row }">
              {{ row.recordType === 'planned' ? t('missions.recordPlanned') : t('missions.recordExecution') }}
            </template>
          </el-table-column>
          <el-table-column :label="t('common.status')" width="120">
            <template #default="{ row }">
              <el-tag :type="execTagType(row.status)" size="small">{{ execStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="plannedAt" :label="t('missions.plannedAt')" min-width="170" />
          <el-table-column prop="actualStartAt" :label="t('missions.actualStartAt')" min-width="170">
            <template #default="{ row }">{{ row.actualStartAt || '-' }}</template>
          </el-table-column>
          <el-table-column prop="actualEndAt" :label="t('missions.actualEndAt')" min-width="170">
            <template #default="{ row }">{{ row.actualEndAt || '-' }}</template>
          </el-table-column>
          <el-table-column :label="t('missions.flightDuration')" width="120">
            <template #default="{ row }">{{ row.flightDurationSec != null ? `${row.flightDurationSec}s` : '-' }}</template>
          </el-table-column>
          <el-table-column :label="t('missions.flightDistance')" width="120">
            <template #default="{ row }">{{ row.flightDistanceM != null ? `${row.flightDistanceM}m` : '-' }}</template>
          </el-table-column>
          <el-table-column :label="t('missions.collectedMedia')" min-width="220">
            <template #default="{ row }">
              <div v-if="(row.media || []).length" class="media-cell">
                <template v-for="(item, idx) in visibleMedia(row)" :key="`${row.id}-${item.id || idx}`">
                  <el-image
                    v-if="isImage(item) && previewMap[mediaKey(item)]"
                    class="thumb"
                    :src="previewMap[mediaKey(item)]"
                    :preview-src-list="[previewMap[mediaKey(item)]]"
                    :initial-index="0"
                    fit="cover"
                    preview-teleported
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
                    :title="item.name"
                    @click="openImage(item)"
                  >
                    图
                  </div>
                  <div v-else class="thumb placeholder" :title="item.name">媒</div>
                </template>
                <span v-if="(row.media || []).length > 3" class="more">
                  {{ t('missions.mediaMore', { n: (row.media || []).length - 3 }) }}
                </span>
              </div>
              <span v-else class="muted">{{ t('missions.noMedia') }}</span>
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
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { fetchMediaDownloadUrl } from '@/api/library'
import {
  fetchMissionDetail,
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
const videoVisible = ref(false)
const playing = ref<ExecutionMediaVO | null>(null)
const playingUrl = ref('')
const imageVisible = ref(false)
const imagePreviewUrl = ref('')
const imagePreviewName = ref('')
let pollTimer: ReturnType<typeof setInterval> | null = null
let loadSeq = 0

function goBack() {
  router.push({ name: 'missions' })
}

function mediaKey(item: ExecutionMediaVO) {
  return String(item.id ?? item.name ?? '')
}

function isImage(item: ExecutionMediaVO) {
  const tpe = (item.mediaType || '').toLowerCase()
  return tpe.includes('image') || tpe.includes('photo')
}

function isVideo(item: ExecutionMediaVO) {
  return (item.mediaType || '').toLowerCase().includes('video')
}

function visibleMedia(row: MissionExecutionVO) {
  return (row.media || []).slice(0, 3)
}

async function openImage(item: ExecutionMediaVO) {
  if (!item.id) {
    ElMessage.warning(t('library.noVideoUrl'))
    return
  }
  try {
    let url = previewMap[mediaKey(item)]
    if (!url) {
      const { data } = await fetchMediaDownloadUrl(item.id)
      url = data.data?.downloadUrl || ''
      if (url) previewMap[mediaKey(item)] = url
    }
    if (!url) {
      ElMessage.warning(t('library.loadFailed'))
      return
    }
    imagePreviewUrl.value = url
    imagePreviewName.value = item.name || ''
    imageVisible.value = true
  } catch {
    ElMessage.error(t('library.loadFailed'))
  }
}

async function hydratePreviews(executions: MissionExecutionVO[]) {
  const pending: ExecutionMediaVO[] = []
  for (const row of executions) {
    for (const item of row.media || []) {
      if (!item.id || previewMap[mediaKey(item)]) continue
      if (!isImage(item) && !isVideo(item)) continue
      pending.push(item)
    }
  }
  const chunk = pending.slice(0, 30)
  await Promise.all(
    chunk.map(async (item) => {
      try {
        const { data } = await fetchMediaDownloadUrl(item.id!)
        const url = data.data?.downloadUrl
        if (url) previewMap[mediaKey(item)] = url
      } catch {
        // 本地媒体或非司空 fileId 时忽略
      }
    }),
  )
}

async function openVideo(item: ExecutionMediaVO) {
  playing.value = item
  playingUrl.value = ''
  if (!item.id) {
    ElMessage.warning(t('library.noVideoUrl'))
    return
  }
  try {
    let url = previewMap[mediaKey(item)]
    if (!url) {
      const { data } = await fetchMediaDownloadUrl(item.id)
      url = data.data?.downloadUrl || ''
      if (url) previewMap[mediaKey(item)] = url
    }
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
  return (data.executions || []).some((row) =>
    ['pending', 'scheduled', 'running'].includes((row.status || '').toLowerCase()),
  )
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
    await hydratePreviews(data.data?.executions || [])
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

.media-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
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

  &.placeholder {
    cursor: default;
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
