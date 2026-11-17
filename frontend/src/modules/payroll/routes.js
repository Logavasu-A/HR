export default [
  { path: '/payroll-runs', component: () => import('./pages/PayrollRuns.vue'), meta: { perm: 'payroll:read' } },
]