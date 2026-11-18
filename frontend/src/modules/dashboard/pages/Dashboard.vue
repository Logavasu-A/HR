<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import Chart from 'chart.js/auto'
import { getSummary } from '../services/dashboardService'
import { errorMessage, money } from '@/shared/utils'

const data = ref(null)
const error = ref('')
const charts = []

const department = ref(null)
const gender = ref(null)
const movement = ref(null)
const leaveTrend = ref(null)
const attendance = ref(null)
const payroll = ref(null)

const PRIMARY = '#6366F1'
const SECONDARY = '#06B6D4'
const SERIES = [PRIMARY, SECONDARY, '#818CF8', '#22D3EE', '#4F46E5', '#0891B2']

Chart.defaults.font.family = "'Inter', system-ui, sans-serif"
Chart.defaults.font.size = 12
Chart.defaults.color = '#71717A'
Chart.defaults.borderColor = '#E4E4E7'
Chart.defaults.plugins.legend.labels.usePointStyle = true
Chart.defaults.plugins.legend.labels.boxWidth = 8
Chart.defaults.plugins.legend.labels.boxHeight = 8
Chart.defaults.plugins.legend.labels.padding = 16
Chart.defaults.plugins.tooltip.backgroundColor = '#18181B'
Chart.defaults.plugins.tooltip.titleColor = '#FAFAFA'
Chart.defaults.plugins.tooltip.bodyColor = '#E4E4E7'
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
    { label: 'Active employees', value: c.headcount, color: '#10B981', icon: '<path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/>' },
    { label: 'Attrition, last 12 months', value: `${c.attritionRate}%`, color: '#F43F5E', icon: '<polyline points="23 18 13.5 8.5 8.5 13.5 1 6"/><polyline points="17 18 23 18 23 12"/>' },
    { label: 'Leave requests waiting', value: c.pendingLeaves, color: '#F59E0B', icon: '<rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/>' },
    { label: 'Expense claims waiting', value: c.pendingExpenses, color: '#F59E0B', icon: '<rect x="2" y="5" width="20" height="14" rx="2"/><path d="M2 10h20"/>' },
    { label: 'Open positions', value: c.openPositions, color: '#6366F1', icon: '<rect x="2" y="7" width="20" height="14" rx="2"/><path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"/>' },
    { label: 'Applicants in pipeline', value: c.applicantsInPipeline, color: '#06B6D4', icon: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 11l-3 3-2-2"/>' },
  ]
})

function make(canvas, config) {
  if (canvas.value) charts.push(new Chart(canvas.value, config))
}

const noLegend = { plugins: { legend: { display: false } }, maintainAspectRatio: false }
const withLegend = { plugins: { legend: { position: 'bottom' } }, maintainAspectRatio: false }
const doughnutOptions = { ...withLegend, cutout: '68%' }
const dashedScales = (whole = false) => ({
  scales: {
    x: {
      border: { display: false, dash: [4, 4] },
      grid: { display: false },
      ticks: { color: '#71717A' },
    },
    y: {
      beginAtZero: true,
      border: { display: false, dash: [4, 4] },
      grid: { color: '#E4E4E7' },
      ticks: whole ? { color: '#71717A', precision: 0 } : { color: '#71717A' },
    },
  },
})

function draw(d) {
  make(department, {
    type: 'bar',
    data: {
      labels: Object.keys(d.byDepartment),
      datasets: [{ data: Object.values(d.byDepartment), backgroundColor: PRIMARY, hoverBackgroundColor: '#4F46E5' }],
    },
    options: { ...noLegend, ...dashedScales(true) },
  })
  make(gender, {
    type: 'doughnut',
    data: {
      labels: Object.keys(d.byGender),
      datasets: [{ data: Object.values(d.byGender), backgroundColor: SERIES, borderColor: '#FFFFFF', borderWidth: 2 }],
    },
    options: doughnutOptions,
  })
  make(movement, {
    type: 'line',
    data: {
      labels: d.movement.labels,
      datasets: [
        { label: 'Joined', data: d.movement.joiners, borderColor: PRIMARY, backgroundColor: 'rgba(99, 102, 241, 0.1)', pointBackgroundColor: PRIMARY, fill: true, tension: 0.35 },
        { label: 'Left', data: d.movement.leavers, borderColor: SECONDARY, backgroundColor: 'rgba(6, 182, 212, 0.1)', pointBackgroundColor: SECONDARY, fill: true, tension: 0.35 },
      ],
    },
    options: { ...withLegend, ...dashedScales(true) },
  })
  make(leaveTrend, {
    type: 'bar',
    data: {
      labels: d.leaveTrend.labels,
      datasets: [{ data: d.leaveTrend.days, backgroundColor: SECONDARY, hoverBackgroundColor: '#0891B2' }],
    },
    options: { ...noLegend, ...dashedScales(false) },
  })
  make(attendance, {
    type: 'doughnut',
    data: {
      labels: Object.keys(d.attendanceToday),
      datasets: [{ data: Object.values(d.attendanceToday), backgroundColor: SERIES, borderColor: '#FFFFFF', borderWidth: 2 }],
    },
    options: doughnutOptions,
  })
  make(payroll, {
    type: 'line',
    data: {
      labels: d.payroll.labels,
      datasets: [{ data: d.payroll.net, borderColor: PRIMARY, backgroundColor: 'rgba(99, 102, 241, 0.1)', pointBackgroundColor: PRIMARY, fill: true, tension: 0.35 }],
    },
    options: { ...noLegend, ...dashedScales(false) },
  })
}

onMounted(async () => {
  try {
    const summary = await getSummary()
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
  <div class="page">
    <div class="page-head"><h1>Dashboard</h1></div>
    <div v-if="error" class="alert">{{ error }}</div>
    <p v-if="!data && !error" class="muted">Loading…</p>

    <template v-if="data">
      <section class="stats" aria-label="Key metrics">
        <div v-for="s in tiles" :key="s.label" class="card stat" :style="{ '--accent': s.color }">
          <div class="stat-top">
            <span class="stat-label">{{ s.label }}</span>
            <span class="stat-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" v-html="s.icon"></svg>
            </span>
          </div>
          <div class="stat-value">{{ s.value }}</div>
        </div>
      </section>

      <section class="charts" aria-label="Analytics charts">
        <div class="card chart">
          <div class="chart-head"><h3>Employees by department</h3></div>
          <div class="canvas"><canvas ref="department"></canvas></div>
        </div>
        <div class="card chart">
          <div class="chart-head"><h3>Attendance today</h3></div>
          <div class="canvas"><canvas ref="attendance"></canvas></div>
        </div>
        <div class="card chart">
          <div class="chart-head"><h3>Joined and left, by month</h3></div>
          <div class="canvas"><canvas ref="movement"></canvas></div>
        </div>
        <div class="card chart">
          <div class="chart-head"><h3>Employees by gender</h3></div>
          <div class="canvas"><canvas ref="gender"></canvas></div>
        </div>
        <div class="card chart">
          <div class="chart-head"><h3>Approved leave days, by month</h3></div>
          <div class="canvas"><canvas ref="leaveTrend"></canvas></div>
        </div>
        <div class="card chart">
          <div class="chart-head">
            <h3>Net payroll, by month</h3>
            <span v-if="data.payroll.labels.length" class="chart-meta">
              Latest: {{ money(data.payroll.net[data.payroll.net.length - 1]) }}
            </span>
            <span v-else class="chart-meta">No runs yet</span>
          </div>
          <div class="canvas"><canvas ref="payroll"></canvas></div>
        </div>
      </section>

      <section class="card pad" aria-label="Upcoming birthdays">
        <div class="chart-head">
          <h3>Birthdays in the next 30 days</h3>
          <span v-if="data.birthdays.length" class="chart-meta">{{ data.birthdays.length }} upcoming</span>
        </div>
        <p v-if="!data.birthdays.length" class="muted">None.</p>
        <ul v-else class="birthday-list">
          <li v-for="b in data.birthdays" :key="b.name + b.date" class="birthday-item">
            <span class="birthday-name">{{ b.name }}</span>
            <span class="birthday-date">{{ b.date }}</span>
          </li>
        </ul>
      </section>
    </template>
  </div>
</template>
