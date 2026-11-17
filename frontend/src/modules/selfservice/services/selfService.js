import api from '@/shared/api'

export const getProfile = () => api.get('/me/profile')
export const getTodayAttendance = () => api.get('/me/attendance/today')
export const getLeaveBalance = () => api.get('/me/leave-balance')
export const punch = (kind) => api.post(`/me/${kind}`).then((r) => r.data)
export const listPayslips = () => api.get('/me/payslips').then((r) => r.data)
