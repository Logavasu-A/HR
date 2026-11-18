import api from '@/shared/api'

export const listRuns = (page, size = 20) =>
  api.get('/payroll/runs', page != null ? { params: { page, size } } : undefined).then((r) => r.data)
export const listSlips = (runId, page, size = 20) =>
  api.get(`/payroll/runs/${runId}/slips`, page != null ? { params: { page, size } } : undefined).then((r) => r.data)
export const createRun = (year, month) => api.post('/payroll/runs', { year, month }).then((r) => r.data)
export const submitRun = (runId) => api.post(`/payroll/runs/${runId}/submit`)
export const deleteRun = (runId) => api.delete(`/payroll/runs/${runId}`)
