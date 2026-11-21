import api from '@/shared/api'

export const getMonthlySummary = (year, month, client = api) =>
  client.get('/attendance/summary', { params: { year, month } }).then((r) => r.data)
