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
  emptyIcon: { type: String, default: 'records' },
  sortable: { type: Boolean, default: true },
  selectable: { type: Boolean, default: false },
  selectedId: { type: [String, Number], default: null },
  selectedIds: { type: Array, default: () => [] },
  hasActions: { type: Boolean, default: null },
  actionsLabel: { type: String, default: 'Actions' },
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

const sortableColumns = computed(() => props.columns.filter(isColumnSortable))
const mobileSort = computed({
  get: () => sortKey.value && sortDir.value ? sortDir.value + ':' + sortKey.value : '',
  set(value) {
    const [direction, ...key] = value.split(':')
    sortKey.value = key.join(':')
    sortDir.value = direction
  },
})

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

function isDateCell(col, row) {
  const value = getPath(row, col.key)
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}(?:$|[T ]\d{2}:\d{2})/.test(value)
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
    <div v-if="sortableColumns.length || selectable" class="table-mobile-controls">
      <select v-if="sortableColumns.length" v-model="mobileSort" aria-label="Sort rows">
        <option value="">Default order</option>
        <template v-for="column in sortableColumns" :key="column.key">
          <option :value="'asc:' + column.key">{{ column.label }} (ascending)</option>
          <option :value="'desc:' + column.key">{{ column.label }} (descending)</option>
        </template>
      </select>
      <label v-if="selectable" class="table-select-target">
        <input type="checkbox" class="check" :checked="allSelected" @change="toggleAll" />
        Select all rows on this page
      </label>
    </div>
    <div class="table-wrapper" tabindex="0" role="region" :aria-label="ariaLabel">
      <table class="data-table responsive-table data-table--records" role="table" :class="{ 'data-table--compact': compact }">
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
                <svg v-if="isColumnSortable(c)" class="sort-indicator" viewBox="0 0 16 16" fill="none"
                  stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                  <path v-if="sortKey !== c.key" d="m5 6 3-3 3 3M5 10l3 3 3-3" />
                  <path v-else :d="sortDir === 'asc' ? 'm4 10 4-4 4 4' : 'm4 6 4 4 4-4'" />
                </svg>
              </span>
            </th>
            <th v-if="showActions" class="actions-col" scope="col">{{ actionsLabel }}</th>
          </tr>
        </thead>
        <tbody role="rowgroup">
          <tr v-if="loading" class="table-state-row">
            <td :colspan="totalColumns" class="empty">
              <div class="table-state-message" role="status">
                <span class="table-state-icon table-state-icon--loading" aria-hidden="true">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
                    <circle cx="12" cy="12" r="8" opacity=".25" />
                    <path d="M20 12a8 8 0 0 0-8-8" />
                  </svg>
                </span>
                {{ loadingText }}
              </div>
            </td>
          </tr>
          <tr v-else-if="!sortedRows.length" class="table-state-row">
            <td :colspan="totalColumns" class="empty">
              <div class="table-state-message">
                <slot name="empty">
                  <span v-if="emptyIcon" class="table-state-icon" aria-hidden="true">
                    <svg v-if="emptyIcon === 'records' || emptyIcon === 'search'" viewBox="0 0 24 24" fill="none"
                      stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                      <path v-if="emptyIcon === 'search'" d="m16 16 4 4m-3-9a6 6 0 1 1-12 0 6 6 0 0 1 12 0Z" />
                      <path v-else d="M4 13 7 5h10l3 8v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1v-5Zm0 0h5l1 2h4l1-2h5" />
                    </svg>
                    <template v-else>{{ emptyIcon }}</template>
                  </span>
                  {{ emptyText }}
                </slot>
              </div>
            </td>
          </tr>
          <template v-else>
            <tr
              v-for="(row, index) in sortedRows"
              :key="resolveKey(row, index)"
              role="row"
              :class="{ selected: isRowSelected(row, index) }"
              @click="emit('row-click', row)"
            >
              <td v-if="selectable" class="table-check-col" role="cell" @click.stop>
                <label class="table-select-target" aria-label="Select row">
                  <input
                    type="checkbox"
                    class="check"
                    :checked="selectedSet.has(resolveKey(row, index))"
                    aria-label="Select row"
                    @change="toggleRow(row, index)"
                  />
                </label>
              </td>
              <td
                v-for="c in columns"
                :key="c.key"
                :data-label="c.label"
                role="cell"
                :class="{
                  num: c.money || c.align === 'right',
                  nowrap: isCellNowrap(c, row),
                }"
              >
                <div class="table-cell-value" :class="{ 'table-date': isDateCell(c, row) }">
                  <slot :name="`cell-${c.key}`" :row="row" :column="c" :value="displayValue(c, row)">
                    <span v-if="resolveBadge(c, row)" :class="resolveBadge(c, row)">{{ displayValue(c, row) }}</span>
                    <template v-else>{{ displayValue(c, row) }}</template>
                  </slot>
                </div>
              </td>
              <td v-if="showActions" class="actions" role="cell" @click.stop>
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
