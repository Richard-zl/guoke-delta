<template>
  <AuditPlaceholder v-if="pageBlocked" />
  <view v-else class="chat-room" :class="{ 'toolbar-open': showToolbar }">
    <scroll-view scroll-y class="msg-list" :scroll-into-view="scrollAnchor" @scroll="onScroll">
      <view v-for="(m, idx) in messages" :key="m.id">
        <view v-if="showTimeSep(idx)" class="time-separator">
          <text>{{ formatMsgTime(m.createdAt || m.timestamp) }}</text>
        </view>
        <ChatBubble :msg="m" :isSelf="isSelf(m)" :avatar="isSelf(m) ? '' : userAvatar" :selfAvatar="''" :name="otherName" :selfName="csInfo.nickname || '客服'" />
      </view>
      <view id="msg-end-a" style="height:2rpx;" />
      <view id="msg-end-b" style="height:2rpx;" />
    </scroll-view>
    <view v-if="showBottomBtn" class="scroll-bottom-btn" @click="scrollToBottom">↓</view>

    <!-- 扩展工具栏 - 固定显示 -->
    <view v-if="showToolbar" class="toolbar">
      <view class="toolbar-item" @click="sendImage">
        <image class="toolbar-icon-img" src="/static/icons/图片.svg" mode="aspectFit" />
        <text class="toolbar-label">图片</text>
      </view>
      <view class="toolbar-item" @click="openProductPicker">
        <image class="toolbar-icon-img" src="/static/icons/商品.svg" mode="aspectFit" />
        <text class="toolbar-label">商品</text>
      </view>
      <view class="toolbar-item" @click="openQuickReply">
        <image class="toolbar-icon-img" src="/static/icons/快捷.svg" mode="aspectFit" />
        <text class="toolbar-label">快捷</text>
      </view>
      <view v-if="canSendSmsReminder" class="toolbar-item" @click="sendSmsReminder">
        <image class="toolbar-icon-img" src="/static/icons/短信.svg" mode="aspectFit" />
        <text class="toolbar-label">短信提醒</text>
      </view>
    </view>

    <!-- 快捷发言弹窗 -->
    <view v-if="showQuickReplyPopup" class="picker-mask" @click="showQuickReplyPopup = false">
      <view class="picker-popup" @click.stop>
        <view class="picker-header">
          <text class="picker-title">快捷发言</text>
          <text class="picker-close" @click="showQuickReplyPopup = false">✕</text>
        </view>
        <scroll-view scroll-y class="picker-list">
          <view v-for="qr in quickReplies" :key="qr.id" class="qr-item" @click="useQuickReply(qr)">
            <text class="qr-text">{{ qr.content }}</text>
            <text v-if="qr.category" class="qr-category">{{ qr.category }}</text>
          </view>
          <view v-if="quickReplies.length === 0" class="picker-empty">暂无快捷发言，请在"我的"页面添加</view>
        </scroll-view>
      </view>
    </view>

    <view class="input-bar">
      <view class="plus-btn" @click="showToolbar = !showToolbar">＋</view>
      <input v-model="inputText" placeholder="输入回复内容" @confirm="send" @focus="showToolbar = false" />
      <view class="send-btn" @click="send">发送</view>
    </view>

    <!-- 商品选择弹窗 -->
    <view v-if="showProductPopup" class="picker-mask" @click="showProductPopup = false">
      <view class="picker-popup" @click.stop>
        <view class="picker-header">
          <text class="picker-title">选择商品发送</text>
          <text class="picker-close" @click="showProductPopup = false">✕</text>
        </view>
        <scroll-view scroll-y class="picker-list">
          <view v-for="p in productList" :key="p.id" class="picker-item" @click="doSendProduct(p)">
            <image class="picker-cover" :src="p.coverImage || p.image" mode="aspectFill" lazy-load />
            <view class="picker-info">
              <text class="picker-name text-ellipsis">{{ p.name }}</text>
              <text class="picker-price">¥{{ Number(p.price || 0).toFixed(2) }}</text>
            </view>
          </view>
          <view v-if="productList.length === 0" class="picker-empty">暂无商品</view>
        </scroll-view>
      </view>
    </view>
  </view>

</template>
<script setup>
import AuditPlaceholder from '@/components/AuditPlaceholder.vue'
import { useAuditPageGuard } from '@/composables/useAuditPageGuard'
import { ref, watch, computed } from 'vue'
import { onLoad, onShow, onUnload } from '@dcloudio/uni-app'
import ChatBubble from '@/components/ChatBubble.vue'
import { getCsChatMessages, csChatMarkRead, getCsProductList, getActiveQuickReplies } from '@/pages-cs/api/cs'
import { getSessionDetail, sendChatSmsReminder } from '@/api/chat'
import { getCsInfo } from '@/utils/auth'
import { chooseAndUpload } from '@/api/file'
import { sendMessage as wsSend } from '@/utils/websocket'
import { useChatStore } from '@/store/chat'
import { formatDateTime } from '@/utils/format'
import { CS_CHAT_SMS_REMINDERS, chooseChatSmsReminder } from '@/utils/chatSmsReminder'
const { pageBlocked } = useAuditPageGuard()

const chatStore = useChatStore()
const csInfo = getCsInfo() || {}
const csId = csInfo.adminId
const userAvatar = ref('')
const otherName = ref('')

const sessionId = ref(0)
const messages = ref([])
const inputText = ref('')
const scrollAnchor = ref('')
const showBottomBtn = ref(false)
const showProductPopup = ref(false)
const productList = ref([])
const showQuickReplyPopup = ref(false)
const quickReplies = ref([])
const showToolbar = ref(false)
const targetType = ref('')
const canSendSmsReminder = computed(() => targetType.value === 'USER' || targetType.value === 'PLAYER')
let _isNearBottom = true

onShow(() => {
  unlockH5TabbarPageScroll()
})

function unlockH5TabbarPageScroll() {
  // #ifdef H5
  document.documentElement.classList.remove('tabbar-page-locked')
  document.body.classList.remove('tabbar-page-locked')
  // #endif
}

function isSelf(m) {
  return m.senderType === 'CS' || m.senderType === 'ADMIN'
}

function scrollToBottom() {
  setTimeout(() => {
    scrollAnchor.value = scrollAnchor.value === 'msg-end-a' ? 'msg-end-b' : 'msg-end-a'
    showBottomBtn.value = false
    _isNearBottom = true
  }, 150)
}

function onScroll(e) {
  const { scrollTop, scrollHeight } = e.detail
  _isNearBottom = scrollHeight - scrollTop < (e.detail.clientHeight || 600) + 300
  showBottomBtn.value = !_isNearBottom
}

function normalizeDate(str) {
  if (!str) return 0
  if (typeof str === 'string' && str.includes(' ')) {
    return new Date(str.replace(' ', 'T')).getTime()
  }
  return new Date(str).getTime()
}

function showTimeSep(idx) {
  if (idx === 0) return true
  const cur = normalizeDate(messages.value[idx].createdAt || 0)
  const prev = normalizeDate(messages.value[idx - 1].createdAt || 0)
  return cur - prev > 300000
}

function formatMsgTime(t) {
  if (!t) return ''
  const str = typeof t === 'string' && t.includes(' ') ? t.replace(' ', 'T') : t
  return formatDateTime(str)
}

onLoad(async (opts) => {
  if (!opts.sessionId) {
    uni.showToast({ title: '会话不存在', icon: 'none' })
    return
  }
  sessionId.value = opts.sessionId
  chatStore.setCurrentSession(opts.sessionId)
  if (opts.name) {
    otherName.value = decodeURIComponent(opts.name)
    uni.setNavigationBarTitle({ title: otherName.value })
  }
  if (opts.avatar) userAvatar.value = decodeURIComponent(opts.avatar)
  try {
    const res = await getSessionDetail(sessionId.value, { chatRole: 'CS' })
    const detail = res.data || {}
    targetType.value = resolveEncodedType(detail.targetId)
    if (!otherName.value && detail.targetName) {
      otherName.value = detail.targetName
      uni.setNavigationBarTitle({ title: otherName.value })
    }
    if (!userAvatar.value && detail.avatar) {
      userAvatar.value = detail.avatar
    }
  } catch (_) {}

  try {
    const res = await getCsChatMessages({ sessionId: sessionId.value, pageNum: 1, pageSize: 50 })
    messages.value = (res.data?.records || []).reverse()
    scrollToBottom()
  } catch (e) {}

  csChatMarkRead(sessionId.value).catch(() => {})
  // 确保 WebSocket 已连接（App.vue 启动时已连，这里是兜底）
  chatStore.connect({ chatRole: 'CS' })
})

onUnload(() => {
  chatStore.clearCurrentSession()
})

// 通过 store 的 newMessage 接收推送
watch(() => chatStore.newMessage, (data) => {
  if (data && String(data.sessionId) === String(sessionId.value)) {
    const exists = messages.value.some(m =>
      String(m.id) === String(data.id) ||
      (m.type === data.type && m.content === data.content &&
        Math.abs(new Date(m.createdAt || 0).getTime() - new Date(data.createdAt || 0).getTime()) < 5000)
    )
    if (!exists) {
      messages.value.push(data)
      scrollToBottom()
    }
  }
})

function sendViaWs(type, content) {
  const data = {
    action: 'send',
    sessionId: sessionId.value,
    senderType: 'CS',
    senderId: csId,
    chatRole: 'CS',
    type,
    content
  }
  const sent = wsSend(data)
  if (!sent) {
    uni.showToast({ title: 'WebSocket未连接', icon: 'none' })
    return false
  }
  messages.value.push({
    id: Date.now(),
    sessionId: sessionId.value,
    senderId: csId,
    senderType: 'CS',
    type,
    content,
    createdAt: new Date().toISOString()
  })
  scrollToBottom()
  return true
}

function send() {
  if (!inputText.value.trim()) return
  const content = inputText.value.trim()
  inputText.value = ''
  sendViaWs('TEXT', content)
}

async function sendSmsReminder() {
  if (!sessionId.value) return
  showToolbar.value = false
  try {
    const selected = await chooseChatSmsReminder(CS_CHAT_SMS_REMINDERS)
    await sendChatSmsReminder(
      { sessionId: sessionId.value, reminderCode: selected.code },
      { chatRole: 'CS' }
    )
    uni.showToast({ title: `已发送${selected.label}`, icon: 'none' })
  } catch (e) {
    if (e?.errMsg?.includes('cancel')) return
  }
}

async function sendImage() {
  showToolbar.value = false
  try {
    const urls = await chooseAndUpload(1)
    if (urls.length) sendViaWs('IMAGE', urls[0])
  } catch (e) { /* cancel */ }
}

async function openProductPicker() {
  showToolbar.value = false
  showProductPopup.value = true
  try {
    const res = await getCsProductList({ pageNum: 1, pageSize: 20, status: 1 })
    productList.value = res.data?.records || res.data || []
  } catch (e) { productList.value = [] }
}

function doSendProduct(p) {
  showProductPopup.value = false
  const content = JSON.stringify({ id: p.id, name: p.name, price: p.price, coverImage: p.coverImage || p.image })
  sendViaWs('PRODUCT', content)
}

async function openQuickReply() {
  showToolbar.value = false
  showQuickReplyPopup.value = true
  if (quickReplies.value.length === 0) {
    try {
      const res = await getActiveQuickReplies()
      quickReplies.value = res.data || []
    } catch (e) { quickReplies.value = [] }
  }
}

function useQuickReply(qr) {
  showQuickReplyPopup.value = false
  inputText.value = qr.content
}

function resolveEncodedType(encodedId) {
  const numeric = Number(encodedId || 0)
  const type = Math.floor(numeric / 1000000000)
  if (type === 2) return 'PLAYER'
  if (type === 3) return 'CS'
  return 'USER'
}
</script>
<style lang="scss" scoped>
.chat-room { height: 100vh; min-height: 100vh; background: #ffffff; position: relative; overflow: hidden; }
.msg-list { height: 100vh; padding: 20rpx 0 calc(128rpx + env(safe-area-inset-bottom)); background: #ffffff; box-sizing: border-box; }
.chat-room.toolbar-open .msg-list { padding-bottom: calc(250rpx + env(safe-area-inset-bottom)); }
.scroll-bottom-btn {
  position: absolute; right: 32rpx; bottom: 180rpx;
  width: 72rpx; height: 72rpx; line-height: 72rpx; text-align: center;
  font-size: 36rpx; color: #ff4544;
  background: rgba(255,255,255,0.9); border: 1rpx solid rgba(255,69,68,0.15);
  border-radius: 50%; z-index: 10; box-shadow: 0 4rpx 12rpx rgba(0,0,0,0.4);
}
.time-separator { text-align: center; padding: 16rpx 0; text { font-size: 22rpx; color: rgba(0,0,0,0.3); background: rgba(0,0,0,0.05); padding: 4rpx 16rpx; border-radius: 8rpx; } }
.toolbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: calc(104rpx + env(safe-area-inset-bottom));
  z-index: 19;
  display: flex; justify-content: space-around; gap: 12rpx; padding: 8rpx 32rpx;
  background: #f5f5f5;
  border-top: 1rpx solid rgba(0,0,0,0.06);
  box-sizing: border-box;
}
.toolbar-item { display: flex; flex-direction: column; align-items: center; gap: 4rpx; min-width: 80rpx; }
.toolbar-icon-img { width: 36rpx; height: 36rpx; }
.toolbar-label { font-size: 18rpx; color: rgba(0,0,0,0.55); }
.input-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 20;
  display: flex; align-items: center; padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom)); background: rgba(255,255,255,0.95);
  border-top: 1rpx solid rgba(0,0,0,0.06);
  box-sizing: border-box;
  input { flex: 1; height: 72rpx; background: rgba(0,0,0,0.05); border: 1rpx solid rgba(0,0,0,0.06); border-radius: 999rpx; padding: 0 24rpx; font-size: 28rpx; color: rgba(0,0,0,0.85); }
  .send-btn { margin-left: 16rpx; padding: 0 32rpx; height: 72rpx; line-height: 72rpx; background: linear-gradient(135deg, #ff4544, #e63939); color: #ffffff; font-weight: bold; font-size: 28rpx; border-radius: 999rpx; }
}
.plus-btn {
  width: 64rpx;
  height: 64rpx;
  line-height: 60rpx;
  text-align: center;
  font-size: 40rpx;
  color: #ff4544;
  border: 1rpx solid rgba(255,69,68,0.18);
  border-radius: 50%;
  margin-right: 16rpx;
  flex-shrink: 0;
}
.picker-mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.7); display: flex; align-items: flex-end; z-index: 999; }
.picker-popup { width: 100%; max-height: 45vh; background: #ffffff; border-top-left-radius: 24rpx; border-top-right-radius: 24rpx; display: flex; flex-direction: column; border: 1rpx solid rgba(0,0,0,0.06); }
.picker-header { display: flex; align-items: center; justify-content: space-between; padding: 28rpx 32rpx; border-bottom: 1rpx solid rgba(0,0,0,0.06); }
.picker-title { font-size: 30rpx; font-weight: bold; color: #ff4544; }
.picker-close { font-size: 36rpx; color: rgba(0,0,0,0.45); padding: 0 8rpx; }
.picker-list { flex: 1; max-height: 38vh; }
.picker-item { display: flex; align-items: center; padding: 20rpx 32rpx; border-bottom: 1rpx solid rgba(0,0,0,0.04); }
.picker-cover { width: 100rpx; height: 100rpx; border-radius: 8rpx; flex-shrink: 0; margin-right: 20rpx; }
.picker-info { flex: 1; overflow: hidden; }
.picker-name { font-size: 28rpx; color: rgba(0,0,0,0.85); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; display: block; }
.picker-price { font-size: 28rpx; color: #ff4544; font-weight: bold; }
.picker-empty { text-align: center; padding: 80rpx 0; color: rgba(0,0,0,0.3); font-size: 28rpx; }
.qr-item { display: flex; align-items: center; justify-content: space-between; padding: 24rpx 32rpx; border-bottom: 1rpx solid rgba(0,0,0,0.04); }
.qr-text { font-size: 28rpx; color: rgba(0,0,0,0.85); flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.qr-category { font-size: 22rpx; color: #ff4544; background: rgba(255,69,68,0.1); padding: 2rpx 12rpx; border-radius: 4rpx; margin-left: 12rpx; flex-shrink: 0; }
</style>
