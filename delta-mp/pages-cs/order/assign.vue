<template>
  <AuditPlaceholder v-if="pageBlocked" />
  <view v-else class="assign-page">
    <view class="limit-tip">
      当前配置：接单员最多同时接 <text class="gold">{{maxConcurrent}}</text> 个订单
    </view>
    <view class="search-bar">
      <input v-model="keyword" placeholder="搜索接单员昵称/手机号" class="search-input" @confirm="searchPlayers" />
      <view class="search-btn" @click="searchPlayers">搜索</view>
    </view>
    <scroll-view scroll-y class="list" @scrolltolower="loadMore">
      <view v-for="p in players" :key="p.id" class="player-card" :class="{ full: isFull(p), offline: !isPlayerOnline(p) }">
        <image class="avatar" :src="p.avatar || '/static/images/default-avatar.png'" mode="aspectFill" lazy-load />
        <view class="info">
          <view class="name-row">
            <text class="name">{{ p.nickname }}</text>
            <text v-if="isPlayerOnline(p)" class="online-badge">在线</text>
            <text class="offline-badge">离线</text>
            <text v-if="isFull(p)" class="full-badge">已满载</text>
          </view>
          <view class="tags">
            <text v-if="p.avgRating" class="tag">⭐{{ Number(p.avgRating).toFixed(1) }}</text>
            <text class="tag done">完成 {{ p.completedOrders || 0 }}</text>
            <text class="tag" :class="(p.activeOrders||0) > 0 ? 'active-tag' : ''">进行中 {{ p.activeOrders || 0 }}/{{ maxConcurrent }}</text>
          </view>
        </view>
        <view class="assign-btn" :class="{ disabled: isDisabled(p) }" @click="doAssign(p)">指派</view>
      </view>
      <view v-if="loading" class="loading-tip">加载中...</view>
      <EmptyState v-if="!loading && players.length===0" text="暂无可用接单员" image="/static/icons/暂无项目.svg" />
    </scroll-view>
  </view>

</template>

<script setup>
import AuditPlaceholder from '@/components/AuditPlaceholder.vue'
import { useAuditPageGuard } from '@/composables/useAuditPageGuard'
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import EmptyState from '@/components/EmptyState.vue'
import { getCsPlayerAssignList, assignOrder } from '@/pages-cs/api/cs'
const { pageBlocked } = useAuditPageGuard()

const orderId = ref('')
const keyword = ref('')
const players = ref([])
const pageNum = ref(1)
const loading = ref(false)
const maxConcurrent = ref(5)

onLoad((opts) => { orderId.value = opts.orderId; loadData() })

function isFull(p) {
  return (p.activeOrders || 0) >= maxConcurrent.value
}
function isPlayerOnline(p) {
  return p.isOnline === 1
}
function isDisabled(p) {
  return isFull(p) || !isPlayerOnline(p)
}

function searchPlayers() { pageNum.value = 1; players.value = []; loadData() }

async function loadData() {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize: 20 }
    if (keyword.value) params.keyword = keyword.value
    const res = await getCsPlayerAssignList(params)
    const data = res.data || {}
    const page = data.players || {}
    const list = page.records || []
    players.value = pageNum.value === 1 ? list : [...players.value, ...list]
    if (data.maxConcurrent != null) maxConcurrent.value = data.maxConcurrent
  } catch (e) { /* ignore */ }
  finally { loading.value = false }
}

function loadMore() { if (!loading.value) { pageNum.value++; loadData() } }

function doAssign(p) {
  if (!isPlayerOnline(p)) {
    uni.showModal({ title: '无法指派', content: `${p.nickname} 当前处于离线状态，无法指派订单`, showCancel: false })
    return
  }
  if (isFull(p)) {
    uni.showModal({
      title: '无法指派',
      content: `${p.nickname} 当前已有 ${p.activeOrders||0} 个进行中订单，已达最大接单数 ${maxConcurrent.value}`,
      showCancel: false
    })
    return
  }
  const activeInfo = (p.activeOrders || 0) > 0 ? `\n该接单员当前有 ${p.activeOrders} 个进行中订单` : ''
  uni.showModal({
    title: '确认指派',
    content: `确定将订单指派给 ${p.nickname}？${activeInfo}`,
    success: async (r) => {
      if (r.confirm) {
        try {
          await assignOrder(orderId.value, p.id)
          uni.showToast({ title: '指派成功' })
          setTimeout(() => uni.navigateBack(), 1500)
        } catch (e) {
          const msg = e?.data?.msg || e?.msg || '指派失败'
          uni.showToast({ title: msg, icon: 'none' })
        }
      }
    }
  })
}
</script>

<style lang="scss" scoped>
.assign-page { background: #ffffff; min-height: 100vh; }

.limit-tip {
  padding: 16rpx 24rpx;
  font-size: 24rpx;
  color: rgba(0,0,0,0.45);
  background: rgba(0,0,0,0.04);
  text-align: center;
  .gold { color: #ff4544; font-weight: bold; }
}

.search-bar {
  display: flex; gap: 16rpx; padding: 16rpx 24rpx;
  .search-input { flex: 1; background: rgba(0,0,0,0.05); padding: 16rpx 24rpx; border-radius: 999rpx; font-size: 26rpx; color: rgba(0,0,0,0.85); }
  .search-btn { padding: 16rpx 32rpx; background: linear-gradient(135deg, #ff4544, #e63939); color: #ffffff; font-weight:bold; border-radius: 999rpx; font-size: 26rpx; }
}

.list { height: calc(100vh - 260rpx); padding: 20rpx 24rpx; }

.player-card {
  display: flex; align-items: center; gap: 16rpx;
  padding: 24rpx; background: rgba(0,0,0,0.04);
  border-radius: 12rpx; margin-bottom: 16rpx;

  &.full { opacity: 0.5; }
  &.offline { opacity: 0.5; }

  .avatar { width: 80rpx; height: 80rpx; border-radius: 50%; flex-shrink: 0; }

  .info {
    flex: 1; overflow: hidden;
    .name-row { display: flex; align-items: center; gap: 8rpx; }
    .name { font-size: 28rpx; font-weight: bold; color: rgba(0,0,0,0.9); }
    .online-badge {
      font-size: 20rpx; color: #22c55e; background: rgba(34,197,94,0.15);
      padding: 2rpx 10rpx; border-radius: 4rpx;
    }
    .offline-badge {
      font-size: 20rpx; color: #94a3b8; background: rgba(148,163,184,0.15);
      padding: 2rpx 10rpx; border-radius: 4rpx;
    }
    .full-badge {
      font-size: 20rpx; color: #ee6723; background: rgba(238,103,35,0.15);
      padding: 2rpx 10rpx; border-radius: 4rpx;
    }
    .tags { display: flex; gap: 8rpx; margin-top: 8rpx; flex-wrap: wrap; }
    .tag {
      font-size: 22rpx; color: rgba(0,0,0,0.55);
      background: rgba(0,0,0,0.05); padding: 4rpx 12rpx; border-radius: 4rpx;
      &.done { color: #07c160; }
      &.active-tag { color: #ee6723; }
    }
  }

  .assign-btn {
    padding: 12rpx 32rpx;
    background: linear-gradient(135deg, #ff4544, #e63939);
    color: #ffffff; font-weight:bold; font-size: 26rpx;
    border-radius: 999rpx; flex-shrink: 0;
    &.disabled { background: rgba(0,0,0,0.06); color: rgba(0,0,0,0.3); }
  }
}

.loading-tip { text-align: center; padding: 32rpx; font-size: 24rpx; color: rgba(0,0,0,0.3); }
</style>
