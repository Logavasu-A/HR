<script setup>
import { provide } from 'vue'
import Layout from '@/shared/components/Layout.vue'
import { workspaceContext } from '@/shared/workspaceContext'

const props = defineProps({
  workspace: { type: Object, required: true },
  owner: { type: String, required: true },
  client: { type: Object, required: true },
  registryCompany: { type: Object, default: null },
})

provide(workspaceContext, {
  api: props.client.api,
  owner: props.owner,
  companyName: props.workspace.name,
  companyDetails: {
    ...props.workspace,
    contactEmail: props.registryCompany?.contact_email,
    registeredAt: props.registryCompany?.created_at,
  },
  path: (path) => `/platform/workspace${path}`,
  can: (permission) => !permission || permission.endsWith(':read') || props.workspace.status === 'ACTIVE',
  explicit: (permission) => permission === 'employee-bank:read' ||
    (permission === 'employee-bank:write' && props.workspace.status === 'ACTIVE'),
})
</script>

<template>
  <Layout>
    <template #before-content><slot /></template>
    <router-view :key="$route.path" />
  </Layout>
</template>
