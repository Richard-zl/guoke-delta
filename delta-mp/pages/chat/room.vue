<template>
  <view class="chat-room" :class="{ 'toolbar-open': showToolbar }">
    <scroll-view scroll-y class="msg-list" :scroll-into-view="scrollAnchor" @scroll="onScroll">
      <view v-for="(m, idx) in messages" :key="m.id">
        <view v-if="showTimeSep(idx)" class="time-separator">
          <text>{{ formatMsgTime(m.createdAt || m.timestamp) }}</text>
        </view>
        <ChatBubble :msg="m" :isSelf="isSelf(m)" :avatar="otherAvatar" :selfAvatar="selfAvatar" :name="otherName" :selfName="selfName" />
      </view>
      <view id="msg-end-a" style="height:2rpx;" />
      <view id="msg-end-b" style="height:2rpx;" />
    </scroll-view>
    <!-- 回到底部按钮 -->
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
      <view class="toolbar-item" @click="openOrderPicker">
        <image class="toolbar-icon-img" src="/static/icons/订单.svg" mode="aspectFit" />
        <text class="toolbar-label">订单</text>
      </view>
      <view v-if="canSendSmsReminder" class="toolbar-item" @click="sendSmsReminder">
        <image class="toolbar-icon-img" src="/static/icons/短信.svg" mode="aspectFit" />
        <text class="toolbar-label">短信提醒</text>
      </view>
    </view>

    <view class="input-bar">
      <view class="plus-btn" @click="showToolbar = !showToolbar">＋</view>
      <input v-model="inputText" placeholder="输入消息" @confirm="send" @focus="showToolbar = false" />
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

    <!-- 订单选择弹窗 -->
    <view v-if="showOrderPopup" class="picker-mask" @click="showOrderPopup = false">
      <view class="picker-popup" @click.stop>
        <view class="picker-header">
          <text class="picker-title">选择订单发送</text>
          <text class="picker-close" @click="showOrderPopup = false">✕</text>
        </view>
        <scroll-view scroll-y class="picker-list">
          <view v-for="o in orderList" :key="o.id" class="picker-item" @click="doSendOrder(o)">
            <view class="picker-info" style="flex:1">
              <text class="picker-name text-ellipsis">{{ o.productName }}</text>
              <view class="picker-meta">
                <text class="picker-price">¥{{ Number(o.amount || 0).toFixed(2) }}</text>
                <text class="picker-status">{{ ORDER_STATUS_TEXT[o.status] || o.status }}</text>
              </view>
              <text class="picker-sub">{{ o.orderNo }}</text>
            </view>
          </view>
          <view v-if="orderList.length === 0" class="picker-empty">暂无订单</view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>
<script setup>
import { ref, computed, watch } from 'vue'
import { onLoad, onShow, onUnload } from '@dcloudio/uni-app'
import ChatBubble from '@/components/ChatBubble.vue'
import { getChatMessageList, getSessionByOrderId, getSessionDetail, markMessageRead, sendChatSmsReminder } from '@/api/chat'
import { getProductList } from '@/api/product'
import { getOrderList } from '@/api/order'
import { useChatStore } from '@/store/chat'
import { useUserStore } from '@/store/user'
import { usePlayerStore } from '@/store/player'
import { useAppStore } from '@/store/app'
import { chooseAndUpload } from '@/api/file'
import { sendMessage as wsSend } from '@/utils/websocket'
import { formatDateTime } from '@/utils/format'
import { ORDER_STATUS_TEXT } from '@/utils/constants'
import { USER_CHAT_SMS_REMINDERS, chooseChatSmsReminder } from '@/utils/chatSmsReminder'

const chatStore = useChatStore()
const userStore = useUserStore()
const playerStore = usePlayerStore()
const appStore = useAppStore()
const sessionId = ref(0)
const messages = ref([])
const inputText = ref('')
const scrollAnchor = ref('')
const showBottomBtn = ref(false)
const sessionReady = ref(false)
const showToolbar = ref(false)
let _isNearBottom = true
const mySenderType = computed(() => (appStore.role === 'player' ? 'PLAYER' : 'USER'))
const USER_CHAT_OPTS = { chatRole: 'USER' }
const mySenderId = computed(() =>
  appStore.role === 'player' ? playerStore.playerInfo?.id : userStore.userInfo?.id
)
const selfAvatar = computed(() =>
  appStore.role === 'player' ? (playerStore.playerInfo?.avatar || '') : (userStore.avatar || userStore.userInfo?.avatar || '')
)
const selfName = computed(() =>
  appStore.role === 'player' ? (playerStore.playerInfo?.nickname || '接单员') : (userStore.userInfo?.nickname || '我')
)
const otherAvatar = ref('')
const otherName = ref('')
const targetType = ref('')
const canSendSmsReminder = computed(() =>
  appStore.role === 'player' ? targetType.value === 'USER' : targetType.value === 'PLAYER'
)

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
  if (!m) return false
  const typeMatch = m.senderType === mySenderType.value || (mySenderType.value === 'CS' && (m.senderType === 'CS' || m.senderType === 'ADMIN'))
  return typeMatch && String(m.senderId) === String(mySenderId.value)
}

const showProductPopup = ref(false)
const showOrderPopup = ref(false)
const productList = ref([])
const orderList = ref([])

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
  const cur = normalizeDate(messages.value[idx].createdAt || messages.value[idx].timestamp || 0)
  const prev = normalizeDate(messages.value[idx - 1].createdAt || messages.value[idx - 1].timestamp || 0)
  return cur - prev > 300000
}

function formatMsgTime(t) {
  if (!t) return ''
  const str = typeof t === 'string' && t.includes(' ') ? t.replace(' ', 'T') : t
  return formatDateTime(str)
}

onLoad(async (opts) => {
  let sid = opts.sessionId
  let sessionData = null
  if (!sid && opts.orderId) {
    try {
      const sRes = await getSessionByOrderId(opts.orderId, USER_CHAT_OPTS)
      sessionData = sRes.data
      sid = sessionData?.id
    } catch (e) {
      uni.showToast({ title: e?.data?.msg || '无法发起聊天', icon: 'none' })
      return
    }
  }
  if (!sid) {
    uni.showToast({ title: '聊天会话不存在', icon: 'none' })
    return
  }
  if (!sessionData && sid) {
    try {
      const dRes = await getSessionDetail(sid, USER_CHAT_OPTS)
      sessionData = dRes.data
    } catch (_) {}
  }
  if (sessionData) {
    otherAvatar.value = sessionData.avatar || ''
    otherName.value = sessionData.targetName || ''
    targetType.value = resolveEncodedType(sessionData.targetId)
    if (sessionData.targetName) uni.setNavigationBarTitle({ title: sessionData.targetName })
  } else if (opts.name) {
    otherName.value = decodeURIComponent(opts.name)
    uni.setNavigationBarTitle({ title: otherName.value })
  }

  sessionId.value = sid
  sessionReady.value = true
  if (appStore.role === 'player' && !playerStore.playerInfo) await playerStore.fetchProfile()
  chatStore.setCurrentSession(sid)
  await loadMessages()
  markMessageRead(sid, USER_CHAT_OPTS).catch(() => {})
  // 确保 WebSocket 已连接（App.vue 启动时已连，这里是兜底）
  chatStore.connect(USER_CHAT_OPTS)
})

onUnload(() => {
  // 只清除当前会话标记，不断开 WebSocket（全局保持连接）
  chatStore.clearCurrentSession()
})

// 通过 store 的 newMessage 接收 WebSocket 推送的消息
watch(() => chatStore.newMessage, (msg) => {
  if (!msg || String(msg.sessionId) !== String(sessionId.value)) return
  const exists = messages.value.some(m =>
    String(m.id) === String(msg.id) ||
    (m.type === msg.type && m.content === msg.content &&
      Math.abs(new Date(m.createdAt || 0).getTime() - new Date(msg.createdAt || 0).getTime()) < 5000)
  )
  if (!exists) {
    messages.value.push(msg)
    scrollToBottom()
  }
})

async function loadMessages() {
  const res = await getChatMessageList({ sessionId: sessionId.value, pageNum: 1, pageSize: 50, ...USER_CHAT_OPTS })
  messages.value = (res.data?.records || []).reverse()
  scrollToBottom()
}

function sendViaWs(type, content) {
  const data = {
    action: 'send',
    sessionId: sessionId.value,
    type,
    content,
    chatRole: mySenderType.value
  }
  const sent = wsSend(data)
  if (!sent) {
    uni.showToast({ title: 'WebSocket未连接', icon: 'none' })
    return false
  }
  messages.value.push({
    id: Date.now(),
    sessionId: sessionId.value,
    senderId: mySenderId.value,
    senderType: mySenderType.value,
    type,
    content,
    createdAt: new Date().toISOString()
  })
  scrollToBottom()
  return true
}

function send() {
  if (!inputText.value.trim() || !sessionReady.value) return
  const content = inputText.value
  inputText.value = ''
  sendViaWs('TEXT', content)
}

async function sendSmsReminder() {
  if (!sessionReady.value) return
  showToolbar.value = false
  try {
    const selected = await chooseChatSmsReminder(USER_CHAT_SMS_REMINDERS)
    await sendChatSmsReminder(
      { sessionId: sessionId.value, reminderCode: selected.code },
      USER_CHAT_OPTS
    )
    uni.showToast({ title: `已发送${selected.label}`, icon: 'none' })
  } catch (e) {
    if (e?.errMsg?.includes('cancel')) return
  }
}

async function sendImage() {
  if (!sessionReady.value) return
  showToolbar.value = false
  try {
    const urls = await chooseAndUpload(1)
    if (urls.length) {
      sendViaWs('IMAGE', urls[0])
    }
  } catch (e) { /* cancel */ }
}

async function openProductPicker() {
  showToolbar.value = false
  showProductPopup.value = true
  try {
    const res = await getProductList({ pageNum: 1, pageSize: 20, status: 1 })
    productList.value = res.data?.records || res.data || []
  } catch (e) { productList.value = [] }
}

async function openOrderPicker() {
  showToolbar.value = false
  showOrderPopup.value = true
  try {
    const res = await getOrderList({ pageNum: 1, pageSize: 20 })
    orderList.value = res.data?.records || res.data || []
  } catch (e) { orderList.value = [] }
}

function doSendProduct(p) {
  showProductPopup.value = false
  const content = JSON.stringify({ id: p.id, name: p.name, price: p.price, coverImage: p.coverImage || p.image })
  sendViaWs('PRODUCT', content)
}

function doSendOrder(o) {
  showOrderPopup.value = false
  const content = JSON.stringify({ id: o.id, orderNo: o.orderNo, productName: o.productName, amount: o.amount, status: o.status })
  sendViaWs('ORDER', content)
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
.chat-room { height: 100vh; min-height: 100vh; background: #f1f5f9; position: relative; overflow: hidden; }
.msg-list { height: 100vh; padding: 20rpx 0 calc(128rpx + env(safe-area-inset-bottom)); background: #f1f5f9; box-sizing: border-box; }
.chat-room.toolbar-open .msg-list { padding-bottom: calc(250rpx + env(safe-area-inset-bottom)); }
.scroll-bottom-btn {
  position: absolute;
  right: 32rpx;
  bottom: 180rpx;
  width: 72rpx;
  height: 72rpx;
  line-height: 72rpx;
  text-align: center;
  font-size: 36rpx;
  color: #ff4544;
  background: rgba(255,255,255,0.9);
  border: 1rpx solid rgba(99, 102, 241, 0.15);
  border-radius: 50%;
  z-index: 10;
  box-shadow: 0 4rpx 12rpx rgba(0,0,0,0.4);
}
.time-separator { text-align: center; padding: 16rpx 0; text { font-size: 22rpx; color: #94a3b8; background: #f1f5f9; padding: 4rpx 16rpx; border-radius: 8rpx; } }

/* 工具栏 */
.toolbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: calc(104rpx + env(safe-area-inset-bottom));
  z-index: 19;
  display: flex;
  justify-content: space-around;
  gap: 12rpx;
  padding: 8rpx 32rpx;
  background: #f1f5f9;
  border-top: 1rpx solid #e2e8f0;
  box-sizing: border-box;
}
.toolbar-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
  min-width: 80rpx;
}
.toolbar-icon-img { width: 36rpx; height: 36rpx; }
.toolbar-label { font-size: 18rpx; color: #64748b; }

.input-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  background: rgba(255,255,255,0.95);
  border-top: 1rpx solid #e2e8f0;
  box-sizing: border-box;

  input { flex: 1; height: 72rpx; background: #f1f5f9; border: 1rpx solid #e2e8f0; border-radius: 999rpx; padding: 0 24rpx; font-size: 28rpx; color: #1e293b; }
  .send-btn { margin-left: 16rpx; padding: 0 32rpx; height: 72rpx; line-height: 72rpx; background: linear-gradient(135deg, #ff4544, #e63939); color: #ffffff; font-weight: bold; font-size: 28rpx; border-radius: 999rpx; }
}
.plus-btn {
  width: 64rpx;
  height: 64rpx;
  line-height: 60rpx;
  text-align: center;
  font-size: 40rpx;
  color: #ff4544;
  border: 1rpx solid rgba(99, 102, 241, 0.15);
  border-radius: 50%;
  margin-right: 16rpx;
  flex-shrink: 0;
}

/* 弹窗选择器 */
.picker-mask {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.7);
  display: flex;
  align-items: flex-end;
  z-index: 999;
}
.picker-popup {
  width: 100%;
  max-height: 45vh;
  background: #ffffff;
  border-top-left-radius: 24rpx;
  border-top-right-radius: 24rpx;
  display: flex;
  flex-direction: column;
}
.picker-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 32rpx;
  border-bottom: 1rpx solid #e2e8f0;
}
.picker-title { font-size: 30rpx; font-weight: bold; color: #ff4544; }
.picker-close { font-size: 36rpx; color: #94a3b8; padding: 0 8rpx; }
.picker-list { flex: 1; max-height: 38vh; }
.picker-item {
  display: flex;
  align-items: center;
  padding: 20rpx 32rpx;
  border-bottom: 1rpx solid #f1f5f9;
}
.picker-cover { width: 100rpx; height: 100rpx; border-radius: 8rpx; flex-shrink: 0; margin-right: 20rpx; }
.picker-info { flex: 1; overflow: hidden; }
.picker-name { font-size: 28rpx; color: #1e293b; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; display: block; }
.picker-price { font-size: 28rpx; color: #ff4544; font-weight: bold; }
.picker-meta { display: flex; align-items: center; gap: 12rpx; margin-top: 8rpx; }
.picker-status { font-size: 22rpx; color: #94a3b8; }
.picker-sub { font-size: 22rpx; color: #94a3b8; display: block; margin-top: 4rpx; }
.picker-empty { text-align: center; padding: 80rpx 0; color: #94a3b8; font-size: 28rpx; }
</style>
