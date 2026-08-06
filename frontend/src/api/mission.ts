import http from './http'
import type { ApiResult } from './http'
import type { PageResult } from './iam'

export interface MissionVO {
  id: number
  name: string
  taskType: string
  strategy: string
  status: string
  deviceId: number
  deviceName?: string
  waylineId?: number
  waylineName?: string
  accuracyMode?: string
  scheduleAt?: string
  cronExpr?: string
  scheduleConfig?: string
  minFlightHeightM?: number
  signalLostAction?: string
  createdAt?: string
  updatedAt?: string
}

export interface ExecutionMediaVO {
  id?: number
  name?: string
  mediaType?: string
  previewUrl?: string
}

export interface MissionExecutionVO {
  id?: number | null
  recordType?: string
  status: string
  plannedAt?: string
  actualStartAt?: string
  actualEndAt?: string
  flightDurationSec?: number
  flightDistanceM?: number
  failReason?: string
  fh2TaskUuid?: string
  media?: ExecutionMediaVO[]
}

export interface MissionDetailVO {
  plan: MissionVO
  executions: MissionExecutionVO[]
  totalCount: number
  pendingCount: number
  runningCount: number
  completedCount: number
  failedCount: number
  cancelledCount: number
}

export function fetchMissions(params?: Record<string, unknown>) {
  return http.get<ApiResult<PageResult<MissionVO> | MissionVO[]>>('/missions', { params })
}

export function fetchMissionDetail(id: number) {
  return http.get<ApiResult<MissionDetailVO>>(`/missions/${id}`)
}

export function createMission(data: Record<string, unknown>) {
  return http.post<ApiResult<MissionVO>>('/missions', data)
}

export function missionAction(id: number, action: 'pause' | 'resume' | 'cancel' | 'copy') {
  return http.post<ApiResult<MissionVO | null>>(`/missions/${id}/actions/${action}`)
}

export function deleteMission(id: number) {
  return http.delete<ApiResult<null>>(`/missions/${id}`)
}
