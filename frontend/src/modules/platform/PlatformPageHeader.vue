<script setup>
defineProps({
  title: { type: String, required: true },
})
</script>

<template>
  <header class="platform-page-header">
    <div class="platform-page-copy">
      <h1>{{ title }}</h1>
    </div>
    <div v-if="$slots.default || $slots.back" class="platform-page-actions">
      <slot name="back" />
      <slot />
    </div>
  </header>
</template>

<style scoped>
.platform-page-header {
  display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between;
  gap: var(--space-16) var(--space-24); padding: var(--space-16) 0; margin-bottom: var(--space-16); min-width: 0;
  border-bottom: 1px solid var(--platform-border);
}
.platform-page-copy { flex: 1 1 19rem; min-width: 0; }
.platform-page-copy h1 { font-family: var(--font-title); font-size: var(--platform-title-size); letter-spacing: -.02em; line-height: 1.15; margin: 0; overflow-wrap: anywhere; }
.platform-page-actions { display: flex; flex-wrap: wrap; align-items: center; gap: var(--space-8); min-width: 0; }
.platform-page-actions :deep(.btn) { min-height: var(--platform-control-default); text-decoration: none; }
@media (max-width: 600px) {
  .platform-page-header { padding: var(--space-8) 0 var(--space-16); gap: var(--space-12); }
}
</style>
