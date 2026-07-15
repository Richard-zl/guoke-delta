import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getPlayerProfile } from '@/api/player'

export const usePlayerStore = defineStore('player', () => {
  const playerInfo = ref(null)

  const playerId = computed(() => playerInfo.value?.id || null)
  const auditStatus = computed(() => playerInfo.value?.status || '')
  const isApproved = computed(() => playerInfo.value?.status === 'ACTIVE')

  /** 获取接单员资料；opts.role='user' 时用用户token（切换前校验），'player' 用接单员token */
  async function fetchProfile(opts = {}) {
    try {
      const { data } = await getPlayerProfile(opts)
      playerInfo.value = data
      return data
    } catch (e) {
      return null
    }
  }

  /** 清空接单员信息 */
  function reset() {
    playerInfo.value = null
  }

  return {
    playerInfo,
    playerId,
    auditStatus,
    isApproved,
    fetchProfile,
    reset
  }
})
