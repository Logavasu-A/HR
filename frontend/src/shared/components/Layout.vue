<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { list as listNotifications } from '@/modules/notifications/services/notificationService'
import { auth, isEmployee } from '@/shared/auth'
import { useWorkspace } from '@/shared/workspaceContext'
import { logout as endSession } from '@/modules/auth/services/authService'
import { resources, selfResources } from '@/modules'
import FinanceAccess from '@/shared/components/FinanceAccess.vue'
import { pretty } from '@/shared/utils'

const props = defineProps({
  platform: Boolean,
  platformSession: { type: Object, default: null },
})
const emit = defineEmits(['platform-logout'])

const router = useRouter()
const route = useRoute()
const { owner, can, path } = useWorkspace()
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
  if (props.platform) {
    const session = props.platformSession
    const list = []
    if (session?.controlTower) list.push({ title: 'Control Tower', path: '/platform/control-tower' })
    if (session && session.role !== 'SECURITY_ADMINISTRATOR') list.push({ title: 'Companies', path: '/platform/companies' })
    if (session?.adminView) list.push({ title: 'Company workspaces', path: '/platform/workspace' })
    if (session?.controlTower) list.push({ title: 'Audit Center', path: '/platform/audit' })
    if (session?.accessCenter) list.push({ title: 'Access Center', path: '/platform/access' })
    return [{ name: 'Platform', list }]
  }
  const items = []
  if (can('dashboard:read')) items.push({ group: 'Overview', title: 'Dashboard', path: '/dashboard' })
  if (owner) items.push({ group: 'Overview', title: 'Company details', path: '/company-details' })
  if (!owner && isEmployee()) {
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

  const order = ['Overview', 'Platform', 'My space', 'Core HR', 'Leave', 'Attendance', 'Payroll', 'Recruitment', 'Expenses', 'Performance', 'Administration']
  const map = new Map(order.map((g) => [g, []]))
  items.forEach((i) => map.get(i.group)?.push(i))
  return [...map.entries()].filter(([, list]) => list.length).map(([name, list]) => ({ name, list }))
})

const activeGroup = computed(() => groups.value.find((group) => group.list.some((item) => path(item.path) === route.path)))
const activeTitle = computed(() => activeGroup.value?.list.find((item) => path(item.path) === route.path)?.title
  || (props.platform && route.path.startsWith('/platform/workspace/') ? 'Company workspaces' : '')
  || ({ '/notifications': 'Notifications', '/change-password': 'Account & security' })[route.path])

function itemIcon(item) {
  const p = String(item.path || '').toLowerCase()
  const t = String(item.title || '').toLowerCase()
  if (p === '/dashboard' || t === 'dashboard') {
    return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="9" rx="1"/><rect x="14" y="3" width="7" height="5" rx="1"/><rect x="14" y="12" width="7" height="9" rx="1"/><rect x="3" y="16" width="7" height="5" rx="1"/></svg>`
  }
  if (p.includes('control-tower') || t.includes('control tower')) {
    return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>`
  }
  if (p.includes('companies') || t.includes('companies') || p.includes('company-details')) {
    return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 21h18"/><path d="M5 21V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16"/><path d="M9 9h1"/><path d="M9 13h1"/><path d="M9 17h1"/><path d="M14 9h1"/><path d="M14 13h1"/><path d="M14 17h1"/></svg>`
  }
  if (p.includes('employee') || t.includes('employee')) {
    return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>`
  }
  if (p.includes('attendance') || t.includes('attendance') || p.includes('shift')) {
    return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>`
  }
  if (p.includes('leave') || t.includes('leave') || p.includes('holiday')) {
    return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>`
  }
  if (p.includes('payroll') || p.includes('salary') || t.includes('payroll') || t.includes('payslip')) {
    return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="5" width="20" height="14" rx="2"/><line x1="2" y1="10" x2="22" y2="10"/></svg>`
  }
  if (p.includes('recruit') || p.includes('job') || p.includes('interview') || t.includes('recruit')) {
    return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="7" width="20" height="14" rx="2" ry="2"/><path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"/></svg>`
  }
  if (p.includes('expense') || p.includes('advance') || t.includes('expense')) {
    return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>`
  }
  if (p.includes('performance') || p.includes('appraisal') || p.includes('goal')) {
    return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><circle cx="12" cy="12" r="6"/><circle cx="12" cy="12" r="2"/></svg>`
  }
  if (p.includes('user') || p.includes('role') || p.includes('audit')) {
    return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 2a10 10 0 1 0 10 10H12V2z"/><path d="M12 2a10 10 0 0 1 10 10"/></svg>`
  }
  if (p.includes('department') || p.includes('branch') || p.includes('designation')) {
    return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>`
  }
  if (p.includes('my')) {
    return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><polyline points="9 22 9 12 15 12 15 22"/></svg>`
  }
  return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="4"/></svg>`
}

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
  if (owner || props.platform) return
  loadUnread()
  timer = setInterval(loadUnread, 60000)
})
onUnmounted(() => clearInterval(timer))
</script>

<template>
  <div class="shell" :class="{ 'platform-shell': platform, 'admin-workspace-shell': owner }" @keydown.esc="closeMenu()">
    <a class="skip-link" href="#workspace-content">Skip to content</a>
    <div v-if="menuOpen" class="sidebar-overlay" aria-hidden="true" @click="closeMenu()"></div>
    <aside class="sidebar" :class="{ open: menuOpen }">
      <div class="brand">
        <img src="/hrms-logo-v3.svg" alt="HRMS" class="brand-logo" width="270" height="72" />
      </div>
      <nav :id="platform ? 'platform-navigation' : 'workspace-navigation'" ref="navigation"
        :aria-label="platform ? 'Platform sidebar' : 'Workspace navigation'">
        <div v-for="g in groups" :key="g.name" class="nav-group">
          <div class="nav-title">{{ g.name }}</div>
          <router-link
            v-for="item in g.list"
            :key="item.path"
            :to="path(item.path)"
            class="sidebar-nav-link"
            :class="{ 'router-link-exact-active': platform && item.path === '/platform/workspace' && route.path.startsWith('/platform/workspace/') }"
            :aria-current="platform && item.path === '/platform/workspace' && route.path.startsWith('/platform/workspace/') ? 'page' : undefined"
            @click="closeMenu(false)"
          >
            <span class="sidebar-nav-icon" aria-hidden="true" v-html="itemIcon(item)"></span>
            <span class="sidebar-nav-title">{{ item.title }}</span>
          </router-link>
        </div>
      </nav>

      <div v-if="!owner && !platform" class="sidebar-footer">
        <router-link to="/change-password" class="sidebar-user-card" title="Account settings">
          <span class="sidebar-avatar">{{ initials }}</span>
          <div class="sidebar-user-meta">
            <span class="sidebar-user-name">{{ displayName }}</span>
            <span class="sidebar-user-role">{{ String(auth.user?.role || '').toLowerCase() }}</span>
          </div>
        </router-link>
        <button class="sidebar-logout-btn" title="Sign out" aria-label="Sign out" @click="logout">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
            <polyline points="16 17 21 12 16 7"/>
            <line x1="21" y1="12" x2="9" y2="12"/>
          </svg>
        </button>
      </div>
    </aside>

    <div class="main">
      <header class="topbar">
        <button
          ref="menuButton" class="btn small menu-btn" type="button"
          :aria-controls="platform ? 'platform-navigation' : 'workspace-navigation'" :aria-expanded="menuOpen" @click="toggleMenu"
        >
          {{ menuOpen ? 'Close menu' : 'Menu' }}
        </button>
        <div v-if="activeTitle" class="workspace-location">
          <span v-if="platform || (activeGroup && activeGroup.name !== activeTitle)" class="workspace-location-group">
            {{ platform ? 'Platform' : activeGroup.name }}
            <span class="breadcrumb-sep" aria-hidden="true">/</span>
          </span>
          <strong class="workspace-location-title">{{ activeTitle }}</strong>
        </div>
        <div class="spacer"></div>
        <div v-if="!owner && !platform && (auth.user?.companyName || auth.user?.companyCode)" class="active-company-badge">
          <span class="company-badge-pulse" aria-hidden="true"></span>
          <span class="company-badge-icon" aria-hidden="true">🏢</span>
          <span class="company-badge-name">{{ auth.user?.companyName || auth.user?.companyCode }}</span>
        </div>
        <FinanceAccess v-if="!platform && (owner || can('payroll:read') || can('payroll:write') || auth.user?.permissions?.includes('employee-bank:read') || isEmployee())" />
        <router-link v-if="!owner && !platform" to="/notifications" class="bell" :aria-label="unread ? `Notifications, ${unread} unread` : 'Notifications'" title="Notifications">
          <svg viewBox="0 0 24 24" fill="none" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>
          <span v-if="unread" class="count">{{ unread }}</span>
        </router-link>
        <router-link v-if="!owner && !platform" to="/change-password" class="who" title="Account &amp; security" aria-label="Account &amp; security">
          <span class="avatar">{{ initials }}</span>
          <span class="who-text">
            <span class="who-name">{{ displayName }}</span>
            <span class="who-role">{{ String(auth.user?.role || '').toLowerCase().replace(/_/g, ' ') }}</span>
          </span>
        </router-link>
        <button v-if="!owner && !platform" class="btn small" @click="logout">Log out</button>
        <template v-if="platformSession">
          <span class="owner-location">
            <strong>{{ platformSession.username }}</strong>
            <span class="muted">{{ pretty(platformSession.role) }}</span>
          </span>
          <button class="btn small" type="button" @click="emit('platform-logout')">Sign out</button>
        </template>
      </header>
      <slot name="before-content" />
      <main id="workspace-content" class="content" tabindex="-1">
        <slot />
      </main>
    </div>
  </div>
</template>

<style scoped>
.platform-shell :deep(a.btn) { text-decoration: none; }
.platform-shell :deep(.data-table :is(th, td) .btn) { white-space: nowrap; overflow-wrap: normal; }
.topbar .menu-btn { display: none; }
@media (max-width: 800px) {
  .topbar .menu-btn { display: inline-flex; }
  .shell:not(.platform-shell, .admin-workspace-shell) .topbar {
    flex-wrap: wrap;
    gap: 8px;
  }
}
.owner-location { min-width: 0; text-align: right; overflow-wrap: anywhere; }
.owner-location span { display: block; font-size: 12px; }
.platform-shell .owner-location { line-height: 1.3; }
.platform-shell .owner-location span { font-size: var(--platform-caption-size); }

.sidebar-nav-link {
  display: flex !important;
  align-items: center !important;
  gap: 10px !important;
  padding: 8px 12px !important;
  border-radius: 8px !important;
  color: var(--ink-text) !important;
  font-size: 13.5px !important;
  font-weight: 500 !important;
  text-decoration: none !important;
  transition: background-color 0.15s ease, color 0.15s ease !important;
}
.sidebar-nav-link::before {
  display: none !important;
}

.sidebar-nav-icon {
  width: 17px;
  height: 17px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: var(--ink-text);
  transition: color 0.15s ease;
}
.sidebar-nav-icon :deep(svg) {
  width: 16px;
  height: 16px;
}

.sidebar-nav-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sidebar-nav-link:hover {
  background: var(--ink-raised) !important;
  color: var(--card-bg) !important;
}
.sidebar-nav-link:hover .sidebar-nav-icon {
  color: var(--card-bg);
}

.sidebar-nav-link.router-link-exact-active {
  background: var(--ink-raised) !important;
  color: var(--card-bg) !important;
  font-weight: 600 !important;
}
.sidebar-nav-link.router-link-exact-active .sidebar-nav-icon {
  color: var(--card-bg);
}

.sidebar-footer {
  margin-top: auto;
  padding: 12px 14px;
  border-top: 1px solid var(--sidebar-border);
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--ink);
}

.sidebar-user-card {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  min-width: 0;
  flex: 1;
}

.sidebar-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--primary);
  color: var(--card-bg);
  font-size: 11.5px;
  font-weight: 700;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  box-shadow: 0 0 0 1.5px var(--ink-accent);
}

.sidebar-user-meta {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.sidebar-user-name {
  color: var(--card-bg);
  font-size: 12.5px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sidebar-user-role {
  color: var(--ink-text);
  font-size: 11px;
  text-transform: capitalize;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sidebar-logout-btn {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  background: transparent;
  border: none;
  color: var(--ink-text);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s ease;
  flex-shrink: 0;
}
.sidebar-logout-btn:hover {
  background: var(--ink-raised);
  color: var(--bad-bg);
}
.sidebar-logout-btn svg {
  width: 15px;
  height: 15px;
}

.active-company-badge {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  background: var(--card-bg);
  border: 1px solid var(--line);
  padding: 4px 10px 4px 8px;
  border-radius: 999px;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--text);
  box-shadow: var(--shadow-card);
}

.company-badge-pulse {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--ok-line);
  box-shadow: 0 0 8px var(--ok-line);
}

.company-badge-icon {
  font-size: 13px;
  line-height: 1;
}

.company-badge-name {
  max-width: 170px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.switch-badge-link {
  font-size: 11px;
  font-weight: 650;
  color: var(--primary);
  text-decoration: none;
  background: var(--primary-soft);
  padding: 2px 8px;
  border-radius: 999px;
  margin-left: 2px;
  transition: all 0.15s ease;
}
.switch-badge-link:hover {
  background: var(--primary);
  color: var(--card-bg);
}

.workspace-location {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13.5px;
}

.workspace-location-group {
  color: var(--muted);
  font-weight: 500;
}

.breadcrumb-sep {
  margin-left: 6px;
  color: var(--ink-text);
}

.workspace-location-title {
  color: var(--text);
  font-weight: 700;
  font-size: 14.5px;
  letter-spacing: -0.015em;
}
</style>
