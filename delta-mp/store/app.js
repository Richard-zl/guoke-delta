import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ROLE } from '@/utils/constants'
import { navigateToRole } from '@/utils/role'

const ROLE_KEY = 'app_role'

export const useAppStore = defineStore('app', () => {
  const role = ref(ROLE.USER)

  /** 从Storage恢复角色 */
  function restoreRole() {
    const saved = uni.getStorageSync(ROLE_KEY)
    if (saved && Object.values(ROLE).includes(saved)) {
      role.value = saved
    }
  }

  /** 持久化角色 */
  function _saveRole() {
    uni.setStorageSync(ROLE_KEY, role.value)
  }

  /** 切换到用户端 */
  function switchToUser() {
    role.value = ROLE.USER
    _saveRole()
    navigateToRole(ROLE.USER)
  }

  /** 切换到接单员端 */
  function switchToPlayer() {
    role.value = ROLE.PLAYER
    _saveRole()
    navigateToRole(ROLE.PLAYER)
  }

  /** 切换到客服端 */
  function switchToCs() {
    role.value = ROLE.CS
    _saveRole()
    navigateToRole(ROLE.CS)
  }

  /** 判断当前角色 */
  const isUser = () => role.value === ROLE.USER
  const isPlayer = () => role.value === ROLE.PLAYER
  const isCs = () => role.value === ROLE.CS

  return {
    role,
    restoreRole,
    switchToUser,
    switchToPlayer,
    switchToCs,
    isUser,
    isPlayer,
    isCs
  }
})
