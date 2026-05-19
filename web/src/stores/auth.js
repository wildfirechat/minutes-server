import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useAuthStore = defineStore('auth', () => {
  const userInfo = ref(null)
  const conferenceId = ref('')
  const authToken = ref(localStorage.getItem('authToken') || '')

  const isLoggedIn = computed(() => !!userInfo.value && !!authToken.value)

  function setParams(params) {
    conferenceId.value = params.conferenceId || ''
  }

  function setUserInfo(info) {
    userInfo.value = info
  }

  function setAuthToken(token) {
    authToken.value = token
    localStorage.setItem('authToken', token)
  }

  function clearAuth() {
    userInfo.value = null
    conferenceId.value = ''
    authToken.value = ''
    localStorage.removeItem('authToken')
  }

  return {
    userInfo,
    conferenceId,
    authToken,
    isLoggedIn,
    setParams,
    setUserInfo,
    setAuthToken,
    clearAuth
  }
})
