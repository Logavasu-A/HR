import { opts, nameAndDescription } from '@/shared/modelHelpers'
import { auth } from '@/shared/auth'

export default [
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
    actions: [
      {
        label: 'Bank details',
        route: (row) => ({ name: 'employee-bank-details', params: { id: row.id } }),
        show: () => auth.user?.permissions?.includes('employee-bank:read') === true,
      },
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
]
