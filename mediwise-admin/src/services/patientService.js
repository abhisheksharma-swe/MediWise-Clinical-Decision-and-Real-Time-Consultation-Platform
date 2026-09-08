import api from './api'

export async function getPatients(params = {}) {
  const response = await api.get('/api/v1/admin/users', {
    params: { role: 'PATIENT', ...params },
  })
  return response.data
}

export async function getPatientById(id) {
  const response = await api.get(`/api/v1/admin/users/${id}`)
  return response.data
}

export async function togglePatientStatus(id, active) {
  const response = await api.patch(`/api/v1/admin/users/${id}/status`, { active })
  return response.data
}

/**
 * Generic admin-users role change: role is one of 'PATIENT' | 'DOCTOR' | 'ADMIN'.
 */
export async function updateUserRole(userId, role) {
  const response = await api.patch(`/api/v1/admin/users/${userId}/role`, { role })
  return response.data
}
