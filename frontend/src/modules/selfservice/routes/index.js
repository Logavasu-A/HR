export default [
  { path: '/my', component: () => import('../pages/MyHome.vue'), meta: { self: true } },
  { path: '/my/payslips', component: () => import('../pages/MyPayslips.vue'), meta: { self: true } },
]
