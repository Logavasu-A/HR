<script setup>
import { onMounted, ref } from 'vue'
import * as payroll from '../services/payrollService'
import { can } from '@/shared/auth'
import { errorMessage, money, MONTHS, periodLabel, pretty } from '@/shared/utils'
import SlipModal from '@/shared/components/SlipModal.vue'

const now = new Date()
const year = ref(now.getFullYear())
const month = ref(now.getMonth() + 1)

const runs = ref([])
const selected = ref(null)
const slips = ref([])
const openSlip = ref(null)
const skipped = ref([])
const error = ref('')
const busy = ref(false)

async function loadRuns() {
  runs.value = await payroll.listRuns()
}

async function showSlips(run) {
  selected.value = run
  slips.value = await payroll.listSlips(run.id)
}

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

    <div class="card table-wrap">
      <table>
        <thead><tr><th>Period</th><th>Status</th><th class="num">Slips</th><th class="num">Total net pay</th><th></th></tr></thead>
        <tbody>
          <tr v-if="!runs.length"><td colspan="5" class="muted">No payroll runs yet.</td></tr>
          <tr v-for="r in runs" :key="r.id" :class="{ selected: selected?.id === r.id }">
            <td>{{ periodLabel(r.periodYear, r.periodMonth) }}</td>
            <td><span :class="`badge ${r.status.toLowerCase()}`">{{ pretty(r.status) }}</span></td>
            <td class="num">{{ r.slipCount }}</td>
            <td class="num">{{ money(r.totalNet) }}</td>
            <td class="actions">
              <button class="btn small" @click="guarded(() => showSlips(r))">View slips</button>
              <button v-if="r.status === 'DRAFT' && can('payroll:approve')" class="btn small" @click="submit(r)">Submit</button>
              <button v-if="r.status === 'DRAFT' && can('payroll:write')" class="btn small danger" @click="remove(r)">Delete</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <template v-if="selected">
      <h2 class="section">Salary slips · {{ periodLabel(selected.periodYear, selected.periodMonth) }}</h2>
      <div class="card table-wrap">
        <table>
          <thead>
            <tr>
              <th>Employee</th><th class="num">Paid days</th><th class="num">Unpaid days</th>
              <th class="num">Gross</th><th class="num">Deductions</th><th class="num">Net pay</th><th></th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!slips.length"><td colspan="7" class="muted">No slips in this run.</td></tr>
            <tr v-for="s in slips" :key="s.id">
              <td>{{ s.employee.employeeCode }} · {{ s.employee.fullName }}</td>
              <td class="num">{{ s.paymentDays }}</td>
              <td class="num">{{ s.lwpDays }}</td>
              <td class="num">{{ money(s.grossPay) }}</td>
              <td class="num">{{ money(s.totalDeduction) }}</td>
              <td class="num">{{ money(s.netPay) }}</td>
              <td class="actions"><button class="btn small" @click="openSlip = s">View</button></td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>

    <SlipModal v-if="openSlip" :slip="openSlip" @close="openSlip = null" />
  </div>
</template>
