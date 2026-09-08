import api from './api'

export async function getAppointments(params = {}) {
  const response = await api.get('/api/v1/admin/appointments', { params })
  return response.data
}

export async function getAppointmentById(id) {
  const response = await api.get(`/api/v1/appointments/${id}`)
  return response.data
}

export async function bookAppointment({ slotId, doctorId, type = 'ONLINE', chiefComplaint }) {
  const response = await api.post('/api/v1/appointments', { slotId, doctorId, type, chiefComplaint })
  return response.data
}

export async function cancelAppointment(id, reason) {
  const response = await api.patch(`/api/v1/appointments/${id}/cancel`, { reason })
  return response.data
}

export async function startAppointment(id) {
  const response = await api.patch(`/api/v1/appointments/${id}/start`)
  return response.data
}

export async function completeAppointment(id, { notes, diagnosis, prescription }) {
  const response = await api.patch(`/api/v1/appointments/${id}/complete`, { notes, diagnosis, prescription })
  return response.data
}

export async function getDoctorAppointments(params = {}) {
  const response = await api.get('/api/v1/appointments/doctor', { params })
  return response.data
}
