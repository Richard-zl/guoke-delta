<template>
  <view class="order-detail" v-if="order">
    <!-- 状态栏 -->
    <view class="status-header">
      <StatusTag :status="order.status" />
      <text class="order-no">{{ order.orderNo }}</text>
    </view>
    <!-- 商品信息 -->
    <view class="card">
      <view class="card-header"><view class="title-bar" /><text class="card-title">商品信息</text></view>
      <view class="info-row"><text class="label">商品</text><text class="value">{{ order.productName }}</text></view>
      <view v-if="order.variantName || order.specInfo || order.specCombination" class="info-row"><text class="label">规格</text><text class="value">{{ order.variantName || order.specCombination || order.specInfo }}</text></view>
      <view v-if="order.unitPrice != null" class="info-row"><text class="label">单价</text><text class="value">¥{{ formatMoney(order.unitPrice) }}</text></view>
      <view v-if="order.quantity > 1" class="info-row"><text class="label">数量</text><text class="value">{{ order.quantity }}</text></view>

      <!-- 价格明细：有优惠券时展示原价 / 优惠 / 实付 -->
      <view v-if="hasCoupon" class="price-breakdown">
        <view class="price-row">
          <text class="price-label">商品原价</text>
          <text class="price-original">¥{{ formatMoney(order.originalAmount) }}</text>
        </view>
        <view class="coupon-strip">
          <view class="coupon-strip-badge">券</view>
          <view class="coupon-strip-info">
            <text class="coupon-strip-name">{{ order.couponName }}</text>
            <text class="coupon-strip-desc">{{ couponTypeLabel }}</text>
          </view>
          <text class="coupon-strip-discount">-¥{{ formatMoney(order.couponDiscountAmount) }}</text>
        </view>
        <view class="price-divider" />
        <view class="price-row total">
          <text class="price-label">实付金额</text>
          <PriceText :value="order.amount" :size="36" />
        </view>
      </view>
      <view v-else class="info-row">
        <text class="label">金额</text>
        <PriceText :value="order.amount" :size="32" />
      </view>
    </view>
    <!-- 动态字段 -->
    <view class="card" v-if="parsedExtra.length">
      <view class="card-header"><view class="title-bar" /><text class="card-title">订单详情</text></view>
      <view v-for="item in parsedExtra" :key="item.key" class="info-row">
        <text class="label">{{ item.key }}</text>
        <text class="value">{{ item.value }}</text>
      </view>
    </view>
    <!-- 接单员信息 -->
    <view class="card" v-if="order.playerName || order.playerId">
      <view class="card-header"><view class="title-bar" /><text class="card-title">接单员信息</text></view>
      <view class="info-row"><text class="label">接单员</text><text class="value">{{ order.playerName || '已指派' }}</text></view>
      <view v-if="order.playerPhone" class="info-row"><text class="label">联系</text><text class="value">{{ order.playerPhone }}</text></view>
    </view>
    <!-- 进度时间线 -->
    <view class="card" v-if="progressList.length">
      <view class="card-header"><view class="title-bar" /><text class="card-title">订单进度</text></view>
      <view class="timeline">
        <view v-for="(p, idx) in progressList" :key="idx" class="timeline-item">
          <view class="timeline-dot" :class="{ active: idx === 0 }" />
          <view class="timeline-line" v-if="idx < progressList.length - 1" />
          <view class="timeline-content">
            <text class="progress-desc">{{ p.content || p.description || ORDER_STATUS_TEXT[p.status] || p.status }}</text>
            <text class="progress-time">{{ p.createdAt }}</text>
            <view v-if="p.images" class="progress-images">
              <image v-for="(img, i) in p.images.split(',')" :key="i" :src="img" mode="aspectFill" class="progress-img" lazy-load @click="previewImg(p.images.split(','), i)" />
            </view>
          </view>
        </view>
      </view>
    </view>
    <!-- 操作按钮 -->
    <view class="actions">
      <view v-if="order.status==='PENDING_PAYMENT'" class="btn" @click="goPay">去支付</view>
      <view v-if="order.status==='PENDING_PAYMENT'" class="btn-ghost" @click="doCancel">取消订单</view>
      <view v-if="['PAID','ASSIGNED'].includes(order.status)" class="btn-warn" @click="doRefund">申请退款</view>
      <view v-if="order.status==='PAID' && !order.playerId" class="btn" @click="showDesignatePicker = true">指定接单员</view>
      <view v-if="order.status==='COMPLETED'" class="btn" @click="doConfirm">确认完成</view>
      <view v-if="order.status==='CONFIRMED' && !order.reviewed" class="btn" @click="goReview">⭐ 去评价</view>
      <view v-if="!isUnderReview && ['IN_PROGRESS','COMPLETED','CONFIRMED'].includes(order.status)" class="btn-ghost" @click="goChat">💬 聊天</view>
      <view v-if="order.status==='IN_PROGRESS'" class="btn-warn" @click="showReplaceModal=true">申请换人</view>
      <view v-if="['IN_PROGRESS','COMPLETED'].includes(order.status)" class="btn-warn" @click="goComplaint">投诉</view>
    </view>
    <!-- 时间信息 -->
    <view class="card time-card">
      <view class="info-row"><text class="label">创建时间</text><text class="value">{{ order.createdAt }}</text></view>
      <view v-if="order.paidAt" class="info-row"><text class="label">支付时间</text><text class="value">{{ order.paidAt }}</text></view>
      <view v-if="order.completedAt" class="info-row"><text class="label">完成时间</text><text class="value">{{ order.completedAt }}</text></view>
    </view>
    <!-- 接单员选择弹窗（底部滑出，复用下单页样式） -->
    <view v-if="showDesignatePicker" class="modal-mask" @click="showDesignatePicker=false">
      <view class="player-picker" :class="{ 'slide-up': showDesignatePicker }" @click.stop>
        <view class="picker-handle"><view class="handle-bar" /></view>
        <view class="picker-header">
          <text class="picker-title">选择接单员</text>
          <text class="picker-close" @click="showDesignatePicker=false">✕</text>
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
            v-for="p in playerList"
            :key="p.id"
            class="picker-item"
            :class="{ full: isFull(p), offline: !isOnline(p) }"
            @click="pickPlayer(p)"
          >
            <image :src="p.avatar || '/static/images/default-avatar.png'" class="picker-avatar" mode="aspectFill" lazy-load />
            <view class="picker-info">
              <view class="picker-name-row">
                <text class="picker-name">{{ p.nickname || '-' }}</text>
                <text v-if="isOnline(p)" class="online-badge">在线</text>
                <text v-else class="offline-badge">离线</text>
              </view>
              <text class="picker-stats">
                完成{{ p.completedOrders || 0 }}单 · 进行中{{ p.activeOrders || 0 }}/{{ maxConcurrent }}
                <text v-if="isFull(p)">（已满载）</text>
              </text>
            </view>
            <text v-if="isOnline(p) && designateSelectedPlayer && designateSelectedPlayer.id === p.id" class="picker-check">✓</text>
          </view>
          <view v-if="playerList.length === 0" class="picker-empty">暂无可用接单员</view>
        </scroll-view>
        <view class="picker-footer">
          <view class="picker-btn cancel" @click="showDesignatePicker=false">取消</view>
          <view class="picker-btn confirm" @click="showDesignatePicker=false">确定</view>
        </view>
      </view>
    </view>
    <!-- 换人弹窗 -->
    <view v-if="showReplaceModal" class="modal-mask" @click="showReplaceModal=false">
      <view class="replace-modal" @click.stop>
        <text class="modal-title">申请换人</text>
        <textarea v-model="replaceReason" placeholder="请输入换人原因" :maxlength="200" class="modal-textarea" />
        <view class="modal-actions">
          <view class="modal-btn cancel" @click="showReplaceModal=false">取消</view>
          <view class="modal-btn confirm" @click="doReplace">提交</view>
        </view>
      </view>
    </view>

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
import { ref, watch, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import StatusTag from '@/components/StatusTag.vue'
import PriceText from '@/components/PriceText.vue'
import { getOrderDetail, getOrderProgress, confirmOrder, cancelOrder, requestReplace, getAvailablePlayers, designatePlayer } from '@/api/order'
import { ORDER_STATUS_TEXT } from '@/utils/constants'
import { requestOrderSubscribe } from '@/utils/subscribe'
import { getCouponTypeLabel } from '@/utils/coupon'
import CsContactModal from '@/components/CsContactModal.vue'
import { useWeworkCs } from '@/composables/useWeworkCs'
import { blockIfUnderReview } from '@/composables/useAuditGuard'
import { useAuditMode } from '@/composables/useAuditMode'

const { modalState: csModal, openWeworkCs } = useWeworkCs()
const { isUnderReview } = useAuditMode()
const order = ref(null)
const orderId = ref(0)
const progressList = ref([])
const hasCoupon = computed(() => {
  const o = order.value
  return o && o.couponName && Number(o.couponDiscountAmount) > 0
})

const couponTypeLabel = computed(() => {
  if (!order.value?.couponType) return ''
  return getCouponTypeLabel({ couponType: order.value.couponType, discountRate: inferRate(order.value.couponType) })
})

function inferRate(type) {
  if (type === 'DISCOUNT_9') return 0.9
  if (type === 'DISCOUNT_8') return 0.8
  if (type === 'DISCOUNT_75') return 0.75
  return 0
}

function formatMoney(val) {
  return Number(val || 0).toFixed(2)
}

const parsedExtra = computed(() => {
  const o = order.value
  if (!o) return []
  if (o.extraFields) {
    try {
      const obj = typeof o.extraFields === 'string' ? JSON.parse(o.extraFields) : o.extraFields
      return Object.entries(obj).map(([key, value]) => ({ key, value }))
    } catch { /* fall through */ }
  }
  const legacy = []
  if (o.gameAccount) legacy.push({ key: '关联账号', value: o.gameAccount })
  if (o.contact) legacy.push({ key: '联系ID', value: o.contact })
  if (o.remark) legacy.push({ key: '备注', value: o.remark })
  return legacy
})

onLoad(async (opts) => {
  if (await blockIfUnderReview()) return
  orderId.value = opts.id
})
onShow(async () => {
  if (await blockIfUnderReview()) return
  unlockH5TabbarPageScroll()
  loadDetail()
})

function unlockH5TabbarPageScroll() {
  // #ifdef H5
  document.documentElement.classList.remove('tabbar-page-locked')
  document.body.classList.remove('tabbar-page-locked')
  // #endif
}

async function loadDetail() {
  const res = await getOrderDetail(orderId.value)
  order.value = res.data
  try {
    const pRes = await getOrderProgress(orderId.value)
    progressList.value = pRes.data || []
  } catch (e) { progressList.value = [] }
}

function goPay() {
  let url = `/pages/order/pay?orderId=${orderId.value}&amount=${order.value.amount}`
  if (hasCoupon.value) {
    url += `&couponId=${order.value.userCouponId}`
    url += `&discountAmount=${order.value.couponDiscountAmount}`
    url += `&couponName=${encodeURIComponent(order.value.couponName)}`
  }
  uni.navigateTo({ url })
}
async function doCancel() {
  uni.showModal({ title: '提示', content: '确定取消订单？', success: async (r) => {
    if (r.confirm) { await cancelOrder(orderId.value); uni.showToast({ title: '已取消' }); loadDetail() }
  }})
}
async function doRefund() {
  uni.showModal({ title: '提示', content: '确定申请退款？订单将被取消并退回支付金额？', success: async (r) => {
    if (r.confirm) { await cancelOrder(orderId.value); uni.showToast({ title: '退款申请已提交' }); loadDetail() }
  }})
}
async function doConfirm() {
  uni.showModal({ title: '提示', content: '确认服务已完成？', success: async (r) => {
    if (r.confirm) {
      await requestOrderSubscribe()
      await confirmOrder(orderId.value); uni.showToast({ title: '已确认' }); loadDetail()
    }
  }})
}
function goReview() { uni.navigateTo({ url: `/pages/review/create?orderId=${orderId.value}` }) }
function goChat() {
  const name = order.value?.playerName || ''
  uni.navigateTo({ url: `/pages/chat/room?orderId=${orderId.value}&name=${encodeURIComponent(name)}` })
}
function goComplaint() {
  openWeworkCs({ scene: 'complaint', order: order.value })
}
const showReplaceModal = ref(false)
const replaceReason = ref('')
const showDesignatePicker = ref(false)
const playerKeyword = ref('')
const playerList = ref([])
const designateSelectedPlayer = ref(null)
const maxConcurrent = ref(5)

watch(showDesignatePicker, (val) => {
  if (val) searchPlayers()
})

async function doReplace() {
  if (!replaceReason.value.trim()) return uni.showToast({ title: '请输入换人原因', icon: 'none' })
  try {
    await requestReplace(orderId.value, { reason: replaceReason.value })
    uni.showToast({ title: '已提交申请' })
    showReplaceModal.value = false
    replaceReason.value = ''
  } catch (e) {
    uni.showToast({ title: e?.data?.msg || '提交失败', icon: 'none' })
  }
}
function previewImg(imgs, idx) { uni.previewImage({ urls: imgs, current: idx }) }

async function searchPlayers() {
  try {
    const res = await getAvailablePlayers({ keyword: playerKeyword.value })
    const data = res.data || {}
    if (data.maxConcurrent) maxConcurrent.value = data.maxConcurrent
    const page = data.players || {}
    playerList.value = page.records || []
  } catch (e) {
    playerList.value = []
  }
}

function isOnline(p) {
  return p.isOnline === 1
}

async function pickPlayer(p) {
  if (!isOnline(p)) {
    return uni.showToast({ title: '该接单员当前离线，无法指定', icon: 'none' })
  }
  if (isFull(p)) {
    return uni.showToast({
      title: `该接单员当前进行中 ${p.activeOrders || 0} 单，已达上限 ${maxConcurrent.value}`,
      icon: 'none'
    })
  }
  designateSelectedPlayer.value = p
  try {
    await designatePlayer(orderId.value, p.id)
    uni.showToast({ title: '已指定接单员' })
    showDesignatePicker.value = false
    designateSelectedPlayer.value = null
    loadDetail()
  } catch (e) {
    uni.showToast({ title: e?.data?.msg || '指定失败', icon: 'none' })
  }
}

function isFull(p) {
  return (p.activeOrders || 0) >= maxConcurrent.value
}
</script>
<style lang="scss" scoped>
.order-detail { background: #f5f7fa; min-height: 100vh; padding-bottom: 40rpx; }
.status-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 28rpx 32rpx; background: #fff;
  box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.04);
  .order-no { font-size: 24rpx; color: #94a3b8; }
}
.card {
  margin: 20rpx 24rpx 0; padding: 28rpx 28rpx 24rpx;
  background: #fff; border-radius: 16rpx;
  box-shadow: 0 2rpx 16rpx rgba(0,0,0,0.04);
}
.card-header { display: flex; align-items: center; margin-bottom: 20rpx; }
.title-bar {
  width: 6rpx; height: 28rpx; border-radius: 3rpx;
  background: linear-gradient(180deg, #ff4544, #e63939);
  margin-right: 12rpx;
}
.card-title { font-size: 28rpx; font-weight: bold; color: #1e293b; }
.info-row {
  display: flex; align-items: center; padding: 12rpx 0;
  .label { font-size: 26rpx; color: #94a3b8; width: 140rpx; flex-shrink: 0; }
  .value { font-size: 26rpx; color: #1e293b; flex: 1; }
}

.price-breakdown {
  margin-top: 8rpx;
  padding: 20rpx;
  background: linear-gradient(135deg, #fff8f8, #fff);
  border-radius: 12rpx;
  border: 1rpx solid rgba(255, 69, 68, 0.12);
}
.price-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8rpx 0;
  .price-label { font-size: 26rpx; color: #64748b; }
  .price-original {
    font-size: 26rpx;
    color: #94a3b8;
    text-decoration: line-through;
  }
  &.total .price-label { font-size: 28rpx; color: #1e293b; font-weight: 600; }
}
.price-divider {
  height: 1rpx;
  background: #f1f5f9;
  margin: 12rpx 0;
}
.coupon-strip {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin: 12rpx 0;
  padding: 16rpx;
  background: #fff;
  border-radius: 10rpx;
  border: 1rpx dashed rgba(255, 69, 68, 0.25);
}
.coupon-strip-badge {
  width: 44rpx;
  height: 44rpx;
  line-height: 44rpx;
  text-align: center;
  background: linear-gradient(135deg, #ff6b6a, #ff4544);
  color: #fff;
  font-size: 22rpx;
  font-weight: bold;
  border-radius: 8rpx;
  flex-shrink: 0;
}
.coupon-strip-info {
  flex: 1;
  min-width: 0;
  .coupon-strip-name {
    font-size: 26rpx;
    color: #1e293b;
    font-weight: 600;
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .coupon-strip-desc {
    font-size: 22rpx;
    color: #94a3b8;
    margin-top: 4rpx;
    display: block;
  }
}
.coupon-strip-discount {
  font-size: 28rpx;
  color: #ff4544;
  font-weight: 700;
  flex-shrink: 0;
}
.timeline { padding-left: 24rpx; }
.timeline-item { display: flex; position: relative; padding-bottom: 28rpx; }
.timeline-dot { width: 16rpx; height: 16rpx; border-radius: 50%; background: #cbd5e1; margin-top: 8rpx; flex-shrink: 0; &.active { background: #ff4544; box-shadow: 0 0 0 4rpx rgba(255,69,68,0.2); } }
.timeline-line { position: absolute; left: 7rpx; top: 28rpx; bottom: 0; width: 2rpx; background: #e2e8f0; }
.timeline-content { margin-left: 20rpx; flex: 1; }
.progress-desc { font-size: 26rpx; color: #1e293b; display: block; }
.progress-time { font-size: 22rpx; color: #94a3b8; display: block; margin-top: 6rpx; }
.progress-images { display: flex; gap: 12rpx; margin-top: 12rpx; }
.progress-img { width: 120rpx; height: 120rpx; border-radius: 12rpx; }
.actions { display: flex; gap: 20rpx; padding: 32rpx 24rpx; flex-wrap: wrap; justify-content: flex-end; }
.btn { padding: 16rpx 40rpx; background: linear-gradient(135deg, #ff4544, #e63939); color: #ffffff; font-weight: bold; font-size: 28rpx; border-radius: 999rpx; }
.btn-ghost { padding: 16rpx 40rpx; border: 1rpx solid rgba(255,69,68,0.4); color: #ff4544; font-size: 28rpx; border-radius: 999rpx; }
.btn-warn { padding: 16rpx 40rpx; border: 1rpx solid rgba(238,10,36,0.4); color: #ee0a24; font-size: 28rpx; border-radius: 999rpx; }
.time-card { margin-bottom: 0; }
.modal-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0,0,0,0.6);
  z-index: 999;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}
.replace-modal { width: 560rpx; background: #ffffff; border-radius: 20rpx; padding: 40rpx; border: 1rpx solid #e2e8f0; }
.modal-title { font-size: 32rpx; font-weight: bold; color: #ff4544; display: block; text-align: center; margin-bottom: 24rpx; }
.modal-textarea { width: 100%; height: 160rpx; background: #f1f5f9; border: none; border-radius: 8rpx; padding: 16rpx; font-size: 28rpx; color: #1e293b; }
.modal-actions { display: flex; gap: 20rpx; margin-top: 24rpx; }
.modal-btn { flex: 1; height: 72rpx; line-height: 72rpx; text-align: center; border-radius: 999rpx; font-size: 28rpx; }
.modal-btn.cancel { border: 1rpx solid #cbd5e1; color: #64748b; }
.modal-btn.confirm { background: linear-gradient(135deg, #ff4544, #e63939); color: #ffffff; font-weight: bold; }
/* 接单员选择弹窗（底部滑出，同下单页样式） */
.player-picker {
  width: 100%;
  max-height: 80vh;
  background: #ffffff;
  border-radius: 24rpx 24rpx 0 0;
  padding: 0 24rpx 24rpx;
  padding-bottom: calc(24rpx + env(safe-area-inset-bottom));
  animation: slideUp 0.3s ease-out;
}
.player-picker.slide-up {
  animation: slideUp 0.3s ease-out;
}
@keyframes slideUp {
  from { transform: translateY(100%); }
  to { transform: translateY(0); }
}
.picker-handle {
  display: flex;
  justify-content: center;
  padding: 16rpx 0;
  .handle-bar { width: 64rpx; height: 8rpx; background: #e2e8f0; border-radius: 4rpx; }
}
.picker-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16rpx; }
.picker-title { font-size: 32rpx; font-weight: bold; color: #ff4544; }
.picker-close { font-size: 36rpx; color: #94a3b8; padding: 0 8rpx; }
.limit-tip {
  padding: 12rpx 24rpx;
  font-size: 24rpx;
  color: rgba(0,0,0,0.45);
  background: rgba(0,0,0,0.04);
  text-align: center;
  border-radius: 8rpx;
  margin-bottom: 16rpx;
  .gold { color: #ff4544; font-weight: bold; }
}
.picker-search {
  display: flex;
  gap: 16rpx;
  margin-bottom: 16rpx;
  input {
    flex: 1;
    height: 72rpx;
    background: #f1f5f9;
    padding: 0 24rpx;
    border-radius: 999rpx;
    font-size: 26rpx;
    color: #1e293b;
    box-sizing: border-box;
  }
  .search-btn {
    padding: 0 32rpx;
    height: 72rpx;
    line-height: 72rpx;
    background: linear-gradient(135deg, #ff4544, #e63939);
    color: #ffffff;
    font-weight: bold;
    border-radius: 999rpx;
    font-size: 26rpx;
    flex-shrink: 0;
  }
}
.picker-list { max-height: 480rpx; }
.picker-item {
  display: flex;
  align-items: center;
  padding: 20rpx 0;
  background: rgba(0,0,0,0.02);
  border-radius: 12rpx;
  margin-bottom: 12rpx;
  &.full { opacity: 0.5; }
  &.offline { opacity: 0.5; }
}
.picker-avatar { width: 80rpx; height: 80rpx; border-radius: 50%; flex-shrink: 0; margin-right: 20rpx; }
.picker-info { flex: 1; overflow: hidden; }
.picker-name-row { display: flex; align-items: center; gap: 8rpx; }
.picker-name { font-size: 28rpx; color: #1e293b; font-weight: 500; }
.online-badge {
  font-size: 20rpx; color: #22c55e; background: rgba(34,197,94,0.15);
  padding: 2rpx 10rpx; border-radius: 4rpx;
}
.offline-badge {
  font-size: 20rpx; color: #94a3b8; background: rgba(148,163,184,0.15);
  padding: 2rpx 10rpx; border-radius: 4rpx;
}
.picker-stats { font-size: 22rpx; color: #94a3b8; display: block; margin-top: 4rpx; }
.picker-check { font-size: 28rpx; color: #ff4544; font-weight: bold; }
.picker-empty { text-align: center; padding: 40rpx; font-size: 26rpx; color: #94a3b8; }
.picker-footer {
  display: flex;
  gap: 20rpx;
  margin-top: 24rpx;
}
.picker-btn {
  flex: 1;
  height: 72rpx;
  line-height: 72rpx;
  text-align: center;
  border-radius: 999rpx;
  font-size: 28rpx;
}
.picker-btn.cancel {
  border: 1rpx solid #cbd5e1;
  color: #64748b;
  background: #ffffff;
}
.picker-btn.confirm {
  background: linear-gradient(135deg, #ff4544, #e63939);
  color: #ffffff;
  font-weight: bold;
}
</style>
