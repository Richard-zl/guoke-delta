<template>
  <AuditPlaceholder v-if="pageBlocked" />

  <view v-else class="withdraw-list">
    <scroll-view scroll-x class="filter-bar">
      <view class="filter-item" :class="{active: !statusFilter}" @click="filterStatus('')">全部</view>
      <view class="filter-item" :class="{active: statusFilter==='PENDING'}" @click="filterStatus('PENDING')">待审核</view>
      <view class="filter-item" :class="{active: statusFilter==='APPROVED'}" @click="filterStatus('APPROVED')">已通过</view>
      <view class="filter-item" :class="{active: statusFilter==='REJECTED'}" @click="filterStatus('REJECTED')">已拒绝</view>
      <view class="filter-item" :class="{active: statusFilter==='COMPLETED'}" @click="filterStatus('COMPLETED')">已到账</view>
    </scroll-view>
    <scroll-view scroll-y class="list" @scrolltolower="loadMore">
      <view v-for="w in records" :key="w.id" class="withdraw-card" @click="goHandle(w.id)">
        <view class="card-top">
          <text class="amount">¥{{ Number(w.amount).toFixed(2) }}</text>
          <StatusTag :status="w.status" :text-map="STATUS_TEXT" :color-map="STATUS_COLOR" />
        </view>
        <view class="card-body">
          <text class="player">接单员：{{ w.playerNickname || w.playerId }}</text>
          <text class="account">{{ w.accountType === 'ALIPAY' ? '支付宝' : w.accountType === 'WECHAT' ? '微信' : '银行卡' }} {{ w.accountNo || '' }}</text>
        </view>
        <text class="time">{{ w.createdAt }}</text>
        <view v-if="w.status==='PENDING'" class="card-actions">
          <view class="btn-approve" @click.stop="doApprove(w.id)">通过</view>
          <view class="btn-reject" @click.stop="goHandle(w.id)">拒绝</view>
        </view>
      </view>
      <view v-if="loading" class="loading-tip">加载中...</view>
<EmptyState v-if="!loading && records.length===0" text="暂无提现记录" image="/static/icons/暂无纪录.svg" />
    </scroll-view>
  </view>

</template>
<script setup>
import AuditPlaceholder from '@/components/AuditPlaceholder.vue'
import { useAuditPageGuard } from '@/composables/useAuditPageGuard'
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import StatusTag from '@/components/StatusTag.vue'
import EmptyState from '@/components/EmptyState.vue'
import { getCsWithdrawList, approveCsWithdraw } from '@/pages-cs/api/cs'

const { pageBlocked } = useAuditPageGuard()
const STATUS_TEXT = { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已拒绝', COMPLETED: '已到账' }
const STATUS_COLOR = { PENDING: '#ff9900', APPROVED: '#ff4544', REJECTED: '#ee0a24', COMPLETED: '#07c160' }
const statusFilter = ref('')
const records = ref([])
const pageNum = ref(1)
const loading = ref(false)
const finished = ref(false)

onShow(() => { refresh() })

function filterStatus(s) { statusFilter.value = s; refresh() }
function refresh() { pageNum.value = 1; records.value = []; finished.value = false; loadData() }
async function loadData() {
  loading.value = true
  const params = { pageNum: pageNum.value, pageSize: 20 }
  if (statusFilter.value) params.status = statusFilter.value
  const res = await getCsWithdrawList(params)
  const list = res.data?.records || []
  if (list.length < 20) finished.value = true
  records.value = pageNum.value === 1 ? list : [...records.value, ...list]
  loading.value = false
}
function loadMore() { if (!loading.value && !finished.value) { pageNum.value++; loadData() } }
function goHandle(id) { uni.navigateTo({ url: '/pages-cs/withdraw/handle?id=' + id }) }
async function doApprove(id) {
  uni.showModal({ title: '确认', content: '确定通过该提现申请？', success: async (r) => {
    if (r.confirm) { try { await approveCsWithdraw(id); uni.showToast({ title: '已通过' }); refresh() } catch (e) {} }
  }})
}
</script>
<style lang="scss" scoped>
.withdraw-list { background: #ffffff; min-height: 100vh; }
.filter-bar { white-space: nowrap; padding: 12rpx 24rpx; background: rgba(0,0,0,0.04); border-bottom: 1rpx solid rgba(0,0,0,0.04); }
.filter-item { display: inline-block; padding: 10rpx 20rpx; margin-right: 12rpx; font-size: 24rpx; color: rgba(0,0,0,0.55); background: rgba(0,0,0,0.05); border-radius: 999rpx; &.active { background: linear-gradient(135deg, #ff4544, #e63939); color: #ffffff; font-weight:bold; } }
.list { height: calc(100vh - 100rpx); padding: 20rpx 24rpx; }
.withdraw-card { background: rgba(0,0,0,0.04); border-radius: 12rpx; padding: 24rpx; margin-bottom: 16rpx;
  .card-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8rpx; .amount { font-size: 34rpx; font-weight: bold; color: rgba(0,0,0,0.85); } }
  .card-body { margin-bottom: 8rpx; .player { font-size: 26rpx; color: rgba(0,0,0,0.85); display: block; } .account { font-size: 24rpx; color: rgba(0,0,0,0.55); display: block; margin-top: 4rpx; } }
  .time { font-size: 22rpx; color: rgba(0,0,0,0.3); display: block; }
  .card-actions { display: flex; justify-content: flex-end; gap: 16rpx; padding-top: 16rpx; margin-top: 12rpx; border-top: 1rpx solid rgba(0,0,0,0.04); }
  .btn-approve { padding: 10rpx 28rpx; background: #07c160; color: #fff; font-size: 24rpx; border-radius: 999rpx; }
  .btn-reject { padding: 10rpx 28rpx; border: 1rpx solid #ee0a24; color: #ee0a24; font-size: 24rpx; border-radius: 999rpx; }
}
.loading-tip { text-align: center; padding: 32rpx; font-size: 24rpx; color: rgba(0,0,0,0.3); }
</style>
