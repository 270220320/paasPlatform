import type { AnnotationRecord } from './annotationStorage'

interface AnnotationGraphic {
  containerID_?: string
  data_?: Partial<AnnotationRecord>
  isEditable_?: boolean
  isStartDraw_?: boolean
  mousemove?: (...args: unknown[]) => void
  clear: () => void
}

interface AnnotationMap {
  off: (event: string, handler: (...args: unknown[]) => void) => void
}

/**
 * zywlgis.clear() removes geometry but leaves an unfinished drawing's mousemove
 * listener attached. Dispose removed annotations before the SDK's prop watcher
 * drops their instances, otherwise moving the mouse draws the deleted area again.
 */
export function pruneAnnotationGraphics(containerId: string, activeIds: readonly string[]): void {
  const runtime = window as unknown as {
    MapDatas?: Map<string, Map<string, AnnotationGraphic>>
    mapEngines?: Map<string, AnnotationMap>
    EditElement?: AnnotationGraphic
  }
  const graphics = runtime.MapDatas?.get(`${containerId}-&-ZYWL-&-Annotation`)
  if (!graphics) return

  const active = new Set(activeIds)
  for (const [key, graphic] of graphics) {
    // The twin engine stores its 2D instance under the same ID plus "-2d".
    if (active.has(key) || (key.endsWith('-2d') && active.has(key.slice(0, -3)))) continue

    const map = runtime.mapEngines?.get(graphic.containerID_ || `${containerId}-maptalks`)
    if (graphic.mousemove) map?.off('mousemove', graphic.mousemove)
    graphic.isEditable_ = false
    graphic.isStartDraw_ = false
    if (graphic.data_) {
      graphic.data_ = { ...graphic.data_, coordinates: [], isEditable: false, _mapClickAddNode: false }
    }
    if (runtime.EditElement === graphic) runtime.EditElement = undefined
    graphic.clear()
    graphics.delete(key)
  }
}
