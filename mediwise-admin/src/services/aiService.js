import api from './api'

// Patient/mobile concern — kept here only so the service surface is complete.
// Not wired to any admin UI button (see final report).
export async function logSymptoms(payload) {
  const response = await api.post('/api/v1/ai/symptom-log', payload)
  return response.data
}

/**
 * All AI reports for a patient (doctor/admin only). Resolves to an array of
 * AiReportResponse: { id, patientId, appointmentId, modelName, modelVersion,
 * urgencyScore, suggestedSpecialty, confidence, recommendation, riskFactors, createdAt }
 */
export async function getPatientAiReports(patientId) {
  const response = await api.get(`/api/v1/ai/reports/${patientId}`)
  return response.data
}

export async function getLatestAiReport(patientId) {
  const response = await api.get(`/api/v1/ai/reports/${patientId}/latest`)
  return response.data
}
