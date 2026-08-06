/**
 * 动态加载司空 PaaS / WaylineEditor SDK 脚本。
 *
 * 关键：paas.js 本身不挂 window.FH2，它在 DOMContentLoaded 时再拉 paas-core.*.js。
 * SPA 在 onMounted 之后加载 paas.js 时 DOMContentLoaded 已触发，必须手动补载 paas-core。
 */

const loaded = new Set<string>()
const inflight = new Map<string, Promise<void>>()

function waitUntil(predicate: () => boolean, timeoutMs = 20000, intervalMs = 50): Promise<boolean> {
  if (predicate()) return Promise.resolve(true)
  return new Promise((resolve) => {
    const start = Date.now()
    const timer = setInterval(() => {
      if (predicate()) {
        clearInterval(timer)
        resolve(true)
        return
      }
      if (Date.now() - start >= timeoutMs) {
        clearInterval(timer)
        resolve(false)
      }
    }, intervalMs)
  })
}

export function loadStylesheet(href: string, id?: string): Promise<void> {
  if (!href) return Promise.resolve()
  if (id && document.getElementById(id)) return Promise.resolve()
  if (loaded.has(href)) return Promise.resolve()
  if (inflight.has(href)) return inflight.get(href)!

  const promise = new Promise<void>((resolve, reject) => {
    const existed = Array.from(document.querySelectorAll('link[rel="stylesheet"]')).find(
      (el) => (el as HTMLLinkElement).href === href || (el as HTMLLinkElement).href.endsWith(href),
    )
    if (existed) {
      loaded.add(href)
      resolve()
      return
    }
    const link = document.createElement('link')
    link.rel = 'stylesheet'
    link.href = href
    if (id) link.id = id
    link.onload = () => {
      loaded.add(href)
      inflight.delete(href)
      resolve()
    }
    link.onerror = () => {
      inflight.delete(href)
      reject(new Error(`stylesheet load failed: ${href}`))
    }
    document.head.appendChild(link)
  })
  inflight.set(href, promise)
  return promise
}

export function loadScript(src: string, attrs: Record<string, string> = {}): Promise<void> {
  if (!src) return Promise.reject(new Error('script src empty'))
  if (loaded.has(src)) return Promise.resolve()
  if (inflight.has(src)) return inflight.get(src)!

  const promise = new Promise<void>((resolve, reject) => {
    const existed = Array.from(document.querySelectorAll('script')).find(
      (el) => el.src === src || el.getAttribute('src') === src,
    )
    if (existed) {
      // 已有标签：等 load / 或已就绪
      if (loaded.has(src)) {
        resolve()
        return
      }
      const onLoad = () => {
        loaded.add(src)
        existed.removeEventListener('load', onLoad)
        existed.removeEventListener('error', onError)
        inflight.delete(src)
        resolve()
      }
      const onError = () => {
        existed.removeEventListener('load', onLoad)
        existed.removeEventListener('error', onError)
        inflight.delete(src)
        reject(new Error(`script load failed: ${src}`))
      }
      existed.addEventListener('load', onLoad)
      existed.addEventListener('error', onError)
      // 已执行完的脚本不会再 fire load，短暂等待后当作完成
      void waitUntil(() => true, 100).then(() => {
        if (!inflight.has(src)) return
        loaded.add(src)
        existed.removeEventListener('load', onLoad)
        existed.removeEventListener('error', onError)
        inflight.delete(src)
        resolve()
      })
      return
    }

    const script = document.createElement('script')
    script.src = src
    Object.entries(attrs).forEach(([k, v]) => script.setAttribute(k, v))
    script.onload = () => {
      loaded.add(src)
      inflight.delete(src)
      resolve()
    }
    script.onerror = () => {
      inflight.delete(src)
      reject(new Error(`script load failed: ${src}`))
    }
    document.head.appendChild(script)
  })
  inflight.set(src, promise)
  return promise
}

/**
 * 从 paas.js 源码解析 paas-core 路径。
 * 线上打包形如："/paas-core.".concat("febdb9036aa846861ceb",".js")
 * 不能只匹配连续的 paas-core.xxx.js。
 */
function parsePaasCorePath(text: string): string | null {
  const concat = text.match(
    /["']\/paas-core\.["']\.concat\(\s*["']([a-zA-Z0-9]+)["']\s*,\s*["']\.js["']\s*\)/,
  )
  if (concat?.[1]) return `paas-core.${concat[1]}.js`

  const plain = text.match(/\/?paas-core\.[a-zA-Z0-9]+\.js/)
  if (plain) return plain[0].replace(/^\//, '')

  return null
}

async function resolvePaasCoreUrl(frontHost: string): Promise<string> {
  const base = frontHost.replace(/\/+$/, '')
  const res = await fetch(`${base}/paas.js`, { cache: 'no-store' })
  if (!res.ok) throw new Error(`无法读取 paas.js：HTTP ${res.status}`)
  const text = await res.text()
  const file = parsePaasCorePath(text)
  if (!file) throw new Error('无法从 paas.js 解析 paas-core 地址')
  return `${base}/${file}`
}

function hasFh2(): boolean {
  return Boolean((window as Window & { FH2?: unknown }).FH2)
}

/**
 * 确保 window.FH2 可用。
 * frontHost 来自后端库表配置（bootstrap），不写死在前端。
 */
export async function loadPaasJs(frontHost: string): Promise<void> {
  if (hasFh2()) return
  if (!frontHost) throw new Error('frontHost 未配置')

  const base = frontHost.replace(/\/+$/, '')
  const paasUrl = `${base}/paas.js`

  // 必须带 fh2 属性：paas.js 用它解析静态资源根路径
  await loadScript(paasUrl, { fh2: '' })

  // 若仍无 FH2：先短暂等 DOMContentLoaded 路径；超时则手动补载 paas-core
  if (!hasFh2()) {
    const viaDomReady = await waitUntil(() => hasFh2(), 800)
    if (!viaDomReady) {
      const coreUrl = await resolvePaasCoreUrl(base)
      await loadScript(coreUrl)
    }
  }

  const ready = await waitUntil(() => hasFh2(), 20000)
  if (!ready || !hasFh2()) {
    throw new Error('FH2 未加载，请检查 frontHost 与 paas.js（paas-core 可能未就绪）')
  }
}

export { waitUntil }
