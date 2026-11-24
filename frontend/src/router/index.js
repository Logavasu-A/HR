import { createRouter, createWebHistory } from 'vue-router'
import api from '@/shared/api'
import { auth, can, companyHomePath, isEmployee, setUser } from '@/shared/auth'
import { resources, selfResources, featureRoutes } from '@/modules'
import CrudPage from '@/shared/components/CrudPage.vue'

const companyRoutes = resources.map((r) => ({
  path: r.path, component: CrudPage, props: { config: r }, meta: { perm: `${r.perm}:read` },
}))
const ownerPages = new Set(['/dashboard', '/attendance-summary', '/payroll-runs', '/employees/:id/bank-details'])
const ownerChildren = [...companyRoutes, ...featureRoutes.filter((route) => ownerPages.has(route.path))]
  .map(({ path, component, props }) => ({ path: path.slice(1), component, props }))

const routes = [
  { path: '/', redirect: () => companyHomePath() },
  ...featureRoutes,
  { path: '/platform/login', component: () => import('@/modules/platform/OwnerLogin.vue'), meta: { bare: true, open: true } },
  { path: '/platform', redirect: '/platform/control-tower' },
  { path: '/platform/control-tower', component: () => import('@/modules/platform/ControlTower.vue'), meta: { bare: true, open: true } },
  { path: '/platform/companies', component: () => import('@/modules/platform/Companies.vue'), meta: { bare: true, open: true } },
  { path: '/platform/access', component: () => import('@/modules/platform/AccessCenter.vue'), meta: { bare: true, open: true } },
  { path: '/platform/audit', component: () => import('@/modules/platform/AuditCenter.vue'), meta: { bare: true, open: true } },
  {
    path: '/platform/workspace',
    component: () => import('@/modules/platform/OwnerWorkspace.vue'),
    meta: { bare: true, open: true },
    children: [
      { path: '', redirect: (to) => ({ path: '/platform/workspace/dashboard', query: to.query }) },
      { path: 'company-details', component: () => import('@/modules/platform/WorkspaceCompanyDetails.vue') },
      ...ownerChildren,
    ],
  },
  { path: '/accept-admin-invite', component: () => import('@/modules/platform/AcceptInvite.vue'), meta: { bare: true, open: true } },
  ...companyRoutes,
  ...selfResources.map((r) => ({ path: r.path, component: CrudPage, props: { config: r }, meta: { self: true } })),
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({ history: createWebHistory(), routes })

let refreshedToken = ''

router.beforeEach(async (to) => {
  if (to.meta.open) return true
  if (!auth.token) return '/login'

  // Once per page load, re-read the user so permission changes made by an admin take effect.
  const token = auth.token
  if (refreshedToken !== token) {
    try {
      const { data } = await api.get('/auth/me')
      if (auth.token !== token) return '/login'
      setUser(data)
      refreshedToken = token
    } catch {
      return '/login'
    }
  }

  if (to.meta.perm && !can(to.meta.perm)) return companyHomePath()
  if (to.meta.self && !isEmployee()) return companyHomePath()
  return true
})

export default router
