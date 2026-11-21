export default [
  {
    path: '/users', group: 'Administration', title: 'Users', perm: 'admin', endpoint: '/admin/users',
    columns: [
      { key: 'username', label: 'Username' },
      { key: 'email', label: 'Email' },
      { key: 'role.name', label: 'Role' },
      { key: 'employee.fullName', label: 'Employee' },
      { key: 'active', label: 'Active', badge: true },
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
    columns: [
      { key: 'name', label: 'Name' },
      { key: 'description', label: 'Description' },
      { key: 'permissions.length', label: 'Permissions', align: 'right' },
    ],
    fields: [
      { key: 'name', label: 'Name', type: 'text', required: true },
      { key: 'description', label: 'Description', type: 'text' },
      {
        key: 'permissions', label: 'Permissions', type: 'multicheck', endpoint: '/admin/permissions',
        optionHelp: {
          prefix: 'scope:',
          text: 'Select at most one scope option. A scoped role uses the employee linked to the account.',
        },
      },
    ],
  },
  {
    path: '/audit-log', group: 'Administration', title: 'Audit log', perm: 'admin', endpoint: '/admin/audit-logs',
    create: false, edit: false, remove: false,
    columns: [
      { key: 'at', label: 'When' },
      { key: 'username', label: 'User' },
      { key: 'action', label: 'Action', badge: true },
      { key: 'path', label: 'What' },
      { key: 'status', label: 'Result', align: 'right' },
      { key: 'ip', label: 'From' },
    ],
    fields: [],
  },
]
