<template>
  <view class="star-rating" :class="{ readonly }">
    <text
      v-for="i in 5"
      :key="i"
      class="star"
      :class="{ active: i <= currentValue, half: i - 0.5 === currentValue }"
      @click="!readonly && $emit('update:modelValue', i)"
    >★</text>
    <text v-if="showText" class="rating-text">{{ currentValue.toFixed(1) }}</text>
  </view>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: { type: Number, default: 0 },
  readonly: { type: Boolean, default: false },
  showText: { type: Boolean, default: false },
  size: { type: Number, default: 36 }
})

defineEmits(['update:modelValue'])

const currentValue = computed(() => props.modelValue || 0)
</script>

<style lang="scss" scoped>
.star-rating {
  display: flex;
  align-items: center;
  gap: 4rpx;

  .star {
    font-size: 36rpx;
    color: #ddd;
    transition: color 0.15s;

    &.active { color: #ff9900; }
  }

  &:not(.readonly) .star { cursor: pointer; }

  .rating-text {
    margin-left: 8rpx;
    font-size: 26rpx;
    color: #ff9900;
    font-weight: 500;
  }
}
</style>
