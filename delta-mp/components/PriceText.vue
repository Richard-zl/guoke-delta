<template>
  <text class="price-text" :style="{ fontSize: size + 'rpx', color }">
    <text class="symbol">¥</text>{{ formatValue }}
  </text>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  value: { type: [Number, String], default: 0 },
  size: { type: Number, default: 32 },
  color: { type: String, default: '#ff6b2b' }
})

const formatValue = computed(() => Number(props.value || 0).toFixed(2))
</script>

<style lang="scss" scoped>
.price-text {
  font-weight: bold;
  .symbol { font-size: 0.7em; }
}
</style>
