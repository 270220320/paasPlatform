import { useUserStore } from '@/stores/user'
import type { ApiResult } from './http'
import type { PageResult } from './iam'

/** 通知级别 */
export type NotificationLevel = 'info' | 'success' | 'warning' | 'error'

/** 站内通知（列表与 SSE 推送共用结构） */
export interface NotificationItem {
  id: number
  type: string
  level: NotificationLevel | string
  title: string
  content?: string | null
  refType?: string | null
  refId?: number | null
  read?: boolean
  createdAt?: string
}

function authHeaders(): Record<string, string> {
  const token = useUserStore().token
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  return headers
}

async function json<T>(url: string, init?: RequestInit): Promise<ApiResult<T>> {
  const response = await fetch(url, {
    ...init,
    headers: { ...authHeaders(), ...(init?.headers as Record<string, string> | undefined) },
  })
  return (await response.json()) as ApiResult<T>
}

export function fetchNotifications(params: { page?: number; pageSize?: number; unreadOnly?: boolean }) {
  return json<PageResult<NotificationItem>>(
    `/api/v1/notifications?page=${params.page ?? 1}&pageSize=${params.pageSize ?? 20}&unreadOnly=${params.unreadOnly ?? false}`,
  )
}

export function fetchUnreadCount() {
  return json<{ count: number }>('/api/v1/notifications/unread-count')
}

export function markNotificationRead(id: number) {
  return json<boolean>(`/api/v1/notifications/${id}/read`, { method: 'POST' })
}

export function markAllNotificationsRead() {
  return json<number>('/api/v1/notifications/read-all', { method: 'POST' })
}

/**
 * 通知 SSE 流（GET + Authorization，EventSource 不支持自定义头，用 fetch 流式解析）。
 * 心跳为 SSE comment 行（:ping），不产生 data 帧自然被忽略；
 * 流断开/超时后由 onDone 触发上层重连。返回 abort 函数。
 */
export function openNotificationStream(
  onEvent: (item: NotificationItem) => void,
  onError?: (message: string) => void,
  onDone?: () => void,
): () => void {
  const controller = new AbortController()
  fetch('/api/v1/notifications/stream', {
    method: 'GET',
    headers: authHeaders(),
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.ok || !response.body) {
        onError?.(`HTTP ${response.status}`)
        return
      }
      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''
      for (;;) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        // SSE 按 \n\n 分帧；通知帧形如 "event:notification\ndata:{...}"
        let sep: number
        while ((sep = buffer.indexOf('\n\n')) >= 0) {
          const frame = buffer.slice(0, sep)
          buffer = buffer.slice(sep + 2)
          const dataLine = frame
            .split('\n')
            .filter((l) => l.startsWith('data:'))
            .map((l) => l.slice(5))
            .join('\n')
            .trim()
          if (!dataLine) continue
          try {
            onEvent(JSON.parse(dataLine) as NotificationItem)
          } catch {
            /* 忽略半帧 */
          }
        }
      }
      // 流正常读完（后端 emitter 超时/complete），上层重连
      onDone?.()
    })
    .catch((e: unknown) => {
      if ((e as Error)?.name !== 'AbortError') {
        onError?.((e as Error)?.message || '网络错误')
      }
    })
  return () => controller.abort()
}
