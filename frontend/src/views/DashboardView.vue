<template>
  <div>
    <div class="toolbar">
      <el-radio-group v-model="range" @change="load">
        <el-radio-button value="7d">近 7 天</el-radio-button>
        <el-radio-button value="30d">近 30 天</el-radio-button>
        <el-radio-button value="90d">近 90 天</el-radio-button>
      </el-radio-group>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-row :gutter="16" v-if="summary">
      <el-col :span="6" v-for="item in cards" :key="item.label">
        <el-card shadow="never" class="stat-card">
          <div class="label">{{ item.label }}</div>
          <div class="value">{{ item.value }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="mt">
      <el-col :span="12">
        <el-card shadow="never" class="panel">
          <template #header>机场状态</template>
          <el-descriptions v-if="summary" :column="2" border>
            <el-descriptions-item label="空闲">{{ summary.dockStatus.idle }}</el-descriptions-item>
            <el-descriptions-item label="作业中">{{ summary.dockStatus.working }}</el-descriptions-item>
            <el-descriptions-item label="调试">{{ summary.dockStatus.debugging }}</el-descriptions-item>
            <el-descriptions-item label="离线">{{ summary.dockStatus.offline }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" class="panel">
          <template #header>存储配额</template>
          <el-descriptions v-if="summary" :column="1" border>
            <el-descriptions-item label="已用 / 总量">
              {{ formatBytes(summary.quota.storageUsedBytes) }} / {{ formatBytes(summary.quota.storageTotalBytes) }}
            </el-descriptions-item>
            <el-descriptions-item label="建图配额">
              {{ summary.quota.mappingUsed }} / {{ summary.quota.mappingQuota }}
            </el-descriptions-item>
            <el-descriptions-item label="直播分钟">
              {{ summary.quota.livestreamMinutesUsed }} / {{ summary.quota.livestreamMinutesTotal }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="mt">
      <el-col :span="12">
        <el-card shadow="never" class="panel">
          <template #header>飞行统计</template>
          <el-descriptions v-if="flight" :column="1" border>
            <el-descriptions-item label="架次">{{ flight.sortieCount }}</el-descriptions-item>
            <el-descriptions-item label="时长(秒)">{{ flight.durationSec }}</el-descriptions-item>
            <el-descriptions-item label="里程(米)">{{ flight.distanceM }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never" class="panel">
          <template #header>任务统计</template>
          <el-descriptions v-if="mission" :column="2" border>
            <el-descriptions-item label="计划">{{ mission.plannedCount }}</el-descriptions-item>
            <el-descriptions-item label="手动">{{ mission.manualCount }}</el-descriptions-item>
            <el-descriptions-item label="成功">{{ mission.successCount }}</el-descriptions-item>
            <el-descriptions-item label="暂停">{{ mission.pausedCount }}</el-descriptions-item>
            <el-descriptions-item label="失败">{{ mission.failedCount }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  fetchDashboardSummary,
  fetchFlightStats,
  fetchMissionStats,
  type DashboardSummary,
} from '@/api/dashboard'

const range = ref('30d')
const summary = ref<DashboardSummary | null>(null)
const flight = ref<{ sortieCount: number; durationSec: number; distanceM: number } | null>(null)
const mission = ref<{
  plannedCount: number
  manualCount: number
  successCount: number
  pausedCount: number
  failedCount: number
} | null>(null)

const cards = computed(() => {
  if (!summary.value) return []
  const s = summary.value
  return [
    { label: '飞行器', value: s.aircraftCount },
    { label: '成员', value: s.memberCount },
    { label: '航线', value: s.waylineCount },
    { label: '媒体', value: s.mediaCount },
    { label: '模型', value: s.modelCount },
    { label: '算法', value: s.algorithmCount },
    { label: 'AI 告警', value: s.aiAlarmCount },
    { label: '报告', value: s.reportCount },
  ]
})

function formatBytes(n: number) {
  if (!n && n !== 0) return '-'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let v = n
  let i = 0
  while (v >= 1024 && i < units.length - 1) {
    v /= 1024
    i++
  }
  return `${v.toFixed(i === 0 ? 0 : 1)} ${units[i]}`
}

async function load() {
  const params = { range: range.value }
  const [s, f, m] = await Promise.all([
    fetchDashboardSummary(params),
    fetchFlightStats(params),
    fetchMissionStats(params),
  ])
  summary.value = s.data.data
  flight.value = f.data.data
  mission.value = m.data.data
}

onMounted(load)
</script>

<style scoped lang="scss">
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  align-items: center;
}
.stat-card {
  background: #141414;
  border-color: #414243;
  margin-bottom: 16px;
  .label {
    color: #a3a6ad;
    font-size: 13px;
  }
  .value {
    margin-top: 8px;
    font-size: 28px;
    color: #e5eaf3;
    font-weight: 600;
  }
}
.panel {
  background: #141414;
  border-color: #414243;
  color: #e5eaf3;
}
.mt {
  margin-top: 8px;
}
</style>
