import http from './http'
import type { ApiResult } from './http'
import type { SafetyCoordinate } from './safety'

export interface WorkAreaVO {
  id: number
  name: string
  zoneType: number
  coordinates: SafetyCoordinate[]
  radiusM?: number
  color?: string
  lineWidth?: number
  clampToGround?: boolean
  minAltitude?: number
  maxAltitude?: number
  description?: string
  status?: string
  updatedAt?: string
}

export interface WorkAreaSaveBody {
  name: string
  zoneType: number
  coordinates: SafetyCoordinate[]
  radiusM?: number
  color?: string
  lineWidth?: number
  clampToGround?: boolean
  minAltitude?: number
  maxAltitude?: number
  description?: string
}

/** 查询本地作业区列表 */
export async function listWorkAreas() {
  const { data } = await http.get<ApiResult<WorkAreaVO[]>>('/work-areas')
  return { list: data.data || [], total: data.data?.length || 0 }
}

/** 创建作业区，返回服务端 ID */
export async function createWorkArea(body: WorkAreaSaveBody): Promise<string> {
  const { data } = await http.post<ApiResult<WorkAreaVO>>('/work-areas', body)
  const id = data.data?.id
  if (id == null) throw new Error(data.message || '创建成功但未返回 id')
  return String(id)
}

/** 更新作业区 */
export async function updateWorkArea(id: string | number, body: WorkAreaSaveBody): Promise<void> {
  await http.put<ApiResult<WorkAreaVO>>(`/work-areas/${id}`, body)
}

/** 删除作业区 */
export async function deleteWorkArea(id: string | number): Promise<void> {
  await http.delete(`/work-areas/${id}`)
}
