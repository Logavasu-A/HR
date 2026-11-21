import { opts, employeeField } from '@/shared/modelHelpers'

export default [
  {
    path: '/appraisals', group: 'Performance', title: 'Appraisals', perm: 'performance', endpoint: '/performance/appraisals',
    columns: [
      { key: 'employee.fullName', label: 'Employee' },
      { key: 'cycle.name', label: 'Cycle' },
      { key: 'selfRating', label: 'Self', align: 'right' },
      { key: 'managerRating', label: 'Manager', align: 'right' },
      { key: 'finalScore', label: 'Final', align: 'right' },
      { key: 'status', label: 'Status', badge: true },
    ],
    fields: [
      employeeField,
      { key: 'cycle', label: 'Appraisal cycle', type: 'ref', endpoint: '/performance/cycles', required: true },
      { key: 'selfRating', label: 'Self rating (1-5)', type: 'number' },
      {
        key: 'managerRating', label: 'Manager rating (1-5)', type: 'number',
        help: 'Final score = 70% manager rating + 30% goal achievement in the cycle.',
      },
      { key: 'feedback', label: 'Feedback', type: 'textarea' },
    ],
    actions: [
      {
        label: 'Finalize', path: (r) => `/performance/appraisals/${r.id}/submit`, perm: 'performance:approve',
        show: (r) => r.status !== 'FINALIZED', confirm: 'Finalize this appraisal? It cannot be changed afterwards.',
      },
    ],
  },
  {
    path: '/goals', group: 'Performance', title: 'Goals', perm: 'performance', endpoint: '/performance/goals',
    columns: [
      { key: 'employee.fullName', label: 'Employee' },
      { key: 'title', label: 'Goal' },
      { key: 'cycle.name', label: 'Cycle' },
      { key: 'weightage', label: 'Weight %', align: 'right' },
      { key: 'progress', label: 'Progress %', align: 'right' },
      { key: 'status', label: 'Status', badge: true },
    ],
    fields: [
      employeeField,
      { key: 'title', label: 'Goal', type: 'text', required: true },
      { key: 'cycle', label: 'Appraisal cycle', type: 'ref', endpoint: '/performance/cycles' },
      { key: 'weightage', label: 'Weightage %', type: 'number' },
      { key: 'progress', label: 'Progress %', type: 'number', default: 0 },
      { key: 'status', label: 'Status', type: 'select', required: true, default: 'OPEN', options: opts('OPEN', 'IN_PROGRESS', 'COMPLETED', 'DROPPED') },
      { key: 'description', label: 'Description', type: 'textarea' },
    ],
  },
  {
    path: '/appraisal-cycles', group: 'Performance', title: 'Appraisal cycles', perm: 'performance', endpoint: '/performance/cycles',
    columns: [
      { key: 'name', label: 'Name' },
      { key: 'startDate', label: 'Start' },
      { key: 'endDate', label: 'End' },
      { key: 'status', label: 'Status', badge: true },
    ],
    fields: [
      { key: 'name', label: 'Name', type: 'text', required: true },
      { key: 'startDate', label: 'Start date', type: 'date' },
      { key: 'endDate', label: 'End date', type: 'date' },
      { key: 'status', label: 'Status', type: 'select', required: true, default: 'PLANNED', options: opts('PLANNED', 'IN_PROGRESS', 'COMPLETED') },
    ],
  },
]
