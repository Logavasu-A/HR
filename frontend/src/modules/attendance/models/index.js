import { opts, employeeField } from '@/shared/modelHelpers'

export default [
  {
    path: '/attendance', group: 'Attendance', title: 'Attendance records', perm: 'attendance', endpoint: '/attendance/records',
    saveNote: 'Saving a record for a date that already has one replaces it.',
    columns: [
      { key: 'attendanceDate', label: 'Date' },
      { key: 'employee.fullName', label: 'Employee' },
      { key: 'status', label: 'Status', badge: true },
      { key: 'inTime', label: 'In' },
      { key: 'outTime', label: 'Out' },
      { key: 'workingHours', label: 'Hours', align: 'right' },
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
      { key: 'graceMinutes', label: 'Grace (min)', align: 'right' },
    ],
    fields: [
      { key: 'name', label: 'Name', type: 'text', required: true },
      { key: 'startTime', label: 'Start time', type: 'time' },
      { key: 'endTime', label: 'End time', type: 'time' },
      { key: 'graceMinutes', label: 'Grace period (minutes)', type: 'number' },
    ],
  },
]
