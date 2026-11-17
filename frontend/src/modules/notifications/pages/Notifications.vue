<script setup>
import { onMounted, ref } from 'vue'
import api from '@/services/api'
import { errorMessage } from '@/utils'

const items = ref([])
const error = ref('')
const loading = ref(true)

async function load() {
  try {
    const { data } = await api.get('/notifications')
    items.value = data.items
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}

async function markAll() {
  try {
    await api.post('/notifications/read-all')
    await load()
  } catch (e) {
    error.value = errorMessage(e)
  }
}

async function markOne(n) {
  if (n.read) return
  try {
    await api.post(`/notifications/${n.id}/read`)
    n.read = true
  } catch (e) {
    error.value = errorMessage(e)
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-head">
      <h1>Notifications</h1>
      <div class="spacer"></div>
      <button class="btn" :disabled="!items.some((n) => !n.read)" @click="markAll">Mark all as read</button>
    </div>
    <div v-if="error" class="alert">{{ error }}</div>
    <p v-if="loading" class="muted">Loading…</p>
    <p v-else-if="!items.length" class="muted">Nothing here yet.</p>

    <div v-for="n in items" :key="n.id" class="card pad note" :class="{ unread: !n.read }" @click="markOne(n)">
      <div class="note-title">{{ n.title }}</div>
      <div>{{ n.message }}</div>
      <div class="muted small-text">{{ n.createdAt.replace('T', ' ').slice(0, 16) }}</div>
    </div>
  </div>
</template>
