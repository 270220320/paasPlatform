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
  uuid?: string
  name?: string
  mediaType?: string
  previewUrl?: string
  downloadUrl?: string
}

export interface ExecutionReconstructVO {
  available?: boolean
  jobId?: number
  status?: string
  fh2ModelId?: number
  previewUrl?: string
  modelUrl?: string
  errorMessage?: string
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
  reconstruct?: ExecutionReconstructVO
}

export interface ReconstructJobVO {
  jobId: number
  status: string
  modelId?: number
  fh2ModelId?: number
  missionExecutionId?: number
  previewUrl?: string
  modelUrl?: string
  errorMessage?: string
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

/** 对某次执行发起司空三维重建 */
export function startMissionReconstruct(planId: number, executionId: number) {
  return http.post<ApiResult<ReconstructJobVO>>(
    `/missions/${planId}/executions/${executionId}/reconstruct`,
    undefined,
    { timeout: 90000 },
  )
}

/** 获取已完成三维重建的模型 ZIP 下载地址 */
export function fetchReconstructModelUrl(planId: number, executionId: number) {
  return http.get<ApiResult<{ downloadUrl: string; expireAt?: string }>>(
    `/missions/${planId}/executions/${executionId}/reconstruct/model-url`,
    { timeout: 60000 },
  )
}

/** 删除执行记录下的采集媒体 */
export function deleteExecutionMedia(planId: number, executionId: number, mediaId: number) {
  return http.delete<ApiResult<null>>(
    `/missions/${planId}/executions/${executionId}/media/${mediaId}`,
  )
}
