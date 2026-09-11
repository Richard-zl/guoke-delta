<template>
  <view class="page" v-if="detail">
    <view class="blob b1" />
    <view class="blob b2" />
    <view class="hero-wrap">
      <image class="hero" :src="detail.coverUrl || detail.avatar" mode="aspectFill" />
    </view>
    <view class="card">
      <text class="name">{{ detail.nickname }}</text>
      <text class="tagline">{{ detail.tagline }}</text>
      <view class="stats">
        <view class="stat s1">
          <text class="num">{{ formatRating(detail.avgRating) }}</text>
          <text class="label">评分</text>
        </view>
        <view class="stat s2">
          <text class="num">{{ detail.completedOrders || 0 }}</text>
          <text class="label">完成单</text>
        </view>
        <view class="stat s3">
          <text class="num">{{ formatRate(detail.completeRate) }}</text>
          <text class="label">完成率</text>
        </view>
        <view class="stat s4" :class="statusClass">
          <text class="num">{{ statusText }}</text>
          <text class="label">状态</text>
        </view>
      </view>
      <view v-if="detail.skillTags?.length" class="tags">
        <text v-for="(tag, i) in detail.skillTags" :key="tag" class="tag" :class="'t' + (i % 3)">{{ tag }}</text>
      </view>
    </view>

    <view class="block voice-block">
      <text class="block-title">语音介绍 · {{ detail.introVoiceSeconds || 0 }}秒</text>
      <view class="voice-btn" :class="{ playing }" hover-class="btn-press" @click="toggleVoice">
        {{ playing ? '暂停' : '播放语音' }}
      </view>
    </view>
    <view v-if="detail.bio" class="block">
      <text class="block-title">简介</text>
      <text class="bio">{{ detail.bio }}</text>
    </view>
    <view v-if="detail.highlightImages?.length" class="block">
      <text class="block-title">高光图</text>
      <view class="gallery">
        <image
          v-for="(img, i) in detail.highlightImages"
          :key="img"
          class="g-img"
          :class="'g' + (i % 3)"
          :src="img"
          mode="aspectFill"
          @click="preview(i)"
        />
      </view>
    </view>
    <view class="block">
      <text class="block-title">老板评价</text>
      <view v-if="!reviews.length" class="empty">暂无评价</view>
      <view v-for="r in reviews" :key="r.id" class="review">
        <view class="review-head">
          <text class="review-name">{{ r.userNickname || '匿名老板' }}</text>
          <view class="star-row">
            <view v-for="n in 5" :key="n" class="star-box" :class="{ on: n <= (r.rating || 0) }" />
          </view>
        </view>
        <text class="content">{{ r.content || '（无文字）' }}</text>
      </view>
      <view v-if="!reviewDone && reviews.length" class="more" @click="loadReviews(false)">加载更多</view>
    </view>
    <view class="bottom-spacer" />
    <view class="bottom">
      <text v-if="!detail.canDesignate" class="tip">{{ detail.designateBlockReason || '暂不可指定' }}</text>
      <view
        class="btn"
        :class="{ disabled: !detail.canDesignate }"
        hover-class="btn-press"
        @click="fromPicker ? pickThis() : goOrder()"
      >
        {{ fromPicker ? '就选 TA' : '指定 TA 下单' }}
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { getShowcaseDetail, getShowcaseReviews } from '@/api/showcase'
import { blockIfUnderReview } from '@/composables/useAuditGuard'

const detail = ref(null)
const fromPicker = ref(false)
const reviews = ref([])
const reviewPage = ref(1)
const reviewDone = ref(false)
const playing = ref(false)
let audio = null

const statusText = computed(() => {
  const d = detail.value
  if (!d) return ''
  if (d.isOnline !== 1) return '离线'
  if ((d.activeOrders || 0) >= (d.maxConcurrent || 1)) return '满载'
  return '在线'
})
const statusClass = computed(() => {
  const d = detail.value
  if (!d || d.isOnline !== 1) return 'off'
  if ((d.activeOrders || 0) >= (d.maxConcurrent || 1)) return 'full'
  return 'on'
})

onLoad(async (opts) => {
  if (await blockIfUnderReview()) return
  fromPicker.value = opts.from === 'picker'
  if (!opts.playerId) {
    uni.showToast({ title: '该打手暂未展示', icon: 'none' })
    setTimeout(() => uni.navigateBack(), 400)
    return
  }
  try {
    const res = await getShowcaseDetail(opts.playerId)
    detail.value = res.data
    loadReviews(true)
  } catch (e) {
    uni.showToast({ title: e.msg || '该打手暂未展示', icon: 'none' })
    setTimeout(() => uni.redirectTo({ url: '/pages/showcase/list' }), 500)
  }
})
onUnload(() => stopVoice())

async function loadReviews(reset) {
  if (!detail.value) return
  if (reset) { reviewPage.value = 1; reviewDone.value = false; reviews.value = [] }
  const res = await getShowcaseReviews(detail.value.playerId, { pageNum: reviewPage.value, pageSize: 10 })
  const records = res.data?.records || []
  reviews.value = reset ? records : reviews.value.concat(records)
  if (records.length < 10) reviewDone.value = true
  else reviewPage.value += 1
}

function formatRating(v) { return v == null ? '-' : Number(v).toFixed(1) }
function formatRate(v) { return v == null ? '-' : `${Number(v)}%` }
function preview(i) { uni.previewImage({ urls: detail.value.highlightImages, current: i }) }

function toggleVoice() {
  const url = detail.value?.introVoiceUrl
  if (!url) return uni.showToast({ title: '暂无语音', icon: 'none' })
  if (playing.value) return stopVoice()
  audio = uni.createInnerAudioContext()
  audio.src = url
  audio.onPlay(() => { playing.value = true })
  audio.onEnded(() => stopVoice())
  audio.onError(() => {
    uni.showToast({ title: '语音加载失败', icon: 'none' })
    stopVoice()
  })
  audio.play()
}
function stopVoice() {
  playing.value = false
  if (audio) { audio.stop(); audio.destroy(); audio = null }
}

function rememberAnd(action) {
  if (!detail.value?.canDesignate) {
    return uni.showToast({ title: detail.value?.designateBlockReason || '暂不可指定', icon: 'none' })
  }
  uni.setStorageSync('designatePlayerId', String(detail.value.playerId))
  action()
}
// 分类页是 tabBar 页，只能用 switchTab
function goOrder() { rememberAnd(() => uni.switchTab({ url: '/pages/category/index' })) }
function pickThis() { rememberAnd(() => uni.navigateBack()) }
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #fff4e8;
  padding-bottom: 180rpx;
  position: relative;
  overflow: hidden;
}
.blob {
  position: absolute; pointer-events: none; z-index: 0;
  border: 6rpx solid #111827;
}
.b1 {
  width: 220rpx; height: 220rpx; top: 280rpx; right: -70rpx;
  background: #7c3aed; border-radius: 62% 38% 48% 52%;
}
.b2 {
  width: 140rpx; height: 140rpx; top: 520rpx; left: -50rpx;
  background: #ffd60a; border-radius: 30% 70% 40% 60%;
}
.hero-wrap {
  position: relative; z-index: 1;
  clip-path: polygon(0 0, 100% 0, 100% 78%, 0 100%);
}
.hero { width: 100%; height: 440rpx; background: #7c3aed; display: block; }
.card {
  margin: -72rpx 24rpx 0; background: #fff; padding: 28rpx 24rpx 24rpx;
  border: 6rpx solid #111827;
  border-radius: 12rpx 40rpx 16rpx 36rpx;
  box-shadow: 10rpx 10rpx 0 #111827;
  position: relative; z-index: 2;
}
.name { font-size: 40rpx; font-weight: 900; color: #111827; }
.tagline {
  display: inline-block; margin-top: 10rpx; padding: 6rpx 16rpx;
  color: #111827; font-size: 24rpx; font-weight: 800;
  background: #ff4544; color: #fff;
  border: 4rpx solid #111827;
  transform: rotate(-2deg);
}
.stats { display: flex; gap: 10rpx; margin-top: 24rpx; }
.stat {
  flex: 1; text-align: center; padding: 12rpx 0 10rpx;
  border: 4rpx solid #111827; color: #111827;
}
.stat.s1 { background: #00d4ff; border-radius: 20rpx 6rpx 18rpx 8rpx; }
.stat.s2 { background: #ffd60a; border-radius: 8rpx 22rpx 8rpx 20rpx; }
.stat.s3 { background: #fff; border-radius: 18rpx 10rpx 6rpx 16rpx; }
.stat.s4 { background: #b8f000; border-radius: 6rpx 16rpx 22rpx 8rpx; }
.stat.s4.off { background: #e2e8f0; }
.stat.s4.full { background: #ff9f1c; }
.num { display: block; font-size: 28rpx; font-weight: 900; }
.label { font-size: 18rpx; font-weight: 700; }
.tags { display: flex; flex-wrap: wrap; gap: 10rpx; margin-top: 20rpx; }
.tag {
  font-size: 22rpx; font-weight: 800; color: #111827;
  padding: 6rpx 14rpx; border: 4rpx solid #111827;
}
.tag.t0 { background: #00d4ff; transform: rotate(-3deg); }
.tag.t1 { background: #ffd60a; transform: rotate(2deg); }
.tag.t2 { background: #b8f000; transform: rotate(-1deg); }
.block {
  margin: 20rpx 24rpx; background: #fff; padding: 24rpx;
  border: 6rpx solid #111827;
  border-radius: 16rpx 28rpx 12rpx 24rpx;
  box-shadow: 8rpx 8rpx 0 #111827;
  position: relative; z-index: 1;
}
.voice-block { background: #7c3aed; }
.voice-block .block-title { color: #fff; }
.block-title { display: block; font-size: 28rpx; font-weight: 900; margin-bottom: 16rpx; color: #111827; }
.voice-btn {
  height: 80rpx; line-height: 80rpx; text-align: center;
  background: #ffd60a; color: #111827; font-size: 28rpx; font-weight: 900;
  border: 6rpx solid #111827; box-shadow: 6rpx 6rpx 0 #111827;
  border-radius: 999rpx 16rpx 999rpx 16rpx;
}
.voice-btn.playing { background: #ff4544; color: #fff; }
.btn-press { transform: translate(4rpx, 4rpx); box-shadow: 2rpx 2rpx 0 #111827; }
.bio { font-size: 26rpx; color: #111827; line-height: 1.65; font-weight: 500; }
.gallery { display: flex; flex-wrap: wrap; gap: 12rpx; }
.g-img {
  width: 200rpx; height: 200rpx; background: #e2e8f0;
  border: 4rpx solid #111827;
}
.g-img.g0 { border-radius: 28rpx 8rpx 20rpx 10rpx; }
.g-img.g1 { border-radius: 10rpx 32rpx 8rpx 24rpx; }
.g-img.g2 { border-radius: 8rpx 16rpx 32rpx 12rpx; }
.empty { color: #64748b; font-size: 26rpx; }
.review { padding: 16rpx 0; border-bottom: 4rpx solid #111827; }
.review-head { display: flex; align-items: center; justify-content: space-between; gap: 12rpx; }
.review-name {
  flex: 1; min-width: 0;
  font-size: 26rpx; font-weight: 800; color: #111827;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.star-row { display: flex; gap: 6rpx; flex-shrink: 0; }
.star-box {
  width: 22rpx; height: 22rpx; background: #e2e8f0; border: 3rpx solid #111827;
  transform: rotate(12deg);
}
.star-box.on { background: #ffd60a; }
.content { display: block; margin-top: 8rpx; font-size: 26rpx; color: #111827; }
.more { text-align: center; color: #111827; font-weight: 800; padding-top: 16rpx; }
.bottom {
  position: fixed; left: 0; right: 0; bottom: 0; z-index: 5;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: #fff4e8; border-top: 6rpx solid #111827;
}
.tip { display: block; text-align: center; font-size: 22rpx; font-weight: 800; color: #111827; margin-bottom: 8rpx; }
.btn {
  height: 88rpx; line-height: 88rpx; text-align: center;
  background: #ff4544; color: #fff; font-size: 30rpx; font-weight: 900;
  border: 6rpx solid #111827; box-shadow: 8rpx 8rpx 0 #111827;
  border-radius: 18rpx 40rpx 18rpx 40rpx;
}
.btn.disabled { background: #cbd5e1; color: #64748b; box-shadow: none; }
.bottom-spacer { height: 8rpx; }
</style>
