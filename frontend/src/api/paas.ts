import http from './http'
import type { ApiResult } from './http'
import type { PageResult } from './iam'
import type { DeviceVO } from './device'
import type { WaylineVO } from './wayline'

export interface PaasBootstrap {
  enabled: boolean
  frontHost: string
  serverHost: string
  gatewayBase: string
  wssUrl: string
  userToken: string
  bindingUuid?: string
  organizationUuid?: string
  projectUuid?: string
  objectKeyPrefix?: string
  waylineSdkCss: string
  waylineSdkJs: string
  deviceSdkCss: string
  deviceSdkJs: string
  replaySdkCss?: string
  replaySdkJs?: string
  /** 直播大屏（DJILivePlayer）SDK 资源，独立于 paas.js */
  liveSdkCss?: string
  liveSdkJs?: string
  authHeaderName: string
}

export function fetchPaasBootstrap() {
  return http.get<ApiResult<PaasBootstrap>>('/paas/bootstrap')
}

export function fetchPaasSdkStatus() {
  return http.get<ApiResult<{ enabled: boolean; sdkAvailable: boolean; initError?: string }>>('/paas/sdk/status')
}

export function syncFh2Devices() {
  return http.post<ApiResult<{ synced: number }>>('/paas/devices/sync')
}

export function syncFh2Waylines() {
  return http.post<ApiResult<{ synced: number }>>('/paas/waylines/sync')
}

export function upsertFh2Wayline(data: { waylineUuid: string; name?: string; folderId?: number }) {
  return http.post<ApiResult<WaylineVO>>('/paas/waylines/upsert', data)
}

export function fetchRemoteFh2Devices() {
  return http.get<ApiResult<PageResult<DeviceVO>>>('/paas/devices/remote')
}

/** 机场远程开机（调试模式→开舱盖→飞行器上电）；第一步同步快速失败，机械动作后台执行 */
export function powerOnFh2Device(dockSn: string) {
  return http.post<ApiResult<{ accepted: boolean; message: string }>>(
    `/paas/devices/${encodeURIComponent(dockSn)}/power-on`,
  )
}

/** 机场远程关机（飞行器断电→关舱盖→退出调试模式） */
export function powerOffFh2Device(dockSn: string) {
  return http.post<ApiResult<{ accepted: boolean; message: string }>>(
    `/paas/devices/${encodeURIComponent(dockSn)}/power-off`,
  )
}

export interface AircraftLocationVO {
  sn: string
  name?: string
  gatewaySn?: string
  model?: string
  modeCode?: string
  latitude: number
  longitude: number
  height?: number
  elevation?: number
  /** 机头朝向角（度，北为 0，顺时针） */
  heading?: number
  updatedAt?: number
}

/** 在飞飞行器实时位置（首页地图） */
export function fetchFlyingAircraftLocations() {
  return http.get<ApiResult<AircraftLocationVO[]>>('/paas/aircraft/locations')
}

type RemoteDevicePayload = {
  code?: number
  message?: string
  data?: unknown
}

function asDeviceList(data: unknown, parentGatewaySn = ''): Record<string, unknown>[] {
  if (Array.isArray(data)) {
    return data.flatMap((item) => {
      if (!item || typeof item !== 'object') return []
      const device = item as Record<string, unknown>
      const ownSn = String(device.sn ?? device.device_sn ?? '')
      const gatewaySn = String(device.gatewaySn ?? device.gateway_sn ?? parentGatewaySn)
      const nestedDevices = [
        device.child_device_list,
        device.childDeviceList,
        device.sub_device_list,
        device.subDeviceList,
        device.sub_devices,
        device.subDevices,
        device.sub_device,
        device.subDevice,
        device.devices,
      ]
      const children = nestedDevices.flatMap((children) => asDeviceList(children, ownSn || gatewaySn))
      const directChild = nestedDevices.flatMap((children) => Array.isArray(children) ? children : [children])
        .find((child) => child && typeof child === 'object') as Record<string, unknown> | undefined
      const droneSn = [
        device.drone_sn,
        device.droneSn,
        directChild?.sn,
        directChild?.device_sn,
      ].map((item) => String(item ?? '').trim()).find(Boolean) || ''
      return [{
        ...device,
        gateway_sn: gatewaySn,
        ...(droneSn ? { cockpit_drone_sn: droneSn } : {}),
      }, ...children]
    })
  }
  if (data && typeof data === 'object') {
    const value = data as Record<string, unknown>
    for (const key of ['list', 'items', 'devices', 'device_list', 'deviceList', 'data']) {
      const result = asDeviceList(value[key], parentGatewaySn)
      if (result.length) return result
    }
  }
  return []
}

/** 取首个非空字符串（trim 后），全部为空返回 ''；用于避免 `??` 对空串不回退的坑 */
function firstNonEmpty(...candidates: unknown[]): string {
  for (const item of candidates) {
    const value = String(item ?? '').trim()
    if (value) return value
  }
  return ''
}

/** 将司空设备管理接口的原始 payload 映射为前端 DeviceVO 列表（纯函数，便于单测） */
export function mapCockpitDevices(payload: RemoteDevicePayload): DeviceVO[] {
  return asDeviceList(payload.data).map((raw) => {
    const gateway = raw.gateway as Record<string, unknown> | undefined
    const deviceModel = (raw.deviceModel ?? raw.device_model ?? {}) as Record<string, unknown>
    const deviceState = (raw.deviceState ?? raw.device_state ?? {}) as Record<string, unknown>
    const subDevice = (deviceState.subDevice ?? deviceState.sub_device ?? {}) as Record<string, unknown>
    const associateDrone = (
      raw.associateDroneDeviceInfo ?? raw.associate_drone_device_info ?? {}
    ) as Record<string, unknown>
    const associateModel = (
      associateDrone.deviceModel ?? associateDrone.device_model ?? {}
    ) as Record<string, unknown>
    const sn = firstNonEmpty(raw.sn, raw.device_sn)
    const gatewaySn = firstNonEmpty(
      raw.gatewaySn,
      raw.gateway_sn,
      gateway?.sn,
      gateway?.device_sn,
      sn,
    )
    // 司空机场主记录：飞行器在 device_state.sub_device / associate_drone_device_info。
    // asDeviceList 可能写入空串 cockpit_drone_sn，不能用 ??（空串不会回退）。
    const droneSn = firstNonEmpty(
      subDevice.deviceSn,
      subDevice.device_sn,
      associateDrone.deviceSn,
      associateDrone.device_sn,
      raw.droneSn,
      raw.drone_sn,
      raw.cockpit_drone_sn,
    )
    const dockOnline = raw.deviceOnlineStatus ?? raw.device_online_status
    const droneOnline = subDevice.deviceOnlineStatus ?? subDevice.device_online_status
      ?? associateDrone.deviceOnlineStatus ?? associateDrone.device_online_status
    const onlineFrom = (v: unknown): string => {
      if (v === true || v === 1) return 'online'
      if (v === false || v === 0) return 'offline'
      return String(v ?? '').trim()
    }
    const dockState = onlineFrom(dockOnline)
    const droneState = onlineFrom(droneOnline)
    return {
      id: Number(raw.id ?? 0),
      sn,
      gatewaySn,
      droneSn,
      name: firstNonEmpty(raw.name, raw.device_name, raw.device_callsign, deviceModel.name, sn),
      deviceType: firstNonEmpty(raw.deviceType, raw.device_type, deviceModel.class),
      model: typeof raw.device_model === 'string'
        ? raw.device_model
        : firstNonEmpty(raw.model, deviceModel.name, deviceModel.class, associateModel.name),
      workStatus: firstNonEmpty(raw.workStatus, raw.work_status, raw.device_working_status),
      onlineState: firstNonEmpty(
        raw.onlineState,
        raw.online_state,
        // 列表每行是机场，在线状态以机场主记录为准：
        // - 机场离线 → 必判离线（device_state.sub_device 是最后一次 OSD 快照，
        //   机场离线后残留历史值，不可采信）；
        // - 机场在线 → 判在线。飞行器常处舱内未开机（sub_device=0）属正常状态，
        //   不能用它拉低机场状态（曾导致驾驶舱误报机场离线）。
        dockState,
        droneState,
      ),
    }
  })
}

/**
 * 直接查询司空设备管理接口；Vite/网关将 /paas-api 转发到 192.168.76.141:30812。
 */
export async function fetchCockpitDevices(): Promise<DeviceVO[]> {
  const response = await fetch('/paas-api/gateway/device/api/v1/manager/devices', {
    headers: {
      // 网关据此挑选并转发鉴权凭据；缺失时会直接返回 401。
      auth_keys: 'user_id',
      'X-User-Token': localStorage.getItem('x-user-token') || '',
      // 私有化司空无 user token，靠 user_id 鉴权（与 setupDeviceManager auth 一致）
      user_id: localStorage.getItem('paas-user-id') || 'paas_test',
    },
  })
  if (!response.ok) {
    throw new Error(`设备列表请求失败：HTTP ${response.status}`)
  }
  const payload = (await response.json()) as RemoteDevicePayload
  if (payload.code !== undefined && payload.code !== 0) {
    throw new Error(payload.message || '设备列表请求失败')
  }
  return mapCockpitDevices(payload)
}
