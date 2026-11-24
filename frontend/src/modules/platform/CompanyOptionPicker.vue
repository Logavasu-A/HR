<script setup>
import { computed, nextTick, ref } from 'vue'

const props = defineProps({
  id: { type: String, required: true },
  options: { type: Array, required: true },
  placeholder: { type: String, required: true },
  disabled: Boolean,
})
const selected = defineModel({ type: String, required: true })
const open = ref(false)
const search = ref('')
const active = ref(-1)
const input = ref(null)
const list = ref(null)
const selectedLabel = computed(() => props.options.find(option => option.value === selected.value)?.label || selected.value)
const filterTerms = computed(() => search.value.toLowerCase().trim().split(/[\s_/]+/))
const matches = computed(() => props.options.filter(option => {
  const text = `${option.value} ${option.label}`.toLowerCase()
  return filterTerms.value.every(term => text.includes(term))
}))
const activeId = computed(() => active.value < 0 ? undefined : `${props.id}-option-${active.value}`)

function show() {
  if (open.value) return
  search.value = ''
  active.value = -1
  open.value = true
}

function close() {
  open.value = false
  active.value = -1
}

function choose(option) {
  selected.value = option.value
  dismiss()
}

async function dismiss() {
  close()
  await nextTick()
  input.value?.select()
}

function filter(event) {
  show()
  search.value = event.target.value
  active.value = matches.value.length ? 0 : -1
  if (list.value) list.value.scrollTop = 0
}

async function move(direction) {
  show()
  if (!matches.value.length) return
  active.value = active.value < 0 && direction < 0
    ? matches.value.length - 1
    : Math.min(matches.value.length - 1, Math.max(0, active.value + direction))
  await nextTick()
  list.value?.children[active.value]?.scrollIntoView({ block: 'nearest' })
}

function confirm() {
  if (!open.value) show()
  else if (active.value >= 0) choose(matches.value[active.value])
}
</script>

<template>
  <div class="company-option-picker">
    <input :id="id" ref="input" role="combobox" aria-autocomplete="list" :aria-expanded="open"
      :aria-controls="id + '-options'" :aria-activedescendant="open ? activeId : undefined"
      :value="open ? search : selectedLabel" :placeholder="placeholder" :disabled="disabled"
      autocomplete="off" @focus="show" @click="show" @input="filter" @blur="close"
      @keydown.down.prevent="move(1)" @keydown.up.prevent="move(-1)"
      @keydown.enter.prevent="confirm" @keydown.esc="dismiss" />
    <svg class="picker-chevron" aria-hidden="true" width="16" height="16" viewBox="0 0 24 24"
      fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
      <path d="m6 9 6 6 6-6" />
    </svg>
    <div v-show="open" class="picker-menu">
      <ul :id="id + '-options'" ref="list" role="listbox" :aria-labelledby="id + '-label'" class="picker-options">
        <li v-for="(option, index) in matches" :id="`${id}-option-${index}`" :key="option.value"
          role="option" :aria-selected="option.value === selected" :class="{ highlighted: index === active }"
          @pointerdown.prevent @click="choose(option)">
          {{ option.label }}
          <span v-if="option.value === selected" aria-hidden="true">✓</span>
        </li>
      </ul>
      <p v-if="open && !matches.length" role="status">No matching options.</p>
    </div>
  </div>
</template>

<style scoped>
.company-option-picker { position: relative; min-inline-size: 0; }
.company-option-picker input { inline-size: 100%; padding-inline-end: 2.375rem; text-overflow: ellipsis; }
.picker-chevron { position: absolute; inset-inline-end: var(--space-12); inset-block-start: 50%; transform: translateY(-50%); color: var(--muted); pointer-events: none; }
.picker-menu {
  position: absolute;
  z-index: 5;
  inset: calc(100% + var(--space-4)) 0 auto;
  padding: var(--space-4);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--card-bg);
  box-shadow: var(--shadow-menu);
}
.picker-options { max-block-size: min(16rem, 38dvh); overflow: auto; margin: 0; padding: 0; list-style: none; }
.picker-options li {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-8);
  min-block-size: 2.75rem;
  padding: var(--space-8);
  border-radius: var(--radius-sm);
  overflow-wrap: anywhere;
  cursor: pointer;
}
.picker-options li:is(:hover, .highlighted, [aria-selected=true]) { color: var(--primary-tint-text); background: var(--primary-tint); }
.picker-options li.highlighted { outline: 1px solid var(--primary); outline-offset: -1px; }
.picker-menu p { margin: 0; padding: var(--space-8); color: var(--muted); }
</style>
