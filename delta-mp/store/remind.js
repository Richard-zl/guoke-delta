import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getCsRemind } from '@/api/cs'
import { getPlayerRemind } from '@/api/player'
import { playMessageNotification } from '@/utils/notificationFeedback'

/** 客服/接单员端提醒红点数据（用户端系统通知用 chatStore.messageUnreadCount） */
export const useRemindStore = defineStore('remind', () => {
  const complaintUnread = ref(0)
  const relayUnread = ref(0)
  const replaceUnread = ref(0)
  const messageUnread = ref(0)
  const systemUnread = ref(0)
  const inviteCount = ref(0)
  let pollTimer = null
  let pollingRole = ''
  let lastRelayUnread = null

  async function fetchCsRemind(options = {}) {
    const notifyIncrease = options.notifyIncrease === true
    try {
      const res = await getCsRemind({ loading: false })
      const d = res.data || {}
      const nextRelayUnread = d.relayUnread ?? 0
      if (notifyIncrease && lastRelayUnread !== null && nextRelayUnread > lastRelayUnread) {
        playMessageNotification()
        uni.showToast({ title: '收到新的接力申请', icon: 'none' })
      }
      complaintUnread.value = d.complaintUnread ?? 0
      relayUnread.value = nextRelayUnread
      replaceUnread.value = d.replaceUnread ?? 0
      messageUnread.value = d.messageUnread ?? 0
      systemUnread.value = d.systemUnread ?? 0
      lastRelayUnread = nextRelayUnread
    } catch (_) {
    complaintUnread.value = 0
      relayUnread.value = 0
      replaceUnread.value = 0
      messageUnread.value = 0
      systemUnread.value = 0
    }
  }

  async function fetchPlayerRemind() {
    try {
      const res = await getPlayerRemind({ loading: false })
      const d = res.data || {}
      inviteCount.value = d.inviteCount ?? 0
      systemUnread.value = d.systemUnread ?? 0
      messageUnread.value = d.messageUnread ?? 0
    } catch (_) {
      inviteCount.value = 0
      systemUnread.value = 0
      messageUnread.value = 0
    }
  }

  function startPolling(role) {
    if (pollTimer && pollingRole === role) return
    stopPolling()

    if (role === 'cs') {
      pollingRole = role
      fetchCsRemind()
      pollTimer = setInterval(() => fetchCsRemind({ notifyIncrease: true }), 15000)
      return
    }

    if (role === 'player') {
      pollingRole = role
      fetchPlayerRemind()
      pollTimer = setInterval(fetchPlayerRemind, 15000)
    }
  }

  function stopPolling() {
    if (pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
    pollingRole = ''
  }

  function clear() {
    stopPolling()
    complaintUnread.value = 0
    relayUnread.value = 0
    replaceUnread.value = 0
    messageUnread.value = 0
    systemUnread.value = 0
    inviteCount.value = 0
    lastRelayUnread = null
  }

  return {
    complaintUnread,
    relayUnread,
    replaceUnread,
    messageUnread,
    systemUnread,
    inviteCount,
    fetchCsRemind,
    fetchPlayerRemind,
    startPolling,
    stopPolling,
    clear
  }
})
