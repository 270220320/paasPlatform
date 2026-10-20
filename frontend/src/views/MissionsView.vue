<template>
  <div>
    <div class="toolbar">
      <el-button v-if="canWrite" type="primary" @click="openCreate">{{ t('missions.create') }}</el-button>
      <el-input v-model="keyword" clearable :placeholder="t('missions.namePlaceholder')" style="width: 200px" @keyup.enter="load" />
      <el-select v-model="status" clearable :placeholder="t('missions.statusPlaceholder')" style="width: 140px" @change="load">
        <el-option :label="t('missions.pending')" value="pending" />
        <el-option :label="t('missions.running')" value="running" />
        <el-option :label="t('missions.paused')" value="paused" />
        <el-option :label="t('missions.completed')" value="completed" />
        <el-option :label="t('missions.cancelled')" value="cancelled" />
        <el-option :label="t('missions.failed')" value="failed" />
      </el-select>
      <el-button @click="load">{{ t('common.refresh') }}</el-button>
    </div>

    <el-table :data="list" border stripe>
      <el-table-column prop="name" :label="t('missions.planName')" min-width="160" show-overflow-tooltip />
      <el-table-column :label="t('missions.taskType')" width="120">
        <template #default="{ row }">{{ taskTypeLabel(row.taskType) }}</template>
      </el-table-column>
      <el-table-column prop="deviceName" :label="t('missions.device')" min-width="140" show-overflow-tooltip />
      <el-table-column prop="waylineName" :label="t('missions.wayline')" min-width="140" show-overflow-tooltip />
      <el-table-column :label="t('missions.strategy')" width="110">
        <template #default="{ row }">{{ strategyLabel(row.strategy) }}</template>
      </el-table-column>
      <el-table-column :label="t('common.status')" width="100">
        <template #default="{ row }">{{ statusLabel(row.status) }}</template>
      </el-table-column>
      <el-table-column prop="createdAt" :label="t('missions.createdAt')" width="180" />
      <el-table-column v-if="canWrite || canRead" :label="t('common.actions')" width="320" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">{{ t('missions.detail') }}</el-button>
          <template v-if="canWrite">
            <el-button v-if="row.status === 'pending'" link type="primary" @click="doAction(row, 'pause')">
              {{ t('missions.pause') }}
            </el-button>
            <el-button v-if="row.status === 'paused'" link type="primary" @click="doAction(row, 'resume')">
              {{ t('missions.resume') }}
            </el-button>
            <el-button
              v-if="canCancel(row.status)"
              link
              type="warning"
              @click="doAction(row, 'cancel')"
            >
              {{ t('missions.cancelAction') }}
            </el-button>
            <el-button link type="danger" @click="onDelete(row)">{{ t('common.delete') }}</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        layout="total, prev, pager, next"
        :total="total"
        @current-change="load"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteMission,
  fetchMissions,
  missionAction,
  type MissionVO,
} from '@/api/mission'
import { useUserStore } from '@/stores/user'

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()
const canWrite = computed(() => !!userStore.user?.permissions?.includes('mission:write'))
const canRead = computed(() => !!userStore.user?.permissions?.includes('mission:read') || canWrite.value)
const list = ref<MissionVO[]>([])
const keyword = ref('')
const status = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

function taskTypeLabel(value?: string) {
  const map: Record<string, string> = {
    normal: t('missions.taskNormal'),
    wayline: t('missions.taskWayline'),
    leapfrog: t('missions.taskLeapfrog'),
    manual: t('missions.taskManual'),
  }
  return value ? map[value] || value : '-'
}

function strategyLabel(value?: string) {
  const map: Record<string, string> = {
    immediate: t('missions.immediate'),
    once: t('missions.once'),
    recurring: t('missions.recurring'),
    continuous: t('missions.continuous'),
  }
  return value ? map[value] || value : '-'
}

function statusLabel(value?: string) {
  const map: Record<string, string> = {
    pending: t('missions.pending'),
    running: t('missions.running'),
    paused: t('missions.paused'),
    completed: t('missions.completed'),
    cancelled: t('missions.cancelled'),
    canceled: t('missions.cancelled'),
    ended: t('missions.cancelled'),
    failed: t('missions.failed'),
  }
  return value ? map[value] || value : '-'
}

function canCancel(status?: string) {
  return status === 'pending' || status === 'running' || status === 'paused'
}

async function load() {
  const { data } = await fetchMissions({
    keyword: keyword.value || undefined,
    status: status.value || undefined,
    page: page.value,
    pageSize: pageSize.value,
  })
  const pageData = data.data as { list: MissionVO[]; total: number }
  list.value = pageData.list || []
  total.value = pageData.total || 0
}

function openCreate() {
  router.push({ name: 'mission-create' })
}

function openDetail(row: MissionVO) {
  router.push({ name: 'mission-detail', params: { id: String(row.id) } })
}

async function doAction(row: MissionVO, action: 'pause' | 'resume' | 'cancel') {
  try {
    await missionAction(row.id, action)
    ElMessage.success(t('missions.actionSuccess'))
    await load()
  } catch {
    // interceptor shows message
  }
}

async function onDelete(row: MissionVO) {
  await ElMessageBox.confirm(t('missions.deleteConfirm', { name: row.name }), t('common.confirmTitle'))
  await deleteMission(row.id)
  ElMessage.success(t('missions.deleted'))
  await load()
}

onMounted(load)
</script>

<style scoped lang="scss">
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
