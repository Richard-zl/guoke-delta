<template>
  <view class="player-card" @click="$emit('click', player)">
    <image class="avatar" :src="player.avatar" mode="aspectFill" lazy-load />
    <view class="info">
      <text class="name">{{ player.nickname }}</text>
      <view class="meta">
        <text v-if="player.level" class="level">Lv.{{ player.level }}</text>
        <text class="rating">⭐{{ player.avgRating || '暂无' }}</text>
        <text class="orders">接单{{ player.completedOrders || 0 }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
defineProps({
  player: { type: Object, required: true }
})
defineEmits(['click'])
</script>

<style lang="scss" scoped>
.player-card {
  display: flex;
  align-items: center;
  background: #f1f5f9;
  border: 1rpx solid #e2e8f0;
  padding: 24rpx;
  border-radius: 12rpx;
  margin-bottom: 16rpx;

  .avatar {
    width: 88rpx;
    height: 88rpx;
    border-radius: 50%;
    margin-right: 20rpx;
    border: 2rpx solid #cbd5e1;
  }

  .info {
    flex: 1;

    .name {
      font-size: 30rpx;
      color: #1e293b;
      font-weight: 500;
    }

    .meta {
      margin-top: 8rpx;
      font-size: 24rpx;
      color: #94a3b8;
      display: flex;
      gap: 20rpx;

      .level {
        color: #ff9900;
        font-weight: 500;
      }
    }
  }
}
</style>
