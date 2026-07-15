import { get, post } from './request'

/** 系统通知列表 */
export const getMessageList = (params) => get('/message/list', params)

/** 系统通知未读数量 */
export const getUnreadCount = (type, options = {}) => get('/message/unread-count', { type }, options)

/** 提醒汇总：系统通知未读 + 聊天消息未读（分离展示） */
export const getRemind = (options = {}) => get('/message/remind', {}, options)

/** 标记单条已读 */
export const markRead = (id) => post(`/message/read/${id}`)

/** 全部已读（使用 query 传参避免 body 反序列化问题） */
export const markAllRead = (userType) => post(`/message/read-all?userType=${encodeURIComponent(userType)}`, {})

/** 上报订阅消息授权状态 */
export const reportSubscribe = (templateId, status) => post('/message/subscribe/report', { templateId, status })
