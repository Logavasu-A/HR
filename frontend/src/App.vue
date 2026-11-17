<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import Layout from '@/shared/components/Layout.vue'

const route = useRoute()
const bare = computed(() => route.meta.bare === true)
</script>

<template>
  <router-view v-if="bare" />
  <Layout v-else>
    <!-- key forces a fresh page when moving between two screens that share a component -->
    <router-view :key="route.path" />
  </Layout>
</template>
