import api from '@/shared/api'

export const getProfile = () => api.get('/me/profile')
export const getTodayAttendance = () => api.get('/me/attendance/today')
export const getLeaveBalance = () => api.get('/me/leave-balance')
export const punch = (kind) => api.post(`/me/${kind}`).then((r) => r.data)
export const listPayslips = (page, size = 20) =>
  api.get('/me/payslips', page != null ? { params: { page, size } } : undefined).then((r) => r.data)
