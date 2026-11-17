/** FULL_TIME -> "Full time" */
export function pretty(value) {
  if (value == null || value === '') return ''
  const s = String(value).replace(/_/g, ' ').toLowerCase()
  return s.charAt(0).toUpperCase() + s.slice(1)
}

export function money(value) {
  if (value == null || value === '') return ''
  return Number(value).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** Reads "employee.department.name" from an object; missing links give undefined. */
export function getPath(obj, path) {
  return path.split('.').reduce((o, k) => (o == null ? o : o[k]), obj)
}

/** Text shown for a linked record in dropdowns. */
export function refLabel(item) {
  if (!item) return ''
  if (item.fullName) return `${item.employeeCode} · ${item.fullName}`
  return item.name ?? item.title ?? item.username ?? `#${item.id}`
}

export const MONTHS = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
]

export function periodLabel(year, month) {
  return `${MONTHS[month - 1]} ${year}`
}

export function errorMessage(e) {
  return e?.response?.data?.message || e?.message || 'Something went wrong'
}
