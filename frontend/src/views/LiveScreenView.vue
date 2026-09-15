<template>
  <div class="live-page">
    <div class="toolbar">
      <el-select
        v-model="selectedSns"
        multiple
        filterable
        clearable
        collapse-tags
        collapse-tags-tooltip
        :placeholder="t('live.selectPlaceholder')"
        :loading="devicesLoading"
        class="device-select"
      >
        <el-option v-for="opt in deviceOptions" :key="opt.sn" :value="opt.sn" :label="opt.label">
          <span class="opt-name">{{ opt.name }}</span>
          <span class="opt-meta" :class="{ 'is-online': opt.online }">{{ opt.tag }} · {{ opt.type }}</span>
        </el-option>
      </el-select>
      <el-radio-group v-model="cols" class="layout-group">
        <el-radio-button :value="1">1×1</el-radio-button>
        <el-radio-button :value="2">2×2</el-radio-button>
        <el-radio-button :value="3">3×3</el-radio-button>
      </el-radio-group>
      <el-button :loading="devicesLoading" @click="loadDevices()">{{ t('live.refresh') }}</el-button>
      <span v-if="statusText" class="status">{{ statusText }}</span>
      <span v-else-if="overflowCount > 0" class="status status-warn">
        {{ t('live.overflowWarn', { n: selectedSns.length, m: cellCount }) }}
      </span>
    </div>

    <div class="grid-wrap">
      <!-- 固定 cols×cols 宫格骨架；空位显示占位，播放器挂载点由 rebuild 动态注入 -->
      <div ref="gridRef" class="live-grid" :style="{ gridTemplateColumns: `repeat(${cols}, 1fr)` }">
        <div v-for="i in cellCount" :key="i" class="live-cell">
          <div v-show="!snAt(i - 1)" class="cell-idle">
            <span class="cell-no">{{ i }}</span>
            <span class="cell-idle-text">{{ t('live.cellIdle') }}</span>
          </div>
          <!-- 出错/开机中时的操作条：正常播放不渲染，不遮挡画面交互 -->
          <div v-if="hasCellIssue(snAt(i - 1))" class="cell-ops">
            <span v-if="errTip(snAt(i - 1))" class="ops-err">{{ errTip(snAt(i - 1)) }}</span>
            <span v-else-if="stateTip(snAt(i - 1))" class="ops-state">{{ stateTip(snAt(i - 1)) }}</span>
            <el-button
              v-if="canPowerOn(snAt(i - 1))"
              size="small"
              type="primary"
              :loading="isPowering(snAt(i - 1))"
              @click="powerOn(snAt(i - 1))"
            >{{ t('live.powerOn') }}</el-button>
            <el-button size="small" :disabled="isPowering(snAt(i - 1))" @click="retrySn(snAt(i - 1))">{{ t('live.retry') }}</el-button>
          </div>
        </div>
      </div>
      <div v-if="bootError" class="center-tip">{{ bootError }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { usePaasBootstrap } from '@/composables/usePaasBootstrap'
import { fetchCockpitDevices, powerOnFh2Device } from '@/api/paas'
import type { DeviceVO } from '@/api/device'
import { loadScript, loadStylesheet } from '@/utils/loadScript'

/** 直播大屏独立字符串错误码（区别于航线编辑器的 4001/5000 数字体系） */
const ERROR_TIPS: Record<string, string> = {
  NOT_INITIALIZED: '播放器未初始化',
  INVALID_SN: 'SN 无效',
  DEVICE_NOT_FOUND: '设备不存在',
  DEVICE_OFFLINE: '设备离线',
  NO_LIVE_CAPACITY: '设备无直播能力',
  NO_LIVE_CAMERA: '无可用摄像头',
  NO_VIDEO_STREAM: '无可用视频流',
  START_LIVE_FAILED: '开播失败',
  TOKEN_EXPIRED: '登录态已过期',
  AUTH_FAILED: '鉴权失败',
  DUPLICATE_INSTANCE: '设备已在其他格子播放',
  NETWORK_DISCONNECTED: '网络连接断开',
}

/** SDK 状态机文案（stateChange 事件）；playing/idle 不展示 */
const STATE_TIPS: Record<string, string> = {
  resolving: '正在解析设备…',
  starting: '正在建立直播…',
  autoplay_blocked: '自动播放被浏览器阻止，正在静音重试…',
  offline: '设备离线',
  error: '播放出错',
  'need-auth': '镜头需要授权',
}

interface DjiLivePlayerInstance {
  on: (event: string, cb: (payload?: unknown) => void) => void
  destroy: () => void
  /** SDK 实例方法：重试拉流（error 状态重新开播）/重新查询设备，开机后恢复画面用 */
  retry?: () => void
  refresh?: () => void
}

declare global {
  interface Window {
    /** 直播大屏 SDK（完全独立、不依赖 paas.js）：initLivePlayer 全局初始化 + DjiLivePlayer 播放器类 */
    DJILivePlayer?: {
      initLivePlayer: (opts: Record<string, unknown>) => void
      DjiLivePlayer: new (opts: { container: string; sn: string; fillMode?: 'contain' | 'cover' | 'fill' }) => DjiLivePlayerInstance
    }
  }
}

/** initLivePlayer 全局仅需一次（多路播放器共用配置），路由往返回来不重复初始化 */
let livePlayerInited = false

/** 可上屏条目：机场主记录 + 由 droneSn 合成的飞行器条目（司空接口无独立飞行器记录） */
interface LiveEntry {
  sn: string
  name: string
  deviceType: string
  onlineState?: string
  /** 所属机场 SN（机场条目=自身；飞行器条目=来源机场），远程开机命令按机场下发 */
  dockSn: string
}

const { t } = useI18n()
const { load, authConfig } = usePaasBootstrap()

const statusText = ref('')
const bootError = ref('')
const devicesLoading = ref(false)
const entries = ref<LiveEntry[]>([])
const selectedSns = ref<string[]>([])
const cols = ref(2)
const gridRef = ref<HTMLElement | null>(null)
/** sn → 播放器实例；同一 SN 只允许一个实例，否则 DUPLICATE_INSTANCE */
const players = new Map<string, DjiLivePlayerInstance>()
/** sn → 设备名，错误提示用 */
const nameBySn = new Map<string, string>()
/** sn → 最近错误码（格子显示开机/重试操作条） */
const cellErrors = ref<Record<string, string>>({})
/** sn → SDK 状态机当前状态（stateChange），操作条实时展示进展 */
const cellStates = ref<Record<string, string>>({})
/** sn → 开机指令执行中 */
const powering = ref<Record<string, boolean>>({})
/** 本会话已自动下发过开机的 SN：SDK 恢复重试期间会反复报错，必须防重复下发 */
const autoPowerTried = new Set<string>()
/** 视为“飞行器关机”的错误码（设备不在拓扑/无直播能力/离线/无流），命中则自动开机 */
const AUTO_POWER_CODES = new Set(['DEVICE_OFFLINE', 'DEVICE_NOT_FOUND', 'NO_LIVE_CAPACITY', 'NO_VIDEO_STREAM'])
/** 开机后的恢复重试定时器（页面卸载必须清理） */
const recoveryTimers = new Set<number>()
let sdkReady = false

/** 宫格数固定为 cols×cols，与布局按钮严格对应 */
const cellCount = computed(() => cols.value * cols.value)
/** 选中路数超出当前布局容量的部分（状态栏提示，超出不渲染） */
const overflowCount = computed(() => selectedSns.value.length - cellCount.value)

const deviceOptions = computed(() =>
  entries.value.map((entry) => {
    const online = isOnline(entry.onlineState)
    const type = typeLabel(entry.deviceType)
    const tag = online ? t('live.online') : t('live.offline')
    return {
      sn: entry.sn,
      name: entry.name,
      type,
      tag,
      online,
      label: `${entry.name}（${type}·${tag}）`,
    }
  }),
)

function snAt(index: number) {
  return selectedSns.value[index]
}

function entryOf(sn?: string) {
  return sn ? entries.value.find((entry) => entry.sn === sn) : undefined
}

/** 飞行器条目才提供开机（命令按所属机场下发：调试模式→开舱盖→上电） */
function canPowerOn(sn?: string) {
  const entry = entryOf(sn)
  return !!entry && entry.deviceType.toLowerCase() === 'aircraft' && !!entry.dockSn
}

function isPowering(sn?: string) {
  return !!sn && !!powering.value[sn]
}

/** 格子是否显示操作条（出错/开机中/非播放态；正常播放不渲染，不遮挡画面） */
function hasCellIssue(sn?: string) {
  if (!sn) return false
  if (cellErrors.value[sn] || powering.value[sn]) return true
  const state = cellStates.value[sn]
  return !!state && !['playing', 'idle'].includes(state)
}

function stateTip(sn?: string) {
  if (!sn) return ''
  return STATE_TIPS[cellStates.value[sn] || ''] || cellStates.value[sn] || ''
}

function errTip(sn?: string) {
  if (!sn) return ''
  return ERROR_TIPS[cellErrors.value[sn] || ''] || cellErrors.value[sn] || ''
}

function retrySn(sn?: string) {
  if (!sn) return
  const player = players.get(sn)
  if (player?.retry) {
    player.retry()
  } else if (player?.refresh) {
    player.refresh()
  } else {
    rebuildPlayers()
  }
}

/** 一键开机：手动路径带确认弹窗；auto（关机自动拉起）免确认，提示后直接下发 */
async function powerOn(sn?: string, auto = false) {
  const entry = entryOf(sn)
  if (!sn || !entry?.dockSn || isPowering(sn)) return
  if (!auto) {
    try {
      await ElMessageBox.confirm(t('live.powerOnConfirm', { name: entry.name }), t('live.powerTitle'), {
        type: 'warning',
      })
    } catch {
      return
    }
  }
  powering.value = { ...powering.value, [sn]: true }
  try {
    await powerOnFh2Device(entry.dockSn)
    ElMessage.success(auto ? t('live.autoPowerOnSent') : t('live.powerOnSent'))
    scheduleRecovery(sn)
  } catch {
    // http 拦截器已统一弹出错误提示；自动开机失败仍可手动重试
    autoPowerTried.delete(sn)
  } finally {
    powering.value = { ...powering.value, [sn]: false }
  }
}

/** 关机自动拉起：仅飞行器条目、每 SN 每会话一次；机场自身离线不属于开机场景 */
function maybeAutoPowerOn(sn: string, code: string) {
  if (!AUTO_POWER_CODES.has(code) || autoPowerTried.has(sn)) return
  if (!canPowerOn(sn) || isPowering(sn)) return
  autoPowerTried.add(sn)
  powerOn(sn, true)
}

/** 开机为机械动作（约 1~2 分钟）：每 15s 重试一次拉流，最多 2 分钟自动停 */
function scheduleRecovery(sn: string) {
  let times = 0
  const timer = window.setInterval(() => {
    times += 1
    if (times > 8 || !players.has(sn)) {
      window.clearInterval(timer)
      recoveryTimers.delete(timer)
      return
    }
    players.get(sn)?.retry?.()
  }, 15_000)
  recoveryTimers.add(timer)
}

function isOnline(state?: string) {
  return ['1', 'true', 'online', 'on'].includes(String(state || '').trim().toLowerCase())
}

function typeLabel(type?: string) {
  const value = String(type || '').toLowerCase()
  if (['airport', 'dock'].includes(value)) return t('live.dock')
  if (['aircraft', 'drone'].includes(value)) return t('live.aircraft')
  return t('live.device')
}

/** 司空设备列表 → 可上屏条目：每台机场 + 其下挂飞行器（droneSn）各成一条，SN 全局去重 */
function buildLiveEntries(list: DeviceVO[]): LiveEntry[] {
  const seen = new Set<string>()
  const result: LiveEntry[] = []
  for (const device of list) {
    if (device.sn && !seen.has(device.sn)) {
      seen.add(device.sn)
      result.push({
        sn: device.sn,
        name: device.name || device.sn,
        deviceType: device.deviceType,
        onlineState: device.onlineState,
        dockSn: device.sn,
      })
    }
    const droneSn = String(device.droneSn || '').trim()
    if (droneSn && droneSn !== device.sn && !seen.has(droneSn)) {
      seen.add(droneSn)
      result.push({
        sn: droneSn,
        name: `${device.name || device.sn} · ${t('live.aircraft')}`,
        deviceType: 'aircraft',
        // 飞行器常在舱内未开机，跟随机场在线状态近似展示
        onlineState: device.onlineState,
        dockSn: device.sn,
      })
    }
  }
  return result
}

function destroyPlayers() {
  players.forEach((player) => {
    try {
      player.destroy()
    } catch {
      // ignore
    }
  })
  players.clear()
  cellErrors.value = {}
  cellStates.value = {}
  // 只清动态注入的播放器挂载点；cell-idle 占位由 Vue 管理，不能动
  gridRef.value?.querySelectorAll('.live-inner').forEach((el) => el.remove())
}

function clearCellError(sn: string) {
  if (cellErrors.value[sn]) {
    const next = { ...cellErrors.value }
    delete next[sn]
    cellErrors.value = next
  }
}

/** 浏览器拒绝有声自动播放（localhost 等陌生源）时：静音重试，muted autoplay 始终放行 */
function resumeBlockedPlay(sn: string) {
  const index = selectedSns.value.indexOf(sn)
  const cell = gridRef.value?.children[index] as HTMLElement | undefined
  const video = cell?.querySelector('video')
  if (video instanceof HTMLVideoElement && video.paused) {
    video.muted = true
    video.play().catch(() => {
      // 静音重试仍失败（极少见）：保留 SDK 自带的“点击播放”覆盖层兜底
    })
  }
}

function rebuildPlayers() {
  destroyPlayers()
  const sdk = window.DJILivePlayer
  const grid = gridRef.value
  if (!sdkReady || !sdk?.DjiLivePlayer || !grid) return
  // 依次填入前 cellCount 路；超出容量的选择不渲染（状态栏已有提示）
  selectedSns.value.slice(0, cellCount.value).forEach((sn, index) => {
    const cell = grid.children[index] as HTMLElement | undefined
    if (!cell) return
    const mount = document.createElement('div')
    mount.id = `live-inner-${index}`
    mount.className = 'live-inner'
    // scoped 样式无法命中 JS 动态创建的元素（缺 data-v 属性），挂载点尺寸必须行内显式指定，
    // 否则高度塌陷为 0，SDK 内部 100% 高度链断裂 → video 高 0 黑屏（流正常在播）
    mount.style.cssText = 'position:absolute;inset:0;'
    cell.appendChild(mount)

    const player = new sdk.DjiLivePlayer({ container: `#live-inner-${index}`, sn, fillMode: 'contain' })
    const label = nameBySn.get(sn) || sn
    player.on('stateChange', (payload) => {
      const state = typeof payload === 'string' ? payload : String((payload as { state?: string })?.state || payload || '')
      if (!state) return
      cellStates.value = { ...cellStates.value, [sn]: state }
      console.info(`[LiveScreen] ${label}: state=${state}`)
      if (state === 'autoplay_blocked') resumeBlockedPlay(sn)
    })
    player.on('error', (payload) => {
      // 组件自带 error/offline 覆盖层；这里记录错误码供操作条（开机/重试）使用
      const err = (payload || {}) as { code?: string; message?: string }
      const code = err.code || 'UNKNOWN'
      cellErrors.value = { ...cellErrors.value, [sn]: code }
      const tip = ERROR_TIPS[code] || err.message || code || '未知错误'
      console.warn(`[LiveScreen] ${label}: ${code} ${tip}`)
      maybeAutoPowerOn(sn, code)
    })
    player.on('deviceOffline', () => {
      cellErrors.value = { ...cellErrors.value, [sn]: 'DEVICE_OFFLINE' }
      console.warn(`[LiveScreen] ${label}: 设备离线，等待自动恢复`)
      maybeAutoPowerOn(sn, 'DEVICE_OFFLINE')
    })
    // 拉流成功（resolved/ready）后清除错误，操作条随之消失
    player.on('resolved', () => clearCellError(sn))
    player.on('ready', () => clearCellError(sn))
    players.set(sn, player)
  })
}

async function loadDevices(silent = false) {
  devicesLoading.value = true
  try {
    entries.value = buildLiveEntries(await fetchCockpitDevices())
    nameBySn.clear()
    entries.value.forEach((entry) => nameBySn.set(entry.sn, entry.name))
    // 剔除已下线移除的勾选项，避免残留 SN 反复 DEVICE_NOT_FOUND
    const known = new Set(entries.value.map((entry) => entry.sn))
    selectedSns.value = selectedSns.value.filter((sn) => known.has(sn))
  } catch (e: unknown) {
    if (!silent) {
      const msg = e instanceof Error ? e.message : String(e)
      ElMessage.error(msg || t('live.deviceLoadFailed'))
    }
  } finally {
    devicesLoading.value = false
  }
}

/** 默认上屏：在线机场优先，最多 4 路（2×2） */
function pickDefaults() {
  const docks = entries.value.filter((entry) =>
    ['airport', 'dock'].includes(String(entry.deviceType || '').toLowerCase()),
  )
  const onlineDocks = docks.filter((entry) => isOnline(entry.onlineState))
  selectedSns.value = (onlineDocks.length ? onlineDocks : docks).slice(0, 4).map((entry) => entry.sn)
}

async function boot() {
  statusText.value = t('live.loadingSdk')
  const cfg = await load()
  if (!cfg?.enabled) {
    bootError.value = t('live.paasDisabled')
    return
  }
  if (!cfg.liveSdkJs || !cfg.liveSdkCss) {
    bootError.value = t('live.sdkMissing')
    return
  }
  await loadStylesheet(cfg.liveSdkCss, 'live-player-sdk-css')
  await loadScript(cfg.liveSdkJs)
  const sdk = window.DJILivePlayer
  if (!sdk?.initLivePlayer || !sdk.DjiLivePlayer) {
    throw new Error(t('live.sdkMissing'))
  }
  if (!livePlayerInited) {
    // serverUrl 不带 /gateway（组件内部自动追加）；鉴权对齐设备管理的 user_id 模式。
    // assetHost：DRTC（WebRTC 引擎）从 frontHost 根路径 /drtc-{version}/sdk.js 加载，
    // 缺省按页面 origin 拼路径（本地 dev 会 404 回退到 index.html），需显式指向司空前端站点。
    sdk.initLivePlayer({
      serverUrl: cfg.serverHost,
      assetHost: cfg.frontHost,
      auth: authConfig(cfg),
      locale: 'zh-CN',
    })
    livePlayerInited = true
  }
  sdkReady = true
  statusText.value = ''
  await loadDevices(true)
  if (!selectedSns.value.length) pickDefaults()
  // pickDefaults 未触发 watch 变更时兜底渲染一次
  rebuildPlayers()
}

// 布局切换会增删宫格 DOM，为避免实例与格子错位，统一全量重建（简单可靠）
watch([selectedSns, cols], () => rebuildPlayers())

onMounted(() => {
  boot().catch((e: unknown) => {
    bootError.value = (e instanceof Error ? e.message : String(e)) || t('live.loadFailed')
    statusText.value = ''
    ElMessage.error(bootError.value)
  })
})

onBeforeUnmount(() => {
  recoveryTimers.forEach((timer) => window.clearInterval(timer))
  recoveryTimers.clear()
  destroyPlayers()
})
</script>

<style scoped lang="scss">
.live-page {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #0a0a0a;
}

.toolbar {
  flex-shrink: 0;
  display: flex;
  flex-wrap: nowrap;
  align-items: center;
  gap: 12px;
  padding: 10px 14px;
}

.device-select {
  flex: 0 1 340px;
  min-width: 200px;
}

/* el-radio-group 默认 flex-wrap:wrap，窄容器下布局按钮会折行 */
.layout-group {
  flex-shrink: 0;
  flex-wrap: nowrap;
  white-space: nowrap;
}

.status {
  color: #86909c;
  font-size: 13px;
  white-space: nowrap;
}

.status-warn {
  color: #e6a23c;
}

.grid-wrap {
  position: relative;
  flex: 1;
  min-height: 0;
  padding: 0 14px 14px;
}

.live-grid {
  width: 100%;
  height: 100%;
  display: grid;
  grid-auto-rows: 1fr;
  gap: 4px;
}

.live-cell {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
  background: #111;
  border: 1px solid #303032;
}

.cell-idle {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: #4e5969;
  user-select: none;
}

.cell-no {
  font-size: 28px;
  font-weight: 700;
  line-height: 1;
}

.cell-idle-text {
  font-size: 12px;
}

.cell-ops {
  position: absolute;
  left: 50%;
  bottom: 12px;
  transform: translateX(-50%);
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: 6px;
  background: rgba(0, 0, 0, 0.65);
  backdrop-filter: blur(2px);
  white-space: nowrap;
}

.ops-err {
  color: #f56c6c;
  font-size: 12px;
}

.ops-state {
  color: #a8abb2;
  font-size: 12px;
}

.center-tip {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: #86909c;
  font-size: 14px;
}

.opt-name {
  float: left;
}

.opt-meta {
  float: right;
  margin-left: 16px;
  font-size: 12px;
  color: #86909c;

  &.is-online {
    color: #67c23a;
  }
}
</style>
