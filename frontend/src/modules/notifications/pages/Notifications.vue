<script setup>
import { onMounted, ref } from 'vue'
import { list, markAllRead, markRead } from '../services/notificationService'
import { errorMessage } from '@/shared/utils'
import PaginationBar from '@/shared/components/PaginationBar.vue'

const items = ref([])
const error = ref('')
const loading = ref(true)
const page = ref(0)
const pageSize = ref(20)
const totalElements = ref(0)
const totalPages = ref(0)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await list(page.value, pageSize.value)
    items.value = data.items || []
    totalElements.value = data.totalElements ?? items.value.length
    totalPages.value = data.totalPages ?? (items.value.length ? 1 : 0)
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}

function onPageChange(p) {
  page.value = p
  load()
}

function onPageSizeChange(s) {
  pageSize.value = s
  page.value = 0
  load()
}

async function markAll() {
  try {
    await markAllRead()
    await load()
  } catch (e) {
    error.value = errorMessage(e)
  }
}

async function markOne(n) {
  if (n.read) return
  try {
    await markRead(n.id)
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

    <div v-if="!loading && totalElements > 0" class="card">
      <PaginationBar
        :page="page"
        :page-size="pageSize"
        :total-elements="totalElements"
        :total-pages="totalPages"
        :loading="loading"
        @update:page="onPageChange"
        @update:page-size="onPageSizeChange"
      />
    </div>
  </div>
</template>
