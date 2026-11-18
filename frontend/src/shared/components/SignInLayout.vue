<script setup>
defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' },
})
</script>

<template>
  <main class="auth-layout">
    <div class="auth-brand">
      <img src="/logo.jpg" alt="HRMS" width="120" height="42" />
      <div v-if="$slots.aside" class="auth-aside">
        <slot name="aside" />
      </div>
    </div>
    <section class="auth-content" aria-labelledby="auth-title">
      <div class="card auth-card">
        <header class="auth-heading">
          <h1 id="auth-title">{{ title }}</h1>
          <p v-if="description">{{ description }}</p>
        </header>
        <slot />
        <footer v-if="$slots.footer" class="auth-footer">
          <slot name="footer" />
        </footer>
      </div>
    </section>
  </main>
</template>

<style scoped>
.auth-layout {
  min-height: 100%;
  display: grid;
  grid-template-columns: minmax(0, 0.92fr) minmax(0, 1.08fr);
  background: #f8f9fd;
}
.auth-brand {
  display: flex;
  flex-direction: column;
  gap: clamp(56px, 16vh, 180px);
  padding: clamp(32px, 7vw, 104px);
  color: #fff;
  background-color: #141432;
  background-image:
    radial-gradient(ellipse at 0% 100%, #2d2467 0, transparent 65%),
    radial-gradient(ellipse at 100% 5%, #243c69 0, transparent 62%);
}
.auth-brand img {
  object-fit: contain;
  padding: 7px 10px;
  border-radius: 8px;
  background: #fff;
}
.auth-aside :deep(h2) {
  max-width: 15ch;
  margin: 0 0 18px;
  color: inherit;
  font-size: clamp(30px, 4vw, 58px);
  line-height: 1.06;
  letter-spacing: -0.045em;
}
.auth-aside :deep(p) {
  max-width: 35ch;
  margin: 0;
  color: #d8dcf3;
  font-size: clamp(15px, 1.4vw, 18px);
}
.auth-content {
  display: grid;
  place-items: center;
  padding: clamp(20px, 5vw, 72px);
}
.auth-card {
  width: min(100%, 440px);
  padding: clamp(22px, 5vw, 40px);
  border-radius: 20px;
  box-shadow: 0 24px 64px rgba(31, 32, 72, 0.08);
}
.auth-heading { margin-bottom: 24px; }
.auth-heading h1 { font-size: clamp(25px, 3vw, 33px); line-height: 1.15; }
.auth-heading p { margin: 10px 0 0; color: var(--muted); }
.auth-footer { margin-top: 22px; padding-top: 18px; border-top: 1px solid var(--line); }
.auth-footer:empty { display: none; }
.auth-footer :deep(a) { color: var(--primary-dark); font-weight: 600; text-underline-offset: 3px; }

@media (max-width: 760px) {
  .auth-layout { grid-template-columns: minmax(0, 1fr); grid-template-rows: auto 1fr; }
  .auth-brand { padding: 18px 20px; }
  .auth-brand img { width: 104px; height: 36px; }
  .auth-aside { display: none; }
  .auth-content { align-items: start; padding: 28px clamp(12px, 4vw, 24px); }
}
</style>
