<template>
  <view class="chat-bubble" :class="{ 'is-self': isSelf }">
    <!-- 自己的头像：有则用图，无则显示昵称首字 -->
    <image v-if="isSelf && selfAvatar" class="avatar" :src="selfAvatar" mode="aspectFill" lazy-load />
    <view v-else-if="isSelf" class="avatar avatar-placeholder">{{ firstChar(selfName) }}</view>
    <!-- 对方头像：有则用图，无则显示名字首字 -->
    <image v-else-if="avatar" class="avatar" :src="avatar" mode="aspectFill" lazy-load />
    <view v-else class="avatar avatar-placeholder">{{ firstChar(name) }}</view>
    <view class="bubble-content" :class="{ self: isSelf }">
      <text v-if="msg.type === 'TEXT'">{{ msg.content }}</text>
      <image v-else-if="msg.type === 'IMAGE'" class="msg-image" :src="msg.content" mode="widthFix" lazy-load @click="previewImage" />
      <!-- 商品卡片 -->
      <view v-else-if="msg.type === 'PRODUCT'" class="card-msg product-card" @click="goProduct(cardData)">
        <image class="card-cover" :src="cardData.coverImage || cardData.image" mode="aspectFill" lazy-load />
        <view class="card-info">
          <text class="card-name text-ellipsis">{{ cardData.name }}</text>
          <text class="card-price">¥{{ Number(cardData.price || 0).toFixed(2) }}</text>
        </view>
        <view class="card-tag">商品</view>
      </view>
      <!-- 订单卡片 -->
      <view v-else-if="msg.type === 'ORDER'" class="card-msg order-card" @click="goOrder(cardData)">
        <view class="card-info" style="flex:1">
          <text class="card-name text-ellipsis">{{ cardData.productName || cardData.orderNo }}</text>
          <view class="card-meta">
            <text class="card-price">¥{{ Number(cardData.amount || 0).toFixed(2) }}</text>
            <text class="card-status">{{ statusText }}</text>
          </view>
          <text class="card-order-no">{{ cardData.orderNo }}</text>
        </view>
        <view class="card-tag">订单</view>
      </view>
      <text v-else class="system-msg">{{ msg.content }}</text>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'
import { ORDER_STATUS_TEXT } from '@/utils/constants'
import { useSiteStore } from '@/store/site'

const props = defineProps({
  msg: { type: Object, required: true },
  isSelf: { type: Boolean, default: false },
  avatar: { type: String, default: '' },
  selfAvatar: { type: String, default: '' },
  name: { type: String, default: '' },
  selfName: { type: String, default: '' }
})

const siteStore = useSiteStore()

function firstChar(str) {
  if (!str || typeof str !== 'string') return '?'
  const s = str.trim()
  return s.length ? s[0] : '?'
}

const cardData = computed(() => {
  if (props.msg.type === 'PRODUCT' || props.msg.type === 'ORDER') {
    try { return JSON.parse(props.msg.content) } catch { return {} }
  }
  return {}
})

const statusText = computed(() => ORDER_STATUS_TEXT[cardData.value?.status] || cardData.value?.status || '')

function previewImage() {
  uni.previewImage({ urls: [props.msg.content], current: 0 })
}
function goProduct(d) {
  if (siteStore.configLoaded && siteStore.isUnderReview) return
  if (d?.id) uni.navigateTo({ url: '/pages/product/detail?id=' + d.id })
}
function goOrder(d) {
  if (siteStore.configLoaded && siteStore.isUnderReview) return
  if (d?.id) uni.navigateTo({ url: '/pages/order/detail?id=' + d.id })
}
</script>

<style lang="scss" scoped>
.chat-bubble {
  display: flex;
  align-items: flex-start;
  margin-bottom: 24rpx;
  padding: 0 24rpx;

  &.is-self {
    flex-direction: row-reverse;
  }

  .avatar {
    width: 72rpx;
    height: 72rpx;
    border-radius: 50%;
    flex-shrink: 0;
  }
  .avatar-placeholder {
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(135deg, #94a3b8, #64748b);
    color: #fff;
    font-size: 32rpx;
    font-weight: bold;
  }

  .bubble-content {
    max-width: 70%;
    margin: 0 16rpx;
    padding: 20rpx 24rpx;
    background: #e2e8f0;
    border: 1rpx solid #f1f5f9;
    border-radius: 12rpx;
    font-size: 28rpx;
    color: #1e293b;
    word-break: break-all;

    &.self {
      background: linear-gradient(135deg, #ff4544, #e63939);
      border-color: transparent;
      color: #1e293b;

      .card-msg { background: #f1f5f9; border-color: #e2e8f0; }
      .card-name { color: #1e293b; }
      .card-price { color: #ff6b2b; }
      .card-status { color: #64748b; }
      .card-order-no { color: #64748b; }
      .card-tag { background: #e2e8f0; color: #64748b; }
    }

    .msg-image {
      max-width: 400rpx;
      border-radius: 8rpx;
    }

    .system-msg {
      color: #94a3b8;
      font-size: 24rpx;
    }
  }
}

/* 卡片消息通用样式 */
.card-msg {
  display: flex;
  padding: 0;
  background: #f1f5f9;
  border: 1rpx solid #e2e8f0;
  border-radius: 12rpx;
  overflow: hidden;
  position: relative;
  min-width: 400rpx;
}
.card-cover {
  width: 160rpx;
  height: 160rpx;
  flex-shrink: 0;
}
.card-info {
  flex: 1;
  padding: 16rpx;
  display: flex;
  flex-direction: column;
  justify-content: center;
  overflow: hidden;
}
.card-name {
  font-size: 26rpx;
  color: #1e293b;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-price {
  font-size: 28rpx;
  color: #ff6b2b;
  font-weight: bold;
  margin-top: 8rpx;
}
.card-meta {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 8rpx;
}
.card-status {
  font-size: 22rpx;
  color: #94a3b8;
}
.card-order-no {
  font-size: 20rpx;
  color: #94a3b8;
  margin-top: 4rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.card-tag {
  position: absolute;
  top: 0;
  right: 0;
  font-size: 18rpx;
  padding: 2rpx 12rpx;
  background: #e2e8f0;
  color: #6366f1;
  border-radius: 0 12rpx 0 8rpx;
}
</style>
