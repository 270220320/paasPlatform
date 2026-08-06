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

http.interceptors.request.use((config) => {
  const userStore = useUserStore()
  if (userStore.token) {
    config.headers.Authorization = `Bearer ${userStore.token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const payload = response.data as ApiResult<unknown>
    if (payload.code === 0) {
      return response
    }
    if (payload.code === 40100 || payload.code === 40101) {
      const userStore = useUserStore()
      userStore.clear()
      router.replace({ name: 'login' })
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
