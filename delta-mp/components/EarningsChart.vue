<template>
  <view class="earnings-chart">
    <view class="chart-header" v-if="title">
      <text class="chart-title">{{ title }}</text>
    </view>
    <view class="chart-body">
      <view class="bar-group" v-for="(item, index) in chartData" :key="index">
        <view class="bar-wrapper">
          <view class="bar" :style="{ height: getBarHeight(item.value) + 'rpx', background: barColor }"></view>
        </view>
        <text class="bar-label">{{ item.label }}</text>
        <text class="bar-value">{{ formatAmount(item.value) }}</text>
      </view>
    </view>
    <view v-if="!chartData || chartData.length === 0" class="empty-tip">
      <text>暂无数据</text>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  /** 图表标题 */
  title: { type: String, default: '' },
  /**
   * 图表数据
   * 格式: [{ label: '1月', value: 1200 }, ...]
   */
  data: { type: Array, default: () => [] },
  /** 柱状图颜色 */
  barColor: { type: String, default: '#6366f1' },
  /** 最大柱高(rpx) */
  maxBarHeight: { type: Number, default: 200 }
})

const chartData = computed(() => props.data || [])

const maxValue = computed(() => {
  if (!chartData.value.length) return 0
  return Math.max(...chartData.value.map(d => d.value), 1)
})

function getBarHeight(value) {
  if (!maxValue.value) return 0
  return Math.round((value / maxValue.value) * props.maxBarHeight)
}

function formatAmount(val) {
  if (val >= 10000) return (val / 10000).toFixed(1) + 'w'
  if (val >= 1000) return (val / 1000).toFixed(1) + 'k'
  return val.toFixed(0)
}
</script>

<style lang="scss" scoped>
.earnings-chart {
  background: #f1f5f9;
  border: 1rpx solid #e2e8f0;
  border-radius: 12rpx;
  padding: 24rpx;
}

.chart-header {
  margin-bottom: 24rpx;
  .chart-title {
    font-size: 28rpx;
    font-weight: bold;
    color: #6366f1;
  }
}

.chart-body {
  display: flex;
  align-items: flex-end;
  justify-content: space-around;
  min-height: 300rpx;
  padding-top: 40rpx;
}

.bar-group {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex: 1;
}

.bar-wrapper {
  display: flex;
  align-items: flex-end;
  height: 200rpx;
}

.bar {
  width: 40rpx;
  min-height: 4rpx;
  border-radius: 8rpx 8rpx 0 0;
  transition: height 0.3s ease;
}

.bar-value {
  font-size: 20rpx;
  color: #64748b;
  margin-top: 8rpx;
}

.bar-label {
  font-size: 22rpx;
  color: #94a3b8;
  margin-top: 8rpx;
}

.empty-tip {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 200rpx;
  font-size: 26rpx;
  color: #94a3b8;
}
</style>
