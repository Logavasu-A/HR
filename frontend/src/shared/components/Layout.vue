<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import api from '@/services/api'
import { auth, can, clearSession, isEmployee } from '@/stores/auth'
import { resources, selfResources } from '@/constants/resources'

const router = useRouter()
const unread = ref(0)
let timer = null

// Sidebar: only the screens this user's role can read.
const groups = computed(() => {
  const items = []
  if (can('dashboard:read')) items.push({ group: 'Overview', title: 'Dashboard', path: '/dashboard' })
  if (isEmployee()) {
    items.push({ group: 'My space', title: 'Home', path: '/my' })
    selfResources.forEach((r) => items.push(r))
    items.push({ group: 'My space', title: 'My payslips', path: '/my/payslips' })
  }
  for (const r of resources) {
    if (!can(`${r.perm}:read`)) continue
    items.push(r)
    if (r.path === '/attendance') items.push({ group: 'Attendance', title: 'Monthly summary', path: '/attendance-summary' })
  }
  if (can('payroll:read')) items.push({ group: 'Payroll', title: 'Payroll runs', path: '/payroll-runs' })

  const order = ['Overview', 'My space', 'Core HR', 'Leave', 'Attendance', 'Payroll', 'Recruitment', 'Expenses', 'Performance', 'Administration']
  const map = new Map(order.map((g) => [g, []]))
  items.forEach((i) => map.get(i.group)?.push(i))
  return [...map.entries()].filter(([, list]) => list.length).map(([name, list]) => ({ name, list }))
})

async function loadUnread() {
  try {
    const { data } = await api.get('/notifications')
    unread.value = data.unread
  } catch {
    // the bell is not essential; ignore failures here
  }
}

function logout() {
  clearSession()
  router.push('/login')
}

onMounted(() => {
  loadUnread()
  timer = setInterval(loadUnread, 60000)
})
onUnmounted(() => clearInterval(timer))
</script>

<template>
  <div class="shell">
    <aside class="sidebar">
      <div class="brand">HRMS</div>
      <nav>
        <div v-for="g in groups" :key="g.name" class="nav-group">
          <div class="nav-title">{{ g.name }}</div>
          <router-link v-for="item in g.list" :key="item.path" :to="item.path">{{ item.title }}</router-link>
        </div>
      </nav>
    </aside>

    <div class="main">
      <header class="topbar">
        <div class="spacer"></div>
        <router-link to="/notifications" class="bell">
          Notifications<span v-if="unread" class="count">{{ unread }}</span>
        </router-link>
        <router-link to="/change-password" class="who">{{ auth.user?.name }} · {{ auth.user?.role }}</router-link>
        <button class="btn small" @click="logout">Log out</button>
      </header>
      <main class="content">
        <slot />
      </main>
    </div>
  </div>
</template>
