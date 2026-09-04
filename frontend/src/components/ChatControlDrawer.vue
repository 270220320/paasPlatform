<template>
  <el-drawer
    v-model="visible"
    class="chat-light-drawer"
    :title="t('chat.title')"
    size="560px"
    :append-to-body="true"
    :destroy-on-close="false"
  >
    <div class="chat-drawer">
      <!-- 状态栏 -->
      <div class="status-bar">
        <el-tooltip :content="`Ollama: ${status?.baseUrl || ''}`" placement="bottom">
          <span class="badge" :class="status?.ollamaReachable ? 'ok' : 'bad'">
            {{ status?.ollamaReachable ? status.model || 'Ollama' : t('chat.ollamaDown') }}
          </span>
        </el-tooltip>
        <span class="badge" :class="status?.sdkAvailable ? 'ok' : 'bad'">
          {{ t('chat.sdk') }}{{ status?.sdkAvailable ? '' : t('chat.offline') }}
        </span>
        <span class="badge dim">L0~L{{ status?.maxSecurityLevel ?? '-' }}</span>
        <div class="spacer" />
        <el-select
          v-model="sessionId"
          size="small"
          style="width: 140px"
          :teleported="false"
          @change="loadMessages"
        >
          <el-option
            v-for="s in sessions"
            :key="s.id"
            :value="s.id"
            :label="s.title || `#${s.id}`"
          />
        </el-select>
        <el-button
          class="icon-btn"
          size="small"
          type="danger"
          plain
          :disabled="!sessionId"
          :title="t('common.delete')"
          @click="removeSession"
        >
          <el-icon><Delete /></el-icon>
        </el-button>
        <el-button size="small" @click="newSession">{{ t('chat.newSession') }}</el-button>
      </div>

      <!-- 设备选择 -->
      <div class="device-bar">
        <el-select
          v-model="dockSn"
          size="small"
          clearable
          filterable
          :teleported="false"
          :placeholder="t('chat.selectDock')"
          style="width: 48%"
          @change="saveDevices"
        >
          <el-option v-for="d in docks" :key="d.sn" :value="d.sn" :label="label(d)" />
        </el-select>
        <el-select
          v-model="droneSn"
          size="small"
          clearable
          filterable
          :teleported="false"
          :placeholder="t('chat.selectDrone')"
          style="width: 48%"
          @change="saveDevices"
        >
          <el-option v-for="d in drones" :key="d.sn" :value="d.sn" :label="label(d)" />
        </el-select>
      </div>

      <!-- 消息流 -->
      <div ref="listRef" class="messages">
        <div v-if="!items.length" class="empty">{{ t('chat.empty') }}</div>
        <template v-for="(item, idx) in items" :key="idx">
          <!-- 用户 -->
          <div v-if="item.type === 'user'" class="row user">
            <div class="bubble user-bubble">{{ item.content }}</div>
          </div>

          <!-- 思考 -->
          <div v-else-if="item.type === 'thinking'" class="row">
            <el-collapse class="thinking">
              <el-collapse-item :title="t('chat.thinking')" name="t">
                <pre class="mono">{{ item.content }}</pre>
              </el-collapse-item>
            </el-collapse>
          </div>

          <!-- 助手 -->
          <div v-else-if="item.type === 'assistant'" class="row">
            <div class="bubble ai-bubble">{{ item.content }}</div>
          </div>

          <!-- 工具调用 -->
          <div v-else-if="item.type === 'tool'" class="row">
            <div class="tool-card">
              <div class="tool-head">
                <el-tag size="small" :type="toolTagType(item.status)">{{ item.name }}</el-tag>
                <span class="tool-status">{{ toolStatusText(item.status) }}</span>
              </div>
              <el-collapse v-if="item.args || item.result">
                <el-collapse-item :title="t('chat.detail')" name="d">
                  <pre class="mono">{{ item.args }}</pre>
                  <pre v-if="item.result" class="mono">{{ item.result }}</pre>
                </el-collapse-item>
              </el-collapse>
            </div>
          </div>

          <!-- 待确认操作 -->
          <div v-else-if="item.type === 'confirm'" class="row">
            <div class="confirm-card" :class="{ danger: item.level >= 3 }">
              <div class="confirm-title">
                <el-icon v-if="item.level >= 3" color="#f56c6c"><WarningFilled /></el-icon>
                {{ item.summary }}
              </div>
              <div class="confirm-meta">
                {{ item.tool }} · L{{ item.level }}
                <span v-if="item.reasonRequired" class="danger-text">
                  {{ t('chat.reasonRequired') }}
                </span>
              </div>
              <pre class="mono args">{{ item.argsText }}</pre>
              <el-input
                v-if="item.reasonRequired && item.status === 'pending'"
                v-model="item.reason"
                size="small"
                :placeholder="t('chat.reasonPlaceholder')"
                style="margin: 6px 0"
              />
              <div v-if="item.status === 'pending'" class="confirm-actions">
                <el-button
                  size="small"
                  type="primary"
                  :loading="item.loading"
                  @click="doConfirm(item)"
                >
                  {{ t('chat.confirmRun') }}
                </el-button>
                <el-button size="small" @click="doCancel(item)">{{ t('common.cancel') }}</el-button>
              </div>
              <pre v-else class="mono">{{ item.result }}</pre>
            </div>
          </div>
        </template>
      </div>

      <!-- 快捷指令 -->
      <div class="chips">
        <el-tag
          v-for="q in quickAsks"
          :key="q"
          class="chip"
          effect="plain"
          @click="send(q)"
        >
          {{ q }}
        </el-tag>
      </div>

      <!-- 输入区 -->
      <div class="input-bar">
        <el-input
          v-model="input"
          type="textarea"
          :rows="2"
          :placeholder="t('chat.placeholder')"
          @keydown.enter.exact.prevent="onEnter"
        />
        <div class="input-actions">
          <el-checkbox v-model="dryRun" size="small">dry-run</el-checkbox>
          <el-button
            v-if="streaming"
            size="small"
            type="danger"
            plain
            @click="stopStream"
          >
            {{ t('chat.stop') }}
          </el-button>
          <el-button
            v-else
            size="small"
            type="primary"
            :disabled="!input.trim() || !sessionId || streaming"
            @click="send()"
          >
            {{ t('chat.send') }}
          </el-button>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, WarningFilled } from '@element-plus/icons-vue'
import {
  askChatStream,
  cancelChatAction,
  confirmChatAction,
  createChatSession,
  deleteChatSession,
  fetchChatMessages,
  fetchChatSessions,
  fetchChatStatus,
  updateChatDevices,
  type ChatEvent,
  type ChatSession,
  type ChatStatus,
} from '@/api/chat'
import { fetchDevices, type DeviceVO } from '@/api/device'

interface ConfirmItem {
  type: 'confirm'
  actionId: string
  tool: string
  level: number
  summary: string
  argsText: string
  reasonRequired: boolean
  status: 'pending' | 'done' | 'cancelled'
  reason: string
  loading: boolean
  result: string
}

type Item =
  | { type: 'user'; content: string }
  | { type: 'thinking'; content: string }
  | { type: 'assistant'; content: string }
  | { type: 'tool'; name: string; status: string; args?: string; result?: string }
  | ConfirmItem

const { t } = useI18n()

const visible = defineModel<boolean>({ default: false })

const status = ref<ChatStatus | null>(null)
const sessions = ref<ChatSession[]>([])
const sessionId = ref<number | null>(null)
const devices = ref<DeviceVO[]>([])
const dockSn = ref('')
const droneSn = ref('')
const items = ref<Item[]>([])
const input = ref('')
const dryRun = ref(false)
const streaming = ref(false)
const listRef = ref<HTMLElement>()

let abortStream: (() => void) | null = null
// 首条消息发出时记录待同步标题（与后端 autoTitleIfFirst 同规则），流结束后更新本地列表
let pendingTitle = ''

const quickAsks = computed(() => [
  t('chat.quick.devices'),
  t('chat.quick.status'),
  t('chat.quick.waylines'),
  t('chat.quick.returnHome'),
])

const docks = computed(() => filterDevices('dock'))
const drones = computed(() => filterDevices('aircraft', 'drone'))

function filterDevices(...keys: string[]) {
  const hit = devices.value.filter((d) =>
    keys.some((k) => (d.deviceType || '').toLowerCase().includes(k)),
  )
  return hit.length ? hit : devices.value
}

function label(d: DeviceVO) {
  const online = d.onlineState === 'online' || d.onlineState === '1'
  return `${d.name}(${d.sn}${online ? '·在线' : ''})`
}

watch(visible, (open) => {
  if (open) {
    init()
  }
})

async function init() {
  try {
    const [st, ss] = await Promise.all([fetchChatStatus(), fetchChatSessions()])
    if (st.code === 0) status.value = st.data
    if (ss.code === 0) sessions.value = ss.data
    if (!sessionId.value) {
      sessionId.value = sessions.value[0]?.id ?? null
    }
    if (!sessionId.value) {
      await newSession(true)
    } else {
      await loadDevices()
      await loadMessages()
    }
  } catch {
    ElMessage.error(t('chat.loadFailed'))
  }
  const page = await fetchDevices({ page: 1, pageSize: 100 }).catch(() => null)
  if (page?.data?.code === 0) {
    devices.value = page.data.data?.list ?? []
  }
}

async function newSession(silent = false) {
  const created = await createChatSession({ title: t('chat.newSession') })
  if (created.code !== 0) {
    if (!silent) ElMessage.error(created.message)
    return
  }
  sessions.value.unshift(created.data)
  sessionId.value = created.data.id
  dockSn.value = ''
  droneSn.value = ''
  items.value = []
}

async function loadDevices() {
  const current = sessions.value.find((s) => s.id === sessionId.value)
  dockSn.value = current?.dockSn || ''
  droneSn.value = current?.droneSn || ''
}

async function loadMessages() {
  if (!sessionId.value) return
  items.value = []
  await loadDevices()
  const res = await fetchChatMessages(sessionId.value)
  if (res.code !== 0) {
    ElMessage.error(res.message)
    return
  }
  for (const m of res.data) {
    if (m.role === 'user') {
      items.value.push({ type: 'user', content: m.content || '' })
    } else if (m.role === 'assistant') {
      items.value.push({ type: 'assistant', content: m.content || '' })
    } else if (m.role === 'tool') {
      if (m.status === 'pending') {
        // 历史遗留 pending（如刷新页面丢失 actionId），标记为已过期
        items.value.push({
          type: 'tool',
          name: m.toolName || '',
          status: 'expired',
          args: m.toolArgs || '',
          result: t('chat.expired'),
        })
      } else {
        items.value.push({
          type: 'tool',
          name: m.toolName || '',
          status: m.status || 'ok',
          args: m.toolArgs || '',
          result: m.toolResult || '',
        })
      }
    }
  }
  scrollBottom()
}

async function saveDevices() {
  if (!sessionId.value) return
  await updateChatDevices(sessionId.value, {
    dockSn: dockSn.value || undefined,
    droneSn: droneSn.value || undefined,
  })
}

function onEnter() {
  if (input.value.trim()) send()
}

function send(preset?: string) {
  const content = (preset ?? input.value).trim()
  if (!content || !sessionId.value || streaming.value) return
  if (!preset) input.value = ''
  // 本会话首条消息：待流结束后同步列表标题（后端 autoTitleIfFirst 已落库）
  if (!pendingTitle && !items.value.some((i) => i.type === 'user')) {
    pendingTitle = content.replace(/\s+/g, ' ').trim().slice(0, 30)
  }
  items.value.push({ type: 'user', content })
  streaming.value = true
  scrollBottom()

  abortStream = askChatStream(
    sessionId.value,
    content,
    dryRun.value,
    (ev: ChatEvent) => onEvent(ev),
    (msg) => {
      items.value.push({ type: 'assistant', content: `⚠️ ${msg}` })
      finish()
    },
    () => finish(), // 流正常读完（含 confirm_required 挂起后的连接关闭）
  )
}

function onEvent(ev: ChatEvent) {
  switch (ev.type) {
    case 'thinking':
      items.value.push({ type: 'thinking', content: ev.content })
      break
    case 'delta':
      pushOrMergeAssistant(ev.content)
      break
    case 'final':
      pushOrMergeAssistant(ev.content)
      finish() // 后端已给出终稿，立即恢复发送按钮
      break
    case 'tool_start':
      items.value.push({
        type: 'tool',
        name: ev.tool,
        status: 'running',
        args: JSON.stringify(ev.args, null, 2),
      })
      break
    case 'tool_result': {
      const last = [...items.value].reverse().find((i) => i.type === 'tool' && i.name === ev.tool)
      if (last && last.type === 'tool') {
        last.status = 'ok'
        last.result = JSON.stringify(ev.data, null, 2)
      }
      break
    }
    case 'confirm_required': {
      items.value.push({
        type: 'confirm',
        actionId: ev.actionId,
        tool: ev.tool,
        level: ev.level,
        summary: ev.summary,
        argsText: JSON.stringify(ev.args, null, 2),
        reasonRequired: ev.reasonRequired,
        status: 'pending',
        reason: '',
        loading: false,
        result: '',
      })
      // 挂起等待确认：本轮流结束
      break
    }
    case 'error':
      items.value.push({ type: 'assistant', content: `⚠️ ${ev.message}` })
      finish()
      break
  }
  scrollBottom()
}

function pushOrMergeAssistant(text: string) {
  const last = items.value[items.value.length - 1]
  if (last?.type === 'assistant' && !last.content.startsWith('⚠️')) {
    last.content = text
  } else {
    items.value.push({ type: 'assistant', content: text })
  }
}

async function doConfirm(item: ConfirmItem) {
  item.loading = true
  try {
    const res = await confirmChatAction(item.actionId, item.reason)
    item.status = 'done'
    item.result = res.code === 0 ? JSON.stringify(res.data, null, 2) : res.message
    if (res.code !== 0) ElMessage.error(res.message)
    else ElMessage.success(t('chat.executed'))
  } finally {
    item.loading = false
    scrollBottom()
  }
}

async function doCancel(item: ConfirmItem) {
  const res = await cancelChatAction(item.actionId)
  if (res.code === 0) {
    item.status = 'cancelled'
    item.result = t('chat.cancelled')
  } else {
    ElMessage.error(res.message)
  }
}

function stopStream() {
  abortStream?.()
  finish()
}

function finish() {
  streaming.value = false
  abortStream = null
  if (pendingTitle) {
    const current = sessions.value.find((s) => s.id === sessionId.value)
    if (current) current.title = pendingTitle
    pendingTitle = ''
  }
  scrollBottom()
}

async function removeSession() {
  if (!sessionId.value) return
  try {
    await ElMessageBox.confirm(t('chat.deleteConfirm'), t('common.confirmTitle'), {
      type: 'warning',
      customClass: 'chat-light-msgbox',
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
    })
  } catch {
    return
  }
  const targetId = sessionId.value
  const res = await deleteChatSession(targetId)
  if (res.code !== 0) {
    ElMessage.error(res.message)
    return
  }
  sessions.value = sessions.value.filter((s) => s.id !== targetId)
  ElMessage.success(t('chat.deleted'))
  if (sessionId.value === targetId) {
    sessionId.value = sessions.value[0]?.id ?? null
    if (sessionId.value) {
      await loadMessages()
    } else {
      await newSession(true)
    }
  }
}

function scrollBottom() {
  nextTick(() => {
    if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight
  })
}

function toolTagType(status: string) {
  if (status === 'ok' || status === 'confirmed') return 'success'
  if (status === 'running') return 'info'
  if (status === 'pending') return 'warning'
  return 'danger'
}

function toolStatusText(status: string) {
  const map: Record<string, string> = {
    running: t('chat.running'),
    ok: t('chat.done'),
    confirmed: t('chat.done'),
    pending: t('chat.pending'),
    cancelled: t('chat.cancelled'),
    error: t('chat.failed'),
    expired: t('chat.expired'),
  }
  return map[status] ?? status
}
</script>

<style scoped lang="scss">
.chat-drawer {
  display: flex;
  flex-direction: column;
  height: 100%;
  gap: 8px;
  background: #fff;
  color: #303133;
}

.status-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.badge {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
  background: #f0f2f5;
  color: #606266;

  &.ok {
    background: #e1f3d8;
    color: #67c23a;
  }

  &.bad {
    background: #fde2e2;
    color: #f56c6c;
  }

  &.dim {
    background: #f4f4f5;
    color: #909399;
  }
}

.spacer {
  flex: 1;
}

/* 紧凑图标按钮（会话删除） */
.icon-btn {
  padding: 8px 9px;
  margin-left: 6px;
}

.device-bar {
  display: flex;
  justify-content: space-between;
}

.messages {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: #fafafa;
  color-scheme: light;
  scrollbar-color: #c0c4cc #f5f7fa;

  &::-webkit-scrollbar {
    width: 8px;
  }

  &::-webkit-scrollbar-thumb {
    background: #cdd0d6;
    border-radius: 4px;
  }

  &::-webkit-scrollbar-track {
    background: transparent;
  }
}

.empty {
  text-align: center;
  color: #909399;
  font-size: 13px;
  margin-top: 40px;
}

.row {
  display: flex;

  &.user {
    justify-content: flex-end;
  }
}

.bubble {
  max-width: 82%;
  padding: 8px 12px;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.user-bubble {
  background: #409eff;
  color: #fff;
  border-bottom-right-radius: 2px;
}

.ai-bubble {
  background: #fff;
  color: #303133;
  border: 1px solid #e4e7ed;
  border-bottom-left-radius: 2px;
}

.thinking {
  width: 100%;
  border: 1px dashed #dcdfe6;
  border-radius: 6px;
}

.tool-card {
  width: 100%;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 6px 10px;
  font-size: 12px;
}

.tool-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tool-status {
  color: #909399;
}

.confirm-card {
  width: 100%;
  background: #fdf6ec;
  border: 1px solid #f3d19e;
  border-radius: 8px;
  padding: 10px;

  &.danger {
    background: #fef0f0;
    border-color: #fbc4c4;
  }
}

.confirm-title {
  font-size: 14px;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
}

.confirm-meta {
  font-size: 12px;
  color: #909399;
  margin: 4px 0;
}

.danger-text {
  color: #f56c6c;
  margin-left: 8px;
}

.confirm-actions {
  display: flex;
  gap: 8px;
}

.mono {
  font-family: ui-monospace, Consolas, monospace;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 4px 0;
  color: #303133;
  background: #fff;

  &.args {
    max-height: 160px;
    overflow-y: auto;
  }
}

.chips {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;

  .chip {
    cursor: pointer;
  }
}

.input-bar {
  border-top: 1px solid #ebeef5;
  padding-top: 8px;
}

.input-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 10px;
  margin-top: 6px;
}
</style>

<style lang="scss">
/* 全局为暗色主题（html.dark），对话抽屉整体强制亮色：白底黑字。
 * 除中性色外必须重置调色板 light 色阶（暗色下被反转为深色，plain 按钮/标签背景会变黑）
 * 及 --el-color-white/black（暗色下互换）。 */
.el-drawer.chat-light-drawer,
.el-message-box.chat-light-msgbox {
  background: #fff;
  /* 原生滚动条跟随 color-scheme，暗色下会变黑 */
  color-scheme: light;
  scrollbar-color: #c0c4cc #f5f7fa;
  --el-color-white: #ffffff;
  --el-color-black: #000000;
  --el-drawer-bg-color: #fff;
  --el-bg-color: #fff;
  --el-bg-color-overlay: #fff;
  --el-fill-color-blank: #fff;
  --el-fill-color-light: #f5f7fa;
  --el-fill-color: #f0f2f5;
  --el-text-color-primary: #303133;
  --el-text-color-regular: #606266;
  --el-text-color-secondary: #909399;
  --el-text-color-placeholder: #a8abb2;
  --el-border-color: #dcdfe6;
  --el-border-color-light: #e4e7ed;
  --el-border-color-lighter: #ebeef5;
  --el-collapse-header-bg-color: #fff;
  --el-collapse-content-bg-color: #fff;
  --el-collapse-border-color: #e4e7ed;
  /* primary */
  --el-color-primary: #409eff;
  --el-color-primary-light-3: #79bbff;
  --el-color-primary-light-5: #a0cfff;
  --el-color-primary-light-7: #c6e2ff;
  --el-color-primary-light-8: #d9ecff;
  --el-color-primary-light-9: #ecf5ff;
  --el-color-primary-dark-2: #337ecc;
  /* danger / error */
  --el-color-danger: #f56c6c;
  --el-color-danger-light-3: #f89898;
  --el-color-danger-light-5: #fab6b6;
  --el-color-danger-light-7: #fbc4c4;
  --el-color-danger-light-8: #fde2e2;
  --el-color-danger-light-9: #fef0f0;
  --el-color-error: #f56c6c;
  --el-color-error-light-8: #fde2e2;
  --el-color-error-light-9: #fef0f0;
  /* warning */
  --el-color-warning: #e6a23c;
  --el-color-warning-light-3: #eebe77;
  --el-color-warning-light-5: #f3d19e;
  --el-color-warning-light-7: #f8e3c5;
  --el-color-warning-light-8: #faecd8;
  --el-color-warning-light-9: #fdf6ec;
  /* success */
  --el-color-success: #67c23a;
  --el-color-success-light-3: #95d475;
  --el-color-success-light-5: #b3e19d;
  --el-color-success-light-7: #d1edc4;
  --el-color-success-light-8: #e1f3d8;
  --el-color-success-light-9: #f0f9eb;
  /* info */
  --el-color-info: #909399;
  --el-color-info-light-3: #b1b3b8;
  --el-color-info-light-5: #c8c9cc;
  --el-color-info-light-7: #dedfe0;
  --el-color-info-light-8: #e9e9eb;
  --el-color-info-light-9: #f4f4f5;
}

.el-drawer.chat-light-drawer {
  .el-drawer__header {
    color: #303133;
    border-bottom-color: #ebeef5;
  }
}

.el-message-box.chat-light-msgbox {
  .el-message-box__title {
    color: #303133;
  }

  .el-message-box__message {
    color: #606266;
  }
}
</style>
