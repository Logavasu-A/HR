<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { pretty } from '@/shared/utils'
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
const selected = ref(null)
const role = ref('')
const reason = ref('')
const busy = ref(false)
const error = ref('')
const notice = ref('')

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
  selected.value = account
  role.value = account.role || 'UNASSIGNED'
  reason.value = ''
  notice.value = ''
}

function target(event) {
  const id = event.request_path?.split('/').at(-1)
  return accounts.value.find(account => account.id === id)?.username || id
}

function submit() {
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
    <PlatformState v-if="error" kind="error" title="Access request could not be completed" :message="error" />
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
          <table class="data-table">
            <thead><tr><th scope="col">Account</th><th scope="col">Status</th><th scope="col">Role</th><th scope="col">Action</th></tr></thead>
            <tbody>
              <tr v-for="account in filteredAccounts" :key="account.id">
                <td><strong>{{ account.username }}</strong></td>
                <td><span class="badge" :class="account.active ? 'active' : 'inactive'">{{ account.active ? 'Active' : 'Inactive' }}</span></td>
                <td>{{ account.role ? pretty(account.role) : 'Unassigned' }}</td>
                <td><button v-if="editable(account)" class="btn small" type="button" :disabled="busy"
                  :aria-label="`Change role for ${account.username}`" @click="edit(account)">Change role</button></td>
              </tr>
              <tr v-if="!filteredAccounts.length" class="table-state-row">
                <td colspan="4"><div class="table-state-message">{{ busy ? 'Loading accounts…' : 'No accounts match these filters.' }}</div></td>
              </tr>
            </tbody>
          </table>
        </div></div>
        <form v-if="selected" class="access-form" @submit.prevent="submit">
          <h3>Change role for {{ selected.username }}</h3>
          <div class="field"><label for="platform-role">Role</label>
            <select id="platform-role" v-model="role">
              <option value="UNASSIGNED">Unassigned</option>
              <option value="PLATFORM_ADMINISTRATOR">Platform Administrator</option>
              <option v-if="session.role === 'GLOBAL_SUPER_ADMIN'" value="SECURITY_ADMINISTRATOR">Security Administrator</option>
            </select>
          </div>
          <div class="field"><label for="platform-reason">Reason for change</label>
            <input id="platform-reason" v-model.trim="reason" maxlength="200" required />
          </div>
          <div class="access-actions">
            <button class="btn primary" :disabled="busy || !reason || role === (selected.role || 'UNASSIGNED')">Confirm role change</button>
            <button class="btn" type="button" :disabled="busy" @click="selected = null">Cancel</button>
          </div>
        </form>
      </section>
      <section class="card access-card">
        <h2>Recent role changes</h2>
        <p v-if="!activity.length && !busy" class="muted">No role changes recorded.</p>
        <ul v-else class="access-activity">
          <li v-for="event in activity" :key="event.id">
            <strong>{{ event.actor }}</strong> · {{ new Date(event.occurred_at).toLocaleString() }}
            <div v-if="target(event)" class="muted">Account {{ target(event) }}</div>
            <div>{{ event.reason }}</div>
          </li>
        </ul>
      </section>
    </template>
  </section>
</template>

<style scoped>
.access-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.access-card { padding: var(--platform-card-padding); margin-bottom: var(--space-16); }
.access-card h2, .access-form h3 { margin: 0 0 12px; }
.access-table-heading { display: flex; justify-content: space-between; gap: 8px; flex-wrap: wrap; }
.access-filters { display: grid; grid-template-columns: minmax(0, 320px) minmax(0, 160px); gap: 12px; margin-bottom: 14px; }
.access-filters .field { margin: 0; min-width: 0; }
.access-filters input, .access-filters select { inline-size: 100%; min-inline-size: 0; }
@media (max-width: 560px) {
  .access-filters { grid-template-columns: minmax(0, 1fr); }
}
.access-table table { min-width: 440px; }
.access-form { max-width: 620px; margin-top: var(--space-16); padding: var(--platform-card-padding); border: 1px solid var(--platform-border); border-radius: var(--platform-radius); }
.access-activity { padding: 0; margin: 0; list-style: none; }
.access-activity li { padding: 12px 0; border-top: 1px solid var(--line); overflow-wrap: anywhere; }
</style>
