<template>
  <view class="pay-page">
    <view class="amount-area">
      <text class="label">支付金额</text>
      <PriceText :value="finalAmount" :size="60" />
      <text v-if="appliedCouponName && discountAmount > 0" class="discount-info">
        {{ appliedCouponName }} 已优惠 ¥{{ discountAmount }}
      </text>
      <text v-if="countdown > 0" class="countdown">支付剩余时间 {{ formatCountdown }}</text>
      <text v-else-if="expired" class="countdown expired">订单已超时</text>
    </view>

    <!-- 优惠券：下单时已绑定，支付页只读展示 -->
    <view v-if="appliedCouponName" class="coupon-section readonly">
      <view class="coupon-left">
        <text class="coupon-label">优惠券</text>
        <text class="coupon-value">{{ appliedCouponName }}</text>
      </view>
      <view class="coupon-right">
        <text class="discount-tag">-¥{{ discountAmount }}</text>
      </view>
    </view>

    <view class="pay-methods">
      <view v-if="canUseWechatPay" class="method" :class="{ active: payType === 'WECHAT' }" @click="payType='WECHAT'">
        <image class="method-icon" src="/static/icons/钞票.svg" mode="aspectFit" />
        <text class="method-name">联系客服支付</text>
        <view class="radio" :class="{ checked: payType === 'WECHAT' }" />
      </view>
      <view class="method" :class="{ active: payType === 'BALANCE' }" @click="payType='BALANCE'">
        <image class="method-icon" src="/static/icons/理财.svg" mode="aspectFit" />
        <view class="method-info">
          <text class="method-name">余额支付</text>
          <text class="balance-text">余额：¥{{ walletBalance }}</text>
        </view>
        <view class="radio" :class="{ checked: payType === 'BALANCE' }" />
      </view>
    </view>

    <view class="btn-pay" :class="{ disabled: expired || submitting }" @click="handlePay">{{ submitting ? '处理中...' : '确认支付' }}</view>
  </view>
</template>

<script setup>
import { ref, computed, onUnmounted } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import PriceText from '@/components/PriceText.vue'
import { balancePay, getPayKfToken } from '@/api/pay'
import { getOrderDetail } from '@/api/order'
import { getWallet } from '@/api/user'
import { requestOrderSubscribe } from '@/utils/subscribe'
import { isMpWeixin } from '@/utils/platform'
import { blockIfUnderReview } from '@/composables/useAuditGuard'
import { useWeworkCs } from '@/composables/useWeworkCs'

const { openWeworkCs } = useWeworkCs()

const orderId = ref(0)
const finalAmount = ref(0)
const discountAmount = ref(0)
const appliedCouponName = ref('')
const canUseWechatPay = isMpWeixin()
const payType = ref(canUseWechatPay ? 'WECHAT' : 'BALANCE')
const walletBalance = ref('0.00')
const countdown = ref(0)
const expired = ref(false)
const submitting = ref(false)
let timer = null

const formatCountdown = computed(() => {
  const m = Math.floor(countdown.value / 60)
  const s = countdown.value % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})

onLoad(async (opts) => {
  if (await blockIfUnderReview()) return
  orderId.value = opts.orderId
  finalAmount.value = parseFloat(opts.amount) || 0
  discountAmount.value = parseFloat(opts.discountAmount) || 0
  appliedCouponName.value = opts.couponName ? decodeURIComponent(opts.couponName) : ''

  try {
    const orderRes = await getOrderDetail(orderId.value)
    const order = orderRes.data
    if (order) {
      finalAmount.value = order.amount || finalAmount.value
      // 从订单详情补全优惠券信息（如从订单详情页「去支付」进入）
      if (!appliedCouponName.value && order.couponName) {
        appliedCouponName.value = order.couponName
        discountAmount.value = Number(order.couponDiscountAmount || 0)
      }
      if (order.status !== 'PENDING_PAYMENT') {
        handleOrderNotPending(order.status)
        return
      }
      if (order.payDeadline) {
        const deadlineMs = new Date(order.payDeadline.replace(' ', 'T')).getTime()
        const remainSec = Math.floor((deadlineMs - Date.now()) / 1000)
        countdown.value = remainSec > 0 ? remainSec : 0
      } else {
        countdown.value = 1800
      }
    }
  } catch (e) {
    countdown.value = 1800
  }

  if (countdown.value <= 0) {
    expired.value = true
  } else {
    startCountdown()
  }

  try {
    const w = await getWallet()
    walletBalance.value = Number(w.data?.balance || 0).toFixed(2)
  } catch (e) { /* ignore */ }
})

onShow(async () => {
  if (await blockIfUnderReview()) return
})

function startCountdown() {
  timer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      clearInterval(timer)
      expired.value = true
    }
  }, 1000)
}

function handleOrderNotPending(status) {
  const msg = status === 'PAID' ? '该订单已支付' : status === 'CANCELLED' ? '订单已取消' : '订单状态已变更'
  uni.showModal({
    title: '提示',
    content: msg,
    showCancel: false,
    success: () => uni.redirectTo({ url: `/pages/order/detail?id=${orderId.value}` })
  })
}

onUnmounted(() => { if (timer) clearInterval(timer) })

async function handlePay() {
  if (submitting.value) return
  if (expired.value) return uni.showToast({ title: '订单已超时', icon: 'none' })
  await requestOrderSubscribe()

  submitting.value = true
  try {
    if (payType.value === 'BALANCE') {
      // 优惠券已在下单时绑定，支付接口不再传 couponId
      await balancePay(orderId.value)
      uni.showToast({ title: '支付成功' })
      setTimeout(() => uni.redirectTo({ url: `/pages/order/detail?id=${orderId.value}` }), 1500)
    } else {
      if (!canUseWechatPay) {
        return uni.showToast({ title: '请使用余额支付', icon: 'none' })
      }
      // 小程序支付能力受限，微信支付改为「联系客服」：签发 payToken → 客服会话自动推送 H5 支付链接
      const res = await getPayKfToken(orderId.value)
      await openWeworkCs({ scene: 'pay', payToken: res.data.token, order: { id: orderId.value } })
    }
  } catch (e) {
    if (e && e.code === 4010) {
      handleAlreadyPaid()
    } else {
      uni.showToast({ title: e?.msg || '支付失败', icon: 'none' })
    }
  } finally {
    submitting.value = false
  }
}

function handleAlreadyPaid() {
  uni.showModal({
    title: '提示',
    content: '该订单已支付，请勿重复支付',
    showCancel: false,
    success: () => uni.redirectTo({ url: `/pages/order/detail?id=${orderId.value}` })
  })
}
</script>

<style lang="scss" scoped>
.pay-page { padding: 40rpx 24rpx; background: #f1f5f9; min-height: 100vh; }
.amount-area { text-align: center; padding: 60rpx 0;
  .label { font-size: 28rpx; color: #64748b; display: block; margin-bottom: 20rpx; }
  .discount-info { font-size: 24rpx; color: #ff4544; display: block; margin-top: 8rpx; }
  .countdown { font-size: 26rpx; color: #ff9900; display: block; margin-top: 16rpx; &.expired { color: #ee0a24; } }
}

.coupon-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  padding: 24rpx;
  border-radius: 12rpx;
  margin-bottom: 24rpx;

  &.readonly { opacity: 1; }

  .coupon-left {
    flex: 1;
    .coupon-label { font-size: 28rpx; color: #1e293b; font-weight: 500; display: block; }
    .coupon-value { font-size: 24rpx; color: #94a3b8; margin-top: 8rpx; display: block; }
  }

  .coupon-right {
    .discount-tag { color: #ff4544; font-size: 28rpx; font-weight: 600; }
  }
}

.pay-methods { background: #ffffff; border: 1rpx solid #e2e8f0; border-radius: 12rpx; overflow: hidden; margin-bottom: 60rpx; }
.method { display: flex; align-items: center; padding: 32rpx 24rpx; border-bottom: 1rpx solid #f1f5f9; gap: 16rpx;
  .method-icon { width: 40rpx; height: 40rpx; flex-shrink: 0; }
  .method-name { font-size: 28rpx; color: #1e293b; flex: 1; }
  .method-info { flex: 1; .balance-text { font-size: 22rpx; color: #94a3b8; display: block; margin-top: 4rpx; } }
  .radio { width: 36rpx; height: 36rpx; border: 2rpx solid #cbd5e1; border-radius: 50%; &.checked { border-color: #ff4544; background: #ff4544; } }
}
.btn-pay { height: 88rpx; line-height: 88rpx; text-align: center; background: linear-gradient(135deg, #ff4544, #e63939); color: #ffffff; font-weight: bold; font-size: 32rpx; border-radius: 999rpx; &.disabled { background: #e2e8f0; color: #94a3b8; } }
</style>
