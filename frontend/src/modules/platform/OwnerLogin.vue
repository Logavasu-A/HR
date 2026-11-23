<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const username = ref('')
const password = ref('')
const showPassword = ref(false)
const capsOn = ref(false)
const remember = ref(false)
const loading = ref(false)
const error = ref('')
const rememberKey = 'hrms_platform_username'

onMounted(() => {
  try {
    username.value = localStorage.getItem(rememberKey) || ''
    remember.value = Boolean(username.value)
  } catch {}
})

function checkCapsLock(event) {
  capsOn.value = event.getModifierState('CapsLock')
}

async function signIn() {
  if (loading.value) return
  error.value = ''
  loading.value = true
  try {
    const response = await fetch('/api/platform/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: username.value, password: password.value }),
    })
    const data = await response.json().catch(() => ({}))
    if (!response.ok || !data.token) {
      error.value = response.status === 401
        ? 'Username or password is incorrect, or sign-in is temporarily locked.'
        : 'Sign-in is unavailable. Try again shortly.'
      return
    }
    const session = await fetch('/api/platform/session', {
      cache: 'no-store',
      headers: { Authorization: 'Bearer ' + data.token },
    })
    if (!session.ok) {
      error.value = 'Your platform session could not be opened.'
      return
    }
    const { role, controlTower } = await session.json()
    try {
      if (remember.value) localStorage.setItem(rememberKey, username.value)
      else localStorage.removeItem(rememberKey)
    } catch {}
    sessionStorage.setItem('hrms_platform_token', data.token)
    await router.replace(role === 'SECURITY_ADMINISTRATOR' ? '/platform/access'
      : controlTower ? '/platform/control-tower' : '/platform/companies')
  } catch {
    error.value = 'Could not connect to the platform. Try again.'
  } finally {
    password.value = ''
    showPassword.value = false
    capsOn.value = false
    loading.value = false
  }
}
</script>

<template>
  <main class="login platform-login">
    <aside class="side" aria-labelledby="platform-intro">
      <img src="/hrms-logo-v3.svg" alt="HRMS" class="brand-logo" width="270" height="72" />
      <div class="side-body">
        <h1 id="platform-intro">Platform console</h1>
        <ul class="posture">
          <li>Company access is recorded in the audit log</li>
          <li>Workspace access expires automatically</li>
        </ul>
      </div>
    </aside>

    <section class="panel" aria-labelledby="platform-sign-in">
      <form class="card" :aria-busy="loading" @submit.prevent="signIn">
        <h2 id="platform-sign-in">Super Admin sign-in</h2>
        <label for="owner-username">Username</label>
        <input id="owner-username" v-model.trim="username" type="text" autocomplete="username"
          autocapitalize="none" spellcheck="false" maxlength="80" :disabled="loading" required />

        <label for="owner-password">Password</label>
        <div class="pw">
          <input id="owner-password" v-model="password" :type="showPassword ? 'text' : 'password'"
            autocomplete="current-password" maxlength="72" :disabled="loading" required
            @keydown="checkCapsLock" @keyup="checkCapsLock" @blur="capsOn = false" />
          <button type="button" class="eye" :aria-pressed="showPassword" :disabled="loading"
            :aria-label="showPassword ? 'Hide password' : 'Show password'" @click="showPassword = !showPassword">
            {{ showPassword ? 'Hide' : 'Show' }}
          </button>
        </div>
        <p v-if="capsOn" class="hint warn" role="status">Caps Lock is on.</p>

        <label class="check"><input v-model="remember" type="checkbox" :disabled="loading" />
          <span>Remember my username on this device</span>
        </label>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <button class="primary" type="submit" :disabled="loading || !username || !password">
          <span v-if="loading" class="spin" aria-hidden="true"></span>
          {{ loading ? 'Signing in…' : 'Sign in' }}
        </button>
        <router-link class="link" to="/login">Company sign-in</router-link>
      </form>
    </section>
  </main>
</template>
<style scoped>
.login {
  min-height: 100dvh;
  display: grid;
  grid-template-columns: minmax(0, 5fr) minmax(0, 7fr);
  color: var(--platform-text);
  background: var(--platform-background);
}
.side {
  background: var(--ink); border-block-start: 3px solid var(--ink-accent);
  color: var(--panel); padding: clamp(var(--space-24), 5vw, var(--space-48)); display: flex; flex-direction: column;
}
.side-body { margin: auto 0; max-width: 380px; }
h1 { color: var(--card-bg); font-size: 34px; line-height: 1.15; font-weight: 500; margin: 0 0 12px; }
.posture { list-style: none; margin: 0; padding: 0; display: grid; gap: 10px; color: var(--ink-text); }
.posture li { padding-left: 22px; position: relative; line-height: 1.5; }
.posture li::before { content: ''; position: absolute; left: 2px; top: 0.5em; width: 9px; height: 5px; border-left: 2px solid var(--ink-text); border-bottom: 2px solid var(--ink-text); transform: rotate(-45deg); }

.panel { display: grid; place-items: center; padding: var(--space-32); }
.card { width: 100%; max-width: 440px; background: var(--panel); border: 1px solid var(--platform-border); border-radius: var(--platform-radius); padding: var(--platform-card-padding); box-shadow: var(--shadow-card); }
h2 { font-size: var(--platform-title-size); font-weight: 650; margin: 0 0 var(--space-8); }
label { display: block; font-size: 14px; font-weight: 500; margin: 16px 0 6px; }
input[type='text'], input[type='password'] {
  width: 100%; box-sizing: border-box; height: var(--platform-control-large); padding: 0 var(--space-12); font: inherit; color: var(--platform-text);
  border: 1px solid var(--input-border); border-radius: var(--platform-radius-small); background: var(--panel);
}

.pw { position: relative; }
.pw input { padding-right: 64px; }
.eye { position: absolute; right: var(--space-8); top: 50%; transform: translateY(-50%); min-width: var(--platform-control-small); height: var(--platform-control-small); padding: 0 var(--space-8); border: 0; background: transparent; color: var(--platform-accent); font: inherit; cursor: pointer; border-radius: var(--platform-radius-small); }
.hint { margin: 8px 0 0; font-size: 13px; }
.warn { color: var(--warn-text); }
.check { display: flex; align-items: center; gap: 10px; font-weight: 400; margin: 18px 0 0; color: var(--muted); cursor: pointer; }
.check input { width: 18px; height: 18px; accent-color: var(--platform-accent); }
.error { margin: var(--space-16) 0 0; padding: var(--space-8) var(--space-12); border-radius: var(--platform-radius-small); background: var(--danger-tint); color: var(--bad-text); border-inline-start: 4px solid var(--bad-line); line-height: 1.45; }
.primary {
  width: 100%; height: var(--platform-control-large); margin-top: var(--space-24); border: 1px solid transparent; border-radius: var(--platform-radius-small); background: var(--primary); color: var(--card-bg); box-shadow: inset 0 1px color-mix(in srgb, var(--card-bg) 18%, transparent);
  font: inherit; font-weight: 500; cursor: pointer; display: inline-flex; align-items: center; justify-content: center; gap: 10px;
}
.primary:hover:not(:disabled) { background: var(--platform-accent-hover); }
.primary:active:not(:disabled) { background: var(--primary-press); }
.primary:disabled { background: var(--disabled-bg); color: var(--disabled-text); border-color: var(--disabled-border); box-shadow: none; cursor: not-allowed; }
.link { display: block; text-align: center; margin: var(--space-16) auto 0; border: 0; background: none; color: var(--platform-accent); font: inherit; cursor: pointer; text-decoration: underline; }
.spin { width: 16px; height: 16px; border: 2px solid color-mix(in srgb, currentColor 50%, transparent); border-top-color: currentColor; border-radius: 50%; animation: spin 0.8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
@media (prefers-reduced-motion: reduce) { .spin { animation-duration: 2.4s; } }
@media (max-width: 860px) {
  .login { grid-template-columns: 1fr; }
  .side { padding: 24px; }
  .side-body { margin: 24px 0 0; }
  .posture { display: none; }
  h1 { font-size: 26px; margin-bottom: 0; }
  .panel { padding: var(--platform-content-gutter); }
}
</style>
