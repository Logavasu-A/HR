<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { login as loginRequest } from '../services/authService'
import { setSession } from '@/shared/auth'
import { errorMessage } from '@/shared/utils'

const router = useRouter()
const route = useRoute()
const companyCode = ref(typeof route.query.company === 'string' ? route.query.company : 'initial')
const username = ref('')
const password = ref('')
const showPassword = ref(false)
const capsLockOn = ref(false)
const rememberMe = ref(true)
const error = ref('')
const busy = ref(false)

onMounted(() => {
  const saved = localStorage.getItem('hrms_remember_login')
  if (saved) {
    try {
      const parsed = JSON.parse(saved)
      if (parsed.companyCode) companyCode.value = parsed.companyCode
      if (parsed.username) username.value = parsed.username
    } catch {}
  }
})

function checkCapsLock(e) {
  if (e && typeof e.getModifierState === 'function') {
    capsLockOn.value = e.getModifierState('CapsLock')
  }
}

async function login() {
  busy.value = true
  error.value = ''
  try {
    const code = companyCode.value?.trim() || 'initial'
    const user = username.value?.trim() || ''

    if (rememberMe.value) {
      localStorage.setItem('hrms_remember_login', JSON.stringify({ companyCode: code, username: user }))
    } else {
      localStorage.removeItem('hrms_remember_login')
    }

    const data = await loginRequest(code, user, password.value)
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
  <main class="company-sign-in">
    <aside class="brand-panel" aria-labelledby="company-brand-title">
      <img class="company-logo" src="/hrms-logo-on-dark.svg" alt="HRMS People Platform" width="270" height="72" />

      <div class="brand-story">
        <h2 id="company-brand-title">Run your people operations from one place.</h2>

        <div class="workspace-preview" aria-label="Sample HRMS workspace">
          <div class="preview-card attendance-preview">
            <div class="preview-heading">
              <strong>Today at work</strong>
              <span class="preview-label">Preview</span>
            </div>
            <div class="attendance-preview-body">
              <svg class="attendance-ring" viewBox="0 0 104 104" role="img" aria-label="Example attendance, 92 percent present">
                <circle cx="52" cy="52" r="42" class="ring-track" />
                <circle cx="52" cy="52" r="42" class="ring-progress" pathLength="100" transform="rotate(-90 52 52)" />
                <text x="52" y="59" text-anchor="middle">92%</text>
              </svg>
              <ul class="attendance-legend">
                <li>Present</li>
                <li>On leave</li>
                <li>Absent</li>
              </ul>
            </div>
          </div>

          <div class="preview-card leave-preview">
            <div class="preview-row">
              <span class="preview-avatar" aria-hidden="true">AK</span>
              <div><strong>Leave request</strong><small>Casual leave, 2 days</small></div>
            </div>
            <span class="preview-status">Pending approval</span>
          </div>

          <div class="preview-card payroll-preview preview-row">
            <span class="preview-lock">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <rect x="5" y="11" width="14" height="9" rx="2" />
                <path d="M8 11V8a4 4 0 0 1 8 0v3" />
              </svg>
            </span>
            <div><strong>Payroll run</strong><small>Locked until verified</small></div>
            <svg class="payroll-trend" viewBox="0 0 64 28" aria-hidden="true">
              <path d="M2 22 14 16 26 19 38 9 50 12 62 3" />
            </svg>
          </div>
        </div>
      </div>

      <ul class="brand-features">
        <li>Role-based access</li>
        <li>Audit trail</li>
        <li>Password-locked payroll</li>
      </ul>
    </aside>

    <section class="sign-in-panel" aria-labelledby="company-sign-in-title">
      <div class="sign-in-content">
        <nav class="sign-in-switch" aria-label="Sign-in type">
          <router-link to="/login" aria-current="page">Company</router-link>
          <router-link to="/platform/login">Platform</router-link>
        </nav>
        <h1 id="company-sign-in-title">Welcome back</h1>

        <form class="auth-form" :aria-busy="busy" @submit.prevent="login">
          <p v-if="error" class="alert" role="alert">{{ error }}</p>
          <div class="sign-in-fields">
            <div class="field">
              <label for="company-code">Company code</label>
              <div class="input-icon-wrap">
                <span class="input-icon">
                  <svg viewBox="0 0 24 24" aria-hidden="true">
                    <rect x="4" y="3" width="16" height="18" rx="2" />
                    <path d="M9 8h2m2 0h2m-6 4h2m2 0h2m-5 9v-4h4v4" />
                  </svg>
                </span>
                <input id="company-code" v-model.trim="companyCode" type="text" autocomplete="organization"
                  placeholder="e.g. initial" maxlength="40" required />
              </div>
            </div>

            <div class="field">
              <label for="username">Email or username</label>
              <div class="input-icon-wrap">
                <span class="input-icon">
                  <svg viewBox="0 0 24 24" aria-hidden="true">
                    <circle cx="12" cy="8" r="4" />
                    <path d="M4 21a8 8 0 0 1 16 0" />
                  </svg>
                </span>
                <input id="username" v-model="username" type="text" autocomplete="username" required />
              </div>
            </div>

            <div class="field">
              <label for="password">Password</label>
              <div class="input-icon-wrap has-trailing">
                <span class="input-icon">
                  <svg viewBox="0 0 24 24" aria-hidden="true">
                    <rect x="5" y="11" width="14" height="9" rx="2" />
                    <path d="M8 11V8a4 4 0 0 1 8 0v3" />
                  </svg>
                </span>
                <input id="password" v-model="password" :type="showPassword ? 'text' : 'password'"
                  autocomplete="current-password" required @keydown="checkCapsLock" @keyup="checkCapsLock"
                  @blur="capsLockOn = false" />
                <button class="password-toggle" type="button" :aria-pressed="showPassword"
                  :aria-label="showPassword ? 'Hide password' : 'Show password'" @click="showPassword = !showPassword">
                  {{ showPassword ? 'Hide' : 'Show' }}
                </button>
              </div>
              <small v-if="capsLockOn" class="caps-warning" role="status">Caps Lock is on.</small>
            </div>
          </div>

          <div class="sign-in-options">
            <label class="remember-login">
              <input v-model="rememberMe" type="checkbox" />
              Remember company and user
            </label>
            <details class="password-help">
              <summary>Forgot password?</summary>
              <p>Ask your company administrator to reset your password.</p>
            </details>
          </div>

          <button type="submit" class="btn primary sign-in-submit" :disabled="busy">
            {{ busy ? 'Signing in…' : 'Sign in' }}
          </button>
        </form>

        <p class="sign-in-security">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="m12 3 8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z" />
            <path d="m9 12 2 2 4-4" />
          </svg>
          Sign-in activity is recorded.
        </p>
      </div>
    </section>
  </main>
</template>

<style scoped>
.company-sign-in {
  --sign-in-ivory: #fbf8f1;
  --sign-in-pine: #17403a;
  --sign-in-input-border: #86938e;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  min-height: 100vh;
  background: var(--sign-in-ivory);
  color: var(--ink);
}

.brand-panel,
.sign-in-panel {
  padding: clamp(28px, 4.375vw, 56px);
}

.brand-panel {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 40px;
  min-width: 0;
  color: var(--card-bg);
  background:
    radial-gradient(circle at 15% 92%, rgba(224, 163, 58, .24), transparent 32%),
    radial-gradient(circle at 95% 8%, rgba(46, 139, 110, .37), transparent 32%),
    radial-gradient(rgba(255, 253, 248, .10) 1.2px, transparent 1.2px) 0 0 / 28px 28px,
    #0c2723;
}

.company-logo { display: block; width: 180px; height: auto; }
.brand-story { display: grid; gap: 40px; }
.brand-story h2,
.sign-in-content h1 {
  margin: 0;
  font-family: var(--font-title);
  font-weight: 600;
  line-height: 1.1;
}
.brand-story h2 {
  max-width: 470px;
  color: var(--card-bg);
  font-size: clamp(32px, 3.6vw, 46px);
}

.workspace-preview {
  position: relative;
  width: min(100%, 528px);
  height: 300px;
}
.preview-card {
  position: absolute;
  border-radius: 18px;
  background: var(--sign-in-ivory);
  color: var(--ink);
  box-shadow: 0 18px 40px rgba(0, 0, 0, .35);
}
.attendance-preview {
  inset: 0 auto auto 0;
  width: 66.3%;
  min-height: 210px;
  padding: 22px clamp(14px, 1.875vw, 24px);
  border: 1px solid rgba(255, 253, 248, .20);
  border-radius: 22px;
  background: rgba(255, 253, 248, .09);
  color: var(--card-bg);
  box-shadow: none;
  backdrop-filter: blur(14px);
}
.preview-heading,
.preview-row,
.attendance-preview-body {
  display: flex;
  align-items: center;
}
.preview-heading { justify-content: space-between; gap: 8px; }
.preview-label { color: #bfe3d8; font-size: 12px; }
.attendance-preview-body { gap: clamp(10px, 2.2vw, 28px); margin-top: 22px; }
.attendance-ring { width: min(104px, 46%); flex: none; }
.attendance-ring circle { fill: none; stroke-width: 10; }
.ring-track { stroke: rgba(255, 253, 248, .16); }
.ring-progress { stroke: var(--accent); stroke-linecap: round; stroke-dasharray: 92 100; }
.attendance-ring text { fill: var(--card-bg); font-family: var(--font-title); font-size: 24px; }
.attendance-legend,
.brand-features {
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: 13px;
}
.attendance-legend { display: grid; gap: 12px; }
.attendance-legend li { display: flex; align-items: center; gap: 8px; white-space: nowrap; }
.attendance-legend li::before {
  content: '';
  width: 10px;
  height: 10px;
  border-radius: 3px;
  background: var(--accent);
}
.attendance-legend li:nth-child(2)::before { background: #5fd3a0; }
.attendance-legend li:nth-child(3)::before { background: rgba(255, 253, 248, .4); }
.leave-preview { inset: 54px 0 auto auto; width: 45.5%; padding: 18px; }
.payroll-preview { inset: 182px auto auto 8.3%; width: 57%; padding: 16px 18px; }
.preview-row { gap: 12px; }
.preview-row strong { display: block; font-size: 14px; line-height: 1.3; }
.preview-row small { color: var(--text-muted); font-size: 12px; }
.preview-avatar,
.preview-lock {
  display: grid;
  flex: none;
  place-items: center;
  width: 40px;
  height: 40px;
  font-weight: 600;
}
.preview-avatar { border-radius: 50%; background: var(--accent); }
.preview-lock { border-radius: 12px; background: var(--sign-in-pine); color: var(--card-bg); }
.preview-lock svg { width: 20px; height: 20px; }
.preview-status {
  display: inline-block;
  margin-top: 14px;
  padding: 6px 12px;
  border-radius: 999px;
  background: #f6e7c6;
  color: #6b4a0c;
  font-size: 12px;
  font-weight: 600;
}
.payroll-trend { width: min(64px, 26%); margin-inline-start: auto; color: #25705f; }
.brand-features { display: flex; flex-wrap: wrap; gap: 10px 28px; color: var(--ink-text); }

.sign-in-panel { display: grid; place-items: center; }
.sign-in-content { display: grid; gap: 26px; width: min(100%, 408px); }
.sign-in-content h1 { color: var(--ink); font-size: clamp(32px, 3.3vw, 42px); }
.sign-in-switch {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px;
  padding: 4px;
  border-radius: 14px;
  background: #efe9dc;
}
.sign-in-switch a {
  display: grid;
  place-items: center;
  min-height: 40px;
  border-radius: 10px;
  color: var(--text-2);
  font-weight: 500;
  text-decoration: none;
}
.sign-in-switch a:hover { background: var(--row-hover); }
.sign-in-switch a[aria-current='page'] {
  background: var(--card-bg);
  box-shadow: 0 1px 3px rgba(16, 48, 43, .18);
  color: var(--ink);
  font-weight: 600;
}

.auth-form,
.sign-in-fields { display: grid; gap: 18px; }
.auth-form .alert,
.sign-in-fields .field { margin: 0; }
.sign-in-fields .field { gap: 8px; }
.sign-in-fields label { color: var(--ink); font-size: 14px; }
.input-icon { left: 16px; }
.input-icon svg { width: 20px; height: 20px; }
.input-icon svg,
.preview-lock svg,
.payroll-trend,
.sign-in-security svg {
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.sign-in-fields input {
  height: 52px;
  padding-inline: 48px 16px;
  border: 1px solid var(--sign-in-input-border);
  border-radius: 14px;
  background: #fff;
  color: var(--ink);
  font-size: 16px;
}
.sign-in-fields .has-trailing input { padding-inline-end: 76px; }
.password-toggle {
  position: absolute;
  inset: 6px 6px auto auto;
  height: 40px;
  min-width: 44px;
  padding: 0 12px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--sign-in-pine);
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}
.password-toggle:hover { background: var(--row-hover); }
.caps-warning { color: var(--warn-text); }

.sign-in-options { display: flex; align-items: baseline; justify-content: space-between; flex-wrap: wrap; gap: 8px 16px; }
.remember-login { display: inline-flex; align-items: center; gap: 10px; }
.remember-login input { width: 20px; height: 20px; margin: 0; accent-color: var(--sign-in-pine); }
.password-help summary { color: var(--sign-in-pine); list-style: none; text-decoration: underline; cursor: pointer; }
.password-help summary::-webkit-details-marker { display: none; }
.password-help p { margin: 6px 0 0; color: var(--text-muted); font-size: 13px; }
.btn.sign-in-submit {
  min-height: 54px;
  border: 0;
  border-radius: 14px;
  background: linear-gradient(180deg, #1f5a4e, #0f2f2a);
  box-shadow: inset 0 -3px 0 var(--accent), 0 10px 24px rgba(15, 47, 42, .28);
  font-size: 16px;
  font-weight: 600;
}
.btn.sign-in-submit:hover:not(:disabled) { background: var(--sign-in-pine); }
.btn.sign-in-submit:disabled { background: var(--disabled-bg); box-shadow: none; }
.sign-in-security {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0;
  padding-top: 18px;
  border-top: 1px solid #ddd6c8;
  color: var(--text-muted);
  font-size: 13px;
}
.sign-in-security svg { width: 18px; height: 18px; flex: none; color: #25705f; }
.sign-in-content :focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; box-shadow: 0 0 0 3px var(--focus-ring); }

@media (max-width: 800px) {
  .company-sign-in { grid-template-columns: minmax(0, 1fr); align-content: start; }
  .brand-panel { padding: 20px 24px; }
  .brand-story, .brand-features { display: none; }
  .company-logo { width: 162px; }
  .sign-in-panel { padding: 32px 24px 40px; align-items: start; }
}

@media (max-width: 360px) {
  .sign-in-panel { padding-inline: 16px; }
}
</style>

