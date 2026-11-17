<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { login as loginRequest } from '../services/authService'
import { setSession } from '@/shared/auth'
import { errorMessage } from '@/shared/utils'

const router = useRouter()
const username = ref('')
const password = ref('')
const error = ref('')
const busy = ref(false)

async function login() {
  busy.value = true
  error.value = ''
  try {
    const data = await loginRequest(username.value, password.value)
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
  <div class="login-wrap">
    <section class="login-hero">
      <div class="brand">
        <span class="brand-mark">
          <svg viewBox="0 0 24 24" fill="none" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/></svg>
        </span>
        HRMS
      </div>
      <div>
        <h2>Everything your people need, in one place.</h2>
        <p>Manage employees, leave, attendance, payroll and performance from a single workspace.</p>
        <ul class="hero-points">
          <li>Leave and attendance tracking</li>
          <li>Payroll runs with printable payslips</li>
          <li>Recruitment through to onboarding</li>
        </ul>
      </div>
      <div class="hero-foot">© HRMS</div>
    </section>
    <section class="login-side">
      <form class="card login" @submit.prevent="login">
        <h1>Welcome back</h1>
        <p class="muted">Sign in to continue</p>
        <div v-if="error" class="alert">{{ error }}</div>
        <div class="field">
          <label for="username">Username</label>
          <input id="username" v-model="username" type="text" autocomplete="username" required autofocus />
        </div>
        <div class="field">
          <label for="password">Password</label>
          <input id="password" v-model="password" type="password" autocomplete="current-password" required />
        </div>
        <button class="btn primary block" type="submit" :disabled="busy">{{ busy ? 'Signing in…' : 'Sign in' }}</button>
      </form>
    </section>
  </div>
</template>
