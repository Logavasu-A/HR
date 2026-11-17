<script setup>
import { computed } from 'vue'
import { money, periodLabel } from '@/shared/utils'

const props = defineProps({ slip: { type: Object, required: true } })
defineEmits(['close'])

const earnings = computed(() => props.slip.lines.filter((l) => l.type === 'EARNING'))
const deductions = computed(() => props.slip.lines.filter((l) => l.type === 'DEDUCTION'))

function print() {
  window.print()
}
</script>

<template>
  <div class="modal-backdrop" @click.self="$emit('close')">
    <div class="modal printable">
      <h2>Payslip · {{ periodLabel(slip.periodYear, slip.periodMonth) }}</h2>
      <div class="slip-head">
        <div><span class="muted">Employee</span><br />{{ slip.employee.employeeCode }} · {{ slip.employee.fullName }}</div>
        <div><span class="muted">Department</span><br />{{ slip.employee.department?.name || '—' }}</div>
        <div><span class="muted">Designation</span><br />{{ slip.employee.designation?.name || '—' }}</div>
        <div><span class="muted">Working days</span><br />{{ slip.workingDays }}</div>
        <div><span class="muted">Paid days</span><br />{{ slip.paymentDays }}</div>
        <div><span class="muted">Unpaid days</span><br />{{ slip.lwpDays }}</div>
      </div>

      <div class="slip-cols">
        <table>
          <thead><tr><th>Earnings</th><th class="num">Amount</th></tr></thead>
          <tbody>
            <tr v-for="l in earnings" :key="l.id"><td>{{ l.componentName }}</td><td class="num">{{ money(l.amount) }}</td></tr>
            <tr class="total"><td>Gross pay</td><td class="num">{{ money(slip.grossPay) }}</td></tr>
          </tbody>
        </table>
        <table>
          <thead><tr><th>Deductions</th><th class="num">Amount</th></tr></thead>
          <tbody>
            <tr v-for="l in deductions" :key="l.id"><td>{{ l.componentName }}</td><td class="num">{{ money(l.amount) }}</td></tr>
            <tr class="total"><td>Total deductions</td><td class="num">{{ money(slip.totalDeduction) }}</td></tr>
          </tbody>
        </table>
      </div>

      <div class="net">Net pay: {{ money(slip.netPay) }}</div>

      <div class="modal-foot no-print">
        <button class="btn" @click="$emit('close')">Close</button>
        <button class="btn primary" @click="print">Print / save as PDF</button>
      </div>
    </div>
  </div>
</template>
