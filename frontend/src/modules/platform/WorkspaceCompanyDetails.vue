<script setup>
import { useWorkspace } from '@/shared/workspaceContext'
import { pretty } from '@/shared/utils'
import PlatformPageHeader from './PlatformPageHeader.vue'

const { companyDetails: company, path } = useWorkspace()
</script>

<template>
  <section>
    <PlatformPageHeader :title="company.name">
      <router-link class="btn" :to="path('/dashboard')">HR dashboard</router-link>
    </PlatformPageHeader>
    <dl class="card company-facts">
      <div><dt>Company code</dt><dd>{{ company.code }}</dd></div>
      <div><dt>Status</dt><dd><span class="badge" :class="company.status.toLowerCase()">{{ pretty(company.status) }}</span></dd></div>
      <div><dt>Contact email</dt><dd>{{ company.contactEmail || 'Unavailable' }}</dd></div>
      <div><dt>Time zone</dt><dd>{{ company.timezone }}</dd></div>
      <div><dt>Currency</dt><dd>{{ company.currency }}</dd></div>
      <div><dt>Registered</dt><dd>{{ company.registeredAt ? new Date(company.registeredAt).toLocaleDateString() : 'Unavailable' }}</dd></div>
    </dl>
    <nav class="card company-shortcuts" aria-label="Selected company HR screens">
      <router-link class="btn" :to="path('/employees')">Employees</router-link>
      <router-link class="btn" :to="path('/attendance')">Attendance</router-link>
      <router-link class="btn" :to="path('/leave-applications')">Leave applications</router-link>
      <router-link class="btn" :to="path('/payroll-runs')">Payroll</router-link>
    </nav>
  </section>
</template>

<style scoped>
.company-facts { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 200px), 1fr)); gap: var(--space-16); padding: var(--platform-card-padding); }
.company-facts dt { color: var(--muted); margin-bottom: 5px; }
.company-facts dd { margin: 0; font-weight: 600; overflow-wrap: anywhere; }
.company-shortcuts { display: flex; flex-wrap: wrap; gap: var(--space-8); padding: var(--platform-card-padding); }
</style>
