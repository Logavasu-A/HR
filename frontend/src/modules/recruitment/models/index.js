import { opts } from '@/shared/modelHelpers'

export default [
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
]
