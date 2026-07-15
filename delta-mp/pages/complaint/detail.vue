<template>
  <view class="complaint-detail" v-if="complaint">
    <view class="card">
      <view class="info-row">
        <text class="label">投诉类型</text>
        <text class="value">{{ complaint.type }}</text>
      </view>
      <view class="info-row">
        <text class="label">处理状态</text>
        <StatusTag :status="complaint.status" :textMap="COMPLAINT_STATUS_TEXT" :colorMap="complaintColorMap" />
      </view>
      <view class="info-row">
        <text class="label">创建时间</text>
        <text class="value">{{ complaint.createdAt }}</text>
      </view>
    </view>
    <view class="card">
      <text class="card-title">投诉内容</text>
      <text class="content-text">{{ complaint.content }}</text>
      <view v-if="complaint.images" class="evidence-images">
        <image v-for="(img, idx) in complaint.images.split(',')" :key="idx" :src="img" mode="aspectFill" class="ev-img" lazy-load @click="previewImg(complaint.images.split(','), idx)" />
      </view>
      <view v-if="complaint.expectedResult" class="expected">
        <text class="label">期望结果</text>
        <text class="value">{{ complaint.expectedResult }}</text>
      </view>
    </view>
    <!-- 处理结果 -->
    <view class="card" v-if="complaint.resultReason || complaint.result">
      <text class="card-title">处理结果</text>
      <text v-if="complaint.resultReason" class="content-text">{{ complaint.resultReason }}</text>
      <view v-if="complaint.refundAmount" class="info-row">
        <text class="label">退款金额</text>
        <text class="value refund">¥{{ Number(complaint.refundAmount).toFixed(2) }}</text>
      </view>
      <view v-if="complaint.playerPenalty && complaint.playerPenalty !== 'NONE'" class="info-row">
        <text class="label">处罚措施</text>
        <text class="value">{{ { FREEZE: '冻结账号', WARNING: '警告', DEDUCT: '扣款' }[complaint.playerPenalty] || complaint.playerPenalty }}</text>
      </view>
    </view>
    <!-- 申诉按钮 -->
    <view class="actions" v-if="complaint.status === 'RESOLVED'">
      <view class="btn" @click="showAppeal = true">我要申诉</view>
    </view>
    <!-- 申诉弹窗 -->
    <view class="modal-mask" v-if="showAppeal" @click="showAppeal=false">
      <view class="modal-body" @click.stop>
        <text class="modal-title">提交申诉</text>
        <textarea v-model="appealContent" placeholder="请说明申诉原因" :maxlength="500" />
        <view class="modal-btns">
          <view class="modal-btn cancel" @click="showAppeal=false">取消</view>
          <view class="modal-btn confirm" @click="submitAppeal">提交</view>
        </view>
      </view>
    </view>
  </view>
  <!-- 关联订单 -->
  <view class="card" v-if="order">
    <text class="card-title">关联订单</text>
    <view class="info-row">
      <text class="label">订单号</text>
      <text class="value">{{ order.orderNo }}</text>
    </view>
    <view class="info-row">
      <text class="label">商品</text>
      <text class="value">{{ order.productName }}</text>
    </view>
    <view class="info-row">
      <text class="label">订单状态</text>
      <text class="value">{{ order.status }}</text>
    </view>
  </view>
  <!-- 订单进度 -->
  <view class="card" v-if="progress && progress.length">
    <text class="card-title">订单进度</text>
    <view v-for="p in progress" :key="p.id" class="info-row">
      <text class="label">{{ p.createdAt }}</text>
      <text class="value">{{ p.content }}</text>
    </view>
  </view>
</template>
<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import StatusTag from '@/components/StatusTag.vue'
import { getComplaintDetail, appealComplaint } from '@/api/complaint'
import { COMPLAINT_STATUS_TEXT } from '@/utils/constants'

const complaintColorMap = { PENDING: '#ff9900', PROCESSING: '#ff4544', RESOLVED: '#07c160', APPEALING: '#ee0a24', APPEAL_RESOLVED: '#07c160' }
const complaint = ref(null)
const order = ref(null)
const progress = ref([])
const complaintId = ref(0)
const showAppeal = ref(false)
const appealContent = ref('')

onLoad(async (opts) => {
  complaintId.value = opts.id
  const res = await getComplaintDetail(opts.id)
  // 后端现在返回 ComplaintDetailVO，兼容旧结构
  if (res.data && res.data.complaint) {
    complaint.value = res.data.complaint
    order.value = res.data.order || null
    progress.value = res.data.progress || []
  } else {
    complaint.value = res.data
  }
})

async function submitAppeal() {
  if (!appealContent.value.trim()) return uni.showToast({ title: '请输入申诉原因', icon: 'none' })
  await appealComplaint(complaintId.value, { appealReason: appealContent.value })
  showAppeal.value = false
  uni.showToast({ title: '申诉已提交' })
  const res = await getComplaintDetail(complaintId.value)
  complaint.value = res.data
}

function previewImg(imgs, idx) { uni.previewImage({ urls: imgs, current: idx }) }
</script>
<style lang="scss" scoped>
.complaint-detail { background: #f1f5f9; min-height: 100vh; }
.card { margin-top: 20rpx; padding: 24rpx; background: #ffffff; border: 1rpx solid #f1f5f9; }
.card-title { font-size: 28rpx; font-weight: bold; display: block; margin-bottom: 16rpx; color: #6366f1; }
.info-row { display: flex; align-items: center; padding: 8rpx 0; .label { font-size: 26rpx; color: #94a3b8; width: 140rpx; } .value { font-size: 26rpx; color: #1e293b; &.refund { color: #ee0a24; font-weight: bold; } } }
.content-text { font-size: 28rpx; color: #1e293b; line-height: 1.6; display: block; }
.evidence-images { display: flex; gap: 12rpx; margin-top: 16rpx; flex-wrap: wrap; }
.ev-img { width: 160rpx; height: 160rpx; border-radius: 8rpx; }
.expected { margin-top: 16rpx; padding-top: 16rpx; border-top: 1rpx solid #f1f5f9; }
.actions { padding: 40rpx 24rpx; }
.btn { height: 88rpx; line-height: 88rpx; text-align: center; background: linear-gradient(135deg, #ff4544, #e63939); color: #ffffff; font-weight: bold; font-size: 32rpx; border-radius: 999rpx; }
.modal-mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.7); display: flex; align-items: center; justify-content: center; z-index: 999; }
.modal-body { width: 80%; background: #ffffff; border: 1rpx solid rgba(99, 102, 241, 0.15); border-radius: 16rpx; padding: 40rpx; textarea { width: 100%; height: 200rpx; background: #f1f5f9; border: 1rpx solid #e2e8f0; border-radius: 8rpx; padding: 20rpx; font-size: 28rpx; margin: 20rpx 0; color: #1e293b; } }
.modal-title { font-size: 32rpx; font-weight: bold; text-align: center; display: block; color: #6366f1; }
.modal-btns { display: flex; gap: 24rpx; }
.modal-btn { flex: 1; height: 76rpx; line-height: 76rpx; text-align: center; border-radius: 999rpx; font-size: 28rpx; &.cancel { background: #f1f5f9; color: #64748b; } &.confirm { background: linear-gradient(135deg, #ff4544, #e63939); color: #ffffff; font-weight: bold; } }
</style>
