import request from '@/utils/request'

export function login(data) {
  return request({ url: '/cs/auth/login', method: 'post', data })
}

/** 个人资料：昵称、头像、自己的登录密码 */
export function getCsProfile() {
  return request({ url: '/cs/profile', method: 'get' })
}

export function updateCsProfile(data) {
  return request({ url: '/cs/profile', method: 'put', data })
}
