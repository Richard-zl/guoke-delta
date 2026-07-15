import { get, post } from './request'

/** 创建订单 */
export const createOrder = (data) => post('/order', data)

/** 订单列表 */
export const getOrderList = (params, opts = {}) => get('/order/my', params, opts)

/** 订单详情 */
export const getOrderDetail = (id) => get(`/order/${id}`)

/** 订单进度 */
export const getOrderProgress = (id) => get(`/order/${id}/progress`)

/** 确认完成 */
export const confirmOrder = (id) => post(`/order/${id}/confirm`)

/** 取消订单 */
export const cancelOrder = (id) => post(`/order/${id}/cancel`)

/** 申请换人 */
export const requestReplace = (id, data) => post(`/order/${id}/replace`, data)

/** 可用接单员列表（复用 cs/player/assign-list 接口，返回结构：{ players: { records }, maxConcurrent }） */
export const getAvailablePlayers = (params) => get('/cs/player/assign-list', params)

/** 指定接单员 */
export const designatePlayer = (id, playerId) => post(`/order/${id}/designate-player`, { playerId })