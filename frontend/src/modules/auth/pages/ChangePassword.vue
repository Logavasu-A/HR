<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getProfile } from '@/modules/selfservice/services/selfService'
import { auth, can, isEmployee } from '@/shared/auth'
import { changePassword, logout } from '../services/authService'
import { errorMessage, pretty } from '@/shared/utils'

const router = useRouter()
const profile = ref(null)
const profileError = ref('')
const now = ref(Date.now())
let clock

const name = computed(() => profile.value?.fullName || auth.user?.employeeName || auth.user?.username || 'Account')
const initials = computed(() => name.value.trim().split(/\s+/).slice(0, 2).map((word) => word[0]).join('').toUpperCase())
const contact = computed(() => [
  ['Username', auth.user?.username],
  ['Work email', profile.value?.email || auth.user?.email],
  ['Phone', profile.value?.phone],
  ['Employee code', profile.value?.employeeCode || auth.user?.employeeCode],
])
const organization = computed(() => [
  ['Department', profile.value?.department?.name],
  ['Designation', profile.value?.designation?.name],
  ['Branch', profile.value?.branch?.name],
  ['Reports to', profile.value?.reportsTo?.fullName],
  ['Date joined', profile.value?.dateOfJoining],
  ['Holiday calendar', profile.value?.holidayList?.name],
])
const modules = Object.entries({
  dashboard: 'Dashboard', core: 'Core HR', leave: 'Leave', attendance: 'Attendance',
  payroll: 'Payroll', recruitment: 'Recruitment', expense: 'Expenses',
  performance: 'Performance', admin: 'Administration',
})
const access = computed(() => modules.map(([key, label]) => ({
  label,
  actions: ['read', 'write', 'approve'].filter((action) => can(`${key}:${action}`)),
})))
const session = computed(() => {
  try {
    const payload = auth.token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    const { iat, exp } = JSON.parse(atob(payload))
    if (!Number.isFinite(iat) || !Number.isFinite(exp) || exp <= iat) return null
    const issued = iat * 1000
    const expires = exp * 1000
    return { issued, expires, remaining: Math.min(1, Math.max(0, (expires - now.value) / (expires - issued))) }
  } catch {
    return null
  }
})
const dateTime = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })

async function signOut() {
  await logout().catch(() => {}) // Local sign-out also works when the server is unreachable.
  router.replace('/login')
}

onMounted(async () => {
  clock = setInterval(() => { now.value = Date.now() }, 1000)
  if (!isEmployee()) return
  try {
    const { data } = await getProfile()
    profile.value = data
  } catch (e) {
    profileError.value = errorMessage(e)
  }
})
onUnmounted(() => clearInterval(clock))

const currentPassword = ref('')
const newPassword = ref('')
const repeat = ref('')
const error = ref('')
const done = ref(false)
const busy = ref(false)
const visible = ref({})
const passwordFields = [
  { key: 'current', label: 'Current password', value: currentPassword, placeholder: 'Enter current password' },
  { key: 'new', label: 'New password', value: newPassword, placeholder: 'At least 8 characters' },
  { key: 'repeat', label: 'Confirm new password', value: repeat, placeholder: 'Re-enter new password' },
]

async function save() {
  error.value = ''
  done.value = false
  if (newPassword.value !== repeat.value) {
    error.value = 'The two new passwords do not match'
    return
  }
  busy.value = true
  try {
    await changePassword(currentPassword.value, newPassword.value)
    done.value = true
    currentPassword.value = ''
    newPassword.value = ''
    repeat.value = ''
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="account-page">
    <div class="page-head">
      <h1>Account &amp; security</h1>
      <div class="account-actions">
        <router-link v-if="isEmployee()" class="btn small" to="/my">My space</router-link>
        <button class="btn small danger" type="button" @click="signOut">Sign out</button>
      </div>
    </div>

    <section class="card account-banner" aria-label="Account summary">
      <div class="account-identity">
        <span class="avatar account-avatar">{{ initials }}</span>
        <div>
          <div class="account-name">
            <h2>{{ name }}</h2>
            <span v-if="profile?.status" class="badge" :class="profile.status.toLowerCase()">{{ pretty(profile.status) }}</span>
            <span v-if="auth.user?.role" class="badge">{{ pretty(auth.user.role) }}</span>
          </div>
          <div class="muted">{{ [profile?.designation?.name, profile?.department?.name, profile?.branch?.name].filter(Boolean).join(' · ') || auth.user?.username }}</div>
        </div>
      </div>
      <dl v-if="profile" class="account-summary">
        <div v-if="profile.dateOfJoining"><dt>Joined</dt><dd>{{ profile.dateOfJoining }}</dd></div>
        <div v-if="profile.employmentType"><dt>Type</dt><dd>{{ pretty(profile.employmentType) }}</dd></div>
        <div v-if="profile.branch?.name"><dt>Branch</dt><dd>{{ profile.branch.name }}</dd></div>
      </dl>
    </section>

    <div class="account-grid">
      <div class="account-stack">
        <section class="card account-panel">
          <h2>Account information</h2>
          <p v-if="profileError" class="alert" role="status">{{ profileError }}</p>
          <h3 class="account-label">Contact details</h3>
          <dl class="account-details">
            <div v-for="[label, value] in contact" :key="label"><dt>{{ label }}</dt><dd>{{ value || '—' }}</dd></div>
          </dl>
          <template v-if="profile">
            <h3 class="account-label">Organization</h3>
            <dl class="account-details">
              <div v-for="[label, value] in organization" :key="label"><dt>{{ label }}</dt><dd>{{ value || '—' }}</dd></div>
            </dl>
          </template>
        </section>

        <section class="card account-panel">
          <div class="account-panel-heading">
            <h2>Role &amp; permissions</h2>
            <span v-if="auth.user?.role" class="badge">{{ pretty(auth.user.role) }}</span>
          </div>
          <ul class="account-permissions">
            <li v-for="module in access" :key="module.label">
              <strong>{{ module.label }}</strong>
              <div class="account-permission-tags">
                <span v-for="action in module.actions" :key="action" class="badge" :class="action === 'approve' ? 'approved' : action === 'write' ? 'update' : ''">{{ pretty(action) }}</span>
                <span v-if="!module.actions.length" class="muted">No access</span>
              </div>
            </li>
          </ul>
        </section>
      </div>

      <div class="account-stack">
        <form class="card account-panel" @submit.prevent="save">
          <h2>Change password</h2>
          <div v-if="error" class="alert">{{ error }}</div>
          <div v-if="done" class="alert ok">Password changed.</div>
          <div v-for="field in passwordFields" :key="field.key" class="field">
            <label :for="`cp-${field.key}`">{{ field.label }} <span class="req">*</span></label>
            <div class="password-input">
              <input
                :id="`cp-${field.key}`" v-model="field.value.value"
                :type="visible[field.key] ? 'text' : 'password'"
                :autocomplete="field.key === 'current' ? 'current-password' : 'new-password'"
                :minlength="field.key === 'current' ? undefined : 8" :placeholder="field.placeholder" required
              />
              <button type="button" class="password-toggle" :aria-label="`${visible[field.key] ? 'Hide' : 'Show'} ${field.label.toLowerCase()}`" @click="visible[field.key] = !visible[field.key]">
                {{ visible[field.key] ? 'Hide' : 'Show' }}
              </button>
            </div>
          </div>
          <div class="account-actions password-submit">
            <button class="btn small primary" type="submit" :disabled="busy">{{ busy ? 'Saving…' : 'Update password' }}</button>
          </div>
        </form>

        <section class="card account-panel">
          <div class="account-panel-heading">
            <h2>Current session</h2>
            <span v-if="session" class="badge" :class="session.remaining ? 'active' : 'closed'">{{ session.remaining ? 'Active' : 'Expired' }}</span>
          </div>
          <template v-if="session">
            <progress class="session-progress" :value="session.remaining" max="1" aria-label="Session time remaining"></progress>
            <dl class="session-details">
              <dt>Signed in as</dt><dd>{{ auth.user?.username }}</dd>
              <dt>Signed in</dt><dd>{{ dateTime.format(session.issued) }}</dd>
              <dt>Expires</dt><dd>{{ dateTime.format(session.expires) }}</dd>
            </dl>
          </template>
          <p v-else class="muted">Session timing is unavailable.</p>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
.account-page .page-head,
.account-banner,
.account-panel-heading,
.account-permissions li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.account-actions, .account-name, .account-permission-tags {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.account-actions a { text-decoration: none; }
.account-banner {
  flex-wrap: wrap;
  padding: 20px;
  margin-bottom: 16px;
  background: linear-gradient(110deg, #f3f0ff, var(--panel) 55%, #ebfafc);
}
.account-identity { display: flex; align-items: center; gap: 14px; min-width: 0; overflow-wrap: anywhere; }
.account-avatar { width: 52px; height: 52px; font-size: 18px; }
.account-name h2, .account-panel-heading h2 { margin: 0; }
.account-summary, .account-details { display: grid; gap: 8px; margin: 0; }
.account-summary { display: flex; flex-wrap: wrap; }
.account-summary > div, .account-details > div {
  min-width: 0;
  padding: 9px 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-sm);
  background: var(--bg);
}
.account-summary > div { background: var(--panel); }
.account-grid { display: grid; grid-template-columns: minmax(0, 1.28fr) minmax(0, 1fr); gap: 16px; align-items: start; }
.account-stack { display: grid; gap: 16px; min-width: 0; }
.account-panel { padding: 18px; min-width: 0; }
.account-panel-heading, .account-panel > h2 {
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--line);
}
.account-label { font-size: 12px; color: var(--muted); margin: 14px 0 8px; }
.account-details { grid-template-columns: repeat(2, minmax(0, 1fr)); }
dt { color: var(--muted); font-size: 12px; }
dd { margin: 2px 0 0; font-weight: 600; overflow-wrap: anywhere; }
.account-permissions { padding: 0; margin: 0; list-style: none; border: 1px solid var(--line); border-radius: var(--radius-sm); }
.account-permissions li { min-height: 38px; padding: 6px 10px; flex-wrap: wrap; }
.account-permissions li + li { border-top: 1px solid var(--line); }
.account-permissions li:nth-child(even) { background: var(--bg); }
.account-permissions .badge { padding-inline: 7px; }
.account-permissions .badge::before { display: none; }
.password-input { position: relative; }
.password-input input { padding-right: 64px; }
.password-toggle {
  position: absolute;
  inset: 3px 4px 3px auto;
  border: 0;
  border-radius: 5px;
  padding: 0 8px;
  background: transparent;
  color: var(--primary-dark);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}
.password-toggle:hover { background: var(--primary-soft); }
.password-submit { justify-content: flex-end; border-top: 1px solid var(--line); padding-top: 12px; }
.session-progress { display: block; width: 100%; height: 8px; accent-color: #0891b2; }
.session-details { display: grid; grid-template-columns: auto minmax(0, 1fr); gap: 5px 18px; margin: 12px 0 0; }
.session-details dd { margin: 0; }

@media (max-width: 760px) {
  .account-grid { grid-template-columns: minmax(0, 1fr); }
}

@media (max-width: 430px) {
  .account-details { grid-template-columns: minmax(0, 1fr); }
  .account-panel, .account-banner { padding: 14px; }
  .password-submit .btn { width: 100%; }
}
</style>
