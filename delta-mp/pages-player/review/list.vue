<template>
  <AuditPlaceholder v-if="pageBlocked" />
  <view v-else class="review-page">
    <scroll-view scroll-y class="list" @scrolltolower="loadMore">
      <view v-for="r in reviews" :key="r.id" class="review-card">
        <view class="card-top">
          <image class="avatar" :src="r.userAvatar || '/static/images/default-avatar.png'" mode="aspectFill" lazy-load />
          <view class="user-info">
            <text class="name">{{ r.userNickname || '匿名用户' }}</text>
            <text class="time">{{ r.createdAt }}</text>
          </view>
          <StarRating :model-value="r.rating" readonly />
        </view>
        <text class="content">{{ r.content }}</text>
        <view v-if="r.images" class="images">
          <image v-for="(img, i) in r.images.split(',')" :key="i" :src="img" mode="aspectFill" class="img" lazy-load @click="previewImg(r.images.split(','), i)" />
        </view>
        <view class="order-info">
          <text class="order-label">订单：{{ r.orderNo || r.orderId }}</text>
          <text class="product">{{ r.productName }}</text>
        </view>
        <!-- 接单员回复 -->
        <view v-if="r.reply" class="reply-box">
          <text class="reply-label">我的回复：</text>
          <text class="reply-content">{{ r.reply }}</text>
        </view>
      </view>
      <view v-if="loading" class="loading-tip">加载中...</view>
<EmptyState v-if="!loading && reviews.length === 0" text="暂无评价" image="/static/icons/暂无纪录.svg" />
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
import StarRating from '@/components/StarRating.vue'
import { getMyReviews } from '@/api/review'

const reviews = ref([])
const pageNum = ref(1)
const loading = ref(false)
const finished = ref(false)

onLoad(() => { loadData() })

async function loadData() {
  loading.value = true
  const res = await getMyReviews({ pageNum: pageNum.value, pageSize: 20 })
  const list = res.data?.records || []
  if (list.length < 20) finished.value = true
  reviews.value = pageNum.value === 1 ? list : [...reviews.value, ...list]
  loading.value = false
}
function loadMore() { if (!loading.value && !finished.value) { pageNum.value++; loadData() } }
function previewImg(imgs, idx) { uni.previewImage({ urls: imgs, current: idx }) }
</script>
<style lang="scss" scoped>
.review-page { background: #ffffff; min-height: 100vh; }
.list { height: 100vh; padding: 20rpx 24rpx; }
.review-card { background: #f1f5f9; border-radius: 12rpx; padding: 24rpx; margin-bottom: 16rpx;
  .card-top { display: flex; align-items: center; gap: 12rpx; margin-bottom: 16rpx; }
  .avatar { width: 64rpx; height: 64rpx; border-radius: 50%; }
  .user-info { flex: 1; .name { font-size: 26rpx; font-weight: bold; display: block; } .time { font-size: 22rpx; color: #94a3b8; display: block; } }
  .content { font-size: 28rpx; color: #1e293b; line-height: 1.6; display: block; margin-bottom: 12rpx; }
  .images { display: flex; gap: 12rpx; margin-bottom: 12rpx; .img { width: 160rpx; height: 160rpx; border-radius: 8rpx; } }
  .order-info { font-size: 22rpx; color: #94a3b8; padding: 12rpx 0; border-top: 1rpx solid #f1f5f9; .order-label { margin-right: 16rpx; } }
  .reply-box { background: rgba(0,0,0,0.05); padding: 16rpx; border-radius: 8rpx; margin-top: 12rpx;
    .reply-label { font-size: 24rpx; color: #ff4544; } .reply-content { font-size: 24rpx; color: #64748b; }
  }
}
.loading-tip { text-align: center; padding: 32rpx; font-size: 24rpx; color: #94a3b8; }
</style>
