import api from '@/shared/api'

export const getMonthlySummary = (year, month) =>
  api.get('/attendance/summary', { params: { year, month } }).then((r) => r.data)
