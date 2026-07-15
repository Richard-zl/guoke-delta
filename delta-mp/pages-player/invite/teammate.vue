<template>
  <AuditPlaceholder v-if="pageBlocked" />
  <view v-else class="invite-page">
    <view class="search-bar">
      <input v-model="keyword" placeholder="搜索接单员昵称" class="search-input" @confirm="searchTeammate" />
      <view class="search-btn" @click="searchTeammate">搜索</view>
    </view>
    <scroll-view
      v-if="players.length > 0"
      scroll-y
      class="list"
      :show-scrollbar="false"
      @scrolltolower="loadMore"
    >
      <view v-for="p in players" :key="p.id" class="player-card">
        <image class="avatar" :src="p.avatar || '/static/images/default-avatar.png'" mode="aspectFill" lazy-load />
        <view class="info">
          <text class="name">{{ p.nickname }}</text>
          <view class="tags">
            <text v-if="p.avgRating" class="tag">⭐{{ Number(p.avgRating).toFixed(1) }}</text>
            <text class="tag">已完成{{ p.completedOrders || 0 }}单</text>
            <text class="tag" :class="{'tag-warn': p.activeOrders >= maxConcurrent}">进行中{{ p.activeOrders || 0 }}/{{ maxConcurrent }}</text>
          </view>
        </view>
        <view class="invite-btn" @click="openInvite(p)">邀请</view>
      </view>
      <view v-if="loading" class="loading-tip">加载中...</view>
    </scroll-view>
    <!-- 空态/加载放在 scroll-view 外，避免真机 scroll-view 高度为 0 时内容不可见 -->
    <view v-if="loading && players.length === 0" class="state-wrap">
      <text class="loading-tip">加载中...</text>
    </view>
    <view v-if="!loading && players.length === 0" class="state-wrap">
      <EmptyState :text="emptyHint" image="/static/icons/暂无项目.svg" />
      <view v-if="pendingInvite" class="pending-tip">
        <text>已邀请 {{ pendingInvite.nickname }}，等待对方确认</text>
        <text class="pending-link" @click="goInviteList">查看邀请列表</text>
      </view>
    </view>
    <!-- 分成方式选择弹窗 -->
    <view v-if="showSplitModal" class="modal-mask" @click="showSplitModal=false">
      <view class="modal-box" @click.stop>
        <text class="modal-title">选择分成方式</text>
        <text class="modal-sub">邀请 {{ inviteTarget?.nickname }}</text>
        <view class="split-options">
          <view
            v-for="opt in splitOptions"
            :key="opt.value"
            class="split-opt"
            :class="{ active: selectedSplit === opt.value, disabled: !opt.enabled }"
            @click="selectSplit(opt)"
          >
            <text class="opt-label">{{ opt.label }}</text>
            <text class="opt-desc">{{ opt.enabled ? opt.desc : '暂未开放' }}</text>
          </view>
        </view>
        <view v-if="selectedSplit==='CUSTOM'" class="custom-input-row">
          <text class="custom-label">分成金额 (元)</text>
          <input v-model="customAmount" type="digit" placeholder="输入金额" class="custom-input" />
        </view>
        <view class="modal-actions">
          <view class="modal-cancel" @click="showSplitModal=false">取消</view>
          <view class="modal-confirm" @click="confirmInvite">确认邀请</view>
        </view>
      </view>
    </view>
  </view>

</template>
<script setup>
import AuditPlaceholder from '@/components/AuditPlaceholder.vue'
import { useAuditPageGuard } from '@/composables/useAuditPageGuard'
const { pageBlocked } = useAuditPageGuard()

import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import EmptyState from '@/components/EmptyState.vue'
import { getAvailableTeammates, inviteTeammate } from '@/api/player'

const orderId = ref('')
const keyword = ref('')
const players = ref([])
const pageNum = ref(1)
const loading = ref(false)
const finished = ref(false)
const maxConcurrent = ref(5)
const emptyHint = ref('暂无可邀请的队友')
const pendingInvite = ref(null)

// 分成方式相关
const showSplitModal = ref(false)
const inviteTarget = ref(null)
const selectedSplit = ref('FIFTY_FIFTY')
const customAmount = ref('')
// 当前仅开放五五开，其余选项置灰展示（后续开放改 enabled 即可）
const splitOptions = [
  { value: 'FIFTY_FIFTY', label: '五五开', desc: '平分接单员收入', enabled: true },
  { value: 'FORTY_SIXTY', label: '四六开', desc: '队友40% 你拿60%', enabled: false },
  { value: 'THIRTY_SEVENTY', label: '三七开', desc: '队友30% 你拿70%', enabled: false },
  { value: 'CUSTOM', label: '自定义金额', desc: '指定队友分成金额', enabled: false },
]

function selectSplit(opt) {
  if (!opt.enabled) return
  selectedSplit.value = opt.value
}

onLoad((opts) => { orderId.value = opts.orderId; loadData() })

function searchTeammate() { pageNum.value = 1; players.value = []; finished.value = false; loadData() }
async function loadData() {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize: 20, orderId: orderId.value }
    if (keyword.value) params.keyword = keyword.value
    const res = await getAvailableTeammates(params, { loading: false })
    const data = res.data || {}
    if (data.maxConcurrent) maxConcurrent.value = data.maxConcurrent
    pendingInvite.value = data.pendingInvite || null
    emptyHint.value = data.emptyHint || '暂无可邀请的队友'
    const page = data.players || {}
    const list = page.records || []
    if (list.length < 20) finished.value = true
    players.value = pageNum.value === 1 ? list : [...players.value, ...list]
  } catch (e) {
    if (pageNum.value === 1) players.value = []
  } finally {
    loading.value = false
  }
}
function goInviteList() { uni.navigateTo({ url: '/pages-player/invite/list?tab=sent' }) }
function loadMore() { if (!loading.value && !finished.value) { pageNum.value++; loadData() } }
function openInvite(player) {
  inviteTarget.value = player
  selectedSplit.value = 'FIFTY_FIFTY'
  customAmount.value = ''
  showSplitModal.value = true
}
async function confirmInvite() {
  if (!inviteTarget.value) return
  const data = { inviteePlayerId: inviteTarget.value.id, splitType: 'FIFTY_FIFTY' }
  if (selectedSplit.value === 'CUSTOM') {
    if (!customAmount.value || Number(customAmount.value) <= 0) {
      return uni.showToast({ title: '请输入有效金额', icon: 'none' })
    }
    data.customAmount = Number(customAmount.value)
  }
  try {
    await inviteTeammate(orderId.value, data)
    uni.showToast({ title: '已发送邀请' })
    showSplitModal.value = false
  } catch (e) {}
}
</script>
<style lang="scss" scoped>
.invite-page {
  min-height: 100vh;
  background: #ffffff;
}
.search-bar {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 24rpx;
  background: #f1f5f9;
  .search-input {
    flex: 1;
    min-height: 72rpx;
    height: 72rpx;
    line-height: 72rpx;
    box-sizing: border-box;
    background: #e2e8f0;
    padding: 0 24rpx;
    border-radius: 999rpx;
    font-size: 26rpx;
  }
  .search-btn {
    flex-shrink: 0;
    padding: 0 32rpx;
    height: 72rpx;
    line-height: 72rpx;
    background: linear-gradient(135deg, #ff4544, #e63939, #e63939);
    color: #ffffff;
    font-weight: bold;
    border-radius: 999rpx;
    font-size: 26rpx;
  }
}
/* 真机 scroll-view 必须显式高度，flex:1;height:0 在模拟器可用但真机会塌成 0 */
.list {
  height: calc(100vh - 110rpx);
  padding: 20rpx 24rpx;
  box-sizing: border-box;
}
.state-wrap {
  padding: 20rpx 24rpx;
  box-sizing: border-box;
}
.player-card { display: flex; align-items: center; gap: 16rpx; padding: 24rpx; background: #f1f5f9; border-radius: 12rpx; margin-bottom: 16rpx;
  .avatar { width: 80rpx; height: 80rpx; border-radius: 50%; }
  .info { flex: 1; .name { font-size: 28rpx; font-weight: bold; display: block; } .tags { display: flex; gap: 8rpx; margin-top: 8rpx; flex-wrap: wrap; } .tag { font-size: 22rpx; color: #64748b; background: #e2e8f0; padding: 4rpx 12rpx; border-radius: 4rpx; &.tag-warn { color: #ee0a24; background: rgba(238,10,36,0.1); } } }
  .invite-btn { padding: 12rpx 32rpx; background: linear-gradient(135deg, #ff4544, #e63939, #e63939); color: #ffffff; font-weight:bold; font-size: 26rpx; border-radius: 999rpx; }
}
.loading-tip { text-align: center; padding: 32rpx; font-size: 24rpx; color: #94a3b8; }
.pending-tip {
  margin: 0 24rpx 24rpx;
  padding: 24rpx;
  background: rgba(255, 69, 68, 0.08);
  border-radius: 12rpx;
  font-size: 24rpx;
  color: #64748b;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  .pending-link { color: #ff4544; font-weight: bold; }
}
/* 分成弹窗 */
.modal-mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; z-index: 999; }
.modal-box { width: 85%; background: #ffffff; border-radius: 16rpx; padding: 40rpx 32rpx; border: 1rpx solid #e2e8f0; }
.modal-title { font-size: 32rpx; font-weight: bold; display: block; text-align: center; color: #ff4544; }
.modal-sub { font-size: 24rpx; color: #94a3b8; display: block; text-align: center; margin-top: 8rpx; }
.split-options { margin-top: 24rpx; }
.split-opt {
  display: flex; justify-content: space-between; align-items: center;
  padding: 20rpx 24rpx; margin-bottom: 12rpx; background: #f1f5f9;
  border-radius: 12rpx; border: 2rpx solid transparent;
  &.active { border-color: #ff4544; background: rgba(255,69,68,0.1); }
  &.disabled {
    opacity: 0.45;
    .opt-label, .opt-desc { color: #cbd5e1; }
  }
  .opt-label { font-size: 28rpx; font-weight: bold; }
  .opt-desc { font-size: 22rpx; color: #94a3b8; }
}
.custom-input-row { display: flex; align-items: center; gap: 16rpx; margin-top: 16rpx;
  .custom-label { font-size: 26rpx; color: #64748b; white-space: nowrap; }
  .custom-input { flex: 1; background: #e2e8f0; padding: 16rpx 24rpx; border-radius: 12rpx; font-size: 28rpx; }
}
.modal-actions { display: flex; gap: 20rpx; margin-top: 32rpx; }
.modal-cancel { flex: 1; text-align: center; padding: 20rpx 0; border: 1rpx solid #cbd5e1; border-radius: 999rpx; font-size: 28rpx; color: #64748b; }
.modal-confirm { flex: 1; text-align: center; padding: 20rpx 0; background: linear-gradient(135deg, #ff4544, #e63939, #e63939); border-radius: 999rpx; font-size: 28rpx; color: #ffffff; font-weight: bold; }
</style>
