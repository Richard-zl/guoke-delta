<template>
  <AuditPlaceholder v-if="pageBlocked" />
  <!-- v-else 与 v-if 不可同节点，否则小程序编译 v-for 会错成 _ctx.item -->
  <view v-else-if="order" class="detail">
    <!-- 状态栏 -->
    <view class="status-bar">
      <view class="status-left"><StatusTag :status="order.status" /></view>
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
      <view class="info-row"><text class="label">订单金额</text><PriceText :value="order.amount" :size="32" /></view>
      <view class="divider" />
      <view class="income-block">
        <view class="income-main">
          <text class="income-label">到手金额</text>
          <text class="income-val">¥{{ incomeAmount(order).toFixed(2) }}</text>
        </view>
        <text class="income-hint">抽佣比例 {{ commissionPercent(order) }}%</text>
      </view>
    </view>
    <!-- 用户信息 -->
    <view class="card" v-if="order.userNickname">
      <view class="card-header"><view class="title-bar" /><text class="card-title">用户信息</text></view>
      <view class="user-row">
        <image class="u-avatar" :src="order.userAvatar || '/static/images/default-avatar.png'" mode="aspectFill" lazy-load />
        <text class="u-name">{{ order.userNickname }}</text>
      </view>
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
    <!-- 队友信息 -->
    <view class="card" v-if="order.teammates && order.teammates.length">
      <view class="card-header"><view class="title-bar" /><text class="card-title">队友信息</text></view>
      <view v-for="t in order.teammates" :key="t.id" class="teammate-row">
        <image class="t-avatar" :src="t.avatar || '/static/images/default-avatar.png'" mode="aspectFill" lazy-load />
        <text class="t-name">{{ t.nickname }}</text>
        <StatusTag :status="t.status" :textMap="teammateStatusText" :colorMap="teammateStatusColor" />
      </view>
    </view>
    <!-- 进度历史 -->
    <view class="card" v-if="progressList.length">
      <view class="card-header"><view class="title-bar" /><text class="card-title">进度历史</text></view>
      <view v-for="(p, idx) in progressList" :key="idx" class="progress-item">
        <text class="p-content">{{ p.content }}</text>
        <text class="p-time">{{ p.createdAt }}</text>
        <view v-if="p.images" class="p-images">
          <image v-for="(img,i) in p.images.split(',')" :key="i" :src="img" mode="aspectFill" class="p-img" lazy-load @click="previewImg(p.images.split(','),i)" />
        </view>
      </view>
    </view>
    <!-- 操作按钮 -->
    <view class="actions">
      <view v-if="order.status==='ASSIGNED'" class="btn" @click="doAccept">接单</view>
      <view v-if="order.status==='ASSIGNED'" class="btn-warn" @click="doReject">拒绝</view>
      <view v-if="['ACCEPTED','WAITING_TEAMMATE'].includes(order.status)" class="btn" @click="doStart">开始服务</view>
      <view v-if="order.status==='IN_PROGRESS'" class="btn" @click="goProgress">提交进度</view>
      <view v-if="order.status==='IN_PROGRESS'" class="btn btn-success" @click="doComplete">完成服务</view>
      <view v-if="order.status==='IN_PROGRESS'" class="btn" @click="showReplaceModal=true">换人</view>
      <view v-if="canInviteTeammate" class="btn" @click="goInvite">邀请队友</view>
      <view v-if="hasPendingInviteOnly" class="btn-ghost" @click="goInviteList">查看待确认邀请</view>
      <view v-if="['ACCEPTED','WAITING_TEAMMATE','IN_PROGRESS','COMPLETED','CONFIRMED'].includes(order.status)" class="btn-ghost" @click="goChat">聊天</view>
    </view>
    <!-- 完成服务弹窗 -->
    <view v-if="showCompleteModal" class="modal-mask" @click="closeCompleteModal">
      <view class="modal-box" @click.stop>
        <text class="modal-title">完成服务</text>
        <text class="modal-sub">系统会自动新增一条名为“结束服务”的进度，必须上传图片后才能完成。</text>
        <view class="complete-block">
          <text class="complete-label">结束服务图片</text>
          <ImageUploader v-model="endServiceImages" :max="6" />
        </view>
        <view class="modal-actions">
          <view class="modal-cancel" @click="closeCompleteModal">取消</view>
          <view class="btn btn-success complete-submit" @click="submitComplete">确认完成</view>
        </view>
      </view>
    </view>
    <!-- 换人弹窗 -->
    <view v-if="showReplaceModal" class="modal-mask" @click="showReplaceModal=false">
      <view class="modal-box" @click.stop>
        <text class="modal-title">换人</text>
        <text class="modal-sub">选择换人方式</text>
        <view class="replace-options">
          <view v-if="hasTeammate" class="replace-opt" @click="doReplace('all')">
            <text class="opt-label">两个都换</text>
            <text class="opt-desc">主接单员和队友都退出，订单回到接单大厅</text>
          </view>
          <view v-if="!hasTeammate" class="replace-opt" @click="doReplace('self')">
            <text class="opt-label">换自己</text>
            <text class="opt-desc">自己退出不拿钱，订单回到接单大厅</text>
          </view>
          <view v-if="hasTeammate" class="replace-opt" @click="doReplace('teammate')">
            <text class="opt-label">换队友</text>
            <text class="opt-desc">踢掉队友，可重新选择分成方式邀请新队友</text>
          </view>
        </view>
        <view class="modal-actions">
          <view class="modal-cancel" @click="showReplaceModal=false">取消</view>
        </view>
      </view>
    </view>
  </view>

</template>
<script setup>
import AuditPlaceholder from '@/components/AuditPlaceholder.vue'
import { useAuditPageGuard } from '@/composables/useAuditPageGuard'
const { pageBlocked } = useAuditPageGuard()

import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import StatusTag from '@/components/StatusTag.vue'
import PriceText from '@/components/PriceText.vue'
import ImageUploader from '@/components/ImageUploader.vue'
import { acceptOrder, startOrder, completeOrder, getPlayerOrderProgress, rejectAssign, replaceSelf, replaceAll, replaceTeammate } from '@/api/player'
import { getOrderDetail } from '@/api/order'
import { requestPlayerSubscribe } from '@/utils/subscribe'
import { usePlayerStore } from '@/store/player'

const playerStore = usePlayerStore()

const order = ref(null)
const orderId = ref(0)
const progressList = ref([])
const parsedExtra = computed(() => {
  const o = order.value
  if (!o) return []
  // 优先展示新版动态字段
  if (o.extraFields) {
    try {
      const obj = typeof o.extraFields === 'string' ? JSON.parse(o.extraFields) : o.extraFields
      return Object.entries(obj).map(([key, value]) => ({ key, value }))
    } catch { /* fall through */ }
  }
  // 兼容老订单：回退展示 gameAccount/contact/remark
  const legacy = []
  if (o.gameAccount) legacy.push({ key: '关联账号', value: o.gameAccount })
  if (o.contact) legacy.push({ key: '联系ID', value: o.contact })
  if (o.remark) legacy.push({ key: '备注', value: o.remark })
  return legacy
})
const showReplaceModal = ref(false)
const showCompleteModal = ref(false)
const endServiceImages = ref([])
const hasTeammate = computed(() => {
  const t = order.value?.teammates
  return t && t.some(m => m.status === 'ACCEPTED' || m.status === 'INVITED')
})
const hasAcceptedTeammate = computed(() => {
  const t = order.value?.teammates
  return t && t.some(m => m.status === 'ACCEPTED')
})
const hasPendingInviteOnly = computed(() => {
  const t = order.value?.teammates
  return t && t.some(m => m.status === 'INVITED') && !hasAcceptedTeammate.value
})
/** 主接打手且仍需组队时可邀请队友（组队中 / 已接单未组满 / 进行中但未组满） */
const canInviteTeammate = computed(() => {
  const o = order.value
  if (!o?.playerId || playerStore.playerId !== o.playerId) return false
  if (hasAcceptedTeammate.value || hasPendingInviteOnly.value) return false
  return ['WAITING_TEAMMATE', 'ACCEPTED', 'IN_PROGRESS'].includes(o.status)
})

const teammateStatusText = { INVITED: '待处理', ACCEPTED: '已接受', REJECTED: '已拒绝', CANCELLED: '已作废', REPLACED: '已更换' }
const teammateStatusColor = { INVITED: '#e6a23c', ACCEPTED: '#07c160', REJECTED: '#ee0a24', CANCELLED: '#999', REPLACED: '#909399' }

onLoad((opts) => { orderId.value = opts.id })
onShow(async () => {
  unlockH5TabbarPageScroll()
  if (!playerStore.playerId) await playerStore.fetchProfile()
  loadDetail()
})

function unlockH5TabbarPageScroll() {
  // #ifdef H5
  document.documentElement.classList.remove('tabbar-page-locked')
  document.body.classList.remove('tabbar-page-locked')
  // #endif
}

/** 订单抽佣比例（0~1），仅用订单字段，无则按 0 */
function commissionRateForOrder(o) {
  if (o && (o.commissionRate === 0 || (o.commissionRate != null && o.commissionRate !== ''))) {
    return Number(o.commissionRate)
  }
  return 0
}
function commissionPercent(o) {
  return Math.round(commissionRateForOrder(o) * 100)
}
function incomeAmount(o) {
  const n = Number(o?.amount) || 0
  return n * (1 - commissionRateForOrder(o))
}

async function loadDetail() {
  const res = await getOrderDetail(orderId.value)
  order.value = res.data
  try {
    const pRes = await getPlayerOrderProgress(orderId.value)
    progressList.value = pRes.data || []
  } catch (e) { progressList.value = [] }
}
async function doAccept() {
  await requestPlayerSubscribe()  // 接单前请求订阅
  await acceptOrder(orderId.value); uni.showToast({ title: '已接单' }); loadDetail()
}
async function doReject() {
  uni.showModal({ title: '提示', content: '确定拒绝该指派？', success: async (r) => {
    if (r.confirm) { await rejectAssign(orderId.value); uni.showToast({ title: '已拒绝' }); uni.navigateBack() }
  }})
}
async function doStart() { await startOrder(orderId.value); uni.showToast({ title: '已开始' }); loadDetail() }
function doComplete() { showCompleteModal.value = true }
function closeCompleteModal() {
  showCompleteModal.value = false
  endServiceImages.value = []
}
async function submitComplete() {
  if (!endServiceImages.value.length) {
    return uni.showToast({ title: '请至少上传一张结束服务图片', icon: 'none' })
  }
  try {
    await requestPlayerSubscribe()
    await completeOrder(orderId.value, { images: endServiceImages.value.join(',') })
    uni.showToast({ title: '已完成' })
    closeCompleteModal()
    loadDetail()
  } catch (e) {
    uni.showToast({ title: e?.data?.msg || '完成失败', icon: 'none' })
  }
}
function goProgress() { uni.navigateTo({ url: '/pages-player/order/progress?orderId=' + orderId.value }) }
function goChat() { uni.navigateTo({ url: '/pages-player/chat/room?orderId=' + orderId.value }) }
function goInvite() { uni.navigateTo({ url: '/pages-player/invite/teammate?orderId=' + orderId.value }) }
function goInviteList() { uni.navigateTo({ url: '/pages-player/invite/list?tab=sent' }) }
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
async function doReplace(type) {
  const msgs = {
    all: '确定两个都换？主接单员和队友都将退出，订单回到接单大厅。',
    self: '确定换自己？你将退出该订单且不参与分成，订单回到接单大厅。',
    teammate: '确定换队友？队友将被移除且不参与分成，你可以重新邀请新队友。'
  }
  uni.showModal({
    title: '确认换人',
    content: msgs[type],
    success: async (r) => {
      if (!r.confirm) return
      try {
        if (type === 'all') await replaceAll(orderId.value)
        else if (type === 'self') await replaceSelf(orderId.value)
        else await replaceTeammate(orderId.value)
        showReplaceModal.value = false
        if (type === 'teammate') {
          uni.showToast({ title: '队友已移除' })
          loadDetail()
        } else {
          uni.showToast({ title: '已退出订单' })
          setTimeout(() => uni.navigateBack(), 1500)
        }
      } catch (e) {
        uni.showToast({ title: e?.msg || '操作失败', icon: 'none' })
      }
    }
  })
}
</script>
<style lang="scss" scoped>
.detail { background: #f5f7fa; min-height: 100vh; padding-bottom: 40rpx; }
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
.card-header {
  display: flex; align-items: center; margin-bottom: 20rpx;
}
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
  .value { font-size: 26rpx; color: #1e293b; flex: 1; &.bold { font-weight: 600; } }
}
.info-row-copy .value { min-width: 0; margin-right: 12rpx; }
.divider { height: 1rpx; background: #f1f5f9; margin: 16rpx 0; }
.income-block {
  background: linear-gradient(135deg, rgba(255,69,68,0.06), rgba(255,69,68,0.02));
  border-radius: 12rpx; padding: 20rpx 24rpx;
}
.income-main { display: flex; align-items: baseline; gap: 12rpx; }
.income-label { font-size: 26rpx; color: #64748b; }
.income-val { font-size: 36rpx; color: #ee0a24; font-weight: bold; }
.income-hint { font-size: 22rpx; color: #94a3b8; margin-top: 6rpx; display: block; }
.teammate-row { display: flex; align-items: center; gap: 12rpx; padding: 16rpx 0; border-bottom: 1rpx solid #f5f7fa; .t-avatar { width: 60rpx; height: 60rpx; border-radius: 50%; } .t-name { flex: 1; font-size: 26rpx; color: #1e293b; } }
.progress-item {
  padding: 16rpx 0; border-bottom: 1rpx solid #f5f7fa;
  .p-content { font-size: 26rpx; color: #1e293b; display: block; }
  .p-time { font-size: 22rpx; color: #94a3b8; display: block; margin-top: 6rpx; }
  .p-images { display: flex; gap: 12rpx; margin-top: 12rpx; }
  .p-img { width: 120rpx; height: 120rpx; border-radius: 12rpx; }
}
.actions { display: flex; gap: 20rpx; padding: 40rpx 24rpx; flex-wrap: wrap; justify-content: flex-end; }
.btn { padding: 16rpx 40rpx; background: linear-gradient(135deg, #ff4544, #e63939); color: #ffffff; font-weight:bold; font-size: 28rpx; border-radius: 999rpx; &.btn-success { background: linear-gradient(135deg, #07c160, #06ad56); } }
.btn-ghost { padding: 16rpx 40rpx; border: 1rpx solid rgba(255,69,68,0.4); color: #ff4544; font-size: 28rpx; border-radius: 999rpx; }
.btn-warn { padding: 16rpx 40rpx; border: 1rpx solid rgba(238,10,36,0.4); color: #ee0a24; font-size: 28rpx; border-radius: 999rpx; }
.user-row { display: flex; align-items: center; gap: 16rpx; .u-avatar { width: 72rpx; height: 72rpx; border-radius: 50%; border: 2rpx solid #f1f5f9; } .u-name { font-size: 28rpx; color: #1e293b; font-weight: 500; } }
/* 换人弹窗 */
.modal-mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.6); display: flex; align-items: center; justify-content: center; z-index: 999; }
.modal-box { width: 85%; background: #ffffff; border-radius: 16rpx; padding: 40rpx 32rpx; border: 1rpx solid #e2e8f0; }
.modal-title { font-size: 32rpx; font-weight: bold; display: block; text-align: center; color: #ff4544; }
.modal-sub { font-size: 24rpx; color: #94a3b8; display: block; text-align: center; margin-top: 8rpx; }
.complete-block { margin-top: 24rpx; }
.complete-label { font-size: 26rpx; color: #1e293b; display: block; margin-bottom: 16rpx; }
.replace-options { margin-top: 24rpx; }
.replace-opt { display: flex; flex-direction: column; padding: 24rpx; margin-bottom: 16rpx; background: #f1f5f9; border-radius: 12rpx;
  &:active { background: rgba(255,69,68,0.1); }
  .opt-label { font-size: 28rpx; font-weight: bold; color: #1e293b; }
  .opt-desc { font-size: 22rpx; color: #94a3b8; margin-top: 6rpx; }
}
.modal-actions { display: flex; gap: 20rpx; margin-top: 32rpx; }
.modal-cancel { flex: 1; text-align: center; padding: 20rpx 0; border: 1rpx solid #cbd5e1; border-radius: 999rpx; font-size: 28rpx; color: #64748b; }
.complete-submit { flex: 1; text-align: center; }
</style>
