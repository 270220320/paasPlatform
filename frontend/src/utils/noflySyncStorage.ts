/** 限飞区设备同步状态本地缓存（与 /safety/syncstatus 合并展示） */

import type { NoFlyZoneRecord } from './noflyZoneStorage'

export type FlightAreaSyncStatus =
  | 'wait_sync'
  | 'synchronizing'
  | 'synchronized'
  | 'fail'
  | 'unknown'

export interface DeviceFlightAreaSync {
  deviceSn: string
  nickname?: string
  syncStatus: FlightAreaSyncStatus
  syncMsg?: string
  /** 最近一次成功同步时的限飞区内容指纹 */
  zoneHash?: string
  updatedAt?: string
}

const STORAGE_KEY = 'aiflow-nofly-device-sync'

export const SYNC_STATUS_LABEL: Record<FlightAreaSyncStatus, string> = {
  wait_sync: '待同步',
  synchronizing: '同步中',
  synchronized: '已同步',
  fail: '同步失败',
  unknown: '未知',
}

export const SYNC_STATUS_TAG_TYPE: Record<FlightAreaSyncStatus, '' | 'success' | 'warning' | 'danger' | 'info'> = {
  wait_sync: 'warning',
  synchronizing: '',
  synchronized: 'success',
  fail: 'danger',
  unknown: 'info',
}

/** 计算限飞区内容指纹，用于判断设备是否需要重新同步 */
export function computeZoneHash(zones: NoFlyZoneRecord[]): string {
  const normalized = zones
    .map((z) => ({
      ID: z.ID,
      type: z.type,
      name: z.customLabel?.content || z.name,
      radius: z.symbol?.radius,
      coordinates: (z.coordinates || []).map((c) => [
        Number(c.longitude.toFixed(7)),
        Number(c.latitude.toFixed(7)),
      ]),
    }))
    .sort((a, b) => a.ID.localeCompare(b.ID))
  return JSON.stringify(normalized)
}

export function loadDeviceSyncMap(): Record<string, DeviceFlightAreaSync> {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return {}
    const list = JSON.parse(raw) as DeviceFlightAreaSync[]
    if (!Array.isArray(list)) return {}
    return Object.fromEntries(list.map((item) => [item.deviceSn, item]))
  } catch {
    return {}
  }
}

export function saveDeviceSyncMap(map: Record<string, DeviceFlightAreaSync>): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(Object.values(map)))
}

/**
 * 合并设备列表与本地同步记录。
 * preferServer=true 时，信任已写入的服务端状态，不再用 zoneHash 改写。
 */
export function resolveDeviceSyncList(
  devices: Array<{ sn: string; name?: string }>,
  zones: NoFlyZoneRecord[],
  options: { preferServer?: boolean } = {},
): DeviceFlightAreaSync[] {
  const map = loadDeviceSyncMap()
  const hash = computeZoneHash(zones)
  const preferServer = Boolean(options.preferServer)

  return devices.map((device) => {
    const prev = map[device.sn]
    let syncStatus: FlightAreaSyncStatus = prev?.syncStatus || 'wait_sync'
    let syncMsg = prev?.syncMsg || ''

    if (!preferServer) {
      if (syncStatus === 'synchronizing') {
        syncStatus = 'fail'
        syncMsg = syncMsg || '同步中断，请重试'
      } else if (syncStatus === 'synchronized') {
        if (!zones.length) {
          syncStatus = 'wait_sync'
          syncMsg = '暂无限飞区'
        } else if (prev?.zoneHash && prev.zoneHash !== hash) {
          syncStatus = 'wait_sync'
          syncMsg = '限飞区已变更，需重新同步'
        }
      } else if (!prev) {
        syncStatus = 'wait_sync'
        syncMsg = zones.length ? '尚未同步到该设备' : '暂无限飞区'
      }
    } else if (!prev) {
      syncStatus = 'unknown'
      syncMsg = '尚未查询到同步状态'
    }

    return {
      deviceSn: device.sn,
      nickname: device.name || prev?.nickname || device.sn,
      syncStatus,
      syncMsg,
      zoneHash: prev?.zoneHash,
      updatedAt: prev?.updatedAt,
    }
  })
}

/** 写入或合并单台设备的同步状态 */
export function upsertDeviceSync(patch: DeviceFlightAreaSync): DeviceFlightAreaSync {
  const map = loadDeviceSyncMap()
  const next: DeviceFlightAreaSync = {
    ...map[patch.deviceSn],
    ...patch,
    updatedAt: new Date().toISOString(),
  }
  map[patch.deviceSn] = next
  saveDeviceSyncMap(map)
  return next
}
