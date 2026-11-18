<script setup>
import { onBeforeUnmount, onMounted, ref, useId } from 'vue'

defineProps({
  title: { type: String, required: true },
  form: { type: Boolean, default: false },
  printable: { type: Boolean, default: false },
})

const emit = defineEmits(['dismiss', 'submit'])
const dialog = ref(null)
const titleId = useId()

onMounted(() => dialog.value.showModal())
onBeforeUnmount(() => dialog.value.close())
</script>

<template>
  <Teleport to="body">
    <dialog
      ref="dialog"
      class="modal"
      :class="{ printable }"
      :aria-labelledby="titleId"
      @cancel.prevent="emit('dismiss')"
      @click.self="emit('dismiss')"
    >
      <component :is="form ? 'form' : 'div'" class="modal-panel" @submit.prevent="emit('submit')">
        <h2 :id="titleId" tabindex="-1" autofocus>{{ title }}</h2>
        <div class="modal-body"><slot /></div>
        <div v-if="$slots.footer" class="modal-foot"><slot name="footer" /></div>
      </component>
    </dialog>
  </Teleport>
</template>
