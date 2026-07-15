import { defineStore } from 'pinia'
import { ref, shallowRef } from 'vue'
import { connectWebSocket, closeWebSocket, onWsMessage, onWsConnect, onWsClose } from '@/utils/websocket'
import { getToken } from '@/utils/auth'
import { ElNotification } from 'element-plus'

export const useChatStore = defineStore('chat', () => {
  const connected = ref(false)
  /** 最新收到的消息（供页面 watch 使用） */
  const newMessage = shallowRef(null)
  /** 全局未读消息数 */
  const globalUnread = ref(0)
  /** 当前正在查看的会话 ID（聊天页设置，用于抑制当前会话通知） */
  const currentSessionId = ref(null)

  let _started = false

  /** 播放悦耳的通知提示音 (三音阶上行叮咚) */
  function playNotificationSound() {
    try {
      const ctx = new (window.AudioContext || window.webkitAudioContext)()
      const now = ctx.currentTime

      // 三个上行音符：C5 → E5 → G5，形成大三和弦
      const notes = [
        { freq: 523, start: 0, dur: 0.12 },
        { freq: 659, start: 0.13, dur: 0.12 },
        { freq: 784, start: 0.26, dur: 0.18 }
      ]

      notes.forEach(({ freq, start, dur }) => {
        const osc = ctx.createOscillator()
        const gain = ctx.createGain()
        osc.type = 'sine'
        osc.frequency.value = freq
        gain.gain.setValueAtTime(0, now + start)
        gain.gain.linearRampToValueAtTime(0.18, now + start + 0.02) // 柔和起音
        gain.gain.exponentialRampToValueAtTime(0.001, now + start + dur) // 自然衰减
        osc.connect(gain)
        gain.connect(ctx.destination)
        osc.start(now + start)
        osc.stop(now + start + dur + 0.05)
      })
    } catch { /* 浏览器不支持 */ }
  }

  function _handleMessage(data) {
    // 更新最新消息（触发 watch）
    newMessage.value = data

    // 如果不是当前正在查看的会话 → 弹通知 + 提示音
    if (!currentSessionId.value || String(data.sessionId) !== String(currentSessionId.value)) {
      globalUnread.value++
      playNotificationSound()

      const senderName = data.senderType === 'USER' ? '用户' : '打手'
      let preview = data.content || ''
      if (data.type === 'IMAGE') preview = '[图片]'
      else if (data.type === 'PRODUCT') preview = '[商品]'
      else if (data.type === 'ORDER') preview = '[订单]'
      if (preview.length > 30) preview = preview.substring(0, 30) + '...'

      ElNotification({
        title: `${senderName}发来新消息`,
        message: preview,
        type: 'info',
        duration: 4000
      })
    }
  }

  /** 启动全局 WebSocket 连接 */
  function start() {
    if (_started) return
    const token = getToken()
    if (!token) return

    _started = true

    onWsConnect(() => {
      connected.value = true
      console.log('[ChatStore] WS connected')
    })

    onWsClose(() => {
      connected.value = false
    })

    onWsMessage(_handleMessage)

    connectWebSocket(token, { chatRole: 'CS' })
  }

  /** 关闭全局 WebSocket 连接 */
  function stop() {
    _started = false
    closeWebSocket()
    connected.value = false
  }

  /** 清除全局未读计数 */
  function clearUnread() {
    globalUnread.value = 0
  }

  return {
    connected,
    newMessage,
    globalUnread,
    currentSessionId,
    start,
    stop,
    clearUnread,
    playNotificationSound
  }
})
