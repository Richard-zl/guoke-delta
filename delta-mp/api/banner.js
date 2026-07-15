import { get } from './request'

/** 活动轮播图（公开接口，无需登录） */
export const getActiveBanners = (opts = {}) => get('/system/banner/active', {}, { auth: false, ...opts })
