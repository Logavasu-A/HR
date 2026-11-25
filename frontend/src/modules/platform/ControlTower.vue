<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { MONTHS, pretty } from '@/shared/utils'
import PlatformState from './PlatformState.vue'
import PlatformPageHeader from './PlatformPageHeader.vue'
import AppDialog from '@/shared/components/AppDialog.vue'

const router = useRouter()
const route = useRoute()
const currentYear = new Date().getFullYear()
const year = ref(currentYear)
const month = ref('')
const data = ref(null)
const loading = ref(true)
const error = ref('')
const search = ref('')
const sort = ref('employees')
const metric = ref('workforce')
const unlockOpen = ref(false)
const financePassword = ref('')
const showFinancePassword = ref(false)
const financeError = ref('')
const verifying = ref(false)
let request

function closeUnlockModal() {
  if (verifying.value) return
  unlockOpen.value = false
  financePassword.value = ''
  showFinancePassword.value = false
  financeError.value = ''
}

const number = new Intl.NumberFormat(undefined, { maximumFractionDigits: 1 })
const count = value => number.format(value ?? 0)
const money = (value, currency) => value == null ? 'Unavailable'
  : new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(value)
const companies = computed(() => data.value?.companies ?? [])
const company = computed(() => route.query.company && companies.value[0])
const periodQuery = computed(() => ({ year: route.query.year, month: route.query.month }))
const total = (field) => companies.value.reduce((sum, row) => sum + (row[field] ?? 0), 0)
const aggregate = (field) => companies.value.reduce((sum, row) => sum + (row.counts[field] ?? 0), 0)
const workedRate = (worked, recorded) => recorded ? `${count(worked * 100 / recorded)}%` : '—'
const active = computed(() => companies.value.filter(row => row.status === 'ACTIVE').length)
const pending = computed(() => companies.value.filter(row => row.status === 'PENDING').length)
const filtered = computed(() => {
  const term = search.value.trim().toLowerCase()
  return companies.value.filter(row => `${row.name} ${row.code}`.toLowerCase().includes(term))
    .sort((a, b) => ((sort.value === 'employees' && b.employees - a.employees) ||
      (sort.value === 'attendance' && (b.counts.attendanceRecords ?? 0) - (a.counts.attendanceRecords ?? 0))) ||
      a.name.localeCompare(b.name))
})
const chartOptions = {
  workforce: [{ key: 'joiners', label: 'Joiners' }, { key: 'leavers', label: 'Leavers' }],
  attendance: [{ key: 'worked', label: 'Worked marks' }, { key: 'absences', label: 'Absences' }],
  leave: [{ key: 'leaveRequests', label: 'Requests' }, { key: 'leaveApproved', label: 'Approved' }],
  recruitment: [{ key: 'jobsPosted', label: 'Jobs posted' }, { key: 'applicants', label: 'Applicants' }],
}
const bars = computed(() => chartOptions[metric.value])
const chartMonths = computed(() => (data.value?.months ?? []).map(row => ({
  month: row.month,
  values: bars.value.map(bar => ({ ...bar, value: Number(row.counts[bar.key] ?? 0) })),
})))
const chartMax = computed(() => {
  const max = Math.max(0, ...chartMonths.value.flatMap(row => row.values.map(bar => bar.value)))
  if (max < 4) return 4
  const magnitude = 10 ** Math.floor(Math.log10(max / 4))
  return Math.ceil(max / (4 * magnitude)) * 4 * magnitude
})
const chartTicks = computed(() => [1, .75, .5, .25, 0].map(value => chartMax.value * value))
const chartHasData = computed(() => chartMonths.value.some(row => row.values.some(bar => bar.value > 0)))
const invitationAlerts = computed(() => companies.value.filter(row => row.invitationAlert))
const pendingLeave = computed(() => aggregate('leavePending'))
const periodLabel = value => MONTHS[Number(value.slice(-2)) - 1]

function applyPeriod() {
  router.push({ query: { company: route.query.company, year: year.value, month: month.value || undefined } })
}

async function unlockPayroll() {
  const token = sessionStorage.getItem('hrms_platform_token')
  verifying.value = true
  financeError.value = ''
  try {
    const response = await fetch('/api/platform/control-tower/step-up', {
      method: 'POST', cache: 'no-store',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ password: financePassword.value }),
    })
    const result = await response.json().catch(() => ({}))
    if (!response.ok || !result.verified) throw new Error('Verification failed or is temporarily locked.')
    unlockOpen.value = false
    await load()
  } catch (failure) {
    financeError.value = failure.message
  } finally {
    financePassword.value = ''
    verifying.value = false
  }
}

async function load() {
  request?.abort()
  const controller = new AbortController()
  request = controller
  loading.value = true
  error.value = ''
  data.value = null
  year.value = Number(route.query.year) || currentYear
  month.value = route.query.month || ''
  search.value = ''
  const token = sessionStorage.getItem('hrms_platform_token')
  if (!token) {
    router.replace('/platform/login')
    return
  }
  try {
    const query = new URLSearchParams({ year: year.value })
    if (month.value) query.set('month', month.value)
    if (route.query.company) query.set('companyId', route.query.company)
    const response = await fetch(`/api/platform/control-tower?${query}`, {
      signal: controller.signal, cache: 'no-store', headers: { Authorization: `Bearer ${token}` },
    })
    if (controller.signal.aborted) return
    if (response.status === 401) {
      if (sessionStorage.getItem('hrms_platform_token') === token) sessionStorage.removeItem('hrms_platform_token')
      router.replace('/platform/login')
      return
    }
    if (response.status === 404 && !route.query.company) {
      router.replace('/platform/companies')
      return
    }
    const payload = await response.json()
    if (!response.ok) throw new Error(payload.message || 'The report could not be loaded.')
    if (!controller.signal.aborted) data.value = payload
  } catch (failure) {
    if (!controller.signal.aborted) error.value = failure.message
  } finally {
    if (!controller.signal.aborted) loading.value = false
  }
}

watch(() => [route.query.year, route.query.month, route.query.company], load, { immediate: true })
onBeforeUnmount(() => request?.abort())
</script>

<template>
  <section class="tower">
    <PlatformPageHeader :title="company?.name || 'Global Control Tower'">
      <template #back>
        <router-link v-if="route.query.company" :to="{ query: periodQuery }" class="tower-back">← Global Control Tower</router-link>
      </template>
        <router-link v-if="!route.query.company" class="btn primary" to="/platform/workspace">Admin View</router-link>
        <router-link v-else-if="company" class="btn primary" :to="{ path: '/platform/workspace', query: { company: company.id } }">
          Open Admin View
        </router-link>
    </PlatformPageHeader>

    <form class="card tower-period" aria-label=" Reporting period " @submit.prevent="applyPeriod">
      <div class="period-context">
        <h2 class="period-title">Reporting period</h2>
        <small v-if="company" class="control-note">{{ company.code }} · {{ pretty(company.status) }} · {{ company.timezone }}</small>
        
      </div>
      <div class="field">
        <label for="tower-year">Year</label>
        <input id="tower-year" v-model="year" type="number" min="1" max="9999" required />
      </div>
      <div class="field">
        <label for="tower-month">Month</label>
        <select id="tower-month" v-model="month">
          <option value="">Full year</option>
          <option v-for="(name, i) in MONTHS" :key="name" :value="String(i + 1)">{{ name }}</option>
        </select>
      </div>
      <button class="btn primary" type="submit" :disabled="loading">Apply period</button>
    </form>

    <PlatformState v-if="loading" kind="loading" title="Loading HR metrics" message="Fetching data for the selected period." />
    <PlatformState v-else-if="error" kind="error" title="HR metrics could not be loaded" :message="error">
      <button class="btn small" @click="load">Retry</button>
      <router-link v-if="route.query.company" class="btn small" :to="{ query: periodQuery }">All companies</router-link>
    </PlatformState>
    <PlatformState v-else-if="data && !companies.length" title="No companies registered">
      <router-link class="btn small" to="/platform/companies">Open company registry</router-link>
    </PlatformState>

    <template v-if="data && companies.length">
      <section class="tower-kpis" aria-label="Key HR metrics">
        <article v-if="!company" class="card kpi">
          <p>Registered companies</p><strong>{{ count(companies.length) }}</strong>
          <small>{{ count(active) }} active · {{ count(pending) }} pending · {{ count(companies.length - active - pending) }} suspended</small>
        </article>
        <article class="card kpi">
          <p>Employees</p><strong>{{ count(total('employees')) }}</strong>
          <small>{{ count(total('activeEmployees')) }} active now</small>
        </article>
        <article class="card kpi">
          <p>Attendance marks</p><strong>{{ count(aggregate('attendanceRecords')) }}</strong>
          <small>{{ count(aggregate('worked')) }} worked · {{ count(aggregate('absences')) }} absent</small>
        </article>
        <article class="card kpi">
          <p>Leave requests</p><strong>{{ count(aggregate('leaveRequests')) }}</strong>
          <small>{{ count(aggregate('leaveDays')) }} approved days · {{ count(aggregate('leavePending')) }} pending</small>
        </article>
        <article class="card kpi">
          <p>Open vacancies</p><strong>{{ count(total('vacancies')) }}</strong>
          <small>{{ count(total('openJobs')) }} open jobs · {{ count(aggregate('applicants')) }} applicants in period</small>
        </article>
      </section>

      <div class="tower-panels">
        <section class="card tower-panel activity-panel" :data-metric="metric" aria-labelledby="activity-title">
          <header class="tower-panel-head">
            <h2 id="activity-title">Monthly activity</h2>
            <select v-model="metric" aria-label="Activity metric">
              <option value="workforce">Workforce movement</option>
              <option value="attendance">Attendance</option>
              <option value="leave">Leave</option>
              <option value="recruitment">Recruitment</option>
            </select>
          </header>
          <div class="tower-legend">
            <span v-for="(bar, i) in bars" :key="bar.key"><i :class="{ alternate: i }"></i>{{ bar.label }}</span>
          </div>
          <p v-if="!chartHasData" class="chart-empty" role="status">No activity for this metric in the selected period.</p>
          <div class="chart-scroll" tabindex="0" role="region" aria-label="Monthly activity chart">
            <div class="tower-chart" :style="{ '--months': chartMonths.length }">
              <div class="chart-guides" aria-hidden="true">
                <div v-for="tick in chartTicks" :key="tick"><span>{{ count(tick) }}</span></div>
              </div>
              <ul class="chart-columns">
                <li v-for="row in chartMonths" :key="row.month" class="chart-row" tabindex="0"
                  :aria-label="periodLabel(row.month) + '. ' + row.values.map(bar => bar.label + ' ' + count(bar.value)).join('. ')">
                  <div class="chart-bars" aria-hidden="true">
                    <span v-for="bar in row.values" :key="bar.key" class="chart-bar"
                      :style="{ height: (bar.value / chartMax * 100) + '%' }" />
                  </div>
                  <time :datetime="row.month" aria-hidden="true">{{ periodLabel(row.month).slice(0, 3) }}</time>
                  <div class="chart-tooltip" aria-hidden="true">
                    <strong>{{ periodLabel(row.month) }}</strong>
                    <span v-for="bar in row.values" :key="bar.key">{{ bar.label }} <b>{{ count(bar.value) }}</b></span>
                  </div>
                </li>
              </ul>
            </div>
          </div>
        </section>

        <div class="tower-side">
          <section class="card tower-panel payroll-panel">
            <header class="tower-panel-head">
              <h2>Submitted payroll</h2>
              <span v-if="data.payrollLocked" class="payroll-lock">Locked</span>
            </header>
            <template v-if="data.payrollLocked">
              <p class="support-copy">Verify your platform password to unlock submitted payroll totals.</p>
              <button class="btn small primary" type="button" @click="unlockOpen = true">Unlock payroll totals</button>
            </template>
            <p v-else-if="!data.payroll.length" class="muted">No submitted payroll in this period.</p>
            <ul v-else class="tower-values">
              <li v-for="item in data.payroll" :key="item.currency">
                <div><strong>{{ item.currency }}</strong><small>{{ count(item.runs) }} submitted runs</small></div>
                <strong>{{ money(item.net, item.currency) }}</strong>
              </li>
            </ul>
            <details v-if="data.payroll.length" class="tower-details">
              <summary>Monthly payroll</summary>
              <ul class="tower-values">
                <li v-for="row in data.months.filter(row => row.payroll.length)" :key="row.month">
                  <time :datetime="row.month">{{ periodLabel(row.month) }}</time>
                  <div class="tower-amounts"><span v-for="item in row.payroll" :key="item.currency">{{ item.currency }} {{ money(item.net, item.currency) }}</span></div>
                </li>
              </ul>
            </details>
          </section>

          <section class="card tower-panel alerts-panel">
            <header class="tower-panel-head">
              <h2>Alerts and notifications</h2>
              <span v-if="invitationAlerts.length" class="badge pending">{{ invitationAlerts.length }} alerts</span>
            </header>
            <dl class="alert-summary">
              <div><dt>Unread notifications</dt><dd>{{ count(total('unread')) }}</dd></div>
              <div><dt>Pending leave in period</dt><dd>{{ count(pendingLeave) }}</dd></div>
            </dl>
            <p v-if="!invitationAlerts.length" class="support-copy">No invitation alerts.</p>
            <ul v-else class="tower-values tower-alerts-list">
              <li v-for="row in invitationAlerts" :key="row.id" class="invitation-row">
                <router-link :to="{ query: { ...periodQuery, company: row.id } }">{{ row.name }}</router-link>
                <span class="badge tower-alert-badge" :class="row.invitationAlert === 'EXPIRED' ? 'pending' : 'rejected'">
                  {{ row.invitationAlert === 'EXPIRED' ? 'Invitation expired' : 'Delivery failed' }}
                </span>
              </li>
            </ul>
          </section>
        </div>
      </div>

      <section class="card tower-panel company-panel">
        <header class="tower-panel-head">
          <h2>{{ company ? 'Department distribution' : 'Company comparison' }}</h2>
          <div v-if="!company" class="tower-actions">
            <input v-model="search" class="search" type="search" aria-label="Filter comparison" placeholder="Search companies" />
            <select v-model="sort" aria-label="Sort comparison">
              <option value="employees">Most employees</option><option value="attendance">Most attendance marks</option><option value="name">Name A–Z</option>
            </select>
          </div>
        </header>
        <div class="table-wrapper" tabindex="0" role="region" :aria-label="company ? 'Departments' : 'Company comparison'">
          <table v-if="company" class="data-table tower-table responsive-table data-table--records" role="table">
            <thead><tr><th scope="col">Department</th><th scope="col" class="num">Employees</th><th scope="col" class="num">Active</th></tr></thead>
            <tbody role="rowgroup">
              <tr v-for="(department, i) in company.departments" :key="i" role="row">
                <td data-label="Department" role="cell"><span class="table-cell-value">{{ department.name }}</span></td>
                <td data-label="Employees" class="num" role="cell"><span class="table-cell-value">{{ count(department.employees) }}</span></td>
                <td data-label="Active" class="num" role="cell"><span class="table-cell-value">{{ count(department.active) }}</span></td>
              </tr>
              <tr v-if="!company.departments.length" class="table-state-row"><td class="empty" colspan="3">
                <div class="table-state-message">This company has no employees.</div>
              </td></tr>
            </tbody>
          </table>
          <table v-else class="data-table tower-table responsive-table data-table--records" role="table">
            <thead><tr>
              <th scope="col" class="company-col">Company</th><th scope="col" class="num">Employees</th><th scope="col" class="num">Active</th><th scope="col" class="num">Worked share</th>
              <th scope="col" class="num">Approved leave days</th><th scope="col" class="num">Applicants</th><th scope="col" class="num">Net payroll</th>
              <th scope="col" class="actions-col">Actions</th>
            </tr></thead>
            <tbody role="rowgroup">
              <tr v-for="row in filtered" :key="row.id" role="row">
                <td data-label="Company" class="company-cell" role="cell">
                  <div class="table-cell-value company-cell-inner">
                    <router-link class="company-name-link" :to="{ path: '/platform/workspace', query: { company: row.id } }">{{ row.name }}</router-link>
                    <div class="company-meta">
                      <span class="badge" :class="row.status.toLowerCase()">{{ pretty(row.status) }}</span>
                      <span class="company-currency-tag">{{ row.currency }}</span>
                    </div>
                  </div>
                </td>
                <td data-label="Employees" class="num" role="cell"><span class="table-cell-value">{{ count(row.employees) }}</span></td>
                <td data-label="Active" class="num" role="cell"><span class="table-cell-value">{{ count(row.activeEmployees) }}</span></td>
                <td data-label="Worked share" class="num" role="cell"><span class="table-cell-value">{{ workedRate(row.counts.worked, row.counts.attendanceRecords) }}</span></td>
                <td data-label="Approved leave days" class="num" role="cell"><span class="table-cell-value">{{ count(row.counts.leaveDays) }}</span></td>
                <td data-label="Applicants" class="num" role="cell"><span class="table-cell-value">{{ count(row.counts.applicants) }}</span></td>
                <td data-label="Net payroll" class="num" role="cell"><span class="table-cell-value">{{ data.payrollLocked ? 'Locked' : row.payrollRuns ? money(row.netPayroll, row.currency) : '—' }}</span></td>
                <td class="actions" role="cell"><router-link class="btn small company-metrics-link" :aria-label="`View metrics for ${row.name}`"
                  :to="{ query: { ...periodQuery, company: row.id } }">View metrics</router-link></td>
              </tr>
              <tr v-if="!filtered.length" class="table-state-row"><td class="empty" colspan="8">
                <div class="table-state-message">No companies match your search.</div>
              </td></tr>
            </tbody>
          </table>
        </div>
        <details class="tower-details muted">
          <summary>How metrics are counted</summary>
          <p>Employee, vacancy and unread counts reflect current records. Worked share divides present, work-from-home and half-day marks by all recorded marks. Leave is grouped by start date. Recruitment uses posting and application dates.</p>
          <p>Payroll sums recorded net amounts from submitted runs by each company’s configured currency. Missing amounts are excluded and currencies are kept separate.</p>
        </details>
      </section>
    </template>

    <AppDialog v-if="unlockOpen" title="Verify platform password" form @dismiss="closeUnlockModal" @submit="unlockPayroll">
      <div class="unlock-modal-body" :aria-busy="verifying">
        <p class="unlock-note">Enter your current Super Admin password to unlock submitted payroll totals for this session.</p>
        <p v-if="financeError" class="alert" role="alert">{{ financeError }}</p>
        <div class="field">
          <label for="tower-password">Current password</label>
          <div class="unlock-pw-wrap">
            <input id="tower-password" v-model="financePassword" :type="showFinancePassword ? 'text' : 'password'"
              autocomplete="current-password" maxlength="72" :disabled="verifying" required autofocus />
            <button type="button" class="unlock-eye-btn" :disabled="verifying"
              @click="showFinancePassword = !showFinancePassword">
              {{ showFinancePassword ? 'Hide' : 'Show' }}
            </button>
          </div>
        </div>
      </div>
      <template #footer>
        <button class="btn" type="button" :disabled="verifying" @click="closeUnlockModal">Cancel</button>
        <button class="btn primary" type="submit" :disabled="verifying || !financePassword">
          {{ verifying ? 'Verifying…' : 'Verify & unlock' }}
        </button>
      </template>
    </AppDialog>
  </section>
</template>

<style scoped>
.tower { display: grid; grid-template-columns: minmax(0, 1fr); gap: 16px; }
.tower > :first-child { margin-bottom: 0; }
.tower-panel-head, .tower-actions, .tower-period { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; }
.tower-panel-head { justify-content: space-between; }
.tower-back { display: inline-block; font-size: var(--platform-body-size); }
.tower-period { display: grid; grid-template-columns: minmax(0, 1fr);
  padding: var(--platform-card-padding); align-items: end; column-gap: 14px; }
.period-context { align-self: center; min-width: 0; overflow-wrap: anywhere; }
.period-title { font-size: 14px; margin: 0; }
.tower-period .field { margin: 0; }
.tower-period :is(input, select) { width: 100%; min-width: 0; }
.tower-period button { min-height: var(--platform-control-default); }
.tower-kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 178px), 1fr)); gap: 14px; }
.kpi {
  padding: 18px 20px;
  display: grid;
  align-content: start;
  gap: 6px;
  min-width: 0;
  border-top: 3px solid var(--border);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}
.kpi:hover { transform: translateY(-1px); box-shadow: var(--shadow-menu); }
.kpi:first-child { background: var(--primary-tint); border-color: var(--primary-tint-2); border-top-color: var(--primary); }
.kpi:nth-child(2) { border-top-color: var(--ok-line); }
.kpi:nth-child(3) { border-top-color: var(--accent-strong); }
.kpi:nth-child(4) { border-top-color: var(--primary-hover); }
.kpi:nth-child(5) { border-top-color: var(--bad-line); }
.kpi p { margin: 0; color: var(--muted); font-size: 13px; font-weight: 500; }
.kpi strong { font-size: 1.85rem; line-height: 1.15; font-variant-numeric: tabular-nums; letter-spacing: -.04em; color: var(--text); }
.kpi small, .tower-values small, .tower-table small { color: var(--muted); }
.unlock-modal-body { display: grid; gap: 14px; }
.unlock-modal-body .field, .unlock-modal-body .alert { margin: 0; }
.unlock-note { margin: 0; color: var(--muted); font-size: 13.5px; line-height: 1.5; }
.unlock-pw-wrap { position: relative; }
.unlock-pw-wrap input { width: 100%; padding-right: 64px; }
.unlock-eye-btn {
  position: absolute; right: 6px; top: 50%; transform: translateY(-50%);
  height: 28px; padding: 0 8px; border: 0; background: transparent;
  color: var(--primary); font: inherit; font-size: 12.5px; font-weight: 600;
  cursor: pointer; border-radius: 6px;
}
.unlock-eye-btn:hover { background: var(--row-hover); }
:global(.modal:has(.unlock-modal-body)) { inline-size: min(29rem, calc(100% - 2 * var(--modal-gutter))); }
.tower-panels { display: grid; grid-template-columns: minmax(0, 1fr); align-items: stretch; gap: 16px; }
.tower-side { display: grid; gap: 16px; min-width: 0; align-content: start; }
.tower-panel { min-width: 0; padding: var(--platform-card-padding); }
.tower-panel h2 { margin: 0; font-size: var(--platform-section-size); letter-spacing: -.015em; }
.tower-panel-head { margin-bottom: 12px; }
.tower-panel-head select { min-width: 0; max-width: 100%; width: auto; }
.company-panel { overflow: hidden; }
.company-panel .table-wrapper { border: 1px solid var(--line); border-radius: var(--radius-sm); overflow-x: auto; }

.tower-table th,
.tower-table td {
  text-align: center !important;
  vertical-align: middle !important;
  padding: 10px 12px !important;
}
.tower-table th {
  white-space: nowrap !important;
  font-size: 11.5px;
  letter-spacing: 0.03em;
}
.tower-table td.num { text-align: center !important; }
.tower-table th.actions-col,
.tower-table td.actions {
  position: static !important;
  text-align: center !important;
  width: 7.5rem;
}
.company-col { min-width: 175px; }
.company-cell-inner { display: flex; flex-direction: column; align-items: center; gap: 5px; }
.company-name-link { font-weight: 650; line-height: 1.3; }
.company-meta { display: flex; align-items: center; justify-content: center; flex-wrap: wrap; gap: 6px; margin-top: 0; }
.company-currency-tag {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11px;
  font-weight: 600;
  line-height: 1.4;
  padding: 1px 6px;
  border-radius: 4px;
  background: var(--page-bg);
  border: 1px solid var(--line);
  color: var(--muted);
}
.badge.suspended { background: var(--bad-bg); color: var(--bad-text); }
.company-metrics-link { max-inline-size: 100%; white-space: nowrap; }
.tower-table a { text-decoration: none; }
.tower-table a:hover { text-decoration: underline; }
.tower-legend, .tower-values { margin: 12px 0 0; padding: 0; list-style: none; }
.tower-legend { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; font-size: 13px; color: var(--muted); }
.tower-legend span { display: flex; align-items: center; gap: 7px; }
.tower-legend i { width: 9px; height: 9px; border-radius: 3px; }
.activity-panel { display: flex; flex-direction: column; --chart-first: var(--primary); --chart-second: var(--text-muted); }
.activity-panel[data-metric=workforce] { --chart-first: var(--ok-line); --chart-second: var(--bad-line); }
.activity-panel[data-metric=attendance] { --chart-second: var(--bad-line); }
.activity-panel[data-metric=leave] { --chart-first: var(--accent-strong); --chart-second: var(--ok-line); }
.tower-legend i, .chart-bar { background: var(--chart-first); }
.tower-legend i.alternate, .chart-bar:nth-child(2) { background: var(--chart-second); }
.chart-empty { margin: 14px 0 0; color: var(--muted); font-size: 13px; }
.chart-scroll { container-type: inline-size; min-inline-size: 0; flex: 1; display: flex; flex-direction: column; justify-content: center; margin-top: 10px; border-radius: 6px; }
.tower-chart { --plot-height: 15.5rem; position: relative; padding: 14px 10px 8px 34px; }
.chart-guides { position: absolute; inset: 14px 10px auto 34px; height: var(--plot-height); display: flex; flex-direction: column; justify-content: space-between; pointer-events: none; }
.chart-guides div { position: relative; border-top: 1px solid var(--line); height: 0; }
.chart-guides span { position: absolute; inset-inline-end: calc(100% + 7px); top: 0; transform: translateY(-50%); color: var(--muted); font-size: 11px; font-variant-numeric: tabular-nums; }
.chart-columns { position: relative; display: grid; grid-template-columns: repeat(var(--months), minmax(0, 1fr)); padding: 0; margin: 0; list-style: none; }
.chart-row { position: relative; min-width: 0; border-radius: 5px; }
.chart-row:hover, .chart-row:focus-visible { background: var(--row-hover); z-index: 1; }
.chart-bars { display: flex; height: var(--plot-height); align-items: end; justify-content: center; gap: 3px; padding-inline: 3px; }
.chart-bar { display: block; width: min(28%, 22px); border-radius: 4px 4px 0 0; transition: height 0.25s ease; }
.chart-row time { display: block; text-align: center; color: var(--muted); font-size: 11px; padding-top: 7px; }
.chart-tooltip { display: none; position: absolute; z-index: 2; top: 8px; left: 0; width: max-content; max-width: min(9rem, 40cqi); overflow-wrap: anywhere; padding: 7px 9px; border-radius: 7px; background: var(--ink); color: var(--card-bg); font-size: 12px; pointer-events: none; }
.chart-tooltip span { display: flex; justify-content: space-between; gap: 8px; }
.chart-row:hover .chart-tooltip, .chart-row:focus-visible .chart-tooltip { display: grid; gap: 2px; }
.chart-row:nth-child(n+7) .chart-tooltip { right: 4px; left: auto; }
.support-copy { color: var(--muted); margin: 8px 0 12px; font-size: 13px; }
.payroll-lock { display: inline-flex; align-items: center; gap: 6px; min-height: 24px; border-radius: 999px; background: var(--idle-bg); color: var(--idle-text); padding: 0 9px; font-size: 12px; font-weight: 650; }
.alert-summary { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; margin: 12px 0; }
.alert-summary div { padding: 10px 12px; border: 1px solid var(--line); border-radius: 8px; background: var(--page-bg); }
.alert-summary dt { color: var(--muted); font-size: 12px; }
.alert-summary dd { margin: 4px 0 0; font-size: 1.2rem; font-weight: 700; font-variant-numeric: tabular-nums; }
.tower-alerts-list { max-height: 195px; overflow-y: auto; padding-right: 4px; }
.tower-amounts { display: grid; gap: 3px; }
.tower-values li { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 9px 0; border-bottom: 1px solid var(--line); }
.tower-values li:last-child { border-bottom: 0; padding-bottom: 0; }
.tower-values small, .tower-table small { display: block; }
.invitation-row a { color: var(--primary-dark); font-weight: 500; text-decoration: none; overflow-wrap: anywhere; }
.invitation-row a:hover { text-decoration: underline; }
.payroll-lock::before { content: ""; width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
.tower-alert-badge { flex-shrink: 0; white-space: nowrap; font-size: 11px; }
.tower-details { margin-top: 12px; }
.tower-details summary { cursor: pointer; font-weight: 500; }
.tower-table a, .tower-back { color: var(--primary-dark); font-weight: 650; }
@container (width < 28rem) {
  .chart-bars { gap: 2px; padding-inline: 0; }
  .chart-row:nth-child(even) time { visibility: hidden; }
}
@media (min-width: 768px) {
  .tower-period { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .period-context, .tower-period button { grid-column: 1 / -1; }
}
@media (min-width: 1025px) {
  .tower-period { grid-template-columns: minmax(0, 1fr) minmax(0, 7rem) minmax(0, 8rem) auto; }
  .period-context, .tower-period button { grid-column: auto; }
  .tower-panels { grid-template-columns: minmax(0, 1fr) minmax(0, .47fr); }
}
@media (width < 768px) {
  .tower-kpis { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .tower-panel-head .tower-actions, .tower-panel-head .search { width: 100%; }
}
</style>
