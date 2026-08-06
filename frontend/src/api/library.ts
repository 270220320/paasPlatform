import http from './http'
import type { ApiResult } from './http'
import type { PageResult } from './iam'

export interface MediaVO {
  id: number
  name: string
  mediaType: string
  payload?: string
  sizeBytes: number
  source: string
  recycleStatus: string
  createdAt?: string
}

export function fetchMedia(params?: Record<string, unknown>) {
  return http.get<ApiResult<PageResult<MediaVO>>>('/library/media', { params })
}

export function fetchMediaDownloadUrl(id: number) {
  return http.get<ApiResult<{ downloadUrl: string; expireAt?: string }>>(`/library/media/${id}/download-url`)
}

export function confirmMedia(data: Record<string, unknown>) {
  return http.post<ApiResult<{ id: number }>>('/library/media', data)
}

export function uploadCredential(data: { fileName: string; mediaType: string; sizeBytes: number }) {
  return http.post<ApiResult<{ uploadUrl: string; storageKey: string; expireAt?: string }>>('/library/upload-credential', data)
}

export function fetchQuota() {
  return http.get<ApiResult<Record<string, number>>>('/library/quota')
}

export function fetchLibraryFeatures() {
  return http.get<ApiResult<{ smartSearch: boolean; changeDetectionPro: boolean; ultraPanorama: boolean }>>(
    '/library/features',
  )
}

export function trashMedia(id: number) {
  return http.post<ApiResult<null>>(`/library/media/${id}/trash`)
}

export function restoreMedia(id: number) {
  return http.post<ApiResult<null>>(`/library/media/${id}/restore`)
}

export function createReconstructJob(mediaIds: number[]) {
  return http.post<ApiResult<{ jobId: number; status: string }>>('/reconstruct-jobs', { mediaIds })
}

export function fetchReconstructJobs() {
  return http.get<ApiResult<PageResult<{ id: number; status: string; createdAt?: string }> | Array<{ id: number; status: string }>>>('/reconstruct-jobs')
}
