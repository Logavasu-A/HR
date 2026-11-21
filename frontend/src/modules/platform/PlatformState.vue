<script setup>
defineProps({ kind: { type: String, default: 'empty' }, title: String, message: String })
</script>

<template>
  <div class="card platform-state" :role="kind === 'error' ? 'alert' : 'status'" :data-state="kind">
    <span v-if="kind === 'loading'" class="state-spinner" aria-hidden="true"></span>
    <div><strong>{{ title }}</strong><p v-if="message">{{ message }}</p></div>
    <div v-if="$slots.default" class="state-actions"><slot /></div>
  </div>
</template>

<style scoped>
.platform-state { display: flex; align-items: center; flex-wrap: wrap; gap: var(--space-12); padding: var(--platform-card-padding); min-width: 0; }
.platform-state p { margin: 4px 0 0; color: var(--muted); overflow-wrap: anywhere; }
.platform-state[data-state=error] { border-inline-start: 4px solid var(--bad-line); background: var(--danger-tint); color: var(--bad-text); }
.state-actions { margin-inline-start: auto; display: flex; flex-wrap: wrap; gap: 8px; }
.state-spinner { width: 20px; height: 20px; border: 2px solid var(--line); border-top-color: var(--primary); border-radius: 50%; animation: rotate .75s linear infinite; flex: none; }
@keyframes rotate { to { transform: rotate(360deg); } }
@media (prefers-reduced-motion: reduce) { .state-spinner { animation: none; } }
@media (max-width: 520px) { .state-actions { flex-basis: 100%; margin-inline-start: 0; } }
</style>
