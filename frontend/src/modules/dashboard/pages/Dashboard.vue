<script setup>
import { nextTick, onMounted, onUnmounted, ref } from 'vue'
import Chart from 'chart.js/auto'
import api from '@/services/api'
import { errorMessage, money } from '@/utils'

const data = ref(null)
const error = ref('')
const charts = []

const department = ref(null)
const gender = ref(null)
const movement = ref(null)
const leaveTrend = ref(null)
const attendance = ref(null)
const payroll = ref(null)

const COLORS = ['#2563eb', '#16a34a', '#f59e0b', '#dc2626', '#7c3aed', '#0891b2', '#64748b', '#db2777']

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
    const res = await api.get('/dashboard/summary')
    data.value = res.data
    await nextTick()
    draw(res.data)
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
        <div class="card stat"><div class="stat-label">Active employees</div><div class="stat-value">{{ data.cards.headcount }}</div></div>
        <div class="card stat"><div class="stat-label">Attrition, last 12 months</div><div class="stat-value">{{ data.cards.attritionRate }}%</div></div>
        <div class="card stat"><div class="stat-label">Leave requests waiting</div><div class="stat-value">{{ data.cards.pendingLeaves }}</div></div>
        <div class="card stat"><div class="stat-label">Expense claims waiting</div><div class="stat-value">{{ data.cards.pendingExpenses }}</div></div>
        <div class="card stat"><div class="stat-label">Open positions</div><div class="stat-value">{{ data.cards.openPositions }}</div></div>
        <div class="card stat"><div class="stat-label">Applicants in pipeline</div><div class="stat-value">{{ data.cards.applicantsInPipeline }}</div></div>
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
