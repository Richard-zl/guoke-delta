import { get, post } from './request'

/** 提交评价 */
export const addReview = (data) => post('/order/review', data)

/** 按商品查评价（公开接口，无需登录） */
export const getReviewsByProduct = (productId, params) => get(`/order/review/product/${productId}`, params, { auth: false })

/** 按接单员查评价 */
export const getReviewsByPlayer = (playerId, params) => get(`/order/review/player/${playerId}`, params)

/** 我的评价列表（接单员端用） */
export const getMyReviews = (params) => get('/order/review/my', params)