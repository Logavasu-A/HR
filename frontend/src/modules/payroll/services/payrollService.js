import api from '@/shared/api'

export const listRuns = () => api.get('/payroll/runs').then((r) => r.data)
export const listSlips = (runId) => api.get(`/payroll/runs/${runId}/slips`).then((r) => r.data)
export const createRun = (year, month) => api.post('/payroll/runs', { year, month }).then((r) => r.data)
export const submitRun = (runId) => api.post(`/payroll/runs/${runId}/submit`)
export const deleteRun = (runId) => api.delete(`/payroll/runs/${runId}`)
