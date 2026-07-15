import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getTokenByRole } from '@/utils/auth'
import { connectWebSocket, closeWebSocket, onWsMessage, onWsConnect, onWsClose, isWsConnected, sendMessage } from '@/utils/websocket'
import { getUnreadCount } from '@/api/message'
import { playMessageNotification } from '@/utils/notificationFeedback'

let systemUnreadPollTimer = null

export const useChatStore = defineStore('chat', () => {
  const connected = ref(false)
  const unreadCount = ref(0)
  const messageUnreadCount = ref(0)
  const currentSessionId = ref(null)
  const newMessage = ref(null)
  let lastSystemUnread = null
  let _callbacksBound = false

  async function fetchMessageUnreadCount() {
    const role = uni.getStorageSync('app_role') || 'user'
    const token = getTokenByRole(role)
    if (!token) {
      stopPoll()
      return
    }
    try {
      const type = role === 'cs' ? 'CS' : role === 'player' ? 'PLAYER' : 'USER'
      const res = await getUnreadCount(type, { loading: false })
      const newCount = res.data ?? 0
      if (lastSystemUnread !== null && newCount > lastSystemUnread) {
        playMessageNotification()
        uni.showToast({ title: '您有新的系统通知', icon: 'none' })
      }
      lastSystemUnread = newCount
      messageUnreadCount.value = newCount
    } catch (e) {
      if (e?.code === 401 || e?.statusCode === 401) {
        stopPoll()
      }
    }
  }

  function stopPoll() {
    if (systemUnreadPollTimer) {
      clearInterval(systemUnreadPollTimer)
      systemUnreadPollTimer = null
    }
  }

  const totalUnreadCount = computed(() => unreadCount.value)

  /**
   * 建立 WebSocket 连接（全局唯一，App 启动后保持常驻）。
   * 进出聊天室只需调 setCurrentSession / clearCurrentSession 切换会话，
   * 无需断开/重连。
   */
  function connect(opts = {}) {
    const role = uni.getStorageSync('app_role') || 'user'
    const token = getTokenByRole(role)
    if (!token) return

    const chatRole = opts.chatRole ?? (role === 'player' ? 'PLAYER' : role === 'cs' ? 'CS' : 'USER')

    // 回调只绑定一次，避免重复注册
    if (!_callbacksBound) {
      _callbacksBound = true

      onWsConnect(() => {
        connected.value = true
        fetchMessageUnreadCount()
        if (systemUnreadPollTimer) clearInterval(systemUnreadPollTimer)
        systemUnreadPollTimer = setInterval(fetchMessageUnreadCount, 25000)
      })

      onWsMessage((data) => {
        newMessage.value = data
        if (String(data.sessionId) !== String(currentSessionId.value)) {
          unreadCount.value++
          fetchMessageUnreadCount()
          playMessageNotification()
          uni.showToast({ title: '收到新消息', icon: 'none' })
        }
      })

      onWsClose(() => {
        connected.value = false
      })
    }

    connectWebSocket(token, chatRole ? { chatRole } : {})
  }

  /**
   * 仅在退出登录时调用，正常切换页面不要调。
   */
  function disconnect() {
    stopPoll()
    closeWebSocket()
    connected.value = false
    _callbacksBound = false
  }

  function setCurrentSession(sessionId) {
    currentSessionId.value = sessionId
  }

  function clearCurrentSession() {
    currentSessionId.value = null
  }

  function resetUnread() {
    unreadCount.value = 0
  }

  function setUnreadFromServer(count) {
    unreadCount.value = Math.max(0, count ?? 0)
  }

  function setSystemUnreadFromServer(count) {
    messageUnreadCount.value = Math.max(0, count ?? 0)
    if (lastSystemUnread !== null) lastSystemUnread = messageUnreadCount.value
  }

  return {
    connected,
    unreadCount,
    messageUnreadCount,
    totalUnreadCount,
    fetchMessageUnreadCount,
    setUnreadFromServer,
    setSystemUnreadFromServer,
    currentSessionId,
    newMessage,
    connect,
    disconnect,
    setCurrentSession,
    clearCurrentSession,
    resetUnread
  }
})
