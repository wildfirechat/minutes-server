<template>
  <div class="entry-page">
    <el-result
      v-if="error"
      icon="error"
      :title="errorTitle"
      :sub-title="errorMsg"
    >
      <template #extra>
        <el-button type="primary" @click="retry">重试</el-button>
      </template>
    </el-result>

    <div v-else class="loading-wrap">
      <el-icon class="is-loading" :size="40"><Loading /></el-icon>
      <p class="loading-text">{{ loadingText }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { login, getAccount, getConfigData } from '@/api'
import wf from '@/jssdk/wf'

const router = useRouter()
const authStore = useAuthStore()

const loadingText = ref('正在加载...')
const error = ref(false)
const errorTitle = ref('加载失败')
const errorMsg = ref('')
const loginStatus = ref(0) // 0: 登录中, 1: 成功, -1: 失败
const configStatus = ref(0) // 0: 认证中, 1: 成功, -1: 失败

function getQueryParams() {
  const search = new URLSearchParams(window.location.search)
  return {
    conferenceId: search.get('conferenceId') || '',
    robotId: search.get('robotId') || ''
  }
}

function validateParams(params) {
  if (!params.conferenceId) return '缺少 conferenceId'
  if (!params.robotId) return '缺少 robotId'
  return null
}

const status = computed(() => {
  if (loginStatus.value === 1 && configStatus.value === 1) return 1
  if (loginStatus.value === -1 || configStatus.value === -1) return -1
  return 0
})

watch(status, (val) => {
  if (val === 1) {
    router.replace('/meeting')
  }
})

async function getAccountInfo() {
  try {
    const res = await getAccount()
    if (res.data.code === 0 && res.data.data) {
      authStore.setUserInfo(res.data.data)
      return true
    }
  } catch (e) {
    console.log('getAccount error', e)
  }
  return false
}

async function doLogin() {
  const params = getQueryParams()
  const invalid = validateParams(params)
  if (invalid) {
    error.value = true
    errorTitle.value = '参数错误'
    errorMsg.value = invalid
    return
  }

  authStore.setParams(params)

  // 并行获取 account 和 config
  const accountPromise = getAccountInfo()

  let configData
  try {
    const configRes = await getConfigData()
    if (configRes.data.code !== 0) {
      throw new Error(configRes.data.message || '获取配置失败')
    }
    configData = configRes.data.data
  } catch (e) {
    error.value = true
    errorTitle.value = '配置获取失败'
    errorMsg.value = e.message || '无法获取应用配置'
    return
  }

  // Native 认证 Web
  wf.config(configData)

  wf.ready(() => {
    configStatus.value = 1
    if (loginStatus.value === 1) {
      return
    }
    // 获取授权码并登录
    wf.biz.getAuthCode(configData.appId, configData.appType, async (authCode) => {
      try {
        const res = await login({
          robotId: params.robotId,
          authCode: authCode
        })
        if (res.data.code !== 0) {
          throw new Error(res.data.message || '登录失败')
        }
        authStore.setUserInfo(res.data.data)
        loginStatus.value = 1
        ElMessage.success('登录成功')
      } catch (err) {
        loginStatus.value = -1
        error.value = true
        errorTitle.value = '登录失败'
        errorMsg.value = err.message || '网络错误，请检查后重试'
      }
    }, (err) => {
      loginStatus.value = -1
      error.value = true
      errorTitle.value = '授权失败'
      errorMsg.value = '无法获取授权码: ' + err
    })
  })

  wf.error((reason) => {
    configStatus.value = -1
    error.value = true
    errorTitle.value = 'Native 认证失败'
    errorMsg.value = reason || '无法完成 Native 认证'
  })

  // 等待 account 结果
  const hasAccount = await accountPromise
  if (hasAccount) {
    loginStatus.value = 1
  } else {
    loadingText.value = '正在登录...'
    authStore.clearAuth()
  }
}

function retry() {
  error.value = false
  loginStatus.value = 0
  configStatus.value = 0
  doLogin()
}

onMounted(() => {
  doLogin()
})
</script>

<style scoped>
.entry-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
}

.loading-wrap {
  text-align: center;
}

.loading-text {
  margin-top: 16px;
  color: #606266;
  font-size: 14px;
}
</style>
