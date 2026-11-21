import api from '@/shared/api'

export const listRuns = (page, size = 20, client = api) =>
  client.get('/payroll/runs', page != null ? { params: { page, size } } : undefined).then((r) => r.data)
export const listSlips = (runId, page, size = 20, client = api) =>
  client.get(`/payroll/runs/${runId}/slips`, page != null ? { params: { page, size } } : undefined).then((r) => r.data)
export const createRun = (year, month, client = api) => client.post('/payroll/runs', { year, month }).then((r) => r.data)
export const submitRun = (runId, client = api) => client.post(`/payroll/runs/${runId}/submit`)
export const deleteRun = (runId, client = api) => client.delete(`/payroll/runs/${runId}`)
