/**
 * Token 存取管理
 * - user_token：用户端专用
 * - player_token：接单员端专用，从用户端切换到接单员端时调用 switch-to-player 获取并保存
 * - cs_token：仅客服端
 * 每次切换到接单员端都需重新获取 player_token
 */

const USER_TOKEN_KEY = 'user_token'
const PLAYER_TOKEN_KEY = 'player_token'
const CS_TOKEN_KEY = 'cs_token'

// ========== 用户 Token ==========
export function getUserToken() {
  return uni.getStorageSync(USER_TOKEN_KEY) || ''
}

export function setUserToken(token) {
  uni.setStorageSync(USER_TOKEN_KEY, token)
}

export function removeUserToken() {
  uni.removeStorageSync(USER_TOKEN_KEY)
}

// ========== 接单员 Token（切换接单员端时获取并保存） ==========
export function getPlayerToken() {
  return uni.getStorageSync(PLAYER_TOKEN_KEY) || ''
}

export function setPlayerToken(token) {
  uni.setStorageSync(PLAYER_TOKEN_KEY, token)
}

export function removePlayerToken() {
  uni.removeStorageSync(PLAYER_TOKEN_KEY)
}

// ========== 客服Token ==========
const CS_INFO_KEY = 'cs_info'

export function getCsToken() {
  return uni.getStorageSync(CS_TOKEN_KEY) || ''
}

export function setCsToken(token) {
  uni.setStorageSync(CS_TOKEN_KEY, token)
}

export function getCsInfo() {
  return uni.getStorageSync(CS_INFO_KEY) || null
}

export function setCsInfo(info) {
  uni.setStorageSync(CS_INFO_KEY, info)
}

export function removeCsToken() {
  uni.removeStorageSync(CS_TOKEN_KEY)
  uni.removeStorageSync(CS_INFO_KEY)
}

// ========== 按角色取 token ==========
export function getTokenByRole(role) {
  if (role === 'cs') return getCsToken()
  if (role === 'player') return getPlayerToken()
  return getUserToken()
}

// ========== 清除全部token ==========
export function clearAllTokens() {
  removeUserToken()
  removePlayerToken()
  removeCsToken()
}
