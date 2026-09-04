import http from './http'
import type { ApiResult } from './http'
import {
  createAnnotationRecord,
  endAnnotationEdit,
  geomTypeToGisType,
  gisTypeToGeomType,
  type AnnotationCoordinate,
  type AnnotationRecord,
} from '@/utils/annotationStorage'

export interface AnnotationVO {
  id: number
  name: string
  geomType: string
  purpose?: string
  geometry: string
  style?: string
  visible: boolean
  syncStatus: string
  folderId?: number
  updatedAt?: string
}

export interface AnnotationFolderVO {
  id: number
  name: string
  locked?: boolean
}

export function fetchAnnotations(params?: Record<string, unknown>) {
  return http.get<ApiResult<AnnotationVO[]>>('/annotations', { params })
}

export function createAnnotation(data: Record<string, unknown>) {
  return http.post<ApiResult<AnnotationVO>>('/annotations', data)
}

export function updateAnnotation(id: number, data: Record<string, unknown>) {
  return http.put<ApiResult<AnnotationVO>>(`/annotations/${id}`, data)
}

export function deleteAnnotation(id: number) {
  return http.delete<ApiResult<null>>(`/annotations/${id}`)
}

export function syncAnnotations(data?: { annotationIds?: number[]; deviceIds?: number[] }) {
  return http.post<ApiResult<{ syncJobId: number; status: string }>>('/annotations/sync', data || {})
}

export function fetchSyncJob(jobId: number) {
  return http.get<ApiResult<{ id: number; status: string; message?: string }>>(`/annotations/sync/${jobId}`)
}

export function fetchMapTabs() {
  return http.get<ApiResult<{ tabs: Array<{ key: string; enabled: boolean; reason?: string }> }>>('/map/tabs/meta')
}

export function fetchAnnotationFolders() {
  return http.get<ApiResult<AnnotationFolderVO[]>>('/annotation-folders')
}

export function createAnnotationFolder(data: { name: string }) {
  return http.post<ApiResult<{ id: number }>>('/annotation-folders', data)
}

function parseJson<T>(raw: string | null | undefined, fallback: T): T {
  if (!raw) return fallback
  try {
    return JSON.parse(raw) as T
  } catch {
    return fallback
  }
}

/** GIS 标注 → 后端创建/更新体 */
export function annotationToSaveBody(item: AnnotationRecord) {
  const geomType = gisTypeToGeomType(item.type)
  const coords = item.coordinates || []
  let geometry: Record<string, unknown>
  if (item.type === 2) {
    geometry = {
      type: 'LineString',
      coordinates: coords.map((c) => [c.longitude, c.latitude, c.altitude ?? 0]),
    }
  } else if (item.type === 4) {
    const c = coords[0]
    geometry = {
      type: 'Point',
      coordinates: [c?.longitude ?? 0, c?.latitude ?? 0, c?.altitude ?? 0],
    }
  } else {
    const ring = coords.map((c) => [c.longitude, c.latitude, c.altitude ?? 0])
    if (ring.length && (ring[0][0] !== ring[ring.length - 1][0] || ring[0][1] !== ring[ring.length - 1][1])) {
      ring.push([...ring[0]])
    }
    geometry = { type: 'Polygon', coordinates: [ring] }
  }
  return {
    name: item.customLabel?.content?.trim() || item.name,
    geomType,
    purpose: item.purpose || 'other',
    geometry: JSON.stringify(geometry),
    style: JSON.stringify({
      ...item.symbol,
      clampToGround: item.isClampToGround !== false,
    }),
    visible: item.visible !== false,
  }
}

/** 后端 VO → GIS Annotations 记录 */
export function annotationVoToRecord(item: AnnotationVO): AnnotationRecord | null {
  if (item?.id == null) return null
  const geomType = String(item.geomType || '').toLowerCase()
  const gisType = geomTypeToGisType(geomType)
  const purpose = item.purpose || 'other'
  const style = parseJson<Record<string, unknown>>(item.style, {})
  const geometry = parseJson<{ type?: string; coordinates?: unknown }>(item.geometry, {})
  const base = createAnnotationRecord(gisType, item.name, purpose)
  const coordinates = extractCoordinates(geometry, gisType)
  if (gisType === 2 && coordinates.length < 2) return null
  if (gisType === 3 && coordinates.length < 3) return null
  if (gisType === 4 && coordinates.length < 1) return null

  return endAnnotationEdit({
    ...base,
    ID: String(item.id),
    name: item.name || base.name,
    purpose,
    visible: item.visible !== false,
    isClampToGround: style.clampToGround !== false,
    symbol: {
      ...base.symbol,
      lineWidth: Number(style.lineWidth) || base.symbol.lineWidth,
      lineColor: String(style.lineColor || base.symbol.lineColor || ''),
      polygonAlpha: Number(style.polygonAlpha ?? base.symbol.polygonAlpha),
      polygonColor: String(style.polygonColor || base.symbol.polygonColor || ''),
      radius: Number(style.radius ?? base.symbol.radius) || undefined,
      lineDasharray: Array.isArray(style.lineDasharray)
        ? (style.lineDasharray as number[])
        : base.symbol.lineDasharray,
    },
    customLabel: {
      ...base.customLabel,
      content: item.name || base.name,
      show: true,
    },
    coordinates,
  })
}

function extractCoordinates(
  geometry: { type?: string; coordinates?: unknown },
  gisType: ReturnType<typeof geomTypeToGisType>,
): AnnotationCoordinate[] {
  const raw = geometry.coordinates
  if (gisType === 2 && Array.isArray(raw)) {
    return raw.map((c) => toCoord(c)).filter((c): c is AnnotationCoordinate => Boolean(c))
  }
  if (gisType === 4 && Array.isArray(raw) && typeof raw[0] === 'number') {
    const c = toCoord(raw)
    return c ? [c] : []
  }
  if (gisType === 3 && Array.isArray(raw) && Array.isArray(raw[0])) {
    const ring = raw[0] as unknown[]
    return ring.map((c) => toCoord(c)).filter((c): c is AnnotationCoordinate => Boolean(c))
  }
  return []
}

function toCoord(raw: unknown): AnnotationCoordinate | null {
  if (!Array.isArray(raw) || raw.length < 2) return null
  const longitude = Number(raw[0])
  const latitude = Number(raw[1])
  if (!Number.isFinite(longitude) || !Number.isFinite(latitude)) return null
  const altitude = raw.length > 2 ? Number(raw[2]) : undefined
  return {
    longitude,
    latitude,
    ...(Number.isFinite(altitude) ? { altitude } : {}),
  }
}
