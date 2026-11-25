<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { pretty } from '@/shared/utils'
import PlatformState from './PlatformState.vue'
import PlatformPageHeader from './PlatformPageHeader.vue'
import CompanyOptionPicker from './CompanyOptionPicker.vue'
import AppDialog from '@/shared/components/AppDialog.vue'

const router = useRouter()
const search = ref('')
const status = ref('')
const sort = ref('newest')
const appliedFilters = ref({ search: '', status: '', sort: 'newest' })
const dashboard = ref(null)
const results = ref(null)
const activity = ref(null)
const activityAction = ref('')
const selection = ref(null)
const form = ref(null)
const email = ref('')
const error = ref('')
const notice = ref('')
const noticeKind = ref('ok')
const busy = ref(false)
const security = ref(null)
const editorHeading = ref(null)
const detailHeading = ref(null)
const localeOptions = ref(null)
const localeLoading = ref(false)
const localeError = ref('')
const currencyNames = new Intl.DisplayNames(undefined, { type: 'currency' })
const choices = computed(() => ({
  timezone: (localeOptions.value?.timezones ?? []).map(value => ({ value, label: value.replaceAll('_', ' ') })),
  currency: (localeOptions.value?.currencies ?? []).map(value => ({ value, label: `${value} · ${currencyNames.of(value)}` })),
}))
const codePreview = computed(() => {
  const name = form.value?.name
  if (!name) return ''
  const words = name.normalize('NFKD').replace(/\p{M}/gu, '').toLowerCase().match(/[a-z0-9]+/g) ?? []
  if (!words.length) return 'co'
  if (words.length > 1) return words.slice(0, 6).map(word => word[0]).join('')
  return words[0].length === 1 ? words[0] + '1' : words[0].slice(0, 4)
})

const fields = [
  { key: 'name', label: 'Company name', max: 160 },
  { key: 'code', label: 'Company code', max: 40, pattern: '[a-z0-9][a-z0-9\\-]{1,39}' },
  { key: 'contactEmail', label: 'Contact email', type: 'email', max: 254 },
  { key: 'timezone', label: 'Time zone', placeholder: 'Search time zones' },
  { key: 'currency', label: 'Currency', placeholder: 'Search currency names or codes' },
]
const metrics = { total: 'Companies', pending: 'Pending', active: 'Active', suspended: 'Suspended' }

async function platform(path, method = 'GET', body) {
  const headers = {}
  const token = sessionStorage.getItem('hrms_platform_token')
  if (token) headers.Authorization = 'Bearer ' + token
  if (body) headers['Content-Type'] = 'application/json'
  const response = await fetch('/api/platform' + path, {
    method, headers, cache: 'no-store', body: body ? JSON.stringify(body) : undefined,
  })
  if (response.status === 401) {
    if (sessionStorage.getItem('hrms_platform_token') === token) {
      sessionStorage.removeItem('hrms_platform_token')
      router.replace('/platform/login')
    }
    throw new Error('Your owner session has ended.')
  }
  const data = await response.json().catch(() => null)
  if (!response.ok) throw new Error(data?.message || 'The request could not be completed.')
  return data
}

async function run(action) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  notice.value = ''
  noticeKind.value = 'ok'
  try {
    await action()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

function companyPage(filters, page) {
  return platform('/companies?' + new URLSearchParams({ ...filters, page }))
}

async function availableCompanyPage(filters, page) {
  const companies = await companyPage(filters, page)
  if (page > 0 && !companies.content.length) {
    return companyPage(filters, Math.max(0, companies.totalPages - 1))
  }
  return companies
}

async function loadCompanies(page = 0, filters = appliedFilters.value) {
  results.value = await availableCompanyPage(filters, page)
  appliedFilters.value = filters
}

function activityPage(action, page) {
  return platform('/activity?' + new URLSearchParams({ action, page }))
}

async function loadActivity(page = 0, action = activityAction.value) {
  activity.value = await activityPage(action, page)
  activityAction.value = action
}

async function loadLocaleOptions() {
  if (localeOptions.value || localeLoading.value) return
  localeError.value = ''
  localeLoading.value = true
  try {
    localeOptions.value = await platform('/companies/form-options')
  } catch (failure) {
    localeError.value = failure.message
  } finally {
    localeLoading.value = false
  }
}

async function refresh(page = results.value?.page ?? 0) {
  const [summary, companies, history] = await Promise.all([
    platform('/dashboard'),
    availableCompanyPage(appliedFilters.value, page),
    security.value?.role === 'GLOBAL_SUPER_ADMIN' ? activityPage(activityAction.value, activity.value?.events.page ?? 0) : null,
  ])
  dashboard.value = summary
  results.value = companies
  activity.value = history
}

function applyFilters() {
  return run(() => loadCompanies(0, { search: search.value.trim(), status: status.value, sort: sort.value }))
}

function filterByStatus(key) {
  status.value = key === 'total' ? '' : key.toUpperCase()
  return applyFilters()
}

function clearFilters() {
  search.value = ''
  status.value = ''
  sort.value = 'newest'
  return applyFilters()
}

function quickCode(name) {
  return (name || 'CO').trim().split(/\s+/).slice(0, 2).map(w => w[0]).join('').toUpperCase() || 'CO'
}

const verbLabels = { GET: 'Read', POST: 'Submit', PUT: 'Update', PATCH: 'Update', DELETE: 'Close' }
function formatOperation(method, path) {
  if (!path) return ''
  const clean = path.replace(/^\/api(\/platform)?/, '').replace(/\/[0-9a-f]{8}-[0-9a-f-]{27,}/gi, '').replace(/\/\d+$/, '')
  const map = {
    '/workspace': 'Admin View session',
    '/control-tower': 'Control Tower metrics',
    '/control-tower/step-up': 'Payroll verification',
    '/companies': 'Company directory',
    '/companies/form-options': 'Locale options',
    '/audit': 'Audit Center',
    '/access': 'Access roles',
    '/dashboard': 'Platform summary',
    '/activity': 'Activity history',
    '/session': 'Platform session',
    '/dashboard/summary': 'Workspace dashboard',
  }
  const target = map[clean] || pretty(clean.split('/').filter(Boolean).slice(0, 2).join(' ') || 'system')
  const verb = verbLabels[method] || method
  return `${verb} · ${target}`
}

function csvField(value) {
  let text = String(value ?? '')
  if (/^[\t\r\n]|^\s*[=+@-]/.test(text)) text = "'" + text
  return '"' + text.replaceAll('"', '""') + '"'
}

function exportCompanies() {
  return run(async () => {
    const filters = appliedFilters.value
    const columns = ['Company', 'Code', 'Status', 'Contact email', 'Time zone', 'Currency', 'Created at']
    const rows = [columns]
    let page = 0
    let last
    do {
      const batch = await companyPage(filters, page++)
      for (const { name, code, status, contact_email, timezone, currency, created_at } of batch.content) {
        rows.push([name, code, pretty(status), contact_email, timezone, currency, created_at])
      }
      last = batch.last
    } while (!last)

    const csv = rows.map(row => row.map(csvField).join(',')).join('\r\n') + '\r\n'
    const url = URL.createObjectURL(new Blob(['\uFEFF', csv], { type: 'text/csv;charset=utf-8' }))
    const link = document.createElement('a')
    link.href = url
    link.download = 'hrms-companies.csv'
    link.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
    notice.value = `Exported ${rows.length - 1} ${rows.length === 2 ? 'company' : 'companies'}.`
  })
}

async function select(id) {
  selection.value = await platform('/companies/' + id)
  form.value = null
}

function add() {
  selection.value = null
  email.value = ''
  form.value = { name: '', contactEmail: '', timezone: 'UTC', currency: 'USD' }
}

function edit() {
  const company = selection.value
  form.value = {
    name: company.name, code: company.code, contactEmail: company.contact_email || '',
    timezone: company.timezone, currency: company.currency,
  }
}

function save() {
  if (!localeOptions.value) return
  return run(async () => {
    const id = selection.value?.id
    if (id) {
      await platform('/companies/' + id, 'PUT', form.value)
      await select(id)
      notice.value = 'Company details updated.'
    } else {
      const result = await platform('/companies', 'POST', { company: form.value, adminEmail: email.value })
      form.value = null
      noticeKind.value = result.delivered ? 'ok' : 'warn'
      notice.value = result.delivered ? 'Company created and invitation sent.' : 'Company created. Email delivery failed. Open the company to resend.'
    }
    await refresh(0)
  })
}

function change(action) {
  const id = selection.value.id
  return run(async () => {
    const result = await platform('/companies/' + id + '/' + action, 'POST')
    await select(id)
    await refresh()
    noticeKind.value = result?.delivered === false ? 'warn' : 'ok'
    notice.value = result ? (result.delivered ? 'Invitation sent.' : 'Invitation email could not be delivered.') : 'Company updated.'
  })
}

const start = () => run(async () => {
  security.value = await platform('/session')
  await refresh(0)
})

async function showCompany(id) {
  await select(id)
  await nextTick()
  detailHeading.value?.focus()
}

async function showEditor(open) {
  open()
  loadLocaleOptions()
  await nextTick()
  editorHeading.value?.focus()
}

onMounted(start)
</script>

<template>
  <section class="owner-console">
    <PlatformPageHeader title="Companies">
        <button v-if="results" class="btn small" :disabled="busy" @click="run(() => refresh())">Refresh</button>
        <button v-if="results" class="btn primary" type="button" :disabled="busy" @click="showEditor(add)">Add company</button>
    </PlatformPageHeader>
    <PlatformState v-if="error" kind="error" title="Company request failed" :message="error">
      <button v-if="!results" class="btn small" type="button" :disabled="busy" @click="start">Try again</button>
    </PlatformState>
    <p v-if="notice" class="alert" :class="noticeKind" role="status">{{ notice }}</p>
    <PlatformState v-if="busy && !results" kind="loading" title="Loading companies" />

    <template v-if="results">
      <section v-if="dashboard" class="owner-stats" aria-label="Filter companies by status">
        <button v-for="(label, key) in metrics" :key="key" class="card owner-metric" type="button" :data-status="key"
          :aria-pressed="appliedFilters.status === (key === 'total' ? '' : key.toUpperCase())"
          :disabled="busy" @click="filterByStatus(key)">
          <div class="stat-label">{{ label }}</div>
          <div class="stat-value">{{ dashboard.counts[key] }}</div>
        </button>
      </section>

      <AppDialog v-if="form" :title="selection ? 'Edit company' : 'Add company'" form @dismiss="form = null" @submit="save">
        <div class="company-modal-form" :aria-busy="busy">
          <div class="form-grid">
            <div v-for="field in fields" :key="field.key" class="field">
              <label :id="field.key + '-label'" :for="field.key">{{ field.label }}</label>
              <CompanyOptionPicker v-if="field.placeholder" :id="field.key" v-model="form[field.key]"
                :options="choices[field.key]" :placeholder="field.placeholder" :disabled="busy || !localeOptions" />
              <template v-else-if="field.key === 'code' && !selection">
                <input id="code" :value="codePreview" placeholder="Short code generated automatically" aria-describedby="generated-code-hint" readonly />
                <small id="generated-code-hint" class="muted">If the short code is taken, a number is added.</small>
              </template>
              <input v-else :id="field.key" v-model.trim="form[field.key]" :type="field.type || 'text'"
                :pattern="field.pattern" :maxlength="field.max" required />
            </div>
            <div v-if="!selection" class="field">
              <label for="admin-email">First admin’s email</label>
              <input id="admin-email" v-model.trim="email" type="email" maxlength="80" placeholder="admin@company.com" required />
            </div>
          </div>
          <p v-if="localeLoading" class="muted modal-status-note" role="status">Loading time zones and currencies…</p>
          <div v-else-if="localeError" class="owner-actions owner-editor-actions">
            <span class="alert" role="alert">Could not load time zones and currencies. {{ localeError }}</span>
            <button class="btn small" type="button" @click="loadLocaleOptions">Retry</button>
          </div>
        </div>
        <template #footer>
          <button class="btn" type="button" :disabled="busy" @click="form = null">Cancel</button>
          <button class="btn primary" type="submit" :disabled="busy || !localeOptions">
            {{ busy ? 'Saving…' : selection ? 'Save changes' : 'Create company and invite admin' }}
          </button>
        </template>
      </AppDialog>

      <AppDialog v-if="selection && !form" :title="selection.name" @dismiss="selection = null">
        <div class="company-detail-modal">
          <div class="company-detail-banner">
            <div class="company-detail-id">
              <span class="company-code-pill">{{ selection.code }}</span>
              <span class="badge" :class="selection.status.toLowerCase()">{{ pretty(selection.status) }}</span>
            </div>
            <router-link v-if="security?.adminView" class="btn small primary"
              :to="{ path: '/platform/workspace', query: { company: selection.id } }">
              Open Admin View →
            </router-link>
          </div>
          <dl class="owner-details">
            <div class="detail-item"><dt>Company code</dt><dd>{{ selection.code }}</dd></div>
            <div class="detail-item"><dt>Contact email</dt><dd>{{ selection.contact_email || '—' }}</dd></div>
            <div class="detail-item"><dt>Time zone</dt><dd>{{ selection.timezone }}</dd></div>
            <div class="detail-item"><dt>Currency</dt><dd>{{ selection.currency }}</dd></div>
            <template v-if="selection.admin_email">
              <div class="detail-item"><dt>First admin</dt><dd>{{ selection.admin_email }}</dd></div>
              <div class="detail-item"><dt>Invitation status</dt><dd>{{ pretty(selection.invitation_status) }}</dd></div>
              <div class="detail-item"><dt>Email delivery</dt><dd>{{ pretty(selection.delivery_status) }}</dd></div>
            </template>
          </dl>
        </div>
        <template #footer>
          <div class="company-modal-footer">
            <div class="company-modal-footer-left">
              <button v-if="selection.status === 'ACTIVE'" class="btn danger" type="button" :disabled="busy" @click="change('suspend')">Suspend company</button>
              <button v-if="selection.status === 'SUSPENDED'" class="btn" type="button" :disabled="busy" @click="change('reactivate')">Reactivate company</button>
              <button v-if="selection.status === 'PENDING'" class="btn" type="button" :disabled="busy" @click="change('invitation/resend')">Resend invitation</button>
              <button v-if="selection.invitation_status === 'PENDING'" class="btn" type="button" :disabled="busy" @click="change('invitation/revoke')">Revoke invitation</button>
            </div>
            <div class="company-modal-footer-right">
              <button class="btn" type="button" :disabled="busy" @click="selection = null">Close</button>
              <button class="btn primary" type="button" :disabled="busy" @click="showEditor(edit)">Edit details</button>
            </div>
          </div>
        </template>
      </AppDialog>

      <div class="owner-grid" :class="{ 'owner-grid--single': security?.role === 'PLATFORM_ADMINISTRATOR' }">
        <section class="card owner-directory" aria-labelledby="company-directory">
          <header class="owner-panel-heading">
            <h2 id="company-directory">Company directory</h2>
            <div class="owner-actions">
              <span class="muted">{{ results.totalElements }} {{ results.totalElements === 1 ? 'company' : 'companies' }}</span>
              <button class="btn small" type="button" :disabled="busy || !results.totalElements" @click="exportCompanies">Export CSV</button>
            </div>
          </header>
          <form class="owner-filters" @submit.prevent="applyFilters">
            <input v-model="search" class="search" type="search" aria-label="Search companies" placeholder="Company, code, or email" />
            <select v-model="status" aria-label="Company status">
              <option value="">All statuses</option>
              <option value="PENDING">Pending</option>
              <option value="ACTIVE">Active</option>
              <option value="SUSPENDED">Suspended</option>
            </select>
            <select v-model="sort" aria-label="Sort companies">
              <option value="newest">Newest first</option>
              <option value="oldest">Oldest first</option>
              <option value="name-asc">Name A–Z</option>
              <option value="name-desc">Name Z–A</option>
            </select>
            <button class="btn" :disabled="busy">Apply filters</button>
            <button v-if="search || status || sort !== 'newest'" class="btn" type="button" :disabled="busy" @click="clearFilters">Clear</button>
          </form>
          <div class="table-wrapper owner-table-wrap" tabindex="0" role="region" aria-label="Companies table">
            <table class="data-table responsive-table company-directory-table" role="table">
              <thead>
                <tr>
                  <th scope="col" class="company-col">Company</th>
                  <th scope="col" class="owner-currency">Currency</th>
                  <th scope="col" class="owner-timezone">Time zone</th>
                  <th scope="col" class="owner-status">Status</th>
                  <th scope="col" class="actions-col">Actions</th>
                </tr>
              </thead>
              <tbody role="rowgroup">
                <tr v-for="company in results.content" :key="company.id" role="row">
                  <td data-label="Company" role="cell">
                    <div class="company-identity">
                      <span class="company-avatar" aria-hidden="true">{{ quickCode(company.name) }}</span>
                      <div class="company-identity-text">
                        <router-link v-if="security?.adminView" class="company-title-link" :to="{ path: '/platform/workspace', query: { company: company.id } }">
                          <strong>{{ company.name }}</strong>
                        </router-link>
                        <strong v-else class="company-title-text">{{ company.name }}</strong>
                        <span class="company-code-badge">{{ company.code }}</span>
                      </div>
                    </div>
                  </td>
                  <td data-label="Currency" role="cell">
                    <span class="company-currency-pill">{{ company.currency || '—' }}</span>
                  </td>
                  <td data-label="Time zone" role="cell">
                    <span class="company-tz muted">{{ company.timezone || 'UTC' }}</span>
                  </td>
                  <td data-label="Status" role="cell">
                    <span class="badge" :class="company.status.toLowerCase()">{{ pretty(company.status) }}</span>
                  </td>
                  <td class="actions" role="cell">
                    <div class="company-row-actions">
                      <router-link v-if="security?.adminView" class="btn small" :aria-label="`Open ${company.name} workspace`"
                        :to="{ path: '/platform/workspace', query: { company: company.id } }">Open workspace</router-link>
                      <button class="btn small tonal" :aria-label="`View ${company.name}`" :disabled="busy" @click="run(() => showCompany(company.id))">Details</button>
                    </div>
                  </td>
                </tr>
                <tr v-if="!results.content.length" class="table-state-row">
                  <td colspan="5" class="empty">
                    <div class="table-state-message">No companies match these filters.</div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-if="results.totalPages > 1" class="owner-pagination">
            <span class="muted">Page {{ results.page + 1 }} of {{ results.totalPages }}</span>
            <button class="btn small" :disabled="busy || results.first" @click="run(() => loadCompanies(results.page - 1))">Previous</button>
            <button class="btn small" :disabled="busy || results.last" @click="run(() => loadCompanies(results.page + 1))">Next</button>
          </div>
        </section>

        <section v-if="security?.role === 'GLOBAL_SUPER_ADMIN'" class="card owner-activity" aria-labelledby="recent-activity">
          <header class="owner-panel-heading">
            <div>
              <h2 id="recent-activity">Activity history</h2>
              <p class="muted activity-subtitle">Recent platform actions, API calls, and tenant operations</p>
            </div>
            <div class="owner-actions">
              <select v-if="activity?.actions.length" :value="activityAction" class="activity-action-select"
                aria-label="Filter activity by action" :disabled="busy" @change="run(() => loadActivity(0, $event.target.value))">
                <option value="">All actions</option>
                <option v-for="action in activity.actions" :key="action" :value="action">{{ pretty(action) }}</option>
              </select>
            </div>
          </header>
          <div v-if="activity?.events.content.length" class="table-wrapper owner-table-wrap" tabindex="0" role="region" aria-label="Activity history table">
            <table class="data-table responsive-table activity-table" role="table">
              <thead>
                <tr>
                  <th scope="col" class="activity-action-col">Action &amp; Context</th>
                  <th scope="col" class="activity-actor-col">Actor</th>
                  <th scope="col" class="activity-op-col">Operation</th>
                  <th scope="col" class="activity-time-col">Time</th>
                </tr>
              </thead>
              <tbody role="rowgroup">
                <tr v-for="event in activity.events.content" :key="event.id" role="row">
                  <td data-label="Action" role="cell">
                    <div class="activity-action-cell">
                      <strong>{{ pretty(event.action) }}</strong>
                      <button v-if="event.company_id" class="owner-company-pill" type="button" :disabled="busy"
                        @click="run(() => showCompany(event.company_id))">{{ event.company_name }}</button>
                    </div>
                    <span v-if="event.reason" class="activity-reason muted">{{ event.reason }}</span>
                  </td>
                  <td data-label="Actor" role="cell">
                    <span class="activity-actor">{{ event.actor || 'System' }}</span>
                  </td>
                  <td data-label="Operation" role="cell">
                    <div v-if="event.request_method" class="activity-req-wrap" :title="`${event.request_method} ${event.request_path}`">
                      <span class="activity-op-pill">{{ formatOperation(event.request_method, event.request_path) }}</span>
                      <span class="badge" :class="Number(event.http_status) >= 400 ? 'rejected' : 'active'">
                        {{ Number(event.http_status) >= 400 ? 'Fail' : 'Pass' }}
                      </span>
                    </div>
                    <span v-else class="muted">—</span>
                  </td>
                  <td data-label="Time" class="activity-time-cell" role="cell">
                    <time :datetime="event.occurred_at">{{ new Date(event.occurred_at).toLocaleString() }}</time>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <p v-else-if="activity" class="muted activity-empty">No activity matches this filter.</p>
          <div v-if="activity?.events.totalPages > 1" class="owner-pagination activity-pagination">
            <span class="muted">Page {{ activity.events.page + 1 }} of {{ activity.events.totalPages }}</span>
            <button class="btn small" :disabled="busy || activity.events.first" @click="run(() => loadActivity(activity.events.page - 1))">Previous</button>
            <button class="btn small" :disabled="busy || activity.events.last" @click="run(() => loadActivity(activity.events.page + 1))">Next</button>
          </div>
        </section>
      </div>
    </template>
  </section>
</template>

<style scoped>
.owner-panel-heading {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px 16px;
}
.owner-actions, .owner-pagination { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.owner-actions a { text-decoration: none; }
.owner-stats { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; margin-bottom: 20px; }
.owner-metric {
  min-width: 0;
  padding: 16px 20px;
  text-align: left;
  cursor: pointer;
  border-top: 3px solid var(--border);
  transition: transform 0.15s ease, box-shadow 0.15s ease, border-color 0.15s ease, background 0.15s ease;
}
.owner-metric[data-status="total"] { border-top-color: var(--primary); }
.owner-metric[data-status="active"] { --status-dot: var(--ok-line); border-top-color: var(--ok-line); }
.owner-metric[data-status="pending"] { --status-dot: var(--warn-line); border-top-color: var(--warn-line); }
.owner-metric[data-status="suspended"] { --status-dot: var(--bad-line); border-top-color: var(--bad-line); }
.owner-metric[data-status]:not([data-status="total"]) .stat-label::before {
  content: ''; display: inline-block; width: 8px; height: 8px;
  border-radius: 50%; margin-inline-end: var(--space-8); background: var(--status-dot);
}
.owner-metric:hover:not(:disabled) { background: var(--row-hover); transform: translateY(-1px); box-shadow: var(--shadow-menu); }
.owner-metric[aria-pressed="true"] {
  background: var(--primary-tint);
  border-color: var(--primary-tint-2);
  box-shadow: inset 0 0 0 1px var(--primary), var(--shadow-card);
}
.owner-grid { display: grid; grid-template-columns: minmax(0, 1fr); gap: 18px; align-items: start; }
.owner-directory { min-width: 0; overflow: hidden; padding: var(--platform-card-padding); }
.owner-directory .owner-panel-heading { margin-bottom: var(--space-16); padding-inline-start: 2px; }
.owner-panel-heading h2 { margin: 0; font-size: var(--platform-section-size); }
.owner-filters {
  display: flex; flex-wrap: wrap; align-items: center; gap: 10px;
  padding: 0 0 var(--space-16);
}
.owner-filters input.search { flex: 1 1 200px; min-width: 0; }
.owner-filters select { width: auto; min-width: 140px; max-width: 100%; }
.owner-table-wrap { border: 1px solid var(--line); border-radius: var(--radius-sm); overflow-x: auto; }
.company-col { min-width: 240px; }
.owner-currency { width: 7rem; }
.owner-timezone { width: 10rem; }
.owner-status { width: 8.5rem; }
.company-directory-table th,
.company-directory-table td,
.activity-table th,
.activity-table td {
  text-align: center !important;
  vertical-align: middle !important;
}
.company-directory-table th,
.activity-table th { white-space: nowrap !important; }
.company-directory-table th.actions-col,
.company-directory-table td.actions { position: static !important; text-align: center !important; width: 16rem; }
.company-row-actions { display: flex; flex-wrap: nowrap; justify-content: center; gap: 8px; min-width: 0; }
.company-identity { display: flex; align-items: center; justify-content: center; gap: 12px; min-width: 0; }
.company-avatar {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  border-radius: 9px;
  background: var(--primary-tint);
  border: 1px solid var(--primary-tint-2);
  color: var(--primary-dark);
  font-size: 12.5px;
  font-weight: 700;
  letter-spacing: 0.02em;
}
.company-identity-text { display: flex; flex-direction: column; align-items: flex-start; gap: 3px; min-width: 0; }
.company-title-link, .company-title-text { font-size: 14px; line-height: 1.3; overflow-wrap: anywhere; }
.company-code-badge {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11.5px;
  color: var(--muted);
  background: var(--page-bg);
  border: 1px solid var(--line);
  padding: 1px 6px;
  border-radius: 4px;
}
.company-currency-pill {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 5px;
  background: var(--page-bg);
  border: 1px solid var(--line);
  color: var(--text);
}
.company-tz { font-size: 13px; }
.company-row-actions .btn { max-inline-size: 100%; white-space: nowrap; }
.company-directory-table th,
.activity-table th { white-space: nowrap !important; }
.owner-directory td:first-child a { text-decoration: none; color: var(--primary); }
.owner-directory td:first-child a:hover { text-decoration: underline; }
.owner-pagination { justify-content: flex-end; padding: 14px var(--table-cell-inline-padding) 4px; border-top: 1px solid var(--line); }
.owner-activity { min-width: 0; padding: var(--platform-card-padding); }
.owner-activity .owner-panel-heading { margin-bottom: 12px; }

/* Pop-up Modal Enhancements */
.company-modal-form .form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(230px, 1fr));
  gap: 14px 18px;
}
.company-modal-form .field { margin: 0; }
.modal-status-note { margin: 12px 0 0; font-size: 13px; }
.company-detail-modal { display: grid; gap: 18px; }
.company-detail-banner {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  background: var(--page-bg);
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
}
.company-detail-id { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.company-code-pill {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12.5px;
  font-weight: 600;
  padding: 3px 9px;
  border-radius: 6px;
  background: var(--card-bg);
  border: 1px solid var(--border-strong);
  color: var(--text);
}
.owner-details {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 190px), 1fr));
  gap: 12px;
  margin: 0;
}
.detail-item {
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--card-bg);
}
.owner-details dt, .owner-activity time { color: var(--muted); font-size: 12px; }
.owner-details dd { margin: 4px 0 0; overflow-wrap: anywhere; font-weight: 600; color: var(--text); }
.company-modal-footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  width: 100%;
}
.company-modal-footer-left,
.company-modal-footer-right {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.owner-activity { min-width: 0; overflow: hidden; padding: var(--platform-card-padding); }
.owner-activity .owner-panel-heading { margin-bottom: var(--space-16); padding-inline-start: 2px; }
.activity-subtitle { margin: 2px 0 0; font-size: 12.5px; }
.activity-action-select { width: auto; min-width: 180px; }
.activity-table td { vertical-align: middle; }
.activity-action-col { width: 36%; }
.activity-actor-col { width: 14%; }
.activity-op-col { width: 30%; }
.activity-time-col { width: 20%; }
.activity-action-cell { display: flex; flex-wrap: wrap; align-items: center; justify-content: center; gap: 6px 8px; }
.activity-reason { display: block; font-size: 12px; margin-top: 4px; text-align: center; }
.activity-actor { font-weight: 500; color: var(--text); }
.activity-req-wrap { display: inline-flex; align-items: center; justify-content: center; gap: 8px; flex-wrap: nowrap; white-space: nowrap; }
.activity-op-pill {
  font-size: 12.5px;
  font-weight: 500;
  padding: 3px 9px;
  border-radius: 6px;
  background: var(--page-bg);
  border: 1px solid var(--line);
  color: var(--text);
}
.activity-time-cell { text-align: center !important; white-space: nowrap; }
.activity-time-cell time { color: var(--muted); font-size: 12.5px; font-variant-numeric: tabular-nums; }
.activity-empty { padding: 16px 0 4px; margin: 0; }
.owner-company-pill {
  padding: 2px 9px;
  border-radius: 999px;
  border: 1px solid var(--primary-tint-2);
  background: var(--primary-tint);
  color: var(--primary-dark);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s ease;
}
.owner-company-pill:hover { background: var(--primary-tint-2); }
.activity-pagination { margin-top: 4px; }
.badge.suspended { background: var(--bad-bg); color: var(--bad-text); }
@media (width < 768px) {
  .company-row-actions { flex-wrap: wrap; gap: var(--space-8); }
  .owner-directory { --table-cell-inline-padding: var(--space-12); }
  .owner-stats { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .owner-metric, .owner-directory, .owner-activity { padding: var(--space-12); }
  .owner-filters input.search { flex-basis: 100%; }
  .owner-filters select { flex: 1 1 7.5rem; }
  .owner-filters > .btn { flex: 1 1 auto; }
  .owner-directory .responsive-table tbody tr:not(.table-state-row) { gap: var(--space-4); padding-block: var(--space-12); }
  .owner-directory .responsive-table tbody td[data-label="Company"]::before,
  .owner-directory .responsive-table tbody td[data-label="Status"]::before { display: none; }
  .owner-directory td[data-label="Company"] strong { font-size: .9375rem; }
  .owner-directory td[data-label="Status"] .badge { min-block-size: 1.5rem; }
  .company-modal-footer { flex-direction: column; align-items: stretch; }
  .company-modal-footer-left, .company-modal-footer-right { justify-content: flex-end; }
}
</style>
