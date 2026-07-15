import request from '@/utils/request'

// ==================== 管理员管理 ====================
export function getAdminList(params) {
  return request({ url: '/system/admin/list', method: 'get', params })
}
export function getAdminById(id) {
  return request({ url: `/system/admin/${id}`, method: 'get' })
}
export function addAdmin(data) {
  return request({ url: '/system/admin', method: 'post', data })
}
export function updateAdmin(data) {
  return request({ url: '/system/admin', method: 'put', data })
}
export function deleteAdmin(id) {
  return request({ url: `/system/admin/${id}`, method: 'delete' })
}

// ==================== 快捷回复管理 ====================
export function getQuickReplyList(params) {
  return request({ url: '/system/quick-reply/list', method: 'get', params })
}
export function addQuickReply(data) {
  return request({ url: '/system/quick-reply', method: 'post', data })
}
export function updateQuickReply(data) {
  return request({ url: '/system/quick-reply', method: 'put', data })
}
export function deleteQuickReply(id) {
  return request({ url: `/system/quick-reply/${id}`, method: 'delete' })
}

// ==================== 公告管理 ====================
export function getNoticeList(params) {
  return request({ url: '/system/notice/list', method: 'get', params })
}
export function addNotice(data) {
  return request({ url: '/system/notice', method: 'post', data })
}
export function updateNotice(data) {
  return request({ url: '/system/notice', method: 'put', data })
}
export function deleteNotice(id) {
  return request({ url: `/system/notice/${id}`, method: 'delete' })
}

// ==================== 轮播图管理 ====================
export function getBannerList(params) {
  return request({ url: '/system/banner/list', method: 'get', params })
}
export function addBanner(data) {
  return request({ url: '/system/banner', method: 'post', data })
}
export function updateBanner(data) {
  return request({ url: '/system/banner', method: 'put', data })
}
export function deleteBanner(id) {
  return request({ url: `/system/banner/${id}`, method: 'delete' })
}

// ==================== 系统配置 ====================
export function getSysConfigList() {
  return request({ url: '/system/config/list', method: 'get' })
}
export function batchUpdateSysConfig(data) {
  return request({ url: '/system/config/batch', method: 'put', data })
}

// ==================== 操作日志 ====================
export function getOperationLogList(params) {
  return request({ url: '/system/operation-log/list', method: 'get', params })
}

// ==================== 公告推送 ====================
export function pushNotice(id) {
  return request({ url: `/system/notice/${id}/push`, method: 'post' })
}
