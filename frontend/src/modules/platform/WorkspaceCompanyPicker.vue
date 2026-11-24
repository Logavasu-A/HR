<script setup>
import { computed, nextTick, ref } from 'vue'
import { pretty } from '@/shared/utils'

const props = defineProps({ companies: { type: Array, required: true }, disabled: Boolean })
const selected = defineModel({ type: String, required: true })
const button = ref(null)
const list = ref(null)
const open = ref(false)
const cursor = ref(0)
const current = computed(() => props.companies.find(company => company.id === selected.value))
const optionId = index => `workspace-company-option-${index}`

function highlight(index) {
  cursor.value = Math.max(0, Math.min(index, props.companies.length - 1))
  list.value?.children[cursor.value]?.scrollIntoView({ block: 'nearest' })
}

async function show(fromEnd = false) {
  if (open.value || !props.companies.length) return
  open.value = true
  await nextTick()
  if (!open.value) return
  const selectedIndex = props.companies.indexOf(current.value)
  list.value?.focus({ preventScroll: true })
  highlight(selectedIndex >= 0 ? selectedIndex : fromEnd ? props.companies.length - 1 : 0)
}

function close(restoreFocus = false) {
  open.value = false
  if (restoreFocus) button.value?.focus()
}

function choose(company) {
  selected.value = company.id
  close(true)
}

function onFocusOut(event) {
  if (!event.currentTarget.contains(event.relatedTarget)) close()
}

function onListKey(event) {
  const last = props.companies.length - 1
  switch (event.key) {
    case 'ArrowDown': highlight(cursor.value + 1); break
    case 'ArrowUp': highlight(cursor.value - 1); break
    case 'Home': highlight(0); break
    case 'End': highlight(last); break
    case 'Escape': close(true); break
    case 'Enter':
    case ' ': choose(props.companies[cursor.value]); break
    default: {
      if (event.key.length !== 1 || event.ctrlKey || event.metaKey || event.altKey) return
      const key = event.key.toLocaleLowerCase()
      for (let offset = 1; offset <= props.companies.length; offset++) {
        const index = (cursor.value + offset) % props.companies.length
        if (props.companies[index].name.toLocaleLowerCase().startsWith(key)) {
          highlight(index)
          break
        }
      }
    }
  }
  event.preventDefault()
}
</script>

<template>
  <div class="workspace-company-picker" @focusout="onFocusOut">
    <button id="owner-workspace-company" ref="button" class="company-picker-trigger" type="button"
      aria-haspopup="listbox" :aria-expanded="open" :aria-controls="open ? 'workspace-company-options' : undefined"
      aria-labelledby="workspace-company-label workspace-company-value" :disabled="disabled || !companies.length"
      @click="open ? close() : show()" @keydown.down.prevent="show()" @keydown.up.prevent="show(true)">
      <span id="workspace-company-value" :title="current?.name">{{ current?.name || 'Select company' }}</span>
      <svg aria-hidden="true" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor"
        stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m6 9 6 6 6-6" /></svg>
    </button>
    <ul v-if="open" id="workspace-company-options" ref="list" class="company-picker-menu" role="listbox"
      aria-labelledby="workspace-company-label" :aria-activedescendant="optionId(cursor)" tabindex="0" @keydown="onListKey">
      <li v-for="(company, index) in companies" :id="optionId(index)" :key="company.id"
        role="option" :aria-selected="company.id === selected" :class="{ highlighted: index === cursor }"
        @mousedown.prevent @click="choose(company)">
        <span class="company-picker-details">
          <strong>{{ company.name }}</strong>
          <small>{{ company.code }}</small>
        </span>
        <span class="badge" :class="company.status === 'SUSPENDED' ? 'rejected' : company.status.toLowerCase()">
          {{ pretty(company.status) }}
        </span>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.workspace-company-picker { position: relative; min-inline-size: 0; }
.company-picker-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-8);
  inline-size: 100%;
  min-block-size: var(--platform-control-default);
  padding-inline: var(--space-12);
  border: 1px solid var(--input-border);
  border-radius: var(--radius-sm);
  background: var(--panel);
  color: var(--text);
  font: inherit;
  text-align: start;
  cursor: pointer;
}
.company-picker-trigger:disabled { opacity: 0.65; cursor: default; }
.company-picker-trigger span { min-inline-size: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.company-picker-trigger svg { flex: none; color: var(--muted); }
.company-picker-trigger[aria-expanded=true] svg { transform: rotate(180deg); }
.company-picker-menu {
  position: absolute;
  z-index: 5;
  inset: calc(100% + var(--space-4)) 0 auto;
  max-block-size: min(18rem, 45dvh);
  overflow: auto;
  overscroll-behavior: contain;
  margin: 0;
  padding: var(--space-4);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--card-bg);
  box-shadow: var(--shadow-menu);
  list-style: none;
}
.company-picker-menu li {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--space-8);
  padding: var(--space-8) var(--space-12);
  border-radius: var(--radius-sm);
  cursor: pointer;
}
.company-picker-menu li + li { border-block-start: 1px solid var(--border); }
.company-picker-menu li:is(:hover, .highlighted) { background: var(--page-bg); }
.company-picker-menu li[aria-selected=true] { background: var(--primary-tint); }
.company-picker-menu:focus-visible li.highlighted { outline: 1px solid var(--primary); outline-offset: -1px; }
.company-picker-details { display: grid; gap: 2px; overflow-wrap: anywhere; }
.company-picker-details small { color: var(--muted); }
</style>
