import { computed, shallowRef } from 'vue'
import api from '@/shared/api'
import { auth } from '@/shared/auth'

const unread = shallowRef(null)
let requestId = 0

export const unreadCount = computed(() => unread.value?.token === auth.token ? unread.value.count : 0)

export async function list(page, size = 20) {
  const token = auth.token
  const id = ++requestId
  const { data } = await api.get('/notifications', page != null ? { params: { page, size } } : undefined)
  if (token === auth.token && id > (unread.value?.id ?? 0)) {
    unread.value = { token, id, count: Math.max(0, Number(data.unread) || 0) }
  }
  return data
}

export function refreshUnread() {
  if (auth.token) return list(0, 1).catch(() => {})
}

export const markAllRead = () => api.post('/notifications/read-all')
export async function markRead(id) {
  const response = await api.post(`/notifications/${id}/read`)
  refreshUnread()
  return response
}
