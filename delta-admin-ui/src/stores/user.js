import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi } from '@/api/auth'
import { getToken, setToken, removeToken, getUserInfo, setUserInfo } from '@/utils/auth'
import { normalizeRole } from '@/utils/role'

export const useUserStore = defineStore('user', () => {
  const token = ref(getToken() || '')
  const adminId = ref(getUserInfo()?.adminId || null)
  const nickname = ref(getUserInfo()?.nickname || '')
  const avatar = ref(getUserInfo()?.avatar || '')
  const role = ref(getUserInfo()?.role || '')

  async function login(loginForm) {
    const res = await loginApi({ username: loginForm.username, password: loginForm.password })
    const { token: t, adminId: id, nickname: name, avatar: av, role: r } = res.data
    const mappedRole = normalizeRole(r)
    token.value = t
    adminId.value = id
    nickname.value = name
    avatar.value = av || ''
    role.value = mappedRole
    setToken(t)
    setUserInfo({ adminId: id, nickname: name, avatar: av || '', role: mappedRole })
    return res
  }

  function updateProfile({ nickname: name, avatar: av }) {
    if (name != null) { nickname.value = name }
    if (av != null) { avatar.value = av }
    const info = getUserInfo() || {}
    setUserInfo({ ...info, nickname: nickname.value, avatar: avatar.value })
  }

  function logout() {
    token.value = ''
    adminId.value = null
    nickname.value = ''
    avatar.value = ''
    role.value = ''
    removeToken()
  }

  return { token, adminId, nickname, avatar, role, login, logout, updateProfile }
})
