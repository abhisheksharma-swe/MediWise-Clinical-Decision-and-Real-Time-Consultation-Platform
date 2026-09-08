import api from './api'

export async function getDoctors(params = {}) {
  const response = await api.get('/api/v1/admin/doctors', { params })
  return response.data
}

export async function getPendingDoctors(params = {}) {
  const response = await api.get('/api/v1/admin/doctors', {
    params: { verified: false, ...params },
  })
  return response.data
}

export async function getDoctorById(id) {
  const response = await api.get(`/api/v1/doctors/${id}`)
  return response.data
}

export async function verifyDoctor(id) {
  const response = await api.patch(`/api/v1/admin/doctors/${id}/verify`)
  return response.data
}

export async function rejectDoctor(id, reason) {
  const response = await api.patch(`/api/v1/admin/doctors/${id}/reject`, { reason })
  return response.data
}

export async function updateUserStatus(userId, active) {
  const response = await api.patch(`/api/v1/admin/users/${userId}/status`, { active })
  return response.data
}

/**
 * Public doctor-discovery listing (distinct from the admin management
 * listing at /api/v1/admin/doctors used by getDoctors above).
 */
export async function getDoctorDirectory(params = {}) {
  const response = await api.get('/api/v1/doctors', { params })
  return response.data
}

export async function toggleDoctorFavorite(id) {
  const response = await api.post(`/api/v1/doctors/${id}/favorite`)
  return response.data
}

export async function getFavoriteDoctors(params = {}) {
  const response = await api.get('/api/v1/doctors/favorites', { params })
  return response.data
}
