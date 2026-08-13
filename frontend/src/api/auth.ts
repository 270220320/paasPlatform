import http from './http'
import type { ApiResult } from './http'

export interface UserVO {
  id: number
  account: string
  displayName: string
  email?: string
  roleCode: string
  roleName: string
  permissions: string[]
}

export interface LoginResponse {
  accessToken: string
  expiresIn: number
  user: UserVO
}

export function loginApi(account: string, password: string) {
  return http.post<ApiResult<LoginResponse>>('/auth/login', { account, password })
}

export function logoutApi() {
  return http.post<ApiResult<null>>('/auth/logout')
}

export function meApi() {
  return http.get<ApiResult<UserVO>>('/auth/me')
}

export function updateEmailApi(email: string) {
  return http.put<ApiResult<null>>('/auth/email', { email })
}

export function changePasswordApi(oldPassword: string, newPassword: string) {
  return http.put<ApiResult<null>>('/auth/password', { oldPassword, newPassword })
}
