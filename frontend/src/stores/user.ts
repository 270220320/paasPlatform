import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { UserVO } from '@/api/auth'

const TOKEN_KEY = 'uav_access_token'
const USER_KEY = 'uav_user'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
  const user = ref<UserVO | null>(readUser())

  function readUser(): UserVO | null {
    const raw = localStorage.getItem(USER_KEY)
    if (!raw) return null
    try {
      return JSON.parse(raw) as UserVO
    } catch {
      return null
    }
  }

  function setSession(accessToken: string, profile: UserVO) {
    token.value = accessToken
    user.value = profile
    localStorage.setItem(TOKEN_KEY, accessToken)
    localStorage.setItem(USER_KEY, JSON.stringify(profile))
  }

  function clear() {
    token.value = ''
    user.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  return { token, user, setSession, clear }
})
