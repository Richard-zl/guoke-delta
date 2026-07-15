<template>
  <AuditPlaceholder v-if="pageBlocked" />
  <view v-else class="handle-page" v-if="complaint">
    <!-- 投诉信息 -->
    <view class="card">
      <view class="card-top">
        <text class="type">{{ COMPLAINT_TYPE_TEXT[complaint.type] || complaint.type }}</text>
        <StatusTag :status="complaint.status" :text-map="COMPLAINT_STATUS_TEXT" :color-map="COMPLAINT_STATUS_COLOR" />
      </view>
      <text class="content">{{ complaint.content }}</text>
      <view v-if="complaint.images" class="images">
        <image v-for="(img,i) in complaint.images.split(',')" :key="i" :src="img" mode="aspectFill" class="img" lazy-load @click="previewImg(complaint.images.split(','),i)" />
      </view>
      <view class="info-row"><text class="label">投诉人</text><text class="value">{{ userNickname || complaint.userId }}</text></view>
      <view v-if="order" class="info-row"><text class="label">订单号</text><text class="value">{{ order.orderNo }}</text></view>
      <view v-if="order" class="info-row"><text class="label">订单金额</text><text class="value gold">¥{{ Number(order.amount).toFixed(2) }}</text></view>
      <view v-if="playerNickname" class="info-row"><text class="label">接单员</text><text class="value">{{ playerNickname }}</text></view>
      <view class="info-row"><text class="label">期望结果</text><text class="value">{{ complaint.expectedResult }}</text></view>
      <view class="info-row"><text class="label">提交时间</text><text class="value">{{ formatTime(complaint.createdAt) }}</text></view>
    </view>

    <!-- 受理操作 -->
    <view v-if="complaint.status==='PENDING'" class="card">
      <view class="submit-btn" @click="doAccept">受理投诉</view>
    </view>

    <!-- 仲裁操作 -->
    <view v-if="complaint.status==='PROCESSING' || complaint.status==='APPEALING'" class="card">
      <text class="card-title">仲裁处理</text>
      <view class="form-item">
        <text class="label">仲裁结果</text>
        <view class="result-options">
          <view class="opt" :class="{active: resolveForm.result==='FULL_REFUND'}" @click="resolveForm.result='FULL_REFUND'">全额退款</view>
          <view class="opt" :class="{active: resolveForm.result==='PARTIAL_REFUND'}" @click="resolveForm.result='PARTIAL_REFUND'">部分退款</view>
          <view class="opt" :class="{active: resolveForm.result==='REDO'}" @click="resolveForm.result='REDO'">重新服务</view>
          <view class="opt" :class="{active: resolveForm.result==='REJECT'}" @click="resolveForm.result='REJECT'">驳回投诉</view>
        </view>
      </view>
      <view v-if="resolveForm.result==='PARTIAL_REFUND'" class="form-item">
        <text class="label">退款金额</text>
        <input type="digit" v-model="resolveForm.refundAmount" placeholder="0.00" class="input" />
      </view>
      <view class="form-item">
        <text class="label">接单员处罚</text>
        <view class="result-options">
          <view class="opt" :class="{active: resolveForm.playerPenalty==='NONE'}" @click="resolveForm.playerPenalty='NONE'">无</view>
          <view class="opt" :class="{active: resolveForm.playerPenalty==='WARNING'}" @click="resolveForm.playerPenalty='WARNING'">警告</view>
          <view class="opt" :class="{active: resolveForm.playerPenalty==='FREEZE'}" @click="resolveForm.playerPenalty='FREEZE'">冻结账号</view>
        </view>
      </view>
      <view class="form-item">
        <text class="label">处理说明</text>
        <textarea v-model="resolveForm.resultReason" placeholder="请输入处理说明" class="textarea" />
      </view>
      <view class="submit-btn" @click="doResolve">提交仲裁</view>
    </view>

    <!-- 已处理结果 -->
    <view v-if="complaint.result" class="card result-card">
      <text class="card-title">处理结果</text>
      <view class="info-row"><text class="label">仲裁结果</text><text class="value">{{ RESULT_TEXT[complaint.result] || complaint.result }}</text></view>
      <view v-if="complaint.resultReason" class="info-row"><text class="label">处理说明</text><text class="value">{{ complaint.resultReason }}</text></view>
      <view v-if="complaint.refundAmount" class="info-row"><text class="label">退款金额</text><text class="value" style="color:#ee0a24;font-weight:bold">¥{{ Number(complaint.refundAmount).toFixed(2) }}</text></view>
      <view v-if="complaint.playerPenalty && complaint.playerPenalty !== 'NONE'" class="info-row"><text class="label">接单员处罚</text><text class="value">{{ PENALTY_TEXT[complaint.playerPenalty] || complaint.playerPenalty }}</text></view>
      <view v-if="complaint.resolvedAt" class="info-row"><text class="label">处理时间</text><text class="value">{{ formatTime(complaint.resolvedAt) }}</text></view>
    </view>
  </view>

</template>

<script setup>
import AuditPlaceholder from '@/components/AuditPlaceholder.vue'
import { useAuditPageGuard } from '@/composables/useAuditPageGuard'
import { ref, reactive } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import StatusTag from '@/components/StatusTag.vue'
import { getCsComplaintDetail, processCsComplaint, resolveCsComplaint } from '@/pages-cs/api/cs'
const { pageBlocked } = useAuditPageGuard()

const COMPLAINT_TYPE_TEXT = { SERVICE_QUALITY: '服务质量', ACCOUNT_ISSUE: '账号问题', DELAY: '进度延迟', FRAUD: '欺诈', OTHER: '其他' }
const COMPLAINT_STATUS_TEXT = { PENDING: '待处理', PROCESSING: '处理中', RESOLVED: '已解决', REJECTED: '已驳回', APPEALING: '申诉中' }
const COMPLAINT_STATUS_COLOR = { PENDING: '#ee0a24', PROCESSING: '#ff9900', RESOLVED: '#07c160', REJECTED: '#999', APPEALING: '#ee0a24' }
const RESULT_TEXT = { FULL_REFUND: '全额退款', PARTIAL_REFUND: '部分退款', REJECT: '驳回投诉', REDO: '重新服务' }
const PENALTY_TEXT = { NONE: '无', WARNING: '警告', FREEZE: '冻结账号' }

const complaint = ref(null)
const order = ref(null)
const userNickname = ref('')
const playerNickname = ref('')
const complaintId = ref('')
const resolveForm = reactive({ result: 'FULL_REFUND', refundAmount: '', resultReason: '', playerPenalty: 'NONE' })

onLoad(async (opts) => {
  complaintId.value = opts.id
  await loadDetail(opts.id)
})

async function loadDetail(id) {
  const res = await getCsComplaintDetail(id)
  const data = res.data || {}
  // 后端返回 ComplaintDetailVO: { complaint, order, userNickname, playerNickname, ... }
  complaint.value = data.complaint || data
  order.value = data.order || null
  userNickname.value = data.userNickname || ''
  playerNickname.value = data.playerNickname || ''
}

async function doAccept() {
  try {
    await processCsComplaint(complaintId.value, {})
    uni.showToast({ title: '已受理' })
    await loadDetail(complaintId.value)
  } catch (e) {}
}

async function doResolve() {
  if (!resolveForm.resultReason) return uni.showToast({ title: '请输入处理说明', icon: 'none' })
  try {
    await resolveCsComplaint(complaintId.value, {
      result: resolveForm.result,
      refundAmount: resolveForm.result === 'PARTIAL_REFUND' ? Number(resolveForm.refundAmount) || 0 : null,
      resultReason: resolveForm.resultReason,
      playerPenalty: resolveForm.playerPenalty
    })
    uni.showToast({ title: '仲裁完成' })
    setTimeout(() => uni.navigateBack(), 1500)
  } catch (e) {}
}

function previewImg(imgs, idx) { uni.previewImage({ urls: imgs, current: idx }) }

function formatTime(t) {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 16)
}
</script>
<style lang="scss" scoped>
.handle-page { background: #ffffff; min-height: 100vh; padding-bottom: 40rpx; }
.card { margin: 20rpx 24rpx; padding: 24rpx; background: rgba(0,0,0,0.04); border-radius: 12rpx; box-sizing: border-box; }
.card-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16rpx; .type { font-size: 30rpx; font-weight: bold; } }
.card-title { font-size: 28rpx; font-weight: bold; display: block; margin-bottom: 16rpx; color: #ff4544; }
.content { font-size: 28rpx; color: rgba(0,0,0,0.85); line-height: 1.6; display: block; margin-bottom: 16rpx; word-break: break-all; }
.images { display: flex; gap: 12rpx; margin-bottom: 16rpx; flex-wrap: wrap; .img { width: 160rpx; height: 160rpx; border-radius: 8rpx; } }
.info-row { display: flex; padding: 8rpx 0; .label { font-size: 24rpx; color: rgba(0,0,0,0.3); width: 140rpx; flex-shrink: 0; } .value { font-size: 24rpx; color: rgba(0,0,0,0.85); flex: 1; word-break: break-all; &.gold { color: #ff4544; font-weight: bold; } } }
.form-item { margin-bottom: 24rpx; .label { font-size: 26rpx; color: rgba(0,0,0,0.85); display: block; margin-bottom: 12rpx; } .input { background: rgba(0,0,0,0.05); padding: 16rpx; border-radius: 8rpx; font-size: 26rpx; color: rgba(0,0,0,0.85); } }
.textarea { background: rgba(0,0,0,0.05); padding: 16rpx; border-radius: 8rpx; font-size: 26rpx; width: 100%; height: 160rpx; color: rgba(0,0,0,0.85); box-sizing: border-box; }
.result-options { display: flex; gap: 16rpx; flex-wrap: wrap; .opt { padding: 12rpx 24rpx; background: rgba(0,0,0,0.05); border-radius: 8rpx; font-size: 26rpx; &.active { background: linear-gradient(135deg, #ff4544, #e63939); color: #ffffff; font-weight:bold; } } }
.submit-btn { background: linear-gradient(135deg, #ff4544, #e63939); color: #ffffff; font-weight:bold; text-align: center; padding: 20rpx; border-radius: 999rpx; font-size: 28rpx; margin-top: 16rpx; }
.result-card { border: 1rpx solid rgba(99,102,241,0.1); }
</style>
