<template>
  <view class="product-detail" v-if="product">
    <!-- 轮播图 -->
    <view class="banner-wrap">
      <swiper class="banner" autoplay :interval="3000" circular :current="swiperIdx" @change="e => swiperIdx = e.detail.current">
        <swiper-item v-for="(img,i) in images" :key="i">
          <image :src="img" mode="aspectFill" class="banner-img" lazy-load @click="previewImg(i)" />
        </swiper-item>
      </swiper>
      <view class="banner-dots" v-if="images.length > 1">
        <view v-for="(img,i) in images" :key="i" class="dot" :class="{ active: swiperIdx === i }" />
      </view>
      <view class="img-counter">{{ swiperIdx + 1 }}/{{ images.length }}</view>
    </view>

    <!-- 价格区域 -->
    <view class="price-card">
      <view class="price-main">
        <text class="yen">¥</text>
        <text class="price-int">{{ priceInt }}</text>
        <text class="price-dec">.{{ priceDec }}</text>
      </view>
      <view class="price-extra">
        <text
          v-if="product.trialLimitTip"
          class="limit-tag trial-limit-tag"
        >
          {{ product.trialLimitTip }}
        </text>
        <text
          v-else-if="product.perUserLimitEnabled === 1 && product.perUserLimitCount"
          class="limit-tag"
        >
          限购 {{ product.perUserLimitCount }} 次
        </text>
      </view>
    </view>

    <!-- 商品信息 -->
    <view class="info-card">
      <text class="product-name">{{ product.name }}</text>
      <text v-if="product.subtitle" class="product-subtitle">{{ product.subtitle }}</text>
      <view class="meta-row" v-if="product.categoryName">
        <view class="category-tag">
          <text class="tag-dot">●</text>
          <text class="tag-text">{{ product.categoryName }}</text>
        </view>
      </view>
      <view class="desc-wrap" v-if="product.description">
        <view class="desc-divider"></view>
        <text class="desc">{{ product.description }}</text>
      </view>
    </view>

    <!-- 规格与数量 -->
    <view class="option-card" v-if="hasVariants || quantityEnabled">
      <view v-if="hasVariants" class="option-block">
        <text class="option-label">规格</text>
        <view class="variant-tags">
          <view
            v-for="v in variants"
            :key="v.id"
            class="variant-tag"
            :class="{ active: selectedVariantId === v.id }"
            @click="selectVariant(v)"
          >
            <text class="variant-name">{{ v.name }}</text>
            <text class="variant-price">¥{{ Number(v.price).toFixed(2) }}</text>
          </view>
        </view>
      </view>
      <view v-if="quantityEnabled" class="option-block quantity-block">
        <text class="option-label">数量</text>
        <view class="quantity-stepper">
          <view class="qty-btn" :class="{ disabled: quantity <= 1 }" @click="changeQuantity(-1)">-</view>
          <text class="qty-value">{{ quantity }}{{ unitLabel }}</text>
          <view class="qty-btn" :class="{ disabled: quantity >= maxQuantity }" @click="changeQuantity(1)">+</view>
        </view>
      </view>
    </view>

    <!-- 优惠券区域（体验单不可用券） -->
    <view class="coupon-section" v-if="couponAllowed && availableCoupons.length > 0" @click="showCouponSelector = true">
      <view class="coupon-left">
        <text class="coupon-label">优惠券</text>
        <text class="coupon-value" v-if="selectedCoupon">
          {{ selectedCoupon.couponName }} 省 ¥{{ calculateDiscount(selectedCoupon) }}
        </text>
        <text class="coupon-value" v-else>有{{ availableCoupons.length }}张优惠券可用</text>
      </view>
      <view class="coupon-right">
        <view class="chevron-right" />
      </view>
    </view>

    <!-- 商品详情 -->
    <view class="detail-section" v-if="product.detail">
      <view class="section-header">
        <view class="section-line"></view>
        <text class="section-title">商品详情</text>
        <view class="section-line"></view>
      </view>
      <view class="detail-body">
        <rich-text :nodes="product.detail" />
      </view>
    </view>

    <!-- 底部操作栏 -->
    <view class="bottom-bar">
      <view class="btn-chat" @click="goChat">
        <image class="btn-icon" src="/static/icons/客服.svg" mode="aspectFit" />
        <text>客服</text>
      </view>
      <view class="btn-buy" :class="{ disabled: trialLimitReached }" @click="goBuy">
        {{ trialLimitReached ? '已达限购' : '立即购买' }}
      </view>
    </view>

    <CouponPicker
      v-model:visible="showCouponSelector"
      v-model="selectedCouponId"
      :coupons="availableCoupons"
      @select="selectCoupon"
      @clear="clearCoupon"
    />

    <CsContactModal
      v-model:visible="csModal.visible"
      :title="csModal.title"
      :qrcode-url="csModal.qrcodeUrl"
      :tips="csModal.tips"
      :copy-text="csModal.copyText"
    />
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onShow, onShareAppMessage, onShareTimeline } from '@dcloudio/uni-app'
import { getProductRichDetail, getProductDetail } from '@/api/product'
import { useUserStore } from '@/store/user'
import { getAvailableCoupons } from '@/api/user'
import CouponPicker from '@/components/CouponPicker.vue'
import CsContactModal from '@/components/CsContactModal.vue'
import { useWeworkCs } from '@/composables/useWeworkCs'
import { calcDiscountAmount } from '@/utils/coupon'
import { blockIfUnderReview } from '@/composables/useAuditGuard'
import { useAuditMode } from '@/composables/useAuditMode'

const userStore = useUserStore()
const { isUnderReview } = useAuditMode()
const { modalState: csModal, openWeworkCs } = useWeworkCs()
const product = ref(null)
const swiperIdx = ref(0)

// 优惠券相关
const availableCoupons = ref([])
const selectedCoupon = ref(null)
const selectedCouponId = ref(null)
const showCouponSelector = ref(false)

// 体验单规则
const couponAllowed = computed(() => product.value?.couponAllowed !== false)
const trialLimitReached = computed(() => product.value?.trialLimitReached === true)

// 分享给好友
onShareAppMessage(() => {
  if (isUnderReview.value) {
    return { title: '热门资讯', path: '/pages/index/index' }
  }
  const p = product.value
  if (!p) return { title: '服务', path: '/pages/index/index' }
  return {
    title: p.name + ' ¥' + Number(p.price || 0).toFixed(2),
    path: '/pages/product/detail?id=' + p.id,
    imageUrl: p.coverImage || p.image || ''
  }
})
// 分享到朋友圈
onShareTimeline(() => {
  if (isUnderReview.value) {
    return { title: '热门资讯', query: '' }
  }
  const p = product.value
  if (!p) return { title: '服务' }
  return {
    title: p.name + ' ¥' + Number(p.price || 0).toFixed(2),
    query: 'id=' + p.id,
    imageUrl: p.coverImage || p.image || ''
  }
})

const priceInt = computed(() => {
  const p = Number(displayPrice.value || 0).toFixed(2)
  return p.split('.')[0]
})
const priceDec = computed(() => {
  const p = Number(displayPrice.value || 0).toFixed(2)
  return p.split('.')[1]
})

const variants = computed(() => product.value?.variants || [])
const hasVariants = computed(() => variants.value.length > 0)
const quantityEnabled = computed(() => product.value?.quantityEnabled === 1)
const unitLabel = computed(() => product.value?.unitLabel || '份')
const maxQuantity = computed(() => product.value?.maxQuantity || 24)
const selectedVariantId = ref(null)
const quantity = ref(1)

const unitPrice = computed(() => {
  if (hasVariants.value) {
    const v = variants.value.find(item => item.id === selectedVariantId.value)
    return Number(v?.price || 0)
  }
  return Number(product.value?.price || 0)
})
const displayPrice = computed(() => unitPrice.value * quantity.value)

function selectVariant(v) {
  selectedVariantId.value = v.id
  loadAvailableCoupons()
}
function changeQuantity(delta) {
  const next = quantity.value + delta
  if (next < 1 || next > maxQuantity.value) return
  quantity.value = next
  loadAvailableCoupons()
}
function initPurchaseOptions() {
  if (hasVariants.value && !selectedVariantId.value) {
    selectedVariantId.value = variants.value[0]?.id ?? null
  }
  quantity.value = 1
}

const images = computed(() => {
  if (!product.value) return []
  const raw = product.value.images
  if (!raw) return [product.value.coverImage || product.value.image]
  try {
    const arr = JSON.parse(raw)
    if (Array.isArray(arr)) return arr
  } catch {}
  return raw.split(',').filter(Boolean)
})

function previewImg(idx) { uni.previewImage({ urls: images.value, current: idx }) }

onLoad(async (opts) => {
  if (await blockIfUnderReview()) return
  try {
    const res = await getProductRichDetail(opts.id)
    const d = res.data
    product.value = d.product || d
    if (d.categoryName) product.value.categoryName = d.categoryName
    initPurchaseOptions()
  } catch (e) {
    const basic = await getProductDetail(opts.id)
    product.value = basic.data
    initPurchaseOptions()
  }
  
  // 加载可用优惠券（体验单跳过）
  if (userStore.isLoggedIn && couponAllowed.value) {
    await loadAvailableCoupons()
  }
})

onShow(async () => {
  if (await blockIfUnderReview()) return
})

async function loadAvailableCoupons() {
  try {
    const res = await getAvailableCoupons({ amount: displayPrice.value || 0 })
    availableCoupons.value = res.data || []
  } catch (e) {
    console.error('获取优惠券失败', e)
  }
}

function selectCoupon(coupon) {
  selectedCoupon.value = coupon
  selectedCouponId.value = coupon.id
}

function clearCoupon() {
  selectedCoupon.value = null
  selectedCouponId.value = null
}

function calculateDiscount(coupon) {
  return calcDiscountAmount(displayPrice.value || 0, coupon).toFixed(2)
}

function goChat() {
  if (!userStore.checkLogin()) return
  openWeworkCs({ scene: 'product', product: product.value })
}

function goBuy() {
  if (trialLimitReached.value) {
    return uni.showToast({ title: product.value?.trialLimitTip || '已达体验单限购', icon: 'none' })
  }
  if (hasVariants.value && !selectedVariantId.value) {
    return uni.showToast({ title: '请选择规格', icon: 'none' })
  }
  const price = displayPrice.value || 0
  const name = encodeURIComponent(product.value.name)
  const couponPart = couponAllowed.value && selectedCouponId.value ? `&couponId=${selectedCouponId.value}` : ''
  let url = `/pages/order/create?productId=${product.value.id}&amount=${price}&productName=${name}${couponPart}`
  url += `&unitPrice=${unitPrice.value}`
  if (hasVariants.value) {
    const v = variants.value.find(item => item.id === selectedVariantId.value)
    url += `&variantId=${selectedVariantId.value}`
    url += `&variantName=${encodeURIComponent(v?.name || '')}`
  }
  if (quantityEnabled.value) {
    url += `&quantity=${quantity.value}`
    url += `&unitLabel=${encodeURIComponent(unitLabel.value)}`
  }
  uni.navigateTo({ url })
}
</script>

<style lang="scss" scoped>
.product-detail {
  background: #f1f5f9;
  min-height: 100vh;
  /* 底部栏高度 + 安全区，避免优惠券等区域被遮挡 */
  padding-bottom: calc(130rpx + env(safe-area-inset-bottom));
}

/* ---- 轮播 ---- */
.banner-wrap {
  position: relative;
}
.banner {
  height: 750rpx;
}
.banner-img {
  width: 100%;
  height: 750rpx;
}
.banner-wrap::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 60rpx;
  background: linear-gradient(to top, rgba(241,245,249,0.9), transparent);
  pointer-events: none;
  z-index: 1;
}
.banner-dots {
  position: absolute;
  bottom: 24rpx;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 12rpx;
  .dot {
    width: 12rpx;
    height: 12rpx;
    border-radius: 50%;
    background: #cbd5e1;
    transition: all 0.3s;
    &.active {
      width: 32rpx;
      border-radius: 6rpx;
      background: #ff4544;
    }
  }
}
.img-counter {
  position: absolute;
  top: 24rpx;
  right: 24rpx;
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.7);
  background: rgba(0, 0, 0, 0.4);
  padding: 4rpx 16rpx;
  border-radius: 20rpx;
}

/* ---- 价格卡片 ---- */
.price-card {
  margin: -20rpx 24rpx 0;
  position: relative;
  z-index: 2;
  background: linear-gradient(135deg, rgba(255, 69, 68, 0.18), rgba(255, 69, 68, 0.08));
  border: 1rpx solid rgba(255, 69, 68, 0.22);
  border-radius: 20rpx;
  padding: 28rpx 32rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.price-main {
  display: flex;
  align-items: baseline;
  .yen {
    font-size: 30rpx;
    color: #ff4544;
    font-weight: bold;
    margin-right: 4rpx;
  }
  .price-int {
    font-size: 56rpx;
    color: #ff4544;
    font-weight: 800;
    line-height: 1;
  }
  .price-dec {
    font-size: 30rpx;
    color: #ff4544;
    font-weight: bold;
  }
}
.price-extra {
  display: flex;
  align-items: center;
  gap: 16rpx;
}
.sales-badge {
  font-size: 22rpx;
  color: #94a3b8;
  background: #f1f5f9;
  padding: 6rpx 18rpx;
  border-radius: 20rpx;
}
.limit-tag {
  font-size: 22rpx;
  color: #b91c1c;
  background: rgba(248, 113, 113, 0.16);
  padding: 6rpx 18rpx;
  border-radius: 20rpx;
}

/* ---- 商品信息 ---- */
.info-card {
  margin: 20rpx 24rpx 0;
  padding: 28rpx 32rpx;
  background: #f1f5f9;
  border: 1rpx solid #f1f5f9;
  border-radius: 20rpx;
}
.product-name {
  font-size: 34rpx;
  font-weight: bold;
  display: block;
  color: #1e293b;
  line-height: 1.5;
}
.product-subtitle {
  font-size: 24rpx;
  color: #94a3b8;
  display: block;
  margin-top: 8rpx;
  line-height: 1.4;
}
.meta-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 16rpx;
}
.category-tag {
  display: flex;
  align-items: center;
  gap: 8rpx;
  font-size: 22rpx;
  color: #ff4544;
  background: rgba(212, 175, 55, 0.1);
  padding: 6rpx 16rpx;
  border-radius: 8rpx;
  border: 1rpx solid rgba(255, 69, 68, 0.15);
  .tag-dot {
    font-size: 14rpx;
    color: #ff4544;
  }
}
.desc-wrap {
  margin-top: 20rpx;
}
.desc-divider {
  height: 1rpx;
  background: #f1f5f9;
  margin-bottom: 20rpx;
}
.desc {
  font-size: 26rpx;
  color: #64748b;
  display: block;
  line-height: 1.7;
}

.option-card {
  margin: 20rpx 24rpx 0;
  padding: 24rpx 28rpx;
  background: #fff;
  border-radius: 16rpx;
}
.option-block + .option-block {
  margin-top: 24rpx;
  padding-top: 24rpx;
  border-top: 1rpx solid #f1f5f9;
}
.option-label {
  font-size: 28rpx;
  color: #1e293b;
  font-weight: 600;
  display: block;
  margin-bottom: 16rpx;
}
.variant-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}
.variant-tag {
  min-width: 160rpx;
  padding: 16rpx 20rpx;
  border: 2rpx solid #e2e8f0;
  border-radius: 12rpx;
  background: #f8fafc;
}
.variant-tag.active {
  border-color: #ff4544;
  background: rgba(255, 69, 68, 0.08);
}
.variant-name {
  display: block;
  font-size: 26rpx;
  color: #1e293b;
}
.variant-price {
  display: block;
  margin-top: 6rpx;
  font-size: 24rpx;
  color: #ff4544;
  font-weight: 600;
}
.quantity-stepper {
  display: flex;
  align-items: center;
  gap: 24rpx;
}
.qty-btn {
  width: 56rpx;
  height: 56rpx;
  line-height: 52rpx;
  text-align: center;
  border: 2rpx solid #e2e8f0;
  border-radius: 12rpx;
  font-size: 32rpx;
  color: #1e293b;
}
.qty-btn.disabled {
  opacity: 0.4;
}
.qty-value {
  min-width: 120rpx;
  text-align: center;
  font-size: 28rpx;
  color: #1e293b;
}

.coupon-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  margin: 20rpx 24rpx 0;
  padding: 24rpx 28rpx;
  border-radius: 16rpx;
  
  .coupon-left {
    flex: 1;
    
    .coupon-label {
      font-size: 28rpx;
      color: #1e293b;
      font-weight: 500;
      display: block;
    }
    
    .coupon-value {
      font-size: 24rpx;
      color: #94a3b8;
      margin-top: 6rpx;
      display: block;
    }
  }
  
  .coupon-right {
    display: flex;
    align-items: center;
  }
}

.chevron-right {
  width: 16rpx;
  height: 16rpx;
  border-top: 3rpx solid #cbd5e1;
  border-right: 3rpx solid #cbd5e1;
  transform: rotate(45deg);
}

/* ---- 详情区域 ---- */
.detail-section {
  margin: 20rpx 24rpx;
  background: #f1f5f9;
  border: 1rpx solid #f1f5f9;
  border-radius: 20rpx;
  overflow: hidden;
}
.section-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20rpx;
  padding: 28rpx 32rpx 0;
}
.section-line {
  flex: 1;
  height: 1rpx;
  background: linear-gradient(to right, transparent, rgba(255, 69, 68, 0.3), transparent);
}
.section-title {
  font-size: 28rpx;
  font-weight: bold;
  color: #ff4544;
  white-space: nowrap;
}
.detail-body {
  padding: 24rpx 32rpx 32rpx;
}

/* ---- 底部栏 ---- */
.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  padding: 16rpx 24rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background: rgba(255, 255, 255, 0.96);
  border-top: 1rpx solid #e2e8f0;
  gap: 20rpx;
  backdrop-filter: blur(20px);
}
.btn-chat {
  flex: 1;
  height: 84rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  border: 1rpx solid rgba(255, 69, 68, 0.4);
  color: #ff4544;
  border-radius: 999rpx;
  font-size: 26rpx;
  .btn-icon {
    width: 36rpx;
    height: 36rpx;
  }
}
.btn-buy {
  flex: 2;
  height: 84rpx;
  line-height: 84rpx;
  text-align: center;
  background: linear-gradient(135deg, #ff4544, #e63939);
  color: #ffffff;
  font-weight: bold;
  border-radius: 999rpx;
  font-size: 30rpx;
  letter-spacing: 2rpx;
  &.disabled {
    background: #cbd5e1;
    color: #64748b;
  }
}
.trial-limit-tag {
  background: rgba(255, 69, 68, 0.1);
  color: #ff4544;
}

</style>