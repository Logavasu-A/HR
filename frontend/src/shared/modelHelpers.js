// Small building blocks shared by every module's model definitions.
import { pretty } from '@/shared/utils'

export const opts = (...values) => values.map((v) => ({ value: v, label: pretty(v) }))

export const employeeField = { key: 'employee', label: 'Employee', type: 'ref', endpoint: '/core/employees', required: true }

export const approveReject = (base, perm) => [
  { label: 'Approve', path: (r) => `${base}/${r.id}/approve`, show: (r) => r.status === 'PENDING', perm },
  {
    label: 'Reject',
    path: (r) => `${base}/${r.id}/reject`,
    show: (r) => r.status === 'PENDING',
    perm,
    danger: true,
    ask: { label: 'Reason for rejecting (optional)', key: 'remarks' },
  },
]

export const nameAndDescription = [
  { key: 'name', label: 'Name', type: 'text', required: true },
  { key: 'description', label: 'Description', type: 'text' },
]

export const expenseLines = (typeEndpoint) => ({
  key: 'lines',
  label: 'Expenses',
  type: 'lines',
  fields: [
    { key: 'expenseType', label: 'Type', type: 'ref', endpoint: typeEndpoint },
    { key: 'expenseDate', label: 'Date', type: 'date' },
    { key: 'description', label: 'Description', type: 'text' },
    { key: 'amount', label: 'Amount', type: 'number', required: true },
  ],
})
