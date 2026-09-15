import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import router from '@/router'
import i18n from '@/i18n'

export interface ApiResult<T> {
  code: number
  message: string
  data: T
  requestId?: string
}

const http = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
})

// 鉴权失效去重标记：多个并发请求同时返回 401 时，只提示并跳转登录一次
let redirectingToLogin = false

http.interceptors.request.use((config) => {
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    // blob 下载响应（航线导出等）无 ApiResult 包装，直接放行，由调用方处理错误分支
    if (response.config.responseType === 'blob') return response
    const payload = response.data as ApiResult<unknown>
    if (payload.code === 0) {
      return response
    }
    if (payload.code === 40100 || payload.code === 40101) {
      if (!redirectingToLogin) {
        redirectingToLogin = true
        const userStore = useUserStore()
        userStore.clear()
        router.replace({ name: 'login' })
        ElMessage.error(payload.message || i18n.global.t('http.requestFailed'))
        // 跳转完成后再放开标记，避免影响下一次会话过期的提示
        setTimeout(() => {
          redirectingToLogin = false
        }, 3000)
      }
      return Promise.reject(payload)
    }
    ElMessage.error(payload.message || i18n.global.t('http.requestFailed'))
    return Promise.reject(payload)
  },
  (error) => {
    ElMessage.error(error?.message || i18n.global.t('http.networkError'))
    return Promise.reject(error)
  },
)

export default http
