import { get, post, put, del } from './request'

// ========== 接单员资料 ==========
/** opts.role: 'user' 用用户token校验（切换前），'player' 用接单员token（接单员端） */
export const getPlayerProfile = (opts = {}) => get('/player/profile', {}, { role: opts.role || 'player', ...opts })
export const updatePlayerProfile = (data) => put('/player/profile', data, { role: 'player' })
export const toggleOnlineStatus = (online) => post(`/player/online-status?online=${online}`, {}, { role: 'player' })
export const applyPlayer = (data) => post('/player/apply', data)

// ========== 接单员首页 ==========
export const getPlayerHome = () => get('/player/home', {}, { role: 'player' })
/** 提醒红点：被邀请数、系统通知未读、消息未读 */
export const getPlayerRemind = (opts = {}) => get('/player/remind', {}, { role: 'player', ...opts })

// ========== 接单员订单 ==========
export const getOrderHall = (params) => get('/player/order/hall', params, { role: 'player' })
// 打手获取自己被指派的订单（包括作为主打手和辅助打手）
export const getMyOrders = (params) => get('/player/order/my-orders', params, { role: 'player' })
export const acceptOrder = (id) => post(`/player/order/${id}/accept`, {}, { role: 'player' })
export const startOrder = (id) => post(`/player/order/${id}/start`, {}, { role: 'player' })
export const completeOrder = (id, data = {}) => post(`/player/order/${id}/complete`, data, { role: 'player' })
export const getPlayerOrderProgress = (id) => get(`/player/order/${id}/progress`, {}, { role: 'player' })
export const rejectAssign = (id) => post(`/player/order/${id}/reject-assign`, {}, { role: 'player' })

// ========== 组队邀请 ==========
export const inviteTeammate = (id, data) => post(`/player/order/${id}/invite-teammate`, data, { role: 'player' })
export const acceptInvite = (id) => post(`/player/order/${id}/accept-invite`, {}, { role: 'player' })
export const rejectInvite = (id) => post(`/player/order/${id}/reject-invite`, {}, { role: 'player' })
export const getInviteList = (params) => get('/player/order/invite-list', params, { role: 'player' })
export const getAvailableTeammates = (params, opts = {}) => get('/player/order/teammate/available', params, { role: 'player', ...opts })

// ========== 接单员工作 ==========
export const getMyWork = (params) => get('/player/work/my', params, { role: 'player' })
export const getAvailableWork = (params) => get('/player/work/available', params, { role: 'player' })
export const startWork = (orderId) => post(`/player/work/${orderId}/start`, {}, { role: 'player' })
export const submitWorkProgress = (orderId, data) => post(`/player/order/${orderId}/progress`, data, { role: 'player' })
export const completeWork = (orderId, data = {}) => post(`/player/work/${orderId}/complete`, data, { role: 'player' })

// ========== 收益 ==========
export const getEarningsSummary = () => get('/player/earnings/summary', {}, { role: 'player' })
export const getEarningsDetail = (params) => get('/player/earnings/detail', params, { role: 'player' })

// ========== 提现 ==========
export const getWithdrawWindow = () => get('/player/withdraw/window', {}, { role: 'player' })
export const applyWithdraw = (data) => post('/player/withdraw', data, { role: 'player' })
export const getWithdrawList = (params) => get('/player/withdraw', params, { role: 'player' })
export const getWithdrawDetail = (id) => get(`/player/withdraw/${id}`, {}, { role: 'player' })

// ========== 提现账户 ==========
export const getAccountList = () => get('/player/account', {}, { role: 'player' })
export const addAccount = (data) => post('/player/account', data, { role: 'player' })
export const updateAccount = (data) => put('/player/account', data, { role: 'player' })
export const deleteAccount = (id) => del(`/player/account/${id}`, {}, { role: 'player' })

// ========== 换人 ==========
export const replaceSelf = (orderId) => post(`/player/order/${orderId}/replace-self`, {}, { role: 'player' })
export const replaceAll = (orderId) => post(`/player/order/${orderId}/replace-all`, {}, { role: 'player' })
export const replaceTeammate = (orderId) => post(`/player/order/${orderId}/replace-teammate`, {}, { role: 'player' })