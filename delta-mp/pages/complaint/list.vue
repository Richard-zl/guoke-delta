<template>
  <view class="complaint-list-page safe-area-bottom">
    <scroll-view scroll-y class="scroll" @scrolltolower="loadMore">
      <view class="filter-bar">
        <view
          v-for="s in statusOptions"
          :key="s.value"
          class="status-chip"
          :class="{ active: query.status === s.value }"
          @click="changeStatus(s.value)"
        >
          {{ s.label }}
        </view>
      </view>

      <view v-if="items.length === 0 && !loading" class="empty">
        <text>暂无投诉工单</text>
      </view>

      <view v-for="c in items" :key="c.id" class="card" @click="goDetail(c)">
        <view class="card-top">
          <text class="type">{{ c.type || '投诉' }}</text>
          <StatusTag :status="c.status" :textMap="COMPLAINT_STATUS_TEXT" />
        </view>
        <view class="card-body">
          <text class="content" number-of-lines="2">{{ c.content }}</text>
        </view>
        <view class="card-bottom">
          <text class="time">{{ c.createdAt }}</text>
          <text class="arrow">详情 ›</text>
        </view>
      </view>

      <view v-if="loading" class="load-tip">加载中...</view>
      <view v-else-if="finished && items.length > 0" class="load-tip">没有更多了</view>
    </scroll-view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import StatusTag from '@/components/StatusTag.vue'
import { getMyComplaints } from '@/api/complaint'
import { COMPLAINT_STATUS_TEXT } from '@/utils/constants'

const items = ref([])
const loading = ref(false)
const finished = ref(false)
const query = ref({ pageNum: 1, pageSize: 10, status: '' })

const statusOptions = [
  { label: '全部', value: '' },
  { label: '待处理', value: 'PENDING' },
  { label: '处理中', value: 'PROCESSING' },
  { label: '已解决', value: 'RESOLVED' },
  { label: '申诉中', value: 'APPEALING' }
]

onLoad(() => {
  resetAndLoad()
})

function resetAndLoad() {
  items.value = []
  finished.value = false
  query.value.pageNum = 1
  fetchList()
}

async function fetchList() {
  if (loading.value || finished.value) return
  loading.value = true
  try {
    const res = await getMyComplaints(query.value)
    const records = res.data?.records || res.data || []
    if (records.length < query.value.pageSize) {
      finished.value = true
    }
    items.value = items.value.concat(records)
    query.value.pageNum += 1
  } catch (e) {
    // ignore
  } finally {
    loading.value = false
  }
}

function loadMore() {
  fetchList()
}

function changeStatus(val) {
  if (query.value.status === val) return
  query.value.status = val
  resetAndLoad()
}

function goDetail(c) {
  uni.navigateTo({ url: `/pages/complaint/detail?id=${c.id}` })
}
</script>

<style lang="scss" scoped>
.complaint-list-page {
  background: #f1f5f9;
  min-height: 100vh;
}
.scroll {
  height: 100vh;
  padding: 20rpx 24rpx 40rpx;
  box-sizing: border-box;
}
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-bottom: 20rpx;
}
.status-chip {
  padding: 8rpx 20rpx;
  border-radius: 999rpx;
  font-size: 24rpx;
  color: #94a3b8;
  border: 1rpx solid rgba(99, 102, 241, 0.1);
}
.status-chip.active {
  background: linear-gradient(135deg, #ff4544, #e63939);
  color: #ffffff;
  border-color: transparent;
}
.card {
  padding: 20rpx 24rpx;
  margin-bottom: 20rpx;
  background: #ffffff;
  border-radius: 16rpx;
  border: 1rpx solid #e2e8f0;
}
.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8rpx;
}
.type {
  font-size: 26rpx;
  color: #1e293b;
}
.card-body .content {
  font-size: 26rpx;
  color: #334155;
}
.card-bottom {
  margin-top: 12rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.time {
  font-size: 22rpx;
  color: #94a3b8;
}
.arrow {
  font-size: 22rpx;
  color: #64748b;
}
.empty {
  margin-top: 80rpx;
  text-align: center;
  color: #94a3b8;
  font-size: 26rpx;
}
.load-tip {
  text-align: center;
  padding: 20rpx 0;
  font-size: 24rpx;
  color: #94a3b8;
}
</style>

