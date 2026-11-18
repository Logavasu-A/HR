<script>
import { ref } from 'vue'

// Shared across all ActionMenu instances so only one row dropdown stays open at a time.
const openMenuId = ref(null)
let nextId = 1
</script>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref as localRef } from 'vue'

const props = defineProps({
  items: { type: Array, default: () => [] },
  label: { type: String, default: 'Row actions' },
})

const id = nextId++
const triggerRef = localRef(null)
const menuRef = localRef(null)
const pos = localRef({ top: 0, left: 0 })
const placement = localRef('bottom')

const isOpen = computed(() => openMenuId.value === id)

function resolveIcon(item) {
  if (item.icon) return item.icon
  const l = String(item.label || '').toLowerCase()
  if (l.includes('edit')) return 'edit'
  if (l.includes('delete') || l.includes('remove')) return 'delete'
  if (l.includes('approve')) return 'approve'
  if (l.includes('reject')) return 'reject'
  if (l.includes('cancel')) return 'cancel'
  if (l.includes('paid') || l.includes('pay')) return 'pay'
  if (l.includes('hire')) return 'hire'
  if (l.includes('submit')) return 'submit'
  if (l.includes('finalize')) return 'finalize'
  if (l.includes('view') || l.includes('slip')) return 'view'
  if (l.includes('progress')) return 'progress'
  if (l.includes('rate')) return 'rate'
  return 'default'
}

function resolveTone(item) {
  if (item.tone) return item.tone
  if (item.danger) return 'danger'
  const l = String(item.label || '').toLowerCase()
  if (
    l.includes('approve') ||
    l.includes('paid') ||
    l.includes('hire') ||
    l.includes('submit') ||
    l.includes('finalize')
  ) {
    return 'success'
  }
  return 'default'
}

function close() {
  if (openMenuId.value === id) openMenuId.value = null
}

async function positionMenu() {
  await nextTick()
  const btn = triggerRef.value
  const menu = menuRef.value
  if (!btn || !menu) return

  const rect = btn.getBoundingClientRect()
  const menuW = menu.offsetWidth || 176
  const menuH = menu.offsetHeight || 110
  const gap = 6
  const margin = 10

  // Prefer opening below; flip upward when near bottom of viewport.
  const spaceBelow = window.innerHeight - rect.bottom
  const openBelow = spaceBelow >= menuH + gap + margin
  placement.value = openBelow ? 'bottom' : 'top'

  let top = openBelow ? rect.bottom + gap : rect.top - menuH - gap
  top = Math.max(margin, Math.min(top, window.innerHeight - menuH - margin))

  // Align right edge to trigger button, clamped inside viewport.
  let left = rect.right - menuW
  left = Math.max(margin, Math.min(left, window.innerWidth - menuW - margin))

  pos.value = { top, left }
}

function getMenuButtons() {
  return Array.from(menuRef.value?.querySelectorAll('[role="menuitem"]:not(:disabled)') || [])
}

async function toggle() {
  if (isOpen.value) {
    close()
    return
  }
  openMenuId.value = id
  await positionMenu()
}

function select(item) {
  if (item.disabled) return
  close()
  triggerRef.value?.focus({ preventScroll: true })
  item.onClick?.()
}

function onDocClick(e) {
  if (!isOpen.value) return
  if (triggerRef.value?.contains(e.target) || menuRef.value?.contains(e.target)) return
  close()
}

function onKeydown(e) {
  if (!isOpen.value) return
  if (e.key === 'Escape') {
    e.preventDefault()
    close()
    triggerRef.value?.focus()
    return
  }
  if (e.key === 'Tab') {
    close()
    return
  }
  if (['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(e.key)) {
    const buttons = getMenuButtons()
    if (!buttons.length) return
    e.preventDefault()
    const current = buttons.indexOf(document.activeElement)
    let next = 0
    if (e.key === 'ArrowDown') next = current < 0 ? 0 : (current + 1) % buttons.length
    else if (e.key === 'ArrowUp') next = current < 0 ? buttons.length - 1 : (current - 1 + buttons.length) % buttons.length
    else if (e.key === 'Home') next = 0
    else if (e.key === 'End') next = buttons.length - 1
    buttons[next]?.focus()
  }
}

function onScroll(event) {
  if (isOpen.value && event.target !== menuRef.value) close()
}

onMounted(() => {
  document.addEventListener('click', onDocClick, true)
  document.addEventListener('keydown', onKeydown)
  window.addEventListener('scroll', onScroll, true)
  window.addEventListener('resize', onScroll)
})

onUnmounted(() => {
  close()
  document.removeEventListener('click', onDocClick, true)
  document.removeEventListener('keydown', onKeydown)
  window.removeEventListener('scroll', onScroll, true)
  window.removeEventListener('resize', onScroll)
})
</script>

<template>
  <div v-if="items.length" class="action-menu-wrap">
    <button
      ref="triggerRef"
      type="button"
      class="action-menu-btn"
      :class="{ open: isOpen }"
      :aria-label="label"
      :aria-expanded="isOpen"
      aria-haspopup="menu"
      @click.stop="toggle"
    >
      <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
        <circle cx="12" cy="5" r="1.8" />
        <circle cx="12" cy="12" r="1.8" />
        <circle cx="12" cy="19" r="1.8" />
      </svg>
    </button>

    <Teleport to="body">
      <div
        v-if="isOpen"
        ref="menuRef"
        class="action-menu-pop"
        :class="`action-menu-pop--${placement}`"
        role="menu"
        :style="{ top: `${pos.top}px`, left: `${pos.left}px` }"
        @click.stop
      >
        <template v-for="(item, idx) in items" :key="item.label">
          <div v-if="item.danger && idx > 0 && !items[idx - 1]?.danger" class="action-menu-sep" role="separator" />
          <button
            type="button"
            role="menuitem"
            class="action-menu-item"
            :class="`action-menu-item--${resolveTone(item)}`"
            :disabled="item.disabled"
            @click="select(item)"
          >
            <span class="action-menu-icon" aria-hidden="true">
              <!-- Edit -->
              <svg v-if="resolveIcon(item) === 'edit'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M12 20h9" />
                <path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z" />
              </svg>
              <!-- Delete -->
              <svg v-else-if="resolveIcon(item) === 'delete'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M3 6h18" />
                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" />
                <path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                <line x1="10" y1="11" x2="10" y2="17" />
                <line x1="14" y1="11" x2="14" y2="17" />
              </svg>
              <!-- Approve -->
              <svg v-else-if="resolveIcon(item) === 'approve'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="9.5" />
                <path d="m8.5 12.2 2.3 2.3 4.7-4.8" />
              </svg>
              <!-- Reject -->
              <svg v-else-if="resolveIcon(item) === 'reject'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="9.5" />
                <path d="m15 9-6 6" />
                <path d="m9 9 6 6" />
              </svg>
              <!-- Cancel -->
              <svg v-else-if="resolveIcon(item) === 'cancel'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="9.5" />
                <path d="m5.5 5.5 13 13" />
              </svg>
              <!-- Mark paid -->
              <svg v-else-if="resolveIcon(item) === 'pay'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <rect x="2" y="5" width="20" height="14" rx="2.5" />
                <line x1="2" y1="10" x2="22" y2="10" />
              </svg>
              <!-- Hire -->
              <svg v-else-if="resolveIcon(item) === 'hire'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
                <circle cx="8.5" cy="7" r="4" />
                <line x1="20" y1="8" x2="20" y2="14" />
                <line x1="17" y1="11" x2="23" y2="11" />
              </svg>
              <!-- Submit -->
              <svg v-else-if="resolveIcon(item) === 'submit'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M22 2 11 13" />
                <path d="m22 2-7 20-4-9-9-4Z" />
              </svg>
              <!-- Finalize -->
              <svg v-else-if="resolveIcon(item) === 'finalize'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <rect x="4" y="11" width="16" height="10" rx="2" />
                <path d="M8 11V7a4 4 0 0 1 8 0v4" />
              </svg>
              <!-- View slips / payslip -->
              <svg v-else-if="resolveIcon(item) === 'view'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z" />
                <path d="M14 2v6h6" />
                <line x1="8" y1="13" x2="16" y2="13" />
                <line x1="8" y1="17" x2="13" y2="17" />
              </svg>
              <!-- Update progress -->
              <svg v-else-if="resolveIcon(item) === 'progress'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="22 7 13.5 15.5 8.5 10.5 2 17" />
                <polyline points="16 7 22 7 22 13" />
              </svg>
              <!-- Rate myself -->
              <svg v-else-if="resolveIcon(item) === 'rate'" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
              </svg>
              <!-- Fallback -->
              <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <polyline points="9 18 15 12 9 6" />
              </svg>
            </span>
            <span class="action-menu-label">{{ item.label }}</span>
          </button>
        </template>
      </div>
    </Teleport>
  </div>
</template>
