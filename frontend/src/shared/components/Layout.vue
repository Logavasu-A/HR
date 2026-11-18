<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { list as listNotifications } from '@/modules/notifications/services/notificationService'
import { auth, can, isEmployee } from '@/shared/auth'
import { logout as endSession } from '@/modules/auth/services/authService'
import { resources, selfResources } from '@/modules'

const router = useRouter()
const route = useRoute()
const unread = ref(0)
const menuOpen = ref(false)
const menuButton = ref(null)
const navigation = ref(null)
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

const activeGroup = computed(() => groups.value.find((group) => group.list.some((item) => item.path === route.path)))
const activeTitle = computed(() => activeGroup.value?.list.find((item) => item.path === route.path)?.title
  || ({ '/notifications': 'Notifications', '/change-password': 'Account & security' })[route.path])

async function toggleMenu() {
  if (menuOpen.value) {
    closeMenu()
    return
  }
  menuOpen.value = true
  await nextTick()
  const links = navigation.value
  const firstLink = links?.querySelector('[aria-current="page"]') || links?.querySelector('a')
  firstLink?.focus()
}

function closeMenu(restoreFocus = true) {
  if (!menuOpen.value) return
  menuOpen.value = false
  if (restoreFocus) menuButton.value?.focus()
}

watch(() => route.path, () => closeMenu(false))

async function loadUnread() {
  try {
    const data = await listNotifications()
    unread.value = data.unread
  } catch {
    // the bell is not essential; ignore failures here
  }
}

async function logout() {
  await endSession().catch(() => {}) // Local sign-out also works when the server is unreachable.
  router.replace('/login')
}

onMounted(() => {
  loadUnread()
  timer = setInterval(loadUnread, 60000)
})
onUnmounted(() => clearInterval(timer))
</script>

<template>
  <div class="shell" @keydown.esc="closeMenu()">
    <a class="skip-link" href="#workspace-content">Skip to content</a>
    <div v-if="menuOpen" class="sidebar-overlay" aria-hidden="true" @click="closeMenu()"></div>
    <aside class="sidebar" :class="{ open: menuOpen }">
      <div class="brand">
        <img src="/logo.jpg" alt="HRMS" class="brand-logo" />
      </div>
      <nav id="workspace-navigation" ref="navigation" aria-label="Workspace navigation">
        <div v-for="g in groups" :key="g.name" class="nav-group">
          <div class="nav-title">{{ g.name }}</div>
          <router-link v-for="item in g.list" :key="item.path" :to="item.path" @click="closeMenu(false)">{{ item.title }}</router-link>
        </div>
      </nav>
    </aside>

    <div class="main">
      <header class="topbar">
        <button
          ref="menuButton" class="btn small menu-btn" type="button"
          aria-controls="workspace-navigation" :aria-expanded="menuOpen" @click="toggleMenu"
        >
          {{ menuOpen ? 'Close menu' : 'Menu' }}
        </button>
        <div v-if="activeTitle" class="workspace-location">
          <span v-if="activeGroup && activeGroup.name !== activeTitle">{{ activeGroup.name }} <span aria-hidden="true">/</span></span>
          <strong>{{ activeTitle }}</strong>
        </div>
        <div class="spacer"></div>
        <router-link to="/notifications" class="bell" :aria-label="unread ? `Notifications, ${unread} unread` : 'Notifications'" title="Notifications">
          <svg viewBox="0 0 24 24" fill="none" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>
          <span v-if="unread" class="count">{{ unread }}</span>
        </router-link>
        <router-link to="/change-password" class="who" title="Account &amp; security" aria-label="Account &amp; security">
          <span class="avatar">{{ initials }}</span>
          <span class="who-text">
            <span class="who-name">{{ displayName }}</span>
            <span class="who-role">{{ String(auth.user?.role || '').toLowerCase().replace(/_/g, ' ') }}</span>
          </span>
        </router-link>
        <button class="btn small" @click="logout">Log out</button>
      </header>
      <main id="workspace-content" class="content" tabindex="-1">
        <slot />
      </main>
    </div>
  </div>
</template>
