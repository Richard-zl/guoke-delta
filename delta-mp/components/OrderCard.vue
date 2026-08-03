<template>
  <view class="order-card" :class="{ 'order-card--embed': embed }" @click="goDetail">
    <!-- 顶部：商品名 + 状态 -->
    <view class="card-top">
      <text class="product-name">{{ order.productName }}</text>
      <StatusTag :status="order.status" />
    </view>
    <!-- 中间：规格 + 订单号 -->
    <view class="card-mid">
      <text v-if="order.variantName || order.specInfo" class="spec">{{ order.variantName || order.specInfo }}</text>
      <text v-if="order.quantity > 1" class="spec">× {{ order.quantity }}</text>
      <text class="order-no">{{ order.orderNo }}</text>
    </view>
    <!-- 接单员信息（用户端看到） -->
    <view v-if="order.playerId && !isPlayerView" class="person-row">
      <image class="person-avatar" :src="order.playerAvatar || '/static/images/default-avatar.png'" mode="aspectFill" lazy-load />
      <text class="person-name">{{ order.playerName || '接单员' + order.playerId }}</text>
      <text class="person-label">服务人员</text>
    </view>
    <!-- 用户信息（接单员端看到） -->
    <view v-if="order.userNickname && isPlayerView" class="person-row">
      <image class="person-avatar" :src="order.userAvatar || '/static/images/default-avatar.png'" mode="aspectFill" lazy-load />
      <text class="person-name">{{ order.userNickname }}</text>
      <text class="person-label">下单用户</text>
    </view>
    <!-- 分割线 -->
    <view class="divider" />
    <!-- 底部：价格 + 操作按钮 -->
    <view class="card-bottom">
      <view class="left">
        <text class="amount-label">¥</text>
        <text class="amount">{{ Number(order.amount).toFixed(2) }}</text>
        <text class="time">{{ formatTime(order.createdAt) }}</text>
      </view>
      <view class="right">
        <view v-if="showChat" class="btn-chat" @click.stop="goChat">💬 聊天</view>
        <view v-if="actionText" class="action-hint" @click.stop="goDetail">{{ actionText }}</view>
      </view>
    </view>
    <!-- 接单员端：到手金额 + 抽佣比例 -->
    <view v-if="isPlayerView" class="income-row">
      <text class="income-label">到手金额：</text>
      <text class="income-value">¥{{ playerIncomeAmount.toFixed(2) }}</text>
      <text class="income-hint">（抽佣比例 {{ playerCommissionPercent }}%）</text>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'
import StatusTag from './StatusTag.vue'
import { useSiteStore } from '@/store/site'

const props = defineProps({
  order: { type: Object, required: true },
  detailPath: { type: String, default: '/pages/order/detail' },
  /** 嵌入模式：无背景/边框/外边距，由外层作为卡片容器 */
  embed: { type: Boolean, default: false }
})

const siteStore = useSiteStore()

const isPlayerView = computed(() => props.detailPath.includes('pages-player'))

/** 订单抽佣比例（0~1），仅用订单字段，无则按 0 */
const playerCommissionRateValue = computed(() => {
  const o = props.order
  if (o && (o.commissionRate === 0 || (o.commissionRate != null && o.commissionRate !== ''))) {
    return Number(o.commissionRate)
  }
  return 0
})
const playerCommissionPercent = computed(() => Math.round(playerCommissionRateValue.value * 100))
const playerIncomeAmount = computed(() => {
  const n = Number(props.order?.amount) || 0
  return n * (1 - playerCommissionRateValue.value)
})

// 有接单员且处于服务进行中的状态才显示聊天按钮（审核期隐藏）
const showChat = computed(() => {
  if (siteStore.configLoaded && siteStore.isUnderReview) return false
  if (!props.order.playerId) return false
  const chatStatuses = ['ACCEPTED', 'WAITING_TEAMMATE', 'IN_PROGRESS', 'COMPLETED', 'CONFIRMED']
  return chatStatuses.includes(props.order.status)
})

const actionText = computed(() => {
  if (isPlayerView.value) {
    const playerMap = {
      ASSIGNED: '接单',
      ACCEPTED: '开始服务',
      IN_PROGRESS: '提交进度'
    }
    return playerMap[props.order.status] || ''
  }
  const map = {
    PENDING_PAYMENT: '去支付',
    COMPLETED: '确认完成',
    CONFIRMED: '去评价'
  }
  return map[props.order.status] || ''
})

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

function goDetail() {
  if (siteStore.configLoaded && siteStore.isUnderReview) return
  uni.navigateTo({ url: `${props.detailPath}?id=${props.order.id}` })
}

function goChat() {
  const chatPath = isPlayerView.value ? '/pages-player/chat/room' : '/pages/chat/room'
  uni.navigateTo({ url: `${chatPath}?orderId=${props.order.id}` })
}
</script>

<style lang="scss" scoped>
.order-card {
  background: #f1f5f9;
  border: 1rpx solid #e2e8f0;
  border-radius: 16rpx;
  padding: 28rpx 28rpx 24rpx;
  margin-bottom: 20rpx;
  &.order-card--embed {
    background: transparent;
    border: none;
    border-radius: 0;
    margin-bottom: 0;
    padding-bottom: 16rpx;
  }
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  .product-name {
    flex: 1;
    font-size: 30rpx;
    font-weight: bold;
    color: #1e293b;
    margin-right: 16rpx;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.card-mid {
  margin-top: 12rpx;
  .spec {
    display: inline-block;
    font-size: 24rpx;
    color: #64748b;
    background: #e2e8f0;
    padding: 4rpx 14rpx;
    border-radius: 6rpx;
    margin-right: 12rpx;
  }
  .order-no {
    font-size: 22rpx;
    color: #94a3b8;
  }
}

.person-row {
  display: flex;
  align-items: center;
  margin-top: 16rpx;
  padding: 14rpx 16rpx;
  background: #f1f5f9;
  border-radius: 12rpx;
  .person-avatar {
    width: 48rpx;
    height: 48rpx;
    border-radius: 50%;
    margin-right: 12rpx;
    flex-shrink: 0;
  }
  .person-name {
    font-size: 26rpx;
    color: #1e293b;
    font-weight: 500;
    flex: 1;
  }
  .person-label {
    font-size: 20rpx;
    color: #94a3b8;
    flex-shrink: 0;
  }
}

.divider {
  height: 1rpx;
  background: #f1f5f9;
  margin: 20rpx 0 16rpx;
}

.card-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  .left {
    display: flex;
    align-items: baseline;
    gap: 8rpx;
    .amount-label {
      font-size: 24rpx;
      color: #ff4544;
      font-weight: bold;
    }
    .amount {
      font-size: 34rpx;
      color: #ff4544;
      font-weight: bold;
    }
    .time {
      font-size: 22rpx;
      color: #94a3b8;
      margin-left: 8rpx;
    }
  }
  .right {
    display: flex;
    align-items: center;
    gap: 12rpx;
    .btn-chat {
      font-size: 24rpx;
      color: #07c160;
      padding: 6rpx 20rpx;
      border: 1rpx solid rgba(7, 193, 96, 0.5);
      border-radius: 999rpx;
    }
    .action-hint {
      font-size: 24rpx;
      color: #fff;
      font-weight: 500;
      padding: 6rpx 20rpx;
      background: linear-gradient(135deg, #ff4544, #e63939);
      border: none;
      border-radius: 999rpx;
    }
  }
}
.income-row {
  margin-top: 12rpx;
  padding-top: 12rpx;
  border-top: 1rpx solid #e2e8f0;
  .income-label { font-size: 24rpx; color: #64748b; }
  .income-value { font-size: 26rpx; color: #ee0a24; font-weight: bold; }
  .income-hint { font-size: 22rpx; color: #94a3b8; margin-left: 8rpx; }
}
</style>
