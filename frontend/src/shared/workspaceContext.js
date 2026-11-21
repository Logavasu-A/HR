import { inject } from 'vue'
import api from './api.js'
import { auth, can } from './auth.js'

export const workspaceContext = Symbol('workspace')

const companyContext = {
  api,
  can,
  explicit: (permission) => auth.user?.permissions?.includes(permission) === true,
  path: (path) => path,
  owner: null,
}

export function useWorkspace() {
  return inject(workspaceContext, companyContext)
}
