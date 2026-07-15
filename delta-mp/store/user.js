import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getUserToken, setUserToken, removeUserToken, removePlayerToken } from '@/utils/auth'
import { userLogin, h5Login } from '@/api/auth'
import { getUserProfile } from '@/api/user'
import { useChatStore } from '@/store/chat'

export const useUserStore = defineStore('user', () => {
  const token = ref(getUserToken())
  const userInfo = ref(null)

  const isLoggedIn = computed(() => !!token.value)
  const nickname = computed(() => userInfo.value?.nickname || '未登录')
  const avatar = computed(() => userInfo.value?.avatar || '')
  const phone = computed(() => userInfo.value?.phone || '')

  /** 微信登录（profile 可选：首次注册 nickname / avatar） */
  async function login(phoneCode = '', profile = {}) {
    try {
      const loginRes = await uni.login({ provider: 'weixin' })
      const { data } = await userLogin(loginRes.code, phoneCode, profile)
      applyLoginResult(data)
      return { success: true, code: 200, data }
    } catch (e) {
      return {
        success: false,
        code: e?.code || 500,
        msg: e?.msg || e?.message || '登录失败'
      }
    }
  }

  /** H5 手机号验证码登录 */
  async function loginByPhone(phone, code) {
    try {
      const { data } = await h5Login({ phone, code })
      applyLoginResult(data)
      return { success: true, code: 200, data }
    } catch (e) {
      return {
        success: false,
        code: e?.code || 500,
        msg: e?.msg || e?.message || '登录失败'
      }
    }
  }

  function applyLoginResult(data) {
    token.value = data.token
    setUserToken(data.token)
    userInfo.value = data
    const chatStore = useChatStore()
    chatStore.fetchMessageUnreadCount()
  }

  /** 获取用户资料 */
  async function fetchProfile(silent = false) {
    if (!token.value) return
    try {
      const { data } = await getUserProfile(silent ? { loading: false } : {})
      userInfo.value = data
    } catch (e) {
      // token失效时已由request拦截器处理
    }
  }

  /** 退出登录 */
  function logout() {
    token.value = ''
    userInfo.value = null
    removeUserToken()
    removePlayerToken()
    uni.reLaunch({ url: '/pages/login/index' })
  }

  /** 检查登录态，未登录则跳登录页 */
  function checkLogin() {
    if (!token.value) {
      uni.navigateTo({ url: '/pages/login/index' })
      return false
    }
    return true
  }

  return {
    token,
    userInfo,
    isLoggedIn,
    nickname,
    avatar,
    phone,
    login,
    loginByPhone,
    fetchProfile,
    logout,
    checkLogin
  }
})
