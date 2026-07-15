<template>
  <AuditPlaceholder v-if="pageBlocked" />
  <view v-else class="player-list">
    <view class="search-bar">
      <input v-model="keyword" placeholder="搜索接单员昵称/手机号" class="search-input" @confirm="refresh" />
    </view>
    <scroll-view scroll-x class="filter-bar">
      <view class="filter-item" :class="{active: !statusFilter}" @click="filterStatus('')">全部</view>
      <view class="filter-item" :class="{active: statusFilter==='PENDING'}" @click="filterStatus('PENDING')">待审核</view>
      <view class="filter-item" :class="{active: statusFilter==='ACTIVE'}" @click="filterStatus('ACTIVE')">已激活</view>
      <view class="filter-item" :class="{active: statusFilter==='FROZEN'}" @click="filterStatus('FROZEN')">已冻结</view>
    </scroll-view>
    <scroll-view scroll-y class="list" @scrolltolower="loadMore">
      <view v-for="p in players" :key="p.id" class="player-card">
        <image class="avatar" :src="p.avatar || '/static/images/default-avatar.png'" mode="aspectFill" lazy-load />
        <view class="info">
          <view class="name-row">
            <text class="name">{{ p.nickname || p.realName }}</text>
            <text v-if="p.status==='FROZEN'" class="status-tag frozen">已冻结</text>
            <text v-if="p.status==='PENDING'" class="status-tag pending">待审核</text>
          </view>
          <view class="tags">
            <text v-if="p.avgRating" class="tag">⭐{{ Number(p.avgRating).toFixed(1) }}</text>
            <text class="tag">完成 {{ p.completedOrders || 0 }}</text>
            <text v-if="p.activeOrders" class="tag active-tag">进行中 {{ p.activeOrders }}</text>
            <text v-if="p.phone" class="tag">{{ p.phone }}</text>
          </view>
          <text v-if="p.status==='FROZEN' && p.frozenUntil" class="frozen-tip">解冻时间：{{ formatTime(p.frozenUntil) }}</text>
        </view>
        <view class="card-actions">
          <text v-if="p.status==='PENDING'" class="action-btn gold" @click="goAudit(p.id)">审核</text>
          <text v-if="p.status==='ACTIVE'" class="action-btn danger" @click="openFreeze(p)">冻结</text>
          <text v-if="p.status==='FROZEN'" class="action-btn gold" @click="goAudit(p.id)">解冻</text>
        </view>
      </view>
      <view v-if="loading" class="loading-tip">加载中...</view>
      <EmptyState v-if="!loading && players.length===0" text="暂无接单员" image="/static/icons/暂无项目.svg" />
    </scroll-view>

    <!-- 冻结时长选择弹窗 -->
    <view v-if="showFreezeModal" class="modal-mask" @click="showFreezeModal = false">
      <view class="modal-body" @click.stop>
        <text class="modal-title">冻结接单员</text>
        <text class="modal-desc">确定冻结 {{ freezeTarget?.nickname }} ？</text>
        <text class="modal-label">选择冻结时长</text>
        <view class="duration-options">
          <view v-for="d in FREEZE_DURATIONS" :key="d.hours" class="dur-item"
            :class="{ active: freezeHours === d.hours }" @click="freezeHours = d.hours">
            {{ d.label }}
          </view>
        </view>
        <view class="modal-btns">
          <view class="modal-btn cancel" @click="showFreezeModal = false">取消</view>
          <view class="modal-btn confirm" @click="doFreeze">确认冻结</view>
        </view>
      </view>
    </view>
  </view>

</template>

<script setup>
import AuditPlaceholder from '@/components/AuditPlaceholder.vue'
import { useAuditPageGuard } from '@/composables/useAuditPageGuard'
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import EmptyState from '@/components/EmptyState.vue'
import { getCsPlayerList, freezeCsPlayer } from '@/pages-cs/api/cs'
const { pageBlocked } = useAuditPageGuard()

const FREEZE_DURATIONS = [
  { hours: 24, label: '1天' },
  { hours: 72, label: '3天' },
  { hours: 168, label: '7天' },
  { hours: 720, label: '30天' },
  { hours: 0, label: '永久' }
]

const keyword = ref('')
const statusFilter = ref('')
const players = ref([])
const pageNum = ref(1)
const loading = ref(false)
const finished = ref(false)
const showFreezeModal = ref(false)
const freezeTarget = ref(null)
const freezeHours = ref(24)

onShow(() => { refresh() })

function filterStatus(s) { statusFilter.value = s; refresh() }
function refresh() { pageNum.value = 1; players.value = []; finished.value = false; loadData() }
async function loadData() {
  loading.value = true
  const params = { pageNum: pageNum.value, pageSize: 20 }
  if (keyword.value) params.keyword = keyword.value
  if (statusFilter.value) params.status = statusFilter.value
  const res = await getCsPlayerList(params)
  const list = res.data?.records || []
  if (list.length < 20) finished.value = true
  players.value = pageNum.value === 1 ? list : [...players.value, ...list]
  loading.value = false
}
function loadMore() { if (!loading.value && !finished.value) { pageNum.value++; loadData() } }
function goAudit(id) { uni.navigateTo({ url: '/pages-cs/player/audit?id=' + id }) }

function openFreeze(p) {
  freezeTarget.value = p
  freezeHours.value = 24
  showFreezeModal.value = true
}

async function doFreeze() {
  if (!freezeTarget.value) return
  try {
    await freezeCsPlayer(freezeTarget.value.id, { hours: freezeHours.value })
    uni.showToast({ title: '已冻结' })
    showFreezeModal.value = false
    refresh()
  } catch (e) {}
}

function formatTime(t) {
  if (!t) return '永久'
  return t.replace('T', ' ').substring(0, 16)
}
</script>

<style lang="scss" scoped>
.player-list { background: #ffffff; min-height: 100vh; overflow: hidden; }

.search-bar { padding: 16rpx 24rpx; }
.search-input { background: rgba(0,0,0,0.05); padding: 24rpx 24rpx; border-radius: 999rpx; font-size: 26rpx; color: rgba(0,0,0,0.85); }

.filter-bar { white-space: nowrap; padding: 12rpx 24rpx; border-bottom: 1rpx solid rgba(0,0,0,0.04); }
.filter-item { display: inline-block; padding: 10rpx 20rpx; margin-right: 12rpx; font-size: 24rpx; color: rgba(0,0,0,0.55); background: rgba(0,0,0,0.05); border-radius: 999rpx; &.active { background: linear-gradient(135deg, #ff4544, #e63939); color: #ffffff; font-weight:bold; } }

.list { height: calc(100vh - 200rpx); padding: 20rpx 24rpx; box-sizing: border-box; }

.player-card {
  display: flex; align-items: center; gap: 16rpx;
  padding: 24rpx; background: rgba(0,0,0,0.04);
  border-radius: 12rpx; margin-bottom: 16rpx;
  box-sizing: border-box; overflow: hidden;

  .avatar { width: 80rpx; height: 80rpx; border-radius: 50%; flex-shrink: 0; }

  .info {
    flex: 1; overflow: hidden;
    .name-row { display: flex; align-items: center; gap: 8rpx; }
    .name { font-size: 28rpx; font-weight: bold; color: rgba(0,0,0,0.9); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .status-tag {
      font-size: 20rpx; padding: 2rpx 10rpx; border-radius: 4rpx; flex-shrink: 0;
      &.frozen { color: #ee0a24; background: rgba(238,10,36,0.12); }
      &.pending { color: #ff9900; background: rgba(255,153,0,0.12); }
    }
    .tags { display: flex; gap: 8rpx; margin-top: 8rpx; flex-wrap: wrap; }
    .tag {
      font-size: 22rpx; color: rgba(0,0,0,0.55);
      background: rgba(0,0,0,0.05); padding: 4rpx 12rpx; border-radius: 4rpx;
      &.active-tag { color: #ee6723; }
    }
    .frozen-tip { font-size: 22rpx; color: #ee0a24; margin-top: 6rpx; display: block; }
  }

  .card-actions {
    flex-shrink: 0;
    .action-btn {
      font-size: 26rpx; padding: 10rpx 24rpx; border-radius: 999rpx;
      &.gold { color: #ff4544; border: 1rpx solid rgba(255,69,68,0.15); }
      &.danger { color: #ee0a24; border: 1rpx solid rgba(238,10,36,0.3); }
    }
  }
}

.loading-tip { text-align: center; padding: 32rpx; font-size: 24rpx; color: rgba(0,0,0,0.3); }

/* 冻结弹窗 */
.modal-mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.7); display: flex; align-items: center; justify-content: center; z-index: 999; }
.modal-body { width: 85%; background: #ffffff; border: 1rpx solid rgba(255,69,68,0.15); border-radius: 16rpx; padding: 40rpx; }
.modal-title { font-size: 32rpx; font-weight: bold; text-align: center; display: block; color: #ff4544; margin-bottom: 16rpx; }
.modal-desc { font-size: 26rpx; color: rgba(0,0,0,0.55); text-align: center; display: block; margin-bottom: 32rpx; }
.modal-label { font-size: 26rpx; color: rgba(0,0,0,0.85); display: block; margin-bottom: 16rpx; }

.duration-options {
  display: flex; flex-wrap: wrap; gap: 16rpx; margin-bottom: 32rpx;
  .dur-item {
    padding: 16rpx 32rpx; background: rgba(0,0,0,0.05);
    border-radius: 8rpx; font-size: 26rpx; color: rgba(0,0,0,0.7);
    text-align: center; min-width: 120rpx;
    &.active { background: linear-gradient(135deg, #ff4544, #e63939); color: #ffffff; font-weight: bold; }
  }
}

.modal-btns { display: flex; gap: 24rpx; }
.modal-btn {
  flex: 1; height: 80rpx; line-height: 80rpx; text-align: center; border-radius: 999rpx; font-size: 28rpx;
  &.cancel { background: rgba(0,0,0,0.05); color: rgba(0,0,0,0.55); }
  &.confirm { background: #ee0a24; color: #fff; font-weight: bold; }
}
</style>
