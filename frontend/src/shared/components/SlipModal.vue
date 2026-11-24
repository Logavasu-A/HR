<script setup>
import { computed } from 'vue'
import { auth } from '@/shared/auth'
import { money, periodLabel } from '@/shared/utils'
import { useWorkspace } from '@/shared/workspaceContext'
import AppDialog from './AppDialog.vue'

const props = defineProps({ slip: { type: Object, required: true } })
defineEmits(['close'])

const workspace = useWorkspace()
const company = computed(() => workspace.owner
  ? workspace.companyName
  : auth.user?.companyName || auth.user?.companyCode)
const initials = computed(() => company.value?.trim().split(/\s+/).slice(0, 2)
  .map((word) => [...word][0]).join('').toUpperCase() || 'HR')
const currency = computed(() => {
  const code = workspace.owner ? workspace.companyDetails?.currency : auth.user?.companyCurrency
  return /^[A-Z]{3}$/.test(code) ? code : null
})
const currencyFormat = computed(() => currency.value && new Intl.NumberFormat(undefined, {
  style: 'currency', currency: currency.value, minimumFractionDigits: 2, maximumFractionDigits: 2,
}))
const currencySymbol = computed(() => currencyFormat.value?.formatToParts(0)
  .find((part) => part.type === 'currency')?.value)
const period = computed(() => periodLabel(props.slip.periodYear, props.slip.periodMonth))
const employee = computed(() => props.slip.employee ?? {})
const joiningDate = computed(() => {
  const date = employee.value.dateOfJoining && new Date(employee.value.dateOfJoining)
  return date && !Number.isNaN(date.getTime())
    ? date.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' })
    : null
})
const breakdowns = computed(() => [
  { label: 'Earnings', rows: props.slip.lines?.filter((line) => line.type === 'EARNING') ?? [],
    totalLabel: 'Gross pay', total: props.slip.grossPay },
  { label: 'Deductions', rows: props.slip.lines?.filter((line) => line.type === 'DEDUCTION') ?? [],
    totalLabel: 'Total deductions', total: props.slip.totalDeduction },
])

const units = ['zero', 'one', 'two', 'three', 'four', 'five', 'six', 'seven', 'eight', 'nine',
  'ten', 'eleven', 'twelve', 'thirteen', 'fourteen', 'fifteen', 'sixteen', 'seventeen', 'eighteen', 'nineteen']
const tens = ['', '', 'twenty', 'thirty', 'forty', 'fifty', 'sixty', 'seventy', 'eighty', 'ninety']
const scales = [[1e12, 'trillion'], [1e9, 'billion'], [1e6, 'million'], [1e3, 'thousand'], [100, 'hundred']]
const minorUnits = { INR: ['paisa', 'paise'], USD: ['cent', 'cents'], EUR: ['cent', 'cents'], GBP: ['penny', 'pence'] }

function integerWords(number) {
  if (number < 20) return units[number]
  if (number < 100) return tens[Math.floor(number / 10)] + (number % 10 ? `-${units[number % 10]}` : '')
  const [divisor, scale] = scales.find(([value]) => number >= value)
  const remainder = number % divisor
  return `${integerWords(Math.floor(number / divisor))} ${scale}${remainder ? ` ${integerWords(remainder)}` : ''}`
}

const netInWords = computed(() => {
  const value = Number(props.slip.netPay)
  if (!currency.value || props.slip.netPay == null || !Number.isFinite(value) || Math.abs(value) >= 1e13) return ''
  const [whole, fraction] = Math.abs(value).toFixed(2).split('.').map(Number)
  const unit = new Intl.NumberFormat('en', {
    style: 'currency', currency: currency.value, currencyDisplay: 'name',
    minimumFractionDigits: 0, maximumFractionDigits: 0,
  })
    .formatToParts(whole).find((part) => part.type === 'currency').value
  const minor = minorUnits[currency.value]
  const fractionText = minor ? `${integerWords(fraction)} ${minor[fraction === 1 ? 0 : 1]}` : `${fraction}/100`
  return `${value < 0 ? 'minus ' : ''}${integerWords(whole)} ${unit}${fraction ? ` and ${fractionText}` : ''} only`
})

function print() {
  window.print()
}
</script>

<template>
  <AppDialog :title="`Payslip · ${period}`" printable @dismiss="$emit('close')">
    <article class="payslip" :aria-label="`Payslip for ${period}`">
      <header class="slip-banner">
        <div class="slip-brand">
          <span class="slip-initials" aria-hidden="true">{{ initials }}</span>
          <div>
            <p class="slip-company">{{ company || 'HRMS' }}</p>
            <p class="slip-masthead-note">Payroll powered by HRMS · People Platform</p>
          </div>
        </div>
        <div class="slip-period">
          <p class="slip-document-title">Payslip</p>
          <p class="slip-masthead-note">{{ period }}</p>
        </div>
      </header>

      <div class="slip-content">
        <dl class="slip-fields">
          <div class="slip-person">
            <dt>Employee</dt>
            <dd>{{ [employee.employeeCode, employee.fullName].filter(Boolean).join(' · ') || '—' }}</dd>
          </div>
          <div><dt>Department</dt><dd>{{ employee.department?.name || '—' }}</dd></div>
          <div><dt>Designation</dt><dd>{{ employee.designation?.name || '—' }}</dd></div>
          <div v-if="joiningDate"><dt>Date of joining</dt><dd>{{ joiningDate }}</dd></div>
        </dl>

        <dl class="slip-fields slip-days">
          <div><dt>Working days</dt><dd>{{ slip.workingDays ?? '—' }}</dd></div>
          <div><dt>Paid days</dt><dd>{{ slip.paymentDays ?? '—' }}</dd></div>
          <div><dt>Unpaid days</dt><dd>{{ slip.lwpDays ?? '—' }}</dd></div>
        </dl>

        <div class="slip-breakdowns">
          <div v-for="group in breakdowns" :key="group.label" class="slip-table-card">
            <table class="slip-table" :aria-label="group.label">
              <thead>
                <tr><th scope="col">{{ group.label }}</th><th scope="col">Amount<span v-if="currencySymbol"> ({{ currencySymbol }})</span></th></tr>
              </thead>
              <tbody>
                <tr v-for="line in group.rows" :key="line.id">
                  <td>{{ line.componentName }}</td><td>{{ money(line.amount) }}</td>
                </tr>
              </tbody>
              <tfoot>
                <tr><th scope="row">{{ group.totalLabel }}</th><td>{{ money(group.total) }}</td></tr>
              </tfoot>
            </table>
          </div>
        </div>

        <div class="slip-net">
          <div class="slip-net-label">
            <span>Net pay</span>
            <p v-if="netInWords">{{ netInWords }}</p>
          </div>
          <strong>{{ slip.netPay == null ? '—' : (currencyFormat?.format(slip.netPay) ?? money(slip.netPay)) }}</strong>
        </div>

        <footer class="slip-signoff">
          <div class="slip-signatures">
            <span>Employee signature</span>
            <span>Authorized signatory</span>
          </div>
          <p>System-generated payslip.</p>
        </footer>
      </div>
    </article>

    <template #footer>
      <button class="btn" @click="$emit('close')">Close</button>
      <button class="btn primary" @click="print">Print / save as PDF</button>
    </template>
  </AppDialog>
</template>

<style scoped>
.payslip {
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--card-bg);
  color: var(--text);
  overflow-wrap: anywhere;
  --slip-gutter: clamp(0.75rem, 3vw, 1.75rem);
}

.slip-banner {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem 1.25rem;
  padding: var(--slip-gutter);
  border-radius: inherit;
  border-end-start-radius: 0;
  border-end-end-radius: 0;
  border-block-end: 0.25rem solid var(--accent);
  background: linear-gradient(110deg, var(--ink), var(--primary));
  color: white;
}

.slip-brand { display: flex; flex: 1 1 15rem; align-items: center; gap: 0.875rem; min-inline-size: 0; }
.slip-initials {
  display: grid;
  place-items: center;
  flex: 0 0 clamp(2.5rem, 6vw, 3.125rem);
  aspect-ratio: 1;
  border-radius: 0.65rem;
  background: var(--sb-accent);
  color: var(--ink);
  font-weight: 800;
  font-size: 1.125rem;
}
.slip-company, .slip-document-title { margin: 0; font-size: clamp(1rem, 2.5vw, 1.375rem); font-weight: 700; line-height: 1.2; }
.slip-document-title, .slip-net span { text-transform: uppercase; letter-spacing: 0.055em; }
.slip-masthead-note { margin: 0.25rem 0 0; color: #c4dfd7; font-size: 0.8125rem; }
.slip-content { display: grid; gap: clamp(1rem, 3vw, 1.5rem); padding: var(--slip-gutter); }

.slip-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.75rem 1rem;
  margin: 0;
}
.slip-person { grid-column: 1 / -1; }
.slip-fields dt { margin-block-end: 0.25rem; color: var(--muted); font-size: 0.8125rem; }
.slip-fields dd { margin: 0; font-weight: 600; }
.slip-days { grid-template-columns: repeat(3, minmax(0, 1fr)); padding-block-start: 1rem; border-block-start: 1px solid var(--border); }

.slip-breakdowns { display: grid; gap: 0.75rem; }
.slip-table-card { min-inline-size: 0; border: 1px solid var(--border); border-radius: 0.625rem; }
.slip-table { inline-size: 100%; border-collapse: separate; border-spacing: 0; font-variant-numeric: tabular-nums; }
.slip-table :is(th, td) { padding: 0.5rem clamp(0.5rem, 1.5vw, 0.875rem); text-align: start; }
.slip-table :is(th, td):last-child { text-align: end; border-inline-start: 1px solid var(--border); }
.slip-table thead { font-size: 0.75rem; text-transform: uppercase; letter-spacing: 0.045em; }
.slip-table :is(thead, tfoot) :is(th, td) { background: var(--page-bg); font-weight: 700; }
.slip-table thead th { border-block-end: 1px solid var(--border); }
.slip-table thead th:first-child { border-start-start-radius: 0.55rem; }
.slip-table thead th:last-child { border-start-end-radius: 0.55rem; }
.slip-table tbody tr + tr td { border-block-start: 1px solid var(--border); }
.slip-table tfoot :is(th, td) { border-block-start: 1px solid var(--border); }
.slip-table tfoot th { border-end-start-radius: 0.55rem; }
.slip-table tfoot td { border-end-end-radius: 0.55rem; }

.slip-net {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.375rem 1rem;
  padding: 0.875rem clamp(0.75rem, 3vw, 1.25rem);
  border-radius: 0.625rem;
  background: var(--primary);
  color: white;
  font-variant-numeric: tabular-nums;
}
.slip-net span { color: #c4dfd7; font-size: 0.8125rem; }
.slip-net-label { flex: 1 1 12rem; }
.slip-net-label p { margin: 0.25rem 0 0; color: #c4dfd7; font-size: 0.8125rem; }
.slip-net-label p::first-letter { text-transform: uppercase; }
.slip-net strong { margin-inline-start: auto; text-align: end; font-size: clamp(1.375rem, 4.5vw, 2rem); }
.slip-signatures { display: grid; grid-template-columns: repeat(2, minmax(0, 11rem)); justify-content: space-between; gap: 1rem; padding-block-start: 1rem; }
.slip-signatures span { padding-block-start: 0.375rem; border-block-start: 1px solid var(--muted); text-align: center; font-size: 0.8125rem; }
.slip-signoff p { margin: 1rem 0 0; text-align: center; color: var(--muted); font-size: 0.75rem; font-style: italic; }

@media (min-width: 640px), print {
  .slip-period { text-align: end; }
  .slip-fields { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .slip-person { grid-column: auto; }
  .slip-breakdowns { grid-template-columns: repeat(2, minmax(0, 1fr)); align-items: start; }
}

@media print {
  .payslip { --slip-gutter: 1.25rem; print-color-adjust: exact; }
  .slip-banner, .slip-fields, .slip-net, .slip-signoff, .slip-table tr { break-inside: avoid; }
}
</style>
