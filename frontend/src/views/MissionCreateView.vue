<template>
  <div class="mission-create-page">
    <aside class="form-panel">
      <div class="form-header">{{ t('missions.createTitle') }}</div>
      <el-scrollbar class="form-scroll">
        <el-form :model="form" label-position="top" class="create-form">
          <el-form-item :label="t('missions.planName')" required>
            <el-input v-model="form.name" :placeholder="t('missions.planNamePlaceholder')" />
          </el-form-item>

          <el-form-item :label="t('missions.taskType')" required>
            <el-radio-group v-model="form.taskType" class="seg-group" @change="onTaskTypeChange">
              <el-radio-button value="normal">{{ t('missions.taskNormal') }}</el-radio-button>
              <el-radio-button value="wayline">{{ t('missions.taskWayline') }}</el-radio-button>
              <el-radio-button value="manual">{{ t('missions.taskManual') }}</el-radio-button>
            </el-radio-group>
          </el-form-item>

          <el-form-item v-if="needsWayline" :label="t('missions.executeWayline')" required>
            <el-button class="picker-btn" @click="waylinePickerVisible = true">
              <el-icon v-if="!selectedWayline"><Plus /></el-icon>
              <span>{{ selectedWayline ? selectedWayline.name : t('missions.selectWayline') }}</span>
            </el-button>
            <el-button
              v-if="selectedWayline"
              link
              type="danger"
              class="clear-btn"
              @click="clearWayline"
            >
              {{ t('common.delete') }}
            </el-button>
          </el-form-item>

          <el-form-item :label="t('missions.selectDevice')" required>
            <el-button class="picker-btn" @click="devicePickerVisible = true">
              <el-icon v-if="!selectedDevice"><Plus /></el-icon>
              <span>
                {{
                  selectedDevice
                    ? `${selectedDevice.name}（${selectedDevice.gatewaySn || selectedDevice.sn}）`
                    : t('missions.selectDeviceBtn')
                }}
              </span>
            </el-button>
          </el-form-item>

          <el-form-item :label="t('missions.strategy')" required>
            <el-radio-group v-model="form.strategy" class="seg-group seg-wrap">
              <el-radio-button value="immediate">{{ t('missions.immediate') }}</el-radio-button>
              <el-radio-button value="once">{{ t('missions.once') }}</el-radio-button>
              <el-radio-button v-if="supportsCycleStrategy" value="recurring">{{ t('missions.recurring') }}</el-radio-button>
              <el-radio-button v-if="supportsCycleStrategy" value="continuous">{{ t('missions.continuous') }}</el-radio-button>
            </el-radio-group>
          </el-form-item>

          <!-- 单次定时 -->
          <template v-if="form.strategy === 'once'">
            <el-form-item :label="t('missions.executeDate')" required>
              <el-date-picker
                v-model="form.executeDate"
                type="date"
                value-format="YYYY-MM-DD"
                :placeholder="t('missions.executeDatePlaceholder')"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item :label="t('missions.executeTime')" required>
              <div class="time-row">
                <el-time-picker
                  v-model="form.timeWindows[0].start"
                  format="HH:mm"
                  value-format="HH:mm"
                  :placeholder="t('missions.earliestStart')"
                  style="width: 46%"
                />
                <span class="time-sep">-</span>
                <el-time-picker
                  v-model="form.timeWindows[0].end"
                  format="HH:mm"
                  value-format="HH:mm"
                  :placeholder="t('missions.latestStart')"
                  style="width: 46%"
                />
              </div>
            </el-form-item>
          </template>

          <!-- 重复定时 / 连续执行（仅航线类 + dji2） -->
          <template v-if="supportsCycleStrategy && (form.strategy === 'recurring' || form.strategy === 'continuous')">
            <el-form-item :label="t('missions.executeDate')" required>
              <el-date-picker
                v-model="form.dateRange"
                type="daterange"
                value-format="YYYY-MM-DD"
                :start-placeholder="t('missions.dateStart')"
                :end-placeholder="t('missions.dateEnd')"
                style="width: 100%"
              />
            </el-form-item>
            <el-form-item :label="t('missions.executeTime')" required>
              <div
                v-for="(tw, idx) in form.timeWindows"
                :key="idx"
                class="time-row multi"
              >
                <el-time-picker
                  v-model="tw.start"
                  format="HH:mm"
                  value-format="HH:mm"
                  :placeholder="form.strategy === 'continuous' ? t('missions.timeStart') : t('missions.earliestStart')"
                  style="width: 38%"
                />
                <span class="time-sep">-</span>
                <el-time-picker
                  v-model="tw.end"
                  format="HH:mm"
                  value-format="HH:mm"
                  :placeholder="form.strategy === 'continuous' ? t('missions.timeEnd') : t('missions.latestStart')"
                  style="width: 38%"
                />
                <el-button
                  circle
                  type="primary"
                  :icon="Plus"
                  size="small"
                  @click="addTimeWindow"
                />
                <el-button
                  circle
                  :icon="Minus"
                  size="small"
                  :disabled="form.timeWindows.length <= 1"
                  @click="removeTimeWindow(idx)"
                />
              </div>
            </el-form-item>
            <el-form-item
              v-if="form.strategy === 'continuous'"
              :label="t('missions.minBattery')"
              required
            >
              <div class="battery-row">
                <el-input-number v-model="form.minBatteryPercent" :min="1" :max="100" :controls="false" />
                <span class="unit">%</span>
              </div>
            </el-form-item>
            <el-form-item :label="t('missions.repeatFrequency')" required>
              <div class="freq-row">
                <span>{{ t('missions.every') }}</span>
                <el-input-number
                  v-model="form.freqInterval"
                  :min="1"
                  :max="365"
                  :controls="false"
                  class="freq-input"
                />
                <el-select v-model="form.freqUnit" style="width: 100px">
                  <el-option :label="t('missions.freqDay')" value="day" />
                  <el-option :label="t('missions.freqWeek')" value="week" />
                  <el-option :label="t('missions.freqMonth')" value="month" />
                </el-select>
              </div>
            </el-form-item>
          </template>

          <!-- 仅立即/单次走 FlightHub SDK createFlightTask 时展示 -->
          <template v-if="showSdkFlightParams">
            <el-form-item :label="t('missions.accuracyMode')" required>
              <el-radio-group v-model="form.accuracyMode" class="seg-group">
                <el-radio-button value="rtk">{{ t('missions.accuracyRtk') }}</el-radio-button>
                <el-radio-button value="standard">{{ t('missions.accuracyGnss') }}</el-radio-button>
              </el-radio-group>
            </el-form-item>

            <el-form-item :label="t('missions.breakpointResume')">
              <div class="switch-row">
                <span class="switch-hint">{{ t('missions.breakpointResumeHint') }}</span>
                <el-switch v-model="form.breakpointResume" />
              </div>
            </el-form-item>

            <el-form-item :label="t('missions.smartRth')">
              <div class="switch-row">
                <span class="switch-hint">{{ t('missions.smartRthHint') }}</span>
                <el-switch v-model="form.smartRth" />
              </div>
            </el-form-item>

            <el-form-item :label="t('missions.minFlightHeight')" required>
              <div class="height-row">
                <el-button size="small" @click="adjustHeight(-100)">-100</el-button>
                <el-button size="small" @click="adjustHeight(-10)">-10</el-button>
                <el-button size="small" @click="adjustHeight(-1)">-1</el-button>
                <el-input-number
                  v-model="form.minFlightHeightM"
                  :min="20"
                  :max="1500"
                  :controls="false"
                  class="height-input"
                />
                <span class="unit">m</span>
                <el-button size="small" @click="adjustHeight(1)">+1</el-button>
                <el-button size="small" @click="adjustHeight(10)">+10</el-button>
                <el-button size="small" @click="adjustHeight(100)">+100</el-button>
              </div>
            </el-form-item>

            <el-form-item :label="t('missions.signalLostAction')" required>
              <el-radio-group v-model="form.signalLostAction" class="seg-group">
                <el-radio-button value="rth">{{ t('missions.rth') }}</el-radio-button>
                <el-radio-button value="continue">{{ t('missions.continueExec') }}</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </template>
        </el-form>
      </el-scrollbar>

      <div class="form-footer">
        <el-button @click="goBack">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="submit">{{ t('common.confirm') }}</el-button>
      </div>
    </aside>

    <WaylinePreviewPane :wayline-uuid="previewUuid" />

    <el-dialog v-model="waylinePickerVisible" :title="t('missions.selectWayline')" width="640px">
      <el-input
        v-model="waylineKeyword"
        clearable
        :placeholder="t('waylines.namePlaceholder')"
        style="margin-bottom: 12px"
        @keyup.enter="loadWaylines"
      />
      <el-scrollbar max-height="420px">
        <div
          v-for="w in waylines"
          :key="w.id"
          class="picker-item"
          :class="{ active: form.waylineId === w.id }"
          @click="pickWayline(w)"
        >
          <div class="picker-name">{{ w.name }}</div>
          <div class="picker-meta">{{ w.aircraftModel || '-' }} · {{ w.updatedAt || '-' }}</div>
        </div>
        <el-empty v-if="!waylines.length" :description="t('waylines.empty')" />
      </el-scrollbar>
    </el-dialog>

    <el-dialog v-model="devicePickerVisible" :title="t('missions.selectDevice')" width="560px">
      <el-scrollbar v-loading="devicesLoading" max-height="420px">
        <div
          v-for="d in devices"
          :key="d.gatewaySn || d.sn"
          class="picker-item"
          :class="{ active: form.deviceSn === (d.gatewaySn || d.sn) }"
          @click="pickDevice(d)"
        >
          <div class="picker-name">{{ d.name }}</div>
          <div class="picker-meta">
            {{ d.gatewaySn || d.sn }}
            <template v-if="d.droneSn"> · {{ d.droneSn }}</template>
            · {{ d.model || d.deviceType || '-' }}
          </div>
        </div>
        <el-empty v-if="!devicesLoading && !devices.length" :description="t('missions.noDevices')" />
      </el-scrollbar>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Plus, Minus } from '@element-plus/icons-vue'
import WaylinePreviewPane from '@/components/WaylinePreviewPane.vue'
import type { DeviceVO } from '@/api/device'
import { fetchCockpitDevices } from '@/api/paas'
import { createMission } from '@/api/mission'
import { fetchWaylines, type WaylineVO } from '@/api/wayline'
import { usePaasBootstrap } from '@/composables/usePaasBootstrap'

const { t } = useI18n()
const router = useRouter()
const { load: loadPaasConfig } = usePaasBootstrap()

const saving = ref(false)
const devicesLoading = ref(false)
const allDevices = ref<DeviceVO[]>([])
const waylines = ref<WaylineVO[]>([])
const waylineKeyword = ref('')
const waylinePickerVisible = ref(false)
const devicePickerVisible = ref(false)
const selectedWayline = ref<WaylineVO | null>(null)
const selectedDevice = ref<DeviceVO | null>(null)

/** 与驾驶舱一致：设备管理接口以机场为主，排除独立飞行器记录 */
const devices = computed(() =>
  allDevices.value.filter((device) => {
    if (!device.gatewaySn || device.deviceType.toLowerCase() === 'aircraft') return false
    return true
  }),
)

const form = reactive({
  name: t('missions.createTitle'),
  taskType: 'wayline',
  deviceSn: '' as string,
  waylineId: undefined as number | undefined,
  strategy: 'immediate',
  executeDate: '' as string,
  dateRange: [] as string[],
  timeWindows: [{ start: '', end: '' }] as Array<{ start: string; end: string }>,
  freqInterval: 1,
  freqUnit: 'day',
  minBatteryPercent: 90,
  accuracyMode: 'rtk',
  minFlightHeightM: 30,
  signalLostAction: 'rth',
  breakpointResume: false,
  smartRth: false,
})

const needsWayline = computed(() => form.taskType === 'normal' || form.taskType === 'wayline')
/** 周期/连续走 dji2，手动任务不下发司空 */
const supportsCycleStrategy = computed(() => needsWayline.value)
/** 立即/单次走 createFlightTask，才支持精度/断点/返航等 */
const showSdkFlightParams = computed(
  () => needsWayline.value && (form.strategy === 'immediate' || form.strategy === 'once'),
)
const previewUuid = computed(() =>
  needsWayline.value ? selectedWayline.value?.fh2WaylineUuid || '' : '',
)

watch(
  () => form.strategy,
  (strategy) => {
    if (!form.timeWindows.length) {
      form.timeWindows.push({ start: '', end: '' })
    }
    if (strategy === 'once' && form.timeWindows.length > 1) {
      form.timeWindows.splice(1)
    }
  },
)

watch(
  () => form.taskType,
  () => {
    if (!supportsCycleStrategy.value && (form.strategy === 'recurring' || form.strategy === 'continuous')) {
      form.strategy = 'immediate'
    }
  },
)

function goBack() {
  router.push({ name: 'missions' })
}

function adjustHeight(delta: number) {
  const next = Number(form.minFlightHeightM || 0) + delta
  form.minFlightHeightM = Math.min(1500, Math.max(20, next))
}

function addTimeWindow() {
  form.timeWindows.push({ start: '', end: '' })
}

function removeTimeWindow(idx: number) {
  if (form.timeWindows.length <= 1) return
  form.timeWindows.splice(idx, 1)
}

function validateSchedule(): string | null {
  if (form.strategy === 'immediate') return null
  if (form.strategy === 'once') {
    if (!form.executeDate) return t('missions.executeDateRequired')
    const tw = form.timeWindows[0]
    if (!tw?.start || !tw?.end) return t('missions.executeTimeRequired')
    if (tw.end < tw.start) return t('missions.timeRangeInvalid')
    return null
  }
  if (!form.dateRange?.[0] || !form.dateRange?.[1]) return t('missions.dateRangeRequired')
  if (form.dateRange[1] < form.dateRange[0]) return t('missions.dateRangeInvalid')
  for (const tw of form.timeWindows) {
    if (!tw.start || !tw.end) return t('missions.executeTimeRequired')
    if (tw.end < tw.start) return t('missions.timeRangeInvalid')
  }
  if (!form.freqInterval || form.freqInterval < 1) return t('missions.freqRequired')
  if (form.strategy === 'continuous') {
    if (form.minBatteryPercent == null || form.minBatteryPercent < 1 || form.minBatteryPercent > 100) {
      return t('missions.batteryRequired')
    }
  }
  return null
}

function onTaskTypeChange() {
  if (!needsWayline.value) {
    clearWayline()
  }
  if (!supportsCycleStrategy.value && (form.strategy === 'recurring' || form.strategy === 'continuous')) {
    form.strategy = 'immediate'
  }
}

function clearWayline() {
  selectedWayline.value = null
  form.waylineId = undefined
}

function pickWayline(w: WaylineVO) {
  if (!w.id) {
    ElMessage.warning(t('missions.waylineLocalMissing'))
    return
  }
  if (!w.fh2WaylineUuid) {
    ElMessage.warning(t('missions.waylinePreviewUnavailable'))
  }
  selectedWayline.value = w
  form.waylineId = w.id
  waylinePickerVisible.value = false
}

function pickDevice(d: DeviceVO) {
  selectedDevice.value = d
  form.deviceSn = d.gatewaySn || d.sn
  devicePickerVisible.value = false
}

async function loadWaylines() {
  const { data } = await fetchWaylines({
    keyword: waylineKeyword.value || undefined,
    status: 'published',
    page: 1,
    pageSize: 100,
  })
  const pageData = data.data as { list: WaylineVO[] }
  waylines.value = pageData.list || []
}

async function loadDevices() {
  devicesLoading.value = true
  try {
    await loadPaasConfig()
    allDevices.value = await fetchCockpitDevices()
  } catch (e: unknown) {
    allDevices.value = []
    ElMessage.error(e instanceof Error ? e.message : t('missions.deviceLoadFailed'))
  } finally {
    devicesLoading.value = false
  }
}

async function submit() {
  if (!form.name.trim()) {
    ElMessage.warning(t('missions.planNameRequired'))
    return
  }
  if (!form.deviceSn) {
    ElMessage.warning(t('missions.selectDeviceRequired'))
    return
  }
  if (needsWayline.value && !form.waylineId) {
    ElMessage.warning(t('missions.selectWaylineRequired'))
    return
  }
  const scheduleError = validateSchedule()
  if (scheduleError) {
    ElMessage.warning(scheduleError)
    return
  }
  saving.value = true
  try {
    const payload: Record<string, unknown> = {
      name: form.name.trim(),
      taskType: form.taskType,
      deviceSn: form.deviceSn,
      waylineId: needsWayline.value ? form.waylineId : undefined,
      strategy: form.strategy,
      // 隐藏字段仍传默认值，满足后端必填校验；周期/手动不会下发到 SDK
      accuracyMode: showSdkFlightParams.value ? form.accuracyMode : 'rtk',
      minFlightHeightM: showSdkFlightParams.value
        ? Number(form.minFlightHeightM ?? 30) || 30
        : 30,
      signalLostAction: showSdkFlightParams.value ? form.signalLostAction || 'rth' : 'rth',
      breakpointResume: showSdkFlightParams.value ? !!form.breakpointResume : false,
      smartRth: showSdkFlightParams.value ? !!form.smartRth : false,
      multimodalEnabled: false,
    }
    if (form.strategy === 'once') {
      payload.executeDate = form.executeDate
      payload.timeWindows = [{ start: form.timeWindows[0].start, end: form.timeWindows[0].end }]
    } else if (form.strategy === 'recurring' || form.strategy === 'continuous') {
      payload.dateStart = form.dateRange[0]
      payload.dateEnd = form.dateRange[1]
      payload.timeWindows = form.timeWindows.map((tw) => ({ start: tw.start, end: tw.end }))
      payload.freqInterval = form.freqInterval
      payload.freqUnit = form.freqUnit
      if (form.strategy === 'continuous') {
        payload.minBatteryPercent = form.minBatteryPercent
      }
    }
    await createMission(payload)
    ElMessage.success(t('missions.created'))
    goBack()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadDevices(), loadWaylines()])
})
</script>

<style scoped lang="scss">
.mission-create-page {
  display: flex;
  height: 100%;
  min-height: 0;
  background: #0a0a0a;
  color: #e5eaf3;
}

.form-panel {
  width: 380px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #2b2b2c;
  background: #141414;
  min-height: 0;
}

.form-header {
  height: 52px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  font-size: 16px;
  font-weight: 600;
  border-bottom: 1px solid #2b2b2c;
}

.form-scroll {
  flex: 1;
  min-height: 0;
  padding: 12px 16px;
}

.create-form {
  :deep(.el-form-item__label) {
    color: #cfd3dc;
  }
}

.seg-group {
  display: flex;
  width: 100%;

  :deep(.el-radio-button) {
    flex: 1;
  }

  :deep(.el-radio-button__inner) {
    width: 100%;
  }
}

.seg-wrap {
  flex-wrap: wrap;

  :deep(.el-radio-button) {
    flex: 1 1 45%;
  }
}

.picker-btn {
  width: 100%;
  justify-content: flex-start;
  height: 40px;
  border-style: dashed;
}

.clear-btn {
  margin-top: 4px;
}

.switch-row {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.switch-hint {
  color: #8b919a;
  font-size: 12px;
}

.height-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
}

.height-input {
  width: 72px;
}

.unit {
  color: #8b919a;
  margin: 0 4px;
}

.time-row {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;

  &.multi {
    margin-bottom: 8px;
  }
}

.time-sep {
  color: #8b919a;
}

.freq-row,
.battery-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.freq-input {
  width: 72px;
}

.form-footer {
  display: flex;
  gap: 12px;
  padding: 12px 16px;
  border-top: 1px solid #2b2b2c;

  .el-button {
    flex: 1;
  }
}

.picker-item {
  padding: 12px;
  margin-bottom: 8px;
  border-radius: 8px;
  border: 1px solid #2b2b2c;
  background: #1a1a1a;
  cursor: pointer;

  &:hover {
    border-color: #3a3a3c;
  }

  &.active {
    border-color: #409eff;
    background: rgba(64, 158, 255, 0.12);
  }
}

.picker-name {
  font-weight: 600;
  margin-bottom: 4px;
}

.picker-meta {
  font-size: 12px;
  color: #8b919a;
}
</style>
