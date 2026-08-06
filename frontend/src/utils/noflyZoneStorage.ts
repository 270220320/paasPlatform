/** 自定义限飞区本地存储（浏览器 localStorage） */

export type NoFlyZoneType = 3 | 4 // 3=多边形 4=圆形

export interface NoFlyCoordinate {
  longitude: number
  latitude: number
  altitude?: number
}

export interface NoFlyZoneRecord {
  ID: string
  type: NoFlyZoneType
  name: string
  visible: boolean
  isClampToGround: boolean
  isHighlight: boolean
  isEditable: boolean
  isDelete: boolean
  zIndex: number
  defaultHeight: number
  symbol: {
    lineWidth: number
    polygonAlpha: number
    polygonColor: string
    radius?: number
  }
  customLabel: {
    show: boolean
    content: string
    showBackground: boolean
    dx: number
    dy: number
  }
  coordinates: NoFlyCoordinate[]
  /** 引擎内部：点击地图加点 */
  _mapClickAddNode?: boolean
  _centerPointShow?: boolean
}

const STORAGE_KEY = 'aiflow-nofly-zones'

export function createZoneId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

export function createNoFlyZone(type: NoFlyZoneType, name?: string): NoFlyZoneRecord {
  const label = name || (type === 4 ? '圆形限飞区' : '多边形限飞区')
  return {
    ID: createZoneId(),
    type,
    name: label,
    visible: true,
    isClampToGround: false,
    isHighlight: true,
    isEditable: true,
    isDelete: true,
    zIndex: 0,
    defaultHeight: 100,
    symbol: {
      lineWidth: 4,
      polygonAlpha: 0.35,
      polygonColor: '#E23C39',
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

export function startZoneEdit(zone: NoFlyZoneRecord): NoFlyZoneRecord {
  return {
    ...zone,
    isEditable: true,
    _mapClickAddNode: true,
    _centerPointShow: zone.type === 4,
  }
}

export function endZoneEdit(zone: NoFlyZoneRecord): NoFlyZoneRecord {
  return {
    ...zone,
    isEditable: false,
    _mapClickAddNode: false,
    _centerPointShow: false,
  }
}

export function loadNoFlyZones(): NoFlyZoneRecord[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return []
    const list = JSON.parse(raw) as NoFlyZoneRecord[]
    if (!Array.isArray(list)) return []
    return list.map((zone) => endZoneEdit({
      ...zone,
      visible: zone.visible !== false,
      isDelete: true,
      isHighlight: true,
    }))
  } catch {
    return []
  }
}

export function saveNoFlyZones(zones: NoFlyZoneRecord[]): void {
  const payload = zones.map((zone) => endZoneEdit({
    ...zone,
    coordinates: zone.coordinates || [],
  }))
  localStorage.setItem(STORAGE_KEY, JSON.stringify(payload))
}
