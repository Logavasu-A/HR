<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { list as listNotifications } from '@/modules/notifications/services/notificationService'
import { auth, can, clearSession, isEmployee } from '@/shared/auth'
import { resources, selfResources } from '@/modules'

const route = useRoute()
const router = useRouter()
const unread = ref(0)
const menuOpen = ref(false)
const displayName = computed(() => auth.user?.employeeName || auth.user?.username || '')
const roleLabel = computed(() => String(auth.user?.role || '').toLowerCase().replace(/_/g, ' '))
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

const currentCrumb = computed(() => {
  for (const g of groups.value) {
    const match = g.list.find((i) => i.path === route.path)
    if (match) return { group: g.name, title: match.title }
  }
  if (route.path === '/notifications') return { group: 'Workspace', title: 'Notifications' }
  if (route.path === '/change-password') return { group: 'Account', title: 'Change password' }
  return { group: 'Workspace', title: 'HRMS' }
})

function navIcon(item) {
  const p = item.path || ''
  if (p === '/dashboard') return 'dashboard'
  if (p === '/my') return 'home'
  if (p.includes('payslip') || p.includes('payroll') || p.includes('salary')) return 'payroll'
  if (p.includes('employee') || p.includes('applicant')) return 'users'
  if (p.includes('department') || p.includes('branch') || p.includes('designation')) return 'building'
  if (p.includes('leave') || p.includes('holiday')) return 'calendar'
  if (p.includes('attendance') || p.includes('shift')) return 'clock'
  if (p.includes('job') || p.includes('interview')) return 'briefcase'
  if (p.includes('expense') || p.includes('advance')) return 'wallet'
  if (p.includes('appraisal') || p.includes('goal') || p.includes('cycle')) return 'target'
  if (p.includes('user') || p.includes('role') || p.includes('audit')) return 'shield'
  return 'dot'
}

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

function onKeydown(e) {
  if (e.key === 'Escape' && menuOpen.value) menuOpen.value = false
}

watch(() => route.path, () => {
  menuOpen.value = false
})

onMounted(() => {
  loadUnread()
  timer = setInterval(loadUnread, 60000)
  document.addEventListener('keydown', onKeydown)
})

onUnmounted(() => {
  clearInterval(timer)
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <div class="shell">
    <div v-if="menuOpen" class="sidebar-backdrop" aria-hidden="true" @click="menuOpen = false" />

    <aside class="sidebar" :class="{ open: menuOpen }" aria-label="Primary navigation">
      <div class="brand">
        <router-link to="/" class="brand-link" aria-label="HRMS Home">
          <img src="/logo.jpg" alt="HRMS" class="brand-logo" />
        </router-link>
        <button type="button" class="sidebar-close" aria-label="Close navigation" @click="menuOpen = false">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
            <line x1="18" y1="6" x2="6" y2="18" />
            <line x1="6" y1="6" x2="18" y2="18" />
          </svg>
        </button>
      </div>

      <nav @click="menuOpen = false">
        <div v-for="g in groups" :key="g.name" class="nav-group">
          <div class="nav-title">{{ g.name }}</div>
          <router-link v-for="item in g.list" :key="item.path" :to="item.path" class="nav-item">
            <span class="nav-icon" aria-hidden="true">
              <svg v-if="navIcon(item) === 'dashboard'" viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <rect x="3" y="3" width="7" height="7" rx="1.5" />
                <rect x="14" y="3" width="7" height="7" rx="1.5" />
                <rect x="14" y="14" width="7" height="7" rx="1.5" />
                <rect x="3" y="14" width="7" height="7" rx="1.5" />
              </svg>
              <svg v-else-if="navIcon(item) === 'home'" viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
                <polyline points="9 22 9 12 15 12 15 22" />
              </svg>
              <svg v-else-if="navIcon(item) === 'users'" viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" />
                <circle cx="9" cy="7" r="4" />
                <path d="M22 21v-2a4 4 0 0 0-3-3.87" />
                <path d="M16 3.13a4 4 0 0 1 0 7.75" />
              </svg>
              <svg v-else-if="navIcon(item) === 'building'" viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <rect x="4" y="2" width="16" height="20" rx="2" />
                <path d="M9 22v-4h6v4" />
                <path d="M8 6h.01M16 6h.01M12 6h.01M8 10h.01M16 10h.01M12 10h.01M8 14h.01M16 14h.01M12 14h.01" />
              </svg>
              <svg v-else-if="navIcon(item) === 'calendar'" viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <rect x="3" y="4" width="18" height="18" rx="2" />
                <line x1="16" y1="2" x2="16" y2="6" />
                <line x1="8" y1="2" x2="8" y2="6" />
                <line x1="3" y1="10" x2="21" y2="10" />
              </svg>
              <svg v-else-if="navIcon(item) === 'clock'" viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="9" />
                <polyline points="12 7 12 12 15.5 14" />
              </svg>
              <svg v-else-if="navIcon(item) === 'payroll'" viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <rect x="2" y="5" width="20" height="14" rx="2" />
                <line x1="2" y1="10" x2="22" y2="10" />
              </svg>
              <svg v-else-if="navIcon(item) === 'briefcase'" viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <rect x="2" y="7" width="20" height="14" rx="2" />
                <path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16" />
              </svg>
              <svg v-else-if="navIcon(item) === 'wallet'" viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1" />
                <path d="M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4" />
              </svg>
              <svg v-else-if="navIcon(item) === 'target'" viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="9" />
                <circle cx="12" cy="12" r="5" />
                <circle cx="12" cy="12" r="1.5" />
              </svg>
              <svg v-else-if="navIcon(item) === 'shield'" viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
              </svg>
              <svg v-else viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="3" />
              </svg>
            </span>
            <span class="nav-label">{{ item.title }}</span>
          </router-link>
        </div>
      </nav>

      <div class="sidebar-foot">
        <router-link to="/change-password" class="sidebar-profile" title="Account & password settings" @click="menuOpen = false">
          <span class="avatar">{{ initials }}</span>
          <span class="sidebar-profile-meta">
            <span class="sidebar-profile-name">{{ displayName }}</span>
            <span class="sidebar-profile-role">{{ roleLabel || 'Member' }}</span>
          </span>
        </router-link>
      </div>
    </aside>

    <div class="main">
      <header class="topbar">
        <div class="topbar-left">
          <button
            type="button"
            class="menu-btn"
            aria-label="Toggle navigation menu"
            :aria-expanded="menuOpen"
            @click="menuOpen = !menuOpen"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <line x1="3" y1="6" x2="21" y2="6" />
              <line x1="3" y1="12" x2="21" y2="12" />
              <line x1="3" y1="18" x2="21" y2="18" />
            </svg>
          </button>

          <nav class="breadcrumb" aria-label="Breadcrumb">
            <span class="breadcrumb-group">{{ currentCrumb.group }}</span>
            <span class="breadcrumb-sep" aria-hidden="true">/</span>
            <span class="breadcrumb-current">{{ currentCrumb.title }}</span>
          </nav>
        </div>

        <div class="spacer"></div>

        <div class="topbar-right">
          <router-link to="/notifications" class="bell" title="Notifications" aria-label="Notifications">
            <svg viewBox="0 0 24 24" fill="none" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9" />
              <path d="M13.73 21a2 2 0 0 1-3.46 0" />
            </svg>
            <span v-if="unread" class="count">{{ unread }}</span>
          </router-link>

          <router-link to="/change-password" class="who" title="Change password">
            <span class="avatar">{{ initials }}</span>
            <span class="who-text">
              <span class="who-name">{{ displayName }}</span>
              <span class="who-role">{{ roleLabel }}</span>
            </span>
          </router-link>

          <button type="button" class="btn topbar-logout" @click="logout">Log out</button>
        </div>
      </header>

      <main class="content">
        <slot />
      </main>
    </div>
  </div>
</template>
