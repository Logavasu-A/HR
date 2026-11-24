<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { refreshUnread, unreadCount } from '@/modules/notifications/services/notificationService'
import { auth, companyHomePath, isEmployee } from '@/shared/auth'
import { useWorkspace } from '@/shared/workspaceContext'
import { logout as endSession } from '@/modules/auth/services/authService'
import { resources, selfResources } from '@/modules'
import FinanceAccess from '@/shared/components/FinanceAccess.vue'
import AccountMenu from '@/shared/components/AccountMenu.vue'
import SignOutDialog from '@/shared/components/SignOutDialog.vue'
import PageBackButton from '@/shared/components/PageBackButton.vue'
import SidebarNavigation from '@/shared/components/SidebarNavigation.vue'
import { pretty } from '@/shared/utils'

const props = defineProps({
  platform: Boolean,
  platformSession: { type: Object, default: null },
})
const emit = defineEmits(['platform-logout'])

const router = useRouter()
const route = useRoute()
const { owner, can, path } = useWorkspace()
const backFallback = computed(() => {
  if (owner) return route.path === '/platform/workspace/dashboard' ? '/platform/control-tower' : '/platform/workspace/dashboard'
  if (!props.platform) return companyHomePath()
  const session = props.platformSession
  if (!session) return ''
  if (session.controlTower) return '/platform/control-tower'
  return session.role === 'SECURITY_ADMINISTRATOR' ? '/platform/access' : '/platform/companies'
})
const menuOpen = ref(false)
const menuButton = ref(null)
const sidebar = ref(null)
const mainContent = ref(null)
const confirmingSignOut = ref(false)
const signingOut = ref(false)
const companyName = computed(() => !owner && !props.platform && (auth.user?.companyName || auth.user?.companyCode))
const displayName = computed(() => auth.user?.employeeName || auth.user?.username || '')
const initialsFor = name => (name || '?').split(/\s+/).map(word => word[0]).slice(0, 2).join('').toUpperCase()
const initials = computed(() => initialsFor(displayName.value))
const sidebarProfile = computed(() => {
  if (owner) return { name: owner, initials: initialsFor(owner), role: 'Super Admin · Admin View' }
  if (props.platform) {
    const session = props.platformSession
    return session && { name: session.username, initials: initialsFor(session.username), role: pretty(session.role) }
  }
  return {
    name: displayName.value,
    initials: initials.value,
    role: auth.user?.role || '',
    accountLink: '/change-password',
  }
})
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
    return list.length ? [{ name: 'Platform', list }] : []
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

function toggleMenu() {
  if (menuOpen.value) {
    closeMenu()
    return
  }
  menuOpen.value = true
  sidebar.value?.focusNavigation()
}

function closeMenu(restoreFocus = true) {
  if (!menuOpen.value) return
  menuOpen.value = false
  sidebar.value?.clearSearch()
  if (restoreFocus) nextTick(() => menuButton.value?.focus({ preventScroll: true }))
}

function navigateFromSidebar() {
  if (!menuOpen.value) return
  closeMenu(false)
  nextTick(() => mainContent.value?.focus())
}

watch(menuOpen, open => document.documentElement.classList.toggle('navigation-locked', open), { flush: 'sync' })

watch(() => route.path, () => closeMenu(false))

function loadUnread() {
  if (!document.hidden) refreshUnread()
}

async function logout() {
  if (signingOut.value) return
  signingOut.value = true
  try {
    await endSession().catch(() => {}) // Local sign-out also works when the server is unreachable.
    await router.replace('/login')
  } finally {
    signingOut.value = false
    confirmingSignOut.value = false
  }
}

function cancelSignOut() {
  if (!signingOut.value) confirmingSignOut.value = false
}

onMounted(() => {
  if (props.platform || owner) return
  loadUnread()
  timer = setInterval(loadUnread, 15000)
  window.addEventListener('focus', loadUnread)
})
onUnmounted(() => {
  clearInterval(timer)
  window.removeEventListener('focus', loadUnread)
  if (menuOpen.value) document.documentElement.classList.remove('navigation-locked')
})
</script>

<template>
  <div class="shell" :class="{ 'platform-shell': platform, 'admin-workspace-shell': owner }" @keydown.esc="closeMenu()">
    <a class="skip-link" href="#workspace-content" :inert="menuOpen">Skip to content</a>
    <div v-if="menuOpen" class="sidebar-overlay" aria-hidden="true" @click="closeMenu()"></div>
    <SidebarNavigation
      ref="sidebar" :groups="groups" :path="path" :profile="sidebarProfile" :platform="platform"
      :open="menuOpen"
      @open="menuOpen = true" @close="closeMenu()" @navigate="navigateFromSidebar"
    />

    <div class="main" :inert="menuOpen">
      <header class="topbar">
        <button
          ref="menuButton" class="btn small menu-btn" type="button"
          aria-label="Menu"
          :aria-controls="platform ? 'platform-navigation' : 'workspace-navigation'" :aria-expanded="menuOpen" @click="toggleMenu"
        >
          <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.8"
            stroke-linecap="round" aria-hidden="true"><path d="M4 6h16M4 12h16M4 18h16"/></svg>
        </button>
        <PageBackButton class="topbar-back" :fallback="backFallback" />
        <div v-if="activeTitle" class="workspace-location">
          <span v-if="companyName" class="mobile-company">{{ companyName }}</span>
          <span v-if="platform || (activeGroup && activeGroup.name !== activeTitle)" class="workspace-location-group">
            {{ platform ? 'Platform' : activeGroup.name }}
            <span class="breadcrumb-sep" aria-hidden="true">/</span>
          </span>
          <strong class="workspace-location-title">{{ activeTitle }}</strong>
        </div>
        <div class="spacer"></div>
        <div v-if="companyName" class="active-company-badge" :title="companyName">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"
            stroke-linejoin="round" aria-hidden="true"><path d="M4 21h16M6 21V5a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v16M10 8h4m-4 4h4m-3 9v-5h2v5"/></svg>
          <span class="company-badge-name">{{ companyName }}</span>
        </div>
        <FinanceAccess v-if="!platform && (owner || can('payroll:read') || can('payroll:write') || auth.user?.permissions?.includes('employee-bank:read') || isEmployee())" />
        <router-link v-if="!owner && !platform" to="/notifications" class="bell" :aria-label="unreadCount ? `Notifications, ${unreadCount} unread` : 'Notifications'" title="Notifications">
          <svg viewBox="0 0 24 24" fill="none" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>
          <span v-if="unreadCount > 0" class="count" aria-hidden="true">{{ unreadCount }}</span>
        </router-link>
        <AccountMenu v-if="platformSession" :name="platformSession.username" :role="pretty(platformSession.role)"
          @sign-out="emit('platform-logout')" />
        <AccountMenu v-else-if="!owner && !platform && displayName" :name="displayName" :initials="initials"
          :role="pretty(auth.user?.role)" account-link="/change-password" @sign-out="confirmingSignOut = true" />
      </header>
      <slot name="before-content" />
      <main id="workspace-content" ref="mainContent" class="content" tabindex="-1">
        <slot />
      </main>
    </div>
    <SignOutDialog v-if="confirmingSignOut" :busy="signingOut" @dismiss="cancelSignOut" @confirm="logout" />
  </div>
</template>

<style scoped>
.platform-shell :deep(a.btn) { text-decoration: none; }
.topbar {
  --toolbar-control-height: var(--tap-target);
  --toolbar-border: color-mix(in srgb, var(--ink-text) 38%, var(--ink));
  gap: var(--space-8);
}
.topbar > * { max-inline-size: 100%; }
.topbar .menu-btn, .mobile-company { display: none; }
.workspace-location { flex: 1 1 auto; }
.topbar > .workspace-location + .spacer { display: none; }
.workspace-location-title, .mobile-company { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mobile-company { color: var(--ink-text); font-size: .75rem; }
.topbar :is(.menu-btn, .bell), .topbar :deep(.finance-trigger) {
  min-block-size: var(--toolbar-control-height);
  border: 1px solid var(--toolbar-border);
  border-radius: var(--radius-sm);
  background: var(--ink-raised);
  color: var(--card-bg);
  box-shadow: none;
}
.topbar :is(.menu-btn, .bell):is(:hover, :active, [aria-current="page"], [aria-expanded="true"]),
.topbar :deep(.finance-trigger:is(:hover, :active)) {
  background: var(--primary);
  border-color: var(--ink-text);
}
.topbar :is(.menu-btn, .bell):focus-visible, .topbar :deep(.finance-trigger:focus-visible) {
  outline: 2px solid var(--ink-accent);
  outline-offset: 2px;
}
.topbar .bell { inline-size: var(--toolbar-control-height); block-size: var(--toolbar-control-height); }
.topbar .bell svg { inline-size: 20px; block-size: 20px; stroke: currentColor; }
.topbar .count {
  inset: -3px -3px auto auto;
  min-inline-size: 17px;
  padding-inline: 3px;
  border-color: var(--ink);
  font-size: .625rem;
  font-variant-numeric: tabular-nums;
  line-height: 14px;
}
@media (width < 768px) {
  .topbar { flex-wrap: nowrap; min-block-size: var(--platform-topbar-height); padding-block: var(--space-8); }
  .topbar:has(.mobile-company) { flex-wrap: wrap; row-gap: var(--space-4); }
  .topbar:has(.mobile-company) .topbar-back {
    position: absolute;
    inset-block-end: var(--space-8);
    inset-inline-start: var(--content-gutter);
  }
  .topbar .workspace-location { display: grid; flex: 1 1 auto; gap: 0; }
  .topbar .workspace-location:has(.mobile-company) { display: contents; }
  .workspace-location-title {
    flex: 1 1 0;
    min-inline-size: 0;
    white-space: normal;
    overflow-wrap: anywhere;
    line-height: 1.2;
  }
  .mobile-company {
    display: block;
    order: 1;
    flex: 0 0 100%;
    align-content: center;
    min-block-size: calc(var(--tap-target) + var(--space-4));
    padding-inline-start: calc(var(--tap-target) + var(--space-8));
    border-block-start: 1px solid var(--ink-raised);
    white-space: normal;
    overflow-wrap: anywhere;
  }
  .topbar :is(.workspace-location-group, .active-company-badge, .workspace-location + .spacer) {
    display: none;
  }
  .topbar :is(.menu-btn, .bell), .topbar :deep(.finance-trigger) {
    display: inline-grid;
    place-items: center;
    flex: 0 0 var(--tap-target);
    inline-size: var(--tap-target);
    block-size: var(--tap-target);
    padding: 0;
  }
}
@media (min-width: 768px) {
  .topbar { min-block-size: 4rem; padding-block: var(--space-8); }
  .topbar :deep(.finance-trigger) { white-space: nowrap; }
}
@media (768px <= width < 1300px) {
  .topbar .active-company-badge, .topbar .workspace-location:has(.mobile-company) .workspace-location-group { display: none; }
  .topbar .workspace-location:has(.mobile-company) { display: grid; gap: 0; }
  .mobile-company { display: block; }
}
@media (min-width: 1025px) {
  .topbar { --toolbar-control-height: 2.5rem; }
}
.active-company-badge {
  display: inline-flex;
  align-items: center;
  gap: var(--space-8);
  border: 1px solid var(--toolbar-border);
  padding: var(--space-4) var(--space-12);
  border-radius: var(--radius-sm);
  min-block-size: var(--toolbar-control-height);
  max-inline-size: min(27%, 17rem);
  font-size: .8125rem;
  font-weight: 600;
  color: var(--ink-text);
  min-inline-size: 0;
}
.active-company-badge svg {
  inline-size: 16px;
  block-size: 16px;
  flex: none;
}

.company-badge-name {
  min-inline-size: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-location {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13.5px;
}

.workspace-location-group {
  color: var(--ink-text);
  font-weight: 500;
  flex-shrink: 0;
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
