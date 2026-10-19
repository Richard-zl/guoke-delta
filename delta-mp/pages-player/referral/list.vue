<template>
  <AuditPlaceholder v-if="pageBlocked" />
  <view v-else class="referral-page">
    <scroll-view scroll-y class="list">
      <view v-for="item in customers" :key="item.userId" class="customer" @click="openDetail(item.userId)">
        <image class="avatar" :src="item.avatar || '/static/images/default-avatar.png'" mode="aspectFill" />
        <view class="info">
          <text class="name">{{ item.nickname || '未命名客户' }}</text>
          <text class="meta">{{ item.phone || '无手机号' }} · 比例 {{ item.ratePercent }}%</text>
          <text class="meta">绑定于 {{ item.boundAt || '-' }}</text>
        </view>
        <text class="arrow">›</text>
      </view>
      <view v-if="loading" class="loading-tip">加载中...</view>
      <EmptyState v-if="!loading && customers.length === 0" text="暂无转介绍客户" image="/static/icons/暂无纪录.svg" />
    </scroll-view>
  </view>
</template>

<script setup>
import AuditPlaceholder from '@/components/AuditPlaceholder.vue'
import { useAuditPageGuard } from '@/composables/useAuditPageGuard'
const { pageBlocked } = useAuditPageGuard()

import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import EmptyState from '@/components/EmptyState.vue'
import { getReferralCustomers } from '@/api/player'

const customers = ref([])
const loading = ref(false)

onShow(() => { loadData() })

/** 只展示当前有效绑定，不能在这里改比例或解绑。 */
async function loadData() {
  loading.value = true
  try {
    const res = await getReferralCustomers()
    customers.value = res.data || []
  } finally {
    loading.value = false
  }
}

function openDetail(userId) {
  uni.navigateTo({ url: `/pages-player/referral/detail?userId=${userId}` })
}
</script>

<style lang="scss" scoped>
.referral-page { background: #ffffff; min-height: 100vh; }
.list { height: 100vh; }
.customer {
  display: flex; align-items: center; padding: 24rpx; background: #f8fafc; margin: 16rpx 24rpx; border-radius: 12rpx;
  .avatar { width: 80rpx; height: 80rpx; border-radius: 50%; margin-right: 20rpx; }
  .info { flex: 1; .name { font-size: 28rpx; color: #1e293b; display: block; } .meta { font-size: 22rpx; color: #94a3b8; display: block; margin-top: 4rpx; } }
  .arrow { color: #94a3b8; font-size: 36rpx; }
}
.loading-tip { text-align: center; padding: 32rpx; font-size: 24rpx; color: #94a3b8; }
</style>
