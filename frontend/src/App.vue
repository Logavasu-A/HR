<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import Layout from '@/shared/components/Layout.vue'
import PlatformLayout from '@/modules/platform/PlatformLayout.vue'

const route = useRoute()
const bare = computed(() => route.meta.bare === true)
const platformConsole = computed(() => ['/platform/control-tower', '/platform/companies', '/platform/access', '/platform/audit'].includes(route.path))
</script>

<template>
  <PlatformLayout v-if="platformConsole"><router-view /></PlatformLayout>
  <router-view v-else-if="bare" />
  <Layout v-else>
    <!-- key forces a fresh page when moving between two screens that share a component -->
    <router-view :key="route.path" />
  </Layout>
</template>
