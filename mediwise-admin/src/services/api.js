import axios from 'axios'
import { API_BASE_URL } from '../utils/constants'

const api = axios.create({
  baseURL: API_BASE_URL || undefined,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('mediwise_admin_token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

function forceLogout() {
  localStorage.removeItem('mediwise_admin_token')
  localStorage.removeItem('mediwise_admin_refresh_token')
  localStorage.removeItem('mediwise_admin_user')
  if (window.location.pathname !== '/login') {
    window.location.href = '/login'
  }
}

let isRefreshing = false
let pendingRequests = []

function subscribeTokenRefresh(callback) {
  pendingRequests.push(callback)
}

function onTokenRefreshed(newToken) {
  pendingRequests.forEach((callback) => callback(newToken))
  pendingRequests = []
}

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config || {}
    const status = error.response?.status
    const url = originalRequest.url || ''
    const isLoginRequest = url.includes('/api/v1/auth/login')
    const isRefreshRequest = url.includes('/api/v1/auth/refresh')

    // Silent refresh: on a 401 (not login/refresh itself), try one token
    // refresh and replay the original request before forcing logout.
    if (status === 401 && !isLoginRequest && !isRefreshRequest && !originalRequest._retry) {
      const storedRefreshToken = localStorage.getItem('mediwise_admin_refresh_token')
      if (!storedRefreshToken) {
        forceLogout()
        return Promise.reject(error)
      }

      originalRequest._retry = true

      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          subscribeTokenRefresh((newToken) => {
            if (!newToken) {
              reject(error)
              return
            }
            originalRequest.headers = { ...originalRequest.headers, Authorization: `Bearer ${newToken}` }
            resolve(api(originalRequest))
          })
        })
      }

      isRefreshing = true
      try {
        const { refreshToken } = await import('./authService')
        const res = await refreshToken()
        const newToken = res?.data?.accessToken
        const newRefreshToken = res?.data?.refreshToken

        if (!newToken) {
          throw new Error('Token refresh returned no access token')
        }

        localStorage.setItem('mediwise_admin_token', newToken)
        if (newRefreshToken) {
          localStorage.setItem('mediwise_admin_refresh_token', newRefreshToken)
        }

        isRefreshing = false
        onTokenRefreshed(newToken)

        originalRequest.headers = { ...originalRequest.headers, Authorization: `Bearer ${newToken}` }
        return api(originalRequest)
      } catch (refreshError) {
        isRefreshing = false
        onTokenRefreshed(null)
        forceLogout()
        return Promise.reject(refreshError)
      }
    }

    if ((status === 401 || status === 403) && !isLoginRequest) {
      forceLogout()
    }
    return Promise.reject(error)
  }
)

export default api
