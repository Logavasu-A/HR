<script setup>
import AppDialog from './AppDialog.vue'

defineProps({
  platform: Boolean,
  busy: Boolean,
  error: { type: String, default: '' },
})
defineEmits(['dismiss', 'confirm'])
</script>

<template>
  <AppDialog :title="platform ? 'Sign out of the platform?' : 'Sign out of your account?'" @dismiss="$emit('dismiss')">
    <p class="sign-out-message">
      {{ platform ? 'You’ll need to sign in again to access the platform console.'
        : 'You’ll need to sign in again to access your company workspace.' }}
    </p>
    <p v-if="error" class="alert" role="alert">{{ error }}</p>
    <template #footer>
      <button type="button" class="btn" :disabled="busy" @click="$emit('dismiss')">Stay signed in</button>
      <button type="button" class="btn primary" :disabled="busy" @click="$emit('confirm')">{{ busy ? 'Signing out…' : 'Sign out' }}</button>
    </template>
  </AppDialog>
</template>

<style scoped>
:global(.modal:has(.sign-out-message)) { inline-size: min(28rem, calc(100% - 2 * var(--modal-gutter))); }
.sign-out-message { margin: 0; color: var(--muted); }
</style>
