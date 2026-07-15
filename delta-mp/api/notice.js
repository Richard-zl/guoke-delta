import { get } from './request'

/** 活动公告（公开接口，无需登录） */
export const getActiveNotices = (opts = {}) => get('/system/notice/active', {}, { auth: false, ...opts })
