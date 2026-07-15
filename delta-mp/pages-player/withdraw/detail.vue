<template>
  <AuditPlaceholder v-if="pageBlocked" />
  <view v-else class="detail-page" v-if="detail">
    <!-- 状态 -->
    <view class="status-header" :class="statusClass">
      <text class="status-text">{{ WITHDRAW_STATUS_TEXT[detail.status] || detail.status }}</text>
      <text v-if="detail.rejectReason" class="reject-reason">原因：{{ detail.rejectReason }}</text>
    </view>
    <!-- 金额 -->
    <view class="amount-section">
      <text class="amount">¥{{ Number(detail.amount).toFixed(2) }}</text>
    </view>
    <!-- 详情信息 -->
    <view class="info-card">
      <view class="info-row"><text class="label">提现单号</text><text class="value">{{ detail.withdrawNo || detail.id }}</text></view>
      <view class="info-row"><text class="label">提现方式</text><text class="value">{{ detail.accountType === 'ALIPAY' ? '支付宝' : detail.accountType === 'WECHAT' ? '微信' : '银行卡' }}</text></view>
      <view class="info-row"><text class="label">提现账号</text><text class="value">{{ detail.accountNo || '-' }}</text></view>
      <view v-if="detail.remark" class="info-row"><text class="label">备注</text><text class="value">{{ detail.remark }}</text></view>
      <view class="info-row"><text class="label">申请时间</text><text class="value">{{ detail.createdAt }}</text></view>
      <view v-if="detail.processedAt" class="info-row"><text class="label">处理时间</text><text class="value">{{ detail.processedAt }}</text></view>
    </view>
  </view>

</template>
<script setup>
import AuditPlaceholder from '@/components/AuditPlaceholder.vue'
import { useAuditPageGuard } from '@/composables/useAuditPageGuard'
const { pageBlocked } = useAuditPageGuard()

import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getWithdrawDetail } from '@/api/player'

const WITHDRAW_STATUS_TEXT = { PENDING: '审核中', APPROVED: '已通过', REJECTED: '已拒绝', COMPLETED: '已到账', CANCELLED: '已取消' }
const detail = ref(null)
const statusClass = computed(() => {
  const map = { PENDING: 'pending', APPROVED: 'approved', COMPLETED: 'completed', REJECTED: 'rejected' }
  return map[detail.value?.status] || ''
})

onLoad(async (opts) => {
  const res = await getWithdrawDetail(opts.id)
  detail.value = res.data
})
</script>
<style lang="scss" scoped>
.detail-page { background: #ffffff; min-height: 100vh; }
.status-header { padding: 40rpx; text-align: center; color: #fff;
  &.pending { background: #ff9900; } &.approved, &.completed { background: #07c160; } &.rejected { background: #ee0a24; }
  .status-text { font-size: 36rpx; font-weight: bold; display: block; }
  .reject-reason { font-size: 24rpx; opacity: 0.9; display: block; margin-top: 8rpx; }
}
.amount-section { text-align: center; padding: 48rpx; background: #f1f5f9; .amount { font-size: 56rpx; font-weight: bold; color: #1e293b; } }
.info-card { margin: 24rpx; background: #f1f5f9; border-radius: 12rpx; padding: 8rpx 24rpx; }
.info-row { display: flex; justify-content: space-between; padding: 20rpx 0; border-bottom: 1rpx solid #f1f5f9;
  .label { font-size: 26rpx; color: #94a3b8; } .value { font-size: 26rpx; color: #1e293b; }
}
</style>
