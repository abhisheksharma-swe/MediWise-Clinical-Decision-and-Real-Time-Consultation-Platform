import api from './api'

export async function loginWithEmailPassword(email, password) {
  const response = await api.post('/api/v1/auth/login', {
    emailOrPhone: email,
    password,
  })
  return response.data
}

export async function getMe() {
  const response = await api.get('/api/v1/auth/me')
  return response.data
}

export async function getAppConfig() {
  const response = await api.get('/api/v1/auth/config')
  return response.data
}

export async function register(payload) {
  const response = await api.post('/api/v1/auth/register', payload)
  return response.data
}

export async function refreshToken() {
  const storedRefreshToken = localStorage.getItem('mediwise_admin_refresh_token')
  const response = await api.post('/api/v1/auth/refresh', null, {
    headers: { 'X-Refresh-Token': storedRefreshToken },
  })
  return response.data
}

export async function forgotPassword({ emailOrPhone }) {
  const response = await api.post('/api/v1/auth/forgot-password', { emailOrPhone })
  return response.data
}

export async function resetPassword({ emailOrPhone, token, newPassword }) {
  const response = await api.post('/api/v1/auth/reset-password', { emailOrPhone, token, newPassword })
  return response.data
}

export async function changePassword({ currentPassword, newPassword }) {
  const response = await api.post('/api/v1/auth/change-password', { currentPassword, newPassword })
  return response.data
}

export async function logout() {
  try {
    const token = localStorage.getItem('mediwise_admin_token')
    if (token) {
      await api.post('/api/v1/auth/logout', null, {
        headers: { Authorization: `Bearer ${token}` },
      })
    }
  } finally {
    localStorage.removeItem('mediwise_admin_token')
    localStorage.removeItem('mediwise_admin_refresh_token')
    localStorage.removeItem('mediwise_admin_user')
  }
}
