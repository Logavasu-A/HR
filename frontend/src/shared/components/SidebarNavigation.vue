<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const props = defineProps({
  groups: { type: Array, required: true },
  path: { type: Function, required: true },
  profile: { type: Object, default: null },
  platform: Boolean,
  workspaceName: { type: String, default: '' },
  preferenceScope: { type: String, default: '' },
  finance: Boolean,
  open: Boolean,
  pendingLeaves: { type: Number, default: 0 },
  unread: { type: Number, default: 0 },
})
const emit = defineEmits(['open', 'close', 'navigate', 'logout', 'finance'])
const router = useRouter()
const route = useRoute()
const nav = ref(null)
const search = ref(null)
const flyoutPanel = ref(null)
const flyoutSearch = ref(null)
const flyoutSection = ref(null)
const flyoutOpen = ref(false)
const flyoutTop = ref(16)
let flyoutTrigger
let hoverTimer
let flyoutPreview = false
const toggle = ref(null)
const query = ref('')
const expandedId = ref(null)
const viewport = window.matchMedia('(width < 768px)')
const tabletViewport = window.matchMedia('(768px <= width < 1025px)')
const mobile = ref(viewport.matches)
const tablet = ref(tabletViewport.matches)
const preference = props.platform ? 'hrms_platform_sidebar_collapsed' : 'hrms_workspace_sidebar_collapsed'
const collapsed = ref(localStorage.getItem(preference) === 'true')
const themeKey = 'hrms_sidebar_theme'
const theme = ref(localStorage.getItem(themeKey) === 'light' ? 'light' : 'dark')
const pinnedPaths = ref([])
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
  bell: '<path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9m-8 12a2 2 0 0 0 4 0"/>',
  search: '<circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 4 4"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  check: '<path d="m5 12 4 4L19 6"/>',
  play: '<path d="m9 5 11 7-11 7V5Z"/>',
  star: '<path d="m12 3 2.8 5.7 6.2.9-4.5 4.4 1.1 6.2-5.6-3-5.6 3 1.1-6.2L3 9.6l6.2-.9L12 3Z"/>',
  lock: '<rect x="5" y="10" width="14" height="11" rx="2"/><path d="M8 10V7a4 4 0 1 1 8 0v3m-4 4v3"/>',
  sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5"/>',
  moon: '<path d="M20 15.5A8 8 0 0 1 8.5 4 8 8 0 1 0 20 15.5Z"/>',
}
const itemIcons = {
  '/dashboard': 'dashboard', '/company-details': 'organization', '/my': 'home',
  '/my/leaves': 'calendar', '/my/attendance': 'clock', '/my/expenses': 'expense',
  '/my/goals': 'goal', '/my/appraisals': 'goal', '/my/payslips': 'payroll',
  '/payroll-runs': 'payroll', '/notifications': 'bell',
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
const quickDestinations = [
  { path: '/employees', icon: 'plus' },
  { path: '/leave-applications', icon: 'check' },
  { path: '/payroll-runs', icon: 'play' },
  { path: '/job-openings', icon: 'hiring' },
]

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
const pages = computed(() => new Map(sections.value.flatMap(section => section.items.flatMap(item =>
  (item.links || [item]).map(link => [link.path, { ...link, icon: link.icon || item.icon, section: section.id }]),
))))
const quickLinks = computed(() => quickDestinations.flatMap(({ path, icon }) => {
  const page = pages.value.get(path)
  return page ? [{ ...page, icon }] : []
}))
const pinned = computed(() => pinnedPaths.value.map(path => pages.value.get(path)).filter(Boolean))
const pinKey = computed(() => `hrms_sidebar_pins:${props.preferenceScope}`)
watch(pinKey, key => {
  try {
    const saved = JSON.parse(localStorage.getItem(key))
    pinnedPaths.value = Array.isArray(saved) ? saved.filter(path => typeof path === 'string') : ['/leave-applications', '/payroll-runs']
  } catch {
    pinnedPaths.value = ['/leave-applications', '/payroll-runs']
  }
}, { immediate: true })

function setTheme(value) {
  theme.value = value
  localStorage.setItem(themeKey, value)
}

function togglePin(path) {
  if (!pages.value.has(path)) return
  pinnedPaths.value = pinnedPaths.value.includes(path)
    ? pinnedPaths.value.filter(item => item !== path)
    : [...pinnedPaths.value, path]
  localStorage.setItem(pinKey.value, JSON.stringify(pinnedPaths.value))
}

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
const flyoutSections = computed(() => flyoutSection.value
  ? filtered.value.filter(section => section.id === flyoutSection.value)
  : filtered.value)

function isCurrent(item) {
  const destination = props.path(item.path)
  return route.path === destination || (['/employees', '/platform/workspace'].includes(item.path)
    && route.path.startsWith(destination + '/'))
}
const isActive = item => item.links ? item.links.some(isCurrent) : isCurrent(item)
const currentPage = computed(() => [...pages.value.values()].find(isCurrent))
const branches = computed(() => sections.value.flatMap(section => section.items).filter(item => item.links))
const activeBranchId = computed(() => branches.value.find(isActive)?.id ?? null)
const isExpanded = item => expandedId.value === item.id
const clearSearch = () => { query.value = '' }
const navigate = () => { closeFlyout(false); clearSearch(); emit('navigate') }

function toggleBranch(item) {
  expandedId.value = isExpanded(item) ? null : item.id
}

function closeFlyout(restoreFocus = true) {
  clearTimeout(hoverTimer)
  if (!flyoutOpen.value) return
  flyoutOpen.value = false
  flyoutPreview = false
  clearSearch()
  if (restoreFocus) flyoutTrigger?.focus({ preventScroll: true })
}

watch([() => route.path, activeBranchId], ([, id]) => {
  if (id) expandedId.value = id
  revealActiveLink()
}, { immediate: true })

watch(branches, available => {
  if (!available.some(isExpanded)) expandedId.value = null
})

watch(matches, term => {
  if (!term || rail.value) return
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

async function revealActiveLink() {
  await nextTick()
  const list = nav.value
  const active = list?.querySelector('a[aria-current="page"], .rail-button.nav-active')
  if (!list?.clientHeight || !active) return
  const viewport = list.getBoundingClientRect()
  const row = active.getBoundingClientRect()
  if (row.bottom > viewport.bottom) list.scrollTop += row.bottom - viewport.bottom + 8
  else if (row.top < viewport.top) list.scrollTop += row.top - viewport.top - 8
}

async function toggleRail() {
  closeFlyout(false)
  clearSearch()
  setCollapsed(!collapsed.value)
  await nextTick()
  if (rail.value && nav.value) nav.value.scrollTop = 0
  revealActiveLink()
  toggle.value?.focus()
}

function positionFlyout() {
  const panel = flyoutPanel.value
  if (!panel) return
  const triggerTop = flyoutTrigger?.getBoundingClientRect().top ?? 12
  const panelHeight = Math.min(panel.scrollHeight + 2, window.innerHeight - 24)
  flyoutTop.value = Math.max(12, Math.min(triggerTop, window.innerHeight - panelHeight - 12))
}

function trackRailScroll() {
  const list = nav.value
  if (!flyoutOpen.value || !list?.contains(flyoutTrigger)) return
  const viewport = list.getBoundingClientRect()
  const trigger = flyoutTrigger.getBoundingClientRect()
  if (trigger.bottom <= viewport.top || trigger.top >= viewport.bottom) closeFlyout(false)
  else positionFlyout()
}

async function showFlyout(section, trigger, preview = false) {
  clearTimeout(hoverTimer)
  flyoutTrigger = trigger
  flyoutPreview = preview
  clearSearch()
  flyoutTop.value = 12
  flyoutSection.value = section?.id ?? null
  flyoutOpen.value = true
  await nextTick()
  if (trigger !== flyoutTrigger || !flyoutPanel.value || !flyoutOpen.value) return
  positionFlyout()
  if (!preview) {
    if (section) flyoutPanel.value.querySelector('a[aria-current="page"], a')?.focus()
    else flyoutSearch.value?.focus()
  }
}

function openSection(section, event) {
  if (flyoutOpen.value && flyoutSection.value === section.id && !flyoutPreview) closeFlyout()
  else showFlyout(section, event.currentTarget)
}

function previewSection(section, event) {
  if (tablet.value || event.pointerType !== 'mouse' || (flyoutOpen.value && !flyoutPreview)
    || document.querySelector('dialog[open]')) return
  showFlyout(section, event.currentTarget, true)
}

function cancelPreviewClose() {
  clearTimeout(hoverTimer)
}

function schedulePreviewClose() {
  if (!flyoutPreview) return
  cancelPreviewClose()
  hoverTimer = setTimeout(() => closeFlyout(false), 180)
}

function dismissOnOutsideClick(event) {
  if (flyoutOpen.value && !flyoutPanel.value?.contains(event.target) && !flyoutTrigger?.contains(event.target)) closeFlyout(false)
}

async function focusSearch() {
  if (rail.value) return showFlyout(null, document.activeElement)
  if (mobile.value) emit('open')
  await nextTick()
  search.value?.focus()
  search.value?.select()
}

function handleShortcut(event) {
  if (event.key === 'Escape' && flyoutOpen.value) {
    closeFlyout()
    return
  }
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
  closeFlyout(false)
}
onMounted(() => {
  viewport.addEventListener('change', syncViewport)
  tabletViewport.addEventListener('change', syncViewport)
  window.addEventListener('keydown', handleShortcut)
  window.addEventListener('pointerdown', dismissOnOutsideClick)
})
onUnmounted(() => {
  viewport.removeEventListener('change', syncViewport)
  tabletViewport.removeEventListener('change', syncViewport)
  window.removeEventListener('keydown', handleShortcut)
  window.removeEventListener('pointerdown', dismissOnOutsideClick)
  clearTimeout(hoverTimer)
})
defineExpose({ focusNavigation, clearSearch })
</script>

<template>
  <aside class="sidebar hr-sidebar" :class="{ open, 'is-collapsed': rail, 'is-tablet': tablet }"
    :data-theme="theme" @keydown.tab="trapDrawerFocus">
    <div class="sidebar-brand">
      <div class="sidebar-brand-mark" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.8"
          stroke-linecap="round" stroke-linejoin="round" v-html="icons[platform ? 'shield' : 'people']"></svg>
      </div>
      <div v-if="!rail" class="sidebar-brand-name" :title="workspaceName || 'HRMS'">
        <strong>{{ workspaceName || 'HRMS' }}</strong>
        <span v-if="workspaceName">HRMS People Platform</span>
      </div>
      <button v-if="!rail" ref="toggle" type="button" class="sidebar-icon-button sidebar-collapse"
        aria-label="Collapse sidebar" title="Collapse sidebar" aria-expanded="true" :aria-controls="navId" @click="toggleRail">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <rect x="3" y="4" width="18" height="16" rx="2"/><path d="M9 4v16"/>
          <path d="m16 9-3 3 3 3"/>
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

    <nav :id="navId" ref="nav" :aria-label="platform ? 'Platform sidebar' : 'Workspace navigation'"
      @scroll="trackRailScroll">
      <div v-if="rail" class="sidebar-rail">
        <template v-for="section in sections" :key="section.id">
          <template v-if="platform || (section.items.length === 1 && !section.items[0].links)">
            <router-link v-for="item in section.items" :key="item.id" :to="path(item.path)"
              class="sidebar-icon-button rail-button" :class="{ 'nav-active': isCurrent(item) }"
              :data-section="section.id" :aria-current="isCurrent(item) ? 'page' : undefined"
              :aria-label="item.title" :title="item.title" @click="navigate">
              <span class="sidebar-glyph"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"
                stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" v-html="icons[item.icon]"></svg></span>
            </router-link>
          </template>
          <button v-else type="button" class="sidebar-icon-button rail-button"
            :class="{ 'nav-active': section.items.some(isActive) }" :aria-label="'Open ' + section.name + ' menu'"
            :aria-expanded="flyoutOpen && flyoutSection === section.id" :data-section="section.id"
            :aria-controls="flyoutOpen && flyoutSection === section.id ? navId + '-flyout' : undefined"
            @click="openSection(section, $event)" @pointerenter="previewSection(section, $event)" @pointerleave="schedulePreviewClose">
            <span class="sidebar-glyph"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"
              stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" v-html="icons[section.icon]"></svg></span>
            <span v-if="section.id === 'leave' && pendingLeaves" class="sidebar-rail-count" :title="`${pendingLeaves} pending leave requests`"></span>
          </button>
        </template>
      </div>

      <template v-else>
        <div v-if="!matches && quickLinks.length" class="sidebar-quick-links" aria-label="Quick links">
          <router-link v-for="link in quickLinks" :key="link.path" :to="path(link.path)"
            class="sidebar-icon-button sidebar-quick-link" :data-section="link.section" :title="link.title"
            :aria-label="link.title" @click="navigate">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"
              stroke-linejoin="round" aria-hidden="true" v-html="icons[link.icon]"></svg>
          </router-link>
        </div>
        <div v-if="!matches && (pinned.length || currentPage)" class="sidebar-section sidebar-pinned">
          <div class="sidebar-pinned-heading">
            <h2 class="sidebar-section-title">
              <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" v-html="icons.star"></svg>
              Pinned
            </h2>
            <button v-if="currentPage && !pinnedPaths.includes(currentPage.path)" type="button" class="sidebar-icon-button sidebar-pin"
              :aria-label="`Pin ${currentPage.title}`" :title="`Pin ${currentPage.title}`" @click="togglePin(currentPage.path)">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
                aria-hidden="true" v-html="icons.plus"></svg>
            </button>
          </div>
          <div v-for="link in pinned" :key="link.path" class="sidebar-pinned-item" :data-section="link.section">
            <router-link :to="path(link.path)" class="sidebar-row" :class="{ 'nav-active': isCurrent(link) }" @click="navigate">
              <span class="sidebar-glyph"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"
                stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" v-html="icons[link.icon]"></svg></span>
              <span class="sidebar-label">{{ link.title }}</span>
            </router-link>
            <button type="button" class="sidebar-icon-button sidebar-pin" :title="`Unpin ${link.title}`"
              :aria-label="`Unpin ${link.title}`" @click="togglePin(link.path)">
              <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" v-html="icons.star"></svg>
            </button>
          </div>
        </div>
        <p v-if="matches && !filtered.length" class="sidebar-empty" role="status">No matching pages</p>
        <div v-for="section in filtered" :id="navId + '-section-' + section.id" :key="section.id"
          class="sidebar-section" :data-section="section.id">
          <h2 class="sidebar-section-title">{{ section.name }}</h2>
          <template v-for="item in section.items" :key="item.id">
            <template v-if="item.links">
              <button type="button" class="sidebar-row" :class="{ 'nav-active': isActive(item) }"
                :aria-expanded="isExpanded(item)" :aria-controls="navId + '-' + item.id"
                @click="toggleBranch(item)">
                <span class="sidebar-glyph"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"
                  stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" v-html="icons[item.icon]"></svg></span>
                <span class="sidebar-label">{{ item.title }}</span>
                <span v-if="item.id === 'leave' && pendingLeaves" class="sidebar-badge">
                  {{ pendingLeaves }}<span class="visually-hidden"> pending requests</span>
                </span>
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
              <span class="sidebar-glyph"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"
                stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" v-html="icons[item.icon]"></svg></span>
              <span class="sidebar-label">{{ item.title }}</span>
              <span v-if="item.path === '/notifications' && unread" class="sidebar-badge">
                {{ unread }}<span class="visually-hidden"> unread</span>
              </span>
            </router-link>
          </template>
        </div>
      </template>
    </nav>

    <footer class="sidebar-footer">
      <button v-if="finance && !rail" type="button" class="sidebar-row sidebar-finance" @click="emit('finance')">
        <span class="sidebar-glyph"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"
          stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" v-html="icons.lock"></svg></span>
        <span class="sidebar-label">Financial approvals</span>
      </button>
      <div v-if="profile" class="sidebar-account">
      <component :is="profile.accountLink ? 'router-link' : 'div'" class="sidebar-user-card"
        :to="profile.accountLink" :aria-label="profile.accountLink ? 'Account & security' : undefined"
        :title="profile.name" @click="profile.accountLink && navigate()">
        <span class="sidebar-avatar" :aria-hidden="!rail">{{ profile.initials }}</span>
        <span v-if="!rail" class="sidebar-user-meta">
          <strong>{{ profile.name }}</strong><span>{{ profile.role }}</span>
        </span>
      </component>
      <button v-if="profile.signOut" type="button" class="sidebar-icon-button" aria-label="Sign out" title="Sign out" @click="emit('logout')">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M10 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5m5 4 5 5-5 5m5-5H9"/>
        </svg>
      </button>
      </div>
      <div v-if="!rail" class="sidebar-theme" role="group" aria-label="Sidebar theme">
        <button v-for="option in ['light', 'dark']" :key="option" type="button" :aria-pressed="theme === option"
          @click="setTheme(option)">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"
            stroke-linejoin="round" aria-hidden="true" v-html="icons[option === 'light' ? 'sun' : 'moon']"></svg>
          {{ option === 'light' ? 'Light' : 'Dark' }}
        </button>
      </div>
      <button v-if="rail && !tablet" ref="toggle" type="button" class="sidebar-icon-button sidebar-collapse rail-button"
        aria-label="Expand sidebar" title="Expand sidebar" aria-expanded="false" :aria-controls="navId" @click="toggleRail">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"
          stroke-linejoin="round" aria-hidden="true"><rect x="3" y="4" width="18" height="16" rx="2"/><path d="M9 4v16m4-11 3 3-3 3"/></svg>
      </button>
    </footer>
  </aside>
  <Teleport to="body">
    <template v-if="rail && flyoutOpen">
      <div v-if="tablet" class="tablet-nav-backdrop" aria-hidden="true"></div>
      <nav :id="navId + '-flyout'" ref="flyoutPanel" class="sidebar-flyout" :data-theme="theme"
        :style="{ '--flyout-top': flyoutTop + 'px' }" aria-label="Sidebar submenu"
        @pointerenter="cancelPreviewClose" @pointerleave="schedulePreviewClose">
        <form v-if="!flyoutSection" class="sidebar-search" role="search" @submit.prevent="selectSearchResult">
          <input ref="flyoutSearch" v-model="query" type="search" autocomplete="off" aria-label="Search menu"
            placeholder="Search menu" @keydown.esc="escapeSearch"/>
        </form>
        <p v-if="!flyoutSections.length" class="sidebar-empty" role="status">No matching pages</p>
        <div v-for="section in flyoutSections" :key="section.id" class="sidebar-section" :data-section="section.id">
          <h2 class="sidebar-flyout-title">{{ section.name }}</h2>
          <template v-for="item in section.items" :key="item.id">
            <h3 v-if="item.links && item.title !== section.name" class="sidebar-flyout-group">{{ item.title }}</h3>
            <router-link v-for="link in item.links || [item]" :key="link.path" :to="path(link.path)" class="sidebar-row"
              :class="{ 'nav-active': isCurrent(link) }" :aria-current="isCurrent(link) ? 'page' : undefined" @click="navigate">
              <span class="sidebar-label">{{ link.title }}</span>
            </router-link>
          </template>
        </div>
      </nav>
    </template>
  </Teleport>
</template>

<style scoped>
.hr-sidebar, .sidebar-flyout {
  --menu-bg: #0b3731;
  --menu-surface: #174b43;
  --menu-raised: #20574e;
  --menu-active: #27534b;
  --menu-line: #3b665e;
  --menu-fg: #f0faf7;
  --menu-text: #d0e6df;
  --menu-muted: #a0bfb6;
  --menu-mark: #5de1cb;
  --menu-mark-fg: #092f29;
  --menu-focus: #f0c15b;
}

:is(.hr-sidebar, .sidebar-flyout)[data-theme="light"] {
  --menu-bg: #f5faf7;
  --menu-surface: #edf6f0;
  --menu-raised: #e3f0e8;
  --menu-active: #daede1;
  --menu-line: #abc7b8;
  --menu-fg: #183e35;
  --menu-text: #285146;
  --menu-muted: #47685e;
  --menu-mark: #2a7865;
  --menu-mark-fg: #fff;
  --menu-focus: #9a610d;
}

.hr-sidebar {
  width: 16.875rem;
  color: var(--menu-text);
  background: linear-gradient(155deg, #11554c 0%, var(--menu-bg) 46%, #081f1a 100%);
  border-color: var(--menu-line);
  border-block-start: 3px solid var(--accent);
}
.hr-sidebar[data-theme="light"] { background: linear-gradient(155deg, #e6f3ec, var(--menu-bg) 65%); }
.hr-sidebar.is-collapsed { width: var(--sidebar-rail-width); }
.hr-sidebar.is-tablet, .hr-sidebar.is-collapsed { z-index: 25; }

.sidebar-brand {
  flex: none;
  display: flex;
  align-items: center;
  gap: var(--space-8);
  min-block-size: 3.75rem;
  margin: var(--space-12);
  padding: var(--space-8);
  border: 1px solid var(--menu-line);
  border-radius: var(--radius);
  background: var(--menu-surface);
}
.sidebar-brand-mark {
  display: grid;
  place-items: center;
  flex: 0 0 2.25rem;
  block-size: 2.25rem;
  color: var(--menu-mark-fg);
  background: var(--menu-mark);
  border-radius: var(--radius-sm);
}
.sidebar-brand-name { flex: 1; display: grid; min-inline-size: 0; gap: 2px; }
.sidebar-brand-name > *, .sidebar-user-meta > * {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sidebar-brand-name strong, .sidebar-user-meta strong { color: var(--menu-fg); font-size: .8125rem; font-weight: 650; }
.sidebar-brand-name strong {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  white-space: normal;
  overflow-wrap: anywhere;
  line-height: 1.25;
}
.sidebar-brand-name span, .sidebar-user-meta > span { color: var(--menu-muted); font-size: .6875rem; }
.sidebar-search-area { padding: 0 var(--space-12) var(--space-4); }
.sidebar-search {
  display: flex;
  align-items: center;
  gap: var(--space-8);
  min-block-size: var(--tap-target);
  padding-inline: var(--space-8);
  border: 1px solid var(--menu-line);
  border-radius: var(--radius-sm);
  background: var(--menu-surface);
  color: var(--menu-muted);
}
.sidebar-search:focus-within { border-color: var(--menu-focus); }
.sidebar-search input {
  min-inline-size: 0;
  inline-size: 100%;
  padding: 0;
  min-block-size: 2.5rem;
  border: 0;
  background: none;
  box-shadow: none;
  color: var(--menu-fg);
  outline: none;
  font: inherit;
  font-size: .8125rem;
}
.sidebar-search input::placeholder { color: var(--menu-muted); }
.sidebar-search input::-webkit-search-cancel-button { filter: brightness(0) invert(1); }
[data-theme="light"] .sidebar-search input::-webkit-search-cancel-button { filter: none; }
.sidebar-search kbd {
  flex: none;
  padding: 0 var(--space-4);
  border: 1px solid var(--menu-line);
  border-radius: 5px;
  font: inherit;
  font-size: .6875rem;
}

[data-section] { --section-accent: #60ddc9; }
[data-section="core-hr"], [data-section="administration"] { --section-accent: #bfb1ff; }
[data-section="leave"] { --section-accent: #ffb978; }
[data-section="attendance"], [data-section="my-space"] { --section-accent: #90c5ff; }
[data-section="payroll"] { --section-accent: #f7cc72; }
[data-section="recruitment"] { --section-accent: #f4a0d4; }
[data-section="performance"], [data-section="expenses"] { --section-accent: #92dfae; }
[data-theme="light"] [data-section="overview"], [data-theme="light"] [data-section="platform"] { --section-accent: #176d5c; }
[data-theme="light"] [data-section="core-hr"], [data-theme="light"] [data-section="administration"] { --section-accent: #644798; }
[data-theme="light"] [data-section="leave"], [data-theme="light"] [data-section="payroll"] { --section-accent: #905611; }
[data-theme="light"] [data-section="attendance"], [data-theme="light"] [data-section="my-space"] { --section-accent: #245e99; }
[data-theme="light"] [data-section="recruitment"] { --section-accent: #9b3a75; }
[data-theme="light"] [data-section="performance"], [data-theme="light"] [data-section="expenses"] { --section-accent: #216b44; }

.hr-sidebar nav { padding: var(--space-8) var(--space-12) var(--space-16); overscroll-behavior: contain; scrollbar-width: thin; scrollbar-color: var(--menu-line) transparent; }
.sidebar-section + .sidebar-section { margin-block-start: var(--space-12); }
.sidebar-section-title {
  display: flex;
  align-items: center;
  gap: var(--space-8);
  margin: 0;
  padding: var(--space-4) var(--space-8);
  color: var(--menu-muted);
  font-size: .6875rem;
  font-weight: 700;
  letter-spacing: .075em;
  text-transform: uppercase;
}
.sidebar-section-title::before {
  content: '';
  inline-size: 6px;
  block-size: 6px;
  border-radius: 50%;
  background: var(--section-accent, var(--menu-focus));
}
.sidebar-row, .sidebar-icon-button {
  display: flex;
  align-items: center;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--menu-text);
  font: inherit;
  text-decoration: none;
  text-align: start;
  cursor: pointer;
}
.hr-sidebar .sidebar-row {
  width: 100%;
  min-block-size: 2.5rem;
  padding: var(--space-4) var(--space-8);
  margin: 1px 0;
  gap: var(--space-8);
  font-size: .84375rem;
  font-weight: 500;
}
.sidebar-row:hover, .sidebar-icon-button:hover, .sidebar-user-card:hover {
  color: var(--menu-fg);
  background: var(--menu-raised);
}
:is(.hr-sidebar, .sidebar-flyout) .nav-active,
.hr-sidebar .rail-button[aria-expanded="true"] {
  color: var(--menu-fg);
  background: var(--menu-active);
  box-shadow: inset 0 0 0 1px var(--menu-line), inset 2px 0 var(--menu-focus);
  font-weight: 650;
}
.sidebar-glyph {
  display: grid;
  place-items: center;
  flex: 0 0 1.875rem;
  block-size: 1.875rem;
  color: var(--section-accent, var(--menu-mark));
  background: color-mix(in srgb, currentColor 17%, var(--menu-bg));
  border-radius: var(--radius-sm);
}
.sidebar-row svg, .sidebar-icon-button svg, .sidebar-search svg, .sidebar-theme svg {
  width: 17px;
  height: 17px;
  flex: none;
}
.sidebar-label { flex: 1; min-inline-size: 0; overflow-wrap: anywhere; }
.sidebar-row .sidebar-chevron { inline-size: 12px; block-size: 12px; color: var(--menu-muted); }
.sidebar-chevron.is-open { transform: rotate(90deg); }
.sidebar-submenu {
  margin: 1px 0 var(--space-4) 1.375rem;
  padding: 0 0 0 var(--space-12);
  list-style: none;
  border-inline-start: 1px solid var(--menu-line);
}
.sidebar-submenu li { position: relative; }
.sidebar-submenu li::before {
  content: '';
  position: absolute;
  inset-block-start: 50%;
  inset-inline-start: calc(-1 * var(--space-12));
  inline-size: var(--space-8);
  border-block-start: 1px solid var(--menu-line);
}
.hr-sidebar .sidebar-child { min-block-size: 2rem; font-size: .8125rem; }
.sidebar-badge {
  flex: none;
  display: inline-grid;
  place-items: center;
  min-inline-size: 1.3125rem;
  padding-inline: var(--space-4);
  border-radius: 999px;
  background: var(--accent);
  color: #292007;
  font-size: .6875rem;
  font-weight: 700;
  line-height: 1.125rem;
}
.sidebar-empty { padding: var(--space-8); margin: 0; color: var(--menu-muted); }
.hr-sidebar .sidebar-icon-button {
  inline-size: 2rem;
  block-size: 2rem;
  padding: 0;
  margin: 0;
  justify-content: center;
  flex: none;
}
.hr-sidebar .sidebar-collapse, .hr-sidebar .sidebar-close { color: var(--menu-muted); }
.hr-sidebar .sidebar-close { display: none; }
.hr-sidebar .rail-button {
  position: relative;
  inline-size: var(--tap-target);
  block-size: var(--tap-target);
}
.sidebar-rail-count {
  position: absolute;
  inset: 5px 5px auto auto;
  inline-size: 8px;
  block-size: 8px;
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 0 2px var(--menu-bg);
}
.is-collapsed .sidebar-brand {
  justify-content: center;
  padding-inline: 0;
  border-color: transparent;
  background: transparent;
}
.is-collapsed .sidebar-search-area { display: flex; justify-content: center; padding-inline: 0; }
.is-collapsed .sidebar-search-area .rail-button { border: 1px solid var(--menu-line); background: var(--menu-surface); }
.is-collapsed nav { padding-inline: 0; }
.sidebar-rail { display: grid; justify-items: center; gap: var(--space-4); }

.sidebar-quick-links { display: flex; gap: var(--space-4); margin-block-end: var(--space-8); }
.hr-sidebar .sidebar-quick-link {
  flex: 1 1 0;
  min-inline-size: 0;
  min-block-size: var(--tap-target);
  border: 1px solid var(--menu-line);
  background: color-mix(in srgb, var(--section-accent) 10%, var(--menu-bg));
  color: var(--section-accent);
}
.sidebar-quick-link:hover { background: var(--menu-raised); }
.sidebar-pinned-heading, .sidebar-pinned-item { display: flex; align-items: center; justify-content: space-between; }
.sidebar-pinned-heading .sidebar-section-title::before { display: none; }
.sidebar-pinned-heading .sidebar-section-title svg { inline-size: 13px; block-size: 13px; color: var(--menu-focus); }
.sidebar-pinned-item .sidebar-row { flex: 1; min-inline-size: 0; }
.sidebar-pinned .sidebar-pin { color: var(--menu-focus); }
.sidebar-pinned .sidebar-pin svg { inline-size: 15px; block-size: 15px; }

.sidebar-footer {
  flex: none;
  display: grid;
  gap: var(--space-8);
  margin-inline: var(--space-12);
  padding-block: var(--space-8) var(--space-12);
  border-block-start: 1px solid var(--menu-line);
}
.hr-sidebar .sidebar-finance {
  margin: 0;
  border: 1px solid var(--menu-line);
  color: var(--menu-fg);
  background: var(--menu-surface);
  font-weight: 600;
}
.hr-sidebar .sidebar-finance:hover { background: var(--menu-raised); }
.sidebar-account {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-4);
  background: var(--menu-surface);
  border: 1px solid var(--menu-line);
  border-radius: var(--radius-sm);
}
.sidebar-user-card {
  display: flex;
  align-items: center;
  gap: var(--space-8);
  min-inline-size: 0;
  flex: 1;
  padding: var(--space-4);
  border-radius: var(--radius-sm);
  color: var(--menu-fg);
  text-decoration: none;
}
.sidebar-avatar {
  display: grid;
  place-items: center;
  flex: 0 0 2rem;
  block-size: 2rem;
  border-radius: 50%;
  background: var(--menu-mark);
  color: var(--menu-mark-fg);
  font-size: .75rem;
  font-weight: 700;
}
.sidebar-user-meta { display: grid; min-inline-size: 0; line-height: 1.4; }
.sidebar-theme {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 2px;
  padding: 2px;
  border: 1px solid var(--menu-line);
  border-radius: var(--radius-sm);
}
.sidebar-theme button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-4);
  min-block-size: 1.875rem;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--menu-muted);
  font: inherit;
  font-size: .75rem;
  cursor: pointer;
}
.sidebar-theme button[aria-pressed="true"], .sidebar-theme button:hover {
  background: var(--menu-surface);
  color: var(--menu-fg);
}
.is-collapsed :is(.sidebar-footer, .sidebar-account) { justify-items: center; justify-content: center; }
.is-collapsed .sidebar-account { flex-direction: column; background: transparent; border: 0; padding: 0; }
.visually-hidden { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); white-space: nowrap; }
:is(.hr-sidebar, .sidebar-flyout) :is(a, button):focus-visible { outline: 2px solid var(--menu-focus); outline-offset: -2px; }

.tablet-nav-backdrop { position: fixed; inset: 0; z-index: 23; }
.sidebar-flyout {
  position: fixed;
  z-index: 26;
  inset: var(--flyout-top, 16px) auto auto calc(var(--sidebar-rail-width) + var(--space-8));
  inline-size: min(17rem, calc(100% - var(--sidebar-rail-width) - var(--space-24)));
  max-block-size: calc(100dvh - var(--flyout-top, 16px) - var(--space-12));
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: var(--space-12);
  border: 1px solid var(--menu-line);
  border-radius: var(--radius);
  background: var(--menu-bg);
  box-shadow: var(--shadow-menu);
}
.sidebar-flyout-title {
  margin: 0 0 var(--space-8);
  padding: var(--space-4) var(--space-8) var(--space-8);
  border-block-end: 1px solid var(--menu-line);
  color: var(--menu-fg);
  font-size: .9375rem;
}
.sidebar-flyout-group {
  margin: var(--space-8) 0 var(--space-4);
  padding-inline: var(--space-8);
  color: var(--menu-muted);
  font-size: .75rem;
}
.sidebar-flyout .sidebar-row { min-block-size: var(--tap-target); padding: var(--space-8); }
.sidebar-child[aria-current="page"]::after, .sidebar-flyout a[aria-current="page"]::after {
  content: '';
  inline-size: 6px;
  block-size: 6px;
  flex: none;
  margin-inline-start: auto;
  border-radius: 50%;
  background: var(--menu-focus);
}
.sidebar-flyout .sidebar-search { margin-bottom: var(--space-8); }
@media (width < 768px) {
  .hr-sidebar { width: min(19rem, 86vw); }
  .hr-sidebar .sidebar-collapse { display: none; }
  .hr-sidebar .sidebar-close { display: inline-flex; }
  .hr-sidebar :is(.sidebar-icon-button, .sidebar-user-card) { min-block-size: var(--tap-target); min-inline-size: var(--tap-target); }
  .hr-sidebar .sidebar-row, .sidebar-theme button { min-block-size: var(--tap-target); }
  .sidebar-search input { font-size: 16px; }
}
@media (max-height: 500px) {
  .sidebar-footer { gap: var(--space-4); padding-block: var(--space-4); }
}
</style>
