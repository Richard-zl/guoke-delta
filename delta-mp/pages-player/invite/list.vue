<template>
  <AuditPlaceholder v-if="pageBlocked" />
  <view v-else class="invite-list-page">
    <!-- Tab 切换 -->
    <view class="tabs">
      <view class="tab" :class="{active: tab==='received'}" @click="switchTab('received')">收到的邀请</view>
      <view class="tab" :class="{active: tab==='sent'}" @click="switchTab('sent')">发出的邀请</view>
    </view>
    <scroll-view scroll-y class="list" @scrolltolower="loadMore">
      <view v-for="item in records" :key="item.id" class="invite-card">
        <view class="card-top">
          <image class="avatar" :src="(tab==='received' ? item.fromAvatar : item.toAvatar) || '/static/images/default-avatar.png'" mode="aspectFill" lazy-load />
          <view class="info">
            <text class="name">{{ tab === 'received' ? item.fromNickname : item.toNickname }}</text>
            <text class="order-no">订单：{{ item.orderNo || item.orderId }}</text>
            <text class="split-info">分成：{{ SPLIT_TYPE_TEXT[item.splitType] || '平分' }}{{ item.splitType === 'CUSTOM' && item.splitAmount ? ' ¥' + item.splitAmount : '' }}</text>
          </view>
          <view class="status-label" :style="{color: INVITE_STATUS_COLOR[item.status] || '#999'}">{{ INVITE_STATUS_TEXT[item.status] || item.status }}</view>
        </view>
        <view v-if="tab==='received' && item.status==='INVITED'" class="card-actions">
          <view class="btn-accept" @click="doAccept(item)">接受</view>
          <view class="btn-reject" @click="doReject(item)">拒绝</view>
        </view>
      </view>
      <view v-if="loading" class="loading-tip">加载中...</view>
<EmptyState v-if="!loading && records.length === 0" text="暂无邀请记录" image="/static/icons/暂无纪录.svg" />
    </scroll-view>
  </view>

</template>
<script setup>
import AuditPlaceholder from '@/components/AuditPlaceholder.vue'
import { useAuditPageGuard } from '@/composables/useAuditPageGuard'
const { pageBlocked } = useAuditPageGuard()

import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import EmptyState from '@/components/EmptyState.vue'
import StatusTag from '@/components/StatusTag.vue'
import { getInviteList, acceptInvite, rejectInvite } from '@/api/player'

const INVITE_STATUS_TEXT = { INVITED: '待处理', ACCEPTED: '已接受', REJECTED: '已拒绝', CANCELLED: '已作废' }
const INVITE_STATUS_COLOR = { INVITED: '#ff9900', ACCEPTED: '#07c160', REJECTED: '#ee0a24', CANCELLED: '#999' }
const SPLIT_TYPE_TEXT = { FIFTY_FIFTY: '五五开', FORTY_SIXTY: '四六开', THIRTY_SEVENTY: '三七开', CUSTOM: '自定义' }
const tab = ref('received')
const records = ref([])
const pageNum = ref(1)
const loading = ref(false)
const finished = ref(false)

onLoad((opts) => { if (opts.tab === 'sent' || opts.tab === 'received') tab.value = opts.tab })
onShow(() => { refresh() })

function switchTab(t) { tab.value = t; refresh() }
function refresh() { pageNum.value = 1; records.value = []; finished.value = false; loadData() }
async function loadData() {
  loading.value = true
  const res = await getInviteList({ pageNum: pageNum.value, pageSize: 20, type: tab.value })
  const list = res.data?.records || (Array.isArray(res.data) ? res.data : [])
  if (list.length < 20) finished.value = true
  records.value = pageNum.value === 1 ? list : [...records.value, ...list]
  loading.value = false
}
function loadMore() { if (!loading.value && !finished.value) { pageNum.value++; loadData() } }
/** 接受/拒绝接口需要传 orderId，不能传邀请记录 id */
function getOrderId(item) {
  return item?.orderId ?? null
}
async function doAccept(item) {
  const orderId = getOrderId(item)
  if (orderId == null) { uni.showToast({ title: '数据异常，缺少订单信息', icon: 'none' }); return }
  try {
    await acceptInvite(orderId)
    uni.showToast({ title: '已接受' })
    refresh()
  } catch (e) {
    uni.showToast({ title: e?.data?.msg || e?.message || '接受失败', icon: 'none' })
  }
}
async function doReject(item) {
  const orderId = getOrderId(item)
  if (orderId == null) { uni.showToast({ title: '数据异常，缺少订单信息', icon: 'none' }); return }
  uni.showModal({ title: '提示', content: '确定拒绝该邀请？', success: async (r) => {
    if (!r.confirm) return
    try {
      await rejectInvite(orderId)
      uni.showToast({ title: '已拒绝' })
      refresh()
    } catch (e) {
      uni.showToast({ title: e?.data?.msg || e?.message || '拒绝失败', icon: 'none' })
    }
  }})
}
</script>
<style lang="scss" scoped>
.invite-list-page { background: #ffffff; min-height: 100vh; }
.tabs { display: flex; background: #f1f5f9; border-bottom: 1rpx solid #f1f5f9;
  .tab { flex: 1; text-align: center; padding: 24rpx 0; font-size: 28rpx; color: #64748b; position: relative;
    &.active { color: #ff4544; &::after { content: ''; position: absolute; bottom: 0; left: 30%; right: 30%; height: 4rpx; background: linear-gradient(135deg, #ff4544, #e63939, #e63939); border-radius: 2rpx; } }
  }
}
.list { height: calc(100vh - 100rpx); padding: 20rpx 24rpx; }
.invite-card { background: #f1f5f9; border-radius: 12rpx; padding: 24rpx; margin-bottom: 16rpx;
  .card-top { display: flex; align-items: center; gap: 16rpx; }
  .avatar { width: 72rpx; height: 72rpx; border-radius: 50%; }
  .info { flex: 1; .name { font-size: 28rpx; font-weight: bold; display: block; } .order-no { font-size: 22rpx; color: #94a3b8; display: block; margin-top: 4rpx; } .split-info { font-size: 22rpx; color: #94a3b8; display: block; margin-top: 4rpx; } }
  .status-label { font-size: 24rpx; font-weight: 500; white-space: nowrap; }
  .card-actions { display: flex; justify-content: flex-end; gap: 20rpx; margin-top: 16rpx; padding-top: 16rpx; border-top: 1rpx solid #f1f5f9; }
  .btn-accept { padding: 10rpx 32rpx; background: linear-gradient(135deg, #ff4544, #e63939, #e63939); color: #ffffff; font-weight:bold; font-size: 26rpx; border-radius: 999rpx; }
  .btn-reject { padding: 10rpx 32rpx; border: 1rpx solid #ee0a24; color: #ee0a24; font-size: 26rpx; border-radius: 999rpx; }
}
.loading-tip { text-align: center; padding: 32rpx; font-size: 24rpx; color: #94a3b8; }
</style>
