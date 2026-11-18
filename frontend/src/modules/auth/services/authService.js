import api from '@/shared/api'
import { clearSession } from '@/shared/auth'

export const login = (companyCode, username, password) =>
  api.post('/auth/login', { companyCode, username, password }).then((r) => r.data)

export async function logout() {
  try {
    await api.post('/auth/logout', null, { timeout: 5000 })
  } finally {
    clearSession()
  }
}
export const changePassword = (currentPassword, newPassword) =>
  api.post('/auth/change-password', { currentPassword, newPassword })
