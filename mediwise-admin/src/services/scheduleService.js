import api from './api'

export async function getDoctorSlots(doctorId, date) {
  const response = await api.get(`/api/v1/doctors/${doctorId}/slots`, { params: { date } })
  return response.data
}

export async function lockSlot(slotId) {
  const response = await api.post(`/api/v1/slots/${slotId}/lock`)
  return response.data
}

export async function releaseSlot(slotId) {
  const response = await api.delete(`/api/v1/slots/${slotId}/lock`)
  return response.data
}
