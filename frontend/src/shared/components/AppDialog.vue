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
        <header class="modal-head">
          <h2 :id="titleId" tabindex="-1" autofocus>{{ title }}</h2>
          <button
            type="button"
            class="modal-close-btn"
            aria-label="Close dialog"
            @click="emit('dismiss')"
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="18" y1="6" x2="6" y2="18"></line>
              <line x1="6" y1="6" x2="18" y2="18"></line>
            </svg>
          </button>
        </header>
        <div class="modal-body"><slot /></div>
        <div v-if="$slots.footer" class="modal-foot"><slot name="footer" /></div>
      </component>
    </dialog>
  </Teleport>
</template>
