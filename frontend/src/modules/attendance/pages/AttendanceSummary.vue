<script setup>
import { onMounted, ref, watch } from 'vue'
import { getMonthlySummary } from '../services/attendanceService'
import { errorMessage, MONTHS } from '@/shared/utils'

const now = new Date()
const year = ref(now.getFullYear())
const month = ref(now.getMonth() + 1)
const rows = ref([])
const error = ref('')
const loading = ref(false)

async function load() {
  loading.value = true
  error.value = ''
  try {
    rows.value = await getMonthlySummary(year.value, month.value)
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}

watch([year, month], load)
onMounted(load)
</script>

<template>
  <div>
    <div class="page-head">
      <h1>Attendance summary</h1>
      <div class="spacer"></div>
      <select v-model="month" aria-label="Month">
        <option v-for="(m, i) in MONTHS" :key="m" :value="i + 1">{{ m }}</option>
      </select>
      <input v-model.number="year" type="number" min="2000" max="2100" class="year" aria-label="Year" />
    </div>
    <div v-if="error" class="alert">{{ error }}</div>

    <div class="card table-wrap">
      <table>
        <thead>
          <tr>
            <th>Employee</th><th class="num">Present</th><th class="num">Absent</th><th class="num">Half day</th>
            <th class="num">On leave</th><th class="num">Work from home</th><th class="num">Hours worked</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading"><td colspan="7" class="muted">Loading…</td></tr>
          <tr v-else-if="!rows.length"><td colspan="7" class="muted">No active employees.</td></tr>
          <template v-else>
            <tr v-for="r in rows" :key="r.employeeId">
              <td>{{ r.employeeCode }} · {{ r.name }}</td>
              <td class="num">{{ r.PRESENT }}</td>
              <td class="num">{{ r.ABSENT }}</td>
              <td class="num">{{ r.HALF_DAY }}</td>
              <td class="num">{{ r.ON_LEAVE }}</td>
              <td class="num">{{ r.WORK_FROM_HOME }}</td>
              <td class="num">{{ r.workingHours }}</td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>
  </div>
</template>
