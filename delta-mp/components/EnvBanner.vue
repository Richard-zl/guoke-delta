<template>
  <view v-if="visible" class="env-banner">
    <text class="env-banner__badge">{{ STAGING_LABEL }}</text>
    <text class="env-banner__hint">{{ STAGING_HINT }}</text>
  </view>
</template>

<script setup>
import { computed } from 'vue'
import { isStagingEnv, STAGING_LABEL, STAGING_HINT } from '@/utils/env'

const visible = computed(() => isStagingEnv())
</script>

<style lang="scss" scoped>
.env-banner {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  z-index: 99999;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 8rpx;
  padding: 8rpx 24rpx;
  padding-top: calc(8rpx + env(safe-area-inset-top));
  background: linear-gradient(90deg, #b45309, #d97706);
  box-shadow: 0 4rpx 16rpx rgba(180, 83, 9, 0.35);
}
.env-banner__badge {
  padding: 4rpx 16rpx;
  border-radius: 8rpx;
  font-size: 22rpx;
  font-weight: 700;
  color: #fffbeb;
  background: rgba(255, 255, 255, 0.2);
  border: 1rpx solid rgba(255, 255, 255, 0.35);
}
.env-banner__hint {
  font-size: 22rpx;
  color: rgba(255, 251, 235, 0.95);
}
</style>
