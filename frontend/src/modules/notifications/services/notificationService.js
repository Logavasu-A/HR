import api from '@/shared/api'

export const list = () => api.get('/notifications').then((r) => r.data)
export const markAllRead = () => api.post('/notifications/read-all')
export const markRead = (id) => api.post(`/notifications/${id}/read`)
