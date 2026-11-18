<script setup>
import { onMounted, ref } from 'vue'
import * as payroll from '../services/payrollService'
import { can } from '@/shared/auth'
import { errorMessage, money, MONTHS, periodLabel, pretty } from '@/shared/utils'
import SlipModal from '@/shared/components/SlipModal.vue'
import DataTable from '@/shared/components/DataTable.vue'
import PaginationBar from '@/shared/components/PaginationBar.vue'

const runColumns = [
  { key: 'periodYear', label: 'Period', nowrap: true },
  { key: 'status', label: 'Status', badge: true },
  { key: 'slipCount', label: 'Slips', align: 'right' },
  { key: 'totalNet', label: 'Total net pay', money: true },
]

const slipColumns = [
  { key: 'employee.employeeCode', label: 'Employee' },
  { key: 'paymentDays', label: 'Paid days', align: 'right' },
  { key: 'lwpDays', label: 'Unpaid days', align: 'right' },
  { key: 'grossPay', label: 'Gross', money: true },
  { key: 'totalDeduction', label: 'Deductions', money: true },
  { key: 'netPay', label: 'Net pay', money: true },
]

const now = new Date()
const year = ref(now.getFullYear())
const month = ref(now.getMonth() + 1)

const runs = ref([])
const runsPage = ref(0)
const runsSize = ref(20)
const runsTotal = ref(0)
const runsPages = ref(0)

const selected = ref(null)
const slips = ref([])
const slipsPage = ref(0)
const slipsSize = ref(20)
const slipsTotal = ref(0)
const slipsPages = ref(0)

const openSlip = ref(null)
const skipped = ref([])
const error = ref('')
const busy = ref(false)

async function loadRuns() {
  const data = await payroll.listRuns(runsPage.value, runsSize.value)
  if (Array.isArray(data)) {
    runs.value = data
    runsTotal.value = data.length
    runsPages.value = data.length ? 1 : 0
  } else {
    runs.value = data.content || []
    runsTotal.value = data.totalElements ?? runs.value.length
    runsPages.value = data.totalPages ?? (runs.value.length ? 1 : 0)
  }
}

async function loadSlips(run) {
  if (!run) return
  const data = await payroll.listSlips(run.id, slipsPage.value, slipsSize.value)
  if (Array.isArray(data)) {
    slips.value = data
    slipsTotal.value = data.length
    slipsPages.value = data.length ? 1 : 0
  } else {
    slips.value = data.content || []
    slipsTotal.value = data.totalElements ?? slips.value.length
    slipsPages.value = data.totalPages ?? (slips.value.length ? 1 : 0)
  }
}

async function showSlips(run) {
  selected.value = run
  slipsPage.value = 0
  await loadSlips(run)
}

const onRunsPage = (p) => guarded(async () => { runsPage.value = p; await loadRuns() })
const onRunsSize = (s) => guarded(async () => { runsSize.value = s; runsPage.value = 0; await loadRuns() })
const onSlipsPage = (p) => guarded(async () => { slipsPage.value = p; await loadSlips(selected.value) })
const onSlipsSize = (s) => guarded(async () => { slipsSize.value = s; slipsPage.value = 0; await loadSlips(selected.value) })

async function guarded(fn) {
  busy.value = true
  error.value = ''
  try {
    await fn()
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}

const calculate = () => guarded(async () => {
  const data = await payroll.createRun(Number(year.value), Number(month.value))
  skipped.value = data.skipped || []
  await loadRuns()
  await showSlips(data)
})

const submit = (run) => guarded(async () => {
  if (!window.confirm(`Submit payroll for ${periodLabel(run.periodYear, run.periodMonth)}? Slips become final and employees are notified.`)) return
  await payroll.submitRun(run.id)
  await loadRuns()
  if (selected.value?.id === run.id) await showSlips(runs.value.find((r) => r.id === run.id))
})

const remove = (run) => guarded(async () => {
  if (!window.confirm('Delete this draft payroll run and its slips?')) return
  await payroll.deleteRun(run.id)
  if (selected.value?.id === run.id) {
    selected.value = null
    slips.value = []
  }
  await loadRuns()
})

onMounted(() => guarded(loadRuns))
</script>

<template>
  <div>
    <div class="page-head"><h1>Payroll runs</h1></div>
    <p class="hint">
      Calculating creates draft salary slips for every active employee with a salary assignment.
      You can recalculate a month as often as needed until it is submitted.
    </p>
    <div v-if="error" class="alert">{{ error }}</div>

    <div v-if="can('payroll:write')" class="card pad toolbar">
      <div class="field">
        <label for="pr-month">Month</label>
        <select id="pr-month" v-model="month">
          <option v-for="(m, i) in MONTHS" :key="m" :value="i + 1">{{ m }}</option>
        </select>
      </div>
      <div class="field">
        <label for="pr-year">Year</label>
        <input id="pr-year" v-model="year" type="number" min="2000" max="2100" />
      </div>
      <button class="btn primary" :disabled="busy" @click="calculate">{{ busy ? 'Working…' : 'Calculate payroll' }}</button>
    </div>

    <div v-if="skipped.length" class="alert warn">
      Left out of the last calculation:
      <ul><li v-for="s in skipped" :key="s">{{ s }}</li></ul>
    </div>

    <DataTable
      :columns="runColumns"
      :rows="runs"
      :selected-id="selected?.id ?? null"
      empty-text="No payroll runs yet."
      aria-label="Payroll runs"
    >
      <template #cell-periodYear="{ row }">{{ periodLabel(row.periodYear, row.periodMonth) }}</template>
      <template #cell-status="{ row }"><span :class="`badge ${row.status.toLowerCase()}`">{{ pretty(row.status) }}</span></template>
      <template #cell-totalNet="{ row }">{{ money(row.totalNet) }}</template>
      <template #actions="{ row }">
        <button class="btn small" @click="guarded(() => showSlips(row))">View slips</button>
        <button v-if="row.status === 'DRAFT' && can('payroll:approve')" class="btn small" @click="submit(row)">Submit</button>
        <button v-if="row.status === 'DRAFT' && can('payroll:write')" class="btn small danger" @click="remove(row)">Delete</button>
      </template>
      <template #footer>
        <PaginationBar
          v-if="runsTotal > 0"
          :page="runsPage"
          :page-size="runsSize"
          :total-elements="runsTotal"
          :total-pages="runsPages"
          :loading="busy"
          @update:page="onRunsPage"
          @update:page-size="onRunsSize"
        />
      </template>
    </DataTable>

    <template v-if="selected">
      <h2 class="section">Salary slips · {{ periodLabel(selected.periodYear, selected.periodMonth) }}</h2>
      <DataTable
        :columns="slipColumns"
        :rows="slips"
        empty-text="No slips in this run."
        aria-label="Salary slips"
      >
        <template #cell-employee.employeeCode="{ row }">{{ row.employee.employeeCode }} · {{ row.employee.fullName }}</template>
        <template #cell-grossPay="{ row }">{{ money(row.grossPay) }}</template>
        <template #cell-totalDeduction="{ row }">{{ money(row.totalDeduction) }}</template>
        <template #cell-netPay="{ row }">{{ money(row.netPay) }}</template>
        <template #actions="{ row }">
          <button class="btn small" @click="openSlip = row">View</button>
        </template>
        <template #footer>
          <PaginationBar
            v-if="slipsTotal > 0"
            :page="slipsPage"
            :page-size="slipsSize"
            :total-elements="slipsTotal"
            :total-pages="slipsPages"
            :loading="busy"
            @update:page="onSlipsPage"
            @update:page-size="onSlipsSize"
          />
        </template>
      </DataTable>
    </template>

    <SlipModal v-if="openSlip" :slip="openSlip" @close="openSlip = null" />
  </div>
</template>
