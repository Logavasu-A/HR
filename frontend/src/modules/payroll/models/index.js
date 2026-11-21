import { opts, employeeField } from '@/shared/modelHelpers'

export default [
  {
    path: '/salary-assignments', group: 'Payroll', title: 'Salary assignments', perm: 'payroll', endpoint: '/payroll/assignments',
    columns: [
      { key: 'employee.fullName', label: 'Employee' },
      { key: 'structure.name', label: 'Structure' },
      { key: 'baseAmount', label: 'Monthly base', money: true },
      { key: 'fromDate', label: 'From' },
    ],
    fields: [
      employeeField,
      { key: 'structure', label: 'Salary structure', type: 'ref', endpoint: '/payroll/structures', required: true },
      { key: 'baseAmount', label: 'Monthly base salary', type: 'number', required: true },
      { key: 'fromDate', label: 'From date', type: 'date', required: true },
    ],
  },
  {
    path: '/salary-structures', group: 'Payroll', title: 'Salary structures', perm: 'payroll', endpoint: '/payroll/structures',
    columns: [
      { key: 'name', label: 'Name' },
      { key: 'active', label: 'Active' },
      { key: 'lines.length', label: 'Components', align: 'right' },
    ],
    fields: [
      { key: 'name', label: 'Name', type: 'text', required: true },
      { key: 'active', label: 'Active', type: 'checkbox', default: true },
      {
        key: 'lines', label: 'Components', type: 'lines',
        fields: [
          { key: 'component', label: 'Component', type: 'ref', endpoint: '/payroll/components', required: true },
          {
            key: 'amountType', label: 'Calculated as', type: 'select', required: true, default: 'PERCENT_OF_BASE',
            options: opts('FIXED', 'PERCENT_OF_BASE', 'PERCENT_OF_GROSS'),
            help: '"Percent of gross" is meant for deductions.',
          },
          { key: 'amountValue', label: 'Amount or %', type: 'number', required: true },
        ],
      },
    ],
  },
  {
    path: '/salary-components', group: 'Payroll', title: 'Salary components', perm: 'payroll', endpoint: '/payroll/components',
    columns: [
      { key: 'name', label: 'Name' },
      { key: 'abbr', label: 'Short' },
      { key: 'type', label: 'Type', badge: true },
      { key: 'dependsOnPaymentDays', label: 'Reduced for unpaid days' },
    ],
    fields: [
      { key: 'name', label: 'Name', type: 'text', required: true },
      { key: 'abbr', label: 'Short name', type: 'text' },
      { key: 'type', label: 'Type', type: 'select', required: true, default: 'EARNING', options: opts('EARNING', 'DEDUCTION') },
      { key: 'dependsOnPaymentDays', label: 'Reduce in proportion to unpaid days', type: 'checkbox', default: true },
    ],
  },
]
