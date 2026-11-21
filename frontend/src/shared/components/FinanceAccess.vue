<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useWorkspace } from '@/shared/workspaceContext'
import { financeChallenges } from '@/shared/financeAccess'
import { pretty } from '@/shared/utils'

const { api } = useWorkspace()
const dialog = ref(null)
const queue = ref(null)
const code = ref('')
const secret = ref('')
const reason = ref('AUTHORIZED_CHANGE')
const error = ref('')
const queueError = ref('')
const busy = ref(false)
const queueBusy = ref(false)
const rows = ref([])
let pending

function clear() {
  pending?.cancel()
  pending = null
  secret.value = ''
}

function challenge(event) {
  if (event.detail.api !== api) return
  event.preventDefault()
  if (pending) {
    event.detail.cancel()
    return
  }
  pending = event.detail
  code.value = pending.code
  secret.value = ''
  error.value = ''
  if (!dialog.value.open) dialog.value.showModal()
}

async function confirm() {
  const current = pending
  if (!current) return
  busy.value = true
  error.value = ''
  try {
    let headers
    if (code.value === 'STEP_UP_REQUIRED') {
      const { data } = await api.post('/sensitive/step-up', { password: secret.value })
      if (!data.verified) throw new Error('Verification failed or is temporarily locked.')
    } else {
      headers = { 'X-HRMS-Confirm': 'true', 'X-HRMS-Reason': reason.value }
    }
    if (pending !== current) return
    pending = null
    dialog.value.close()
    current.retry(headers)
  } catch (failure) {
    error.value = failure.response?.data?.message || failure.message
  } finally {
    secret.value = ''
    busy.value = false
  }
}

async function loadQueue() {
  queueBusy.value = true
  queueError.value = ''
  try {
    rows.value = (await api.get('/sensitive/approvals')).data
  } catch (failure) {
    queueError.value = failure.response?.status === 404 ? 'Financial approvals are unavailable in this workspace.'
      : failure.response?.data?.message || failure.message
  } finally {
    queueBusy.value = false
  }
}

function openQueue() {
  rows.value = []
  queue.value.showModal()
  loadQueue()
}

async function decide(row, approved) {
  if (!window.confirm(approved ? 'Approve this financial change?' : 'Reject this financial change?')) return
  queueBusy.value = true
  queueError.value = ''
  try {
    await api.post(`/sensitive/approvals/${row.id}/decision`, { approved })
    await loadQueue()
  } catch (failure) {
    queueError.value = failure.response?.data?.message || failure.message
  } finally {
    queueBusy.value = false
  }
}

onMounted(() => financeChallenges.addEventListener('finance-challenge', challenge))
onBeforeUnmount(() => {
  financeChallenges.removeEventListener('finance-challenge', challenge)
  clear()
})
</script>

<template>
  <button type="button" class="btn small" @click="openQueue">Financial approvals</button>
  <dialog ref="queue" class="finance-dialog" aria-label="Financial approvals">
    <header><h2>Financial approvals</h2><button type="button" class="btn small" @click="queue.close()">Close</button></header>
    <p v-if="queueError" class="alert" role="alert">{{ queueError }}</p>
    <p v-if="queueBusy" role="status">Loading financial approvals…</p>
    <p v-else-if="!rows.length && !queueError" class="muted">No financial approvals to show.</p>
    <ul v-if="rows.length">
      <li v-for="row in rows" :key="row.id">
        <strong>{{ row.method }} {{ row.path.replace('/api/', '') }}</strong>
        <div>{{ pretty(row.reason) }} · {{ pretty(row.status) }}</div>
        <div v-if="row.canDecide" class="finance-actions">
          <button class="btn small primary" :disabled="queueBusy" @click="decide(row, true)">Approve</button>
          <button class="btn small" :disabled="queueBusy" @click="decide(row, false)">Reject</button>
        </div>
        <p v-else-if="row.status === 'APPROVED'" class="muted">The requester can repeat the approved change.</p>
      </li>
    </ul>
  </dialog>
  <dialog ref="dialog" class="finance-dialog" aria-label="Confirm financial access" @cancel="clear">
    <form @submit.prevent="confirm">
      <h2>{{ code === 'STEP_UP_REQUIRED' ? 'Verify financial access' : 'Confirm financial change' }}</h2>
      <p v-if="error" class="alert" role="alert">{{ error }}</p>
      <div v-if="code === 'STEP_UP_REQUIRED'" class="field">
        <label for="finance-password">Current password</label>
        <input id="finance-password" v-model="secret" type="password" autocomplete="current-password" maxlength="72" required />
      </div>
      <div v-else class="field">
        <label for="finance-reason">Reason</label>
        <select id="finance-reason" v-model="reason">
          <option value="AUTHORIZED_CHANGE">Authorized change</option>
          <option value="REGULAR_PROCESSING">Regular processing</option>
          <option value="RECORD_CORRECTION">Record correction</option>
        </select>
      </div>
      <div class="finance-actions">
        <button class="btn primary" :disabled="busy">{{ code === 'STEP_UP_REQUIRED' ? 'Verify' : 'Confirm' }}</button>
        <button type="button" class="btn" @click="clear(); dialog.close()">Cancel</button>
      </div>
    </form>
  </dialog>
</template>

<style scoped>
.finance-dialog { width: min(560px, 92vw); max-height: 85vh; color: var(--text); background: var(--card-bg); border: 1px solid var(--line); border-radius: 12px; padding: 20px; }
.finance-dialog::backdrop { background: var(--scrim); }
.finance-dialog header { display: flex; justify-content: space-between; align-items: start; gap: 12px; }
.finance-dialog h2 { margin: 0 0 14px; }
.finance-dialog ul { list-style: none; padding: 0; }
.finance-dialog li { padding: 10px 0; border-top: 1px solid var(--line); overflow-wrap: anywhere; }
.finance-actions { display: flex; gap: 8px; margin-top: 10px; }
</style>
