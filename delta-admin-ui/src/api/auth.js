import request from '@/utils/request'

export function login(data) {
  return request({ url: '/cs/auth/login', method: 'post', data })
}

/** 客服/管理员资料（改名字换头像） */
export function getCsProfile() {
  return request({ url: '/cs/profile', method: 'get' })
}

export function updateCsProfile(data) {
  return request({ url: '/cs/profile', method: 'put', data })
}
