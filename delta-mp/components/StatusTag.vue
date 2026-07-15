<template>
  <view class="status-tag" :style="{ color: statusColor, backgroundColor: statusBg }">
    {{ statusText }}
  </view>
</template>

<script setup>
import { computed } from 'vue'
import { ORDER_STATUS_TEXT, ORDER_STATUS_COLOR } from '@/utils/constants'

const props = defineProps({
  status: { type: String, default: '' },
  textMap: { type: Object, default: () => ORDER_STATUS_TEXT },
  colorMap: { type: Object, default: () => ORDER_STATUS_COLOR }
})

const statusText = computed(() => props.textMap[props.status] || props.status)
const statusColor = computed(() => props.colorMap[props.status] || '#999')
const statusBg = computed(() => {
  const c = props.colorMap[props.status] || '#999'
  return c + '1A' // 10%透明度
})
</script>

<style lang="scss" scoped>
.status-tag {
  display: inline-block;
  font-size: 22rpx;
  padding: 4rpx 16rpx;
  border-radius: 999rpx;
  white-space: nowrap;
}
</style>
