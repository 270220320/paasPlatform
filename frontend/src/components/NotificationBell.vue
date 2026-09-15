<template>
  <el-popover placement="bottom-end" :width="380" trigger="click" popper-class="notif-popper" @show="onPanelShow">
    <template #reference>
      <span class="bell" :title="t('notification.title')">
        <el-badge :value="unreadCount" :max="99" :hidden="unreadCount === 0">
          <el-icon :size="20" color="#cfd3dc"><Bell /></el-icon>
        </el-badge>
      </span>
    </template>

    <div class="panel">
      <div class="panel-head">
        <span class="panel-title">{{ t('notification.title') }}</span>
        <el-button v-if="hasUnread" link type="primary" size="small" :loading="markingAll" @click="onMarkAllRead">
          {{ t('notification.markAllRead') }}
        </el-button>
      </div>

      <el-scrollbar max-height="360px">
        <div v-if="items.length === 0" class="empty">{{ t('notification.empty') }}</div>
        <div
          v-for="item in items"
          :key="item.id"
          class="item"
          :class="{ 'item--unread': !item.read }"
          @click="onItemClick(item)"
        >
          <span class="dot" :class="`dot--${levelOf(item)}`" />
          <div class="body">
            <div class="row">
              <span class="item-title">{{ item.title }}</span>
              <span class="time">{{ shortTime(item.createdAt) }}</span>
            </div>
            <div v-if="item.content" class="content">{{ item.content }}</div>
          </div>
        </div>
        <div v-if="hasMore" class="more" @click="loadMore">{{ t('notification.loadMore') }}</div>
        <div v-else-if="items.length > 0" class="more more--end">{{ t('notification.noMore') }}</div>
      </el-scrollbar>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElNotification } from 'element-plus'
import { Bell } from '@element-plus/icons-vue'
import {
  fetchNotifications,
  fetchUnreadCount,
  markAllNotificationsRead,
  markNotificationRead,
  openNotificationStream,
} from '@/api/notification'
import type { NotificationItem } from '@/api/notification'

const { t } = useI18n()
const router = useRouter()

const unreadCount = ref(0)
const items = ref<NotificationItem[]>([])
const page = ref(1)
const hasMore = ref(false)
const markingAll = ref(false)

/** 列表存在未读项或角标非零都展示「全部已读」，避免轮询未刷新时入口消失 */
const hasUnread = computed(
  () => markingAll.value || unreadCount.value > 0 || items.value.some((i) => !i.read),
)

let abortStream: (() => void) | null = null
let reconnectTimer: number | null = null
let pollTimer: number | null = null
let disposed = false

onMounted(() => {
  refreshCount()
  connectStream()
  // SSE 断连期间的兜底轮询（未读数轻量接口）
  pollTimer = window.setInterval(refreshCount, 30_000)
})

onBeforeUnmount(() => {
  disposed = true
  abortStream?.()
  if (reconnectTimer) window.clearTimeout(reconnectTimer)
  if (pollTimer) window.clearInterval(pollTimer)
})

function connectStream() {
  if (disposed) return
  abortStream?.()
  abortStream = openNotificationStream(
    (item) => {
      unreadCount.value += 1
      items.value.unshift(item)
      toast(item)
    },
    () => {
      scheduleReconnect()
    },
    () => {
      // 后端 emitter 30 分钟超时正常结束：立即重连
      scheduleReconnect(500)
    },
  )
}

function scheduleReconnect(delay = 5_000) {
  if (disposed || reconnectTimer) return
  reconnectTimer = window.setTimeout(() => {
    reconnectTimer = null
    connectStream()
  }, delay)
}

async function refreshCount() {
  try {
    const res = await fetchUnreadCount()
    unreadCount.value = res.data?.count ?? 0
  } catch {
    // 网络异常时保留旧值
  }
}

function onPanelShow() {
  page.value = 1
  void loadPage(true)
}

async function loadPage(reset: boolean) {
  const res = await fetchNotifications({ page: page.value, pageSize: 20 })
  const data = res.data
  const list = data?.list ?? []
  items.value = reset ? list : [...items.value, ...list]
  hasMore.value = items.value.length < (data?.total ?? 0)
}

function loadMore() {
  page.value += 1
  void loadPage(false)
}

async function onMarkAllRead() {
  if (markingAll.value) return
  markingAll.value = true
  try {
    await markAllNotificationsRead()
    unreadCount.value = 0
    items.value = items.value.map((i) => ({ ...i, read: true }))
  } catch {
    // 错误提示由 http 层统一处理
  } finally {
    markingAll.value = false
  }
}

async function onItemClick(item: NotificationItem) {
  if (!item.read) {
    item.read = true
    unreadCount.value = Math.max(0, unreadCount.value - 1)
    try {
      await markNotificationRead(item.id)
    } catch {
      // 已乐观更新，失败不回滚
    }
  }
  const target = routeOf(item)
  if (target) router.push(target)
}

/** 按关联对象跳转：计划详情 / 航线 / 设备 / 媒体库 */
function routeOf(item: NotificationItem): string | null {
  switch (item.refType) {
    case 'mission_plan':
      return item.refId ? `/missions/${item.refId}` : '/missions'
    case 'reconstruct_job':
      return '/library'
    case 'wayline':
      return '/waylines'
    case 'device':
      return '/devices'
    default:
      return null
  }
}

function toast(item: NotificationItem) {
  const level = levelOf(item)
  const type = (['success', 'warning', 'error'].includes(level) ? level : 'info') as
    | 'success'
    | 'warning'
    | 'error'
    | 'info'
  ElNotification({
    title: item.title,
    message: item.content || '',
    type,
    customClass: 'notif-toast-light',
    duration: level === 'error' ? 8000 : 4500,
  })
}

function levelOf(item: NotificationItem): string {
  return item.level || 'info'
}

function shortTime(createdAt?: string): string {
  if (!createdAt) return ''
  // 后端格式 yyyy-MM-dd HH:mm:ss：今天只显示时分，其余显示月-日
  const now = new Date()
  const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
  if (createdAt.startsWith(today)) {
    return createdAt.slice(11, 16)
  }
  return createdAt.slice(5, 16)
}
</script>

<style scoped lang="scss">
.bell {
  display: inline-flex;
  align-items: center;
  cursor: pointer;
  padding: 4px;

  :deep(.el-badge__content) {
    transform: translate(8px, -4px);
  }
}

.panel {
  margin: -4px -2px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4px 8px;
  border-bottom: 1px solid #ebeef5;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.empty {
  padding: 32px 0;
  text-align: center;
  color: #909399;
  font-size: 13px;
}

.item {
  display: flex;
  gap: 8px;
  padding: 10px 4px;
  border-bottom: 1px solid #f2f3f5;
  cursor: pointer;
  transition: background 0.15s;

  &:hover {
    background: #f5f7fa;
  }

  &:last-of-type {
    border-bottom: none;
  }
}

.item--unread {
  .item-title {
    font-weight: 600;
  }
}

.dot {
  flex-shrink: 0;
  width: 8px;
  height: 8px;
  margin-top: 6px;
  border-radius: 50%;

  &--info {
    background: #409eff;
  }

  &--success {
    background: #67c23a;
  }

  &--warning {
    background: #e6a23c;
  }

  &--error {
    background: #f56c6c;
  }
}

.body {
  flex: 1;
  min-width: 0;
}

.row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.item-title {
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.time {
  flex-shrink: 0;
  font-size: 12px;
  color: #909399;
}

.content {
  margin-top: 2px;
  font-size: 12px;
  color: #909399;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.more {
  padding: 8px 0 4px;
  text-align: center;
  font-size: 12px;
  color: #409eff;
  cursor: pointer;

  &--end {
    color: #c0c4cc;
    cursor: default;
  }
}
</style>

<style lang="scss">
/* 全局为暗色主题（html.dark），通知面板与弹窗强制亮色：白底黑字。
 * 变量重置口径同 chat-light-drawer：暗色下 light 色阶被反转、white/black 互换，必须整套还原。 */
.el-popper.notif-popper,
.el-notification.notif-toast-light {
  background: #fff;
  /* 原生滚动条跟随 color-scheme，暗色下会变黑 */
  color-scheme: light;
  scrollbar-color: #c0c4cc #f5f7fa;
  --el-color-white: #ffffff;
  --el-color-black: #000000;
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

.el-notification.notif-toast-light {
  .el-notification__title {
    color: #303133;
  }

  .el-notification__message {
    color: #606266;
  }

  .el-notification__closeBtn {
    color: #909399;
  }
}
</style>
