/**
 * 监听司空 WaylineEditor 发起的「改航线名」请求，把新名称同步回本地。
 * 编辑器改名会直接打司空 PUT，不会触发 wayline-save。
 *
 * 使用引用计数单例 patch，避免重复包裹 fetch/XHR，卸载后完整还原原生实现。
 */

export type WaylineRenamePayload = { waylineUuid: string; name: string }

type Cleanup = () => void
type RenameListener = (payload: WaylineRenamePayload) => void

const WAYLINE_PUT_RE = /\/waylines\/([^/?#]+)(?:\?|#|$)/i

export function extractWaylineUuid(url: string): string | null {
  if (!url || /check-name/i.test(url)) return null
  const m = url.match(WAYLINE_PUT_RE)
  if (!m?.[1]) return null
  const id = decodeURIComponent(m[1]).trim()
  return id || null
}

export function parseNameFromBody(body: unknown): string | null {
  if (body == null) return null
  let text = ''
  if (typeof body === 'string') {
    text = body
  } else if (body instanceof URLSearchParams) {
    text = body.toString()
  } else if (typeof Blob !== 'undefined' && body instanceof Blob) {
    return null
  } else if (typeof body === 'object' && !(body instanceof ArrayBuffer) && !(ArrayBuffer.isView(body))) {
    try {
      text = JSON.stringify(body)
    } catch {
      return null
    }
  } else {
    return null
  }
  try {
    const json = JSON.parse(text) as { name?: unknown }
    if (typeof json?.name === 'string' && json.name.trim()) {
      return json.name.trim()
    }
  } catch {
    // ignore non-json
  }
  return null
}

function requestUrl(input: RequestInfo | URL): string {
  if (typeof input === 'string') return input
  if (input instanceof URL) return input.href
  return input.url
}

const listeners = new Set<RenameListener>()
let patched = false
let nativeFetch: typeof window.fetch | null = null
let nativeXhrOpen: typeof XMLHttpRequest.prototype.open | null = null
let nativeXhrSend: typeof XMLHttpRequest.prototype.send | null = null

function notifyRename(payload: WaylineRenamePayload) {
  listeners.forEach((fn) => {
    try {
      fn(payload)
    } catch {
      // ignore listener errors
    }
  })
}

function ensurePatched() {
  if (patched || typeof window === 'undefined') return
  patched = true
  nativeFetch = window.fetch.bind(window)
  nativeXhrOpen = XMLHttpRequest.prototype.open
  nativeXhrSend = XMLHttpRequest.prototype.send

  window.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
    const method = (init?.method || (input instanceof Request ? input.method : 'GET')).toUpperCase()
    const url = requestUrl(input)
    const uuid = method === 'PUT' || method === 'PATCH' ? extractWaylineUuid(url) : null
    let pendingName: string | null = null
    if (uuid) {
      pendingName = parseNameFromBody(init?.body)
      if (!pendingName && input instanceof Request) {
        try {
          pendingName = parseNameFromBody(await input.clone().text())
        } catch {
          pendingName = null
        }
      }
    }
    const res = await nativeFetch!(input, init)
    if (uuid && pendingName && res.ok) {
      notifyRename({ waylineUuid: uuid, name: pendingName })
    }
    return res
  }

  XMLHttpRequest.prototype.open = function (this: XMLHttpRequest, method: string, url: string | URL, ...rest: unknown[]) {
    ;(this as XMLHttpRequest & { __wlMethod?: string; __wlUrl?: string }).__wlMethod = String(method || 'GET').toUpperCase()
    ;(this as XMLHttpRequest & { __wlMethod?: string; __wlUrl?: string }).__wlUrl = String(url)
    return (nativeXhrOpen as (...args: unknown[]) => void).apply(this, [method, url, ...rest])
  } as typeof XMLHttpRequest.prototype.open

  XMLHttpRequest.prototype.send = function (this: XMLHttpRequest, body?: Document | XMLHttpRequestBodyInit | null) {
    const meta = this as XMLHttpRequest & { __wlMethod?: string; __wlUrl?: string }
    const method = meta.__wlMethod || 'GET'
    const url = meta.__wlUrl || ''
    const uuid = method === 'PUT' || method === 'PATCH' ? extractWaylineUuid(url) : null
    const pendingName = uuid ? parseNameFromBody(body) : null
    if (uuid && pendingName) {
      this.addEventListener('load', () => {
        if (this.status >= 200 && this.status < 300) {
          notifyRename({ waylineUuid: uuid, name: pendingName })
        }
      })
    }
    return nativeXhrSend!.call(this, body as XMLHttpRequestBodyInit)
  }
}

function maybeUnpatch() {
  if (!patched || listeners.size > 0) return
  if (nativeFetch) window.fetch = nativeFetch
  if (nativeXhrOpen) XMLHttpRequest.prototype.open = nativeXhrOpen
  if (nativeXhrSend) XMLHttpRequest.prototype.send = nativeXhrSend
  nativeFetch = null
  nativeXhrOpen = null
  nativeXhrSend = null
  patched = false
}

/**
 * 拦截 fetch / XHR，在司空改名成功后回调。
 * 返回卸载函数。
 */
export function installWaylineRenameWatcher(onRename: RenameListener): Cleanup {
  listeners.add(onRename)
  ensurePatched()
  return () => {
    listeners.delete(onRename)
    maybeUnpatch()
  }
}
