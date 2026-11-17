import { employeeField, approveReject, nameAndDescription, expenseLines } from '@/shared/modelHelpers'

export default [
  {
    path: '/expense-claims', group: 'Expenses', title: 'Expense claims', perm: 'expense', endpoint: '/expense/claims',
    columns: [
      { key: 'employee.fullName', label: 'Employee' },
      { key: 'claimDate', label: 'Date' },
      { key: 'totalAmount', label: 'Total', money: true },
      { key: 'remarks', label: 'Remarks' },
      { key: 'status', label: 'Status', badge: true },
    ],
    fields: [
      employeeField,
      { key: 'claimDate', label: 'Claim date', type: 'date' },
      { key: 'remarks', label: 'Remarks', type: 'text' },
      expenseLines('/expense/types'),
    ],
    actions: [
      ...approveReject('/expense/claims', 'expense:approve'),
      { label: 'Mark paid', path: (r) => `/expense/claims/${r.id}/pay`, show: (r) => r.status === 'APPROVED', perm: 'expense:approve' },
    ],
  },
  {
    path: '/employee-advances', group: 'Expenses', title: 'Employee advances', perm: 'expense', endpoint: '/expense/advances',
    columns: [
      { key: 'employee.fullName', label: 'Employee' },
      { key: 'advanceDate', label: 'Date' },
      { key: 'amount', label: 'Amount', money: true },
      { key: 'purpose', label: 'Purpose' },
      { key: 'status', label: 'Status', badge: true },
    ],
    fields: [
      employeeField,
      { key: 'advanceDate', label: 'Date', type: 'date', required: true },
      { key: 'amount', label: 'Amount', type: 'number', required: true },
      { key: 'purpose', label: 'Purpose', type: 'text' },
    ],
    actions: [
      { label: 'Approve', path: (r) => `/expense/advances/${r.id}/approve`, show: (r) => r.status === 'PENDING', perm: 'expense:approve' },
      { label: 'Reject', path: (r) => `/expense/advances/${r.id}/reject`, show: (r) => r.status === 'PENDING', perm: 'expense:approve', danger: true },
      { label: 'Mark paid', path: (r) => `/expense/advances/${r.id}/pay`, show: (r) => r.status === 'APPROVED', perm: 'expense:approve' },
    ],
  },
  {
    path: '/expense-types', group: 'Expenses', title: 'Expense types', perm: 'expense', endpoint: '/expense/types',
    columns: [{ key: 'name', label: 'Name' }, { key: 'description', label: 'Description' }],
    fields: nameAndDescription,
  },
]
