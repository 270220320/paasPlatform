import http from './http'
import type { ApiResult } from './http'

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

export interface MemberVO {
  id: number
  account: string
  displayName: string
  roleId: number
  roleName: string
  joinMethod: string
  joinedAt: string
  status: string
}

export interface RoleVO {
  id: number
  code: string
  name: string
  description?: string
  isSystem?: boolean
  locked?: boolean
  permissionIds?: number[]
  permissionCodes?: string[]
}

export interface PermissionVO {
  id: number
  code: string
  module: string
  action: string
  name: string
}

export function fetchMembers(params: Record<string, unknown>) {
  return http.get<ApiResult<PageResult<MemberVO>>>('/members', { params })
}

export function createMember(data: {
  account: string
  displayName: string
  roleId: number
  password: string
}) {
  return http.post<ApiResult<{ id: number }>>('/members', data)
}

export function updateMember(id: number, data: { displayName?: string; roleId?: number; status?: string }) {
  return http.put<ApiResult<null>>(`/members/${id}`, data)
}

export function deleteMember(id: number) {
  return http.delete<ApiResult<null>>(`/members/${id}`)
}

export function fetchRoles() {
  return http.get<ApiResult<RoleVO[]>>('/roles')
}

export function fetchRoleOptions() {
  return http.get<ApiResult<RoleVO[]>>('/roles/options')
}

export function fetchRoleDetail(id: number) {
  return http.get<ApiResult<RoleVO>>(`/roles/${id}`)
}

export function updateRole(id: number, data: { name?: string; description?: string; permissionIds: number[] }) {
  return http.put<ApiResult<null>>(`/roles/${id}`, data)
}

export function fetchPermissions() {
  return http.get<ApiResult<PermissionVO[]>>('/permissions')
}
