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
