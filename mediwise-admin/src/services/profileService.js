import api from './api'

export async function getProfile() {
  const response = await api.get('/api/v1/profile')
  return response.data
}

export async function updateProfile(payload) {
  const response = await api.put('/api/v1/profile', payload)
  return response.data
}

export async function uploadProfileImage(file) {
  const formData = new FormData()
  formData.append('file', file)
  const response = await api.post('/api/v1/profile/image', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return response.data
}
