import api from './api'

export async function getAdminStats() {
  const response = await api.get('/api/v1/admin/stats')
  return response.data
}

export async function getDashboardAnalytics() {
  const response = await api.get('/api/v1/analytics/dashboard')
  return response.data
}

/**
 * Appointment analytics for a date range.
 * from/to are required 'YYYY-MM-DD' LocalDate strings; doctorId is optional.
 * Resolves (unwrapped from ApiResponse) to an AppointmentAnalyticsResponse:
 * { from, to, totalCount, completedCount, cancelledCount, noShowCount,
 *   totalRevenue, averageConsultationFee, dailyCounts, bySpecialty, byType,
 *   topDoctors: [{ doctorId, doctorName, appointmentCount, revenue, averageRating }] }
 */
export async function getAppointmentAnalytics({ from, to, doctorId } = {}) {
  const params = { from, to }
  if (doctorId) params.doctorId = doctorId
  const response = await api.get('/api/v1/analytics/appointments', { params })
  return response.data
}

/**
 * Admin-only revenue breakdown for a date range. Same response shape as
 * getAppointmentAnalytics but never scoped to a single doctor.
 */
export async function getRevenueAnalytics({ from, to } = {}) {
  const response = await api.get('/api/v1/analytics/revenue', { params: { from, to } })
  return response.data
}
