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

const COLORS = ['#4f46e5', '#16a34a', '#f59e0b', '#dc2626', '#7c3aed', '#0891b2', '#64748b', '#db2777']

Chart.defaults.font.family = "'Inter', system-ui, sans-serif"
Chart.defaults.color = '#6b7390'
Chart.defaults.borderColor = '#eceef6'
Chart.defaults.plugins.legend.labels.usePointStyle = true
Chart.defaults.elements.bar.borderRadius = 6
Chart.defaults.elements.line.borderWidth = 2.5
Chart.defaults.elements.point.radius = 3

const tiles = computed(() => {
  const c = data.value?.cards
  if (!c) return []
  return [
    { label: 'Active employees', value: c.headcount, color: '#4f46e5', icon: '<path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/>' },
    { label: 'Attrition, last 12 months', value: `${c.attritionRate}%`, color: '#dc2626', icon: '<polyline points="23 18 13.5 8.5 8.5 13.5 1 6"/><polyline points="17 18 23 18 23 12"/>' },
    { label: 'Leave requests waiting', value: c.pendingLeaves, color: '#f59e0b', icon: '<rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/>' },
    { label: 'Expense claims waiting', value: c.pendingExpenses, color: '#0891b2', icon: '<rect x="2" y="5" width="20" height="14" rx="2"/><path d="M2 10h20"/>' },
    { label: 'Open positions', value: c.openPositions, color: '#16a34a', icon: '<rect x="2" y="7" width="20" height="14" rx="2"/><path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"/>' },
    { label: 'Applicants in pipeline', value: c.applicantsInPipeline, color: '#7c3aed', icon: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 11l-3 3-2-2"/>' },
  ]
})

function make(canvas, config) {
  if (canvas.value) charts.push(new Chart(canvas.value, config))
}

const noLegend = { plugins: { legend: { display: false } }, maintainAspectRatio: false }
const withLegend = { plugins: { legend: { position: 'bottom' } }, maintainAspectRatio: false }
const wholeNumbers = { scales: { y: { beginAtZero: true, ticks: { precision: 0 } } } }

function draw(d) {
  make(department, {
    type: 'bar',
    data: { labels: Object.keys(d.byDepartment), datasets: [{ data: Object.values(d.byDepartment), backgroundColor: COLORS[0] }] },
    options: { ...noLegend, ...wholeNumbers },
  })
  make(gender, {
    type: 'doughnut',
    data: { labels: Object.keys(d.byGender), datasets: [{ data: Object.values(d.byGender), backgroundColor: COLORS }] },
    options: withLegend,
  })
  make(movement, {
    type: 'line',
    data: {
      labels: d.movement.labels,
      datasets: [
        { label: 'Joined', data: d.movement.joiners, borderColor: COLORS[1], backgroundColor: COLORS[1], tension: 0.3 },
        { label: 'Left', data: d.movement.leavers, borderColor: COLORS[3], backgroundColor: COLORS[3], tension: 0.3 },
      ],
    },
    options: { ...withLegend, ...wholeNumbers },
  })
  make(leaveTrend, {
    type: 'bar',
    data: { labels: d.leaveTrend.labels, datasets: [{ data: d.leaveTrend.days, backgroundColor: COLORS[2] }] },
    options: { ...noLegend, scales: { y: { beginAtZero: true } } },
  })
  make(attendance, {
    type: 'doughnut',
    data: { labels: Object.keys(d.attendanceToday), datasets: [{ data: Object.values(d.attendanceToday), backgroundColor: COLORS }] },
    options: withLegend,
  })
  make(payroll, {
    type: 'line',
    data: { labels: d.payroll.labels, datasets: [{ data: d.payroll.net, borderColor: COLORS[4], backgroundColor: COLORS[4], tension: 0.3 }] },
    options: { ...noLegend, scales: { y: { beginAtZero: true } } },
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
  <div>
    <div class="page-head"><h1>Dashboard</h1></div>
    <div v-if="error" class="alert">{{ error }}</div>
    <p v-if="!data && !error" class="muted">Loading…</p>

    <template v-if="data">
      <div class="stats">
        <div v-for="s in tiles" :key="s.label" class="card stat" :style="{ '--accent': s.color }">
          <div class="stat-top">
            <span class="stat-label">{{ s.label }}</span>
            <span class="stat-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" v-html="s.icon"></svg>
            </span>
          </div>
          <div class="stat-value">{{ s.value }}</div>
        </div>
      </div>

      <div class="charts">
        <div class="card chart"><h3>Employees by department</h3><div class="canvas"><canvas ref="department"></canvas></div></div>
        <div class="card chart"><h3>Attendance today</h3><div class="canvas"><canvas ref="attendance"></canvas></div></div>
        <div class="card chart"><h3>Joined and left, by month</h3><div class="canvas"><canvas ref="movement"></canvas></div></div>
        <div class="card chart"><h3>Employees by gender</h3><div class="canvas"><canvas ref="gender"></canvas></div></div>
        <div class="card chart"><h3>Approved leave days, by month</h3><div class="canvas"><canvas ref="leaveTrend"></canvas></div></div>
        <div class="card chart">
          <h3>Net payroll, by month</h3>
          <div class="canvas"><canvas ref="payroll"></canvas></div>
          <p v-if="!data.payroll.labels.length" class="muted">No payroll runs yet.</p>
          <p v-else class="muted">Latest: {{ money(data.payroll.net[data.payroll.net.length - 1]) }}</p>
        </div>
      </div>

      <div class="card pad">
        <h3>Birthdays in the next 30 days</h3>
        <p v-if="!data.birthdays.length" class="muted">None.</p>
        <ul v-else class="plain">
          <li v-for="b in data.birthdays" :key="b.name + b.date">{{ b.date }} · {{ b.name }}</li>
        </ul>
      </div>
    </template>
  </div>
</template>
