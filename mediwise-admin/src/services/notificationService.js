import api from './api'

export async function getNotifications(params = {}) {
  const response = await api.get('/api/v1/notifications', { params })
  return response.data
}

export async function markNotificationRead(id) {
  const response = await api.patch(`/api/v1/notifications/${id}/read`)
  return response.data
}

export async function markAllNotificationsRead() {
  const response = await api.patch('/api/v1/notifications/read-all')
  return response.data
}
