import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi } from '@/api/auth'
import { getToken, setToken, removeToken, getUserInfo, setUserInfo } from '@/utils/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref(getToken() || '')
  const adminId = ref(getUserInfo()?.adminId || null)
  const nickname = ref(getUserInfo()?.nickname || '')
  const avatar = ref(getUserInfo()?.avatar || '')
  const role = ref(getUserInfo()?.role || '')

  async function login(loginForm) {
    // 发送标准化角色: admin → "ADMIN", cs → "CS"
    const payload = { ...loginForm }
    payload.role = payload.role === 'admin' ? 'ADMIN' : 'CS'
    const res = await loginApi(payload)
    const { token: t, adminId: id, nickname: name, avatar: av, role: r } = res.data
    // 后端返回有效角色 ADMIN/CS，映射为前端值 admin/cs (兼容旧值 BOTH)
    const mappedRole = (r === 'ADMIN' || r === 'BOTH') ? 'admin' : 'cs'
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
