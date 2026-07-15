<template>
  <view v-if="visible" class="modal-mask" @click="close">
    <view class="picker-panel" @click.stop>
      <view class="picker-header">
        <text class="picker-title">选择优惠券</text>
        <text class="picker-close" @click="close">✕</text>
      </view>
      <scroll-view scroll-y class="picker-list" :show-scrollbar="false">
        <view class="picker-list-inner">
          <CouponCard
            v-for="coupon in coupons"
            :key="coupon.id"
            :coupon="coupon"
            :selected="modelValue === coupon.id"
            compact
            @click="onSelect(coupon)"
          />
          <view v-if="coupons.length === 0" class="empty-tip">暂无可用优惠券</view>
        </view>
      </scroll-view>
      <view v-if="showClear" class="picker-footer" @click="onClear">
        <text>不使用优惠券</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import CouponCard from '@/components/CouponCard.vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  coupons: { type: Array, default: () => [] },
  modelValue: { type: [Number, String], default: null },
  showClear: { type: Boolean, default: true }
})

const emit = defineEmits(['update:visible', 'update:modelValue', 'select', 'clear'])

function close() {
  emit('update:visible', false)
}

function onSelect(coupon) {
  emit('update:modelValue', coupon.id)
  emit('select', coupon)
  close()
}

function onClear() {
  emit('update:modelValue', null)
  emit('clear')
  close()
}
</script>

<style lang="scss" scoped>
.modal-mask {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.6);
  z-index: 999;
  display: flex;
  align-items: flex-end;
}

.picker-panel {
  width: 100%;
  max-height: 75vh;
  background: #f4f6f9;
  border-radius: 24rpx 24rpx 0 0;
  padding: 24rpx 24rpx 0;
  padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  animation: slideUp 0.3s ease-out;
}

@keyframes slideUp {
  from { transform: translateY(100%); }
  to { transform: translateY(0); }
}

.picker-header {
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 20rpx;
  margin-bottom: 8rpx;

  .picker-title { font-size: 32rpx; font-weight: bold; color: #ff4544; }
  .picker-close { font-size: 40rpx; color: #94a3b8; padding: 0 8rpx; }
}

/* 微信小程序 scroll-view 必须设置明确高度，flex:1 + height:0 会导致列表区域高度为 0 */
.picker-list {
  width: 100%;
  max-height: 55vh;
  min-height: 240rpx;
}

.picker-list-inner {
  padding: 8rpx 4rpx 32rpx;
}

.empty-tip {
  text-align: center;
  padding: 60rpx;
  font-size: 28rpx;
  color: #94a3b8;
}

.picker-footer {
  flex-shrink: 0;
  text-align: center;
  padding: 24rpx;
  font-size: 26rpx;
  color: #94a3b8;
  border-top: 1rpx solid #e2e8f0;
  margin-top: 8rpx;
}
</style>
