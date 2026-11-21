import { expenseLines } from '@/shared/modelHelpers'

export default [
  {
    path: '/my/leaves', group: 'My space', title: 'My leaves', endpoint: '/me/leaves',
    create: true, edit: false, remove: false,
    columns: [
      { key: 'leaveType.name', label: 'Type' },
      { key: 'fromDate', label: 'From' },
      { key: 'toDate', label: 'To' },
      { key: 'totalDays', label: 'Days', align: 'right' },
      { key: 'reason', label: 'Reason' },
      { key: 'status', label: 'Status', badge: true },
      { key: 'remarks', label: 'Remarks' },
    ],
    fields: [
      { key: 'leaveType', label: 'Leave type', type: 'ref', endpoint: '/me/leave-types', required: true },
      { key: 'fromDate', label: 'From', type: 'date', required: true },
      { key: 'toDate', label: 'To', type: 'date', required: true },
      { key: 'halfDay', label: 'Half day', type: 'checkbox' },
      { key: 'reason', label: 'Reason', type: 'textarea' },
    ],
    actions: [
      {
        label: 'Cancel', path: (r) => `/me/leaves/${r.id}/cancel`, danger: true,
        show: (r) => r.status === 'PENDING' || r.status === 'APPROVED', confirm: 'Cancel this leave?',
      },
    ],
  },
  {
    path: '/my/attendance', group: 'My space', title: 'My attendance', endpoint: '/me/attendance',
    create: false, edit: false, remove: false,
    columns: [
      { key: 'attendanceDate', label: 'Date' },
      { key: 'status', label: 'Status', badge: true },
      { key: 'inTime', label: 'In' },
      { key: 'outTime', label: 'Out' },
      { key: 'workingHours', label: 'Hours', align: 'right' },
      { key: 'remarks', label: 'Remarks' },
    ],
    fields: [],
  },
  {
    path: '/my/expenses', group: 'My space', title: 'My expenses', endpoint: '/me/expenses',
    create: true, edit: false, remove: false,
    columns: [
      { key: 'claimDate', label: 'Date' },
      { key: 'totalAmount', label: 'Total', money: true },
      { key: 'remarks', label: 'Remarks' },
      { key: 'status', label: 'Status', badge: true },
    ],
    fields: [
      { key: 'claimDate', label: 'Claim date', type: 'date' },
      { key: 'remarks', label: 'Remarks', type: 'text' },
      expenseLines('/me/expense-types'),
    ],
  },
  {
    path: '/my/goals', group: 'My space', title: 'My goals', endpoint: '/me/goals',
    create: false, edit: false, remove: false,
    columns: [
      { key: 'title', label: 'Goal' },
      { key: 'cycle.name', label: 'Cycle' },
      { key: 'weightage', label: 'Weight %', align: 'right' },
      { key: 'progress', label: 'Progress %', align: 'right' },
      { key: 'status', label: 'Status', badge: true },
    ],
    fields: [],
    actions: [
      {
        label: 'Update progress', path: (r) => `/me/goals/${r.id}/progress`,
        show: (r) => r.status !== 'DROPPED',
        ask: { label: 'Progress so far, 0 to 100', key: 'progress', number: true, required: true },
      },
    ],
  },
  {
    path: '/my/appraisals', group: 'My space', title: 'My appraisals', endpoint: '/me/appraisals',
    create: false, edit: false, remove: false,
    columns: [
      { key: 'cycle.name', label: 'Cycle' },
      { key: 'selfRating', label: 'Self', align: 'right' },
      { key: 'managerRating', label: 'Manager', align: 'right' },
      { key: 'finalScore', label: 'Final', align: 'right' },
      { key: 'feedback', label: 'Feedback' },
      { key: 'status', label: 'Status', badge: true },
    ],
    fields: [],
    actions: [
      {
        label: 'Rate myself', path: (r) => `/me/appraisals/${r.id}/self-rating`,
        show: (r) => r.status !== 'FINALIZED',
        ask: { label: 'Your rating, 1 to 5', key: 'selfRating', number: true, required: true },
      },
    ],
  },
]
