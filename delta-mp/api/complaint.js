import { get, post } from './request'

/** 提交投诉 */
export const createComplaint = (data) => post('/order/complaint', data)

/** 投诉详情 */
export const getComplaintDetail = (id) => get(`/order/complaint/${id}`)

/** 申诉 */
export const appealComplaint = (id, data) => post(`/order/complaint/${id}/appeal`, data)

/** 我的投诉列表 */
export const getMyComplaints = (params) => get('/order/complaint/my', params)
