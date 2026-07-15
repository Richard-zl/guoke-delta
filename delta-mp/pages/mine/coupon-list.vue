<template>
  <view class="coupon-list-page">
    <!-- 选项卡 -->
    <view class="coupon-tabs">
      <view
        v-for="tab in tabs"
        :key="tab.value"
        class="tab"
        :class="{ active: currentTab === tab.value }"
        @click="switchTab(tab.value)"
      >
        {{ tab.label }}
      </view>
    </view>

    <!-- 优惠券列表 -->
    <scroll-view scroll-y class="coupon-scroll" @scrolltolower="loadMore">
      <view
        v-for="coupon in couponList"
        :key="coupon.id"
        class="coupon-card"
        :class="{ disabled: coupon.status !== 'UNUSED' }"
      >
        <!-- 左侧金额区 -->
        <view class="card-left">
          <view class="amount-wrap">
            <text class="amount-num">{{ getCouponAmount(coupon) }}</text>
            <text class="amount-unit">{{ isCash(coupon) ? '元' : '折' }}</text>
          </view>
          <text class="condition-text">{{ coupon.minAmount > 0 ? `满${coupon.minAmount}元可用` : '无门槛' }}</text>
        </view>

        <!-- 锯齿分隔线 -->
        <view class="divider">
          <view class="notch top" />
          <view class="dash-line" />
          <view class="notch bottom" />
        </view>

        <!-- 右侧信息区 -->
        <view class="card-right">
          <text class="coupon-name">{{ coupon.couponName }}</text>
          <text class="coupon-type-tag">{{ getCouponTypeLabel(coupon) }}</text>
          <text class="expire-text">有效期至 {{ formatExpire(coupon.expireTime) }}</text>
        </view>

        <!-- 状态水印 -->
        <view class="status-stamp" v-if="coupon.status !== 'UNUSED'">
          <text>{{ coupon.status === 'USED' ? '已使用' : '已过期' }}</text>
        </view>
      </view>

      <EmptyState v-if="!loading && couponList.length === 0" text="暂无优惠券" />
      <view class="loading-more" v-if="loadingMore">加载中...</view>
      <view class="no-more" v-if="finished && couponList.length > 0">没有更多了</view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getUserCoupons } from '@/api/user'
import EmptyState from '@/components/EmptyState.vue'
import { blockIfUnderReview } from '@/composables/useAuditGuard'

const tabs = [
  { value: 'UNUSED', label: '未使用' },
  { value: 'USED', label: '已使用' },
  { value: 'EXPIRED', label: '已过期' }
]
const currentTab = ref('UNUSED')
const couponList = ref([])
const pageNum = ref(1)
const loading = ref(false)
const loadingMore = ref(false)
const finished = ref(false)

onShow(async () => {
  if (await blockIfUnderReview()) return
  refresh()
})

function switchTab(tab) {
  currentTab.value = tab
  refresh()
}

function refresh() {
  pageNum.value = 1
  couponList.value = []
  finished.value = false
  loadData()
}

async function loadData() {
  if (loading.value || finished.value) return
  loading.value = true
  try {
    const res = await getUserCoupons({
      pageNum: pageNum.value,
      pageSize: 10,
      status: currentTab.value
    })
    const list = res.data?.records || []
    if (list.length < 10) finished.value = true
    couponList.value = pageNum.value === 1 ? list : [...couponList.value, ...list]
  } catch (e) {
    console.error(e)
    uni.showToast({ title: '加载失败', icon: 'none' })
  } finally {
    loading.value = false
    loadingMore.value = false
  }
}

function loadMore() {
  if (finished.value || loading.value) return
  loadingMore.value = true
  pageNum.value++
  loadData()
}

/** 是否为现金券 */
function isCash(coupon) {
  return coupon.couponType?.startsWith('CASH')
}

/** 右侧显示的数值（不含单位） */
function getCouponAmount(coupon) {
  if (isCash(coupon)) {
    return Number(coupon.cashAmount || 0).toFixed(0)
  }
  // discountRate: 0.8 → 8折，用 toFixed(1) 再 parseFloat 去掉多余小数点
  return parseFloat((coupon.discountRate * 10).toFixed(1))
}

/** 券类型标签文案 */
function getCouponTypeLabel(coupon) {
  if (isCash(coupon)) return '代金券'
  return `${parseFloat((coupon.discountRate * 10).toFixed(1))}折优惠券`
}

/** 格式化过期时间 yyyy-MM-dd */
function formatExpire(dateStr) {
  if (!dateStr) return '--'
  return dateStr.slice(0, 10)
}
</script>

<style lang="scss" scoped>
$red: #ff4544;
$red-light: #fff1f0;
$gray: #94a3b8;
$text-dark: #1e293b;

.coupon-list-page {
  min-height: 100vh;
  background: #f4f6f9;
}

/* ── 选项卡 ── */
.coupon-tabs {
  display: flex;
  background: #fff;
  position: sticky;
  top: 0;
  z-index: 10;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);

  .tab {
    flex: 1;
    text-align: center;
    font-size: 28rpx;
    color: $gray;
    padding: 28rpx 0 24rpx;
    position: relative;

    &.active {
      color: $red;
      font-weight: 600;

      &::after {
        content: '';
        position: absolute;
        bottom: 0;
        left: 50%;
        transform: translateX(-50%);
        width: 48rpx;
        height: 4rpx;
        background: $red;
        border-radius: 2rpx;
      }
    }
  }
}

/* ── 列表滚动区 ── */
.coupon-scroll {
  height: calc(100vh - 108rpx);
  padding: 24rpx 28rpx;
  box-sizing: border-box;
}

/* ── 优惠券卡片 ── */
.coupon-card {
  display: flex;
  align-items: stretch;
  background: #fff;
  border-radius: 20rpx;
  margin-bottom: 24rpx;
  overflow: visible;
  box-shadow: 0 6rpx 24rpx rgba(255, 69, 68, 0.08);
  position: relative;

  &.disabled {
    box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04);

    .card-left {
      background: linear-gradient(145deg, #cbd5e1, #94a3b8);
    }

    .amount-num,
    .amount-unit {
      color: #fff;
    }

    .condition-text {
      color: rgba(255,255,255,0.7);
    }
  }
}

/* 左侧彩色区 */
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
  letter-spacing: -2rpx;
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

/* 锯齿分隔 */
.divider {
  width: 20rpx;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
  background: #fff;

  .notch {
    width: 28rpx;
    height: 28rpx;
    background: #f4f6f9;
    border-radius: 50%;
    flex-shrink: 0;
    position: relative;
    z-index: 1;

    &.top {
      margin-top: -14rpx;
      box-shadow: inset -4rpx 0 8rpx rgba(255,69,68,0.08);
    }

    &.bottom {
      margin-bottom: -14rpx;
      box-shadow: inset -4rpx 0 8rpx rgba(255,69,68,0.08);
    }
  }

  .dash-line {
    flex: 1;
    width: 2rpx;
    background: repeating-linear-gradient(
      to bottom,
      #e2e8f0 0,
      #e2e8f0 8rpx,
      transparent 8rpx,
      transparent 16rpx
    );
  }
}

/* 右侧信息区 */
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
  display: block;
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
  display: block;
}

/* 状态水印 */
.status-stamp {
  position: absolute;
  right: 24rpx;
  top: 50%;
  transform: translateY(-50%) rotate(-15deg);
  border: 4rpx solid #cbd5e1;
  border-radius: 10rpx;
  padding: 6rpx 16rpx;

  text {
    font-size: 26rpx;
    color: #cbd5e1;
    font-weight: 700;
    letter-spacing: 4rpx;
  }
}

.loading-more,
.no-more {
  text-align: center;
  padding: 24rpx;
  font-size: 24rpx;
  color: $gray;
}
</style>