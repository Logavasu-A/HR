<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const props = defineProps({
  groups: { type: Array, required: true },
  path: { type: Function, required: true },
  profile: { type: Object, default: null },
  platform: Boolean,
  open: Boolean,
})
const emit = defineEmits(['open', 'close', 'navigate'])
const router = useRouter()
const route = useRoute()
const nav = ref(null)
const search = ref(null)
const tabletPanel = ref(null)
const tabletSearch = ref(null)
const tabletSection = ref(null)
const tabletOpen = ref(false)
let tabletTrigger
const toggle = ref(null)
const query = ref('')
const expandedId = ref(null)
const viewport = window.matchMedia('(width < 768px)')
const tabletViewport = window.matchMedia('(768px <= width < 1025px)')
const mobile = ref(viewport.matches)
const tablet = ref(tabletViewport.matches)
const preference = props.platform ? 'hrms_platform_sidebar_collapsed' : 'hrms_workspace_sidebar_collapsed'
const collapsed = ref(localStorage.getItem(preference) === 'true')
const rail = computed(() => tablet.value || (collapsed.value && !mobile.value))
const navId = props.platform ? 'platform-navigation' : 'workspace-navigation'
const shortcut = /Mac|iPhone|iPad/.test(navigator.platform) ? '⌘ K' : 'Ctrl K'

const icons = {
  dashboard: '<rect x="3" y="3" width="7" height="9" rx="1"/><rect x="14" y="3" width="7" height="5" rx="1"/><rect x="14" y="12" width="7" height="9" rx="1"/><rect x="3" y="16" width="7" height="5" rx="1"/>',
  home: '<path d="m3 10 9-7 9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1z"/>',
  people: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2m20 0v-2a4 4 0 0 0-3-3.9M16 3.1a4 4 0 0 1 0 7.8"/><circle cx="9" cy="7" r="4"/>',
  organization: '<path d="M3 21h18M5 21V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v16M9 8h1m4 0h1m-6 4h1m4 0h1m-4 9v-5h2v5"/>',
  calendar: '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M16 3v4M8 3v4M3 10h18m-12 6 2 2 4-4"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  payroll: '<rect x="3" y="5" width="18" height="14" rx="2"/><path d="M3 10h18m-14 5h4"/>',
  hiring: '<rect x="3" y="7" width="18" height="14" rx="2"/><path d="M8 7V5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M3 13h18m-9-1v3"/>',
  expense: '<path d="M5 3h14v18l-3-2-4 2-4-2-3 2zM9 8h6m-6 4h6m-6 4h3"/>',
  goal: '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="5"/><circle cx="12" cy="12" r="1"/>',
  shield: '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>',
  audit: '<rect x="5" y="4" width="14" height="17" rx="2"/><path d="M9 4V2h6v2m-6 6h6m-6 4h6m-6 4h4"/>',
  key: '<circle cx="8" cy="15" r="4"/><path d="m11 12 8-8m-2 2 3 3m-5-1 2 2"/>',
  search: '<circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 4 4"/>',

}
const itemIcons = {
  '/dashboard': 'dashboard', '/company-details': 'organization', '/my': 'home',
  '/my/leaves': 'calendar', '/my/attendance': 'clock', '/my/expenses': 'expense',
  '/my/goals': 'goal', '/my/appraisals': 'goal', '/my/payslips': 'payroll',
  '/payroll-runs': 'payroll',
  '/platform/control-tower': 'shield', '/platform/companies': 'organization',
  '/platform/workspace': 'hiring', '/platform/audit': 'audit', '/platform/access': 'key',
}
const sectionIcons = {
  Overview: 'dashboard', 'My space': 'home', 'Core HR': 'people', Leave: 'calendar',
  Attendance: 'clock', Payroll: 'payroll', Recruitment: 'hiring', Expenses: 'expense',
  Performance: 'goal', Administration: 'shield',
}
const groupLabels = {
  Leave: 'Leave management', Attendance: 'Attendance', Recruitment: 'Hiring',
  Expenses: 'Expense management', Performance: 'Performance management', Administration: 'User access & audit',
}
const branch = (id, title, icon, links) => ({ id, title, icon, links })
const sections = computed(() => props.groups.map(({ name, list }) => {
  const id = name.toLowerCase().replaceAll(' ', '-')
  const icon = sectionIcons[name]
  const direct = item => ({ ...item, id: item.path, icon: itemIcons[item.path] || icon })
  let items = list.map(direct)
  if (name === 'Core HR') {
    items = [
      branch('employees', 'Employees', 'people', list.filter(item => item.path === '/employees')
        .map(item => ({ ...item, title: 'All employees' }))),
      branch('organization', 'Organization', 'organization', list.filter(item => item.path !== '/employees')),
    ].filter(item => item.links.length)
  } else if (name === 'Payroll') {
    const setup = list.filter(item => item.path !== '/payroll-runs')
    items = [...(setup.length ? [branch('salary-setup', 'Salary setup', 'payroll', setup)] : []),
      ...list.filter(item => item.path === '/payroll-runs').map(direct)]
  } else if (groupLabels[name]) {
    items = [branch(id, groupLabels[name], icon, list)]
  }
  return { id, name, icon, items }
}))

const matches = computed(() => query.value.trim().toLowerCase())
const filtered = computed(() => {
  if (!matches.value) return sections.value
  const hasText = text => text.toLowerCase().includes(matches.value)
  return sections.value.map(section => ({
    ...section,
    items: section.items.flatMap(item => {
      if (hasText(section.name) || hasText(item.title)) return [item]
      const links = item.links?.filter(link => hasText(link.title))
      return links?.length ? [{ ...item, links }] : []
    }),
  })).filter(section => section.items.length)
})
const tabletSections = computed(() => tabletSection.value
  ? filtered.value.filter(section => section.id === tabletSection.value)
  : filtered.value)

function isCurrent(item) {
  const destination = props.path(item.path)
  return route.path === destination || (['/employees', '/platform/workspace'].includes(item.path)
    && route.path.startsWith(destination + '/'))
}
const isActive = item => item.links ? item.links.some(isCurrent) : isCurrent(item)
const branches = computed(() => sections.value.flatMap(section => section.items).filter(item => item.links))
const activeBranchId = computed(() => branches.value.find(isActive)?.id ?? null)
const isExpanded = item => expandedId.value === item.id
const clearSearch = () => { query.value = '' }
const navigate = () => { closeTablet(false); clearSearch(); emit('navigate') }

function toggleBranch(item) {
  expandedId.value = isExpanded(item) ? null : item.id
}

function closeTablet(restoreFocus = true) {
  if (!tabletOpen.value) return
  tabletOpen.value = false
  clearSearch()
  if (restoreFocus) tabletTrigger?.focus()
}

watch([() => route.path, activeBranchId], ([, id]) => {
  if (id) expandedId.value = id
}, { immediate: true })

watch(branches, available => {
  if (!available.some(isExpanded)) expandedId.value = null
})

watch(matches, term => {
  if (!term || tablet.value) return
  const results = filtered.value.flatMap(section => section.items).filter(item => item.links)
  if (results.length && !results.some(isExpanded)) expandedId.value = results[0].id
})

function setCollapsed(value) {
  collapsed.value = value
  localStorage.setItem(preference, String(value))
}

async function focusNavigation() {
  await nextTick()
  const selected = nav.value?.querySelector('a[aria-current="page"]')
  const first = selected || nav.value?.querySelector('a, button')
  first?.focus()
}

async function toggleRail() {
  clearSearch()
  setCollapsed(!collapsed.value)
  await nextTick()
  toggle.value?.focus()
}

async function openSection(section) {
  if (tablet.value) {
    if (tabletOpen.value && tabletSection.value === section.id) {
      closeTablet()
      return
    }
    tabletTrigger = document.activeElement
    clearSearch()
    tabletSection.value = section.id
    tabletOpen.value = true
    await nextTick()
    tabletPanel.value?.querySelector('a, button')?.focus()
    return
  }
  const selected = section.items.find(isActive) || section.items[0]
  if (selected?.links) expandedId.value = selected.id
  setCollapsed(false)
  await nextTick()
  const element = document.getElementById(navId + '-section-' + section.id)
  const target = element?.querySelector('a[aria-current="page"]') || element?.querySelector('a, button')
  target?.focus()
}

async function focusSearch() {
  if (tablet.value) {
    tabletTrigger = document.activeElement
    tabletSection.value = null
    tabletOpen.value = true
    await nextTick()
    tabletSearch.value?.focus()
    return
  }
  if (mobile.value) emit('open')
  else if (rail.value) setCollapsed(false)
  await nextTick()
  search.value?.focus()
  search.value?.select()
}

function handleShortcut(event) {
  if (event.defaultPrevented || !((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k')) return
  if (document.querySelector('dialog[open]')) return
  event.preventDefault()
  focusSearch()
}

function escapeSearch(event) {
  if (!query.value) return
  event.preventDefault()
  event.stopPropagation()
  clearSearch()
}

function selectSearchResult() {
  const item = filtered.value[0]?.items[0]
  const link = item?.links ? item.links[0] : item
  if (!matches.value || !link) return
  router.push(props.path(link.path))
  navigate()
}

function trapDrawerFocus(event) {
  if (!mobile.value || !props.open) return
  const controls = [...event.currentTarget.querySelectorAll('a[href], button, input')].filter(element =>
    !element.disabled && element.getClientRects().length && element.tabIndex >= 0)
  const first = controls[0]
  const last = controls.at(-1)
  if ((event.shiftKey && document.activeElement === first) || (!event.shiftKey && document.activeElement === last)) {
    event.preventDefault()
    const target = event.shiftKey ? last : first
    target?.focus()
  }
}

function syncViewport() {
  mobile.value = viewport.matches
  tablet.value = tabletViewport.matches
  if (!mobile.value && props.open) emit('close')
  if (!tablet.value) closeTablet(false)
}
onMounted(() => {
  viewport.addEventListener('change', syncViewport)
  tabletViewport.addEventListener('change', syncViewport)
  window.addEventListener('keydown', handleShortcut)
})
onUnmounted(() => {
  viewport.removeEventListener('change', syncViewport)
  tabletViewport.removeEventListener('change', syncViewport)
  window.removeEventListener('keydown', handleShortcut)
})
defineExpose({ focusNavigation, clearSearch })
</script>

<template>
  <aside class="sidebar hr-sidebar" :class="{ open, 'is-collapsed': rail, 'is-tablet': tablet }" @keydown.tab="trapDrawerFocus">
    <div class="sidebar-brand">
      <img v-if="!rail" src="/hrms-logo-on-dark.svg" alt="HRMS People Platform" width="128" height="34" />
      <button v-if="!tablet" ref="toggle" type="button" class="sidebar-icon-button sidebar-collapse"
        :aria-label="rail ? 'Expand sidebar' : 'Collapse sidebar'"
        :title="rail ? 'Expand sidebar' : 'Collapse sidebar'" :aria-expanded="!rail" :aria-controls="navId" @click="toggleRail">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <rect x="3" y="4" width="18" height="16" rx="2"/><path d="M9 4v16"/>
          <path :d="rail ? 'm13 9 3 3-3 3' : 'm16 9-3 3 3 3'"/>
        </svg>
      </button>
      <button type="button" class="sidebar-icon-button sidebar-close" aria-label="Close navigation menu" @click="emit('close')">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" aria-hidden="true">
          <path d="m6 6 12 12M18 6 6 18"/>
        </svg>
      </button>
    </div>

    <div class="sidebar-search-area">
      <button v-if="rail" type="button" class="sidebar-icon-button rail-button"
        aria-label="Search menu" aria-keyshortcuts="Control+k Meta+k" title="Search menu" @click="focusSearch">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"
          aria-hidden="true" v-html="icons.search"></svg>
      </button>
      <form v-else class="sidebar-search" role="search" aria-label="Navigation" @submit.prevent="selectSearchResult">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"
          aria-hidden="true" v-html="icons.search"></svg>
        <input ref="search" v-model="query" type="search" autocomplete="off" aria-label="Search menu"
          aria-keyshortcuts="Control+k Meta+k" placeholder="Search menu" @keydown.esc="escapeSearch" />
        <kbd v-if="!query" aria-hidden="true">{{ shortcut }}</kbd>
      </form>
    </div>

    <nav :id="navId" ref="nav" :aria-label="platform ? 'Platform sidebar' : 'Workspace navigation'">
      <div v-if="rail" class="sidebar-rail">
        <template v-for="section in sections" :key="section.id">
          <template v-if="platform || (section.items.length === 1 && !section.items[0].links)">
            <router-link v-for="item in section.items" :key="item.id" :to="path(item.path)"
              class="sidebar-icon-button rail-button" :class="{ 'nav-active': isCurrent(item) }"
              :aria-label="item.title" :title="item.title" @click="navigate">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"
                stroke-linejoin="round" aria-hidden="true" v-html="icons[item.icon]"></svg>
            </router-link>
          </template>
          <button v-else type="button" class="sidebar-icon-button rail-button"
            :class="{ 'nav-active': section.items.some(isActive) }" :aria-label="'Open ' + section.name + ' menu'"
            :aria-expanded="tablet ? tabletOpen && tabletSection === section.id : undefined"
            :title="section.name" @click="openSection(section)">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"
              stroke-linejoin="round" aria-hidden="true" v-html="icons[section.icon]"></svg>
          </button>
        </template>
      </div>

      <template v-else>
        <p v-if="matches && !filtered.length" class="sidebar-empty" role="status">No matching pages</p>
        <div v-for="section in filtered" :id="navId + '-section-' + section.id" :key="section.id" class="sidebar-section">
          <h2 class="sidebar-section-title">{{ section.name }}</h2>
          <template v-for="item in section.items" :key="item.id">
            <template v-if="item.links">
              <button type="button" class="sidebar-row" :class="{ 'nav-active': isActive(item) }"
                :aria-expanded="isExpanded(item)" :aria-controls="navId + '-' + item.id"
                @click="toggleBranch(item)">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"
                  stroke-linejoin="round" aria-hidden="true" v-html="icons[item.icon]"></svg>
                <span class="sidebar-label">{{ item.title }}</span>
                <svg class="sidebar-chevron" :class="{ 'is-open': isExpanded(item) }" viewBox="0 0 24 24"
                  fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                  <path d="m9 5 7 7-7 7" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
              </button>
              <ul v-show="isExpanded(item)" :id="navId + '-' + item.id" class="sidebar-submenu">
                <li v-for="link in item.links" :key="link.path">
                  <router-link :to="path(link.path)" class="sidebar-row sidebar-child"
                    :class="{ 'nav-active': isCurrent(link) }" :aria-current="isCurrent(link) ? 'page' : undefined"
                    @click="navigate">{{ link.title }}</router-link>
                </li>
              </ul>
            </template>
            <router-link v-else :to="path(item.path)" class="sidebar-row"
              :class="{ 'nav-active': isCurrent(item) }"
              :aria-current="isCurrent(item) ? 'page' : undefined" @click="navigate">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"
                stroke-linejoin="round" aria-hidden="true" v-html="icons[item.icon]"></svg>
              <span class="sidebar-label">{{ item.title }}</span>
            </router-link>
          </template>
        </div>
      </template>
    </nav>

    <div v-if="profile" class="sidebar-footer">
      <component :is="profile.accountLink ? 'router-link' : 'div'" class="sidebar-user-card"
        :to="profile.accountLink" :aria-label="profile.accountLink ? 'Account & security' : undefined"
        :title="profile.name" @click="profile.accountLink && navigate()">
        <span class="sidebar-avatar" :aria-hidden="!rail">{{ profile.initials }}</span>
        <span v-if="!rail" class="sidebar-user-meta">
          <strong>{{ profile.name }}</strong><span>{{ profile.role }}</span>
        </span>
      </component>
    </div>
  </aside>
  <Teleport to="body">
    <template v-if="tablet && tabletOpen">
      <div class="tablet-nav-backdrop" aria-hidden="true" @click="closeTablet()"></div>
      <nav ref="tabletPanel" class="tablet-nav-panel" aria-label="Sidebar submenu" @keydown.esc.stop="closeTablet()">
        <form v-if="!tabletSection" class="sidebar-search" role="search" @submit.prevent="selectSearchResult">
          <input ref="tabletSearch" v-model="query" type="search" autocomplete="off" aria-label="Search menu"
            placeholder="Search menu" @keydown.esc="escapeSearch"/>
        </form>
        <p v-if="!tabletSections.length" class="sidebar-empty" role="status">No matching pages</p>
        <div v-for="section in tabletSections" :key="section.id" class="sidebar-section">
          <h2 class="sidebar-section-title">{{ section.name }}</h2>
          <router-link v-for="link in section.items.flatMap(item => item.links || [item])" :key="link.path"
            :to="path(link.path)" class="sidebar-row" :class="{ 'nav-active': isCurrent(link) }" @click="navigate">
            {{ link.title }}
          </router-link>
        </div>
      </nav>
    </template>
  </Teleport>
</template>


<style scoped>
.hr-sidebar {
  width: 16.875rem;
  background: linear-gradient(180deg, var(--sb-bg-start), var(--sb-bg-mid), var(--sb-bg-end));
  border-color: var(--sb-border);
  color: var(--sb-text-2);
}
.hr-sidebar.is-collapsed { width: var(--sidebar-rail-width); }
.hr-sidebar.is-tablet { z-index: 25; }
.sidebar-brand { display: flex; min-height: 64px; align-items: center; justify-content: space-between; gap: 8px; padding: 10px 14px; }
.sidebar-brand img { width: 128px; height: auto; }
.sidebar-search-area { padding: 0 12px 4px; }
.sidebar-search {
  display: flex; align-items: center; gap: 8px; min-height: 38px;
  padding: 0 10px; border: 1px solid var(--sb-border); border-radius: 9px; background: var(--sb-hover); color: var(--sb-muted);
}
.sidebar-search:focus-within { border-color: var(--sb-accent); }
.sidebar-search input {
  min-width: 0; width: 100%; padding: 0; height: 36px; border: 0;
  background: none; box-shadow: none; color: var(--sb-text); outline: none; font: inherit; font-size: 13px;
}
.sidebar-search input::placeholder { color: var(--sb-muted); }
.sidebar-search input::-webkit-search-cancel-button { filter: brightness(0) invert(1); }
.sidebar-search kbd {
  flex: 0 0 auto; padding: 0 5px; border: 1px solid var(--sb-border);
  border-radius: 5px; color: var(--sb-muted); font: inherit; font-size: 11px; line-height: 20px;
}
.hr-sidebar nav, .tablet-nav-panel { scrollbar-width: thin; scrollbar-color: var(--sb-border) transparent; }
.hr-sidebar nav { padding: 8px 10px 16px; overscroll-behavior: contain; }
.hr-sidebar nav::-webkit-scrollbar-thumb { background-color: var(--sb-border); }
.sidebar-section + .sidebar-section { margin-top: 13px; }
.sidebar-section-title {
  margin: 0; padding: 4px 10px; color: var(--sb-muted);
  font-size: 10.5px; font-weight: 700; line-height: 1.5; letter-spacing: 0.09em; text-transform: uppercase;
}
.sidebar-row, .sidebar-icon-button {
  display: flex; align-items: center; border: 0; border-radius: 8px; background: transparent;
  color: var(--sb-text-2); font: inherit; text-decoration: none; text-align: left; cursor: pointer;
}
.hr-sidebar .sidebar-row { width: 100%; min-height: 37px; padding: 5px 10px; margin: 1px 0; gap: 8px; font-size: 13.5px; font-weight: 500; }
.sidebar-row:hover, .sidebar-icon-button:hover, .sidebar-user-card:hover { color: var(--sb-text); background: var(--sb-hover); }
.hr-sidebar .nav-active, .hr-sidebar .router-link-exact-active, .tablet-nav-panel .nav-active {
  background: var(--sb-active-bg); color: var(--sb-text);
  box-shadow: inset 0 0 0 1px var(--sb-active-border), inset 2px 0 var(--sb-accent);
}
.hr-sidebar .nav-active, .hr-sidebar .router-link-exact-active { font-weight: 650; }
.sidebar-row svg, .sidebar-icon-button svg, .sidebar-search svg {
  width: 18px; height: 18px; flex: 0 0 auto; color: var(--sb-muted);
}
.nav-active > svg, .router-link-exact-active > svg, .rail-button[aria-expanded="true"] > svg { color: var(--sb-accent); }
.sidebar-label { flex: 1; min-width: 0; overflow-wrap: anywhere; }
.sidebar-row .sidebar-chevron { width: 13px; height: 13px; }
.sidebar-chevron.is-open { transform: rotate(90deg); }
.sidebar-submenu { margin: 1px 0 5px 19px; padding: 0 0 0 10px; list-style: none; border-left: 1px solid var(--sb-border); }
.hr-sidebar .sidebar-child { min-height: 32px; font-size: 13px; }
.sidebar-child[aria-current="page"], .tablet-nav-panel .nav-active { position: relative; }
.sidebar-child[aria-current="page"]::after, .tablet-nav-panel .nav-active::after {
  content: ''; position: absolute; inset: 50% 7px auto auto; transform: translateY(-50%);
  width: 4px; height: 4px; border-radius: 50%; background: var(--sb-accent);
}
.sidebar-empty { padding: 8px 10px; margin: 0; color: var(--sb-muted); }
.hr-sidebar .sidebar-icon-button { width: 34px; height: 34px; padding: 0; margin: 0; justify-content: center; flex: 0 0 auto; }
.hr-sidebar .sidebar-collapse {
  width: 40px;
  height: 40px;
  border: 1px solid var(--sb-border);
  border-radius: var(--radius-sm);
  background: var(--sb-hover);
  color: var(--sb-muted);
}
.hr-sidebar .sidebar-collapse:hover { background: var(--sb-active-bg); border-color: var(--sb-active-border); }
.hr-sidebar .sidebar-close { display: none; }
.hr-sidebar .rail-button { width: 40px; height: 40px; }
.is-collapsed .sidebar-brand, .is-collapsed .sidebar-search-area { display: flex; justify-content: center; padding-inline: 0; }
.is-collapsed nav { padding-inline: 0; }
.sidebar-rail { display: grid; justify-items: center; gap: 4px; }
.sidebar-footer {
  flex: 0 0 auto; display: flex; align-items: center; gap: 3px; margin: 0 12px;
  padding: 10px 0; border-top: 1px solid var(--sb-border);
}
.sidebar-user-card {
  display: flex; align-items: center; gap: 9px; min-width: 0; flex: 1;
  padding: 4px; border-radius: 8px; color: var(--sb-text); background: var(--sb-hover); text-decoration: none;
}
.sidebar-user-card:hover { background: var(--sb-active-bg); }
.sidebar-avatar {
  display: grid; place-items: center; flex: 0 0 auto; width: 34px; height: 34px;
  border-radius: 50%; background: var(--sb-avatar-bg); color: var(--sb-avatar-text); font-size: 12px; font-weight: 700;
}
.sidebar-user-meta { display: grid; min-width: 0; line-height: 1.4; }
.sidebar-user-meta > * { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.sidebar-user-meta strong { font-size: 13px; font-weight: 650; }
.sidebar-user-meta > span { font-size: 11.5px; color: var(--sb-muted); }
.is-collapsed .sidebar-footer { flex-direction: column; margin-inline: 8px; }
.hr-sidebar :is(a, button):focus-visible { outline: 2px solid var(--sb-accent); outline-offset: -2px; }
.tablet-nav-backdrop { position: fixed; inset: 0; z-index: 23; }
.tablet-nav-panel {
  position: fixed; z-index: 24; inset: var(--space-16) auto auto calc(var(--sidebar-rail-width) + var(--space-8));
  width: min(17rem, calc(100% - var(--sidebar-rail-width) - var(--space-24)));
  max-height: calc(100dvh - 2 * var(--space-16)); overflow-y: auto;
  padding: var(--space-12); border: 1px solid var(--sb-border);
  border-radius: var(--radius); background: var(--sb-bg-mid); box-shadow: var(--shadow-menu);
}
.tablet-nav-panel .sidebar-row { min-height: var(--tap-target); padding: var(--space-8); overflow-wrap: anywhere; }
.tablet-nav-panel a:focus-visible { outline-color: var(--sb-accent); outline-offset: -2px; }
.is-tablet :is(.sidebar-row, .sidebar-icon-button, .sidebar-user-card) { min-height: var(--tap-target); min-width: var(--tap-target); }
.is-tablet.is-collapsed .sidebar-brand { min-height: var(--space-16); }
@media (width < 768px) {
  .hr-sidebar .sidebar-collapse { display: none; }
  .hr-sidebar .sidebar-close { display: inline-flex; width: 44px; height: 44px; }
  .hr-sidebar :is(.sidebar-icon-button, .sidebar-user-card) { min-height: var(--tap-target); min-width: var(--tap-target); }
  .hr-sidebar :is(.sidebar-row, .sidebar-child) { min-height: 44px; }
  .sidebar-brand { min-height: 56px; }
  .sidebar-search input { font-size: 16px; }
}
</style>
