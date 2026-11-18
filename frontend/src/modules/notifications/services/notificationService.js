import api from '@/shared/api'

export const list = (page, size = 20) =>
  api.get('/notifications', page != null ? { params: { page, size } } : undefined).then((r) => r.data)
export const markAllRead = () => api.post('/notifications/read-all')
export const markRead = (id) => api.post(`/notifications/${id}/read`)
