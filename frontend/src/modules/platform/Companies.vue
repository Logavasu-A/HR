<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { pretty } from '@/shared/utils'

const router = useRouter()
const search = ref('')
const status = ref('')
const dashboard = ref(null)
const results = ref(null)
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
    method, headers, body: body ? JSON.stringify(body) : undefined,
  })
  if (response.status === 401) {
    sessionStorage.removeItem('hrms_platform_token')
    router.replace('/platform/login')
    throw new Error('Your owner session has ended.')
  }
  const data = await response.json().catch(() => null)
  if (!response.ok) throw new Error(data?.message || 'The request could not be completed.')
  return data
}

async function run(action) {
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

async function load(page = 0) {
  const query = new URLSearchParams({ search: search.value, status: status.value, page })
  const [summary, companies] = await Promise.all([platform('/dashboard'), platform('/companies?' + query)])
  dashboard.value = summary
  results.value = companies
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
    await load()
  })
}

function change(action) {
  const id = selection.value.id
  return run(async () => {
    const result = await platform('/companies/' + id + '/' + action, 'POST')
    await select(id)
    await load(results.value.page)
    noticeKind.value = result?.delivered === false ? 'warn' : 'ok'
    notice.value = result ? (result.delivered ? 'Invitation sent.' : 'Invitation email could not be delivered.') : 'Company updated.'
  })
}

async function signOut() {
  await platform('/logout', 'POST')
  sessionStorage.removeItem('hrms_platform_token')
  router.replace('/platform/login')
}

const start = () => run(async () => {
  security.value = await platform('/session')
  await load()
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
  <main class="content owner-console">
    <header class="owner-header">
      <div>
        <p class="owner-section">Platform administration</p>
        <h1>Companies</h1>
      </div>
      <div class="owner-actions">
        <router-link class="btn small" to="/platform/login">Sign in again</router-link>
        <button class="btn small" :disabled="busy || !security" @click="run(signOut)">Sign out</button>
        <button v-if="results" class="btn primary" type="button" :disabled="busy" @click="showEditor(add)">Add company</button>
      </div>
    </header>
    <p v-if="error" class="alert" role="alert">{{ error }}</p>
    <p v-if="notice" class="alert" :class="noticeKind" role="status">{{ notice }}</p>
    <p v-if="busy && !results" role="status">Loading companies…</p>
    <button v-if="error && !results" class="btn" type="button" :disabled="busy" @click="start">Try again</button>

    <template v-if="results">
      <section class="owner-stats" aria-label="Company overview">
        <div v-for="(label, key) in metrics" :key="key" class="card owner-metric" :data-status="key">
          <div class="stat-label">{{ label }}</div>
          <div class="stat-value">{{ dashboard.counts[key] }}</div>
        </div>
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
          <span class="badge" :class="selection.status.toLowerCase()">{{ pretty(selection.status) }}</span>
        </div>
        <dl class="owner-details">
          <div><dt>Company code</dt><dd>{{ selection.code }}</dd></div>
          <div><dt>Time zone</dt><dd>{{ selection.timezone }}</dd></div>
          <div><dt>Currency</dt><dd>{{ selection.currency }}</dd></div>
          <template v-if="selection.admin_email">
            <div><dt>First admin</dt><dd>{{ selection.admin_email }}</dd></div>
            <div><dt>Invitation status</dt><dd>{{ pretty(selection.invitation_status) }}</dd></div>
            <div><dt>Email delivery</dt><dd>{{ pretty(selection.delivery_status) }}</dd></div>
          </template>
        </dl>
        <div class="owner-actions">
          <button class="btn" :disabled="busy" @click="showEditor(edit)">Edit details</button>
          <button v-if="selection.status === 'ACTIVE'" class="btn danger" :disabled="busy" @click="change('suspend')">Suspend company</button>
          <button v-if="selection.status === 'SUSPENDED'" class="btn" :disabled="busy" @click="change('reactivate')">Reactivate company</button>
          <button v-if="selection.status === 'PENDING'" class="btn" :disabled="busy" @click="change('invitation/resend')">Resend invitation</button>
          <button v-if="selection.invitation_status === 'PENDING'" class="btn" :disabled="busy" @click="change('invitation/revoke')">Revoke invitation</button>
        </div>
      </section>

      <div class="owner-grid">
        <section class="card owner-directory" aria-labelledby="company-directory">
          <header class="owner-panel-heading">
            <h2 id="company-directory">Company directory</h2>
            <span class="muted">{{ results.totalElements }} {{ results.totalElements === 1 ? 'company' : 'companies' }}</span>
          </header>
          <form class="owner-filters" @submit.prevent="run(() => load())">
            <input v-model="search" class="search" type="search" aria-label="Search companies" placeholder="Search company or code" />
            <select v-model="status" aria-label="Company status">
              <option value="">All statuses</option>
              <option value="PENDING">Pending</option>
              <option value="ACTIVE">Active</option>
              <option value="SUSPENDED">Suspended</option>
            </select>
            <button class="btn" :disabled="busy">Apply filters</button>
          </form>
          <div class="table-wrapper" tabindex="0" role="region" aria-label="Companies table">
            <table class="data-table">
              <thead>
                <tr>
                  <th scope="col">Company</th>
                  <th scope="col" class="owner-status">Status</th>
                  <th scope="col" class="actions-col">Details</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="company in results.content" :key="company.id">
                  <td><strong>{{ company.name }}</strong><span class="company-code muted">{{ company.code }}</span></td>
                  <td><span class="badge" :class="company.status.toLowerCase()">{{ pretty(company.status) }}</span></td>
                  <td class="actions"><button class="btn small" :aria-label="`View ${company.name}`" :disabled="busy" @click="run(() => showCompany(company.id))">View</button></td>
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
            <button class="btn small" :disabled="busy || results.first" @click="run(() => load(results.page - 1))">Previous</button>
            <button class="btn small" :disabled="busy || results.last" @click="run(() => load(results.page + 1))">Next</button>
          </div>
        </section>

        <section class="card owner-activity" aria-labelledby="recent-activity">
          <h2 id="recent-activity">Recent activity</h2>
          <ul v-if="dashboard.activity.length">
            <li v-for="(event, index) in dashboard.activity" :key="index">
              <strong>{{ pretty(event.action) }}</strong>
              <span v-if="event.company_name">{{ event.company_name }}</span>
              <time :datetime="event.occurred_at">{{ new Date(event.occurred_at).toLocaleString() }}</time>
            </li>
          </ul>
          <p v-else class="muted">No activity yet.</p>
        </section>
      </div>
    </template>
  </main>
</template>

<style scoped>
.owner-console { max-width: 1360px; }
.owner-header, .owner-panel-heading {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px 16px;
}
.owner-header { margin-bottom: 24px; }
.owner-header h1 { font-size: clamp(26px, 4vw, 38px); line-height: 1.12; }
.owner-section { margin: 0 0 4px; color: var(--primary-dark); font-size: 13px; font-weight: 650; }
.owner-actions, .owner-pagination { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.owner-actions a { text-decoration: none; }
.owner-stats { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-bottom: 20px; }
.owner-metric { min-width: 0; padding: 16px; border-top: 3px solid var(--primary); }
.owner-metric[data-status="active"] { border-top-color: var(--success); }
.owner-metric[data-status="pending"] { border-top-color: var(--warn); }
.owner-metric[data-status="suspended"] { border-top-color: var(--danger); }
.owner-grid { display: grid; grid-template-columns: minmax(0, 2.1fr) minmax(250px, 1fr); gap: 16px; align-items: start; }
.owner-directory { min-width: 0; overflow: hidden; }
.owner-directory .owner-panel-heading { padding: 14px var(--table-cell-inline-padding) 6px; }
.owner-panel-heading h2 { margin: 0; }
.owner-filters {
  display: grid; grid-template-columns: minmax(0, 1fr) auto auto; align-items: stretch; gap: 8px;
  padding: 6px var(--table-cell-inline-padding) 14px;
}
.owner-filters input.search { width: 100%; }
.owner-editor-actions { margin-top: 16px; }
.owner-status { width: 1%; }
.company-code { display: block; font-size: 12px; }
.owner-pagination { justify-content: flex-end; padding: 12px var(--table-cell-inline-padding); border-top: 1px solid var(--line); }
.owner-editor, .owner-activity { min-width: 0; padding: 18px; }
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
.badge.suspended { background: var(--danger-soft); color: var(--danger-text); }
@media (max-width: 860px) { .owner-grid { grid-template-columns: minmax(0, 1fr); } }
@media (max-width: 520px) {
  .owner-stats { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .owner-filters { grid-template-columns: minmax(0, 1fr) auto; }
  .owner-filters input.search { grid-column: 1 / -1; }
  .owner-editor-actions > .primary { flex: 1 1 auto; }
}
</style>
