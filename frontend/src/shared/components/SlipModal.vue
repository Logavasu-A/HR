<script setup>
import { computed, onMounted, onUnmounted } from 'vue'
import { money, periodLabel } from '@/shared/utils'

const props = defineProps({ slip: { type: Object, required: true } })
const emit = defineEmits(['close'])

const earnings = computed(() => props.slip.lines.filter((l) => l.type === 'EARNING'))
const deductions = computed(() => props.slip.lines.filter((l) => l.type === 'DEDUCTION'))

function print() {
  window.print()
}

function onKeydown(e) {
  if (e.key === 'Escape') emit('close')
}

onMounted(() => document.addEventListener('keydown', onKeydown))
onUnmounted(() => document.removeEventListener('keydown', onKeydown))
</script>

<template>
  <div class="modal-backdrop" role="dialog" aria-modal="true" @click.self="emit('close')">
    <div class="modal printable">
      <div class="modal-head">
        <h2>Payslip · {{ periodLabel(slip.periodYear, slip.periodMonth) }}</h2>
        <button type="button" class="modal-close no-print" aria-label="Close payslip" @click="emit('close')">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <line x1="18" y1="6" x2="6" y2="18" />
            <line x1="6" y1="6" x2="18" y2="18" />
          </svg>
        </button>
      </div>

      <div class="modal-body">
        <div class="slip-head">
          <div class="slip-meta-item">
            <span class="slip-meta-label">Employee</span>
            <span class="slip-meta-value">{{ slip.employee.employeeCode }} · {{ slip.employee.fullName }}</span>
          </div>
          <div class="slip-meta-item">
            <span class="slip-meta-label">Department</span>
            <span class="slip-meta-value">{{ slip.employee.department?.name || '—' }}</span>
          </div>
          <div class="slip-meta-item">
            <span class="slip-meta-label">Designation</span>
            <span class="slip-meta-value">{{ slip.employee.designation?.name || '—' }}</span>
          </div>
          <div class="slip-meta-item">
            <span class="slip-meta-label">Working days</span>
            <span class="slip-meta-value">{{ slip.workingDays }}</span>
          </div>
          <div class="slip-meta-item">
            <span class="slip-meta-label">Paid days</span>
            <span class="slip-meta-value">{{ slip.paymentDays }}</span>
          </div>
          <div class="slip-meta-item">
            <span class="slip-meta-label">Unpaid days</span>
            <span class="slip-meta-value">{{ slip.lwpDays }}</span>
          </div>
        </div>

        <div class="slip-cols">
          <div class="table-card">
            <div class="table-wrapper table-wrapper--auto">
              <table class="data-table data-table--compact">
                <thead><tr><th>Earnings</th><th class="num">Amount</th></tr></thead>
                <tbody>
                  <tr v-for="l in earnings" :key="l.id"><td>{{ l.componentName }}</td><td class="num">{{ money(l.amount) }}</td></tr>
                  <tr class="total"><td>Gross pay</td><td class="num">{{ money(slip.grossPay) }}</td></tr>
                </tbody>
              </table>
            </div>
          </div>
          <div class="table-card">
            <div class="table-wrapper table-wrapper--auto">
              <table class="data-table data-table--compact">
                <thead><tr><th>Deductions</th><th class="num">Amount</th></tr></thead>
                <tbody>
                  <tr v-for="l in deductions" :key="l.id"><td>{{ l.componentName }}</td><td class="num">{{ money(l.amount) }}</td></tr>
                  <tr class="total"><td>Total deductions</td><td class="num">{{ money(slip.totalDeduction) }}</td></tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>

        <div class="slip-net">
          <span class="slip-net-label">Net pay</span>
          <span class="slip-net-value">{{ money(slip.netPay) }}</span>
        </div>
      </div>

      <div class="modal-foot no-print">
        <button type="button" class="btn" @click="emit('close')">Close</button>
        <button type="button" class="btn primary" @click="print">Print / save as PDF</button>
      </div>
    </div>
  </div>
</template>
