import { useUserStore } from '@/stores/user'
import type { ApiResult } from './http'

/** 对话会话 */
export interface ChatSession {
  id: number
  title: string
  dockSn?: string | null
  droneSn?: string | null
  status: string
  updatedAt?: string
}

export type ChatMessageRole = 'user' | 'assistant' | 'tool'

/** 对话消息（user/assistant/tool） */
export interface ChatMessage {
  id: number
  sessionId: number
  role: ChatMessageRole
  content?: string | null
  thinking?: string | null
  toolName?: string | null
  toolArgs?: string | null
  toolResult?: string | null
  status?: string | null
  createdAt?: string
}

/** 面板状态 */
export interface ChatStatus {
  enabled: boolean
  ollamaReachable: boolean
  baseUrl: string
  models: string[]
  model?: string | null
  sdkAvailable: boolean
  maxSecurityLevel: number
  tools: Array<{ name: string; level: number; description: string }>
}

/** SSE 事件（后端 ChatAgentService 推送） */
export type ChatEvent =
  | { type: 'thinking'; content: string }
  | { type: 'delta'; content: string }
  | { type: 'tool_start'; tool: string; args: Record<string, unknown> }
  | { type: 'tool_result'; tool: string; data: Record<string, unknown>; messageId: number }
  | {
      type: 'confirm_required'
      actionId: string
      tool: string
      level: number
      summary: string
      args: Record<string, unknown>
      reasonRequired: boolean
    }
  | { type: 'final'; content: string; messageId?: number }
  | { type: 'error'; message: string }

function authHeaders(): Record<string, string> {
  const token = useUserStore().token
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  return headers
}

/**
 * SSE 对话（POST + Authorization，EventSource 不支持，用 fetch 流式解析）。
 * onEvent 在每个事件解析后回调；onDone 在流正常读完后回调（用于复位 streaming 状态）；
 * onError 在 HTTP 非 2xx / 网络错误时回调；返回 abort 函数。
 */
export function askChatStream(
  sessionId: number,
  content: string,
  dryRun: boolean,
  onEvent: (event: ChatEvent) => void,
  onError?: (message: string) => void,
  onDone?: () => void,
): () => void {
  const controller = new AbortController()
  fetch(`/api/v1/chat/sessions/${sessionId}/messages`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify({ content, dryRun }),
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.ok || !response.body) {
        const text = await response.text().catch(() => '')
        let message = `HTTP ${response.status}`
        try {
          const payload = JSON.parse(text) as ApiResult<unknown>
          if (payload.message) message = payload.message
        } catch {
          /* 非 JSON 响应 */
        }
        onError?.(message)
        return
      }
      const reader = response.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''
      for (;;) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        // SSE 按 \n\n 分帧；每帧形如 "event:message\ndata:{...}"
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
            onEvent(JSON.parse(dataLine) as ChatEvent)
          } catch {
            /* 忽略半帧 */
          }
        }
      }
      // 流正常读完（后端 emitter.complete() 后 reader 返回 done）
      onDone?.()
    })
    .catch((e: unknown) => {
      if ((e as Error)?.name !== 'AbortError') {
        onError?.((e as Error)?.message || '网络错误')
      }
    })
  return () => controller.abort()
}

async function json<T>(url: string, init?: RequestInit): Promise<ApiResult<T>> {
  const response = await fetch(url, {
    ...init,
    headers: { ...authHeaders(), ...(init?.headers as Record<string, string> | undefined) },
  })
  return (await response.json()) as ApiResult<T>
}

export function fetchChatStatus() {
  return json<ChatStatus>('/api/v1/chat/status')
}

export function fetchChatSessions() {
  return json<ChatSession[]>('/api/v1/chat/sessions')
}

export function createChatSession(data: { title?: string; dockSn?: string; droneSn?: string }) {
  return json<ChatSession>('/api/v1/chat/sessions', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export function updateChatDevices(
  sessionId: number,
  data: { dockSn?: string; droneSn?: string },
) {
  return json<void>(`/api/v1/chat/sessions/${sessionId}/devices`, {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

export function deleteChatSession(sessionId: number) {
  return json<void>(`/api/v1/chat/sessions/${sessionId}`, { method: 'DELETE' })
}

export function fetchChatMessages(sessionId: number) {
  return json<ChatMessage[]>(`/api/v1/chat/sessions/${sessionId}/messages`)
}

export function confirmChatAction(actionId: string, reason?: string) {
  return json<Record<string, unknown>>(`/api/v1/chat/actions/${actionId}/confirm`, {
    method: 'POST',
    body: JSON.stringify({ reason: reason || '' }),
  })
}

export function cancelChatAction(actionId: string) {
  return json<void>(`/api/v1/chat/actions/${actionId}/cancel`, { method: 'POST' })
}
