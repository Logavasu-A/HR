<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import SignInLayout from '@/shared/components/SignInLayout.vue'

const router = useRouter()
const username = ref('')
const password = ref('')
const error = ref('')
const busy = ref(false)

async function signIn() {
  error.value = ''
  busy.value = true
  try {
    const response = await fetch('/api/platform/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: username.value, password: password.value }),
    })
    const data = await response.json().catch(() => ({}))
    if (!response.ok || !data.token) throw new Error(data.message || 'Owner sign-in is unavailable.')
    sessionStorage.setItem('hrms_platform_token', data.token)
    await router.replace('/platform/companies')
  } catch (e) {
    error.value = e.message
  } finally {
    password.value = ''
    busy.value = false
  }
}
</script>

<template>
  <SignInLayout title="Platform owner" description="Sign in with your platform account.">
    <template #aside>
      <h2>Company administration</h2>
      <p>Manage company access and invitations from the owner console.</p>
    </template>
    <form :aria-busy="busy" @submit.prevent="signIn">
      <p v-if="error" class="alert" role="alert">{{ error }}</p>
      <div class="field">
        <label for="owner-username">Username</label>
        <input id="owner-username" v-model.trim="username" autocomplete="username" maxlength="80" required />
      </div>
      <div class="field">
        <label for="owner-password">Password</label>
        <input id="owner-password" v-model="password" type="password" autocomplete="current-password" maxlength="72" required />
      </div>
      <button class="btn primary block" :disabled="busy">{{ busy ? 'Signing in…' : 'Sign in' }}</button>
    </form>
    <template #footer>
      <router-link to="/login">Company sign-in</router-link>
    </template>
  </SignInLayout>
</template>
