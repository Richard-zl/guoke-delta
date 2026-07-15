/**
 * WebSocket 管理 - 客服/管理端聊天
 * 心跳保活 + 断线自动重连
 */

const HEARTBEAT_INTERVAL = 30000
const MAX_RECONNECT = 5

let ws = null
let heartbeatTimer = null
let reconnectCount = 0
let currentToken = null
let onMessageCallback = null
let onConnectCallback = null
let onCloseCallback = null
let manualClose = false

let currentChatRole = ''

function getWsUrl(token, chatRole = '') {
  const params = new URLSearchParams()
  params.set('token', token)
  if (chatRole) params.set('chatRole', chatRole)
  const qs = params.toString()

  // 优先使用环境变量（dev/staging 指向 test 子域）
  const envWs = import.meta.env.VITE_WS_BASE_URL
  if (envWs) {
    const sep = envWs.includes('?') ? '&' : '?'
    return `${envWs}${sep}${qs}`
  }

  const loc = window.location
  const path = '/ws/chat'
  const base = loc.hostname === 'localhost' || loc.hostname === '127.0.0.1'
    ? 'ws://localhost:8080'
    : `${loc.protocol === 'https:' ? 'wss:' : 'ws:'}//${loc.host}`
  return `${base}${path}?${qs}`
}

/**
 * 建立连接
 * @param {string} token
 * @param {{ chatRole?: string }} options - 客服端传 chatRole: 'CS' 或 'ADMIN'，便于后端按身份推送
 */
export function connectWebSocket(token, options = {}) {
  if (ws) closeWebSocket()
  currentToken = token
  currentChatRole = options.chatRole || ''
  reconnectCount = 0
  manualClose = false
  _doConnect()
}

function _doConnect() {
  const url = getWsUrl(currentToken, currentChatRole)
  ws = new WebSocket(url)

  ws.onopen = () => {
    console.log('[WS] connected')
    reconnectCount = 0
    _startHeartbeat()
    onConnectCallback && onConnectCallback()
  }

  ws.onmessage = (evt) => {
    try {
      const data = JSON.parse(evt.data)
      if (data.type !== 'pong') {
        onMessageCallback && onMessageCallback(data)
      }
    } catch { /* ignore non-json */ }
  }

  ws.onerror = (err) => {
    console.error('[WS] error:', err)
  }

  ws.onclose = () => {
    console.log('[WS] closed')
    _stopHeartbeat()
    ws = null
    onCloseCallback && onCloseCallback()
    // 自动重连（非手动关闭时）
    if (!manualClose && reconnectCount < MAX_RECONNECT) {
      reconnectCount++
      console.log(`[WS] reconnecting (${reconnectCount}/${MAX_RECONNECT})...`)
      setTimeout(() => _doConnect(), 3000 * reconnectCount)
    }
  }
}

function _startHeartbeat() {
  _stopHeartbeat()
  heartbeatTimer = setInterval(() => {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ type: 'ping' }))
    }
  }, HEARTBEAT_INTERVAL)
}

function _stopHeartbeat() {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
}

/**
 * 发送消息
 */
export function sendWsMessage(data) {
  if (!ws || ws.readyState !== WebSocket.OPEN) {
    console.warn('[WS] not connected, cannot send')
    return false
  }
  ws.send(JSON.stringify(data))
  return true
}

/**
 * 手动关闭连接（阻止自动重连）
 */
export function closeWebSocket() {
  manualClose = true
  _stopHeartbeat()
  if (ws) {
    ws.close()
    ws = null
  }
}

/**
 * 注册回调
 */
export function onWsMessage(callback) { onMessageCallback = callback }
export function onWsConnect(callback) { onConnectCallback = callback }
export function onWsClose(callback) { onCloseCallback = callback }
export function isWsConnected() { return ws !== null && ws.readyState === WebSocket.OPEN }
