import { get, post } from './request'

/** 微信支付（返回wx.requestPayment参数） */
export const createPayment = (orderId, couponId) => post(`/pay/wx/${orderId}`, { couponId })

/** 打手入驻押金支付(100元)，返回paymentNo及wx.requestPayment参数 */
export const createPlayerDeposit = () => post('/pay/player-deposit')

/** 余额支付 */
export const balancePay = (orderId, couponId) => post(`/pay/balance/${orderId}`, { couponId })

/** 交易流水 */
export const getTransactions = (params) => get('/pay/transactions', params)