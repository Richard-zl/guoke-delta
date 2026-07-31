<template>
  <AuditPlaceholder v-if="pageBlocked" />
  <view v-else class="earnings-page">
    <!-- 收益概览 -->
    <view class="summary-card">
      <text class="card-label">累计收益（元）</text>
      <text class="total-amount">¥{{ summary.totalIncome || '0.00' }}</text>
      <text class="pending-label">待入账 ¥{{ formatMoney(summary.pendingBalance) }}</text>
      <view class="row">
        <view class="col"><text class="num">¥{{ summary.monthIncome || '0.00' }}</text><text class="desc">本月收益</text></view>
        <view class="divider" />
        <view class="col"><text class="num">¥{{ summary.weekIncome || '0.00' }}</text><text class="desc">本周收益</text></view>
        <view class="divider" />
        <view class="col"><text class="num">¥{{ summary.todayIncome || '0.00' }}</text><text class="desc">今日收益</text></view>
      </view>
    </view>
    <!-- 统计卡片 -->
    <view class="stat-grid">
      <view class="stat-item">
        <text class="stat-num">{{ summary.totalOrders || 0 }}</text>
        <text class="stat-label">累计订单</text>
      </view>
      <view class="stat-item">
        <text class="stat-num">{{ summary.monthOrders || 0 }}</text>
        <text class="stat-label">本月订单</text>
      </view>
      <view class="stat-item">
        <text class="stat-num">¥{{ summary.avgOrderAmount || '0.00' }}</text>
        <text class="stat-label">单均收益</text>
      </view>
      <view class="stat-item">
        <text class="stat-num">{{ summary.completionRate || '0' }}%</text>
        <text class="stat-label">完成率</text>
      </view>
    </view>
    <!-- 操作入口 -->
    <view class="action-list">
      <view class="action-row" @click="goDetail">
        <text>📋 收益明细</text><text class="arrow">›</text>
      </view>
      <view class="action-row" @click="goWithdraw">
        <text>💳 去提现</text><text class="arrow">›</text>
      </view>
    </view>
  </view>

</template>
<script setup>
import AuditPlaceholder from '@/components/AuditPlaceholder.vue'
import { useAuditPageGuard } from '@/composables/useAuditPageGuard'
const { pageBlocked } = useAuditPageGuard()

import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getEarningsSummary } from '@/api/player'

const summary = ref({})

onShow(async () => {
  try { const res = await getEarningsSummary(); summary.value = res.data || {} } catch (e) {}
})

function formatMoney(val) {
  return Number(val || 0).toFixed(2)
}

function goDetail() { uni.navigateTo({ url: '/pages-player/earnings/detail' }) }
function goWithdraw() { uni.navigateTo({ url: '/pages-player/withdraw/index' }) }
</script>
<style lang="scss" scoped>
.earnings-page { background: #ffffff; min-height: 100vh; }
.summary-card { background: linear-gradient(135deg, #ff4544, #e63939, #e63939); color: #ffffff; font-weight:bold; margin: 24rpx; border-radius: 16rpx; padding: 40rpx 32rpx;
  .card-label { font-size: 26rpx; opacity: 0.85; display: block; }
  .total-amount { font-size: 56rpx; font-weight: bold; display: block; margin: 16rpx 0 8rpx; }
  .pending-label { font-size: 24rpx; opacity: 0.85; display: block; margin-bottom: 24rpx; }
  .row { display: flex; align-items: center; }
  .col { flex: 1; text-align: center; .num { font-size: 30rpx; font-weight: bold; display: block; } .desc { font-size: 22rpx; opacity: 0.8; display: block; margin-top: 4rpx; } }
  .divider { width: 1rpx; height: 48rpx; background: rgba(255,255,255,0.3); }
}
.stat-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 20rpx; padding: 0 24rpx 24rpx;
  .stat-item { background: #f1f5f9; padding: 24rpx; border-radius: 12rpx; text-align: center;
    .stat-num { font-size: 32rpx; font-weight: bold; color: #1e293b; display: block; }
    .stat-label { font-size: 24rpx; color: #94a3b8; display: block; margin-top: 8rpx; }
  }
}
.action-list { margin: 0 24rpx; background: #f1f5f9; border-radius: 12rpx; overflow: hidden; }
.action-row { display: flex; justify-content: space-between; align-items: center; padding: 28rpx 24rpx; border-bottom: 1rpx solid #f1f5f9; font-size: 28rpx; .arrow { color: #cbd5e1; } }
</style>
