<template>
  <AuditPlaceholder v-if="pageBlocked" />

  <view v-else class="replace-list tab-page">
    <scroll-view scroll-x class="filter-bar">
      <view class="filter-item" :class="{active: !statusFilter}" @click="filterStatus('')">全部</view>
      <view class="filter-item" :class="{active: statusFilter==='PENDING'}" @click="filterStatus('PENDING')">待审核</view>
      <view class="filter-item" :class="{active: statusFilter==='APPROVED'}" @click="filterStatus('APPROVED')">已通过</view>
      <view class="filter-item" :class="{active: statusFilter==='REJECTED'}" @click="filterStatus('REJECTED')">已拒绝</view>
    </scroll-view>

    <scroll-view scroll-y class="list" :show-scrollbar="false" @scrolltolower="loadMore">
      <view v-for="r in replaces" :key="r.id" class="replace-card">
        <view class="card-top">
          <text class="order-no">{{ r.orderNo }}</text>
          <StatusTag :status="r.status" :text-map="REPLACE_STATUS_TEXT" :color-map="REPLACE_STATUS_COLOR" />
        </view>
        <view class="card-body">
          <text class="product-name">{{ r.productName }}</text>
          <view class="info-row">
            <text class="label">申请用户：</text>
            <text class="value">{{ r.userNickname || '-' }}</text>
          </view>
          <view class="info-row">
            <text class="label">当前打手：</text>
            <text class="value">{{ r.playerNickname || '-' }}</text>
          </view>
          <view class="info-row" v-if="r.reason">
            <text class="label">换人原因：</text>
            <text class="value reason">{{ r.reason }}</text>
          </view>
          <view class="info-row" v-if="r.processedAt">
            <text class="label">处理时间：</text>
            <text class="value">{{ formatTime(r.processedAt) }}</text>
          </view>
          <view class="info-row" v-if="r.operatorRemark">
            <text class="label">处理备注：</text>
            <text class="value">{{ r.operatorRemark }}</text>
          </view>
        </view>
        <view v-if="r.status==='PENDING'" class="card-actions">
          <view class="btn-approve" @click="openApproveModal(r)">同意换人</view>
          <view class="btn-reject" @click="openRejectModal(r)">拒绝</view>
        </view>
      </view>
      <view v-if="loading" class="loading-tip">加载中...</view>
      <EmptyState v-if="!loading && replaces.length===0" text="暂无换人申请" image="/static/icons/暂无项目.svg" />
    </scroll-view>

    <!-- 同意换人弹窗 -->
    <view v-if="showApproveModal" class="modal-mask" @click="closeApproveModal">
      <view class="modal-box" @click.stop>
        <text class="modal-title">同意换人</text>
        <view class="notice-bar">
          <text class="notice-text">⚠️ 被换打手本单不参与结算，无任何收益</text>
        </view>

        <!-- 选择处理方式 -->
        <view v-if="!showPlayerPicker">
          <text class="section-label">请选择处理方式</text>
          <view class="mode-item" @click="selectMode('hall')">
            <view class="mode-icon">🏛️</view>
            <view class="mode-info">
              <text class="mode-name">放到接单大厅</text>
              <text class="mode-desc">订单重新进入大厅，等待打手自主接单</text>
            </view>
            <text class="mode-arrow">›</text>
          </view>
          <view class="mode-item" @click="selectMode('assign')">
            <view class="mode-icon">👤</view>
            <view class="mode-info">
              <text class="mode-name">指定打手</text>
              <text class="mode-desc">从接单员列表中手动指定一名打手</text>
            </view>
            <text class="mode-arrow">›</text>
          </view>
          <view class="modal-actions">
            <view class="modal-cancel" @click="closeApproveModal">取消</view>
          </view>
        </view>

        <!-- 选择打手 -->
        <view v-if="showPlayerPicker">
          <view class="search-bar">
            <input v-model="playerKeyword" placeholder="搜索接单员昵称/手机号" class="search-input" @confirm="searchPlayers" />
            <view class="search-btn" @click="searchPlayers">搜索</view>
          </view>
          <scroll-view scroll-y class="player-list" @scrolltolower="loadMorePlayers">
            <view v-for="p in players" :key="p.id" class="player-card" :class="{full: isFull(p)}" @click="confirmAssignPlayer(p)">
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
            <view v-if="playerLoading" class="loading-tip">加载中...</view>
            <EmptyState v-if="!playerLoading && players.length===0" text="暂无可用接单员" image="/static/icons/暂无项目.svg" />
          </scroll-view>
          <view class="modal-actions">
            <view class="modal-cancel" @click="showPlayerPicker=false">返回</view>
          </view>
        </view>
      </view>
    </view>

    <!-- 拒绝弹窗 -->
    <view v-if="showRejectModal" class="modal-mask" @click="showRejectModal=false">
      <view class="modal-box" @click.stop>
        <text class="modal-title">拒绝换人申请</text>
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
import { getCsReplaceList, approveCsReplace, rejectCsReplace, getCsPlayerAssignList, assignOrder } from '@/pages-cs/api/cs'

const { pageBlocked } = useAuditPageGuard()
const REPLACE_STATUS_TEXT = { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已拒绝' }
const REPLACE_STATUS_COLOR = { PENDING: '#ff4544', APPROVED: '#07c160', REJECTED: '#999' }

const statusFilter = ref('')
const replaces = ref([])
const pageNum = ref(1)
const loading = ref(false)
const finished = ref(false)

const showApproveModal = ref(false)
const showRejectModal = ref(false)
const showPlayerPicker = ref(false)
const currentReplace = ref(null)
const rejectReason = ref('')

const playerKeyword = ref('')
const players = ref([])
const playerPageNum = ref(1)
const playerLoading = ref(false)
const playerFinished = ref(false)
const maxConcurrent = ref(5)

const remindStore = useRemindStore()

onShow(() => {
  remindStore.fetchCsRemind()
  refresh()
})

function filterStatus(s) { statusFilter.value = s; refresh() }
function refresh() { pageNum.value = 1; replaces.value = []; finished.value = false; loadData() }

async function loadData() {
  loading.value = true
  const params = { pageNum: pageNum.value, pageSize: 20 }
  if (statusFilter.value) params.status = statusFilter.value
  try {
    const res = await getCsReplaceList(params)
    const list = res.data?.records || []
    if (list.length < 20) finished.value = true
    replaces.value = pageNum.value === 1 ? list : [...replaces.value, ...list]
  } catch (e) { /* ignore */ }
  loading.value = false
}

function loadMore() { if (!loading.value && !finished.value) { pageNum.value++; loadData() } }

function openApproveModal(r) {
  currentReplace.value = r
  showPlayerPicker.value = false
  playerKeyword.value = ''
  players.value = []
  showApproveModal.value = true
}

function closeApproveModal() {
  showApproveModal.value = false
  showPlayerPicker.value = false
  currentReplace.value = null
}

function selectMode(mode) {
  if (mode === 'hall') {
    uni.showModal({
      title: '确认同意',
      content: '同意换人后，订单将重新放入接单大厅，被换打手本单无收益。确认吗？',
      success: async (res) => {
        if (res.confirm) {
          await doApprove(null)
        }
      }
    })
  } else {
    // 指定打手
    showPlayerPicker.value = true
    playerPageNum.value = 1
    playerFinished.value = false
    players.value = []
    loadPlayers()
  }
}

function isFull(p) { return (p.activeOrders || 0) >= maxConcurrent.value }

function searchPlayers() {
  playerPageNum.value = 1
  players.value = []
  playerFinished.value = false
  loadPlayers()
}

function loadMorePlayers() {
  if (!playerLoading.value && !playerFinished.value) {
    playerPageNum.value++
    loadPlayers()
  }
}

async function loadPlayers() {
  playerLoading.value = true
  try {
    const params = { pageNum: playerPageNum.value, pageSize: 20 }
    if (playerKeyword.value) params.keyword = playerKeyword.value
    const res = await getCsPlayerAssignList(params)
    const data = res.data || {}
    const page = data.players || {}
    const list = page.records || []
    if (list.length < 20) playerFinished.value = true
    if (data.maxConcurrent != null) maxConcurrent.value = data.maxConcurrent
    players.value = playerPageNum.value === 1 ? list : [...players.value, ...list]
  } catch (e) { /* ignore */ }
  playerLoading.value = false
}

function confirmAssignPlayer(p) {
  if (isFull(p)) {
    uni.showToast({ title: '该接单员已满载', icon: 'none' })
    return
  }
  uni.showModal({
    title: '确认指定',
    content: `同意换人并将订单指派给 ${p.nickname}？\n被换打手本单无收益。`,
    success: async (res) => {
      if (res.confirm) {
        await doApprove(p)
      }
    }
  })
}

async function doApprove(selectedPlayer) {
  if (!currentReplace.value) return
  try {
    await approveCsReplace(currentReplace.value.id, {})
    if (selectedPlayer) {
      try {
        await assignOrder(currentReplace.value.orderId, selectedPlayer.id)
        uni.showToast({ title: '已同意，打手已指派' })
      } catch (e) {
        // 换人已成功，指派失败则提示
        uni.showToast({ title: '换人成功，指派失败请手动指派', icon: 'none' })
      }
    } else {
      uni.showToast({ title: '已同意，订单已入接单大厅' })
    }
    closeApproveModal()
    remindStore.fetchCsRemind()
    refresh()
  } catch (e) {
    const msg = e?.data?.msg || e?.msg || '操作失败'
    uni.showToast({ title: msg, icon: 'none' })
  }
}

function openRejectModal(r) {
  currentReplace.value = r
  rejectReason.value = ''
  showRejectModal.value = true
}

async function confirmReject() {
  if (!currentReplace.value) return
  if (!rejectReason.value?.trim()) {
    return uni.showToast({ title: '请输入拒绝原因', icon: 'none' })
  }
  try {
    await rejectCsReplace(currentReplace.value.id, { remark: rejectReason.value.trim() })
    uni.showToast({ title: '已拒绝' })
    showRejectModal.value = false
    currentReplace.value = null
    remindStore.fetchCsRemind()
    refresh()
  } catch (e) {
    const msg = e?.data?.msg || e?.msg || '操作失败'
    uni.showToast({ title: msg, icon: 'none' })
  }
}

function formatTime(t) {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 16)
}
</script>

<style lang="scss" scoped>
.replace-list { background: #f1f5f9; overflow: hidden; position: relative; }

.filter-bar { white-space: nowrap; padding: 12rpx 24rpx; background: #ffffff; border-bottom: 1rpx solid #e2e8f0; }
.filter-item {
  display: inline-block; padding: 10rpx 20rpx; margin-right: 12rpx; font-size: 24rpx;
  color: #64748b; background: #f1f5f9; border-radius: 999rpx;
  &.active { background: linear-gradient(135deg, #ff4544, #e63939); color: #ffffff; font-weight: bold; }
}

.list { height: calc(100vh - 200rpx - env(safe-area-inset-bottom)); padding: 20rpx 24rpx; box-sizing: border-box; }

.replace-card {
  background: #ffffff; border-radius: 12rpx; padding: 24rpx; margin-bottom: 16rpx; border: 1rpx solid #e2e8f0;
  .card-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12rpx;
    .order-no { font-size: 24rpx; color: #64748b; }
  }
  .card-body {
    .product-name { font-size: 28rpx; font-weight: bold; color: #1e293b; display: block; margin-bottom: 8rpx; }
    .info-row {
      display: flex; margin-bottom: 6rpx; font-size: 26rpx;
      .label { color: #64748b; width: 140rpx; flex-shrink: 0; }
      .value { color: #1e293b; flex: 1; word-break: break-all;
        &.reason { -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; display: -webkit-box; }
      }
    }
  }
  .card-actions { display: flex; gap: 16rpx; justify-content: flex-end; padding-top: 16rpx; margin-top: 12rpx; border-top: 1rpx solid #e2e8f0; }
  .btn-approve { padding: 10rpx 28rpx; background: linear-gradient(135deg, #ff4544, #e63939); color: #ffffff; font-weight: bold; font-size: 24rpx; border-radius: 999rpx; }
  .btn-reject { padding: 10rpx 28rpx; border: 1rpx solid #ee0a24; color: #ee0a24; font-size: 24rpx; border-radius: 999rpx; }
}

.loading-tip { text-align: center; padding: 32rpx; font-size: 24rpx; color: #64748b; }

.modal-mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; z-index: 999; }
.modal-box { width: 90%; max-height: 82vh; background: #ffffff; border-radius: 16rpx; padding: 32rpx; box-sizing: border-box; }

.modal-title { font-size: 32rpx; font-weight: bold; display: block; text-align: center; color: #ff4544; margin-bottom: 20rpx; }

.notice-bar {
  background: rgba(238,10,36,0.06); border-radius: 8rpx; padding: 16rpx 20rpx; margin-bottom: 24rpx;
  .notice-text { font-size: 24rpx; color: #ee0a24; }
}

.section-label { font-size: 26rpx; color: #64748b; display: block; margin-bottom: 16rpx; }

.mode-item {
  display: flex; align-items: center; gap: 20rpx; padding: 24rpx 20rpx;
  background: #f8fafc; border-radius: 12rpx; margin-bottom: 16rpx; border: 1rpx solid #e2e8f0;
  .mode-icon { font-size: 40rpx; width: 60rpx; text-align: center; flex-shrink: 0; }
  .mode-info { flex: 1;
    .mode-name { font-size: 28rpx; font-weight: bold; color: #1e293b; display: block; margin-bottom: 6rpx; }
    .mode-desc { font-size: 22rpx; color: #64748b; }
  }
  .mode-arrow { font-size: 32rpx; color: #cbd5e1; flex-shrink: 0; }
}

.search-bar { display: flex; gap: 16rpx; margin-bottom: 16rpx;
  .search-input { flex: 1; background: #f1f5f9; padding: 16rpx 24rpx; border-radius: 999rpx; font-size: 26rpx; color: #1e293b; }
  .search-btn { padding: 16rpx 32rpx; background: linear-gradient(135deg, #ff4544, #e63939); color: #ffffff; font-weight: bold; border-radius: 999rpx; font-size: 26rpx; flex-shrink: 0; }
}

.player-list { max-height: 400rpx; }

.player-card {
  display: flex; align-items: center; gap: 16rpx; padding: 20rpx; background: #f1f5f9; border-radius: 12rpx; margin-bottom: 12rpx;
  &.full { opacity: 0.5; }
  .avatar { width: 64rpx; height: 64rpx; border-radius: 50%; flex-shrink: 0; }
  .info { flex: 1;
    .name { font-size: 28rpx; font-weight: bold; color: #1e293b; display: block; }
    .tags { display: flex; gap: 8rpx; margin-top: 6rpx; flex-wrap: wrap; }
    .tag { font-size: 22rpx; color: #64748b; background: #e2e8f0; padding: 4rpx 12rpx; border-radius: 4rpx; }
  }
}

.reason-row { margin-top: 16rpx;
  .reason-label { font-size: 26rpx; color: #64748b; display: block; margin-bottom: 8rpx; }
  .reason-input { width: 100%; min-height: 120rpx; background: #f1f5f9; padding: 16rpx 24rpx; border-radius: 12rpx; font-size: 28rpx; box-sizing: border-box; color: #1e293b; }
}

.modal-actions { display: flex; gap: 20rpx; margin-top: 24rpx; }
.modal-cancel {
  flex: 1; text-align: center; padding: 20rpx 0; border: 1rpx solid #e2e8f0;
  border-radius: 999rpx; font-size: 28rpx; color: #64748b;
}
.modal-confirm {
  flex: 1; text-align: center; padding: 20rpx 0;
  background: linear-gradient(135deg, #ff4544, #e63939);
  border-radius: 999rpx; font-size: 28rpx; color: #ffffff; font-weight: bold;
}
</style>
