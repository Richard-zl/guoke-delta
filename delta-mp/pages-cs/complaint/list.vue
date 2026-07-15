<template>
  <AuditPlaceholder v-if="pageBlocked" />
  <view v-else class="complaint-list tab-page">
    <scroll-view scroll-x class="filter-bar">
      <view class="filter-item" :class="{active: !statusFilter}" @click="filterStatus('')">全部</view>
      <view class="filter-item" :class="{active: statusFilter==='PENDING'}" @click="filterStatus('PENDING')">待处理</view>
      <view class="filter-item" :class="{active: statusFilter==='PROCESSING'}" @click="filterStatus('PROCESSING')">处理中</view>
      <view class="filter-item" :class="{active: statusFilter==='RESOLVED'}" @click="filterStatus('RESOLVED')">已解决</view>
    </scroll-view>
    <scroll-view scroll-y class="list" :show-scrollbar="false" @scrolltolower="loadMore">
      <view v-for="c in complaints" :key="c.id" class="complaint-card" @click="goHandle(c.id)">
        <view class="card-top">
          <text class="type">{{ COMPLAINT_TYPE_TEXT[c.type] || c.type }}</text>
          <StatusTag :status="c.status" :text-map="COMPLAINT_STATUS_TEXT" :color-map="COMPLAINT_STATUS_COLOR" />
        </view>
        <text class="content">{{ c.content }}</text>
        <view class="card-bottom">
          <text class="user">投诉人：{{ c.userNickname || c.userId }}</text>
          <text class="time">{{ c.createdAt }}</text>
        </view>
      </view>
      <view v-if="loading" class="loading-tip">加载中...</view>
<EmptyState v-if="!loading && complaints.length===0" text="暂无投诉" image="/static/icons/暂无项目.svg" />
    </scroll-view>
    <CustomTabBar :current="2" />
  </view>

</template>
<script setup>
import AuditPlaceholder from '@/components/AuditPlaceholder.vue'
import { useAuditPageGuard } from '@/composables/useAuditPageGuard'
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import StatusTag from '@/components/StatusTag.vue'
import EmptyState from '@/components/EmptyState.vue'
import CustomTabBar from '@/components/CustomTabBar.vue'
import { useRemindStore } from '@/store/remind'
import { getCsComplaintList } from '@/pages-cs/api/cs'
const { pageBlocked } = useAuditPageGuard()

const remindStore = useRemindStore()
const COMPLAINT_TYPE_TEXT = { SERVICE_QUALITY: '服务质量', ACCOUNT_ISSUE: '账号问题', DELAY: '进度延迟', FRAUD: '欺诈', OTHER: '其他' }
const COMPLAINT_STATUS_TEXT = { PENDING: '待处理', PROCESSING: '处理中', RESOLVED: '已解决', REJECTED: '已驳回' }
const COMPLAINT_STATUS_COLOR = { PENDING: '#ff4544', PROCESSING: '#ff9900', RESOLVED: '#07c160', REJECTED: '#999' }
const statusFilter = ref('')
const complaints = ref([])
const pageNum = ref(1)
const loading = ref(false)
const finished = ref(false)

onShow(() => { remindStore.fetchCsRemind(); refresh() })

function filterStatus(s) { statusFilter.value = s; refresh() }
function refresh() { pageNum.value = 1; complaints.value = []; finished.value = false; loadData() }
async function loadData() {
  loading.value = true
  const params = { pageNum: pageNum.value, pageSize: 20 }
  if (statusFilter.value) params.status = statusFilter.value
  const res = await getCsComplaintList(params)
  const list = res.data?.records || []
  if (list.length < 20) finished.value = true
  complaints.value = pageNum.value === 1 ? list : [...complaints.value, ...list]
  loading.value = false
}
function loadMore() { if (!loading.value && !finished.value) { pageNum.value++; loadData() } }
function goHandle(id) { uni.navigateTo({ url: '/pages-cs/complaint/handle?id=' + id }) }
</script>
<style lang="scss" scoped>
.complaint-list { background: #ffffff; overflow: hidden; position: relative; }
.filter-bar { white-space: nowrap; padding: 12rpx 24rpx; border-bottom: 1rpx solid rgba(0,0,0,0.04); }
.filter-item { display: inline-block; padding: 10rpx 20rpx; margin-right: 12rpx; font-size: 24rpx; color: rgba(0,0,0,0.55); background: rgba(0,0,0,0.05); border-radius: 999rpx; &.active { background: linear-gradient(135deg, #ff4544, #e63939); color: #ffffff; font-weight:bold; } }
.list { height: calc(100vh - 200rpx - env(safe-area-inset-bottom)); padding: 20rpx 24rpx; box-sizing: border-box; }
.complaint-card {
  background: rgba(0,0,0,0.04); border-radius: 12rpx; padding: 24rpx; margin-bottom: 16rpx;
  box-sizing: border-box; overflow: hidden;
  .card-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12rpx; .type { font-size: 28rpx; font-weight: bold; flex-shrink: 0; } }
  .content { font-size: 26rpx; color: rgba(0,0,0,0.55); display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; margin-bottom: 12rpx; word-break: break-all; }
  .card-bottom { display: flex; justify-content: space-between; overflow: hidden; .user { font-size: 22rpx; color: rgba(0,0,0,0.3); flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; } .time { font-size: 22rpx; color: rgba(0,0,0,0.3); flex-shrink: 0; margin-left: 12rpx; } }
}
.loading-tip { text-align: center; padding: 32rpx; font-size: 24rpx; color: rgba(0,0,0,0.3); }
</style>
