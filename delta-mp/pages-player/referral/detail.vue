<template>
  <AuditPlaceholder v-if="pageBlocked" />
  <view v-else class="detail-page">
    <view class="header">
      <image class="avatar" :src="profile.avatar || '/static/images/default-avatar.png'" mode="aspectFill" />
      <view>
        <text class="name">{{ profile.nickname || '未命名客户' }}</text>
        <text class="meta">{{ profile.phone || '无手机号' }} · 比例 {{ profile.ratePercent }}%</text>
      </view>
    </view>
    <scroll-view scroll-y class="list">
      <view v-for="item in fees" :key="item.orderId" class="record-item">
        <view class="left">
          <text class="desc">订单 {{ item.orderNo || item.orderId }}</text>
          <text class="time">{{ item.time || '-' }}</text>
          <text class="time">{{ item.settled ? '已入账' : '待入账' }}</text>
        </view>
        <text class="amount">¥{{ Number(item.amount || 0).toFixed(2) }}</text>
      </view>
      <view v-if="loading" class="loading-tip">加载中...</view>
      <EmptyState v-if="!loading && fees.length === 0" text="暂无介绍费" image="/static/icons/暂无纪录.svg" />
    </scroll-view>
  </view>
</template>

<script setup>
import AuditPlaceholder from '@/components/AuditPlaceholder.vue'
import { useAuditPageGuard } from '@/composables/useAuditPageGuard'
const { pageBlocked } = useAuditPageGuard()

import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import EmptyState from '@/components/EmptyState.vue'
import { getReferralFees } from '@/api/player'

const profile = ref({})
const fees = ref([])
const loading = ref(false)

onLoad((query) => { loadData(query.userId) })

/** 剩余介绍费在确认后就能看到，状态区分待入账和已入账。 */
async function loadData(userId) {
  loading.value = true
  try {
    const res = await getReferralFees(userId)
    profile.value = res.data || {}
    fees.value = res.data?.fees || []
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss" scoped>
.detail-page { background: #ffffff; min-height: 100vh; }
.header {
  display: flex; align-items: center; padding: 32rpx; background: #f1f5f9;
  .avatar { width: 88rpx; height: 88rpx; border-radius: 50%; margin-right: 20rpx; }
  .name { font-size: 30rpx; color: #1e293b; font-weight: bold; display: block; }
  .meta { font-size: 22rpx; color: #64748b; display: block; margin-top: 6rpx; }
}
.list { height: calc(100vh - 152rpx); }
.record-item {
  display: flex; justify-content: space-between; align-items: center; padding: 24rpx; border-bottom: 1rpx solid #f1f5f9;
  .left { flex: 1; .desc { font-size: 28rpx; color: #1e293b; display: block; } .time { font-size: 22rpx; color: #94a3b8; display: block; margin-top: 4rpx; } }
  .amount { font-size: 30rpx; color: #07c160; font-weight: bold; }
}
.loading-tip { text-align: center; padding: 32rpx; font-size: 24rpx; color: #94a3b8; }
</style>
