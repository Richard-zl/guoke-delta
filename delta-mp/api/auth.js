import { post } from './request'

/** 用户微信登录（profile 可选：首次注册 nickname / avatar） */
export const userLogin = (code, phoneCode, profile = {}) => post('/user/auth/login', {
  code,
  phoneCode,
  nickname: profile.nickname,
  avatar: profile.avatar
}, { auth: false })

/** H5 手机号验证码登录 */
export const h5Login = (data) => post('/user/auth/h5-login', data, { auth: false })

/** 用户切换到打手端：携带 user_token，返回 player_token（每次切换都需重新获取） */
export const switchToPlayerToken = () => post('/user/auth/switch-to-player', {}, { role: 'user', loading: true })

/** 客服登录（账号密码） */
export const csLogin = (data) => post('/cs/auth/login', data, { auth: false })
