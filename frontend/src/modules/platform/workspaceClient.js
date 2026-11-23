import axios from 'axios'
import { installFinanceAccess } from '../../shared/financeAccess.js'

export function createWorkspaceClient({ token, id, onInvalid, adapter }) {
  const lifetime = new AbortController()
  const api = axios.create({ baseURL: '/api', allowAbsoluteUrls: false, adapter })

  api.interceptors.request.use((request) => {
    if (lifetime.signal.aborted) throw new axios.CanceledError('Admin View was closed')
    request.headers.set('Authorization', `Bearer ${token}`)
    request.headers.set('X-HRMS-Workspace', id)
    request.signal = request.signal ? AbortSignal.any([request.signal, lifetime.signal]) : lifetime.signal
    return request
  })

  api.interceptors.response.use(
    (response) => {
      if (lifetime.signal.aborted) throw new axios.CanceledError('Admin View was closed')
      return response
    },
    (error) => {
      if (lifetime.signal.aborted) throw new axios.CanceledError('Admin View was closed')
      if ([401, 410].includes(error.response?.status)) onInvalid(error.response.status)
      throw error
    },
  )

  installFinanceAccess(api, () => lifetime.signal.aborted ? null : id)
  return { api, retire: () => lifetime.abort() }
}
