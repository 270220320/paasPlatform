<template>
  <div>
    <el-row :gutter="16" class="mb">
      <el-col :span="24">
        <el-card shadow="never" class="panel">
          <template #header>{{ t('algorithms.capabilities') }}</template>
          <el-descriptions v-if="caps" :column="2" border>
            <el-descriptions-item :label="t('algorithms.multimodalAvailable')">{{ caps.multimodalAvailable ? t('common.yes') : t('common.no') }}</el-descriptions-item>
            <el-descriptions-item :label="t('algorithms.enabledDetectionCount')">{{ caps.enabledDetectionCount }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <div class="toolbar">
      <el-button @click="load">{{ t('common.refresh') }}</el-button>
    </div>

    <el-table :data="list" border stripe>
      <el-table-column prop="name" :label="t('algorithms.algoName')" min-width="180" />
      <el-table-column prop="algoType" :label="t('common.type')" width="140" />
      <el-table-column prop="status" :label="t('common.status')" width="120" />
      <el-table-column prop="updatedAt" :label="t('algorithms.updatedAt')" width="180" />
      <el-table-column v-if="canWrite" :label="t('common.actions')" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="setStatus(row, 'enabled')">{{ t('algorithms.enable') }}</el-button>
          <el-button link type="warning" @click="setStatus(row, 'disabled')">{{ t('algorithms.disable') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <h3 class="section">{{ t('algorithms.alarms') }}</h3>
    <el-table :data="alarms" border stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="algorithmId" :label="t('algorithms.algorithmId')" width="100" />
      <el-table-column prop="targetLabel" :label="t('algorithms.target')" min-width="160" />
      <el-table-column prop="raisedAt" :label="t('algorithms.time')" width="180" />
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  fetchAlgorithmAlarms,
  fetchAlgorithmCapabilities,
  fetchAlgorithms,
  updateAlgorithm,
  type AlgorithmVO,
} from '@/api/algorithm'
import { useUserStore } from '@/stores/user'

const { t } = useI18n()
const userStore = useUserStore()
const canWrite = computed(() => !!userStore.user?.permissions?.includes('algorithm:write'))
const list = ref<AlgorithmVO[]>([])
const caps = ref<{ multimodalAvailable: boolean; enabledDetectionCount: number } | null>(null)
const alarms = ref<Array<{ id: number; algorithmId: number; targetLabel?: string; raisedAt: string }>>([])

async function load() {
  const [a, c, al] = await Promise.all([
    fetchAlgorithms(),
    fetchAlgorithmCapabilities(),
    fetchAlgorithmAlarms({ page: 1, pageSize: 50 }),
  ])
  list.value = a.data.data || []
  caps.value = c.data.data
  const page = al.data.data as { list?: typeof alarms.value } | typeof alarms.value
  alarms.value = Array.isArray(page) ? page : page.list || []
}

async function setStatus(row: AlgorithmVO, status: string) {
  await updateAlgorithm(row.id, { status })
  ElMessage.success(t('algorithms.updated'))
  await load()
}

onMounted(load)
</script>

<style scoped lang="scss">
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
.panel {
  background: #141414;
  border-color: #414243;
  color: #e5eaf3;
}
.mb {
  margin-bottom: 16px;
}
.section {
  margin: 24px 0 12px;
  color: #e5eaf3;
  font-size: 16px;
  font-weight: 600;
}
</style>
