<script setup>
import { computed, ref, useSlots, watch } from 'vue'
import { getPath } from '@/shared/utils'

const props = defineProps({
  columns: { type: Array, required: true },
  rows: { type: Array, default: () => [] },
  rowKey: { type: [String, Function], default: 'id' },
  loading: { type: Boolean, default: false },
  loadingText: { type: String, default: 'Loading…' },
  emptyText: { type: String, default: 'No records yet.' },
  emptyIcon: { type: String, default: '📭' },
  sortable: { type: Boolean, default: true },
  selectable: { type: Boolean, default: false },
  selectedId: { type: [String, Number], default: null },
  selectedIds: { type: Array, default: () => [] },
  hasActions: { type: Boolean, default: null },
  actionsLabel: { type: String, default: '' },
  formatCell: { type: Function, default: null },
  badgeClass: { type: Function, default: null },
  isShort: { type: Function, default: null },
  compact: { type: Boolean, default: false },
  ariaLabel: { type: String, default: 'Data table' },
})

const emit = defineEmits(['row-click', 'update:selectedIds'])
const slots = useSlots()

const sortKey = ref('')
const sortDir = ref('')

// Reset sort when column set changes (e.g. switching modules)
watch(
  () => props.columns,
  () => {
    sortKey.value = ''
    sortDir.value = ''
  },
)

const showActions = computed(() => (props.hasActions !== null ? props.hasActions : Boolean(slots.actions)))
const totalColumns = computed(
  () => props.columns.length + (props.selectable ? 1 : 0) + (showActions.value ? 1 : 0),
)

function resolveKey(row, index) {
  if (typeof props.rowKey === 'function') return props.rowKey(row, index)
  return getPath(row, props.rowKey) ?? index
}

function isColumnSortable(col) {
  return props.sortable && col.sortable !== false
}

function toggleSort(col) {
  if (!isColumnSortable(col)) return
  if (sortKey.value !== col.key) {
    sortKey.value = col.key
    sortDir.value = 'asc'
  } else if (sortDir.value === 'asc') {
    sortDir.value = 'desc'
  } else {
    sortKey.value = ''
    sortDir.value = ''
  }
}

function ariaSort(col) {
  if (!isColumnSortable(col) || sortKey.value !== col.key) return undefined
  return sortDir.value === 'asc' ? 'ascending' : 'descending'
}

const sortedRows = computed(() => {
  if (!sortKey.value || !sortDir.value || props.rows.length <= 1) return props.rows
  const col = props.columns.find((c) => c.key === sortKey.value)
  const dir = sortDir.value === 'asc' ? 1 : -1
  return [...props.rows].sort((a, b) => {
    const rawA = getPath(a, sortKey.value)
    const rawB = getPath(b, sortKey.value)
    const emptyA = rawA == null || rawA === ''
    const emptyB = rawB == null || rawB === ''
    if (emptyA && emptyB) return 0
    if (emptyA) return 1
    if (emptyB) return -1
    if (typeof rawA === 'number' && typeof rawB === 'number') return (rawA - rawB) * dir
    if (col?.money) {
      const nA = Number(rawA)
      const nB = Number(rawB)
      if (!Number.isNaN(nA) && !Number.isNaN(nB)) return (nA - nB) * dir
    }
    const strA = String(props.formatCell ? props.formatCell(col, a) : rawA)
    const strB = String(props.formatCell ? props.formatCell(col, b) : rawB)
    return strA.localeCompare(strB, undefined, { numeric: true, sensitivity: 'base' }) * dir
  })
})

function displayValue(col, row) {
  if (props.formatCell) return props.formatCell(col, row)
  const v = getPath(row, col.key)
  return v == null ? '' : v
}

function resolveBadge(col, row) {
  if (props.badgeClass) return props.badgeClass(col, row)
  if (!col.badge) return ''
  const v = getPath(row, col.key)
  return v ? `badge ${String(v).toLowerCase()}` : ''
}

function isCellNowrap(col, row) {
  if (col.nowrap === true) return true
  if (col.nowrap === false) return false
  if (props.isShort) return props.isShort(col, row)
  return false
}

// ---- row selection ----
const selectedSet = computed(() => new Set(props.selectedIds))
const allSelected = computed(
  () => props.rows.length > 0 && props.rows.every((r, i) => selectedSet.value.has(resolveKey(r, i))),
)

function isRowSelected(row, index) {
  const key = resolveKey(row, index)
  if (props.selectedId !== null && props.selectedId === key) return true
  return props.selectable && selectedSet.value.has(key)
}

function toggleAll(e) {
  emit('update:selectedIds', e.target.checked ? props.rows.map((r, i) => resolveKey(r, i)) : [])
}

function toggleRow(row, index) {
  const key = resolveKey(row, index)
  const next = new Set(selectedSet.value)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  emit('update:selectedIds', [...next])
}
</script>

<template>
  <div class="table-card">
    <div class="table-wrapper" tabindex="0" role="region" :aria-label="ariaLabel">
      <table class="data-table" :class="{ 'data-table--compact': compact }">
        <thead>
          <tr>
            <th v-if="selectable" class="table-check-col" scope="col">
              <input
                type="checkbox"
                class="check"
                :checked="allSelected"
                aria-label="Select all rows on this page"
                @change="toggleAll"
              />
            </th>
            <th
              v-for="c in columns"
              :key="c.key"
              scope="col"
              :style="c.width ? { width: c.width } : undefined"
              :class="{
                num: c.money || c.align === 'right',
                sortable: isColumnSortable(c),
                sorted: sortKey === c.key,
              }"
              :tabindex="isColumnSortable(c) ? 0 : undefined"
              :aria-sort="ariaSort(c)"
              @click="toggleSort(c)"
              @keydown.enter.prevent="toggleSort(c)"
              @keydown.space.prevent="toggleSort(c)"
            >
              <span class="th-inner">
                <span>{{ c.label }}</span>
                <span v-if="isColumnSortable(c)" class="sort-indicator" aria-hidden="true">
                  {{ sortKey === c.key ? (sortDir === 'asc' ? '▲' : '▼') : '⇅' }}
                </span>
              </span>
            </th>
            <th v-if="showActions" class="actions-col" scope="col">{{ actionsLabel }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading" class="table-state-row">
            <td :colspan="totalColumns" class="empty">{{ loadingText }}</td>
          </tr>
          <tr v-else-if="!sortedRows.length" class="table-state-row">
            <td :colspan="totalColumns" class="empty">
              <slot name="empty">
                <span v-if="emptyIcon" class="empty-icon">{{ emptyIcon }}</span>
                {{ emptyText }}
              </slot>
            </td>
          </tr>
          <template v-else>
            <tr
              v-for="(row, index) in sortedRows"
              :key="resolveKey(row, index)"
              :class="{ selected: isRowSelected(row, index) }"
              @click="emit('row-click', row)"
            >
              <td v-if="selectable" class="table-check-col" @click.stop>
                <input
                  type="checkbox"
                  class="check"
                  :checked="selectedSet.has(resolveKey(row, index))"
                  aria-label="Select row"
                  @change="toggleRow(row, index)"
                />
              </td>
              <td
                v-for="c in columns"
                :key="c.key"
                :class="{
                  num: c.money || c.align === 'right',
                  nowrap: isCellNowrap(c, row),
                }"
              >
                <slot :name="`cell-${c.key}`" :row="row" :column="c" :value="displayValue(c, row)">
                  <span v-if="resolveBadge(c, row)" :class="resolveBadge(c, row)">{{ displayValue(c, row) }}</span>
                  <template v-else>{{ displayValue(c, row) }}</template>
                </slot>
              </td>
              <td v-if="showActions" class="actions" @click.stop>
                <slot name="actions" :row="row" />
              </td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>
    <slot name="footer" />
  </div>
</template>
