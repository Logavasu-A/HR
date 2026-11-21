<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { pretty } from '@/shared/utils'
import PlatformState from './PlatformState.vue'
import PlatformPageHeader from './PlatformPageHeader.vue'

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

const fields = [
  { key: 'name', label: 'Company name', max: 160 },
  { key: 'code', label: 'Company code', max: 40, pattern: '[a-z0-9][a-z0-9\\-]{1,39}' },
  { key: 'contactEmail', label: 'Contact email', type: 'email', max: 254 },
  { key: 'timezone', label: 'Time zone', max: 80 },
  { key: 'currency', label: 'Currency', max: 3, pattern: '[A-Z]{3}' },
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
  form.value = { name: '', code: '', contactEmail: '', timezone: 'UTC', currency: 'USD' }
}

function edit() {
  const company = selection.value
  form.value = {
    name: company.name, code: company.code, contactEmail: company.contact_email || '',
    timezone: company.timezone, currency: company.currency,
  }
}

function save() {
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

      <form v-if="form" class="card owner-editor" @submit.prevent="save">
        <h2 ref="editorHeading" tabindex="-1">{{ selection ? 'Edit company' : 'Add company' }}</h2>
        <div class="form-grid">
          <div v-for="field in fields" :key="field.key" class="field">
            <label :for="field.key">{{ field.label }}</label>
            <input :id="field.key" v-model.trim="form[field.key]" :type="field.type || 'text'" :pattern="field.pattern" :maxlength="field.max" required />
          </div>
          <div v-if="!selection" class="field">
            <label for="admin-email">First admin’s email</label>
            <input id="admin-email" v-model.trim="email" type="email" maxlength="80" required />
          </div>
        </div>
        <div class="owner-actions owner-editor-actions">
          <button class="btn primary" :disabled="busy">{{ selection ? 'Save changes' : 'Create company and invite admin' }}</button>
          <button class="btn" type="button" :disabled="busy" @click="form = null">Cancel</button>
        </div>
      </form>

      <section v-if="selection && !form" class="card owner-editor" aria-labelledby="company-detail">
        <div class="owner-panel-heading">
          <h2 id="company-detail" ref="detailHeading" tabindex="-1">{{ selection.name }}</h2>
          <div class="owner-actions">
            <span class="badge" :class="selection.status.toLowerCase()">{{ pretty(selection.status) }}</span>
            <button class="btn small" type="button" :disabled="busy" @click="selection = null">Close details</button>
          </div>
        </div>
        <dl class="owner-details">
          <div><dt>Company code</dt><dd>{{ selection.code }}</dd></div>
          <div><dt>Contact email</dt><dd>{{ selection.contact_email || '—' }}</dd></div>
          <div><dt>Time zone</dt><dd>{{ selection.timezone }}</dd></div>
          <div><dt>Currency</dt><dd>{{ selection.currency }}</dd></div>
          <template v-if="selection.admin_email">
            <div><dt>First admin</dt><dd>{{ selection.admin_email }}</dd></div>
            <div><dt>Invitation status</dt><dd>{{ pretty(selection.invitation_status) }}</dd></div>
            <div><dt>Email delivery</dt><dd>{{ pretty(selection.delivery_status) }}</dd></div>
          </template>
        </dl>
        <div class="owner-actions">
          <router-link v-if="security?.adminView" class="btn primary"
            :to="{ path: '/platform/workspace', query: { company: selection.id } }">Open Admin View</router-link>
          <button class="btn" :disabled="busy" @click="showEditor(edit)">Edit details</button>
          <button v-if="selection.status === 'ACTIVE'" class="btn danger" :disabled="busy" @click="change('suspend')">Suspend company</button>
          <button v-if="selection.status === 'SUSPENDED'" class="btn" :disabled="busy" @click="change('reactivate')">Reactivate company</button>
          <button v-if="selection.status === 'PENDING'" class="btn" :disabled="busy" @click="change('invitation/resend')">Resend invitation</button>
          <button v-if="selection.invitation_status === 'PENDING'" class="btn" :disabled="busy" @click="change('invitation/revoke')">Revoke invitation</button>
        </div>
      </section>

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
          <div class="table-wrapper" tabindex="0" role="region" aria-label="Companies table">
            <table class="data-table">
              <thead>
                <tr>
                  <th scope="col">Company</th>
                  <th scope="col" class="owner-status">Status</th>
                  <th scope="col" class="actions-col">Actions</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="company in results.content" :key="company.id">
                  <td>
                    <router-link v-if="security?.adminView" :to="{ path: '/platform/workspace', query: { company: company.id } }">
                      <strong>{{ company.name }}</strong>
                    </router-link>
                    <strong v-else>{{ company.name }}</strong>
                    <span class="company-code muted">{{ [company.code, company.currency, company.timezone].filter(Boolean).join(' · ') }}</span>
                  </td>
                  <td><span class="badge" :class="company.status.toLowerCase()">{{ pretty(company.status) }}</span></td>
                  <td class="actions">
                    <div class="company-row-actions">
                      <router-link v-if="security?.adminView" class="btn small" :aria-label="`Open ${company.name} workspace`"
                        :to="{ path: '/platform/workspace', query: { company: company.id } }">Open workspace</router-link>
                      <button class="btn small tonal" :aria-label="`View ${company.name}`" :disabled="busy" @click="run(() => showCompany(company.id))">Details</button>
                    </div>
                  </td>
                </tr>
                <tr v-if="!results.content.length" class="table-state-row">
                  <td colspan="3" class="empty">
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
          <div class="owner-panel-heading">
            <h2 id="recent-activity">Activity history</h2>
            <select v-if="activity?.actions.length" :value="activityAction" aria-label="Filter activity by action"
              :disabled="busy" @change="run(() => loadActivity(0, $event.target.value))">
              <option value="">All actions</option>
              <option v-for="action in activity.actions" :key="action" :value="action">{{ pretty(action) }}</option>
            </select>
          </div>
          <ul v-if="activity?.events.content.length">
            <li v-for="event in activity.events.content" :key="event.id">
              <strong>{{ pretty(event.action) }}</strong>
              <button v-if="event.company_id" class="owner-company-link" type="button" :disabled="busy"
                @click="run(() => showCompany(event.company_id))">{{ event.company_name }}</button>
              <span class="muted">{{ event.actor }}</span>
              <span v-if="event.reason">{{ event.reason }}</span>
              <span v-if="event.request_method" class="muted activity-request">
                {{ event.request_method }} {{ event.request_path }} · {{ event.http_status }}
              </span>
              <time :datetime="event.occurred_at">{{ new Date(event.occurred_at).toLocaleString() }}</time>
            </li>
          </ul>
          <p v-else-if="activity" class="muted">No activity matches this filter.</p>
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
.owner-stats { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-bottom: 20px; }
.owner-metric { min-width: 0; padding: var(--platform-card-padding); text-align: left; cursor: pointer; }
.owner-metric[data-status="active"] { --status-dot: var(--ok-line); }
.owner-metric[data-status="pending"] { --status-dot: var(--warn-line); }
.owner-metric[data-status="suspended"] { --status-dot: var(--bad-line); }
.owner-metric[data-status]:not([data-status="total"]) .stat-label::before {
  content: ''; display: inline-block; width: 8px; height: 8px;
  border-radius: 50%; margin-inline-end: var(--space-8); background: var(--status-dot);
}
.owner-metric:hover:not(:disabled) { background: var(--row-hover); }
.owner-metric[aria-pressed="true"] {
  background: var(--primary-tint); border-color: var(--primary-tint-2);
  box-shadow: inset 3px 0 0 var(--accent), var(--shadow-card);
}
.owner-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(245px, .32fr); gap: 16px; align-items: start; }
.owner-grid--single { grid-template-columns: minmax(0, 1fr); }
.owner-directory { min-width: 0; overflow: hidden; padding: var(--platform-card-padding); }
.owner-directory .owner-panel-heading { margin-bottom: var(--space-12); padding-inline-start: var(--table-cell-inline-padding); }
.owner-panel-heading h2 { margin: 0; }
.owner-filters {
  display: flex; flex-wrap: wrap; align-items: stretch; gap: 8px;
  padding: 0 0 var(--space-16);
}
.owner-filters input.search { flex: 1 1 185px; min-width: 0; }
.owner-filters select { width: auto; min-width: 0; max-width: 100%; }
.owner-editor-actions { margin-top: 16px; }
.owner-status { width: 1%; }
.company-code { display: block; font-size: 12px; overflow-wrap: anywhere; }
.company-row-actions { display: flex; justify-content: flex-end; gap: 6px; min-width: max-content; }
.company-row-actions .btn { white-space: nowrap; }
.owner-directory .data-table { min-width: 580px; }
.owner-directory .actions-col { width: 225px; }
.owner-directory td:first-child a { text-decoration: none; }
.owner-directory td:first-child a:hover { text-decoration: underline; }
.owner-pagination { justify-content: flex-end; padding: 12px var(--table-cell-inline-padding); border-top: 1px solid var(--line); }
.owner-editor, .owner-activity { min-width: 0; padding: var(--platform-card-padding); }
.owner-editor { margin-bottom: 16px; border-top: 3px solid var(--primary); }
.owner-editor h2 { scroll-margin-top: 16px; }
.owner-details {
  display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 150px), 1fr));
  gap: 12px; margin: 14px 0 18px;
}
.owner-details dt, .owner-activity time { color: var(--muted); font-size: 12px; }
.owner-details dd { margin: 0; overflow-wrap: anywhere; font-weight: 600; }
.owner-activity ul { padding: 0; margin: 0; list-style: none; }
.owner-activity li { display: grid; gap: 2px; padding: 10px 0; border-bottom: 1px solid var(--line); }
.owner-activity li:last-child { border-bottom: 0; padding-bottom: 0; }
.activity-request { overflow-wrap: anywhere; }
.owner-company-link { justify-self: start; padding: 0; border: 0; background: transparent; color: var(--primary-dark); text-align: left; cursor: pointer; }
.owner-company-link:hover { text-decoration: underline; }
.activity-pagination { padding-inline: 0; margin-top: 12px; }
.badge.suspended { background: var(--bad-bg); color: var(--bad-text); }
@media (max-width: 1200px) { .owner-grid { grid-template-columns: minmax(0, 1fr); } }
@media (max-width: 520px) {
  .owner-directory .data-table { min-width: 0; table-layout: fixed; }
  .owner-directory :is(.owner-status, .actions-col) { width: 4.75rem; }
  .owner-directory :is(.owner-status, .actions-col, td:nth-child(2), td.actions) { padding-inline: 4px; }
  .owner-directory .badge { padding-inline: 5px; gap: 4px; font-size: 11px; }
  .company-row-actions { display: grid; justify-content: stretch; min-width: 0; }
  .company-row-actions .btn { margin: 0; padding-inline: 4px; white-space: normal; text-align: center; }
  .owner-stats { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .owner-filters input.search { flex-basis: 100%; }
  .owner-filters select { flex: 1 1 120px; }
  .owner-editor-actions > .primary { flex: 1 1 auto; }
}
</style>
