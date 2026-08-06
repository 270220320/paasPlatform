<template>
  <div>
    <div class="toolbar">
      <el-input
        v-model="keyword"
        clearable
        :placeholder="t('cockpit.searchPlaceholder')"
        style="width: 240px"
        @keyup.enter="load"
      />
      <el-button @click="load">{{ t('common.refresh') }}</el-button>
    </div>

    <el-table v-loading="loading" :data="docks" border stripe>
      <el-table-column prop="name" :label="t('cockpit.dockName')" min-width="180" />
      <el-table-column prop="sn" :label="t('cockpit.gatewaySn')" min-width="180" />
      <el-table-column prop="droneSn" :label="t('cockpit.aircraftSn')" min-width="180">
        <template #default="{ row }">{{ row.droneSn || '-' }}</template>
      </el-table-column>
      <el-table-column prop="model" :label="t('cockpit.model')" min-width="140" />
      <el-table-column prop="onlineState" :label="t('cockpit.onlineStatus')" width="120">
        <template #default="{ row }">{{ formatOnline(row.onlineState) }}</template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="120" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            @click="openCockpit(row)"
          >
            {{ t('cockpit.open') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty
      v-if="!loading && !docks.length"
      :description="t('cockpit.noDock')"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { fetchCockpitDevices } from '@/api/paas'
import type { DeviceVO } from '@/api/device'
import { usePaasBootstrap } from '@/composables/usePaasBootstrap'

const { t } = useI18n()
const router = useRouter()
const { load: loadPaasConfig } = usePaasBootstrap()
const keyword = ref('')
const loading = ref(false)
const devices = ref<DeviceVO[]>([])

const docks = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return devices.value.filter((device) => {
    // 设备管理接口以机场为主记录，关联飞行器位于 device_state.sub_device。
    // 排除独立返回的飞行器记录，保留具备机场 SN 的记录。
    if (!device.gatewaySn || device.deviceType.toLowerCase() === 'aircraft') return false
    if (!query) return true
    return [device.name, device.sn, device.gatewaySn, device.droneSn, device.model]
      .some((value) => value?.toLowerCase().includes(query))
  })
})

function formatOnline(value?: string) {
  const text = String(value || '').trim().toLowerCase()
  if (!text) return '-'
  if (['1', 'true', 'online', 'on'].includes(text)) return t('cockpit.online')
  if (['0', 'false', 'offline', 'off'].includes(text)) return t('cockpit.offline')
  return value
}

async function load() {
  loading.value = true
  try {
    await loadPaasConfig()
    devices.value = await fetchCockpitDevices()
  } finally {
    loading.value = false
  }
}

function openCockpit(device: DeviceVO) {
  const gatewaySn = device.gatewaySn || device.sn
  const droneSn = device.droneSn || ''
  if (!gatewaySn || !droneSn) {
    ElMessage.warning(t('cockpit.noAircraft'))
    return
  }
  router.push({
    name: 'cockpit-detail',
    query: {
      gatewaySn,
      droneSn,
      name: device.name,
    },
  })
}

onMounted(load)
</script>

<style scoped lang="scss">
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
</style>
