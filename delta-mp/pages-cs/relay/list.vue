<template>
  <AuditPlaceholder v-if="pageBlocked" />
  <view v-else class="relay-list tab-page">
    <scroll-view scroll-x class="filter-bar">
      <view class="filter-item" :class="{active: !statusFilter}" @click="filterStatus('')">全部</view>
      <view class="filter-item" :class="{active: statusFilter==='PENDING'}" @click="filterStatus('PENDING')">待审核</view>
      <view class="filter-item" :class="{active: statusFilter==='APPROVED'}" @click="filterStatus('APPROVED')">已通过</view>
      <view class="filter-item" :class="{active: statusFilter==='REJECTED'}" @click="filterStatus('REJECTED')">已拒绝</view>
    </scroll-view>
    <scroll-view scroll-y class="list" :show-scrollbar="false" @scrolltolower="loadMore">
      <view v-for="r in relays" :key="r.id" class="relay-card">
        <view class="card-top">
          <text class="order-no">{{ r.orderNo }}</text>
          <StatusTag :status="r.status" :text-map="RELAY_STATUS_TEXT" :color-map="RELAY_STATUS_COLOR" />
        </view>
        <view class="card-body">
          <text class="product-name">{{ r.productName }}</text>
          <view class="info-row">
            <text class="label">原接单员：</text>
            <text class="value">{{ r.playerNickname || r.originalPlayerName || '-' }}</text>
          </view>
          <view class="info-row">
            <text class="label">分成：</text>
            <text class="value">{{ SPLIT_TYPE_TEXT[r.splitType] || r.splitType }}</text>
          </view>
          <view class="info-row" v-if="r.reason">
            <text class="label">原因：</text>
            <text class="value reason">{{ r.reason }}</text>
          </view>
        </view>
        <view v-if="r.status==='PENDING'" class="card-actions">
          <view class="btn-approve" @click="openApproveModal(r)">审核通过</view>
          <view class="btn-reject" @click="openRejectModal(r)">拒绝</view>
        </view>
      </view>
      <view v-if="loading" class="loading-tip">加载中...</view>
      <EmptyState v-if="!loading && relays.length===0" text="暂无接力申请" image="/static/icons/暂无项目.svg" />
    </scroll-view>
    <!-- 审核通过 - 选择接力接单员 -->
    <view v-if="showApproveModal" class="modal-mask" @click="showApproveModal=false">
      <view class="modal-box approve-modal" @click.stop>
        <text class="modal-title">选择接力接单员</text>
        <view class="search-bar">
          <input v-model="playerKeyword" placeholder="搜索接单员昵称/手机号" class="search-input" @confirm="searchPlayers" />
          <view class="search-btn" @click="searchPlayers">搜索</view>
        </view>
        <scroll-view scroll-y class="player-list" @scrolltolower="loadMoreApprovePlayers">
          <view v-for="p in approvePlayers" :key="p.id" class="player-card" :class="{ full: isFull(p) }" @click="selectPlayer(p)">
            <image class="avatar" :src="p.avatar || '/static/images/default-avatar.png'" mode="aspectFill" lazy-load />
            <view class="info">
              <text class="name">{{ p.nickname }}</text>
              <view class="tags">
                <text v-if="p.avgRating" class="tag">⭐{{ Number(p.avgRating).toFixed(1) }}</text>
                <text class="tag">完成 {{ p.completedOrders || 0 }}</text>
                <text class="tag">进行中 {{ p.activeOrders || 0 }}/{{ maxConcurrent }}</text>
              </view>
            </view>
          </view>
          <view v-if="approveLoading" class="loading-tip">加载中...</view>
          <EmptyState v-if="!approveLoading && approvePlayers.length===0" text="暂无可用接单员" image="/static/icons/暂无项目.svg" />
        </scroll-view>
        <view class="modal-actions">
          <view class="modal-cancel" @click="showApproveModal=false">取消</view>
        </view>
      </view>
    </view>
    <!-- 拒绝 - 输入原因 -->
    <view v-if="showRejectModal" class="modal-mask" @click="showRejectModal=false">
      <view class="modal-box" @click.stop>
        <text class="modal-title">拒绝接力申请</text>
        <view class="reason-row">
          <text class="reason-label">拒绝原因</text>
          <textarea v-model="rejectReason" placeholder="请输入拒绝原因" class="reason-input" />
        </view>
        <view class="modal-actions">
          <view class="modal-cancel" @click="showRejectModal=false">取消</view>
          <view class="modal-confirm" @click="confirmReject">确认拒绝</view>
        </view>
      </view>
    </view>
    <CustomTabBar :current="-1" />
  </view>

</template>
<script setup>
import AuditPlaceholder from '@/components/AuditPlaceholder.vue'
import { useAuditPageGuard } from '@/composables/useAuditPageGuard'
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import StatusTag from '@/components/StatusTag.vue'
import EmptyState from '@/components/EmptyState.vue'
import CustomTabBar from '@/components/CustomTabBar.vue'
import { useRemindStore } from '@/store/remind'
import { getCsRelayList, approveCsRelay, rejectCsRelay, getCsPlayerAssignList } from '@/pages-cs/api/cs'
const { pageBlocked } = useAuditPageGuard()

const RELAY_STATUS_TEXT = { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已拒绝' }
const RELAY_STATUS_COLOR = { PENDING: '#ff4544', APPROVED: '#07c160', REJECTED: '#999' }
const SPLIT_TYPE_TEXT = {
  FIFTY_FIFTY: '五五开',
  FORTY_SIXTY: '四六开',
  THIRTY_SEVENTY: '三七开',
  CUSTOM: '自定义金额'
}

const statusFilter = ref('')
const relays = ref([])
const pageNum = ref(1)
const loading = ref(false)
const finished = ref(false)

const showApproveModal = ref(false)
const showRejectModal = ref(false)
const currentRelay = ref(null)
const playerKeyword = ref('')
const approvePlayers = ref([])
const approvePageNum = ref(1)
const approveLoading = ref(false)
const approveFinished = ref(false)
const maxConcurrent = ref(5)
const rejectReason = ref('')
const remindStore = useRemindStore()

onShow(() => {
  remindStore.fetchCsRemind()
  refresh()
})

function filterStatus(s) { statusFilter.value = s; refresh() }
function refresh() { pageNum.value = 1; relays.value = []; finished.value = false; loadData() }

async function loadData() {
  loading.value = true
  const params = { pageNum: pageNum.value, pageSize: 20 }
  if (statusFilter.value) params.status = statusFilter.value
  try {
    const res = await getCsRelayList(params)
    const list = res.data?.records || []
    if (list.length < 20) finished.value = true
    relays.value = pageNum.value === 1 ? list : [...relays.value, ...list]
  } catch (e) { /* ignore */ }
  loading.value = false
}

function loadMore() { if (!loading.value && !finished.value) { pageNum.value++; loadData() } }

function isFull(p) {
  return (p.activeOrders || 0) >= maxConcurrent.value
}

function openApproveModal(r) {
  currentRelay.value = r
  playerKeyword.value = ''
  approvePlayers.value = []
  approvePageNum.value = 1
  approveFinished.value = false
  showApproveModal.value = true
  loadApprovePlayers()
}

function searchPlayers() {
  approvePageNum.value = 1
  approvePlayers.value = []
  approveFinished.value = false
  loadApprovePlayers()
}

function loadMoreApprovePlayers() {
  if (!approveLoading.value && !approveFinished.value) {
    approvePageNum.value++
    loadApprovePlayers()
  }
}

async function loadApprovePlayers() {
  approveLoading.value = true
  try {
    const params = { pageNum: approvePageNum.value, pageSize: 20 }
    if (playerKeyword.value) params.keyword = playerKeyword.value
    const res = await getCsPlayerAssignList(params)
    const data = res.data || {}
    const page = data.players || {}
    const list = page.records || []
    if (list.length < 20) approveFinished.value = true
    if (data.maxConcurrent != null) maxConcurrent.value = data.maxConcurrent
    approvePlayers.value = approvePageNum.value === 1 ? list : [...approvePlayers.value, ...list]
  } catch (e) { /* ignore */ }
  approveLoading.value = false
}

async function selectPlayer(p) {
  if (isFull(p)) {
    uni.showToast({ title: '该接单员已满载', icon: 'none' })
    return
  }
  if (!currentRelay.value) return
  try {
    await approveCsRelay(currentRelay.value.id, { playerId: p.id })
    uni.showToast({ title: '审核通过' })
    showApproveModal.value = false
    currentRelay.value = null
    remindStore.fetchCsRemind()
    refresh()
  } catch (e) {
    const msg = e?.data?.msg || e?.msg || '操作失败'
    uni.showToast({ title: msg, icon: 'none' })
  }
}

function openRejectModal(r) {
  currentRelay.value = r
  rejectReason.value = ''
  showRejectModal.value = true
}

async function confirmReject() {
  if (!currentRelay.value) return
  if (!rejectReason.value?.trim()) {
    return uni.showToast({ title: '请输入拒绝原因', icon: 'none' })
  }
  try {
    await rejectCsRelay(currentRelay.value.id, { reason: rejectReason.value.trim() })
    uni.showToast({ title: '已拒绝' })
    showRejectModal.value = false
    currentRelay.value = null
    remindStore.fetchCsRemind()
    refresh()
  } catch (e) {
    const msg = e?.data?.msg || e?.msg || '操作失败'
    uni.showToast({ title: msg, icon: 'none' })
  }
}
</script>
<style lang="scss" scoped>
.relay-list { background: #f1f5f9; overflow: hidden; position: relative; }
.filter-bar { white-space: nowrap; padding: 12rpx 24rpx; background: #ffffff; border-bottom: 1rpx solid #e2e8f0; }
.filter-item { display: inline-block; padding: 10rpx 20rpx; margin-right: 12rpx; font-size: 24rpx; color: #64748b; background: #f1f5f9; border-radius: 999rpx;
  &.active { background: linear-gradient(135deg, #ff4544, #e63939); color: #ffffff; font-weight: bold; }
}
.list { height: calc(100vh - 200rpx - env(safe-area-inset-bottom)); padding: 20rpx 24rpx; box-sizing: border-box; }
.relay-card {
  background: #ffffff; border-radius: 12rpx; padding: 24rpx; margin-bottom: 16rpx; border: 1rpx solid #e2e8f0;
  .card-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12rpx;
    .order-no { font-size: 24rpx; color: #64748b; }
  }
  .card-body {
    .product-name { font-size: 28rpx; font-weight: bold; color: #1e293b; display: block; margin-bottom: 8rpx; }
    .info-row {
      display: flex;
      margin-bottom: 6rpx;
      font-size: 26rpx;
      .label {
        color: #64748b;
        width: 120rpx;
        flex-shrink: 0;
      }
      .value {
        color: #1e293b;
        flex: 1;
        word-break: break-all;
      }
      .value.reason {
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;
        display: -webkit-box;
      }
    }
  }
  .card-actions { display: flex; gap: 16rpx; justify-content: flex-end; padding-top: 16rpx; margin-top: 12rpx; border-top: 1rpx solid #e2e8f0; }
  .btn-approve { padding: 10rpx 28rpx; background: linear-gradient(135deg, #ff4544, #e63939); color: #ffffff; font-weight: bold; font-size: 24rpx; border-radius: 999rpx; }
  .btn-reject { padding: 10rpx 28rpx; border: 1rpx solid #ee0a24; color: #ee0a24; font-size: 24rpx; border-radius: 999rpx; }
}
.loading-tip { text-align: center; padding: 32rpx; font-size: 24rpx; color: #64748b; }

.modal-mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; z-index: 999; }
.modal-box {
  width: 90%; max-height: 80vh; background: #ffffff; border-radius: 16rpx; padding: 32rpx; border: 1rpx solid #e2e8f0;
  &.approve-modal .player-list { max-height: 400rpx; }
}
.modal-title { font-size: 32rpx; font-weight: bold; display: block; text-align: center; color: #ff4544; margin-bottom: 24rpx; }
.search-bar { display: flex; gap: 16rpx; margin-bottom: 16rpx;
  .search-input { flex: 1; background: #f1f5f9; padding: 16rpx 24rpx; border-radius: 999rpx; font-size: 26rpx; color: #1e293b; }
  .search-btn { padding: 16rpx 32rpx; background: linear-gradient(135deg, #ff4544, #e63939); color: #ffffff; font-weight: bold; border-radius: 999rpx; font-size: 26rpx; }
}
.player-card {
  display: flex; align-items: center; gap: 16rpx; padding: 20rpx; background: #f1f5f9; border-radius: 12rpx; margin-bottom: 12rpx;
  &.full { opacity: 0.5; }
  .avatar { width: 64rpx; height: 64rpx; border-radius: 50%; flex-shrink: 0; }
  .info { flex: 1; .name { font-size: 28rpx; font-weight: bold; color: #1e293b; display: block; } .tags { display: flex; gap: 8rpx; margin-top: 6rpx; flex-wrap: wrap; } .tag { font-size: 22rpx; color: #64748b; background: #e2e8f0; padding: 4rpx 12rpx; border-radius: 4rpx; } }
}
.reason-row { margin-top: 16rpx;
  .reason-label { font-size: 26rpx; color: #64748b; display: block; margin-bottom: 8rpx; }
  .reason-input { width: 100%; min-height: 120rpx; background: #f1f5f9; padding: 16rpx 24rpx; border-radius: 12rpx; font-size: 28rpx; box-sizing: border-box; color: #1e293b; }
}
.modal-actions {
  display: flex;
  gap: 20rpx;
  margin-top: 24rpx;
}
.modal-cancel {
  flex: 1;
  text-align: center;
  padding: 20rpx 0;
  border: 1rpx solid #e2e8f0;
  border-radius: 999rpx;
  font-size: 28rpx;
  color: #64748b;
}
.modal-confirm {
  flex: 1;
  text-align: center;
  padding: 20rpx 0;
  background: linear-gradient(135deg, #ff4544, #e63939);
  border-radius: 999rpx;
  font-size: 28rpx;
  color: #ffffff;
  font-weight: bold;
}
</style>
