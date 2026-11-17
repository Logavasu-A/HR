import api from '@/shared/api'

export const login = (username, password) => api.post('/auth/login', { username, password }).then((r) => r.data)
export const changePassword = (currentPassword, newPassword) =>
  api.post('/auth/change-password', { currentPassword, newPassword })
