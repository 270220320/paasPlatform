/** zywlgis Annotations 本地结构（与 CustomNoFlyZones 编辑引擎字段对齐） */

export type AnnotationGisType = 2 | 3 | 4 // 2=折线 3=多边形 4=圆形

export interface AnnotationCoordinate {
  longitude: number
  latitude: number
  altitude?: number
}

export interface AnnotationRecord {
  ID: string
  type: AnnotationGisType
  name: string
  visible: boolean
  isClampToGround: boolean
  isHighlight: boolean
  isEditable: boolean
  isDelete: boolean
  zIndex: number
  defaultHeight: number
  /** 业务用途：other / no_fly / work_area / poi */
  purpose?: string
  symbol: {
    lineWidth: number
    lineColor?: string
    polygonAlpha?: number
    polygonColor?: string
    radius?: number
    /** 虚线样式，禁飞区常用 */
    lineDasharray?: number[]
  }
  customLabel: {
    show: boolean
    content: string
    showBackground: boolean
    dx: number
    dy: number
  }
  coordinates: AnnotationCoordinate[]
  _mapClickAddNode?: boolean
  _centerPointShow?: boolean
}

const STORAGE_KEY = 'aiflow-map-annotations'

export function createAnnotationId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

export function createAnnotationRecord(
  type: AnnotationGisType,
  name?: string,
  purpose = 'other',
  color = '#2F80ED',
): AnnotationRecord {
  const label = name || (type === 4 ? '圆形标注' : type === 2 ? '折线标注' : '多边形标注')
  const fill = color || '#2F80ED'
  return {
    ID: createAnnotationId(),
    type,
    name: label,
    purpose,
    visible: true,
    isClampToGround: true,
    isHighlight: true,
    isEditable: true,
    isDelete: true,
    zIndex: 0,
    defaultHeight: 100,
    symbol: {
      lineWidth: 2,
      lineColor: fill,
      polygonAlpha: type === 2 ? 0 : 0.28,
      polygonColor: fill,
      ...(type === 4 ? { radius: 300 } : {}),
    },
    customLabel: {
      show: true,
      content: label,
      showBackground: true,
      dx: 0,
      dy: -25,
    },
    coordinates: [],
    _mapClickAddNode: true,
    _centerPointShow: type === 4,
  }
}

export function startAnnotationEdit(item: AnnotationRecord): AnnotationRecord {
  return {
    ...item,
    isEditable: true,
    _mapClickAddNode: true,
    _centerPointShow: item.type === 4,
  }
}

export function endAnnotationEdit(item: AnnotationRecord): AnnotationRecord {
  return {
    ...item,
    isEditable: false,
    _mapClickAddNode: false,
    _centerPointShow: false,
  }
}

export function loadAnnotationsLocal(): AnnotationRecord[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const list = JSON.parse(raw) as AnnotationRecord[]
    if (!Array.isArray(list)) return []
    return list.map((item) => endAnnotationEdit({ ...item, visible: item.visible !== false, isDelete: true }))
  } catch {
    return []
  }
}

export function saveAnnotationsLocal(list: AnnotationRecord[]): void {
  localStorage.setItem(
    STORAGE_KEY,
    JSON.stringify(list.map((item) => endAnnotationEdit({ ...item, coordinates: item.coordinates || [] }))),
  )
}

export function gisTypeToGeomType(type: AnnotationGisType): 'line' | 'polygon' | 'circle' {
  if (type === 2) return 'line'
  if (type === 4) return 'circle'
  return 'polygon'
}

export function geomTypeToGisType(geomType?: string): AnnotationGisType {
  const t = String(geomType || '').toLowerCase()
  if (t === 'line' || t === 'linestring') return 2
  if (t === 'circle') return 4
  return 3
}
