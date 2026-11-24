<script setup>
import { onMounted, onUnmounted, ref, useId } from 'vue'

defineProps({
  name: { type: String, required: true },
  role: { type: String, default: '' },
  initials: { type: String, default: '' },
  accountLink: { type: String, default: '' },
})
const emit = defineEmits(['sign-out'])
const account = ref(null)
const trigger = ref(null)
const open = ref(false)
const menuId = useId()

function close(restoreFocus = false) {
  open.value = false
  if (restoreFocus) trigger.value?.focus({ preventScroll: true })
}

function closeFromOutside(target) {
  if (open.value && !account.value?.contains(target)) close()
}

function onPointerDown(event) {
  closeFromOutside(event.target)
}

function requestSignOut() {
  close(true)
  emit('sign-out')
}

onMounted(() => document.addEventListener('pointerdown', onPointerDown))
onUnmounted(() => document.removeEventListener('pointerdown', onPointerDown))
</script>

<template>
  <div ref="account" class="account-menu" :class="{ 'account-with-avatar': initials }"
    @focusout="closeFromOutside($event.relatedTarget)"
    @keydown.esc.stop="open && close(true)">
    <button ref="trigger" class="account-trigger" type="button" :aria-expanded="open"
      :aria-label="`Account menu for ${name}`"
      :aria-controls="menuId" @click="open = !open">
      <span v-if="initials" class="account-avatar" aria-hidden="true">{{ initials }}</span>
      <span class="account-text">
        <strong :title="name">{{ name }}</strong>
        <span v-if="role">{{ role }}</span>
      </span>
      <svg aria-hidden="true" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor"
        stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="m6 9 6 6 6-6" /></svg>
    </button>
    <div v-show="open" :id="menuId" class="account-options">
      <div v-if="initials" class="account-mobile-name">
        <strong>{{ name }}</strong>
        <span v-if="role">{{ role }}</span>
      </div>
      <router-link v-if="accountLink" class="account-action" :to="accountLink" @click="close()">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8"
          stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" /><circle cx="12" cy="7" r="4" /></svg>
        Account &amp; security
      </router-link>
      <button type="button" class="account-action" @click="requestSignOut">
        <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.8"
          stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M10 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5m5 4 5 5-5 5m5-5H9" /></svg>
        Sign out
      </button>
    </div>
  </div>
</template>

<style scoped>
.account-menu { position: relative; min-inline-size: 0; max-inline-size: min(46vw, 16rem); margin-inline-start: auto; }
.account-trigger {
  display: flex;
  align-items: center;
  gap: var(--space-8);
  min-block-size: var(--toolbar-control-height, var(--tap-target));
  max-inline-size: 100%;
  padding: var(--space-4) var(--space-8);
  border: 1px solid var(--toolbar-border);
  border-radius: var(--radius-sm);
  background: var(--ink-raised);
  color: var(--card-bg);
  font: inherit;
  text-align: start;
  cursor: pointer;
}
.account-trigger:is(:hover, [aria-expanded=true]) { background: var(--primary); border-color: var(--ink-text); }
.account-trigger:focus-visible { outline: 2px solid var(--ink-accent); outline-offset: 2px; }
.account-trigger svg { flex: none; color: var(--ink-text); }
.account-trigger[aria-expanded=true] svg { transform: rotate(180deg); }
.account-avatar {
  display: grid;
  place-items: center;
  flex: none;
  inline-size: 1.75rem;
  block-size: 1.75rem;
  border-radius: var(--radius-sm);
  background: var(--primary);
  font-size: .8125rem;
  font-weight: 700;
}
.account-text { display: grid; min-inline-size: 0; line-height: 1.25; }
.account-text > * { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.account-text > span { color: var(--ink-text); font-size: var(--platform-caption-size); }
.account-options {
  position: absolute;
  inset-block-start: calc(100% + var(--space-8));
  inset-inline-end: 0;
  inline-size: 10rem;
  padding: var(--space-4);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  background: var(--card-bg);
  box-shadow: var(--shadow-menu);
}
.account-with-avatar .account-options { inline-size: 13rem; }
.account-action {
  display: flex;
  align-items: center;
  gap: var(--space-8);
  inline-size: 100%;
  min-block-size: var(--tap-target);
  padding: var(--space-8) var(--space-12);
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text);
  text-decoration: none;
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}
.account-action svg { flex: none; }
.account-action:hover { background: var(--page-bg); }
.account-action:focus-visible { outline-offset: -2px; }
.account-mobile-name { display: none; }
@media (width < 768px) {
  .account-text > span { display: none; }
  .account-with-avatar .account-trigger { justify-content: center; min-inline-size: var(--tap-target); padding: 0; }
  .account-with-avatar .account-trigger :is(.account-text, svg) { display: none; }
  .account-mobile-name {
    display: grid;
    gap: var(--space-4);
    padding: var(--space-8) var(--space-12);
    border-block-end: 1px solid var(--border);
    overflow-wrap: anywhere;
  }
  .account-mobile-name > span { color: var(--muted); font-size: var(--platform-caption-size); }
}
</style>
