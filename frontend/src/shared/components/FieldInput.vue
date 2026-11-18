<script setup>
import { computed } from 'vue'
import { refLabel } from '@/shared/utils'

const props = defineProps({
  field: { type: Object, required: true },
  modelValue: { default: '' },
  // records for type 'ref', strings for type 'multicheck'
  options: { type: Array, default: () => [] },
})
const emit = defineEmits(['update:modelValue'])

const inputType = computed(() => {
  switch (props.field.type) {
    case 'number': return 'number'
    case 'date': return 'date'
    case 'time': return 'time'
    case 'datetime': return 'datetime-local'
    case 'password': return 'password'
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
    <label v-for="o in options" :key="o">
      <input type="checkbox" :checked="Array.isArray(modelValue) && modelValue.includes(o)" @change="toggle(o)" />
      {{ o }}
    </label>
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
