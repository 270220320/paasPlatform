import http from './http'
import type { ApiResult } from './http'
import type { PageResult } from './iam'

export interface WaylineVO {
  id?: number | null
  name: string
  aircraftModel: string
  waylineType: string
  status: string
  fh2WaylineUuid?: string
  source?: string
  folderId?: number
  updatedAt?: string
}

export function fetchWaylines(params?: Record<string, unknown>) {
  return http.get<ApiResult<PageResult<WaylineVO> | WaylineVO[]>>('/waylines', { params })
}

export function fetchWayline(id: number) {
  return http.get<ApiResult<WaylineVO & { content?: { structuredParams?: string; storageKey?: string } }>>(`/waylines/${id}`)
}

export function createWayline(data: Record<string, unknown>) {
  return http.post<ApiResult<{ id: number } | WaylineVO>>('/waylines', data)
}

export function updateWayline(id: number, data: Record<string, unknown>) {
  return http.put<ApiResult<null>>(`/waylines/${id}`, data)
}

export function deleteWayline(id: number) {
  return http.delete<ApiResult<null>>(`/waylines/${id}`)
}

export function deleteWaylineByUuid(waylineUuid: string) {
  return http.delete<ApiResult<null>>(`/waylines/uuid/${encodeURIComponent(waylineUuid)}`)
}

export interface WaylineFolderVO {
  id: number
  name: string
  parentId?: number
  createdAt?: string
}

export function fetchWaylineFolders() {
  return http.get<ApiResult<WaylineFolderVO[]>>('/wayline-folders')
}

export function createWaylineFolder(data: { name: string; parentId?: number }) {
  return http.post<ApiResult<{ id: number }>>('/wayline-folders', data)
}
