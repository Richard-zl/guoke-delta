import { get, post, put, del } from '@/api/request'

const CS = { role: 'cs' }

// ========== 仪表盘 ==========
export const getDashboard = () => get('/cs/dashboard', {}, CS)
/** 提醒红点：投诉未读、消息未读、系统通知未读 */
export const getCsRemind = (opts = {}) => get('/cs/remind', {}, { ...CS, ...opts })
export const getMonitorRealtime = () => get('/cs/monitor/realtime', {}, CS)
export const getMonitorOverview = () => get('/cs/monitor/overview', {}, CS)

// ========== 订单管理 ==========
export const getCsOrderList = (params) => get('/cs/order/list', params, CS)
export const getCsOrderDetail = (id) => get(`/cs/order/${id}`, {}, CS)
export const assignOrder = (orderId, playerId) => post(`/cs/order/${orderId}/assign/${playerId}`, {}, CS)

// ========== 投诉管理 ==========
export const getCsComplaintList = (params) => get('/cs/complaint/list', params, CS)
export const processCsComplaint = (id, data) => put(`/cs/complaint/${id}/process`, data, CS)
export const resolveCsComplaint = (id, data) => put(`/cs/complaint/${id}/resolve`, data, CS)

// ========== 用户管理 ==========
export const getCsUserList = (params) => get('/cs/user/list', params, CS)
export const updateCsUserStatus = (id, data) => put(`/cs/user/${id}/status`, data, CS)

// ========== 接单员管理 ==========
export const getCsPlayerList = (params) => get('/cs/player/list', params, CS)
export const getCsPlayerAssignList = (params) => get('/cs/player/assign-list', params, CS)
export const auditCsPlayer = (id, data) => put(`/cs/player/${id}/audit`, data, CS)
export const freezeCsPlayer = (id, data = {}) => put(`/cs/player/${id}/freeze`, data, CS)

// ========== 提现审核 ==========
export const getCsWithdrawList = (params) => get('/cs/withdraw/list', params, CS)
export const approveCsWithdraw = (id) => put(`/cs/withdraw/${id}/approve`, {}, CS)
export const rejectCsWithdraw = (id, data) => put(`/cs/withdraw/${id}/reject`, data, CS)

// ========== 商品管理 ==========
export const getCsProductList = (params) => get('/cs/product/list', params, CS)
export const saveCsProduct = (data) => post('/cs/product', data, CS)

// ========== 分类管理 ==========
export const getCsCategoryList = (params) => get('/cs/category/list', params, CS)
export const saveCsCategory = (data) => post('/cs/category', data, CS)

// ========== 客服聊天 ==========
export const getCsChatSessions = (params) => get('/cs/chat/sessions', params, CS)
export const getCsChatMessages = (params) => get('/common/chat/message/list', params, CS)
export const csChatMarkRead = (sessionId) => post(`/common/chat/message/read?sessionId=${sessionId}`, {}, CS)

// ========== 详情接口 ==========
export const getCsComplaintDetail = (id) => get(`/cs/complaint/${id}`, {}, CS)
export const getCsPlayerDetail = (id) => get(`/cs/player/${id}`, {}, CS)
export const getCsWithdrawDetail = (id) => get(`/cs/withdraw/${id}`, {}, CS)
export const getCsProductDetail = (id) => get(`/cs/product/${id}`, {}, CS)

// ========== 删除分类 ==========
export const deleteCsCategory = (id) => del(`/cs/category/${id}`, {}, CS)

// ========== 快捷回复 ==========
export const getQuickReplyList = (params) => get('/system/quick-reply/list', params, CS)
export const getActiveQuickReplies = (params) => get('/system/quick-reply/active', params, CS)
export const addQuickReply = (data) => post('/system/quick-reply', data, CS)
export const updateQuickReply = (data) => put('/system/quick-reply', data, CS)
export const deleteQuickReply = (id) => del(`/system/quick-reply/${id}`, {}, CS)

