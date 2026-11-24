<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const props = defineProps({ fallback: { type: String, default: '' }, publicPage: Boolean })
const route = useRoute()
const router = useRouter()

const canGoBack = computed(() => {
  const current = route.fullPath
  const previous = router.options.history.state.back
  if (typeof previous !== 'string' || !previous.startsWith('/') || previous.startsWith('//') || previous === current) return false
  const destination = router.resolve(previous)
  if (props.publicPage) return ['/login', '/platform/login', '/accept-admin-invite'].includes(destination.path)
  if (['/login', '/platform/login', '/accept-admin-invite'].includes(destination.path)) return false
  return destination.path.startsWith('/platform') === route.path.startsWith('/platform')
})
const canUseFallback = computed(() => Boolean(props.fallback && props.fallback !== route.path))

function goBack() {
  if (canGoBack.value) router.back()
  else if (canUseFallback.value) router.replace(props.fallback)
}
</script>

<template>
  <button class="page-back" type="button" aria-label="Go back" title="Go back"
    :disabled="!canGoBack && !canUseFallback" @click="goBack">
    <span class="page-back-icon" aria-hidden="true">
      <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8"
        stroke-linecap="round" stroke-linejoin="round"><path d="m12 5-7 7 7 7M5 12h14" /></svg>
    </span>
  </button>
</template>

<style scoped>
.page-back {
  display: inline-grid;
  place-items: center;
  flex: none;
  inline-size: 2rem;
  block-size: 2rem;
  padding: 0;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  cursor: pointer;
}
.page-back-icon {
  display: grid;
  place-items: center;
  inline-size: 2rem;
  block-size: 2rem;
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--panel);
  color: var(--primary-dark);
}
.page-back:hover:not(:disabled) .page-back-icon { background: var(--primary-tint); border-color: var(--input-border); }
.page-back:focus-visible { outline: 2px solid var(--ink-accent); outline-offset: 2px; }
.page-back:disabled { opacity: 0.55; cursor: default; }
.page-back.auth-page-back { margin-block-end: var(--space-12); }
@media (width < 768px), (pointer: coarse) {
  .page-back { inline-size: var(--tap-target); block-size: var(--tap-target); }
}
</style>
