<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { list as listNotifications } from '@/modules/notifications/services/notificationService'
import { auth, can, clearSession, isEmployee } from '@/shared/auth'
import { resources, selfResources } from '@/modules'

const router = useRouter()
const unread = ref(0)
const menuOpen = ref(false)
const displayName = computed(() => auth.user?.employeeName || auth.user?.username || '')
const initials = computed(() =>
  (displayName.value || '?').split(/\s+/).map((w) => w[0]).slice(0, 2).join('').toUpperCase(),
)
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
    const data = await listNotifications()
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
    <aside class="sidebar" :class="{ open: menuOpen }">
      <div class="brand">
        <img src="/logo.jpg" alt="HRMS" class="brand-logo" />
      </div>
      <nav @click="menuOpen = false">
        <div v-for="g in groups" :key="g.name" class="nav-group">
          <div class="nav-title">{{ g.name }}</div>
          <router-link v-for="item in g.list" :key="item.path" :to="item.path">{{ item.title }}</router-link>
        </div>
      </nav>
    </aside>

    <div class="main">
      <header class="topbar">
        <button class="btn small menu-btn" @click="menuOpen = !menuOpen">☰ Menu</button>
        <div class="spacer"></div>
        <router-link to="/notifications" class="bell" title="Notifications">
          <svg viewBox="0 0 24 24" fill="none" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>
          <span v-if="unread" class="count">{{ unread }}</span>
        </router-link>
        <router-link to="/change-password" class="who" title="Change password">
          <span class="avatar">{{ initials }}</span>
          <span class="who-text">
            <span class="who-name">{{ displayName }}</span>
            <span class="who-role">{{ String(auth.user?.role || '').toLowerCase().replace(/_/g, ' ') }}</span>
          </span>
        </router-link>
        <button class="btn small" @click="logout">Log out</button>
      </header>
      <main class="content">
        <slot />
      </main>
    </div>
  </div>
</template>
