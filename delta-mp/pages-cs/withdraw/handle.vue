<template>
  <AuditPlaceholder v-if="pageBlocked" />

  <view v-else class="handle-page" v-if="detail">
    <view class="status-header" :class="statusClass">
      <text class="status-text">{{ STATUS_TEXT[detail.status] || detail.status }}</text>
    </view>
    <view class="amount-section">
      <text class="amount">¥{{ Number(detail.amount).toFixed(2) }}</text>
    </view>
    <view class="card">
      <view class="info-row"><text class="label">接单员</text><text class="value">{{ detail.playerNickname || detail.playerId }}</text></view>
      <view class="info-row"><text class="label">提现方式</text><text class="value">{{ detail.accountType === 'ALIPAY' ? '支付宝' : detail.accountType === 'WECHAT' ? '微信' : '银行卡' }}</text></view>
      <view class="info-row"><text class="label">提现账号</text><text class="value">{{ detail.accountNo || '-' }}</text></view>
      <view v-if="detail.realName" class="info-row"><text class="label">真实姓名</text><text class="value">{{ detail.realName }}</text></view>
      <view class="info-row"><text class="label">申请时间</text><text class="value">{{ detail.createdAt }}</text></view>
      <view v-if="detail.remark" class="info-row"><text class="label">备注</text><text class="value">{{ detail.remark }}</text></view>
    </view>
    <!-- 审核操作 -->
    <view v-if="detail.status==='PENDING'" class="card">
      <text class="card-title">审核操作</text>
      <view class="form-item">
        <text class="label">拒绝原因（拒绝时必填）</text>
        <textarea v-model="rejectReason" placeholder="请输入拒绝原因" class="textarea" />
      </view>
      <view class="btn-group">
        <view class="btn-approve" @click="doApprove">通过</view>
        <view class="btn-reject" @click="doReject">拒绝</view>
      </view>
    </view>
    <view v-if="detail.rejectReason" class="card">
      <text class="card-title">拒绝原因</text>
      <text class="reject-text">{{ detail.rejectReason }}</text>
    </view>
  </view>

</template>
<script setup>
import AuditPlaceholder from '@/components/AuditPlaceholder.vue'
import { useAuditPageGuard } from '@/composables/useAuditPageGuard'
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getCsWithdrawDetail, approveCsWithdraw, rejectCsWithdraw } from '@/pages-cs/api/cs'

const { pageBlocked } = useAuditPageGuard()
const STATUS_TEXT = { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已拒绝', COMPLETED: '已到账' }
const detail = ref(null)
const withdrawId = ref('')
const rejectReason = ref('')
const statusClass = computed(() => {
  const map = { PENDING: 'pending', APPROVED: 'approved', COMPLETED: 'completed', REJECTED: 'rejected' }
  return map[detail.value?.status] || ''
})

onLoad(async (opts) => {
  withdrawId.value = opts.id
  const res = await getCsWithdrawDetail(opts.id)
  detail.value = res.data
})

async function doApprove() {
  uni.showModal({ title: '确认', content: '确定通过该提现申请？', success: async (r) => {
    if (r.confirm) { try { await approveCsWithdraw(withdrawId.value); uni.showToast({ title: '已通过' }); setTimeout(() => uni.navigateBack(), 1500) } catch (e) {} }
  }})
}
async function doReject() {
  if (!rejectReason.value) return uni.showToast({ title: '请输入拒绝原因', icon: 'none' })
  uni.showModal({ title: '确认', content: '确定拒绝该提现申请？', success: async (r) => {
    if (r.confirm) { try { await rejectCsWithdraw(withdrawId.value, { reason: rejectReason.value }); uni.showToast({ title: '已拒绝' }); setTimeout(() => uni.navigateBack(), 1500) } catch (e) {} }
  }})
}
</script>
<style lang="scss" scoped>
.handle-page { background: #ffffff; min-height: 100vh; }
.status-header { padding: 40rpx; text-align: center; color: #fff;
  &.pending { background: #ff9900; } &.approved, &.completed { background: #07c160; } &.rejected { background: #ee0a24; }
  .status-text { font-size: 36rpx; font-weight: bold; }
}
.amount-section { text-align: center; padding: 48rpx; background: rgba(0,0,0,0.04); .amount { font-size: 56rpx; font-weight: bold; color: rgba(0,0,0,0.85); } }
.card { margin: 20rpx 24rpx; padding: 24rpx; background: rgba(0,0,0,0.04); border-radius: 12rpx; }
.card-title { font-size: 28rpx; font-weight: bold; display: block; margin-bottom: 16rpx; color: rgba(0,0,0,0.85); }
.info-row { display: flex; justify-content: space-between; padding: 12rpx 0; border-bottom: 1rpx solid rgba(0,0,0,0.04); .label { font-size: 26rpx; color: rgba(0,0,0,0.3); } .value { font-size: 26rpx; color: rgba(0,0,0,0.85); } }
.form-item { margin-bottom: 24rpx; .label { font-size: 26rpx; color: rgba(0,0,0,0.85); display: block; margin-bottom: 12rpx; } }
.textarea { background: rgba(0,0,0,0.05); padding: 16rpx; border-radius: 8rpx; font-size: 26rpx; width: 100%; height: 160rpx; color: rgba(0,0,0,0.85); }
.btn-group { display: flex; gap: 20rpx; }
.btn-approve { flex: 1; text-align: center; padding: 20rpx; background: #07c160; color: #fff; border-radius: 999rpx; font-size: 28rpx; }
.btn-reject { flex: 1; text-align: center; padding: 20rpx; background: #ee0a24; color: #fff; border-radius: 999rpx; font-size: 28rpx; }
.reject-text { font-size: 26rpx; color: #ee0a24; }
</style>
