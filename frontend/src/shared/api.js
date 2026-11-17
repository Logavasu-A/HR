import axios from 'axios'
import { auth, clearSession } from '@/shared/auth'

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
    if (error.response?.status === 401 && !error.config?.url?.includes('/auth/login')) {
      clearSession()
      if (location.pathname !== '/login') {
        location.assign('/login')
      }
    }
    return Promise.reject(error)
  },
)

export default api
