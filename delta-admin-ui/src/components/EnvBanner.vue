<template>
  <div v-if="visible" class="env-banner" role="status">
    <span class="env-banner__badge">{{ STAGING_LABEL }}</span>
    <span class="env-banner__hint">{{ STAGING_HINT }}</span>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { isStagingEnv, STAGING_LABEL, STAGING_HINT } from '@/utils/env'

const visible = computed(() => isStagingEnv())
</script>

<style lang="scss" scoped>
.env-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding: 8px 16px;
  background: linear-gradient(90deg, #b45309 0%, #d97706 100%);
  color: #fffbeb;
  font-size: 13px;
  line-height: 1.4;
  box-shadow: 0 2px 8px rgba(180, 83, 9, 0.25);
}
.env-banner__badge {
  flex-shrink: 0;
  padding: 2px 10px;
  border-radius: 4px;
  font-weight: 700;
  font-size: 12px;
  letter-spacing: 0.04em;
  background: rgba(255, 255, 255, 0.22);
  border: 1px solid rgba(255, 255, 255, 0.35);
}
.env-banner__hint {
  opacity: 0.95;
}
</style>
