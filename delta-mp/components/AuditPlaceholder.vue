<template>
  <view class="audit-placeholder">
    <image class="icon" src="/static/icons/暂无权限.svg" mode="aspectFit" />
    <text class="title">{{ title }}</text>
    <text class="desc">{{ description }}</text>
    <view class="btn" @click="goHome">返回首页</view>
  </view>
</template>

<script setup>
defineProps({
  title: { type: String, default: '功能筹备中' },
  description: {
    type: String,
    default: '当前版本仅提供资讯浏览，该功能暂未开放。'
  }
})

function goHome() {
  uni.switchTab({ url: '/pages/index/index' })
}
</script>

<style lang="scss" scoped>
.audit-placeholder {
  min-height: 100vh;
  padding: 120rpx 48rpx;
  background: #f1f5f9;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-sizing: border-box;
}
.icon {
  width: 120rpx;
  height: 120rpx;
  opacity: 0.5;
  margin-bottom: 32rpx;
}
.title {
  font-size: 34rpx;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 16rpx;
}
.desc {
  font-size: 26rpx;
  color: #64748b;
  line-height: 1.6;
  text-align: center;
  margin-bottom: 48rpx;
}
.btn {
  padding: 20rpx 64rpx;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #ff4544, #e63939);
  color: #fff;
  font-size: 28rpx;
}
</style>
