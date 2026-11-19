import resources from './models'

export const routes = [
  {
    path: '/employees/:id/bank-details',
    name: 'employee-bank-details',
    component: () => import('./pages/EmployeeBankDetails.vue'),
    meta: { perm: 'core:read' },
  },
]

export { resources }
