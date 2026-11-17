<script setup>
import { ref } from 'vue'
import api from '@/services/api'
import { errorMessage } from '@/utils'

const currentPassword = ref('')
const newPassword = ref('')
const repeat = ref('')
const error = ref('')
const done = ref(false)
const busy = ref(false)

async function save() {
  error.value = ''
  done.value = false
  if (newPassword.value !== repeat.value) {
    error.value = 'The two new passwords do not match'
    return
  }
  busy.value = true
  try {
    await api.post('/auth/change-password', { currentPassword: currentPassword.value, newPassword: newPassword.value })
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
  <div>
    <div class="page-head"><h1>Change password</h1></div>
    <form class="card pad narrow" @submit.prevent="save">
      <div v-if="error" class="alert">{{ error }}</div>
      <div v-if="done" class="alert ok">Password changed.</div>
      <div class="field">
        <label for="cp-current">Current password</label>
        <input id="cp-current" v-model="currentPassword" type="password" autocomplete="current-password" required />
      </div>
      <div class="field">
        <label for="cp-new">New password (8+ characters)</label>
        <input id="cp-new" v-model="newPassword" type="password" autocomplete="new-password" minlength="8" required />
      </div>
      <div class="field">
        <label for="cp-repeat">Repeat new password</label>
        <input id="cp-repeat" v-model="repeat" type="password" autocomplete="new-password" minlength="8" required />
      </div>
      <button class="btn primary" type="submit" :disabled="busy">{{ busy ? 'Saving…' : 'Change password' }}</button>
    </form>
  </div>
</template>
