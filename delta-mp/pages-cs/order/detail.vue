<template>
  <AuditPlaceholder v-if="pageBlocked" />
  <view v-else class="cs-order-detail" v-if="order">
    <!-- 状态栏 -->
    <view class="status-bar">
      <StatusTag :status="order.status" />
      <view class="status-right">
        <text class="order-no">{{ order.orderNo }}</text>
        <text class="copy-link" @click="copyOrderNo">复制单号</text>
      </view>
    </view>
    <!-- 订单信息 -->
    <view class="card">
      <view class="card-header"><view class="title-bar" /><text class="card-title">订单信息</text></view>
      <view class="info-row"><text class="label">商品</text><text class="value bold">{{ order.productName }}</text></view>
      <view v-if="order.variantName || order.specCombination || order.specInfo" class="info-row"><text class="label">规格</text><text class="value">{{ order.variantName || order.specCombination || order.specInfo }}</text></view>
      <view v-if="order.unitPrice != null" class="info-row"><text class="label">单价</text><text class="value">¥{{ Number(order.unitPrice).toFixed(2) }}</text></view>
      <view v-if="order.quantity > 1" class="info-row"><text class="label">数量</text><text class="value">{{ order.quantity }}</text></view>
      <view class="info-row"><text class="label">金额</text><text class="value price">¥{{ Number(order.amount).toFixed(2) }}</text></view>
      <view class="info-row"><text class="label">下单时间</text><text class="value">{{ order.createdAt }}</text></view>
    </view>
    <!-- 动态字段 -->
    <view class="card" v-if="parsedExtra.length">
      <view class="card-header"><view class="title-bar" /><text class="card-title">订单详情</text></view>
      <view v-for="item in parsedExtra" :key="item.key" class="info-row info-row-copy">
        <text class="label">{{ item.key }}</text>
        <text class="value">{{ item.value }}</text>
        <text class="copy-link row-copy-link" @click="copyDetailItem(item)">复制</text>
      </view>
    </view>
    <!-- 用户信息 -->
    <view class="card">
      <view class="card-header"><view class="title-bar" /><text class="card-title">用户信息</text></view>
      <view class="info-row"><text class="label">用户</text><text class="value">{{ order.userNickname || ('ID: ' + order.userId) }}</text></view>
      <view v-if="order.contact" class="info-row"><text class="label">联系</text><text class="value">{{ order.contact }}</text></view>
    </view>
    <!-- 接单员信息 -->
    <view class="card" v-if="order.playerId">
      <view class="card-header"><view class="title-bar" /><text class="card-title">接单员信息</text></view>
      <view class="info-row"><text class="label">接单员</text><text class="value">{{ order.playerName || ('ID: ' + order.playerId) }}</text></view>
    </view>
    <!-- 订单进度 -->
    <view class="card" v-if="progressList.length">
      <view class="card-header"><view class="title-bar" /><text class="card-title">订单进度</text></view>
      <view class="progress-list">
        <view v-for="(p, idx) in progressList" :key="p.id || idx" class="progress-item">
          <view class="progress-dot" :class="{ last: idx === progressList.length - 1 }" />
          <view class="progress-content">
            <text class="progress-time">{{ p.createdAt }}</text>
            <text class="progress-text">{{ p.content || statusLabel(p.toStatus) || p.toStatus }}</text>
            <view v-if="p.images" class="progress-images">
              <image v-for="(img, i) in p.images.split(',')" :key="i" :src="img" mode="aspectFill" class="progress-img" lazy-load @click="previewImg(p.images.split(','), i)" />
            </view>
          </view>
        </view>
      </view>
    </view>
    <!-- 操作按钮 -->
    <view class="actions">
      <view v-if="order.status==='PAID'||order.status==='PENDING'" class="btn" @click="goAssign">指派接单员</view>
      <view v-if="order.status==='COMPLETED'" class="btn" @click="doConfirm">结单</view>
      <view v-if="['ASSIGNED','IN_PROGRESS'].includes(order.status)" class="btn-warn" @click="doRefund">退款</view>
      <view class="btn-ghost" @click="goChat">联系用户</view>
    </view>
  </view>

</template>
<script setup>
import AuditPlaceholder from '@/components/AuditPlaceholder.vue'
import { useAuditPageGuard } from '@/composables/useAuditPageGuard'
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import StatusTag from '@/components/StatusTag.vue'
import { getCsOrderDetail, getCsOrderProgress, csRefundOrder, csConfirmOrder } from '@/pages-cs/api/cs'
const { pageBlocked } = useAuditPageGuard()

const statusLabelMap = {
  PENDING_PAYMENT: '待支付', PAID: '待接单', ASSIGNED: '已指派', ACCEPTED: '已接单',
  WAITING_TEAMMATE: '组队中', IN_PROGRESS: '进行中', COMPLETED: '待确认',
  CONFIRMED: '已完成', REVIEWED: '已评价', CANCELLED: '已取消',
  REFUNDING: '退款中', REFUNDED: '已退款', DISPUTED: '争议中', ARBITRATED: '已仲裁'
}
function statusLabel(s) { return statusLabelMap[s] || s }

const order = ref(null)
const progressList = ref([])
const orderId = ref('')
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
  orderId.value = opts.id
  await loadDetail()
})

onShow(() => {
  unlockH5TabbarPageScroll()
})

function unlockH5TabbarPageScroll() {
  // #ifdef H5
  document.documentElement.classList.remove('tabbar-page-locked')
  document.body.classList.remove('tabbar-page-locked')
  // #endif
}

function goAssign() { uni.navigateTo({ url: '/pages-cs/order/assign?orderId=' + orderId.value }) }
function goChat() { uni.navigateTo({ url: '/pages-cs/chat/room?orderId=' + orderId.value }) }
function previewImg(imgs, idx) { uni.previewImage({ urls: imgs, current: idx }) }
function copyText(text, title) {
  if (!text) {
    return uni.showToast({ title: '暂无可复制内容', icon: 'none' })
  }
  uni.setClipboardData({
    data: text,
    success: () => uni.showToast({ title, icon: 'none' })
  })
}
function copyOrderNo() {
  copyText(order.value?.orderNo || '', '订单号已复制')
}
function copyDetailItem(item) {
  copyText(String(item?.value ?? ''), `${item?.key || '内容'}已复制`)
}
async function loadDetail() {
  const [detailRes, progressRes] = await Promise.all([
    getCsOrderDetail(orderId.value),
    getCsOrderProgress(orderId.value)
  ])
  order.value = detailRes.data
  progressList.value = progressRes.data || []
}
async function doRefund() {
  uni.showModal({
    title: '提示',
    content: '确定为该订单退款？订单将被取消并退回支付金额。',
    success: async (r) => {
      if (r.confirm) {
        try {
          await csRefundOrder(orderId.value)
          uni.showToast({ title: '退款已提交' })
          await loadDetail()
        } catch (e) {
          uni.showToast({ title: e?.data?.msg || '操作失败', icon: 'none' })
        }
      }
    }
  })
}
async function doConfirm() {
  uni.showModal({
    title: '提示',
    content: '确定手动结单？订单将变为已确认状态。',
    success: async (r) => {
      if (r.confirm) {
        try {
          await csConfirmOrder(orderId.value)
          uni.showToast({ title: '已结单' })
          await loadDetail()
        } catch (e) {
          uni.showToast({ title: e?.data?.msg || '操作失败', icon: 'none' })
        }
      }
    }
  })
}
</script>
<style lang="scss" scoped>
.cs-order-detail { background: #f5f7fa; min-height: 100vh; padding-bottom: 40rpx; }
.status-bar {
  display: flex; justify-content: space-between; align-items: center;
  padding: 28rpx 32rpx; background: #fff;
  box-shadow: 0 2rpx 12rpx rgba(0,0,0,0.04);
  .status-right { display: flex; align-items: center; gap: 16rpx; }
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
.copy-link {
  padding: 8rpx 18rpx;
  font-size: 22rpx;
  color: #ff4544;
  border: 1rpx solid rgba(255,69,68,0.28);
  border-radius: 999rpx;
  flex-shrink: 0;
}
.row-copy-link {
  padding: 6rpx 16rpx;
  font-size: 20rpx;
}
.info-row {
  display: flex; align-items: center; padding: 12rpx 0;
  .label { font-size: 26rpx; color: #94a3b8; width: 140rpx; flex-shrink: 0; }
  .value { font-size: 26rpx; color: #1e293b; flex: 1; &.bold { font-weight: 600; } &.price { color: #ee0a24; font-weight: bold; } }
}
.info-row-copy .value { min-width: 0; margin-right: 12rpx; }
.progress-list { padding: 8rpx 0; }
.progress-item { display: flex; align-items: flex-start; margin-bottom: 24rpx; }
.progress-item:last-child { margin-bottom: 0; }
.progress-dot {
  width: 16rpx; height: 16rpx; border-radius: 50%;
  background: #cbd5e1; flex-shrink: 0; margin: 10rpx 16rpx 0 0;
  &.last { background: linear-gradient(135deg, #ff4544, #e63939); }
}
.progress-content { flex: 1; min-width: 0; }
.progress-time { display: block; font-size: 22rpx; color: #94a3b8; margin-bottom: 4rpx; }
.progress-text { font-size: 26rpx; color: #1e293b; }
.progress-images { display: flex; gap: 12rpx; margin-top: 12rpx; flex-wrap: wrap; }
.progress-img { width: 120rpx; height: 120rpx; border-radius: 12rpx; }

.actions { display: flex; gap: 20rpx; padding: 40rpx 24rpx; justify-content: flex-end; }
.btn { padding: 16rpx 40rpx; background: linear-gradient(135deg, #ff4544, #e63939); color: #ffffff; font-weight:bold; font-size: 28rpx; border-radius: 999rpx; }
.btn-ghost { padding: 16rpx 40rpx; border: 1rpx solid rgba(255,69,68,0.4); color: #ff4544; font-size: 28rpx; border-radius: 999rpx; }
.btn-warn { padding: 16rpx 40rpx; border: 1rpx solid rgba(238,10,36,0.4); color: #ee0a24; font-size: 28rpx; border-radius: 999rpx; }
</style>
