<template>
  <view
    class="coupon-card"
    :class="{ disabled, selected, compact }"
    @click="$emit('click')"
  >
    <view class="card-left">
      <view class="amount-wrap">
        <text class="amount-num">{{ amountNum }}</text>
        <text class="amount-unit">{{ isCashCoupon(coupon) ? '元' : '折' }}</text>
      </view>
      <text class="condition-text">{{ coupon.minAmount > 0 ? `满${coupon.minAmount}元可用` : '无门槛' }}</text>
    </view>
    <view class="divider">
      <view class="notch top" />
      <view class="dash-line" />
      <view class="notch bottom" />
    </view>
    <view class="card-right">
      <text class="coupon-name">{{ coupon.couponName }}</text>
      <text class="coupon-type-tag">{{ getCouponTypeLabel(coupon) }}</text>
      <text class="expire-text">有效期至 {{ formatCouponExpire(coupon.expireTime) }}</text>
    </view>
    <view v-if="selected" class="selected-check">✓</view>
  </view>
</template>

<script setup>
import { computed } from 'vue'
import { isCashCoupon, getCouponAmountNum, getCouponTypeLabel, formatCouponExpire } from '@/utils/coupon'

const props = defineProps({
  coupon: { type: Object, required: true },
  selected: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  compact: { type: Boolean, default: false }
})

defineEmits(['click'])

const amountNum = computed(() => getCouponAmountNum(props.coupon))
</script>

<style lang="scss" scoped>
$red: #ff4544;
$red-light: #fff1f0;
$gray: #94a3b8;
$text-dark: #1e293b;

.coupon-card {
  display: flex;
  align-items: stretch;
  background: #fff;
  border-radius: 20rpx;
  margin-bottom: 24rpx;
  overflow: visible;
  box-shadow: 0 6rpx 24rpx rgba(255, 69, 68, 0.08);
  position: relative;
  border: 2rpx solid transparent;

  &.selected {
    border-color: $red;
    box-shadow: 0 6rpx 24rpx rgba(255, 69, 68, 0.18);
  }

  &.disabled {
    box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04);
    .card-left { background: linear-gradient(145deg, #cbd5e1, #94a3b8); }
    .amount-num, .amount-unit { color: #fff; }
    .condition-text { color: rgba(255,255,255,0.7); }
  }

  &.compact {
    margin-bottom: 16rpx;
    .amount-num { font-size: 56rpx; }
  }
}

.card-left {
  width: 210rpx;
  flex-shrink: 0;
  background: linear-gradient(145deg, #ff6b6a, $red);
  border-radius: 20rpx 0 0 20rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 32rpx 16rpx;
  box-sizing: border-box;
}

.amount-wrap {
  display: flex;
  align-items: baseline;
  gap: 4rpx;
}

.amount-num {
  font-size: 72rpx;
  font-weight: 800;
  color: #fff;
  line-height: 1;
}

.amount-unit {
  font-size: 28rpx;
  font-weight: 600;
  color: rgba(255,255,255,0.9);
  padding-bottom: 4rpx;
}

.condition-text {
  font-size: 20rpx;
  color: rgba(255,255,255,0.75);
  margin-top: 10rpx;
  text-align: center;
}

.divider {
  width: 20rpx;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  background: #fff;

  .notch {
    width: 28rpx;
    height: 28rpx;
    background: #f4f6f9;
    border-radius: 50%;
    flex-shrink: 0;
    position: relative;
    z-index: 1;
    &.top { margin-top: -14rpx; }
    &.bottom { margin-bottom: -14rpx; }
  }

  .dash-line {
    flex: 1;
    width: 2rpx;
    background: repeating-linear-gradient(to bottom, #e2e8f0 0, #e2e8f0 8rpx, transparent 8rpx, transparent 16rpx);
  }
}

.card-right {
  flex: 1;
  min-width: 0;
  padding: 28rpx 24rpx 28rpx 16rpx;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 10rpx;
}

.coupon-name {
  font-size: 30rpx;
  font-weight: 700;
  color: $text-dark;
}

.coupon-type-tag {
  display: inline-block;
  font-size: 20rpx;
  color: $red;
  background: $red-light;
  border-radius: 6rpx;
  padding: 4rpx 12rpx;
  align-self: flex-start;
}

.expire-text {
  font-size: 22rpx;
  color: $gray;
}

.selected-check {
  position: absolute;
  right: 20rpx;
  top: 20rpx;
  width: 40rpx;
  height: 40rpx;
  background: $red;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 24rpx;
  font-weight: bold;
}
</style>
