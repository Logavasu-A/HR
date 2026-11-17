export default [
  { path: '/attendance-summary', component: () => import('../pages/AttendanceSummary.vue'), meta: { perm: 'attendance:read' } },
]
