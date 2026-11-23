<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import OwnerWorkspaceContent from './OwnerWorkspaceContent.vue'
import { createWorkspaceClient } from './workspaceClient.js'
import PlatformState from './PlatformState.vue'
import PlatformLayout from './PlatformLayout.vue'
import PlatformPageHeader from './PlatformPageHeader.vue'

const router = useRouter()
const route = useRoute()
const token = sessionStorage.getItem('hrms_platform_token')
const owner = ref('')
const companies = ref([])
const selectedCompany = ref('')
const companySearch = ref('')
const pickerOpen = ref(false)
const companySearchField = ref(null)
const closing = ref(false)
const reason = ref('')
const reasonField = ref(null)
const loading = ref(true)
const busy = ref(false)
const error = ref('')
const known = shallowRef(null)
const active = shallowRef(null)
let expiryTimer
let disposed = false

const companyMark = computed(() => active.value?.workspace.name.split(/\s+/).slice(0, 2)
  .map(word => word[0]).join('').toUpperCase())
const matches = computed(() => {
  const term = companySearch.value.trim().toLowerCase()
  return companies.value.filter(row => `${row.name} ${row.code} ${row.status}`.toLowerCase().includes(term))
})
const companyOptions = computed(() => companies.value.filter(row =>
  row.id === selectedCompany.value || matches.value.includes(row)))
const target = computed(() => companies.value.find(row => row.id === selectedCompany.value))
const layoutProps = computed(() => active.value ? {
  workspace: active.value.workspace,
  owner: owner.value,
  client: active.value.client,
  registryCompany: companies.value.find(company => company.id === active.value.workspace.companyId),
} : {})

async function togglePicker() {
  pickerOpen.value = !pickerOpen.value
  if (pickerOpen.value) {
    await nextTick()
    companySearchField.value?.focus()
  }
}

async function platform(path, method = 'GET', body) {
  const headers = { Authorization: `Bearer ${token}` }
  if (body) headers['Content-Type'] = 'application/json'
  const response = await fetch(`/api/platform${path}`, {
    method, headers, cache: 'no-store', body: body ? JSON.stringify(body) : undefined,
  })
  const data = await response.json().catch(() => null)
  if (!response.ok) {
    const failure = new Error(data?.message || 'The platform request could not be completed.')
    failure.status = response.status
    throw failure
  }
  return data
}

async function directory() {
  const first = await platform('/companies?sort=name-asc')
  const companies = [...first.content]
  for (let page = 1; page < first.totalPages; page++) {
    const next = await platform(`/companies?sort=name-asc&page=${page}`)
    companies.push(...next.content)
  }
  return companies
}

function retire() {
  active.value?.client.retire()
  active.value = null
  clearTimeout(expiryTimer)
}

function signIn() {
  if (sessionStorage.getItem('hrms_platform_token') === token) sessionStorage.removeItem('hrms_platform_token')
  router.replace('/platform/login')
}

async function invalidated(id, status) {
  if (active.value?.workspace.id !== id) return
  retire()
  if (status === 401) return signIn()
  error.value = 'This Admin View was replaced. Select a company and enter a reason to open it again.'
  try {
    const current = await platform('/workspace')
    if (!disposed && active.value === null && !busy.value) known.value = current
  } catch (failure) {
    if (failure.status === 401) signIn()
  }
}

function show(workspace) {
  const client = createWorkspaceClient({ token, id: workspace.id, onInvalid: (status) => invalidated(workspace.id, status) })
  known.value = workspace
  active.value = { workspace, client }
  selectedCompany.value = workspace.companyId
  companySearch.value = ''
  pickerOpen.value = false
  expiryTimer = setTimeout(() => invalidated(workspace.id, 401),
    Math.max(0, new Date(workspace.expiresAt).getTime() - Date.now()))
}

async function initialize() {
  loading.value = true
  error.value = ''
  try {
    if (!token) return signIn()
    const session = await platform('/session')
    if (!session.adminView) {
      error.value = 'Your platform account cannot open Admin View.'
      return
    }
    const [list, current] = await Promise.all([directory(), platform('/workspace')])
    if (disposed) return
    owner.value = session.username
    companies.value = list
    known.value = current
    const requested = route.query.company
    if (list.some(company => company.id === requested) && requested !== current?.companyId) {
      selectedCompany.value = requested
      await nextTick()
      reasonField.value?.focus()
    } else if (current) {
      show(current)
    }
  } catch (failure) {
    if (failure.status === 401) signIn()
    else error.value = failure.message
  } finally {
    loading.value = false
  }
}

async function enter() {
  if (busy.value || !selectedCompany.value || reason.value.trim().length < 5) return
  busy.value = true
  error.value = ''
  const expectedWorkspaceId = known.value?.id ?? null
  retire()
  try {
    const workspace = await platform('/workspace', 'PUT', {
      companyId: selectedCompany.value, expectedWorkspaceId, reason: reason.value.trim(),
    })
    if (disposed) return
    reason.value = ''
    known.value = workspace
    // A detail URL contains an employee ID from the previous company.
    await router.replace({ path: route.params.id ? '/platform/workspace/employees' : route.path })
    if (disposed) return
    show(workspace)
  } catch (failure) {
    if (failure.status === 401) signIn()
    else {
      pickerOpen.value = true
      error.value = failure.message
      if (failure.status === 410) known.value = await platform('/workspace').catch(() => null)
    }
  } finally {
    busy.value = false
  }
}

async function closeGrant() {
  const grant = known.value?.id
  retire()
  if (!grant) return
  try {
    await platform(`/workspace/${grant}`, 'DELETE')
    known.value = null
  } catch (failure) {
    if (failure.status === 401 || failure.status === 410) known.value = null
    else throw failure
  }
}

async function returnToTower() {
  closing.value = true
  busy.value = true
  try {
    await closeGrant()
    await router.push('/platform')
  } catch (failure) {
    error.value = failure.message
  } finally {
    closing.value = false
    busy.value = false
  }
}

onBeforeRouteLeave(async () => {
  if (!busy.value) await closeGrant().catch(() => {})
  else retire()
})
onBeforeUnmount(() => {
  disposed = true
  retire()
})
onMounted(initialize)
</script>

<template>
  <div class="owner-workspace">
    <component :is="active ? OwnerWorkspaceContent : PlatformLayout" :key="active?.workspace.id || 'select-company'" v-bind="layoutProps">
    <PlatformPageHeader v-if="!active" title="Company workspaces" />
    <header class="workspace-banner" :class="{ 'workspace-entry': !active }">
      <div v-if="active" class="workspace-heading">
        <div class="workspace-company">
          <span v-if="companyMark" class="workspace-initials" aria-hidden="true">{{ companyMark }}</span>
          <div>
            <p class="workspace-mode">Super Admin · Admin View<span v-if="active"> · {{ owner }}</span></p>
            <p class="workspace-name">{{ active?.workspace.name || 'Company workspace' }}</p>
          </div>
          <span v-if="active && active.workspace.status !== 'ACTIVE'" class="badge pending">Read-only</span>
        </div>
        <div class="workspace-actions">
          <button class="btn small" type="button" :disabled="busy" @click="returnToTower">Back to Control Tower</button>
          <button v-if="active" class="btn small picker-toggle" type="button" aria-controls="workspace-picker"
            :aria-expanded="pickerOpen" :disabled="busy" @click="togglePicker">
            {{ pickerOpen ? 'Close switcher' : 'Change company' }}
          </button>
        </div>
      </div>
      <h2 v-else class="workspace-entry-title">Open Admin View</h2>

      <form v-if="companies.length" v-show="!active || pickerOpen" id="workspace-picker" class="workspace-switch"
        :aria-busy="busy" @submit.prevent="enter">
        <div class="field">
          <label for="workspace-company-search">Find a company</label>
          <input id="workspace-company-search" ref="companySearchField" v-model="companySearch" type="search" placeholder="Name, code, or status" :disabled="busy" />
        </div>
        <div class="field">
          <label for="owner-workspace-company">Company</label>
          <select id="owner-workspace-company" v-model="selectedCompany" :disabled="busy" required>
            <option disabled value="">Select company</option>
            <option v-for="company in companyOptions" :key="company.id" :value="company.id">
              {{ company.name }}{{ company.status === 'ACTIVE' ? '' : ` (${company.status.toLowerCase()})` }}
            </option>
          </select>
        </div>
        <div class="field workspace-reason">
          <label for="owner-workspace-reason">Access reason</label>
          <input id="owner-workspace-reason" ref="reasonField" v-model="reason" :disabled="busy" required minlength="5" maxlength="300"
            placeholder="Describe why you need access" />
        </div>
        <button class="btn primary" :disabled="busy" type="submit">{{ active ? 'Switch company' : 'Open Admin View' }}</button>
        <small v-if="companySearch && !matches.length" class="muted workspace-hint" role="status">No companies match your search.</small>
        <small v-if="target && target.status !== 'ACTIVE'" class="muted workspace-hint">{{ target.name }} opens in read-only mode.</small>
      </form>
    </header>

    <div v-if="error || loading || busy || (!active && !companies.length)" class="workspace-state">
      <PlatformState v-if="error" kind="error" title="Admin View needs attention" :message="error">
        <button v-if="!companies.length" class="btn small" type="button" @click="initialize">Try again</button>
      </PlatformState>
      <PlatformState v-else-if="loading || busy" kind="loading"
        :title="loading ? 'Loading company directory' : closing ? 'Closing Admin View' : 'Opening ' + target?.name" />
      <PlatformState v-else title="No companies available" message="The company registry is empty.">
        <router-link class="btn small" to="/platform/companies">Company registry</router-link>
      </PlatformState>
    </div>

    </component>
  </div>
</template>

<style scoped>
.workspace-banner { padding: 12px max(var(--content-gutter), calc((100% - var(--content-width)) / 2 + var(--content-gutter))); background: var(--panel); border-bottom: 1px solid var(--line); }
.workspace-entry { margin: 0; padding: var(--platform-card-padding); border: 1px solid var(--platform-border); border-radius: var(--platform-radius); box-shadow: var(--shadow-sm); }
.workspace-entry-title { margin: 0; font-size: var(--platform-section-size); }
.workspace-heading, .workspace-company, .workspace-actions { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.workspace-heading { justify-content: space-between; }
.workspace-company { min-width: 0; }
.workspace-company > div { flex: 1; min-width: 0; }
.workspace-mode { color: var(--primary-dark); font-size: 12px; font-weight: 700; margin: 0; }
.workspace-name { font-size: 16px; font-weight: 700; margin: 0; overflow-wrap: anywhere; }
.workspace-initials { display: grid; place-items: center; width: 38px; height: 38px; flex: none; border-radius: 9px;
  background: var(--primary-tint); color: var(--primary-dark); font-weight: 700; }
.workspace-switch { display: grid; grid-template-columns: minmax(0, 1fr);
  align-items: start; gap: 12px; margin-top: 12px; padding-top: 12px; border-top: 1px solid var(--line); }
.workspace-switch .field { margin: 0; }
.workspace-switch .field, .workspace-switch :is(input, select) { min-width: 0; }
.workspace-switch :is(input, select) { width: 100%; }
.workspace-switch button { align-self: end; justify-self: start; min-height: var(--platform-control-default); }
.workspace-state { margin-top: 14px; }
.workspace-hint { grid-column: 1 / -1; }
@media (min-width: 768px) {
  .workspace-switch { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (width < 768px) {
  .workspace-actions { width: 100%; gap: var(--space-8); }
  .workspace-actions > .btn { flex: 1 1 auto; }
  .workspace-entry { padding: var(--space-16); }
  .workspace-switch button { justify-self: stretch; }
}
</style>
