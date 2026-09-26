import api from './api'

export async function getAuditLogs(params = {}) {
  const response = await api.get('/api/v1/admin/audit-logs', { params })
  return { data: response.data?.data?.content || [] }
}
