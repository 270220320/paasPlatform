import http from './http'
import type { ApiResult } from './http'
import type { PageResult } from './iam'

export interface AlgorithmVO {
  id: number
  name: string
  algoType: string
  status: string
  updatedAt?: string
}

export function fetchAlgorithms() {
  return http.get<ApiResult<AlgorithmVO[]>>('/algorithms')
}

export function updateAlgorithm(id: number, data: { status: string; config?: unknown }) {
  return http.put<ApiResult<null>>(`/algorithms/${id}`, data)
}

export function fetchAlgorithmCapabilities() {
  return http.get<ApiResult<{ multimodalAvailable: boolean; enabledDetectionCount: number }>>('/algorithms/capabilities')
}

export function fetchAlgorithmAlarms(params?: Record<string, unknown>) {
  return http.get<ApiResult<PageResult<{ id: number; algorithmId: number; targetLabel?: string; raisedAt: string }> | Array<{ id: number; algorithmName?: string; targetLabel?: string; raisedAt: string }>>>('/algorithm-alarms', { params })
}
