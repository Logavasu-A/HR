<script setup>
defineProps({
  title: { type: String, required: true },
  badge: { type: String, default: 'Enterprise Suite v2.0' },
})
</script>

<template>
  <main class="auth-layout">
    <div class="auth-brand">
      <div class="auth-brand-top">
        <div class="auth-logo-wrap">
          <img src="/hrms-logo-v3.svg" alt="HRMS" class="auth-logo-img" width="270" height="72" />
        </div>
        <span class="auth-env-badge">
          <span class="auth-env-dot"></span>
          {{ badge }}
        </span>
      </div>

      <div class="auth-aside">
        <slot name="aside">
          <h2>Next-generation Human Resource Management</h2>
          <p>Unified workforce management, automated payroll, real-time attendance, and executive control.</p>
        </slot>
      </div>

      <div class="auth-brand-footer">
        <div class="auth-features">
          <div class="auth-feature-item">
            <svg class="auth-feature-icon" viewBox="0 0 20 20" fill="currentColor">
              <path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd"/>
            </svg>
            <span>Role-Based Multi-Tenant Isolation</span>
          </div>
          <div class="auth-feature-item">
            <svg class="auth-feature-icon" viewBox="0 0 20 20" fill="currentColor">
              <path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd"/>
            </svg>
            <span>Live Attendance & One-Click Payroll Runs</span>
          </div>
          <div class="auth-feature-item">
            <svg class="auth-feature-icon" viewBox="0 0 20 20" fill="currentColor">
              <path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd"/>
            </svg>
            <span>Super Admin Control Tower & Analytics</span>
          </div>
        </div>
        <p class="auth-copyright">© Enterprise HRMS · High-Security Isolation Architecture</p>
      </div>
    </div>

    <section class="auth-content" aria-labelledby="auth-title">
      <div class="auth-card">
        <header class="auth-heading">
          <div class="auth-heading-badge">
            <span class="auth-heading-dot"></span>
            Authentication
          </div>
          <h1 id="auth-title">{{ title }}</h1>
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
  min-height: 100dvh;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.12fr);
  background: var(--page-bg);
}

.auth-brand {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: clamp(32px, 6vw, 84px);
  color: var(--card-bg);
  background: var(--ink);
  position: relative;
  overflow: hidden;
}

.auth-brand-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  z-index: 1;
}

.auth-logo-wrap {
  background: var(--card-bg);
  padding: 6px 14px;
  border-radius: 12px;
  box-shadow: none;
  display: inline-flex;
  align-items: center;
}

.auth-logo-img {
  display: block;
  object-fit: contain;
  height: 38px;
  width: auto;
}

.auth-env-badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: var(--ink-raised);
  border: 1px solid var(--ink-raised);
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.02em;
  color: var(--ink-text);
  backdrop-filter: blur(8px);
}

.auth-env-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--ink-text);
  box-shadow: none;
  animation: pulse-dot 2s infinite ease-in-out;
}

@keyframes pulse-dot {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.6; transform: scale(0.85); }
}

.auth-aside {
  margin: clamp(2.5rem, 8dvh, 5rem) 0;
  z-index: 1;
}

.auth-aside :deep(h2) {
  font-size: clamp(28px, 3.2vw, 44px);
  font-weight: 800;
  line-height: 1.14;
  letter-spacing: -0.035em;
  margin: 0 0 16px;
  color: var(--card-bg);
}

.auth-aside :deep(p) {
  font-size: clamp(14.5px, 1.2vw, 16.5px);
  line-height: 1.6;
  color: var(--ink-text);
  max-width: 44ch;
  margin: 0;
}

.auth-brand-footer {
  z-index: 1;
  padding-top: 24px;
  border-top: 1px solid var(--ink-raised);
}

.auth-features {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 20px;
}

.auth-feature-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13.5px;
  color: var(--ink-text);
  font-weight: 500;
}

.auth-feature-icon {
  width: 17px;
  height: 17px;
  color: var(--ink-text);
  flex-shrink: 0;
}

.auth-copyright {
  margin: 0;
  font-size: 12px;
  color: var(--ink-text);
}

/* Right Content */
.auth-content {
  display: grid;
  place-items: center;
  padding: clamp(24px, 5vw, 64px);
}

.auth-card {
  width: min(100%, 460px);
  padding: clamp(28px, 5vw, 44px);
  background: var(--card-bg);
  border: 1px solid var(--border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-card);
}

.auth-heading {
  margin-bottom: 24px;
}

.auth-heading-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--primary);
  background: var(--primary-soft);
  padding: 3px 9px;
  border-radius: 999px;
  margin-bottom: 12px;
}

.auth-heading-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--primary);
}

.auth-heading h1 {
  font-size: clamp(24px, 2.6vw, 29px);
  font-weight: 750;
  letter-spacing: -0.025em;
  color: var(--text);
  margin: 0;
}

.auth-footer {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid var(--line);
}

@media (max-width: 860px) {
  .auth-layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .auth-brand {
    padding: 24px 20px;
  }
  .auth-aside {
    margin: 24px 0 16px;
  }
  .auth-features {
    display: none;
  }
  .auth-content {
    padding: 32px 16px 48px;
  }
  .auth-card {
    padding: 24px 20px;
    box-shadow: var(--shadow-card);
  }
}
</style>
