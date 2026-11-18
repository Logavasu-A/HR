<script setup>
import { ref } from 'vue'
import SignInLayout from '@/shared/components/SignInLayout.vue'

const token = window.location.hash.slice(1)
if (token) history.replaceState(history.state, '', window.location.pathname + window.location.search)
const password = ref('')
const repeat = ref('')
const companyCode = ref('')
const error = ref('')
const busy = ref(false)

async function accept() {
  error.value = ''
  if (password.value !== repeat.value) {
    error.value = 'Passwords must match.'
    return
  }
  busy.value = true
  try {
    const response = await fetch('/api/auth/accept-admin-invite', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token, password: password.value }),
    })
    const data = await response.json().catch(() => ({}))
    if (!response.ok) throw new Error(data.message || 'This invitation could not be accepted.')
    companyCode.value = data.companyCode
    password.value = ''
    repeat.value = ''
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <SignInLayout title="Set up your admin account">
    <template #aside>
      <h2>Company onboarding</h2>
      <p>Set a password to activate your company and sign in as its first administrator.</p>
    </template>
    <template v-if="companyCode">
      <p role="status">Your company is active. Sign in with company code <strong>{{ companyCode }}</strong> and your invitation email.</p>
      <router-link class="btn primary" :to="{ path: '/login', query: { company: companyCode } }">Sign in</router-link>
    </template>
    <form v-else-if="token" :aria-busy="busy" @submit.prevent="accept">
      <p v-if="error" class="alert" role="alert">{{ error }}</p>
      <div class="field">
        <label for="invite-password">Password (12 or more characters)</label>
        <input id="invite-password" v-model="password" type="password" minlength="12" maxlength="72" autocomplete="new-password" required />
      </div>
      <div class="field">
        <label for="invite-repeat">Confirm password</label>
        <input id="invite-repeat" v-model="repeat" type="password" autocomplete="new-password" required />
      </div>
      <button class="btn primary block" :disabled="busy">{{ busy ? 'Setting up…' : 'Accept invitation' }}</button>
    </form>
    <p v-else class="alert">Open the invitation link from your email to continue.</p>
    <template v-if="!companyCode" #footer>
      <router-link to="/login">Company sign-in</router-link>
    </template>
  </SignInLayout>
</template>
