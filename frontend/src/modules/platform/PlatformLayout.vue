<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Layout from '@/shared/components/Layout.vue'
import PlatformState from './PlatformState.vue'

const router = useRouter()
const session = ref(null)
const error = ref('')
const busy = ref(false)
const request = new AbortController()

async function loadSession() {
  busy.value = true
  error.value = ''
  try {
    const token = sessionStorage.getItem('hrms_platform_token')
    if (!token) return await router.replace('/platform/login')
    const response = await fetch('/api/platform/session', {
      headers: { Authorization: 'Bearer ' + token },
      cache: 'no-store',
      signal: request.signal,
    })
    if (response.status === 401) {
      if (sessionStorage.getItem('hrms_platform_token') === token) sessionStorage.removeItem('hrms_platform_token')
      return await router.replace('/platform/login')
    }
    if (!response.ok) throw new Error('Could not load your platform session.')
    session.value = await response.json()
  } catch (failure) {
    if (!request.signal.aborted) error.value = failure.message
  } finally {
    busy.value = false
  }
}

async function logout() {
  if (busy.value) return
  busy.value = true
  const token = sessionStorage.getItem('hrms_platform_token')
  try {
    const response = await fetch('/api/platform/logout', {
      method: 'POST',
      headers: { Authorization: 'Bearer ' + token },
    })
    if (!response.ok && response.status !== 401) throw new Error()
    if (sessionStorage.getItem('hrms_platform_token') === token) sessionStorage.removeItem('hrms_platform_token')
    await router.replace('/platform/login')
  } catch {
    error.value = 'Sign-out did not complete. Try again.'
  } finally {
    busy.value = false
  }
}

onMounted(loadSession)
onBeforeUnmount(() => request.abort())
</script>

<template>
  <Layout platform :platform-session="session" @platform-logout="logout">
    <PlatformState v-if="error" kind="error" :title="error">
      <button v-if="!session" class="btn small" type="button" @click="loadSession">Retry</button>
    </PlatformState>
    <slot v-if="session" />
    <PlatformState v-else-if="busy" kind="loading" title="Loading the platform console" />
  </Layout>
</template>