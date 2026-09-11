<template>
  <view class="page">
    <view class="blob b1" />
    <view class="blob b2" />
    <view v-if="!list.length && !loading" class="empty">暂无上墙打手</view>
    <view
      v-for="item in list"
      :key="item.playerId"
      class="row"
      hover-class="row-press"
      @click="goDetail(item.playerId)"
    >
      <view class="cover-wrap">
        <image class="cover" :src="item.coverUrl || item.avatar" mode="aspectFill" />
      </view>
      <view class="info">
        <view class="name-row">
          <text class="name">{{ item.nickname }}</text>
          <text class="badge" :class="statusClass(item)">{{ statusText(item) }}</text>
        </view>
        <text class="tagline">{{ item.tagline }}</text>
        <view class="meta">
          <text class="chip score">{{ formatRating(item.avgRating) }} 分</text>
          <text class="chip done">完成 {{ item.completedOrders || 0 }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import { getActiveShowcase } from '@/api/showcase'
import { useAuditMode } from '@/composables/useAuditMode'
import { blockIfUnderReview } from '@/composables/useAuditGuard'

const { isUnderReview } = useAuditMode()
const list = ref([])
const loading = ref(false)
const pageNum = ref(1)
const noMore = ref(false)

onLoad(async () => {
  if (await blockIfUnderReview()) return
  fetchList(true)
})
onPullDownRefresh(async () => {
  await fetchList(true)
  uni.stopPullDownRefresh()
})
onReachBottom(() => { if (!noMore.value) fetchList(false) })

async function fetchList(reset) {
  if (loading.value || isUnderReview.value) return
  loading.value = true
  if (reset) { pageNum.value = 1; noMore.value = false }
  try {
    const res = await getActiveShowcase({ pageNum: pageNum.value, pageSize: 20 })
    const records = res.data?.records || []
    list.value = reset ? records : list.value.concat(records)
    if (records.length < 20) noMore.value = true
    else pageNum.value += 1
  } catch (e) {
    if (reset) list.value = []
  } finally {
    loading.value = false
  }
}

function formatRating(v) { return v == null ? '-' : Number(v).toFixed(1) }
function isFull(item) { return (item.activeOrders || 0) >= (item.maxConcurrent || 1) }
function statusText(item) {
  if (item.isOnline !== 1) return '离线'
  if (isFull(item)) return '满载'
  return '可接'
}
function statusClass(item) {
  if (item.isOnline !== 1) return 'off'
  if (isFull(item)) return 'full'
  return 'on'
}
function goDetail(playerId) {
  uni.navigateTo({ url: `/pages/showcase/detail?playerId=${playerId}` })
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #fff4e8;
  padding: 20rpx 24rpx 48rpx;
  position: relative;
  overflow: hidden;
}
.blob {
  position: absolute; pointer-events: none;
  border: 6rpx solid #111827;
}
.b1 {
  width: 180rpx; height: 180rpx; top: -40rpx; right: -40rpx;
  background: #7c3aed; border-radius: 70% 30% 60% 40%;
  transform: rotate(18deg);
}
.b2 {
  width: 120rpx; height: 120rpx; bottom: 80rpx; left: -36rpx;
  background: #00d4ff; border-radius: 40% 60% 30% 70%;
}
.empty { text-align: center; color: #111827; padding: 120rpx 0; font-size: 28rpx; position: relative; }
.row {
  display: flex; align-items: stretch; background: #fff;
  border: 6rpx solid #111827;
  border-radius: 28rpx 8rpx 36rpx 12rpx;
  box-shadow: 8rpx 8rpx 0 #111827;
  padding: 0; margin-bottom: 24rpx; overflow: hidden;
  position: relative;
}
.row-press { transform: translate(4rpx, 4rpx); box-shadow: 4rpx 4rpx 0 #111827; }
.cover-wrap {
  width: 168rpx; flex-shrink: 0; overflow: hidden;
  clip-path: polygon(0 0, 100% 0, 86% 100%, 0 100%);
}
.cover { width: 168rpx; height: 176rpx; background: #7c3aed; display: block; }
.info { flex: 1; min-width: 0; padding: 20rpx 20rpx 20rpx 8rpx; }
.name-row { display: flex; align-items: center; gap: 10rpx; }
.name { font-size: 32rpx; font-weight: 800; color: #111827; }
.badge {
  font-size: 20rpx; font-weight: 800; padding: 4rpx 12rpx;
  border: 4rpx solid #111827; color: #111827;
}
.badge.on { background: #b8f000; }
.badge.off { background: #e2e8f0; }
.badge.full { background: #ffd60a; }
.tagline {
  display: inline-block; margin-top: 10rpx; padding: 4rpx 12rpx;
  font-size: 24rpx; font-weight: 700; color: #111827;
  background: #ffd60a; border: 3rpx solid #111827;
  transform: skewX(-8deg);
  max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.meta { display: flex; gap: 8rpx; margin-top: 12rpx; flex-wrap: wrap; }
.chip {
  font-size: 20rpx; font-weight: 700; color: #111827;
  padding: 4rpx 10rpx; border: 3rpx solid #111827;
}
.chip.score { background: #00d4ff; }
.chip.done { background: #fff; }
</style>
