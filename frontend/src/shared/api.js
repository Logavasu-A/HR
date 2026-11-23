import axios from 'axios'
import { auth, clearSession } from '@/shared/auth'
import { installFinanceAccess } from '@/shared/financeAccess'

const api = axios.create({ baseURL: '/api' })

api.interceptors.request.use((config) => {
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    // An expired or invalid session sends the user back to the login screen.
    const requestToken = error.config?.headers?.Authorization
    if (error.response?.status === 401 && error.config?.url !== '/auth/login'
        && auth.token && requestToken === `Bearer ${auth.token}`) {
      clearSession()
      if (location.pathname !== '/login' && location.pathname !== '/platform/login') {
        location.assign(location.pathname.startsWith('/platform') ? '/platform/login' : '/login')
      }
    }
    return Promise.reject(error)
  },
)

installFinanceAccess(api, () => auth.token)
export default api
