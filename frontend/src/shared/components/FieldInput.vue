<script setup>
import { computed, ref, useId } from 'vue'
import { refLabel } from '@/shared/utils'

const props = defineProps({
  field: { type: Object, required: true },
  modelValue: { default: '' },
  // records for type 'ref', strings for type 'multicheck'
  options: { type: Array, default: () => [] },
})
const emit = defineEmits(['update:modelValue'])

const showPassword = ref(false)
const optionHelpId = useId()
const hasOptionHelp = (option) => props.field.optionHelp && String(option).startsWith(props.field.optionHelp.prefix)
const firstHelpOption = computed(() => props.options.find(hasOptionHelp))

const inputType = computed(() => {
  switch (props.field.type) {
    case 'number': return 'number'
    case 'date': return 'date'
    case 'time': return 'time'
    case 'datetime': return 'datetime-local'
    case 'password': return showPassword.value ? 'text' : 'password'
    default: return 'text'
  }
})

function set(value) {
  emit('update:modelValue', value)
}

function setRef(value) {
  set(value === '' ? '' : Number(value))
}

function toggle(option) {
  const list = Array.isArray(props.modelValue) ? [...props.modelValue] : []
  const i = list.indexOf(option)
  if (i >= 0) list.splice(i, 1)
  else list.push(option)
  set(list)
}
</script>

<template>
  <select v-if="field.type === 'ref'" :value="modelValue" :required="field.required" @change="setRef($event.target.value)">
    <option value="">— none —</option>
    <option v-for="o in options" :key="o.id" :value="o.id">{{ refLabel(o) }}</option>
  </select>

  <select v-else-if="field.type === 'select'" :value="modelValue" :required="field.required" @change="set($event.target.value)">
    <option value="">— none —</option>
    <option v-for="o in field.options" :key="o.value" :value="o.value">{{ o.label }}</option>
  </select>

  <textarea v-else-if="field.type === 'textarea'" :value="modelValue" rows="3" :required="field.required" @input="set($event.target.value)"></textarea>

  <input v-else-if="field.type === 'checkbox'" type="checkbox" class="check" :checked="modelValue === true" @change="set($event.target.checked)" />

  <div v-else-if="field.type === 'multicheck'" class="checks">
    <template v-for="o in options" :key="o">
      <small v-if="o === firstHelpOption" :id="optionHelpId" class="control-note">{{ field.optionHelp.text }}</small>
      <label>
        <input type="checkbox" :checked="Array.isArray(modelValue) && modelValue.includes(o)"
          :aria-describedby="hasOptionHelp(o) ? optionHelpId : undefined" @change="toggle(o)" />
        <span>{{ o }}</span>
      </label>
    </template>
  </div>

  <div v-else-if="field.type === 'password'" class="input-icon-wrap has-trailing">
    <input
      :type="showPassword ? 'text' : 'password'"
      :value="modelValue"
      :required="field.required"
      autocomplete="new-password"
      placeholder="••••••••"
      @input="set($event.target.value)"
    />
    <button
      type="button"
      class="input-trailing-btn"
      :aria-label="showPassword ? 'Hide password' : 'Show password'"
      @click="showPassword = !showPassword"
    >
      <svg v-if="showPassword" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="16" height="16">
        <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/>
        <line x1="1" y1="1" x2="23" y2="23"/>
      </svg>
      <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" width="16" height="16">
        <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
        <circle cx="12" cy="12" r="3"/>
      </svg>
    </button>
  </div>

  <input
    v-else
    :type="inputType"
    :value="modelValue"
    :required="field.required"
    :step="field.type === 'number' ? 'any' : undefined"
    :autocomplete="field.type === 'password' ? 'new-password' : 'off'"
    @input="set($event.target.value)"
  />
</template>
