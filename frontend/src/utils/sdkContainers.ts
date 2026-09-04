/**
 * 司空 SDK（FH2 / UranusDeviceManagerApp / WaylineEditor）通过固定的 DOM ID
 * 以 document.getElementById 查找“全局容器”。这些 ID 在多个视图（设备管理、
 * 航线编辑、航线预览、任务回放、驾驶舱）间共用，且部分 SDK 会把节点移动/复制到
 * document.body 下。组件卸载时 Vue 只能清理自身 vdom 树内的节点，被 SDK 移走
 * 的节点成为“游离节点”残留下来。
 *
 * 后续页面再 getElementById 时会命中游离节点（隐藏、0 尺寸），导致地图/编辑器
 * 渲染到看不见的容器 —— 表现为“先进设备管理、再进航线编辑就白屏”。
 *
 * 这里提供统一的清理：删除所有“不在 keepWithin 内”的同名游离节点。
 */

/** SDK 通过 getElementById 查找的跨视图全局容器 ID */
export const FH2_GLOBAL_CONTAINER_IDS = [
  'map-app-global',
  'project-map-app-placeholder',
  'wayline-app-container',
  'project-middle-container',
  'project-right-micro-app',
  'project-app-container',
  'wayline-header',
  'device-manager-app-container',
  'uranus-device-manager-app',
  'cockpit-app-container',
  'cockpit-header-container',
  'fly-data-app-container',
  'real-time-app-container',
] as const

/**
 * 清理游离的全局 SDK 容器节点。
 *
 * @param keepWithin 当前组件根元素；其内部（含自身）的节点不会被删除。
 *                   省略时删除文档中所有同名节点。
 */
export function purgeOrphanSdkContainers(keepWithin?: HTMLElement | null): void {
  if (typeof document === 'undefined') return
  for (const id of FH2_GLOBAL_CONTAINER_IDS) {
    const nodes = document.querySelectorAll(`[id="${id}"]`)
    nodes.forEach((node) => {
      if (keepWithin && (keepWithin === node || keepWithin.contains(node))) return
      node.parentNode?.removeChild(node)
    })
  }
}

type Fh2ResetApi = {
  destroyCockpit?: (self?: boolean) => Promise<boolean> | boolean
  destroyMap?: () => Promise<unknown> | unknown
  destroyPaasView?: () => Promise<unknown> | unknown
  destroyDevice?: () => Promise<unknown> | unknown
}

/** 依次拆除 FH2 共享上下文（驾驶舱/地图/项目视图/设备视图），幂等且失败忽略 */
export async function destroyFh2Contexts(): Promise<void> {
  const fh2 = (window as unknown as { FH2?: Fh2ResetApi }).FH2
  if (!fh2) return
  // 默认 false：卸载驾驶舱 + 地图 + 项目视图（司空文档要求切页这样调）
  try {
    await Promise.resolve(fh2.destroyCockpit?.())
  } catch {
    // ignore
  }
  try {
    await Promise.resolve(fh2.destroyPaasView?.())
  } catch {
    // ignore
  }
  try {
    await Promise.resolve(fh2.destroyMap?.())
  } catch {
    // ignore
  }
  try {
    await Promise.resolve(fh2.destroyDevice?.())
  } catch {
    // ignore
  }
}

/**
 * 离开驾驶舱/设备管理等页面后，重置 FH2 共享地图上下文。
 * 不传 keepWithin 时会清掉所有同名全局容器，避免下一页 getElementById 命中 0 尺寸游离节点。
 * 仅限挂载流程中 await 使用；onBeforeUnmount 场景必须改用 teardownSdkOnRouteLeave，
 * 否则 purge 排在 destroy 链之后迟到执行，会误删下一页已挂载的容器（如设备管理空白）。
 */
export async function resetFh2SharedMapContext(keepWithin?: HTMLElement | null): Promise<void> {
  if (typeof window === 'undefined') return
  await destroyFh2Contexts()
  purgeOrphanSdkContainers(keepWithin)
  purgeWaylineSdkOverlays()
}

/**
 * 航线 WaylineEditor 的 Ant 弹层（含「高程数据」生成/下载弹窗）会 teleport 到
 * document.body，prefixCls 为 uranus-wayline-app-ant-*。组件 destroy 后这些
 * fixed 层常残留在其它页面上。
 */
const WAYLINE_BODY_OVERLAY_SELECTORS = [
  '.uranus-wayline-app-ant-modal-root',
  '.uranus-wayline-app-ant-modal-wrap',
  '.uranus-wayline-app-ant-modal-mask',
  '.uranus-wayline-app-ant-tooltip',
  '.uranus-wayline-app-ant-popover',
  '.uranus-wayline-app-ant-message',
  '.uranus-wayline-app-ant-select-dropdown',
  '.uranus-wayline-app-ant-dropdown',
  '.uranus-wayline-app-ant-dropdown-menu',
] as const

export function purgeWaylineSdkOverlays(): void {
  if (typeof document === 'undefined') return
  for (const selector of WAYLINE_BODY_OVERLAY_SELECTORS) {
    document.querySelectorAll(selector).forEach((node) => {
      // modal-root 优先整棵拆掉；若只有 wrap/mask，再删自身
      const root = node.closest('.uranus-wayline-app-ant-modal-root')
      const target = root && selector.includes('modal') ? root : node
      if (target.isConnected) target.parentNode?.removeChild(target)
    })
  }
  // 兜底：仅检查 body 直接子节点中、文案含「高程数据」的 fixed 浮层
  Array.from(document.body.children).forEach((node) => {
    if (!(node instanceof HTMLElement)) return
    if (node.id === 'app' || node.classList.contains('layout')) return
    const style = window.getComputedStyle(node)
    if (style.position !== 'fixed' && style.position !== 'absolute') return
    const text = (node.textContent || '').trim()
    if (text.includes('高程数据')) {
      node.parentNode?.removeChild(node)
    }
  })
}

/**
 * 司空系 SDK（wayline-edit-sdk / fly-data-sdk）加载 WASM 运行时依赖（libppe/libwpmz 等）
 * 时用 data-fh2-dep 属性查重，且只比对相对路径、不比对 src/basePath。
 * 两个 SDK 的依赖表完全相同（wasm-v06.00.00.124/...），先离开的页面若留下
 * 「加载失败或未完成」的依赖标签，下一页同路径依赖会命中死标签——
 * load/error 事件已错过，等待永不 settle → loadMap 永挂
 * （回放页表现为一直「正在加载回放组件…」且无任何报错）。
 * 进入任一 SDK 页面前先移除全部 data-fh2-dep 标签，强制按本页 basePath 重载。
 */
export function purgeFh2DepScripts(): void {
  if (typeof document === 'undefined') return
  document.querySelectorAll('script[data-fh2-dep]').forEach((node) => {
    node.parentNode?.removeChild(node)
  })
}

/**
 * 路由离开（onBeforeUnmount）专用清理：DOM purge 在卸载的同步阶段完成，destroy 链异步兑底。
 * Vue 先卸载旧页面再挂载新页面，同步 purge 时下一页容器必然还不存在，不会误删；
 * 若沿用 resetFh2SharedMapContext 的“先 await destroy、后 purge”顺序，fire-and-forget
 * 调用会在下一页（如设备管理）挂载好 #uranus-device-manager-app 之后才 purge，
 * 把新页面挂载点当游离节点删掉，表现为“先进航线/驾驶舱、再进设备管理就空白”。
 */
export function teardownSdkOnRouteLeave(): void {
  purgeOrphanSdkContainers()
  purgeWaylineSdkOverlays()
  void destroyFh2Contexts()
}
