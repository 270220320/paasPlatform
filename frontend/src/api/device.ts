import http from './http'
import type { ApiResult } from './http'
import type { PageResult } from './iam'

export interface DeviceVO {
  id: number
  sn: string
  gatewaySn?: string
  droneSn?: string
  name: string
  deviceType: string
  model: string
  firmwareVersion?: string
  firmwareUpgradeStatus?: string
  flightSafetyDbVersion?: string
  workStatus: string
  joinedAt?: string
  onlineAt?: string
  onlineState?: string
}

export interface BindCodeVO {
  code: string
  workspaceCode: string
  workspaceId: number
  expireAt: string
  status: string
}

export function fetchDevices(params: Record<string, unknown>) {
  return http.get<ApiResult<PageResult<DeviceVO>>>('/devices', { params })
}

export function fetchCurrentBindCode() {
  return http.get<ApiResult<BindCodeVO>>('/bind-codes/current')
}

export function refreshBindCode() {
  return http.post<ApiResult<BindCodeVO>>('/bind-codes/refresh')
}

export interface DeviceIssueVO {
  id: number
  deviceId: number
  description: string
  trackingNo?: string
  status: string
  createdAt?: string
}

export function fetchDeviceIssues(params?: { deviceId?: number }) {
  return http.get<ApiResult<DeviceIssueVO[]>>('/device-issues', { params })
}

export function createDeviceIssue(data: { deviceId: number; description: string }) {
  return http.post<ApiResult<DeviceIssueVO>>('/device-issues', data)
}
