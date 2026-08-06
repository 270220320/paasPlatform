/** 加载 zywlgis 依赖的 Cesium / SuperMap 等全局脚本（对齐 paasVue，并补齐 peer/i18n） */
import jquery from 'jquery'

const loadedScripts = new Set<string>()
const loadedStyles = new Set<string>()

/** zywlgis 状态栏会读 Datai18n.zoom；缺失会抛错并跳过 setMapEngineAction（地图无法点击绘制） */
const GIS_I18N_FALLBACK: Record<string, string> = {
  zoom: '层级',
  longitude: '经度',
  latitude: '纬度',
  insert_waypoint_before: '在{index}号航点前插入航点',
  insert_waypoint_after: '在{index}号航点后插入航点',
  lift_RTH_lock: '取消禁止返航点',
  set_as_no_RTH_zone: '设为禁止返航点',
  append_waypoint: '在最后航点新增航点',
  删除端点: '删除端点',
  删除图形: '删除图形',
  删除兴趣点: '删除兴趣点',
  删除被测区: '删除被测区',
}

let i18nPatched = false

function normalizeGisI18n(value: unknown): Record<string, string> {
  const raw = value && typeof value === 'object' ? (value as Record<string, string>) : {}
  return { ...GIS_I18N_FALLBACK, ...raw }
}

function loadScript(src: string, optional = false): Promise<void> {
  if (loadedScripts.has(src)) return Promise.resolve()
  const existing = document.querySelector<HTMLScriptElement>(`script[data-gis-dep="${src}"]`)
  if (existing) {
    loadedScripts.add(src)
    return Promise.resolve()
  }
  return new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = src
    script.async = false
    script.dataset.gisDep = src
    script.onload = () => {
      loadedScripts.add(src)
      resolve()
    }
    script.onerror = () => {
      if (optional) {
        console.warn(`[gis] 可选脚本未找到，已跳过：${src}`)
        resolve()
        return
      }
      reject(new Error(`脚本加载失败：${src}`))
    }
    document.head.appendChild(script)
  })
}

function loadStyle(href: string): Promise<void> {
  if (loadedStyles.has(href)) return Promise.resolve()
  const existing = document.querySelector<HTMLLinkElement>(`link[data-gis-dep="${href}"]`)
  if (existing) {
    loadedStyles.add(href)
    return Promise.resolve()
  }
  return new Promise((resolve, reject) => {
    const link = document.createElement('link')
    link.rel = 'stylesheet'
    link.href = href
    link.dataset.gisDep = href
    link.onload = () => {
      loadedStyles.add(href)
      resolve()
    }
    link.onerror = () => reject(new Error(`样式加载失败：${href}`))
    document.head.appendChild(link)
  })
}

declare global {
  interface Window {
    Cesium?: unknown
    CESIUM_BASE_URL?: string
    Datai18n?: Record<string, string>
    jQuery?: typeof jquery
    $?: typeof jquery
  }
}

/**
 * 确保 window.Datai18n 始终可用。
 * zywlgis 的 refreshInternationalization 可能把 Datai18n 写成 undefined，
 * 这里用 setter 兜底，避免初始化中断导致地图 click 未绑定。
 */
export function ensureGisI18n(): void {
  if (typeof window === 'undefined') return

  if (!i18nPatched) {
    i18nPatched = true
    let current = normalizeGisI18n(window.Datai18n)
    Object.defineProperty(window, 'Datai18n', {
      configurable: true,
      enumerable: true,
      get: () => current,
      set: (value: unknown) => {
        current = normalizeGisI18n(value)
      },
    })
    return
  }

  if (typeof window.Datai18n?.zoom !== 'string') {
    window.Datai18n = { ...GIS_I18N_FALLBACK }
  }
}

/** 挂载 jQuery 到全局（zywlgis peerDependency） */
export function ensureJquery(): void {
  if (typeof window === 'undefined') return
  window.jQuery = window.jQuery || jquery
  window.$ = window.$ || jquery
}

let loading: Promise<void> | null = null

/** 在挂载 ZYWLGIS 前调用，确保 Cesium / Datai18n / jQuery 可用 */
export function ensureGisDependencies(): Promise<void> {
  ensureJquery()
  ensureGisI18n()

  if (typeof window !== 'undefined' && window.Cesium) {
    ensureGisI18n()
    return Promise.resolve()
  }
  if (loading) return loading

  loading = (async () => {
    ensureJquery()
    ensureGisI18n()
    window.CESIUM_BASE_URL = '/libs/Cesium/'
    await loadStyle('/libs/Cesium/Widgets/widgets.css')
    await loadScript('/libs/Cesium/Cesium.js')
    await loadScript('/libs/Cesium/Cesium-Version.js', true)
    await loadScript('/libs/Cesium/Cesium-When.js', true)
    await loadScript('/libs/SuperMap3D/SuperMap3D.js', true)
    await loadScript('/libs/cpRPA.js', true)
    await loadScript('/libs/egm96_mhl.js', true)
    ensureGisI18n()
    if (!window.Cesium) throw new Error('Cesium 加载完成但全局对象未就绪，请检查 public/libs（zywlgis）')
  })().catch((error) => {
    loading = null
    throw error
  })

  return loading
}
