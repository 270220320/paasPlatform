/** 作业区本地存储（浏览器 localStorage），复用 NoFlyZoneRecord 结构以兼容 GIS 渲染 */
import type { NoFlyZoneRecord, NoFlyZoneType } from './noflyZoneStorage'
import { createZoneId, endZoneEdit, startZoneEdit } from './noflyZoneStorage'
import type { WorkAreaVO } from '@/api/workArea'

const STORAGE_KEY = 'aiflow-work-areas'
export const WORK_AREA_PREFIX = 'wa-'
export const WORK_AREA_DEFAULT_COLOR = '#2EB85C'

/**
 * 生成纯色线条纹理 data URI（用于 zywlgis symbol.appearance / linePatternFile）。
 * 引擎在 maptalks 2D 下边框使用 linePatternFile 而非 lineColor，
 * 不设 appearance 时默认为红色纹理，故需为作业区生成绿色纹理。
 */
function solidLineAppearance(color: string): string {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="8" height="4"><rect width="8" height="4" fill="${color}"/></svg>`
  return `data:image/svg+xml,${encodeURIComponent(svg)}`
}

/** 作业区绿色边框纹理（常态） */
const WORK_AREA_APPEARANCE = solidLineAppearance(WORK_AREA_DEFAULT_COLOR)
/** 作业区绿色边框纹理（高亮 hover 态，稍亮） */
const WORK_AREA_HIGHLIGHT_APPEARANCE = solidLineAppearance('#5cd69a')

/** 判断 GIS 图形 ID 是否为作业区（前缀区分） */
export function isWorkAreaId(id: string): boolean {
  return id.startsWith(WORK_AREA_PREFIX)
}

/** 将服务端作业区 ID 转为 GIS 使用的前缀 ID */
export function toWorkAreaGisId(id: number | string): string {
  return `${WORK_AREA_PREFIX}${id}`
}

/** 从 GIS ID 提取服务端作业区 ID */
export function fromWorkAreaGisId(gisId: string): string {
  return gisId.slice(WORK_AREA_PREFIX.length)
}

/** 创建作业区 GIS 图形记录 */
export function createWorkAreaZone(type: NoFlyZoneType, name?: string): NoFlyZoneRecord {
  const label = name || (type === 4 ? '圆形作业区' : '多边形作业区')
  return {
    ID: `${WORK_AREA_PREFIX}${createZoneId()}`,
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
      lineColor: WORK_AREA_DEFAULT_COLOR,
      polygonAlpha: 0.2,
      polygonColor: WORK_AREA_DEFAULT_COLOR,
      appearance: WORK_AREA_APPEARANCE,
      highlightAppearance: WORK_AREA_HIGHLIGHT_APPEARANCE,
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

/** 服务端 VO → GIS 图形记录 */
export function workAreaToZone(item: WorkAreaVO): NoFlyZoneRecord | null {
  if (item?.id == null) return null
  const type = (item.zoneType === 4 ? 4 : 3) as NoFlyZoneType
  const name = item.name || '作业区'
  const coords = (item.coordinates || []).filter(
    (c) => Number.isFinite(c.longitude) && Number.isFinite(c.latitude),
  )
  if (type === 4 && !coords.length) return null
  if (type === 3 && coords.length < 3) return null

  const base = createWorkAreaZone(type, name)
  return endZoneEdit({
    ...base,
    ID: toWorkAreaGisId(item.id),
    name,
    isClampToGround: item.clampToGround !== false,
    symbol: {
      ...base.symbol,
      lineWidth: item.lineWidth || 4,
      lineColor: item.color || WORK_AREA_DEFAULT_COLOR,
      polygonColor: item.color || WORK_AREA_DEFAULT_COLOR,
      appearance: solidLineAppearance(item.color || WORK_AREA_DEFAULT_COLOR),
      highlightAppearance: solidLineAppearance('#5cd69a'),
      ...(type === 4 ? { radius: Number(item.radiusM) || 300 } : {}),
    },
    customLabel: {
      ...base.customLabel,
      content: name,
      show: true,
    },
    coordinates: coords.map((c) => ({
      longitude: c.longitude,
      latitude: c.latitude,
      altitude: c.altitude,
    })),
  })
}

export function startWorkAreaEdit(zone: NoFlyZoneRecord): NoFlyZoneRecord {
  return startZoneEdit(zone)
}

export function endWorkAreaEdit(zone: NoFlyZoneRecord): NoFlyZoneRecord {
  return endZoneEdit(zone)
}

export function loadWorkAreasLocal(): NoFlyZoneRecord[] {
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

export function saveWorkAreasLocal(zones: NoFlyZoneRecord[]): void {
  const payload = zones.map((zone) => endZoneEdit({
    ...zone,
    coordinates: zone.coordinates || [],
  }))
  localStorage.setItem(STORAGE_KEY, JSON.stringify(payload))
}
