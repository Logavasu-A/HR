<script setup>
import { computed } from 'vue'

const props = defineProps({
  page: { type: Number, required: true },
  pageSize: { type: Number, required: true },
  totalElements: { type: Number, required: true },
  totalPages: { type: Number, required: true },
  loading: { type: Boolean, default: false },
  filteredCount: { type: Number, default: null },
  sizes: { type: Array, default: () => [10, 20, 50, 100] },
})

const emit = defineEmits(['update:page', 'update:pageSize'])

const start = computed(() => (props.totalElements === 0 ? 0 : props.page * props.pageSize + 1))
const end = computed(() => Math.min((props.page + 1) * props.pageSize, props.totalElements))

const pages = computed(() => {
  const total = Math.max(1, props.totalPages)
  const cur = props.page
  if (total <= 7) return Array.from({ length: total }, (_, i) => i)
  const out = [0]
  if (cur > 2) out.push('…')
  const from = Math.max(1, cur - 1)
  const to = Math.min(total - 2, cur + 1)
  for (let i = from; i <= to; i++) out.push(i)
  if (cur < total - 3) out.push('…')
  out.push(total - 1)
  return out
})

function go(p) {
  if (props.loading) return
  if (p < 0 || p >= Math.max(1, props.totalPages) || p === props.page) return
  emit('update:page', p)
}

function onSizeChange(e) {
  emit('update:pageSize', Number(e.target.value))
}
</script>

<template>
  <div v-if="totalElements > 0" class="pagination-bar">
    <div class="pagination-info">
      Showing <strong>{{ start }}–{{ end }}</strong> of <strong>{{ totalElements }}</strong> records
      <span v-if="filteredCount !== null" class="muted"> ({{ filteredCount }} matching on this page)</span>
    </div>
    <div class="pagination-controls">
      <select class="page-size" :value="pageSize" :disabled="loading" aria-label="Rows per page" @change="onSizeChange">
        <option v-for="s in sizes" :key="s" :value="s">{{ s }} / page</option>
      </select>
      <div class="page-buttons" role="navigation" aria-label="Pagination">
        <button type="button" class="btn small page-btn" :disabled="loading || page <= 0" @click="go(page - 1)">Prev</button>
        <template v-for="(p, idx) in pages" :key="`${p}-${idx}`">
          <span v-if="p === '…'" class="page-ellipsis" aria-hidden="true">…</span>
          <button
            v-else
            type="button"
            class="btn small page-btn"
            :class="{ active: p === page }"
            :aria-current="p === page ? 'page' : undefined"
            :disabled="loading"
            @click="go(p)"
          >
            {{ p + 1 }}
          </button>
        </template>
        <button type="button" class="btn small page-btn" :disabled="loading || page >= totalPages - 1" @click="go(page + 1)">Next</button>
      </div>
    </div>
  </div>
</template>
