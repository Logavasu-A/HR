<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { pretty } from '@/shared/utils'
import AppDialog from '@/shared/components/AppDialog.vue'
import PlatformPageHeader from './PlatformPageHeader.vue'
import PlatformState from './PlatformState.vue'

const router = useRouter()
const session = ref(null)
const accounts = ref([])
const accountSearch = ref('')
const accountStatus = ref('')
const filteredAccounts = computed(() => {
  const term = accountSearch.value.trim().toLowerCase()
  return accounts.value.filter(account => {
    const matchesStatus = !accountStatus.value || account.active === (accountStatus.value === 'active')
    return matchesStatus && [account.username, pretty(account.role || 'UNASSIGNED')]
      .some(value => value.toLowerCase().includes(term))
  })
})
const activity = ref([])
const historyDate = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'medium' })
const roleHistory = computed(() => activity.value.map(event => {
  // The audit endpoint stores the role transition and the reason in one field.
  const change = /^([A-Z_]+) → ([A-Z_]+) \(([A-Z_]+)\)\s+([\s\S]*)$/.exec(event.reason ?? '')
  const occurredAt = event.occurred_at ? new Date(event.occurred_at) : null
  return {
    id: event.id,
    account: target(event),
    actor: event.actor,
    occurredAt: event.occurred_at,
    timestamp: occurredAt && !Number.isNaN(occurredAt.getTime()) ? historyDate.format(occurredAt) : null,
    previousRole: change && pretty(change[1]),
    nextRole: change && pretty(change[2]),
    assigned: change && change[2] !== 'UNASSIGNED',
    actorRole: change && pretty(change[3]),
    reason: change ? change[4] : event.reason,
  }
}))
const selected = ref(null)
const role = ref('')
const reason = ref('')
const busy = ref(false)
const error = ref('')
const notice = ref('')
const canSubmit = computed(() => selected.value && !busy.value && reason.value
  && role.value !== (selected.value.role || 'UNASSIGNED'))

async function request(path, method = 'GET', body) {
  const token = sessionStorage.getItem('hrms_platform_token')
  const response = await fetch('/api/platform' + path, {
    method, cache: 'no-store', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: body && JSON.stringify(body),
  })
  if (response.status === 401) {
    if (sessionStorage.getItem('hrms_platform_token') === token) sessionStorage.removeItem('hrms_platform_token')
    await router.replace('/platform/login')
  }
  const payload = await response.json().catch(() => null)
  if (!response.ok) throw new Error(payload?.message || (response.status === 403 ? 'Access denied.' : 'Request failed.'))
  return payload
}

async function run(action) {
  error.value = ''
  busy.value = true
  try {
    await action()
  } catch (failure) {
    error.value = failure.message
  } finally {
    busy.value = false
  }
}

async function refresh() {
  const [rows, audit] = await Promise.all([request('/access'), request('/activity?action=PLATFORM_ROLE_CHANGE')])
  accounts.value = rows
  activity.value = audit.events.content
}

function editable(account) {
  return account.username !== session.value.username && account.role !== 'GLOBAL_SUPER_ADMIN'
    && (session.value.role === 'GLOBAL_SUPER_ADMIN' || account.role !== 'SECURITY_ADMINISTRATOR')
}

function edit(account) {
  error.value = ''
  selected.value = account
  role.value = account.role || 'UNASSIGNED'
  reason.value = ''
  notice.value = ''
}

function dismissRoleChange() {
  if (busy.value) return
  selected.value = null
  error.value = ''
}

function target(event) {
  const id = event.request_path?.split('/').at(-1)
  return accounts.value.find(account => account.id === id)?.username || id
}

function submit() {
  if (!canSubmit.value) return
  return run(async () => {
    await request('/access/' + selected.value.id, 'PUT', { role: role.value, reason: reason.value })
    notice.value = 'Platform role updated.'
    selected.value = null
    await refresh()
  })
}

onMounted(() => run(async () => {
  session.value = await request('/session')
  if (!session.value.accessCenter) throw new Error('Access denied. Your role does not have access to this page.')
  await refresh()
}))
</script>

<template>
  <section class="access-center">
    <PlatformPageHeader title="Platform Access Center">
      <button v-if="session?.accessCenter" class="btn" type="button" :disabled="busy" @click="run(refresh)">Refresh</button>
    </PlatformPageHeader>
    <PlatformState v-if="error && !selected" kind="error" title="Access request could not be completed" :message="error" />
    <p v-if="notice" class="alert ok" role="status">{{ notice }}</p>
    <PlatformState v-if="busy && !accounts.length" kind="loading" title="Loading platform access" />
    <template v-if="session?.accessCenter">
      <section class="card access-card">
        <div class="access-table-heading">
          <h2>Platform accounts</h2>
          <span class="muted">{{ filteredAccounts.length }} of {{ accounts.length }} accounts</span>
        </div>
        <div class="access-filters" role="group" aria-label="Filter platform accounts">
          <div class="field">
            <label for="access-search">Search accounts</label>
            <input id="access-search" v-model="accountSearch" type="search" placeholder="Username or role" />
          </div>
          <div class="field">
            <label for="access-status">Status</label>
            <select id="access-status" v-model="accountStatus">
              <option value="">All statuses</option>
              <option value="active">Active</option>
              <option value="inactive">Inactive</option>
            </select>
          </div>
        </div>
        <div class="table-card"><div class="access-table table-wrapper" tabindex="0" role="region" aria-label="Platform accounts">
          <table class="data-table responsive-table data-table--records" role="table">
            <thead><tr><th scope="col">Account</th><th scope="col">Status</th><th scope="col">Role</th><th scope="col" class="actions-col">Actions</th></tr></thead>
            <tbody role="rowgroup">
              <tr v-for="account in filteredAccounts" :key="account.id" role="row">
                <td data-label="Account" role="cell"><strong class="table-cell-value">{{ account.username }}</strong></td>
                <td data-label="Status" role="cell"><div class="table-cell-value"><span class="badge" :class="account.active ? 'active' : 'inactive'">{{ account.active ? 'Active' : 'Inactive' }}</span></div></td>
                <td data-label="Role" role="cell"><div class="table-cell-value"><span class="access-role" :class="{ 'access-role-assigned': account.role && account.role !== 'UNASSIGNED' }">{{ account.role ? pretty(account.role) : 'Unassigned' }}</span></div></td>
                <td class="actions" role="cell"><button v-if="editable(account)" class="btn small" type="button" :disabled="busy"
                  :aria-label="`Change role for ${account.username}`" aria-haspopup="dialog" @click="edit(account)">Change role</button></td>
              </tr>
              <tr v-if="!filteredAccounts.length" class="table-state-row">
                <td class="empty" colspan="4"><div class="table-state-message">{{ busy ? 'Loading accounts…' : 'No accounts match these filters.' }}</div></td>
              </tr>
            </tbody>
          </table>
        </div></div>
      </section>
      <section class="card access-card access-history" aria-labelledby="role-history-heading">
        <h2 id="role-history-heading">Recent role changes</h2>
        <p v-if="!roleHistory.length" class="access-history-state muted" role="status">
          {{ busy ? 'Loading role changes…' : 'No role changes recorded.' }}
        </p>
        <ul v-else class="access-activity" role="list">
          <li v-for="event in roleHistory" :key="event.id" class="access-history-entry">
            <div class="access-history-change">
              <strong v-if="event.account" class="access-history-account">{{ event.account }}</strong>
              <div v-if="event.nextRole" class="access-role-transition">
                <span class="access-role">{{ event.previousRole }}</span>
                <span class="access-role-arrow" aria-label="changed to" role="img">→</span>
                <span class="access-role" :class="{ 'access-role-assigned': event.assigned }">{{ event.nextRole }}</span>
              </div>
              <p v-if="event.reason" class="access-history-reason">{{ event.reason }}</p>
            </div>
            <div class="access-history-meta">
              <div v-if="event.actor" class="access-history-actor">
                <span>Changed by <strong>{{ event.actor }}</strong></span>
                <span v-if="event.actorRole" class="muted">{{ event.actorRole }}</span>
              </div>
              <time v-if="event.timestamp" :datetime="event.occurredAt">{{ event.timestamp }}</time>
            </div>
          </li>
        </ul>
      </section>
      <AppDialog v-if="selected" :title="`Change role for ${selected.username}`" form
        @dismiss="dismissRoleChange" @submit="submit">
        <div class="role-change-fields" :aria-busy="busy">
          <div class="role-change-transition">
            <dl class="role-change-current">
              <dt>Current role</dt>
              <dd>{{ pretty(selected.role || 'UNASSIGNED') }}</dd>
            </dl>
            <span class="role-change-arrow" aria-hidden="true">→</span>
            <div class="field">
              <label for="platform-role">New role</label>
              <select id="platform-role" v-model="role" :disabled="busy">
                <option value="UNASSIGNED">Unassigned</option>
                <option value="PLATFORM_ADMINISTRATOR">Platform administrator</option>
                <option v-if="session.role === 'GLOBAL_SUPER_ADMIN'" value="SECURITY_ADMINISTRATOR">Security administrator</option>
              </select>
            </div>
          </div>
          <div class="field">
            <label for="platform-reason">Reason for change</label>
            <textarea id="platform-reason" v-model.trim="reason" :disabled="busy" maxlength="200" rows="3"
              placeholder="Explain why this account needs a different role" aria-describedby="platform-reason-hint" required></textarea>
            <small id="platform-reason-hint" class="muted">The reason appears in Recent role changes.</small>
          </div>
          <p v-if="error" class="alert" role="alert">{{ error }}</p>
        </div>
        <template #footer>
          <button class="btn" type="button" :disabled="busy" @click="dismissRoleChange">Cancel</button>
          <button class="btn primary" type="submit" :disabled="!canSubmit">
            {{ busy ? 'Saving…' : 'Confirm role change' }}
          </button>
        </template>
      </AppDialog>
    </template>
  </section>
</template>

<style scoped>
.access-table th,
.access-table td {
  text-align: center !important;
  vertical-align: middle !important;
}
.access-table th { white-space: nowrap !important; }
.access-table td[data-label="Status"] { width: 8rem; }
.access-table th.actions-col,
.access-table td.actions { position: static !important; width: 9.5rem; }
.access-card { padding: var(--platform-card-padding); margin-bottom: var(--space-16); }
.access-card h2 { margin: 0 0 12px; }
.access-table-heading { display: flex; justify-content: space-between; gap: 8px; flex-wrap: wrap; }
.access-filters { display: grid; grid-template-columns: minmax(0, 1fr); gap: 12px; margin-bottom: 14px; }
.access-filters .field { margin: 0; min-width: 0; }
.access-filters input, .access-filters select { inline-size: 100%; min-inline-size: 0; }
@media (min-width: 768px) {
  .access-filters { grid-template-columns: minmax(0, 20rem) minmax(0, 10rem); }
}
:global(.modal:has(.role-change-fields)) { inline-size: min(35rem, calc(100% - 2 * var(--modal-gutter))); }
.role-change-fields { display: grid; gap: var(--space-16); }
.role-change-fields :is(.field, .alert) { min-inline-size: 0; margin: 0; }
.role-change-transition {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--space-8);
}
.role-change-current { display: grid; gap: var(--space-4); min-inline-size: 0; margin: 0; }
.role-change-current dt { color: var(--muted); font-size: 0.875rem; font-weight: 600; }
.role-change-current dd {
  display: flex;
  align-items: center;
  min-block-size: var(--platform-control-default);
  margin: 0;
  padding: var(--space-8) var(--space-12);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--page-bg);
  font-weight: 600;
  overflow-wrap: anywhere;
}
.role-change-arrow {
  display: grid;
  place-items: center;
  justify-self: start;
  inline-size: 1.75rem;
  block-size: 1.75rem;
  border-radius: 50%;
  background: var(--primary-tint);
  color: var(--primary-tint-text);
  font-weight: 700;
  transform: rotate(90deg);
}
.role-change-fields textarea { inline-size: 100%; resize: vertical; }
@media (min-width: 500px) {
  .role-change-transition {
    grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
    align-items: end;
    gap: var(--space-12);
  }
  .role-change-arrow { margin-block-end: calc((var(--platform-control-default) - 1.75rem) / 2); transform: none; }
}
.access-history { container-type: inline-size; }
.access-history h2 { margin-block-end: var(--space-16); }
.access-activity {
  padding: 0;
  margin: 0;
  list-style: none;
  border: 1px solid var(--platform-border);
  border-radius: var(--platform-radius-small);
}
.access-history-entry {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--space-12);
  padding: var(--space-16);
  overflow-wrap: anywhere;
}
.access-history-entry + .access-history-entry { border-block-start: 1px solid var(--platform-border); }
.access-history-change { display: grid; justify-items: start; align-content: start; gap: var(--space-8); }
.access-history-account { color: var(--platform-text); font-size: 0.9375rem; }
.access-role-transition { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-4) var(--space-8); }
.access-role {
  min-inline-size: 0;
  padding: var(--space-4) var(--space-8);
  border-radius: 999px;
  background: var(--platform-background);
  color: var(--platform-muted);
  font-size: 0.8125rem;
  font-weight: 600;
}
.access-role-assigned { background: var(--primary-tint); color: var(--primary-tint-text); }
.access-role-arrow { color: var(--platform-muted); }
.access-history-reason { margin: 0; color: var(--platform-muted); }
.access-history-meta {
  display: grid;
  align-content: start;
  gap: var(--space-4);
  color: var(--platform-muted);
  font-size: 0.8125rem;
}
.access-history-actor { display: grid; gap: var(--space-4); }
.access-history-actor strong { color: var(--platform-text); }
.access-history-state { margin: 0; padding: var(--space-16); border: 1px solid var(--platform-border); border-radius: var(--platform-radius-small); }
@container (min-width: 38rem) {
  .access-history-entry { grid-template-columns: minmax(0, 1fr) minmax(0, 14rem); gap: var(--space-24); }
  .access-history-meta { text-align: end; }
}
</style>
