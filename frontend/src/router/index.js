import { createRouter, createWebHistory } from 'vue-router'
import api from '@/shared/api'
import { auth, can, isEmployee, setUser } from '@/shared/auth'
import { resources, selfResources, featureRoutes } from '@/modules'
import CrudPage from '@/shared/components/CrudPage.vue'

function homePath() {
  if (can('dashboard:read')) return '/dashboard'
  if (isEmployee()) return '/my'
  return '/change-password'
}

const routes = [
  { path: '/', redirect: () => homePath() },
  ...featureRoutes,
  ...resources.map((r) => ({ path: r.path, component: CrudPage, props: { config: r }, meta: { perm: `${r.perm}:read` } })),
  ...selfResources.map((r) => ({ path: r.path, component: CrudPage, props: { config: r }, meta: { self: true } })),
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({ history: createWebHistory(), routes })

let refreshed = false

router.beforeEach(async (to) => {
  if (to.meta.open) return true
  if (!auth.token) return '/login'

  // Once per page load, re-read the user so permission changes made by an admin take effect.
  if (!refreshed) {
    refreshed = true
    try {
      const { data } = await api.get('/auth/me')
      setUser(data)
    } catch {
      return '/login'
    }
  }

  if (to.meta.perm && !can(to.meta.perm)) return homePath()
  if (to.meta.self && !isEmployee()) return homePath()
  return true
})

export default router
