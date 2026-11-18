<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login as loginRequest } from '../services/authService'
import { setSession } from '@/shared/auth'
import { errorMessage } from '@/shared/utils'
import SignInLayout from '@/shared/components/SignInLayout.vue'

const router = useRouter()
const route = useRoute()
const companyCode = ref(typeof route.query.company === 'string' ? route.query.company : 'initial')
const username = ref('')
const password = ref('')
const error = ref('')
const busy = ref(false)

async function login() {
  busy.value = true
  error.value = ''
  try {
    const data = await loginRequest(companyCode.value, username.value, password.value)
    setSession(data.token, data.user)
    router.push('/')
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <SignInLayout title="Company sign-in" description="Enter your company code and account details.">
    <template #aside>
      <h2>Your company workspace</h2>
      <p>Manage employee records, attendance, leave and payroll in one place.</p>
    </template>
    <form :aria-busy="busy" @submit.prevent="login">
      <div v-if="error" class="alert" role="alert">{{ error }}</div>
      <div class="field">
        <label for="company-code">Company code</label>
        <input id="company-code" v-model.trim="companyCode" type="text" autocomplete="organization" maxlength="40" required />
      </div>
      <div class="field">
        <label for="username">Email or username</label>
        <input id="username" v-model="username" type="text" autocomplete="username" required />
      </div>
      <div class="field">
        <label for="password">Password</label>
        <input id="password" v-model="password" type="password" autocomplete="current-password" required />
      </div>
      <button class="btn primary block" type="submit" :disabled="busy">{{ busy ? 'Signing in…' : 'Sign in' }}</button>
    </form>
    <template #footer>
      <router-link to="/platform/login">Platform owner sign-in</router-link>
    </template>
  </SignInLayout>
</template>
