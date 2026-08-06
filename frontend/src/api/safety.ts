import http from './http'
import type { ApiResult } from './http'
import type { NoFlyZoneRecord } from '@/utils/noflyZoneStorage'
import { createNoFlyZone, endZoneEdit, type NoFlyZoneType } from '@/utils/noflyZoneStorage'
import type { FlightAreaSyncStatus } from '@/utils/noflySyncStorage'

export interface SafetyCoordinate {
  longitude: number
  latitude: number
  altitude?: number
}

export interface SafetyAreaVO {
  id: number
  name: string
  zoneType: number
  coordinates: SafetyCoordinate[]
  radiusM?: number
  color?: string
  lineWidth?: number
  clampToGround?: boolean
  syncStatus?: string
  updatedAt?: string
}

export interface SafetyDeviceSyncVO {
  deviceSn: string
  nickname?: string
  syncStatus: string
  syncMsg?: string
  zoneHash?: string
  updatedAt?: string
}

export interface SafetyAreaSaveBody {
  name: string
  zoneType: number
  coordinates: SafetyCoordinate[]
  radiusM?: number
  color?: string
  lineWidth?: number
  clampToGround?: boolean
}

/** 查询本地限飞区列表 */
export async function listSafetyAreas() {
  const { data } = await http.get<ApiResult<SafetyAreaVO[]>>('/safety/areas')
  return { list: data.data || [], total: data.data?.length || 0 }
}

/** 创建限飞区，返回服务端 ID（字符串，供 GIS ID 使用） */
export async function createSafetyArea(body: SafetyAreaSaveBody): Promise<string> {
  const { data } = await http.post<ApiResult<SafetyAreaVO>>('/safety/areas', body)
  const id = data.data?.id
  if (id == null) throw new Error(data.message || '创建成功但未返回 id')
  return String(id)
}

/** 更新限飞区 */
export async function updateSafetyArea(id: string | number, body: SafetyAreaSaveBody): Promise<void> {
  await http.put<ApiResult<SafetyAreaVO>>(`/safety/areas/${id}`, body)
}

/** 删除限飞区 */
export async function deleteSafetyArea(id: string | number): Promise<void> {
  await http.delete(`/safety/areas/${id}`)
}

/** 同步限飞区状态到本地设备同步表 */
export async function syncSafetyAreas(
  deviceSns: string[],
  nicknames?: Record<string, string>,
): Promise<SafetyDeviceSyncVO[]> {
  if (!deviceSns.length) throw new Error('请选择需要同步的设备')
  const { data } = await http.post<ApiResult<SafetyDeviceSyncVO[]>>('/safety/sync', {
    deviceSns,
    nicknames,
  })
  return data.data || []
}

/** 查询设备同步状态 */
export async function fetchSafetySyncStatus(deviceSns: string[]): Promise<SafetyDeviceSyncVO[]> {
  if (!deviceSns.length) return []
  const { data } = await http.get<ApiResult<SafetyDeviceSyncVO[]>>('/safety/sync-status', {
    params: { deviceSns },
    paramsSerializer: {
      serialize: (params) => {
        const q = new URLSearchParams()
        const list = (params as { deviceSns?: string[] }).deviceSns || []
        list.forEach((sn) => q.append('deviceSns', sn))
        return q.toString()
      },
    },
  })
  return data.data || []
}

/** GIS 限飞区 → 本地保存请求体 */
export function zoneToSaveBody(zone: NoFlyZoneRecord): SafetyAreaSaveBody {
  return {
    name: zone.customLabel?.content?.trim() || zone.name,
    zoneType: zone.type,
    coordinates: (zone.coordinates || []).map((c) => ({
      longitude: c.longitude,
      latitude: c.latitude,
      altitude: c.altitude,
    })),
    radiusM: zone.type === 4 ? Number(zone.symbol?.radius) || 300 : undefined,
    color: zone.symbol?.polygonColor || '#E23C39',
    lineWidth: Number(zone.symbol?.lineWidth) || 4,
    clampToGround: zone.isClampToGround !== false,
  }
}

/** 本地 VO → GIS CustomNoFlyZones 记录 */
export function safetyAreaToZone(item: SafetyAreaVO): NoFlyZoneRecord | null {
  if (item?.id == null) return null
  const type = (item.zoneType === 4 ? 4 : 3) as NoFlyZoneType
  const name = item.name || '限飞区'
  const coords = (item.coordinates || []).filter(
    (c) => Number.isFinite(c.longitude) && Number.isFinite(c.latitude),
  )
  if (type === 4 && !coords.length) return null
  if (type === 3 && coords.length < 3) return null

  const base = createNoFlyZone(type, name)
  return endZoneEdit({
    ...base,
    ID: String(item.id),
    name,
    isClampToGround: item.clampToGround !== false,
    symbol: {
      ...base.symbol,
      lineWidth: item.lineWidth || 4,
      polygonColor: item.color || '#E23C39',
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

/** 状态值映射到页面状态 */
export function mapSafetySyncStatus(raw?: string): FlightAreaSyncStatus {
  const value = String(raw || '').trim().toLowerCase()
  if (!value) return 'unknown'
  if (value === 'waiting' || value === 'wait_sync') return 'wait_sync'
  if (value === 'synchronizing' || value === 'syncing') return 'synchronizing'
  if (value === 'synchronized' || value === 'synced' || value === 'success') return 'synchronized'
  if (value === 'fail' || value === 'failed' || value === 'error') return 'fail'
  return 'unknown'
}

export function getSyncStatusDeviceSn(item: SafetyDeviceSyncVO): string {
  return String(item.deviceSn || '').trim()
}
