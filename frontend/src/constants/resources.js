// Every list-and-form screen is described here and rendered by components/CrudPage.vue.
//
// columns: { key (dotted path), label, money?, badge? }
// fields:  { key, label, type, required?, options?, endpoint?, fields? (for type 'lines'), default? }
//   types: text, textarea, number, date, time, datetime, checkbox, password,
//          select (static options), ref (record from another endpoint), lines (child rows),
//          multicheck (list of strings from an endpoint)
// actions: { label, path(row), show(row), perm, ask?: { label, key, number? }, danger?, confirm? }
// flags:   create / edit / remove default to the "<perm>:write" permission; set false to switch off,
//          or true for self-service screens that have no permission module.

import { pretty } from '@/utils'

const opts = (...values) => values.map((v) => ({ value: v, label: pretty(v) }))

const employeeField = { key: 'employee', label: 'Employee', type: 'ref', endpoint: '/core/employees', required: true }

const approveReject = (base, perm) => [
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

const nameAndDescription = [
  { key: 'name', label: 'Name', type: 'text', required: true },
  { key: 'description', label: 'Description', type: 'text' },
]

const expenseLines = (typeEndpoint) => ({
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

export const resources = [
  // ------------------------------------------------------------ Core HR
  {
    path: '/employees', group: 'Core HR', title: 'Employees', perm: 'core', endpoint: '/core/employees',
    columns: [
      { key: 'employeeCode', label: 'Code' },
      { key: 'fullName', label: 'Name' },
      { key: 'department.name', label: 'Department' },
      { key: 'designation.name', label: 'Designation' },
      { key: 'dateOfJoining', label: 'Joined' },
      { key: 'status', label: 'Status', badge: true },
    ],
    fields: [
      { key: 'employeeCode', label: 'Employee code (blank = automatic)', type: 'text' },
      { key: 'firstName', label: 'First name', type: 'text', required: true },
      { key: 'lastName', label: 'Last name', type: 'text' },
      { key: 'email', label: 'Email', type: 'text' },
      { key: 'phone', label: 'Phone', type: 'text' },
      { key: 'gender', label: 'Gender', type: 'select', options: opts('MALE', 'FEMALE', 'OTHER') },
      { key: 'dateOfBirth', label: 'Date of birth', type: 'date' },
      { key: 'dateOfJoining', label: 'Date of joining', type: 'date', required: true },
      { key: 'employmentType', label: 'Employment type', type: 'select', options: opts('FULL_TIME', 'PART_TIME', 'CONTRACT', 'INTERN') },
      { key: 'status', label: 'Status', type: 'select', options: opts('ACTIVE', 'INACTIVE', 'LEFT'), default: 'ACTIVE', required: true },
      { key: 'relievingDate', label: 'Relieving date (when left)', type: 'date' },
      { key: 'department', label: 'Department', type: 'ref', endpoint: '/core/departments' },
      { key: 'designation', label: 'Designation', type: 'ref', endpoint: '/core/designations' },
      { key: 'branch', label: 'Branch', type: 'ref', endpoint: '/core/branches' },
      { key: 'reportsTo', label: 'Reports to', type: 'ref', endpoint: '/core/employees' },
      { key: 'holidayList', label: 'Holiday list (blank = default)', type: 'ref', endpoint: '/leave/holiday-lists' },
      { key: 'address', label: 'Address', type: 'textarea' },
      { key: 'bankName', label: 'Bank name', type: 'text' },
      { key: 'bankAccountNo', label: 'Bank account number', type: 'text' },
    ],
  },
  {
    path: '/departments', group: 'Core HR', title: 'Departments', perm: 'core', endpoint: '/core/departments',
    columns: [{ key: 'name', label: 'Name' }, { key: 'description', label: 'Description' }],
    fields: nameAndDescription,
  },
  {
    path: '/designations', group: 'Core HR', title: 'Designations', perm: 'core', endpoint: '/core/designations',
    columns: [{ key: 'name', label: 'Name' }, { key: 'description', label: 'Description' }],
    fields: nameAndDescription,
  },
  {
    path: '/branches', group: 'Core HR', title: 'Branches', perm: 'core', endpoint: '/core/branches',
    columns: [{ key: 'name', label: 'Name' }, { key: 'description', label: 'Description' }],
    fields: nameAndDescription,
  },

  // ------------------------------------------------------------ Leave
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

  // ------------------------------------------------------------ Attendance
  {
    path: '/attendance', group: 'Attendance', title: 'Attendance records', perm: 'attendance', endpoint: '/attendance/records',
    hint: 'Shows the last two months. Saving a record for a date that already has one replaces it.',
    columns: [
      { key: 'attendanceDate', label: 'Date' },
      { key: 'employee.fullName', label: 'Employee' },
      { key: 'status', label: 'Status', badge: true },
      { key: 'inTime', label: 'In' },
      { key: 'outTime', label: 'Out' },
      { key: 'workingHours', label: 'Hours' },
      { key: 'remarks', label: 'Remarks' },
    ],
    fields: [
      employeeField,
      { key: 'attendanceDate', label: 'Date', type: 'date', required: true },
      {
        key: 'status', label: 'Status', type: 'select', required: true, default: 'PRESENT',
        options: opts('PRESENT', 'ABSENT', 'HALF_DAY', 'ON_LEAVE', 'WORK_FROM_HOME'),
      },
      { key: 'inTime', label: 'In time', type: 'time' },
      { key: 'outTime', label: 'Out time', type: 'time' },
      { key: 'shift', label: 'Shift', type: 'ref', endpoint: '/attendance/shifts' },
      { key: 'remarks', label: 'Remarks', type: 'text' },
    ],
  },
  {
    path: '/shifts', group: 'Attendance', title: 'Shift types', perm: 'attendance', endpoint: '/attendance/shifts',
    columns: [
      { key: 'name', label: 'Name' },
      { key: 'startTime', label: 'Start' },
      { key: 'endTime', label: 'End' },
      { key: 'graceMinutes', label: 'Grace (min)' },
    ],
    fields: [
      { key: 'name', label: 'Name', type: 'text', required: true },
      { key: 'startTime', label: 'Start time', type: 'time' },
      { key: 'endTime', label: 'End time', type: 'time' },
      { key: 'graceMinutes', label: 'Grace period (minutes)', type: 'number' },
    ],
  },

  // ------------------------------------------------------------ Payroll
  {
    path: '/salary-assignments', group: 'Payroll', title: 'Salary assignments', perm: 'payroll', endpoint: '/payroll/assignments',
    hint: 'Gives an employee a salary structure and a monthly base salary from a date onward. Payroll uses the latest one.',
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
    hint: 'Each line is a fixed amount or a percentage. "Percent of gross" is meant for deductions.',
    columns: [
      { key: 'name', label: 'Name' },
      { key: 'active', label: 'Active' },
      { key: 'lines.length', label: 'Components' },
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

  // ------------------------------------------------------------ Recruitment
  {
    path: '/job-openings', group: 'Recruitment', title: 'Job openings', perm: 'recruitment', endpoint: '/recruitment/openings',
    columns: [
      { key: 'title', label: 'Title' },
      { key: 'department.name', label: 'Department' },
      { key: 'vacancies', label: 'Vacancies' },
      { key: 'postedOn', label: 'Posted' },
      { key: 'closesOn', label: 'Closes' },
      { key: 'status', label: 'Status', badge: true },
    ],
    fields: [
      { key: 'title', label: 'Title', type: 'text', required: true },
      { key: 'department', label: 'Department', type: 'ref', endpoint: '/core/departments' },
      { key: 'designation', label: 'Designation', type: 'ref', endpoint: '/core/designations' },
      { key: 'vacancies', label: 'Vacancies', type: 'number', default: 1 },
      { key: 'status', label: 'Status', type: 'select', required: true, default: 'OPEN', options: opts('OPEN', 'ON_HOLD', 'CLOSED') },
      { key: 'postedOn', label: 'Posted on', type: 'date' },
      { key: 'closesOn', label: 'Closes on', type: 'date' },
      { key: 'description', label: 'Description', type: 'textarea' },
    ],
  },
  {
    path: '/job-applicants', group: 'Recruitment', title: 'Job applicants', perm: 'recruitment', endpoint: '/recruitment/applicants',
    columns: [
      { key: 'name', label: 'Name' },
      { key: 'jobOpening.title', label: 'Opening' },
      { key: 'email', label: 'Email' },
      { key: 'appliedOn', label: 'Applied' },
      { key: 'rating', label: 'Rating' },
      { key: 'status', label: 'Stage', badge: true },
    ],
    fields: [
      { key: 'name', label: 'Name', type: 'text', required: true },
      { key: 'jobOpening', label: 'Job opening', type: 'ref', endpoint: '/recruitment/openings' },
      { key: 'email', label: 'Email', type: 'text' },
      { key: 'phone', label: 'Phone', type: 'text' },
      { key: 'source', label: 'Source (referral, job site, ...)', type: 'text' },
      { key: 'resumeUrl', label: 'Resume link', type: 'text' },
      {
        key: 'status', label: 'Stage', type: 'select', required: true, default: 'APPLIED',
        options: opts('APPLIED', 'SHORTLISTED', 'INTERVIEW', 'OFFERED', 'HIRED', 'REJECTED'),
      },
      { key: 'rating', label: 'Rating (1-5)', type: 'number' },
      { key: 'appliedOn', label: 'Applied on', type: 'date' },
      { key: 'offeredCtc', label: 'Offered salary (yearly)', type: 'number' },
      { key: 'notes', label: 'Notes', type: 'textarea' },
    ],
    actions: [
      {
        label: 'Hire', path: (r) => `/recruitment/applicants/${r.id}/hire`, perm: 'recruitment:write',
        show: (r) => r.status === 'OFFERED',
        confirm: 'Create an employee record for this applicant, joining today?',
      },
    ],
  },
  {
    path: '/interviews', group: 'Recruitment', title: 'Interviews', perm: 'recruitment', endpoint: '/recruitment/interviews',
    columns: [
      { key: 'applicant.name', label: 'Applicant' },
      { key: 'scheduledAt', label: 'When' },
      { key: 'interviewer.fullName', label: 'Interviewer' },
      { key: 'mode', label: 'Mode' },
      { key: 'rating', label: 'Rating' },
      { key: 'status', label: 'Status', badge: true },
    ],
    fields: [
      { key: 'applicant', label: 'Applicant', type: 'ref', endpoint: '/recruitment/applicants', required: true },
      { key: 'scheduledAt', label: 'Date and time', type: 'datetime' },
      { key: 'interviewer', label: 'Interviewer', type: 'ref', endpoint: '/core/employees' },
      { key: 'mode', label: 'Mode', type: 'select', options: opts('IN_PERSON', 'PHONE', 'VIDEO') },
      { key: 'status', label: 'Status', type: 'select', required: true, default: 'SCHEDULED', options: opts('SCHEDULED', 'COMPLETED', 'CANCELLED') },
      { key: 'rating', label: 'Rating (1-5)', type: 'number' },
      { key: 'feedback', label: 'Feedback', type: 'textarea' },
    ],
  },

  // ------------------------------------------------------------ Expenses
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

  // ------------------------------------------------------------ Performance
  {
    path: '/appraisals', group: 'Performance', title: 'Appraisals', perm: 'performance', endpoint: '/performance/appraisals',
    hint: 'Final score = 70% manager rating + 30% goal achievement in the cycle.',
    columns: [
      { key: 'employee.fullName', label: 'Employee' },
      { key: 'cycle.name', label: 'Cycle' },
      { key: 'selfRating', label: 'Self' },
      { key: 'managerRating', label: 'Manager' },
      { key: 'finalScore', label: 'Final' },
      { key: 'status', label: 'Status', badge: true },
    ],
    fields: [
      employeeField,
      { key: 'cycle', label: 'Appraisal cycle', type: 'ref', endpoint: '/performance/cycles', required: true },
      { key: 'selfRating', label: 'Self rating (1-5)', type: 'number' },
      { key: 'managerRating', label: 'Manager rating (1-5)', type: 'number' },
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
      { key: 'weightage', label: 'Weight %' },
      { key: 'progress', label: 'Progress %' },
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

  // ------------------------------------------------------------ Administration
  {
    path: '/users', group: 'Administration', title: 'Users', perm: 'admin', endpoint: '/admin/users',
    hint: 'Link a user to an employee so they can use the self-service screens.',
    columns: [
      { key: 'username', label: 'Username' },
      { key: 'email', label: 'Email' },
      { key: 'role.name', label: 'Role' },
      { key: 'employee.fullName', label: 'Employee' },
      { key: 'active', label: 'Active' },
    ],
    fields: [
      { key: 'username', label: 'Username', type: 'text', required: true },
      { key: 'email', label: 'Email (for notifications)', type: 'text' },
      { key: 'password', label: 'Password (8+ characters; blank keeps the current one)', type: 'password' },
      { key: 'role', label: 'Role', type: 'ref', endpoint: '/admin/roles', required: true },
      { key: 'employee', label: 'Employee', type: 'ref', endpoint: '/core/employees' },
      { key: 'active', label: 'Active', type: 'checkbox', default: true },
    ],
  },
  {
    path: '/roles', group: 'Administration', title: 'Roles', perm: 'admin', endpoint: '/admin/roles',
    hint: 'read = view the screens, write = add and change records, approve = approve, reject, pay, submit. "*" means everything.',
    columns: [
      { key: 'name', label: 'Name' },
      { key: 'description', label: 'Description' },
      { key: 'permissions.length', label: 'Permissions' },
    ],
    fields: [
      { key: 'name', label: 'Name', type: 'text', required: true },
      { key: 'description', label: 'Description', type: 'text' },
      { key: 'permissions', label: 'Permissions', type: 'multicheck', endpoint: '/admin/permissions' },
    ],
  },
  {
    path: '/audit-log', group: 'Administration', title: 'Audit log', perm: 'admin', endpoint: '/admin/audit-logs',
    create: false, edit: false, remove: false,
    hint: 'Every change made through the system and every login, newest first (latest 500).',
    columns: [
      { key: 'at', label: 'When' },
      { key: 'username', label: 'User' },
      { key: 'action', label: 'Action', badge: true },
      { key: 'path', label: 'What' },
      { key: 'status', label: 'Result' },
      { key: 'ip', label: 'From' },
    ],
    fields: [],
  },
]

// ------------------------------------------------------------ Self-service (needs a linked employee)
export const selfResources = [
  {
    path: '/my/leaves', group: 'My space', title: 'My leaves', endpoint: '/me/leaves',
    create: true, edit: false, remove: false,
    columns: [
      { key: 'leaveType.name', label: 'Type' },
      { key: 'fromDate', label: 'From' },
      { key: 'toDate', label: 'To' },
      { key: 'totalDays', label: 'Days' },
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
    hint: 'This month. Check in and out from the My space home screen.',
    columns: [
      { key: 'attendanceDate', label: 'Date' },
      { key: 'status', label: 'Status', badge: true },
      { key: 'inTime', label: 'In' },
      { key: 'outTime', label: 'Out' },
      { key: 'workingHours', label: 'Hours' },
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
      { key: 'weightage', label: 'Weight %' },
      { key: 'progress', label: 'Progress %' },
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
      { key: 'selfRating', label: 'Self' },
      { key: 'managerRating', label: 'Manager' },
      { key: 'finalScore', label: 'Final' },
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
