import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ElNotification } from 'element-plus'
import router from '@/router'
import { useUserStore } from '@/stores/user'
import { canReviewRefund } from '@/utils/role'

/**
 * 用音频文件播报，不走浏览器朗读。
 * 朗读会在订单页重绘、以及切到别的标签时被停掉；音频元素挂在页面上，切走也能继续响。
 */
const voiceQueue = []
let voicePlaying = false

function voiceUrl(file) {
  const prefix = (import.meta.env.BASE_URL || '/').replace(/\/$/, '')
  return `${prefix}/audio/${file}`
}

function createVoice(file) {
  const audio = new Audio(voiceUrl(file))
  audio.preload = 'auto'
  audio.setAttribute('playsinline', '')
  audio.style.position = 'fixed'
  audio.style.left = '-9999px'
  audio.style.width = '0'
  audio.style.height = '0'
  return audio
}

const voiceClips = {
  order: createVoice('new-order.wav'),
  refund: createVoice('new-refund.wav'),
}

function mountVoices() {
  if (!document.body) return
  Object.values(voiceClips).forEach((audio) => {
    if (!audio.isConnected) document.body.appendChild(audio)
  })
}

/** 第一次点击时解开浏览器的自动播放限制，之后后台标签也能出声 */
function unlockVoices() {
  mountVoices()
  Object.values(voiceClips).forEach((audio) => {
    audio.muted = true
    const pending = audio.play()
    const reset = () => {
      if (voicePlaying || voiceQueue.length > 0) {
        audio.muted = false
        return
      }
      audio.pause()
      audio.currentTime = 0
      audio.muted = false
    }
    if (pending && typeof pending.then === 'function') pending.then(reset).catch(reset)
    else reset()
  })
}

if (typeof window !== 'undefined') {
  window.addEventListener('pointerdown', unlockVoices, { capture: true, once: true })
}

function drainVoices() {
  if (voicePlaying || voiceQueue.length === 0) return
  const kind = voiceQueue[0]
  const audio = voiceClips[kind]
  if (!audio) {
    voiceQueue.shift()
    drainVoices()
    return
  }
  mountVoices()
  voicePlaying = true
  audio.muted = false
  audio.currentTime = 0
  const finish = () => {
    if (!voicePlaying) return
    voicePlaying = false
    voiceQueue.shift()
    drainVoices()
  }
  audio.onended = finish
  const pending = audio.play()
  if (!pending || typeof pending.catch !== 'function') return
  pending.catch(() => {
    voicePlaying = false
    const retry = () => {
      if (document.visibilityState !== 'visible') return
      document.removeEventListener('visibilitychange', retry)
      drainVoices()
    }
    document.addEventListener('visibilitychange', retry)
  })
}

/** 任意页面、包括切到别的浏览器标签，都播这段语音 */
function playVoice(kind) {
  voiceQueue.push(kind)
  drainVoices()
}

/** 运营后台业务提醒：新订单、退款申请，由 WebSocket 推送后语音播报 */
export const useOrderNotifyStore = defineStore('orderNotify', () => {
  const unread = ref(0)
  const refundUnread = ref(0)
  /** 订单列表在场时递增，用来刷新当前表格 */
  const refreshTick = ref(0)
  /** 点击订单提醒时递增，订单列表切到待指派 */
  const unassignedTick = ref(0)
  /** 退款列表在场时递增，用来刷新当前表格 */
  const refundRefreshTick = ref(0)
  /** 点击退款提醒时递增，退款列表切到待处理 */
  const refundFocusTick = ref(0)

  function describe(data) {
    const orderNo = data?.orderNo || ''
    const product = data?.productName || ''
    const amount = data?.amount ? ` ¥${data.amount}` : ''
    const name = product.length > 20 ? `${product.slice(0, 20)}...` : product
    return [orderNo, name + amount].filter(Boolean).join(' ')
  }

  /** 打开待指派列表，并清掉顶栏未读 */
  function openUnassigned() {
    unread.value = 0
    unassignedTick.value++
    router.push({ path: '/order/list', query: { quick: 'unassigned' } })
  }

  /** 打开待处理退款。没有审核权限时只清掉未读。 */
  function openRefund() {
    refundUnread.value = 0
    if (!canReviewRefund(useUserStore().role)) return
    refundFocusTick.value++
    router.push({ path: '/refund', query: { status: 'PENDING' } })
  }

  function onPaid(data) {
    // 先播语音，再刷新列表。订单页重绘不会把这段声音停掉，切到别的标签也会播。
    playVoice('order')
    refreshTick.value++
    const onOrderPage = router.currentRoute.value.path.startsWith('/order')
    if (!onOrderPage) unread.value++
    ElNotification({
      title: '新订单已支付',
      message: describe(data),
      type: 'success',
      duration: 8000,
      onClick: () => openUnassigned()
    })
  }

  function onRefund(data) {
    playVoice('refund')
    refundRefreshTick.value++
    const onRefundPage = router.currentRoute.value.path.startsWith('/refund')
    if (!onRefundPage) refundUnread.value++
    ElNotification({
      title: '新的退款申请',
      message: describe(data) || '客人申请了退款，请尽快审核',
      type: 'warning',
      duration: 8000,
      onClick: () => openRefund()
    })
  }

  return {
    unread,
    refundUnread,
    refreshTick,
    unassignedTick,
    refundRefreshTick,
    refundFocusTick,
    onPaid,
    onRefund,
    openUnassigned,
    openRefund
  }
})
