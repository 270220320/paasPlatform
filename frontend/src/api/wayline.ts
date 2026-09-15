import { ElMessage } from 'element-plus'
import i18n from '@/i18n'
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

/** 导入 KMZ（U-032）：解析失败后端返回 40003；restored=true 表示恢复更新了既有同 uuid 记录 */
export function importWayline(file: File, folderId?: number) {
  const form = new FormData()
  form.append('file', file)
  if (folderId != null) form.append('folderId', String(folderId))
  return http.post<ApiResult<WaylineVO & { restored?: boolean; content?: { structuredParams?: string; storageKey?: string } }>>(
    '/waylines/import',
    form,
    { timeout: 60000 },
  )
}

export interface WaylineExportBlob {
  blob: Blob
  /** 后端 RFC 5987 编码文件名，解析失败时为空串，由调用方回退 */
  filename: string
}

/** 导出航线（U-032）：KMZ 本体优先，无本体时降级封装 JSON；鉴权失败等错误分支在此拒绝 */
export async function exportWayline(id: number): Promise<WaylineExportBlob> {
  const resp = await http.get<Blob>(`/waylines/${id}/export`, { responseType: 'blob', timeout: 60000 })
  const blob = resp.data
  const filename = parseDispositionFilename(resp.headers?.['content-disposition'] as string)
  if (blob.type.includes('application/json')) {
    // 后端错误 ApiResult 与正常导出封装 JSON 共用 application/json，按 code 字段区分：
    // 错误体 {code!=0,message}；导出体 {format:uav-wayline-export,...} 无 code
    let payload: Partial<ApiResult<unknown>> | null = null
    try {
      payload = JSON.parse(await blob.text()) as Partial<ApiResult<unknown>>
    } catch {
      payload = null
    }
    if (payload && typeof payload.code === 'number' && payload.code !== 0) {
      ElMessage.error(payload.message || i18n.global.t('http.requestFailed'))
      return Promise.reject(payload)
    }
  }
  return { blob, filename }
}

/** 解析 content-disposition 中 filename*=UTF-8''<enc>，兼容旧 filename= */
function parseDispositionFilename(disposition?: string): string {
  if (!disposition) return ''
  const star = /filename\*=UTF-8''([^;]+)/i.exec(disposition)
  if (star?.[1]) {
    try {
      return decodeURIComponent(star[1].trim().replace(/^"|"$/g, ''))
    } catch {
      return star[1]
    }
  }
  const plain = /filename="?([^";]+)"?/i.exec(disposition)
  return plain?.[1]?.trim() || ''
}

export interface WaylineFolderVO {
  id: number
  name: string
  parentId?: number
  createdAt?: string
  /** 其下航线数，>0 时不可删除（前端隐藏删除按钮） */
  waylineCount?: number
}

export function fetchWaylineFolders() {
  return http.get<ApiResult<WaylineFolderVO[]>>('/wayline-folders')
}

export function createWaylineFolder(data: { name: string; parentId?: number }) {
  return http.post<ApiResult<{ id: number }>>('/wayline-folders', data)
}

export function deleteWaylineFolder(id: number) {
  return http.delete<ApiResult<null>>(`/wayline-folders/${id}`)
}
