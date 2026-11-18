<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { getMonthlySummary } from '../services/attendanceService'
import { errorMessage, MONTHS } from '@/shared/utils'
import DataTable from '@/shared/components/DataTable.vue'
import PaginationBar from '@/shared/components/PaginationBar.vue'

const now = new Date()
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
  { key: 'HALF_DAY', label: 'Half day', align: 'right' },
  { key: 'ON_LEAVE', label: 'On leave', align: 'right' },
  { key: 'WORK_FROM_HOME', label: 'Work from home', align: 'right' },
  { key: 'workingHours', label: 'Hours worked', align: 'right' },
]

const totalElements = computed(() => rows.value.length)
const totalPages = computed(() => Math.ceil(rows.value.length / pageSize.value))
const pagedRows = computed(() => {
  const start = page.value * pageSize.value
  return rows.value.slice(start, start + pageSize.value)
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await getMonthlySummary(year.value, month.value)
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

watch([year, month], load)
onMounted(load)
</script>

<template>
  <div>
    <div class="page-head period-heading">
      <h1>Attendance summary</h1>
      <div class="spacer"></div>
      <div class="period-filters">
        <select v-model="month" aria-label="Month">
          <option v-for="(m, i) in MONTHS" :key="m" :value="i + 1">{{ m }}</option>
        </select>
        <input v-model.number="year" type="number" min="2000" max="2100" class="year" aria-label="Year" />
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
      <template #cell-employeeCode="{ row }">{{ row.employeeCode }} · {{ row.name ?? row.employeeName }}</template>
      <template #cell-PRESENT="{ row }">{{ row.PRESENT ?? row.present ?? 0 }}</template>
      <template #cell-ABSENT="{ row }">{{ row.ABSENT ?? row.absent ?? 0 }}</template>
      <template #cell-HALF_DAY="{ row }">{{ row.HALF_DAY ?? row.halfDay ?? 0 }}</template>
      <template #cell-ON_LEAVE="{ row }">{{ row.ON_LEAVE ?? row.onLeave ?? 0 }}</template>
      <template #cell-WORK_FROM_HOME="{ row }">{{ row.WORK_FROM_HOME ?? row.workFromHome ?? 0 }}</template>
      <template #cell-workingHours="{ row }">{{ row.workingHours ?? '—' }}</template>
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
