export default [
  { path: '/dashboard', component: () => import('../pages/Dashboard.vue'), meta: { perm: 'dashboard:read' } },
]
