import request from '@/utils/request'

// ==================== 用户管理 ====================
export function adminUserList(params) {
  return request({ url: '/admin/user/list', method: 'get', params })
}
export function adminUserDetail(id) {
  return request({ url: `/admin/user/${id}`, method: 'get' })
}
export function adminUserUpdateStatus(id, status) {
  return request({ url: `/admin/user/${id}/status`, method: 'put', params: { status } })
}
export function adminUserAdjustBalance(id, data) {
  return request({ url: `/admin/user/${id}/balance`, method: 'put', data })
}
export function adminUserAdjustPoints(id, params) {
  return request({ url: `/admin/user/${id}/points`, method: 'post', params })
}
export function csUserList(params) {
  return request({ url: '/cs/user/list', method: 'get', params })
}
export function csUserDetail(id) {
  return request({ url: `/cs/user/${id}`, method: 'get' })
}
export function csUserUpdateStatus(id, status) {
  return request({ url: `/cs/user/${id}/status`, method: 'put', data: { status } })
}

// ==================== 优惠券管理 ====================
export function adminCouponList() {
  return request({ url: '/admin/coupon/list', method: 'get' })
}
export function adminUserCouponList(userId, params) {
  return request({ url: `/admin/user/${userId}/coupons`, method: 'get', params })
}
export function adminUserGrantCoupon(userId, params) {
  return request({ url: `/admin/user/${userId}/coupon`, method: 'post', params })
}
export function adminUserRevokeCoupon(userId, userCouponId) {
  return request({ url: `/admin/user/${userId}/coupons/${userCouponId}/revoke`, method: 'put' })
}
export function csUserCouponList(userId, params) {
  return request({ url: `/cs/user/${userId}/coupons`, method: 'get', params })
}

// ==================== 打手管理 ====================
export function adminPlayerList(params) {
  return request({ url: '/admin/player/list', method: 'get', params })
}
export function adminPlayerDetail(id) {
  return request({ url: `/admin/player/${id}`, method: 'get' })
}
export function adminPlayerTransactions(id, params) {
  return request({ url: `/admin/player/${id}/transactions`, method: 'get', params })
}
export function adminPlayerApprove(id) {
  return request({ url: `/admin/player/${id}/approve`, method: 'put' })
}
export function adminPlayerReject(id, reason) {
  return request({ url: `/admin/player/${id}/reject`, method: 'put', params: { reason } })
}
export function adminPlayerUpdateStatus(id, status) {
  return request({ url: `/admin/player/${id}/status`, method: 'put', params: { status } })
}
export function adminPlayerFreeze(id, data) {
  return request({ url: `/admin/player/${id}/freeze`, method: 'put', data })
}
export function adminPlayerUnfreeze(id) {
  return request({ url: `/admin/player/${id}/unfreeze`, method: 'put' })
}
export function adminPlayerUpdate(id, data) {
  return request({ url: `/admin/player/${id}`, method: 'put', data })
}
export function csPlayerList(params) {
  return request({ url: '/cs/player/list', method: 'get', params })
}
/** 指派订单专用打手列表（admin/cs 均可用，含进行中订单数） */
export function playerAssignList(params) {
  return request({ url: '/cs/player/assign-list', method: 'get', params })
}
export function csPlayerDetail(id) {
  return request({ url: `/cs/player/${id}`, method: 'get' })
}
export function csPlayerTransactions(id, params) {
  return request({ url: `/cs/player/${id}/transactions`, method: 'get', params })
}
export function csPlayerAudit(id, status, rejectReason) {
  return request({ url: `/cs/player/${id}/audit`, method: 'put', data: { status, rejectReason } })
}
export function csPlayerFreeze(id) {
  return request({ url: `/cs/player/${id}/freeze`, method: 'put' })
}
export function csPlayerUpdateNickname(id, nickname) {
  return request({ url: `/cs/player/${id}/nickname`, method: 'put', data: { nickname } })
}

// ==================== 订单管理 ====================
export function adminOrderList(params) {
  return request({ url: '/admin/order/list', method: 'get', params })
}
export function adminOrderDetail(id) {
  return request({ url: `/admin/order/${id}`, method: 'get' })
}
// admin 端指派订单（支持两个打手）
export function adminOrderAssign(orderId, playerId, playerId2) {
  let url = `/admin/order/${orderId}/assign/${playerId}`
  if (playerId2) {
    url = `/admin/order/${orderId}/assign/${playerId}?playerId2=${playerId2}`
  }
  return request({ url, method: 'post' })
}
// cs 端指派订单（支持两个打手）
export function csOrderAssign(orderId, playerId, playerId2) {
  let url = `/cs/order/${orderId}/assign/${playerId}`
  if (playerId2) {
    url = `/cs/order/${orderId}/assign/${playerId}?playerId2=${playerId2}`
  }
  return request({ url, method: 'post' })
}
export function adminOrderRefund(orderId) {
  return request({ url: `/admin/order/${orderId}/refund`, method: 'post' })
}
export function adminOrderConfirm(orderId) {
  return request({ url: `/admin/order/${orderId}/confirm`, method: 'post' })
}
export function csOrderRefund(orderId) {
  return request({ url: `/cs/order/${orderId}/refund`, method: 'post' })
}
export function csOrderConfirm(orderId) {
  return request({ url: `/cs/order/${orderId}/confirm`, method: 'post' })
}
export function csOrderList(params) {
  return request({ url: '/cs/order/list', method: 'get', params })
}

// ==================== 投诉管理 ====================
export function csComplaintList(params) {
  return request({ url: '/cs/complaint/list', method: 'get', params })
}
export function csComplaintProcess(id, remark) {
  return request({ url: `/cs/complaint/${id}/process`, method: 'put', data: { remark } })
}
export function csComplaintResolve(id, data) {
  return request({ url: `/cs/complaint/${id}/resolve`, method: 'put', data })
}
export function csComplaintCreate(data) {
  return request({ url: '/cs/complaint/create', method: 'post', data })
}

// 客服端投诉详情
export function csComplaintDetail(id) {
  return request({ url: `/cs/complaint/${id}`, method: 'get' })
}

// ==================== 提现管理 ====================
export function adminWithdrawList(params) {
  return request({ url: '/admin/withdraw/list', method: 'get', params })
}
export function adminWithdrawProcess(id, data) {
  return request({ url: `/admin/withdraw/${id}/process`, method: 'put', data })
}
export function adminWithdrawReject(id, reason) {
  return request({ url: `/admin/withdraw/${id}/reject`, method: 'put', params: { reason } })
}
export function csWithdrawList(params) {
  return request({ url: '/cs/withdraw/list', method: 'get', params })
}
export function csWithdrawApprove(id, data) {
  return request({ url: `/cs/withdraw/${id}/approve`, method: 'put', data })
}
export function csWithdrawReject(id, reason) {
  return request({ url: `/cs/withdraw/${id}/reject`, method: 'put', data: { reason } })
}

// ==================== 聊天记录管理 ====================
export function adminChatSessionList(params) {
  return request({ url: '/admin/chat/session/list', method: 'get', params })
}
export function adminChatMessageList(params) {
  return request({ url: '/admin/chat/message/list', method: 'get', params })
}

// ==================== 订单详情 / 进度 ====================
export function csOrderDetail(id) {
  return request({ url: `/cs/order/${id}`, method: 'get' })
}
export function csOrderProgress(id) {
  return request({ url: `/cs/order/${id}/progress`, method: 'get' })
}
export function adminOrderProgress(id) {
  return request({ url: `/admin/order/${id}/progress`, method: 'get' })
}
export function csChatSessionAllList(params) {
  return request({ url: '/cs/chat/session/list', method: 'get', params })
}
export function csChatMessageList(params) {
  return request({ url: '/cs/chat/message/list', method: 'get', params })
}

// ==================== 快捷回复 ====================
export function getActiveQuickReplies(category) {
  return request({ url: '/system/quick-reply/active', method: 'get', params: { category } })
}

// ==================== 客服实时聊天 ====================
export function csChatSessionList(params) {
  return request({ url: '/cs/chat/sessions', method: 'get', params })
}
export function chatMessageList(params) {
  return request({ url: '/common/chat/message/list', method: 'get', params })
}
export function chatSendMessage(data) {
  return request({ url: '/common/chat/message/send', method: 'post', data })
}
export function chatMarkRead(sessionId) {
  return request({ url: '/common/chat/message/read', method: 'post', params: { sessionId } })
}

// ==================== 换人申请 ====================
export function csReplaceList(params) {
  return request({ url: '/cs/replace/list', method: 'get', params })
}
export function csReplaceApprove(id, remark) {
  return request({ url: `/cs/replace/${id}/approve`, method: 'put', data: { remark } })
}
export function csReplaceReject(id, remark) {
  return request({ url: `/cs/replace/${id}/reject`, method: 'put', data: { remark } })
}

// ==================== 接力申请 ====================
export function csRelayList(params) {
  return request({ url: '/cs/relay/list', method: 'get', params })
}
export function csRelayApprove(id, newPlayerId) {
  return request({ url: `/cs/relay/${id}/approve`, method: 'post', data: { newPlayerId } })
}
export function csRelayReject(id, reason) {
  return request({ url: `/cs/relay/${id}/reject`, method: 'post', data: reason ? { reason } : {} })
}

// ==================== 文件上传 ====================
export function uploadFile(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request({ url: '/common/file/upload', method: 'post', data: formData, headers: { 'Content-Type': 'multipart/form-data' } })
}