<template>
  <router-view />
</template>

<script setup>
import { useSiteStore } from '@/stores/site'
const siteStore = useSiteStore()
siteStore.fetchSiteConfig()
</script>
