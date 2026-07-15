<template>
  <view class="chat-list tab-page">
    <canvas type="2d" id="goldDust" class="gold-dust-canvas"></canvas>
    <scroll-view scroll-y class="session-scroll tab-page-scroll" :show-scrollbar="false">
      <view v-for="s in sessions" :key="s.id" class="session-item" @click="goRoom(s)">
        <image v-if="s.avatar" class="avatar" :src="s.avatar" mode="aspectFill" lazy-load />
        <view v-else class="avatar avatar-placeholder">{{ firstChar(s.targetName || s.nickname || '') }}</view>
        <view class="session-info">
          <view class="top-row">
            <text class="name text-ellipsis">{{ s.targetName || s.nickname || '会话 #' + s.id }}</text>
            <text class="time">{{ formatRelativeTime(s.lastMessageAt) }}</text>
          </view>
          <view class="bottom-row">
            <text class="last-msg text-ellipsis">{{ s.lastMessage || '暂无消息' }}</text>
            <view v-if="s.unreadCount > 0" class="badge">{{ s.unreadCount > 99 ? '99+' : s.unreadCount }}</view>
          </view>
        </view>
      </view>
      <EmptyState v-if="sessions.length===0" text="暂无会话" image="/static/icons/暂无消息对话.svg" />
    </scroll-view>
    <CustomTabBar :current="3" />
  </view>
</template>
<script setup>
import { ref, watch } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import EmptyState from '@/components/EmptyState.vue'
import CustomTabBar from '@/components/CustomTabBar.vue'
import { getSessionList } from '@/api/chat'
import { getRemind } from '@/api/message'
import { useChatStore } from '@/store/chat'
import { formatRelativeTime } from '@/utils/format'
import { useGoldDust } from '@/composables/useGoldDust'
useGoldDust()
const chatStore = useChatStore()
const sessions = ref([])

const USER_CHAT_OPTS = { chatRole: 'USER' }

async function fetchList() {
  try {
    const res = await getSessionList({ ...USER_CHAT_OPTS, loading: false })
    sessions.value = res.data || []
  } catch (e) {
    sessions.value = []
  }
}

onShow(async () => {
  chatStore.connect(USER_CHAT_OPTS)
  try {
    const res = await getRemind({ loading: false })
    const d = res.data || {}
    chatStore.setUnreadFromServer(d.messageUnread ?? 0)
    chatStore.setSystemUnreadFromServer(d.systemUnread ?? 0)
  } catch (_) {}
  await fetchList()
})

watch(() => chatStore.newMessage, () => {
  if (chatStore.newMessage) fetchList()
})
function firstChar(str) { const s = (str && String(str).trim()) || ''; return s.length ? s[0] : '?' }
function goRoom(s) { uni.navigateTo({ url: '/pages/chat/room?sessionId=' + s.id + '&name=' + encodeURIComponent(s.targetName || s.nickname || '') }) }
</script>
<style lang="scss" scoped>
.chat-list {
  background: #f1f5f9;
  height: 100vh;
  position: relative;
  overflow: hidden;
}
.session-scroll {
  height: calc(100vh - 110rpx - env(safe-area-inset-bottom));
  position: relative;
  z-index: 1;
  padding: 20rpx 24rpx;
  padding-bottom: 30rpx;
  box-sizing: border-box;
}
.session-item {
  display: flex;
  align-items: center;
  padding: 24rpx 28rpx;
  background: #ffffff;
  border: 1rpx solid #e2e8f0;
  border-radius: 16rpx;
  margin-bottom: 20rpx;
  position: relative;
  z-index: 1;
  box-sizing: border-box;
}
.session-item:last-of-type { margin-bottom: 0; }
.avatar {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  margin-right: 20rpx;
  flex-shrink: 0;
  border: 2rpx solid rgba(99, 102, 241, 0.1);
}
.avatar-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #94a3b8, #64748b);
  color: #fff;
  font-size: 36rpx;
  font-weight: bold;
}
.session-info { flex: 1; overflow: hidden; }
.top-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  .name { font-size: 28rpx; color: #1e293b; font-weight: 500; flex: 1; }
  .time { font-size: 22rpx; color: #94a3b8; flex-shrink: 0; margin-left: 16rpx; }
}
.bottom-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8rpx;
  .last-msg { font-size: 24rpx; color: #94a3b8; flex: 1; }
}
.badge {
  min-width: 32rpx;
  height: 32rpx;
  line-height: 32rpx;
  padding: 0 8rpx;
  font-size: 20rpx;
  color: #fff;
  background: #ee0a24;
  border-radius: 32rpx;
  text-align: center;
  flex-shrink: 0;
  margin-left: 16rpx;
}
</style>
