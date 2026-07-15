<template>
  <view v-if="visible" class="modal-mask" @click="close">
    <view class="modal-panel" @click.stop>
      <view class="modal-header">
        <text class="modal-title">{{ title }}</text>
        <text class="modal-close" @click="close">✕</text>
      </view>
      <view class="modal-body">
        <image
          v-if="qrcodeUrl"
          class="qrcode-img"
          :src="qrcodeUrl"
          mode="widthFix"
          show-menu-by-longpress
        />
        <text class="tips-text">{{ tips }}</text>
        <view v-if="copyText" class="copy-btn" @click="copyInfo">复制信息</view>
      </view>
    </view>
  </view>
</template>

<script setup>
const props = defineProps({
  visible: { type: Boolean, default: false },
  title: { type: String, default: '联系客服' },
  qrcodeUrl: { type: String, default: '' },
  tips: { type: String, default: '长按识别二维码，添加客服微信' },
  copyText: { type: String, default: '' }
})

const emit = defineEmits(['update:visible'])

function close() {
  emit('update:visible', false)
}

function copyInfo() {
  if (!props.copyText) return
  uni.setClipboardData({
    data: props.copyText,
    success: () => uni.showToast({ title: '已复制，添加客服后粘贴发送', icon: 'none' }),
    fail: () => uni.showToast({ title: '复制失败，请手动记录', icon: 'none' })
  })
}
</script>

<style lang="scss" scoped>
.modal-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
  z-index: 999;
  display: flex;
  align-items: flex-end;
}

.modal-panel {
  width: 100%;
  background: #f4f6f9;
  border-radius: 24rpx 24rpx 0 0;
  padding: 24rpx 32rpx;
  padding-bottom: calc(32rpx + env(safe-area-inset-bottom));
  animation: slideUp 0.3s ease-out;
}

@keyframes slideUp {
  from { transform: translateY(100%); }
  to { transform: translateY(0); }
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24rpx;

  .modal-title {
    font-size: 32rpx;
    font-weight: bold;
    color: #ff4544;
  }

  .modal-close {
    font-size: 40rpx;
    color: #94a3b8;
    padding: 0 8rpx;
  }
}

.modal-body {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.qrcode-img {
  width: 400rpx;
  border-radius: 16rpx;
  background: #fff;
}

.tips-text {
  margin-top: 24rpx;
  font-size: 26rpx;
  color: #64748b;
  text-align: center;
  line-height: 1.5;
}

.copy-btn {
  margin-top: 28rpx;
  padding: 18rpx 48rpx;
  background: linear-gradient(135deg, #ff4544, #e63939);
  color: #fff;
  font-size: 28rpx;
  border-radius: 999rpx;
}
</style>
