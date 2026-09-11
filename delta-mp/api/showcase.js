import { get, put } from './request'

/** 已上墙风采列表（公开） */
export const getActiveShowcase = (params, options = {}) =>
  get('/player/showcase/active', params, { auth: false, ...options })

/** 风采详情（公开） */
export const getShowcaseDetail = (playerId) =>
  get(`/player/showcase/detail/${playerId}`, {}, { auth: false })

/** 风采详情评价（过滤隐藏项） */
export const getShowcaseReviews = (playerId, params) =>
  get(`/order/review/player/${playerId}`, { ...params, forShowcase: true }, { auth: false })

/** 打手自己的风采资料 */
export const getMyShowcase = () => get('/player/showcase/me', {}, { role: 'player' })

export const saveMyVoice = (data) => put('/player/showcase/me/voice', data, { role: 'player' })

export const saveMyGallery = (data) => put('/player/showcase/me/gallery', data, { role: 'player' })
