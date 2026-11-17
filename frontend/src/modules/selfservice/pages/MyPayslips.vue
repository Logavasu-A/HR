<script setup>
import { onMounted, ref } from 'vue'
import { listPayslips } from '../services/selfService'
import { errorMessage, money, periodLabel } from '@/shared/utils'
import SlipModal from '@/shared/components/SlipModal.vue'

const slips = ref([])
const openSlip = ref(null)
const error = ref('')
const loading = ref(true)

onMounted(async () => {
  try {
    slips.value = await listPayslips()
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div>
    <div class="page-head"><h1>My payslips</h1></div>
    <div v-if="error" class="alert">{{ error }}</div>
    <div class="card table-wrap">
      <table>
        <thead><tr><th>Period</th><th class="num">Gross</th><th class="num">Deductions</th><th class="num">Net pay</th><th></th></tr></thead>
        <tbody>
          <tr v-if="loading"><td colspan="5" class="muted">Loading…</td></tr>
          <tr v-else-if="!slips.length"><td colspan="5" class="muted">No payslips yet. They appear here once payroll is submitted.</td></tr>
          <template v-else>
            <tr v-for="s in slips" :key="s.id">
              <td>{{ periodLabel(s.periodYear, s.periodMonth) }}</td>
              <td class="num">{{ money(s.grossPay) }}</td>
              <td class="num">{{ money(s.totalDeduction) }}</td>
              <td class="num">{{ money(s.netPay) }}</td>
              <td class="actions"><button class="btn small" @click="openSlip = s">View</button></td>
            </tr>
          </template>
        </tbody>
      </table>
    </div>
    <SlipModal v-if="openSlip" :slip="openSlip" @close="openSlip = null" />
  </div>
</template>
