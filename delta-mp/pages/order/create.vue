<template>
  <view class="create-order">
    <view class="section">
      <view class="section-title">商品信息</view>
      <text class="product-name">{{ productName }}</text>
      <text v-if="variantName" class="spec">{{ variantName }}</text>
      <text v-if="showQuantityLine" class="spec">{{ unitPriceText }} × {{ quantity }}{{ unitLabel }}</text>
      <text v-else-if="specInfo" class="spec">{{ specInfo }}</text>
      <PriceText :value="finalAmount" :size="36" />
      <text v-if="selectedCoupon && discountAmount > 0" class="discount-info">
        已优惠 ¥{{ discountAmount }}
      </text>
    </view>

    <!-- 体验单限购提示 -->
    <view v-if="trialLimitTip" class="section trial-tip-section">
      <text class="trial-tip">{{ trialLimitTip }}</text>
    </view>

    <!-- 优惠券区域（体验单不可用券） -->
    <view class="coupon-section" v-if="couponAllowed && availableCoupons.length > 0" @click="showCouponSelector = true">
      <view class="coupon-left">
        <text class="coupon-label">优惠券</text>
        <text class="coupon-value" v-if="selectedCoupon">
          {{ selectedCoupon.couponName }} 省 ¥{{ discountAmount }}
        </text>
        <text class="coupon-value" v-else>有{{ availableCoupons.length }}张优惠券可用</text>
      </view>
      <view class="coupon-right">
        <view class="chevron-right" />
      </view>
    </view>

    <!-- 使用已保存信息 -->
    <view class="section saved-section" v-if="savedList.length || formFields.length">
      <view class="section-title">使用已保存信息</view>
      <view class="saved-tags">
        <view
          v-for="item in savedList" :key="item.id"
          class="saved-tag" :class="{ active: activeSavedId === item.id }"
          @click="applySaved(item)"
        >
          <text class="saved-label">{{ item.label || '未命名' }}</text>
        </view>
        <view class="saved-tag" :class="{ active: activeSavedId === 'manual' }" @click="applyManual">
          <text class="saved-label">手动填写</text>
        </view>
      </view>
    </view>
    <view class="section" v-if="formFields.length">
      <view class="section-title">订单信息</view>
      <view v-for="field in formFields" :key="field.id" class="form-item">
        <text class="label">{{ field.fieldLabel }}<text v-if="field.required" class="required">*</text></text>
        <!-- TEXT -->
        <input v-if="field.fieldType === 'TEXT'" v-model="extraFields[field.fieldLabel]" :placeholder="field.placeholder || ('请输入' + field.fieldLabel)" />
        <!-- TEXTAREA -->
        <textarea v-else-if="field.fieldType === 'TEXTAREA'" v-model="extraFields[field.fieldLabel]" :placeholder="field.placeholder || ('请输入' + field.fieldLabel)" :maxlength="500" />
        <!-- SELECT -->
        <picker v-else-if="field.fieldType === 'SELECT'" :range="getFieldOptions(field)" @change="extraFields[field.fieldLabel] = getFieldOptions(field)[$event.detail.value]">
          <view class="picker-value">
            <text :class="{ placeholder: !extraFields[field.fieldLabel] }">{{ extraFields[field.fieldLabel] || field.placeholder || ('请选择' + field.fieldLabel) }}</text>
            <view class="chevron-right" />
          </view>
        </picker>
      </view>
    </view>
    <view class="section">
      <view class="section-title">指定接单员（选填）</view>
      <view class="designate-toggle">
        <text>是否指定接单员</text>
        <switch :checked="wantDesignate" @change="wantDesignate = $event.detail.value" color="#ff4544" />
      </view>
      <view v-if="wantDesignate" class="player-select" @click="showPlayerPicker = true">
        <text v-if="selectedPlayer" class="selected-player">{{ selectedPlayer.nickname }}</text>
        <text v-else class="placeholder">点击选择接单员</text>
        <view class="chevron-right" />
      </view>
    </view>
    <view class="bottom-bar">
      <view class="total">合计：<PriceText :value="finalAmount" :size="36" /></view>
      <view class="btn-submit" @click="submitOrder">提交订单</view>
    </view>

    <!-- 接单员选择弹窗（底部滑出） -->
    <view v-if="showPlayerPicker" class="modal-mask" @click="showPlayerPicker = false">
      <view class="player-picker" :class="{ 'slide-up': showPlayerPicker }" @click.stop>
        <view class="picker-handle"><view class="handle-bar" /></view>
        <view class="picker-header">
          <text class="picker-title">选择接单员</text>
          <text class="picker-close" @click="showPlayerPicker = false">✕</text>
        </view>
        <view class="limit-tip">
          接单员最多同时接 <text class="gold">{{ maxConcurrent }}</text> 个订单
        </view>
        <view class="picker-search">
          <input v-model="playerKeyword" placeholder="搜索接单员昵称/手机号" @confirm="searchPlayers" />
          <view class="search-btn" @click="searchPlayers">搜索</view>
        </view>
        <scroll-view scroll-y class="picker-list">
          <view
            v-for="p in playerList" :key="p.id"
            class="picker-item"
            :class="{ selected: selectedPlayer && selectedPlayer.id === p.id, full: isFull(p), offline: !isOnline(p) }"
            @click="pickPlayer(p)"
          >
            <image :src="p.avatar || '/static/images/default-avatar.png'" class="picker-avatar" mode="aspectFill" />
            <view class="picker-info">
              <view class="picker-name-row">
                <text class="picker-name">{{ p.nickname || '-' }}</text>
                <text v-if="isOnline(p)" class="online-badge">在线</text>
                <text v-else class="offline-badge">离线</text>
                <text v-if="isFull(p)" class="full-badge">已满载</text>
              </view>
              <view class="picker-tags">
                <text v-if="p.avgRating" class="picker-tag">⭐{{ Number(p.avgRating).toFixed(1) }}</text>
                <text class="picker-tag done">完成 {{ p.completedOrders || 0 }}</text>
                <text class="picker-tag" :class="(p.activeOrders||0) > 0 ? 'active-tag' : ''">进行中 {{ p.activeOrders || 0 }}/{{ maxConcurrent }}</text>
              </view>
            </view>
            <text v-if="isOnline(p) && selectedPlayer && selectedPlayer.id === p.id" class="picker-check">✓</text>
          </view>
          <view v-if="playerList.length === 0" class="picker-empty">暂无可用接单员</view>
        </scroll-view>
        <view class="picker-footer">
          <view class="picker-btn cancel" @click="showPlayerPicker = false">取消</view>
          <view class="picker-btn confirm" @click="confirmPickPlayer">确定</view>
        </view>
      </view>
    </view>

    <CouponPicker
      v-model:visible="showCouponSelector"
      v-model="selectedCouponId"
      :coupons="availableCoupons"
      @select="selectCoupon"
      @clear="clearCoupon"
    />
  </view>
</template>

<script setup>
import { ref, reactive, watch, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import PriceText from '@/components/PriceText.vue'
import CouponPicker from '@/components/CouponPicker.vue'
import { calcDiscountAmount } from '@/utils/coupon'
import { createOrder, getAvailablePlayers } from '@/api/order'
import { getProductDetail, getCategoryFormFields } from '@/api/product'
import { getSavedInfoByCategory, saveDynamicInfo } from '@/api/user'
import { getAvailableCoupons } from '@/api/user'
import { useUserStore } from '@/store/user'
import { blockIfUnderReview } from '@/composables/useAuditGuard'

const userStore = useUserStore()
const productId = ref(0)
const specInfo = ref('')
const variantId = ref(null)
const variantName = ref('')
const unitPrice = ref(0)
const quantity = ref(1)
const unitLabel = ref('份')
const amount = ref(0)
const productName = ref('')
const couponId = ref(null)

// 体验单规则
const couponAllowed = ref(true)
const trialLimitTip = ref('')
const trialLimitReached = ref(false)

// 优惠券相关
const availableCoupons = ref([])
const selectedCoupon = ref(null)
const selectedCouponId = ref(null)
const showCouponSelector = ref(false)

// 优惠后金额
const discountAmount = ref(0)
const finalAmount = computed(() => {
  let result = amount.value - discountAmount.value
  return result < 0 ? 0 : result
})
const showQuantityLine = computed(() => quantity.value > 1)
const unitPriceText = computed(() => `¥${Number(resolvedUnitPrice.value).toFixed(2)}`)
const resolvedUnitPrice = computed(() => {
  if (unitPrice.value > 0) return unitPrice.value
  if (amount.value > 0 && quantity.value > 0) return amount.value / quantity.value
  return 0
})

// 动态表单
const formFields = ref([])
const extraFields = reactive({})
const currentCategoryId = ref(null)

// 已保存的下单信息
const savedList = ref([])
const activeSavedId = ref(null)

function getFieldOptions(field) {
  if (!field.options) return []
  return field.options.split(',').map(s => s.trim()).filter(Boolean)
}

async function loadFormFields(pid) {
  try {
    const prodRes = await getProductDetail(pid)
    const product = prodRes.data
    const categoryId = product?.categoryId
    // 体验单：禁用优惠券、展示限购提示
    couponAllowed.value = product?.couponAllowed !== false
    trialLimitTip.value = product?.trialLimitTip || ''
    trialLimitReached.value = product?.trialLimitReached === true
    if (!couponAllowed.value) {
      clearCoupon()
    }
    if (!categoryId) return
    currentCategoryId.value = categoryId
    const res = await getCategoryFormFields(categoryId)
    formFields.value = res.data || []
    loadSavedList(categoryId)
  } catch (e) {
    formFields.value = []
  }
}

async function loadSavedList(categoryId) {
  try {
    const res = await getSavedInfoByCategory(categoryId)
    savedList.value = res.data || []
  } catch (e) {
    savedList.value = []
  }
}

function applySaved(item) {
  activeSavedId.value = item.id
  let fields = {}
  if (item.savedFields) {
    try {
      fields = typeof item.savedFields === 'string' ? JSON.parse(item.savedFields) : item.savedFields
    } catch (e) { /* ignore */ }
  }
  Object.keys(extraFields).forEach(k => { extraFields[k] = '' })
  Object.entries(fields).forEach(([k, v]) => { extraFields[k] = v })
}

function applyManual() {
  activeSavedId.value = 'manual'
  Object.keys(extraFields).forEach(k => { extraFields[k] = '' })
}

// 优惠券相关方法
async function loadAvailableCoupons() {
  if (!couponAllowed.value) {
    availableCoupons.value = []
    return
  }
  try {
    const res = await getAvailableCoupons({ amount: amount.value })
    availableCoupons.value = res.data || []
    // 从商品详情页带入的 couponId，自动选中
    if (couponId.value && !selectedCouponId.value) {
      const found = availableCoupons.value.find(c => String(c.id) === String(couponId.value))
      if (found) selectCoupon(found)
    }
  } catch (e) {
    console.error('获取优惠券失败', e)
  }
}

function selectCoupon(coupon) {
  selectedCoupon.value = coupon
  selectedCouponId.value = coupon.id
  discountAmount.value = calcDiscountAmount(amount.value, coupon)
}

function clearCoupon() {
  selectedCoupon.value = null
  selectedCouponId.value = null
  discountAmount.value = 0
}

// 接单员相关
const wantDesignate = ref(false)
const showPlayerPicker = ref(false)
const selectedPlayer = ref(null)
const playerKeyword = ref('')
const playerList = ref([])
const maxConcurrent = ref(5)

watch(wantDesignate, (val) => {
  if (val) searchPlayers()
})

function isFull(p) {
  return (p.activeOrders || 0) >= maxConcurrent.value
}
function isOnline(p) {
  return p.isOnline === 1
}

async function searchPlayers() {
  try {
    const res = await getAvailablePlayers({ pageNum: 1, pageSize: 50, keyword: playerKeyword.value })
    const data = res.data || {}
    const page = data.players || {}
    playerList.value = page.records || []
    if (data.maxConcurrent != null) maxConcurrent.value = data.maxConcurrent
  } catch (e) {
    playerList.value = []
  }
}

function pickPlayer(p) {
  if (!isOnline(p)) {
    uni.showModal({ title: '无法选择', content: `${p.nickname} 当前处于离线状态，无法指定`, showCancel: false })
    return
  }
  if (isFull(p)) {
    uni.showModal({
      title: '无法选择',
      content: `该接单员当前已有 ${p.activeOrders || 0} 个进行中订单，已达最大接单数 ${maxConcurrent.value}`,
      showCancel: false
    })
    return
  }
  selectedPlayer.value = p
}

function confirmPickPlayer() {
  showPlayerPicker.value = false
}

onLoad(async (opts) => {
  if (await blockIfUnderReview()) return
  productId.value = opts.productId
  specInfo.value = decodeURIComponent(opts.specCombination || opts.specInfo || '')
  variantId.value = opts.variantId ? Number(opts.variantId) : null
  variantName.value = decodeURIComponent(opts.variantName || '')
  unitPrice.value = parseFloat(opts.unitPrice) || 0
  quantity.value = parseInt(opts.quantity, 10) || 1
  unitLabel.value = decodeURIComponent(opts.unitLabel || '份')
  amount.value = parseFloat(opts.amount) || 0
  if (!unitPrice.value && amount.value > 0 && quantity.value > 0) {
    unitPrice.value = amount.value / quantity.value
  }
  productName.value = decodeURIComponent(opts.productName || '服务')
  if (opts.couponId) {
    couponId.value = opts.couponId
  }
  // 先加载商品详情（含体验单规则），再拉优惠券，避免竞态导致体验单仍显示优惠券
  await loadFormFields(opts.productId)
  loadAvailableCoupons()
})

onShow(async () => {
  if (await blockIfUnderReview()) return
})

async function submitOrder() {
  if (!userStore.checkLogin()) return
  if (trialLimitReached.value) {
    return uni.showToast({ title: trialLimitTip.value || '已达体验单限购', icon: 'none' })
  }
  // 校验必填动态字段
  for (const field of formFields.value) {
    if (field.required && !extraFields[field.fieldLabel]?.trim()) {
      return uni.showToast({ title: `请填写${field.fieldLabel}`, icon: 'none' })
    }
  }
  try {
    const fieldsToSubmit = { ...extraFields }
    const orderData = {
      productId: productId.value,
      specInfo: specInfo.value,
      variantId: variantId.value || undefined,
      quantity: quantity.value,
      // 提交折后应付金额，与后端优惠券计算结果对齐（保留两位小数）
      amount: Number(finalAmount.value.toFixed(2)),
      extraFields: fieldsToSubmit,
      designatedPlayerId: selectedPlayer.value?.id
    }
    if (couponAllowed.value && selectedCouponId.value) {
      orderData.couponId = selectedCouponId.value
    }
    const res = await createOrder(orderData)
    // 自动保存本次填写的信息（最多3条，超出覆盖最旧）
    if (currentCategoryId.value && formFields.value.length) {
      const vals = Object.values(fieldsToSubmit).filter(v => v && v.trim())
      if (vals.length) {
        const label = vals.slice(0, 2).join(' / ').substring(0, 30)
        try {
          await saveDynamicInfo({
            categoryId: currentCategoryId.value,
            savedFields: fieldsToSubmit,
            label
          })
        } catch (e) {
          console.error('保存下单信息失败', e)
        }
      }
    }
    let payUrl = `/pages/order/pay?orderId=${res.data.id}&amount=${Number(finalAmount.value.toFixed(2))}`
    if (selectedCouponId.value) {
      payUrl += `&couponId=${selectedCouponId.value}`
      payUrl += `&discountAmount=${discountAmount.value}`
      if (selectedCoupon.value?.couponName) {
        payUrl += `&couponName=${encodeURIComponent(selectedCoupon.value.couponName)}`
      }
    }
    uni.redirectTo({ url: payUrl })
  } catch (e) {
    console.error('create order failed', e)
    const msg = e?.msg || e?.message || '下单失败，请稍后重试'
    uni.showToast({ title: msg, icon: 'none' })
  }
}
</script>

<style lang="scss" scoped>
.create-order { background: #f1f5f9; min-height: 100vh; padding-bottom: 120rpx; }
.section { background: #ffffff; padding: 24rpx; margin: 20rpx 24rpx; border-radius: 16rpx; box-shadow: 0 4rpx 20rpx rgba(99, 102, 241, 0.08); }
.section-title { font-size: 30rpx; font-weight: bold; margin-bottom: 20rpx; color: #ff4544; }
.product-name { font-size: 28rpx; font-weight: bold; color: #1e293b; display: block; margin-bottom: 8rpx; }
.spec { font-size: 24rpx; color: #64748b; display: block; margin-bottom: 12rpx; }
.discount-info { font-size: 24rpx; color: #ff4544; display: block; margin-top: 8rpx; }
.trial-tip-section { padding: 20rpx 24rpx; }
.trial-tip { font-size: 24rpx; color: #ff4544; }
.required { color: #ff4544; margin-left: 4rpx; font-size: 24rpx; }
.form-item { margin-bottom: 24rpx; .label { font-size: 26rpx; color: #64748b; margin-bottom: 12rpx; display: block; }
  input { height: 72rpx; background: #f1f5f9; border: 1rpx solid #e2e8f0; border-radius: 8rpx; padding: 0 24rpx; font-size: 28rpx; color: #1e293b; }
  textarea { width: 100%; height: 160rpx; background: #f1f5f9; border: 1rpx solid #e2e8f0; border-radius: 8rpx; padding: 20rpx 24rpx; font-size: 28rpx; color: #1e293b; box-sizing: border-box; }
}
.picker-value { display: flex; align-items: center; justify-content: space-between; height: 72rpx; background: #f1f5f9; border: 1rpx solid #e2e8f0; border-radius: 8rpx; padding: 0 24rpx; font-size: 28rpx; color: #1e293b;
  .placeholder { color: #94a3b8; }
  .arrow { color: #94a3b8; font-size: 24rpx; }
}
.designate-toggle { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16rpx; font-size: 28rpx; color: #1e293b; }
.player-select { display: flex; align-items: center; justify-content: space-between; padding: 24rpx; background: #f1f5f9; border: 1rpx solid #e2e8f0; border-radius: 8rpx; font-size: 28rpx; color: #1e293b; }
.player-select .placeholder { color: #94a3b8; }
.player-select .arrow { color: #94a3b8; font-size: 24rpx; }
.selected-player { font-weight: 500; color: #ff4544; }

/* 优惠券区域 */
.coupon-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  margin: 0 24rpx 20rpx;
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
  flex-shrink: 0;
}

/* 接单员弹窗样式保持不变 */
.player-picker {
  width: 100%; max-height: 80vh; background: #ffffff;
  border-radius: 24rpx 24rpx 0 0; padding: 0 24rpx 24rpx;
  padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
  animation: slideUp 0.3s ease-out;
}
.picker-handle {
  display: flex; justify-content: center; padding: 16rpx 0;
  .handle-bar { width: 64rpx; height: 8rpx; background: #e2e8f0; border-radius: 4rpx; }
}
.picker-header {
  display: flex; align-items: center; justify-content: space-between;
  margin-bottom: 16rpx;
}
.picker-title { font-size: 32rpx; font-weight: bold; color: #ff4544; }
.picker-close { font-size: 36rpx; color: #94a3b8; padding: 0 8rpx; }
.limit-tip {
  padding: 12rpx 24rpx; font-size: 24rpx; color: rgba(0,0,0,0.45);
  background: rgba(0,0,0,0.04); text-align: center;
  border-radius: 8rpx; margin-bottom: 16rpx;
  .gold { color: #ff4544; font-weight: bold; }
}
.picker-search {
  display: flex; gap: 16rpx; margin-bottom: 16rpx;
  input {
    flex: 1; height: 72rpx; background: #f1f5f9;
    padding: 0 24rpx; border-radius: 999rpx;
    font-size: 26rpx; color: #1e293b; box-sizing: border-box;
  }
  .search-btn {
    padding: 0 32rpx; height: 72rpx; line-height: 72rpx;
    background: linear-gradient(135deg, #ff4544, #e63939);
    color: #ffffff; font-weight: bold; border-radius: 999rpx;
    font-size: 26rpx; flex-shrink: 0;
  }
}
.picker-list { max-height: 480rpx; }
.picker-item {
  display: flex; align-items: center; padding: 20rpx;
  background: rgba(0,0,0,0.02); border-radius: 12rpx; margin-bottom: 12rpx;
  &.selected { background: rgba(99, 102, 241, 0.08); }
  &.full { opacity: 0.5; }
  &.offline { opacity: 0.5; }
}
.picker-avatar { width: 80rpx; height: 80rpx; border-radius: 50%; flex-shrink: 0; margin-right: 20rpx; }
.picker-info { flex: 1; overflow: hidden; }
.picker-name-row { display: flex; align-items: center; gap: 8rpx; }
.picker-name { font-size: 28rpx; color: #1e293b; font-weight: 500; }
.online-badge { font-size: 20rpx; color: #22c55e; background: rgba(34,197,94,0.15); padding: 2rpx 10rpx; border-radius: 4rpx; }
.offline-badge { font-size: 20rpx; color: #94a3b8; background: rgba(148,163,184,0.15); padding: 2rpx 10rpx; border-radius: 4rpx; }
.full-badge { font-size: 20rpx; color: #ee6723; background: rgba(238,103,35,0.15); padding: 2rpx 10rpx; border-radius: 4rpx; }
.picker-tags { display: flex; gap: 8rpx; margin-top: 6rpx; flex-wrap: wrap; }
.picker-tag {
  font-size: 22rpx; color: rgba(0,0,0,0.55);
  background: rgba(0,0,0,0.05); padding: 4rpx 12rpx; border-radius: 4rpx;
  &.done { color: #07c160; }
  &.active-tag { color: #ee6723; }
}
.picker-check { font-size: 28rpx; color: #ff4544; font-weight: bold; margin-left: 12rpx; }
.picker-empty { text-align: center; padding: 40rpx; font-size: 26rpx; color: #94a3b8; }
.picker-footer {
  display: flex; gap: 20rpx; margin-top: 20rpx;
  padding-top: 16rpx; border-top: 1rpx solid #e2e8f0;
}
.picker-btn {
  flex: 1; height: 80rpx; line-height: 80rpx;
  text-align: center; font-size: 28rpx; border-radius: 999rpx;
  &.cancel { border: 1rpx solid #e2e8f0; color: #64748b; }
  &.confirm { background: linear-gradient(135deg, #ff4544, #e63939); color: #ffffff; font-weight: bold; }
}

/* 已保存的下单信息标签 */
.saved-section { padding: 24rpx 24rpx 20rpx !important; }
.saved-tags { display: flex; gap: 16rpx; flex-wrap: wrap; }
.saved-tag {
  display: flex; align-items: center; justify-content: center;
  padding: 16rpx 28rpx; background: #ffffff;
  border: 2rpx solid #e2e8f0; border-radius: 8rpx;
  font-size: 26rpx; color: #1e293b;
  &.active { border-color: #ff4544; color: #ff4544; }
}
.saved-label { max-width: 280rpx; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.bottom-bar { position: fixed; bottom: 0; left: 0; right: 0; display: flex; height: 100rpx; background: rgba(255,255,255,0.95); border-top: 1rpx solid #e2e8f0; align-items: center; padding: 0 24rpx; padding-bottom: env(safe-area-inset-bottom);
  .total { flex: 1; font-size: 28rpx; color: #1e293b; }
  .btn-submit { width: 240rpx; height: 76rpx; line-height: 76rpx; text-align: center; background: linear-gradient(135deg, #ff4544, #e63939); color: #ffffff; font-weight: bold; font-size: 30rpx; border-radius: 999rpx; }
}
</style>