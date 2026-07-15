<template>
  <view class="message-page">
    <view class="header-row">
      <text class="page-title">系统通知</text>
      <view class="btn-read-all" @click="markAllReadClick">一键已读</view>
    </view>
    <scroll-view scroll-y class="list" @scrolltolower="loadMore">
      <view v-for="m in messages" :key="m.id" class="msg-item" @click="onTap(m)">
        <view class="msg-header">
          <text class="msg-title text-ellipsis">{{ m.title }}</text>
          <text v-if="!m.isRead" class="dot"></text>
        </view>
        <text class="msg-content text-ellipsis-2">{{ m.content }}</text>
        <text class="msg-time">{{ m.createdAt }}</text>
      </view>
      <view v-if="loading" class="loading-tip">加载中…</view>
<EmptyState v-if="!loading && messages.length===0" text="暂无通知" image="/static/icons/暂无通知.svg" />
    </scroll-view>
  </view>
</template>
<script setup>
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import EmptyState from '@/components/EmptyState.vue'
import { getMessageList, markRead, markAllRead } from '@/api/message'
import { useChatStore } from '@/store/chat'
import { useSiteStore } from '@/store/site'

const messages = ref([])
const pageNum = ref(1)
const loading = ref(false)
const finished = ref(false)

onLoad(() => { loadData() })
onShow(() => { useChatStore().fetchMessageUnreadCount() })
async function loadData() {
  loading.value = true
  const params = { pageNum: pageNum.value, pageSize: 20 }
  const res = await getMessageList(params)
  const list = res.data?.records || []
  if (list.length < 20) finished.value = true
  messages.value = pageNum.value === 1 ? list : [...messages.value, ...list]
  loading.value = false
}
function loadMore() { if (!loading.value && !finished.value) { pageNum.value++; loadData() } }
async function onTap(m) {
  if (!m.isRead) { await markRead(m.id); m.isRead = 1 }
  if (siteStore.configLoaded && siteStore.isUnderReview) return
  // 导航到对应页面
  if (m.type === 'ORDER' && m.relatedId) {
    uni.navigateTo({ url: `/pages/order/detail?id=${m.relatedId}` })
  }
}

const chatStore = useChatStore()
const siteStore = useSiteStore()
async function markAllReadClick() {
  try {
    const role = uni.getStorageSync('app_role') || 'user'
    const userType = role === 'cs' ? 'CS' : role === 'player' ? 'PLAYER' : 'USER'
    await markAllRead(userType)
    uni.showToast({ title: '已全部标为已读', icon: 'none' })
    messages.value.forEach(m => { m.isRead = 1 })
    chatStore.fetchMessageUnreadCount()
  } catch (e) {
    uni.showToast({ title: '操作失败', icon: 'none' })
  }
}
</script>
<style lang="scss" scoped>
.message-page { background: #f1f5f9; min-height: 100vh; }
.header-row { display: flex; align-items: center; justify-content: space-between; padding: 24rpx 24rpx 20rpx; background: #f1f5f9; border-bottom: 1rpx solid #e2e8f0; }
.page-title { font-size: 32rpx; font-weight: bold; color: #1e293b; }
.btn-read-all { font-size: 28rpx; color: #ff4544; }
.list { height: calc(100vh - 120rpx); }
.msg-item { padding: 24rpx; background: #ffffff; border-bottom: 1rpx solid #f1f5f9; }
.msg-header { display: flex; align-items: center; gap: 8rpx;
  .msg-title { font-size: 28rpx; font-weight: 500; color: #1e293b; flex: 1; }
  .dot { width: 14rpx; height: 14rpx; background: #ee0a24; border-radius: 50%; flex-shrink: 0; }
}
.msg-content { font-size: 26rpx; color: #64748b; margin-top: 8rpx; display: block; }
.msg-time { font-size: 22rpx; color: #94a3b8; margin-top: 8rpx; display: block; }
.loading-tip { text-align: center; padding: 32rpx; font-size: 24rpx; color: #94a3b8; }
</style>
