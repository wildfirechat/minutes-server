import axios from 'axios'
import { ElMessage } from 'element-plus'

const api = axios.create({
  baseURL: '',
  headers: {
    'Content-Type': 'application/json'
  },
  timeout: 30000
})

// 请求拦截器：自动附加 authToken
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('authToken')
    if (token) {
      config.headers['authToken'] = token
    }
    return config
  },
  (error) => Promise.reject(error)
)

api.interceptors.response.use(
  (response) => {
    // 登录接口特殊处理：提取响应头 token 存入 localStorage
    if (response.config.url === '/webapi/login') {
      const authToken = response.headers['authtoken'] || response.headers['authToken']
      if (authToken) {
        localStorage.setItem('authToken', authToken)
      }
    }
    return response
  },
  (error) => {
    if (error.response) {
      const status = error.response.status
      const data = error.response.data
      if (status === 401) {
        ElMessage.error(data?.message || '登录已过期，请重新访问')
        // 清除本地 token
        localStorage.removeItem('authToken')
        setTimeout(() => {
          window.location.reload()
        }, 1500)
      } else {
        ElMessage.error(data?.message || `请求失败 (${status})`)
      }
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请检查网络')
    } else {
      ElMessage.error('网络错误，请检查后重试')
    }
    return Promise.reject(error)
  }
)

export const login = (data) => api.post('/webapi/login', data)

export const getAccount = () => api.get('/webapi/account')

export const getConfigData = () => api.get('/webapi/config')

export const getMeetingDetail = (conferenceId) =>
  api.post(`/webapi/meeting/${encodeURIComponent(conferenceId)}`)

export default api
