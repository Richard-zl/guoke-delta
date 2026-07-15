<template>
  <AuditPlaceholder v-if="pageBlocked" />
  <view v-else class="cs-chat-list tab-page">
    <scroll-view scroll-y class="cs-chat-scroll tab-page-scroll" :show-scrollbar="false">
      <view v-for="s in sessions" :key="s.id" class="session-item" @click="goRoom(s)">
        <image v-if="s.avatar" class="avatar" :src="s.avatar" mode="aspectFill" lazy-load />
        <view class="avatar avatar-placeholder">{{ firstChar(s.targetName || '') }}</view>
        <view class="session-info">
          <view class="top-row">
            <text class="name text-ellipsis">{{ s.targetName || '用户 #' + s.id }}</text>
            <text class="time">{{ formatRelativeTime(s.lastMessageAt) }}</text>
          </view>
          <view class="bottom-row">
            <text class="last-msg text-ellipsis">{{ s.lastMessage || '暂无消息' }}</text>
            <view v-if="s.unreadCount > 0" class="badge">{{ s.unreadCount > 99 ? '99+' : s.unreadCount }}</view>
          </view>
        </view>
      </view>
      <EmptyState v-if="sessions.length === 0" text="暂无会话" image="/static/icons/暂无消息对话.svg" />
      <view class="tab-page-bottom-spacer" />
    </scroll-view>
    <CustomTabBar :current="3" />
  </view>

</template>
<script setup>
import AuditPlaceholder from '@/components/AuditPlaceholder.vue'
import { useAuditPageGuard } from '@/composables/useAuditPageGuard'
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import CustomTabBar from '@/components/CustomTabBar.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useChatStore } from '@/store/chat'
import { useRemindStore } from '@/store/remind'
import { getCsChatSessions } from '@/pages-cs/api/cs'
const { pageBlocked } = useAuditPageGuard()

const chatStore = useChatStore()
const remindStore = useRemindStore()
import { formatRelativeTime } from '@/utils/format'

const sessions = ref([])

onShow(async () => {
  chatStore.fetchMessageUnreadCount()
  remindStore.fetchCsRemind()
  try {
    const res = await getCsChatSessions({ pageNum: 1, pageSize: 100 })
    sessions.value = res.data?.records || res.data || []
  } catch (e) { sessions.value = [] }
})

function firstChar(str) { const s = (str && String(str).trim()) || ''; return s.length ? s[0] : '?' }
function goRoom(s) {
  uni.navigateTo({
    url: '/pages-cs/chat/room?sessionId=' + s.id
      + '&name=' + encodeURIComponent(s.targetName || '')
      + '&avatar=' + encodeURIComponent(s.avatar || '')
  })
}
</script>
<style lang="scss" scoped>
.cs-chat-list { background: #ffffff; position: relative; }
.cs-chat-scroll { padding: 20rpx 24rpx 0; }
.session-item {
  display: flex; align-items: center; padding: 24rpx;
  background: rgba(0, 0, 0, 0.04);
  border-bottom: 1rpx solid rgba(0, 0, 0, 0.04);
  margin-bottom: 16rpx;
  position: relative; z-index: 1;
}
.avatar { width: 88rpx; height: 88rpx; border-radius: 50%; margin-right: 20rpx; flex-shrink: 0; border: 2rpx solid rgba(99, 102, 241, 0.1); }
.avatar-placeholder { display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, #94a3b8, #64748b); color: #fff; font-size: 36rpx; font-weight: bold; }
.session-info { flex: 1; overflow: hidden; }
.top-row {
  display: flex; justify-content: space-between; align-items: center;
  .name { font-size: 28rpx; color: rgba(0, 0, 0, 0.85); font-weight: 500; flex: 1; }
  .time { font-size: 22rpx; color: rgba(0, 0, 0, 0.3); flex-shrink: 0; margin-left: 16rpx; }
}
.bottom-row {
  display: flex; justify-content: space-between; align-items: center; margin-top: 8rpx;
  .last-msg { font-size: 24rpx; color: rgba(0, 0, 0, 0.35); flex: 1; }
}
.badge {
  min-width: 32rpx; height: 32rpx; line-height: 32rpx; padding: 0 8rpx;
  font-size: 20rpx; color: #ffffff; background: #ff4544; border-radius: 32rpx;
  text-align: center; flex-shrink: 0; margin-left: 16rpx;
}
</style>
