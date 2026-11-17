import { employeeField, approveReject } from '@/shared/modelHelpers'

export default [
  {
    path: '/leave-applications', group: 'Leave', title: 'Leave applications', perm: 'leave', endpoint: '/leave/applications',
    edit: false,
    columns: [
      { key: 'employee.fullName', label: 'Employee' },
      { key: 'leaveType.name', label: 'Type' },
      { key: 'fromDate', label: 'From' },
      { key: 'toDate', label: 'To' },
      { key: 'totalDays', label: 'Days' },
      { key: 'reason', label: 'Reason' },
      { key: 'status', label: 'Status', badge: true },
    ],
    fields: [
      employeeField,
      { key: 'leaveType', label: 'Leave type', type: 'ref', endpoint: '/leave/types', required: true },
      { key: 'fromDate', label: 'From', type: 'date', required: true },
      { key: 'toDate', label: 'To', type: 'date', required: true },
      { key: 'halfDay', label: 'Half day', type: 'checkbox' },
      { key: 'reason', label: 'Reason', type: 'textarea' },
    ],
    actions: [
      ...approveReject('/leave/applications', 'leave:approve'),
      {
        label: 'Cancel', path: (r) => `/leave/applications/${r.id}/cancel`, perm: 'leave:write', danger: true,
        show: (r) => r.status === 'APPROVED', confirm: 'Cancel this approved leave?',
      },
    ],
  },
  {
    path: '/leave-allocations', group: 'Leave', title: 'Leave allocations', perm: 'leave', endpoint: '/leave/allocations',
    hint: 'An allocation gives an employee a number of days of one leave type for a period, usually a year.',
    columns: [
      { key: 'employee.fullName', label: 'Employee' },
      { key: 'leaveType.name', label: 'Type' },
      { key: 'fromDate', label: 'From' },
      { key: 'toDate', label: 'To' },
      { key: 'days', label: 'Days' },
    ],
    fields: [
      employeeField,
      { key: 'leaveType', label: 'Leave type', type: 'ref', endpoint: '/leave/types', required: true },
      { key: 'fromDate', label: 'From', type: 'date', required: true },
      { key: 'toDate', label: 'To', type: 'date', required: true },
      { key: 'days', label: 'Days', type: 'number', required: true },
    ],
  },
  {
    path: '/leave-types', group: 'Leave', title: 'Leave types', perm: 'leave', endpoint: '/leave/types',
    columns: [
      { key: 'name', label: 'Name' },
      { key: 'maxDaysPerYear', label: 'Max days / year' },
      { key: 'carryForward', label: 'Carry forward' },
      { key: 'unpaid', label: 'Unpaid' },
      { key: 'allowNegative', label: 'Allow negative' },
    ],
    fields: [
      { key: 'name', label: 'Name', type: 'text', required: true },
      { key: 'maxDaysPerYear', label: 'Max days per year', type: 'number' },
      { key: 'carryForward', label: 'Carry forward to next year', type: 'checkbox' },
      { key: 'unpaid', label: 'Unpaid (leave without pay)', type: 'checkbox' },
      { key: 'allowNegative', label: 'Allow applying beyond the balance', type: 'checkbox' },
    ],
  },
  {
    path: '/holiday-lists', group: 'Leave', title: 'Holiday lists', perm: 'leave', endpoint: '/leave/holiday-lists',
    hint: 'Holidays and weekly offs are skipped when counting leave days and salary working days.',
    columns: [
      { key: 'name', label: 'Name' },
      { key: 'fromDate', label: 'From' },
      { key: 'toDate', label: 'To' },
      { key: 'weeklyOffs', label: 'Weekly offs' },
      { key: 'defaultList', label: 'Default' },
    ],
    fields: [
      { key: 'name', label: 'Name', type: 'text', required: true },
      { key: 'fromDate', label: 'From', type: 'date', required: true },
      { key: 'toDate', label: 'To', type: 'date', required: true },
      { key: 'weeklyOffs', label: 'Weekly offs (e.g. SATURDAY,SUNDAY)', type: 'text' },
      { key: 'defaultList', label: 'Default list for employees without one', type: 'checkbox' },
      {
        key: 'holidays', label: 'Holidays', type: 'lines',
        fields: [
          { key: 'holidayDate', label: 'Date', type: 'date', required: true },
          { key: 'description', label: 'Description', type: 'text' },
        ],
      },
    ],
  },
]
