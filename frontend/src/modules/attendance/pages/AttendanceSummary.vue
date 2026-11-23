<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { getMonthlySummary } from '../services/attendanceService'
import { errorMessage, MONTHS } from '@/shared/utils'
import { useWorkspace } from '@/shared/workspaceContext'
import DataTable from '@/shared/components/DataTable.vue'
import PaginationBar from '@/shared/components/PaginationBar.vue'

const now = new Date()
const { api } = useWorkspace()
const year = ref(now.getFullYear())
const month = ref(now.getMonth() + 1)
const rows = ref([])
const error = ref('')
const loading = ref(false)
const page = ref(0)
const pageSize = ref(20)

const columns = [
  { key: 'employeeCode', label: 'Employee' },
  { key: 'PRESENT', label: 'Present', align: 'right' },
  { key: 'ABSENT', label: 'Absent', align: 'right' },
  { key: 'HALF_DAY', label: 'Half Day', align: 'right' },
  { key: 'ON_LEAVE', label: 'On Leave', align: 'right' },
  { key: 'WORK_FROM_HOME', label: 'Work From Home', align: 'right' },
  { key: 'workingHours', label: 'Hours Worked', align: 'right' },
]

const totalElements = computed(() => rows.value.length)
const totalPages = computed(() => Math.ceil(rows.value.length / pageSize.value))
const pagedRows = computed(() => {
  const start = page.value * pageSize.value
  return rows.value.slice(start, start + pageSize.value)
})

const summaryStats = computed(() => {
  let present = 0
  let absent = 0
  let leave = 0
  rows.value.forEach((r) => {
    present += Number(r.PRESENT ?? r.present ?? 0)
    absent += Number(r.ABSENT ?? r.absent ?? 0)
    leave += Number(r.ON_LEAVE ?? r.onLeave ?? 0)
  })
  return { present, absent, leave, count: rows.value.length }
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await getMonthlySummary(year.value, month.value, api)
    rows.value = Array.isArray(data) ? data : (data?.employees || [])
    page.value = 0
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}

function onPageSizeChange(s) {
  pageSize.value = s
  page.value = 0
}

function initials(name) {
  if (!name) return '?'
  const parts = name.trim().split(/\s+/)
  return parts.length >= 2 ? (parts[0][0] + parts[parts.length - 1][0]).toUpperCase() : parts[0].slice(0, 2).toUpperCase()
}

watch([year, month], load)
onMounted(load)
</script>

<template>
  <div class="attendance-summary-page">
    <div class="page-head period-heading">
      <div>
        <h1>Attendance summary</h1>
      </div>
      <div class="spacer"></div>
      <div class="period-filters-bar">
        <span class="period-filter-icon" aria-hidden="true">📅</span>
        <select v-model="month" aria-label="Select month" class="period-select">
          <option v-for="(m, i) in MONTHS" :key="m" :value="i + 1">{{ m }}</option>
        </select>
        <input v-model.number="year" type="number" min="2000" max="2100" class="year period-year" aria-label="Select year" />
      </div>
    </div>

    <!-- Quick Stats Cards -->
    <div v-if="!loading && totalElements > 0" class="mini-stats-grid">
      <div class="mini-stat">
        <span class="mini-stat-label">Total Employees</span>
        <span class="mini-stat-val">{{ summaryStats.count }}</span>
      </div>
      <div class="mini-stat mini-stat--success">
        <span class="mini-stat-label">Total Present Marks</span>
        <span class="mini-stat-val text-success">{{ summaryStats.present }}</span>
      </div>
      <div class="mini-stat mini-stat--danger">
        <span class="mini-stat-label">Total Absences</span>
        <span class="mini-stat-val text-danger">{{ summaryStats.absent }}</span>
      </div>
      <div class="mini-stat mini-stat--warn">
        <span class="mini-stat-label">Leave Days</span>
        <span class="mini-stat-val text-warn">{{ summaryStats.leave }}</span>
      </div>
    </div>

    <div v-if="error" class="alert">{{ error }}</div>

    <DataTable
      :columns="columns"
      :rows="pagedRows"
      row-key="employeeId"
      :loading="loading"
      empty-text="No active employees."
      aria-label="Attendance summary"
    >
      <template #cell-employeeCode="{ row }">
        <div class="emp-cell">
          <span class="emp-avatar">{{ initials(row.name ?? row.employeeName) }}</span>
          <div class="emp-cell-meta">
            <strong class="emp-name">{{ row.name ?? row.employeeName }}</strong>
            <span class="emp-code-badge">{{ row.employeeCode }}</span>
          </div>
        </div>
      </template>

      <template #cell-PRESENT="{ row }">
        <span class="count-badge count-badge--present">
          {{ row.PRESENT ?? row.present ?? 0 }}
        </span>
      </template>

      <template #cell-ABSENT="{ row }">
        <span class="count-badge" :class="{ 'count-badge--absent': (row.ABSENT ?? row.absent ?? 0) > 0, 'count-badge--zero': !(row.ABSENT ?? row.absent ?? 0) }">
          {{ row.ABSENT ?? row.absent ?? 0 }}
        </span>
      </template>

      <template #cell-HALF_DAY="{ row }">
        <span class="count-badge" :class="{ 'count-badge--half': (row.HALF_DAY ?? row.halfDay ?? 0) > 0, 'count-badge--zero': !(row.HALF_DAY ?? row.halfDay ?? 0) }">
          {{ row.HALF_DAY ?? row.halfDay ?? 0 }}
        </span>
      </template>

      <template #cell-ON_LEAVE="{ row }">
        <span class="count-badge" :class="{ 'count-badge--leave': (row.ON_LEAVE ?? row.onLeave ?? 0) > 0, 'count-badge--zero': !(row.ON_LEAVE ?? row.onLeave ?? 0) }">
          {{ row.ON_LEAVE ?? row.onLeave ?? 0 }}
        </span>
      </template>

      <template #cell-WORK_FROM_HOME="{ row }">
        <span class="count-badge" :class="{ 'count-badge--wfh': (row.WORK_FROM_HOME ?? row.workFromHome ?? 0) > 0, 'count-badge--zero': !(row.WORK_FROM_HOME ?? row.workFromHome ?? 0) }">
          {{ row.WORK_FROM_HOME ?? row.workFromHome ?? 0 }}
        </span>
      </template>

      <template #cell-workingHours="{ row }">
        <span class="hours-cell">{{ row.workingHours ? `${row.workingHours} hrs` : '—' }}</span>
      </template>

      <template #footer>
        <PaginationBar
          v-if="!loading && totalElements > 0"
          :page="page"
          :page-size="pageSize"
          :total-elements="totalElements"
          :total-pages="totalPages"
          :loading="loading"
          @update:page="(p) => (page = p)"
          @update:page-size="onPageSizeChange"
        />
      </template>
    </DataTable>
  </div>
</template>

<style scoped>
.period-filters-bar {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  max-inline-size: 100%;
  background: #FFFFFF;
  border: 1px solid var(--line);
  padding: 4px 10px;
  border-radius: 10px;
  box-shadow: var(--shadow-sm);
}

.period-filter-icon {
  font-size: 14px;
}

.period-select {
  border: none;
  background: transparent;
  padding: 4px 8px;
  font-size: 13px;
  font-weight: 500;
  box-shadow: none;
}
.period-select:focus {
  box-shadow: none;
}

.period-year {
  width: 4.5rem;
  border: 1px solid var(--line);
  padding: 3px 7px;
  font-size: 13px;
  font-weight: 500;
  border-radius: 6px;
}

.mini-stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 11.25rem), 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.mini-stat {
  background: #FFFFFF;
  border: 1px solid var(--line);
  border-radius: 10px;
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  box-shadow: var(--shadow-sm);
}

.mini-stat-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--muted);
}

.mini-stat-val {
  font-size: 20px;
  font-weight: 700;
  color: var(--text);
  font-variant-numeric: tabular-nums;
}

.text-success { color: var(--success-text); }
.text-danger { color: var(--danger-text); }
.text-warn { color: var(--warn-text); }

.emp-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.emp-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: linear-gradient(135deg, #6366F1, #4F46E5);
  color: #FFFFFF;
  font-size: 11px;
  font-weight: 700;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.emp-cell-meta {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-inline-size: 0;
  overflow-wrap: anywhere;
}

.emp-name {
  font-size: 13.5px;
  color: var(--text);
}

.emp-code-badge {
  font-size: 11px;
  font-family: monospace;
  color: var(--muted);
  background: #F4F4F5;
  padding: 1px 5px;
  border-radius: 4px;
  width: fit-content;
}

.count-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  padding: 2px 7px;
  border-radius: 6px;
  font-size: 12.5px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.count-badge--present {
  background: var(--success-soft);
  color: var(--success-text);
}

.count-badge--absent {
  background: var(--danger-soft);
  color: var(--danger-text);
}

.count-badge--half {
  background: var(--warn-soft);
  color: var(--warn-text);
}

.count-badge--leave {
  background: rgba(139, 92, 246, 0.12);
  color: #7C3AED;
}

.count-badge--wfh {
  background: var(--secondary-soft);
  color: #0891B2;
}

.count-badge--zero {
  color: #A1A1AA;
  background: transparent;
  font-weight: 400;
}

.hours-cell {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  color: var(--text);
}
</style>
