<script setup>
import { onMounted, ref } from 'vue'
import { listPayslips } from '../services/selfService'
import { errorMessage, money, periodLabel } from '@/shared/utils'
import SlipModal from '@/shared/components/SlipModal.vue'
import DataTable from '@/shared/components/DataTable.vue'
import ActionMenu from '@/shared/components/ActionMenu.vue'
import PaginationBar from '@/shared/components/PaginationBar.vue'

const columns = [
  { key: 'periodYear', label: 'Period', nowrap: true },
  { key: 'grossPay', label: 'Gross', money: true },
  { key: 'totalDeduction', label: 'Deductions', money: true },
  { key: 'netPay', label: 'Net pay', money: true },
]

const slips = ref([])
const openSlip = ref(null)
const error = ref('')
const loading = ref(true)
const page = ref(0)
const pageSize = ref(20)
const totalElements = ref(0)
const totalPages = ref(0)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await listPayslips(page.value, pageSize.value)
    if (Array.isArray(data)) {
      slips.value = data
      totalElements.value = data.length
      totalPages.value = data.length ? 1 : 0
    } else {
      slips.value = data.content || []
      totalElements.value = data.totalElements ?? slips.value.length
      totalPages.value = data.totalPages ?? (slips.value.length ? 1 : 0)
    }
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}

function onPageChange(p) {
  page.value = p
  load()
}

function onPageSizeChange(s) {
  pageSize.value = s
  page.value = 0
  load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head"><h1>My payslips</h1></div>
    <div v-if="error" class="alert">{{ error }}</div>
    <DataTable
      :columns="columns"
      :rows="slips"
      :loading="loading"
      empty-text="No payslips yet. They appear here once payroll is submitted."
      aria-label="My payslips"
    >
      <template #cell-periodYear="{ row }">{{ periodLabel(row.periodYear, row.periodMonth) }}</template>
      <template #cell-grossPay="{ row }">{{ money(row.grossPay) }}</template>
      <template #cell-totalDeduction="{ row }">{{ money(row.totalDeduction) }}</template>
      <template #cell-netPay="{ row }">{{ money(row.netPay) }}</template>
      <template #actions="{ row }">
        <ActionMenu :items="[{ label: 'View payslip', onClick: () => (openSlip = row) }]" />
      </template>
      <template #footer>
        <PaginationBar
          v-if="!loading && totalElements > 0"
          :page="page"
          :page-size="pageSize"
          :total-elements="totalElements"
          :total-pages="totalPages"
          :loading="loading"
          @update:page="onPageChange"
          @update:page-size="onPageSizeChange"
        />
      </template>
    </DataTable>
    <SlipModal v-if="openSlip" :slip="openSlip" @close="openSlip = null" />
  </div>
</template>
