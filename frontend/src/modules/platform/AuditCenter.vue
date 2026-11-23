<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { pretty } from '@/shared/utils'
import PlatformState from './PlatformState.vue'
import PlatformPageHeader from './PlatformPageHeader.vue'

const router = useRouter()
const filters = reactive({ companyId: '', source: '', outcome: '', search: '', from: '', until: '' })
const applied = ref({})
const companies = ref([])
const rows = ref([])
const page = ref(0)
const requestedPage = ref(0)
const next = ref(false)
const snapshot = ref('')
const loading = ref(false)
const error = ref('')
const detail = ref(null)
const detailHeading = ref(null)
let detailTrigger
const history = ref([])
const historyBusy = ref(false)
const historyError = ref('')
let activeRequest
let historyRequest
const heading = computed(() => detail.value?.approval_id ? 'Approval history' : 'Event details')
const formatDate = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'medium' })

function startOfDay(value, end = false) {
  if (!value) return ''
  const date = new Date(value + 'T00:00:00')
  if (end) date.setDate(date.getDate() + 1)
  return date.toISOString()
}

async function fetchEvents(params, signal) {
  const token = sessionStorage.getItem('hrms_platform_token')
  if (!token) {
    await router.replace('/platform/login')
    throw new Error('Sign in to view the Audit Center.')
  }
  const query = new URLSearchParams(Object.entries(params).filter(([, value]) => value !== '' && value != null))
  const response = await fetch('/api/platform/audit?' + query, {
    signal, cache: 'no-store', headers: { Authorization: `Bearer ${token}` },
  })
  if (response.status === 401) {
    if (sessionStorage.getItem('hrms_platform_token') === token) sessionStorage.removeItem('hrms_platform_token')
    await router.replace('/platform/login')
  }
  const data = await response.json().catch(() => ({}))
  if (!response.ok) throw new Error(response.status === 403 ? 'Access denied. This page requires Global Super Admin.'
    : data.message || 'Audit events could not be loaded.')
  return data
}

async function load(index) {
  requestedPage.value = index
  closeDetail()
  activeRequest?.abort()
  const request = new AbortController()
  activeRequest = request
  loading.value = true
  error.value = ''
  try {
    const data = await fetchEvents({ ...applied.value, page: index, until: snapshot.value || applied.value.until }, request.signal)
    if (request.signal.aborted) return
    rows.value = data.events
    if (index === 0) companies.value = data.companies
    snapshot.value = data.snapshotEnd
    page.value = data.page
    next.value = data.hasNext
  } catch (failure) {
    if (!request.signal.aborted) error.value = failure.message
  } finally {
    if (!request.signal.aborted) loading.value = false
  }
}

function apply() {
  closeDetail()
  rows.value = []
  next.value = false
  page.value = 0
  applied.value = { ...filters, search: filters.search.trim(), from: startOfDay(filters.from), until: startOfDay(filters.until, true) }
  snapshot.value = ''
  return load(0)
}

function reset() {
  Object.assign(filters, { companyId: '', source: '', outcome: '', search: '', from: '', until: '' })
  apply()
}

function closeDetail() {
  historyRequest?.abort()
  detail.value = null
  history.value = []
}

async function openDetail(event, trigger) {
  closeDetail()
  detailTrigger = trigger
  detail.value = event
  await nextTick()
  detailHeading.value?.focus()
  if (!event.approval_id) return
  const request = new AbortController()
  historyRequest = request
  historyBusy.value = true
  historyError.value = ''
  const end = new Date(snapshot.value)
  const start = new Date(end)
  start.setDate(start.getDate() - 365)
  try {
    const result = await fetchEvents({ approvalId: event.approval_id, source: 'FINANCE',
      companyId: event.tenant_id, from: start.toISOString(), until: end.toISOString() }, request.signal)
    if (!request.signal.aborted) history.value = [...result.events].reverse()
  } catch (failure) {
    if (!request.signal.aborted) historyError.value = failure.message
  } finally {
    if (!request.signal.aborted) historyBusy.value = false
  }
}

function dismissDetail() {
  closeDetail()
  detailTrigger?.focus()
  detailTrigger = null
}

onMounted(apply)
onBeforeUnmount(() => {
  activeRequest?.abort()
  historyRequest?.abort()
})
</script>

<template>
  <section class="audit-center">
    <PlatformPageHeader title="Audit Center">
        <button class="btn" type="button" :disabled="loading" @click="apply">Refresh</button>
    </PlatformPageHeader>
    <form class="card audit-filters" aria-label="Audit filters" @submit.prevent="apply">
      <div class="field"><label for="audit-search">Search</label>
        <input id="audit-search" v-model="filters.search" placeholder="Action, actor, or request ID" maxlength="80" />
      </div>
      <div class="field"><label for="audit-company">Company</label>
        <select id="audit-company" v-model="filters.companyId"><option value="">All companies</option>
          <option v-for="company in companies" :key="company.id" :value="company.id">{{ company.name }}</option>
        </select>
      </div>
      <div class="field"><label for="audit-source">Source</label>
        <select id="audit-source" v-model="filters.source">
          <option value="">All sources</option><option value="PLATFORM">Platform</option>
          <option value="COMPANY">Company</option><option value="FINANCE">Finance</option>
        </select>
      </div>
      <div class="field"><label for="audit-outcome">Outcome</label>
        <select id="audit-outcome" v-model="filters.outcome">
          <option value="">All outcomes</option><option value="SUCCESS">Success</option>
          <option value="DENIED">Denied</option><option value="FAILED">Failed</option>
        </select>
      </div>
      <div class="field"><label for="audit-from">From</label><input id="audit-from" v-model="filters.from" type="date" /></div>
      <div class="field"><label for="audit-through">Through</label><input id="audit-through" v-model="filters.until" type="date" /></div>
      <div class="audit-actions">
        <button class="btn primary" :disabled="loading">Apply filters</button>
        <button class="btn" type="button" :disabled="loading" @click="reset">Reset</button>
      </div>
    </form>
    <PlatformState v-if="error" kind="error" title="Audit events could not be loaded" :message="error">
      <button class="btn small" type="button" @click="load(requestedPage)">Retry</button>
      <button class="btn small" type="button" @click="reset">Reset filters</button>
    </PlatformState>
    <section v-else class="card audit-log" :aria-busy="loading" aria-label="Audit events">
      <PlatformState v-if="loading" kind="loading" title="Loading audit events" />
      <PlatformState v-else-if="!rows.length" title="No audit events match your filters" message="Change the date range or reset the filters.">
        <button class="btn small" type="button" @click="reset">Reset filters</button>
      </PlatformState>
      <div v-else class="audit-scroll table-wrapper" tabindex="0" role="region" aria-label="Scrollable audit event table">
        <table class="data-table responsive-table data-table--records" role="table">
          <thead><tr><th scope="col">Time</th><th scope="col">Event</th><th scope="col">Actor</th>
            <th scope="col">Company</th><th scope="col">Outcome</th><th scope="col" class="actions-col">Actions</th></tr></thead>
          <tbody role="rowgroup">
            <tr v-for="event in rows" :key="event.key" role="row">
              <td data-label="Time" role="cell"><time class="table-cell-value" :datetime="event.occurred">{{ formatDate.format(new Date(event.occurred)) }}</time></td>
              <td data-label="Event" role="cell"><div class="table-cell-value"><strong>{{ pretty(event.action) }}</strong><div class="muted">{{ pretty(event.source) }} · {{ event.operation }}</div></div></td>
              <td data-label="Actor" role="cell"><div class="table-cell-value">{{ event.actor || 'System' }}<div v-if="event.actor_role" class="muted">{{ pretty(event.actor_role) }}</div></div></td>
              <td data-label="Company" role="cell"><span class="table-cell-value">{{ event.company || 'Platform' }}</span></td>
              <td data-label="Outcome" role="cell"><div class="table-cell-value"><span class="badge audit-status" :data-status="event.outcome">{{ pretty(event.outcome) }} · {{ event.status }}</span></div></td>
              <td class="actions" role="cell"><button class="btn small" type="button" @click="openDetail(event, $event.currentTarget)">{{ event.approval_id ? 'History' : 'View' }}</button></td>
            </tr>
          </tbody>
        </table>
      </div>
      <footer v-if="rows.length && !loading" class="audit-pagination">
        <span class="muted">Page {{ page + 1 }}</span>
        <button class="btn small" type="button" :disabled="loading || page === 0" @click="load(page - 1)">Previous</button>
        <button class="btn small" type="button" :disabled="loading || !next" @click="load(page + 1)">Next</button>
      </footer>
    </section>
    <section v-if="detail" class="card audit-detail" aria-labelledby="audit-detail-title">
      <header class="page-head audit-header"><h2 id="audit-detail-title" ref="detailHeading" tabindex="-1">{{ heading }}</h2>
        <button type="button" class="btn small" @click="dismissDetail">Close details</button></header>
      <dl>
        <dt>Operation</dt><dd>{{ detail.operation }}</dd>
        <dt>Actor ID</dt><dd>{{ detail.actor_id || 'Unavailable' }}</dd>
        <dt>Request ID</dt><dd>{{ detail.request_id || 'Unavailable' }}</dd>
        <dt v-if="detail.workspace">Workspace ID</dt><dd v-if="detail.workspace">{{ detail.workspace }}</dd>
        <dt v-if="detail.ip">IP address</dt><dd v-if="detail.ip">{{ detail.ip }}</dd>
        <template v-if="detail.approval_id">
          <dt>Approval ID</dt><dd>{{ detail.approval_id }}</dd>
          <dt>Current status</dt><dd>{{ pretty(detail.approval_state) }}</dd>
          <dt>Reason</dt><dd>{{ pretty(detail.reason) }}</dd>
          <dt>Requester</dt><dd>{{ detail.maker }}</dd>
          <dt>Reviewer</dt><dd>{{ detail.checker || 'Awaiting review' }}</dd>
        </template>
      </dl>
      <p v-if="historyBusy && detail.approval_id" role="status">Loading approval history…</p>
      <p v-if="historyError && detail.approval_id" class="alert" role="alert">{{ historyError }}</p>
      <ol v-if="history.length" class="audit-timeline">
        <li v-for="step in history" :key="step.key">
          <strong>{{ pretty(step.action) }}</strong>
          <div class="muted">{{ formatDate.format(new Date(step.occurred)) }} · {{ step.actor }}</div>
        </li>
      </ol>
    </section>
  </section>
</template>

<style scoped>
.audit-center { container-type: inline-size; }
.audit-header, .audit-actions, .audit-pagination { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.audit-header { justify-content: space-between; margin-bottom: 16px; }
.audit-header h1, .audit-header h2, .audit-header p { margin: 0; }
.audit-header p { margin-top: 4px; }
.audit-filters { display: grid; grid-template-columns: minmax(0, 1fr); gap: var(--space-12); padding: var(--platform-card-padding); margin-bottom: var(--space-16); }
.audit-filters .field { margin: 0; min-width: 0; }
.audit-filters input, .audit-filters select { inline-size: 100%; min-inline-size: 0; }
.audit-filters .audit-actions { grid-column: 1 / -1; }
.audit-scroll .muted { font-size: 0.82rem; margin-top: 3px; }
.audit-status[data-status=SUCCESS] { background: var(--ok-bg); color: var(--ok-text); }
.audit-status[data-status=DENIED] { background: var(--warn-bg); color: var(--warn-text); }
.audit-status[data-status=FAILED] { background: var(--bad-bg); color: var(--bad-text); }
.audit-pagination { justify-content: flex-end; padding: 12px 14px; }
.audit-log:has(.audit-scroll), .audit-detail { padding: var(--platform-card-padding); }
.audit-detail { margin-top: var(--space-16); }
.audit-detail dl { display: grid; grid-template-columns: minmax(0, 1fr); gap: 8px; }
.audit-detail dt { color: var(--muted); }
.audit-detail dd { margin: 0; overflow-wrap: anywhere; }
.audit-timeline { margin: 18px 0 0; padding: 0 0 0 14px; list-style: none; border-inline-start: 2px solid var(--line); }
.audit-timeline li { position: relative; padding: 7px 0; }
.audit-timeline li::before { content: ''; position: absolute; inset-inline-start: -19px; top: 13px; width: 8px; height: 8px; border-radius: 50%; background: var(--primary); }
@media (min-width: 768px) {
  .audit-filters { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .audit-detail dl { grid-template-columns: minmax(0, 10rem) minmax(0, 1fr); }
}
@media (max-width: 600px) {
  .audit-header .audit-actions { width: 100%; }
  .audit-header .btn, .audit-filters button, .audit-pagination button { min-height: var(--platform-control-large); }
  .audit-pagination { justify-content: space-between; }
}
</style>
