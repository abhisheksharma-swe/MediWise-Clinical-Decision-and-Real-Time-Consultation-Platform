import api from './api'

export async function getChatMessages(roomId, params = {}) {
  const response = await api.get(`/api/v1/chat/${roomId}/messages`, { params })
  return response.data
}
