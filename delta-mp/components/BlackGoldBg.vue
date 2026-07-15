<template>
  <view class="bg-layer">
    <view class="bg-glow bg-glow-1" />
    <view class="bg-glow bg-glow-2" />
    <view class="bg-glow bg-glow-3" />
  </view>
</template>

<style lang="scss" scoped>
.bg-layer {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  z-index: 0;
  overflow: hidden;
  pointer-events: none;
}
.bg-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(180rpx);
  opacity: 0.12;
}
.bg-glow-1 {
  width: 600rpx; height: 600rpx;
  background: radial-gradient(circle, rgba(139, 92, 246, 0.25) 0%, transparent 70%);
  top: -100rpx; left: -100rpx;
  animation: drift1 8s ease-in-out infinite alternate;
}
.bg-glow-2 {
  width: 500rpx; height: 500rpx;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.2) 0%, transparent 70%);
  bottom: 200rpx; right: -120rpx;
  animation: drift2 10s ease-in-out infinite alternate;
}
.bg-glow-3 {
  width: 400rpx; height: 400rpx;
  background: radial-gradient(circle, rgba(79, 70, 229, 0.15) 0%, transparent 70%);
  top: 50%; left: 40%;
  animation: drift3 12s ease-in-out infinite alternate;
}
@keyframes drift1 {
  0%   { transform: translate(0, 0) scale(1); opacity: 0.08; }
  100% { transform: translate(200rpx, 300rpx) scale(1.2); opacity: 0.15; }
}
@keyframes drift2 {
  0%   { transform: translate(0, 0) scale(1); opacity: 0.06; }
  100% { transform: translate(-250rpx, -200rpx) scale(1.3); opacity: 0.12; }
}
@keyframes drift3 {
  0%   { transform: translate(0, 0) scale(0.8); opacity: 0.05; }
  100% { transform: translate(-150rpx, 150rpx) scale(1.1); opacity: 0.1; }
}
</style>
