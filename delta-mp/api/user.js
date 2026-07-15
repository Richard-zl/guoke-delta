import { get, post, put, del } from './request'

/** 获取用户资料（始终使用 user_token） */
export const getUserProfile = (opts = {}) => get('/user/profile', {}, { role: 'user', ...opts })

/** 修改用户资料（始终使用 user_token） */
export const updateUserProfile = (data) => put('/user/profile', data, { role: 'user' })

/** 修改手机号（始终使用 user_token） */
export const updatePhone = (data) => put('/user/phone', data, { role: 'user' })

/** 获取钱包信息（始终使用 user_token） */
export const getWallet = () => get('/user/wallet', {}, { role: 'user' })

/** 已保存的游戏信息列表（下单页可选择复用） */
export const getGameInfoList = () => get('/user/game-info/list', {}, { role: 'user' })

/** 保存当前填写的游戏信息，下次下单可选择 */
export const saveGameInfo = (data) => post('/user/game-info', data, { role: 'user' })

/** 按分类获取已保存的动态字段信息 */
export const getSavedInfoByCategory = (categoryId) => get(`/user/game-info/list/${categoryId}`, {}, { role: 'user' })

/** 保存动态字段信息 */
export const saveDynamicInfo = (data) => post('/user/game-info/dynamic', data, { role: 'user' })

/** 删除已保存信息 */
export const deleteSavedInfo = (id) => del(`/user/game-info/${id}`, {}, { role: 'user' })

// ==================== 积分和优惠券 ====================

/** 获取用户信息（含积分和等级） */
export const getUserInfo = () => {
  return get('/user/info', {}, { role: 'user' })
}

/** 获取用户优惠券数量 */
export const getCouponCount = () => {
  return get('/user/coupon/count', {}, { role: 'user' })
}

/** 获取用户优惠券列表 */
export const getUserCoupons = (params) => {
  return get('/user/coupon/list', params, { role: 'user' })
}

/** 获取可用优惠券（下单时使用） */
export const getAvailableCoupons = (params) => {
  return get('/user/coupon/available', params, { role: 'user' })
}

/** 获取积分明细 */
export const getPointsDetail = (params) => {
  return get('/user/points/detail', params, { role: 'user' })
}