import http from './http'
import type { ApiResult } from './http'

export interface DashboardSummary {
  quota: {
    storageTotalBytes: number
    storageUsedBytes: number
    mappingQuota: number
    mappingUsed: number
    livestreamMinutesTotal: number
    livestreamMinutesUsed: number
  }
  dockStatus: { idle: number; working: number; debugging: number; offline: number }
  aircraftCount: number
  relayCount: number
  memberCount: number
  waylineCount: number
  algorithmCount: number
  mediaCount: number
  modelCount: number
  reportCount: number
  aiAlarmCount: number
  automation: { workflowCount: number; workflowCycles: number; workflowSuccessRate: number }
  computedAt?: string
}

export type MissionSeriesPoint = {
  date: string
  plannedCount: number
  manualCount: number
  successCount: number
  pausedCount: number
  failedCount: number
}

export type FlightSeriesPoint = {
  date: string
  sortieCount: number
  durationSec: number
  distanceM: number
}

export function fetchDashboardSummary(params?: Record<string, string>) {
  return http.get<ApiResult<DashboardSummary>>('/dashboard/summary', { params })
}

export function fetchFlightStats(params?: Record<string, string>) {
  return http.get<
    ApiResult<{
      sortieCount: number
      durationSec: number
      distanceM: number
      series: FlightSeriesPoint[]
    }>
  >('/dashboard/flight-stats', { params })
}

export function fetchMissionStats(params?: Record<string, string>) {
  return http.get<
    ApiResult<{
      plannedCount: number
      manualCount: number
      successCount: number
      pausedCount: number
      failedCount: number
      series: MissionSeriesPoint[]
    }>
  >('/dashboard/mission-stats', { params })
}
