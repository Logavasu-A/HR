import { reactive } from 'vue'

function readUser() {
  try {
    return JSON.parse(localStorage.getItem('hrms_user') || 'null')
  } catch {
    return null
  }
}

export const auth = reactive({
  token: localStorage.getItem('hrms_token') || '',
  user: readUser(),
})

export function setSession(token, user) {
  auth.token = token
  auth.user = user
  localStorage.setItem('hrms_token', token)
  localStorage.setItem('hrms_user', JSON.stringify(user))
}

export function setUser(user) {
  auth.user = user
  localStorage.setItem('hrms_user', JSON.stringify(user))
}

export function clearSession() {
  auth.token = ''
  auth.user = null
  localStorage.removeItem('hrms_token')
  localStorage.removeItem('hrms_user')
}

/** can('leave:approve') — true when the user's role grants it ("*" and "leave:*" also count). */
export function can(permission) {
  if (!permission) return true
  const perms = auth.user?.permissions || []
  if (perms.includes('*') || perms.includes(permission)) return true
  const module = permission.split(':')[0]
  return perms.includes(`${module}:*`)
}

export function isEmployee() {
  return auth.user?.employeeId != null
}
