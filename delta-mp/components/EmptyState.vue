<template>
  <view class="empty-state">
    <image v-if="image" class="empty-image" :src="image" mode="aspectFit" />
    <text class="empty-text">{{ text }}</text>
    <view v-if="buttonText" class="empty-btn" @click="$emit('action')">
      {{ buttonText }}
    </view>
  </view>
</template>

<script setup>
defineProps({
  text: { type: String, default: '暂无数据' },
  image: { type: String, default: '/static/icons/空空如也.svg' },
  buttonText: { type: String, default: '' }
})
defineEmits(['action'])
</script>

<style lang="scss" scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 120rpx 0;

  .empty-image {
    width: 240rpx;
    height: 240rpx;
    margin-bottom: 24rpx;
    opacity: 0.6;
  }

  .empty-text {
    font-size: 28rpx;
    color: #94a3b8;
  }

  .empty-btn {
    margin-top: 32rpx;
    padding: 16rpx 48rpx;
    font-size: 28rpx;
    color: #fff;
    background: linear-gradient(135deg, #ff4544, #e63939);
    border: none;
    border-radius: 999rpx;
  }
}
</style>
