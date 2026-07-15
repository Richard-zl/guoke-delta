/**
 * 单连接 WebSocket 管理
 *
 * 微信小程序同时最多 2 个 WebSocket（开发工具的热更新会占 1 个），
 * 因此整个小程序只维护 **一条** 业务 WebSocket 连接：
 *   - App 启动后建立，全生命周期保持连接
 *   - 聊天室进出只切换 currentSessionId，不断开/重连
 *   - 发送消息前检查 state，CONNECTING 时自动排队
 */

const WS_BASE_URL = import.meta.env.VITE_WS_BASE_URL || 'ws://guokegames.online/ws/chat'
const HEARTBEAT_INTERVAL = 30000
const MAX_RECONNECT = 5

// DISCONNECTED → CONNECTING → CONNECTED → CLOSING → DISCONNECTED
let state = 'DISCONNECTED'
let socketTask = null
let heartbeatTimer = null
let reconnectCount = 0
let reconnectTimer = null
let currentToken = null
let currentChatRole = ''

const callbacks = { onMessage: null, onConnect: null, onClose: null }
let sendQueue = []
let _pendingConnect = null

// ─── 公开 API ───────────────────────────────────────────

export function connectWebSocket(token, options = {}) {
  const chatRole = options.chatRole || ''

  if (state === 'CONNECTED' || state === 'CONNECTING') {
    if (currentToken === token && currentChatRole === chatRole) {
      console.log('[WS] already connected/connecting, reuse')
      return
    }
    // token 或 chatRole 变了，需要先关再连
    _pendingConnect = { token, chatRole }
    closeWebSocket()
    return
  }

  if (state === 'CLOSING') {
    _pendingConnect = { token, chatRole }
    return
  }

  currentToken = token
  currentChatRole = chatRole
  reconnectCount = 0
  _clearReconnectTimer()
  _doConnect()
}

export function closeWebSocket() {
  reconnectCount = MAX_RECONNECT
  _clearReconnectTimer()
  _stopHeartbeat()
  sendQueue.length = 0
  if (socketTask) {
    state = 'CLOSING'
    try { socketTask.close() } catch (_) {}
  } else {
    state = 'DISCONNECTED'
  }
}

/**
 * 发送数据。
 *  - CONNECTED → 立即发送
 *  - CONNECTING → 放入队列，连接就绪后自动发送
 *  - 其他状态 → 返回 false
 */
export function sendMessage(data) {
  if (state === 'CONNECTED' && socketTask) {
    socketTask.send({ data: JSON.stringify(data) })
    return true
  }
  if (state === 'CONNECTING') {
    sendQueue.push(data)
    return true
  }
  console.warn(`[WS] send failed, state=${state}`)
  return false
}

export function onWsMessage(cb)  { callbacks.onMessage = cb }
export function onWsConnect(cb)  { callbacks.onConnect = cb }
export function onWsClose(cb)    { callbacks.onClose   = cb }
export function isWsConnected()  { return state === 'CONNECTED' }

// ─── 内部实现 ───────────────────────────────────────────

function _doConnect() {
  state = 'CONNECTING'
  let url = `${WS_BASE_URL}?token=${currentToken}`
  if (currentChatRole) url += `&chatRole=${encodeURIComponent(currentChatRole)}`

  console.log(`[WS] connecting → ${url}`)
  socketTask = uni.connectSocket({ url, success() {} })

  socketTask.onOpen(() => {
    console.log('[WS] connected')
    state = 'CONNECTED'
    reconnectCount = 0
    _startHeartbeat()
    callbacks.onConnect && callbacks.onConnect()
    _flushQueue()
  })

  socketTask.onMessage((res) => {
    try {
      const data = JSON.parse(res.data)
      if (data.type !== 'pong') {
        callbacks.onMessage && callbacks.onMessage(data)
      }
    } catch (_) {}
  })

  socketTask.onError((err) => {
    console.error('[WS] error:', err)
  })

  socketTask.onClose(() => {
    console.log('[WS] closed')
    const wasIntentional = state === 'CLOSING'
    _stopHeartbeat()
    socketTask = null
    state = 'DISCONNECTED'
    callbacks.onClose && callbacks.onClose()

    // 有待连的参数（token/chatRole 切换），立即重连
    if (_pendingConnect) {
      const { token, chatRole } = _pendingConnect
      _pendingConnect = null
      currentToken = token
      currentChatRole = chatRole
      reconnectCount = 0
      setTimeout(() => _doConnect(), 100)
      return
    }

    // 非主动关闭 → 自动重连
    if (!wasIntentional && reconnectCount < MAX_RECONNECT) {
      reconnectCount++
      console.log(`[WS] reconnecting (${reconnectCount}/${MAX_RECONNECT})...`)
      reconnectTimer = setTimeout(() => _doConnect(), 3000 * reconnectCount)
    }
  })
}

function _flushQueue() {
  while (sendQueue.length > 0) {
    const d = sendQueue.shift()
    if (socketTask) socketTask.send({ data: JSON.stringify(d) })
  }
}

function _startHeartbeat() {
  _stopHeartbeat()
  heartbeatTimer = setInterval(() => {
    if (state === 'CONNECTED' && socketTask) {
      socketTask.send({ data: JSON.stringify({ type: 'ping' }) })
    }
  }, HEARTBEAT_INTERVAL)
}

function _stopHeartbeat() {
  if (heartbeatTimer) { clearInterval(heartbeatTimer); heartbeatTimer = null }
}

function _clearReconnectTimer() {
  if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null }
}
