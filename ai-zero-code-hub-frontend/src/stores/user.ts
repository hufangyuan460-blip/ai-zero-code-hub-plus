import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { userApi } from '@/api/user'
import type { LoginUserVO, UserLoginRequest, UserRegisterRequest } from '@/api/types'

export const useUserStore = defineStore('user', () => {
  const currentUser = ref<LoginUserVO | null>(null)
  const initialized = ref(false)
  const loading = ref(false)

  const isLogin = computed(() => Boolean(currentUser.value?.id))
  const isAdmin = computed(() => currentUser.value?.userRole === 'admin')

  const init = async () => {
    if (initialized.value) return
    initialized.value = true
    try {
      loading.value = true
      currentUser.value = await userApi.getCurrentUser()
    } catch {
      currentUser.value = null
    } finally {
      loading.value = false
    }
  }

  const login = async (payload: UserLoginRequest) => {
    loading.value = true
    try {
      currentUser.value = await userApi.login(payload)
      initialized.value = true
      return currentUser.value
    } finally {
      loading.value = false
    }
  }

  const register = async (payload: UserRegisterRequest) => {
    loading.value = true
    try {
      const userId = await userApi.register(payload)
      return userId
    } finally {
      loading.value = false
    }
  }

  const logout = async () => {
    loading.value = true
    try {
      await userApi.logout()
    } finally {
      currentUser.value = null
      initialized.value = true
      loading.value = false
    }
  }

  return {
    currentUser,
    initialized,
    loading,
    isLogin,
    isAdmin,
    init,
    login,
    register,
    logout,
  }
})

