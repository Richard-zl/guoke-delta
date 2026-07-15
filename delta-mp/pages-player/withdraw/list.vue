<template>
  <AuditPlaceholder v-if="pageBlocked" />
  <view v-else class="list-page">
    <scroll-view scroll-y class="list" @scrolltolower="loadMore">
      <view v-for="item in records" :key="item.id" class="record-item" @click="goDetail(item.id)">
        <view class="top-row">
          <text class="amount">¥{{ Number(item.amount).toFixed(2) }}</text>
          <StatusTag :status="item.status" :text-map="WITHDRAW_STATUS_TEXT" :color-map="WITHDRAW_STATUS_COLOR" />
        </view>
        <view class="info-row">
          <text class="account">{{ item.accountType === 'ALIPAY' ? '支付宝' : item.accountType === 'WECHAT' ? '微信' : '银行卡' }} {{ item.accountNo || '' }}</text>
          <text class="time">{{ item.createdAt }}</text>
        </view>
      </view>
      <view v-if="loading" class="loading-tip">加载中...</view>
<EmptyState v-if="!loading && records.length === 0" text="暂无提现记录" image="/static/icons/暂无纪录.svg" />
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
import StatusTag from '@/components/StatusTag.vue'
import { getWithdrawList } from '@/api/player'

const WITHDRAW_STATUS_TEXT = { PENDING: '审核中', APPROVED: '已通过', REJECTED: '已拒绝', COMPLETED: '已到账', CANCELLED: '已取消' }
const WITHDRAW_STATUS_COLOR = { PENDING: '#ff9900', APPROVED: '#ff4544', REJECTED: '#ee0a24', COMPLETED: '#07c160', CANCELLED: 'rgba(0,0,0,0.3)' }
const records = ref([])
const pageNum = ref(1)
const loading = ref(false)
const finished = ref(false)

onLoad(() => { loadData() })

async function loadData() {
  loading.value = true
  const res = await getWithdrawList({ pageNum: pageNum.value, pageSize: 20 })
  const list = res.data?.records || []
  if (list.length < 20) finished.value = true
  records.value = pageNum.value === 1 ? list : [...records.value, ...list]
  loading.value = false
}
function loadMore() { if (!loading.value && !finished.value) { pageNum.value++; loadData() } }
function goDetail(id) { uni.navigateTo({ url: '/pages-player/withdraw/detail?id=' + id }) }
</script>
<style lang="scss" scoped>
.list-page { background: #ffffff; min-height: 100vh; }
.list { height: 100vh; }
.record-item { margin: 20rpx 24rpx 0; padding: 24rpx; background: rgba(0,0,0,0.04); border-radius: 12rpx;
  .top-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12rpx; }
  .amount { font-size: 34rpx; font-weight: bold; color: rgba(0,0,0,0.85); }
  .info-row { display: flex; justify-content: space-between; .account { font-size: 24rpx; color: rgba(0,0,0,0.55); } .time { font-size: 22rpx; color: rgba(0,0,0,0.3); } }
}
.loading-tip { text-align: center; padding: 32rpx; font-size: 24rpx; color: rgba(0,0,0,0.3); }
</style>
