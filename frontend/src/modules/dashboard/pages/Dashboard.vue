<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import Chart from 'chart.js/auto'
import { getSummary } from '../services/dashboardService'
import { errorMessage, money } from '@/shared/utils'
import { useWorkspace } from '@/shared/workspaceContext'

const { api, can, path } = useWorkspace()
const data = ref(null)
const error = ref('')
const charts = []

const gender = ref(null)
const movement = ref(null)
const leaveTrend = ref(null)
const attendance = ref(null)
const payroll = ref(null)

const palette = getComputedStyle(document.documentElement)
const color = name => palette.getPropertyValue(name).trim()
const primary = color('--primary')
const sage = color('--ok-line')
const brick = color('--bad-line')
const saffron = color('--accent-strong')
const slate = color('--text-muted')
const cream = color('--card-bg')
const border = color('--border')
const series = [primary, sage, saffron, brick, slate]
const attendanceColors = {
  Present: primary,
  Absent: brick,
  'Half day': saffron,
  'On leave': sage,
  'Work from home': color('--primary-hover'),
  'Not marked': slate,
}
const tilePermissions = {
  '/employees': 'core:read',
  '/leave-applications': 'leave:read',
  '/expense-claims': 'expense:read',
  '/job-openings': 'recruitment:read',
  '/job-applicants': 'recruitment:read',
}

Chart.defaults.font.family = "'Inter', system-ui, sans-serif"
Chart.defaults.color = slate
Chart.defaults.borderColor = border
Chart.defaults.plugins.legend.labels.usePointStyle = true
Chart.defaults.plugins.legend.labels.boxWidth = 8
Chart.defaults.plugins.legend.labels.boxHeight = 8
Chart.defaults.plugins.tooltip.backgroundColor = color('--ink')
Chart.defaults.plugins.tooltip.titleColor = cream
Chart.defaults.plugins.tooltip.bodyColor = cream
Chart.defaults.plugins.tooltip.padding = 10
Chart.defaults.plugins.tooltip.cornerRadius = 8
Chart.defaults.elements.bar.borderRadius = 6
Chart.defaults.elements.line.borderWidth = 2.5
Chart.defaults.elements.point.radius = 3
Chart.defaults.elements.point.hoverRadius = 5

const tiles = computed(() => {
  const c = data.value?.cards
  if (!c) return []
  return [
    { label: 'Active employees', value: c.headcount, color: 'var(--ok-text)', route: '/employees', icon: '<path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/>' },
    { label: 'Attrition, last 12 months', value: `${c.attritionRate}%`, color: 'var(--bad-line)', route: '/employees', icon: '<polyline points="23 18 13.5 8.5 8.5 13.5 1 6"/><polyline points="17 18 23 18 23 12"/>' },
    { label: 'Leave requests waiting', value: c.pendingLeaves, color: 'var(--warn-text)', route: '/leave-applications', icon: '<rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/>' },
    { label: 'Expense claims waiting', value: c.pendingExpenses, color: 'var(--warn-text)', route: '/expense-claims', icon: '<rect x="2" y="5" width="20" height="14" rx="2"/><path d="M2 10h20"/>' },
    { label: 'Open positions', value: c.openPositions, color: 'var(--primary)', route: '/job-openings', icon: '<rect x="2" y="7" width="20" height="14" rx="2"/><path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"/>' },
    { label: 'Applicants in pipeline', value: c.applicantsInPipeline, color: 'var(--text-muted)', route: '/job-applicants', icon: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 11l-3 3-2-2"/>' },
  ].map(tile => ({ ...tile, route: can(tilePermissions[tile.route]) ? path(tile.route) : undefined }))
})

const departments = computed(() => {
  const entries = Object.entries(data.value?.byDepartment || {})
  const maximum = entries.reduce((largest, [, count]) => Math.max(largest, count), 0)
  return entries.map(([name, count]) => ({ name, count, width: maximum ? count / maximum * 100 : 0 }))
})

function make(canvas, config) {
  if (canvas.value) charts.push(new Chart(canvas.value, config))
}

const noLegend = { plugins: { legend: { display: false } }, responsive: true, maintainAspectRatio: false }
const withLegend = { ...noLegend, plugins: { legend: { position: 'bottom' } } }
const doughnutOptions = { ...withLegend, cutout: '68%' }
const dashedScales = (whole = false) => ({
  scales: {
    x: {
      border: { display: false, dash: [4, 4] },
      grid: { display: false },
      ticks: { color: slate, autoSkip: true, maxRotation: 45 },
    },
    y: {
      beginAtZero: true,
      border: { display: false, dash: [4, 4] },
      grid: { color: border },
      ticks: whole ? { color: slate, precision: 0 } : { color: slate },
    },
  },
})

function draw(d) {
  make(gender, {
    type: 'doughnut',
    data: {
      labels: Object.keys(d.byGender),
      datasets: [{ data: Object.values(d.byGender), backgroundColor: series, borderColor: cream, borderWidth: 2 }],
    },
    options: doughnutOptions,
  })
  make(movement, {
    type: 'line',
    data: {
      labels: d.movement.labels,
      datasets: [
        { label: 'Joined', data: d.movement.joiners, borderColor: sage, backgroundColor: color('--ok-bg'), pointBackgroundColor: sage, fill: true, tension: 0.35 },
        { label: 'Left', data: d.movement.leavers, borderColor: brick, backgroundColor: color('--danger-tint'), pointBackgroundColor: brick, fill: true, tension: 0.35 },
      ],
    },
    options: { ...withLegend, ...dashedScales(true) },
  })
  make(leaveTrend, {
    type: 'bar',
    data: {
      labels: d.leaveTrend.labels,
      datasets: [{ data: d.leaveTrend.days, backgroundColor: sage, hoverBackgroundColor: color('--ok-text') }],
    },
    options: { ...noLegend, ...dashedScales(false) },
  })
  make(attendance, {
    type: 'doughnut',
    data: {
      labels: Object.keys(d.attendanceToday),
      datasets: [{ data: Object.values(d.attendanceToday), backgroundColor: Object.keys(d.attendanceToday).map(status => attendanceColors[status] ?? slate), borderColor: cream, borderWidth: 2 }],
    },
    options: doughnutOptions,
  })
  if (d.payrollLocked) return
  make(payroll, {
    type: 'line',
    data: {
      labels: d.payroll.labels,
      datasets: [{ data: d.payroll.net, borderColor: primary, backgroundColor: color('--primary-tint'), pointBackgroundColor: primary, fill: true, tension: 0.35 }],
    },
    options: { ...noLegend, ...dashedScales(false) },
  })
}

onMounted(async () => {
  try {
    const summary = await getSummary(api)
    data.value = summary
    await nextTick()
    draw(summary)
  } catch (e) {
    error.value = errorMessage(e)
  }
})

onUnmounted(() => charts.forEach((c) => c.destroy()))
</script>

<template>
  <div class="legacy-dashboard">
    <div class="page-head dashboard-header">
      <h1>Dashboard</h1>
      <div class="dashboard-badge">
        <span class="live-dot" aria-hidden="true"></span>
        Live Analytics
      </div>
    </div>
    <div v-if="error" class="alert">{{ error }}</div>
    <p v-if="!data && !error" class="muted">Loading analytics…</p>

    <template v-if="data">
      <div class="stats">
        <component
          :is="s.route ? 'router-link' : 'div'"
          v-for="s in tiles"
          :key="s.label"
          :to="s.route"
          class="card stat"
          :class="{ 'stat--clickable': Boolean(s.route) }"
          :style="{ '--stat-color': s.color }"
        >
          <div class="stat-top">
            <span class="stat-label">{{ s.label }}</span>
            <span class="stat-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" v-html="s.icon"></svg>
            </span>
          </div>
          <div class="stat-bottom">
            <div class="stat-value">{{ s.value }}</div>
            <span v-if="s.route" class="stat-arrow" aria-hidden="true">→</span>
          </div>
        </component>
      </div>

      <div class="charts">
        <div class="card chart">
          <h3 id="employees-by-department">Employees by department</h3>
          <ul v-if="departments.length" class="department-plot" aria-labelledby="employees-by-department">
            <li v-for="row in departments" :key="row.name" class="department-row">
              <span class="department-name">{{ row.name }}</span>
              <span class="department-track" aria-hidden="true">
                <span :style="{ width: row.width + '%' }"></span>
              </span>
              <strong class="department-count">{{ row.count }}</strong>
            </li>
          </ul>
          <p v-else class="muted department-empty">No active employees to show.</p>
        </div>
        <div class="card chart"><h3>Attendance today</h3><div class="canvas"><canvas ref="attendance"></canvas></div></div>
        <div class="card chart"><h3>Joined and left, by month</h3><div class="canvas"><canvas ref="movement"></canvas></div></div>
        <div class="card chart"><h3>Employees by gender</h3><div class="canvas"><canvas ref="gender"></canvas></div></div>
        <div class="card chart"><h3>Approved leave days, by month</h3><div class="canvas"><canvas ref="leaveTrend"></canvas></div></div>
        <div class="card chart">
          <h3>Net payroll, by month</h3>
          <div class="canvas">
            <p v-if="data.payrollLocked" class="chart-placeholder muted">Payroll totals require verified payroll access.</p>
            <p v-else-if="!data.payroll.labels.length" class="chart-placeholder muted">No payroll runs yet.</p>
            <canvas v-else ref="payroll"></canvas>
          </div>
          <p v-if="!data.payrollLocked && data.payroll.labels.length" class="chart-note muted">
            Latest: {{ money(data.payroll.net[data.payroll.net.length - 1]) }}
          </p>
        </div>
      </div>

      <div class="card pad birthday-card">
        <div class="birthday-card-head">
          <span class="birthday-icon" aria-hidden="true">🎂</span>
          <h3>Upcoming Birthdays (Next 30 Days)</h3>
        </div>
        <p v-if="!data.birthdays.length" class="muted">No employee birthdays in the next 30 days.</p>
        <div v-else class="birthdays-grid">
          <div v-for="b in data.birthdays" :key="b.name + b.date" class="birthday-pill">
            <span class="birthday-date">{{ b.date }}</span>
            <strong class="birthday-name">{{ b.name }}</strong>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.legacy-dashboard {
  --dashboard-gap: 16px;
  --dashboard-plot-height: 13.125rem;
  container-type: inline-size;
  display: grid;
  gap: var(--dashboard-gap);
}
.legacy-dashboard :is(.stats, .charts) { gap: var(--dashboard-gap); margin: 0; }
.legacy-dashboard .stats { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.legacy-dashboard .charts { grid-template-columns: minmax(0, 1fr); }
.legacy-dashboard :is(.chart, .stat, .birthday-card) { min-width: 0; padding: 16px; }
.legacy-dashboard .stat { display: flex; flex-direction: column; }
.legacy-dashboard .stat-top { align-items: start; gap: 8px; }
.legacy-dashboard .stat-label { overflow-wrap: anywhere; }
.legacy-dashboard .stat-icon { width: 32px; height: 32px; }
.legacy-dashboard .stat-value { margin: 0; line-height: 1.15; }
.legacy-dashboard .chart h3 { margin: 0 0 12px; line-height: 1.4; }
.legacy-dashboard .canvas { height: var(--dashboard-plot-height); }

.department-plot {
  display: grid;
  grid-template-columns: minmax(0, 44%) minmax(0, 1fr) max-content;
  gap: 4px 8px;
  margin: 0;
  padding: 0;
  list-style: none;
  max-block-size: var(--dashboard-plot-height);
  overflow-y: auto;
}
.department-row {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: subgrid;
  align-items: center;
  min-height: 16px;
  font-size: 11.5px;
  line-height: 1.35;
}
.department-name { overflow-wrap: anywhere; color: var(--text-2); }
.department-track { height: 8px; overflow: hidden; border-radius: 4px; background: var(--primary-tint); }
.department-track > span { display: block; height: 100%; border-radius: inherit; background: var(--primary); }
.department-count { text-align: right; font-variant-numeric: tabular-nums; }
.department-empty { margin: 0; }

@container (width < 21rem) {
  .legacy-dashboard .stats { grid-template-columns: minmax(0, 1fr); }
}
@media (width < 768px) {
  .department-plot { max-block-size: none; overflow: visible; }
}
@media (min-width: 768px) {
  .legacy-dashboard { --dashboard-plot-height: 14rem; }
}
@container (min-width: 38rem) {
  .legacy-dashboard .stats { grid-template-columns: repeat(3, minmax(0, 1fr)); }
  .legacy-dashboard .charts { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (min-width: 1025px) {
  .legacy-dashboard { --dashboard-plot-height: 15.625rem; }
}
@container (min-width: 58rem) {
  .legacy-dashboard .stats { grid-template-columns: repeat(6, minmax(0, 1fr)); }
  .legacy-dashboard .charts { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}
@container (max-width: 430px) {
  .legacy-dashboard :is(.stat, .chart, .birthday-card) { padding: 12px; }
  .legacy-dashboard .stats, .legacy-dashboard .charts { gap: 10px; }
  .legacy-dashboard .stat-top { gap: 6px; }
  .legacy-dashboard .stat-icon { width: 28px; height: 28px; }
  .legacy-dashboard .stat-value { font-size: 26px; }
}

.dashboard-header {
  justify-content: space-between;
  margin: 0;
}
.dashboard-badge {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  background: var(--card-bg);
  border: 1px solid var(--line);
  padding: 5px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text);
  box-shadow: var(--shadow-card);
}
.live-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--ok-line);
  box-shadow: 0 0 8px var(--ok-line);
}

.stat--clickable {
  text-decoration: none;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  transition: all 0.16s ease;
}
.stat--clickable::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--stat-color);
}
.stat--clickable:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-menu);
  border-color: var(--border-strong);
}
.stat-bottom {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 6px;
  margin-top: auto;
  padding-top: 10px;
}
.stat-arrow {
  color: var(--muted);
  font-size: 15px;
  font-weight: 600;
  transition: transform 0.15s ease, color 0.15s ease;
}
.stat--clickable:hover .stat-arrow {
  transform: translateX(4px);
  color: var(--stat-color);
}

.chart-placeholder {
  display: grid;
  place-items: center;
  height: 100%;
  margin: 0;
  padding: 12px;
  text-align: center;
}
.chart-note { margin: 8px 0 0; }

.birthday-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
}
.birthday-card-head h3 {
  margin: 0;
}
.birthday-icon {
  font-size: 18px;
}
.birthdays-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.birthday-pill {
  display: inline-flex;
  align-items: center;
  flex-wrap: wrap;
  min-width: 0;
  gap: 8px;
  background: var(--row-hover);
  border: 1px solid var(--line);
  padding: 6px 12px;
  border-radius: 8px;
  font-size: 13px;
}
.birthday-date {
  color: var(--primary);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.birthday-name {
  overflow-wrap: anywhere;
  color: var(--text);
}
</style>
