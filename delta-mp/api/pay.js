import { get, post } from './request'

/** 微信支付（返回wx.requestPayment参数） */
export const createPayment = (orderId, couponId) => post(`/pay/wx/${orderId}`, { couponId })

/** 打手入驻押金支付(100元)，返回paymentNo及wx.requestPayment参数 */
export const createPlayerDeposit = () => post('/pay/player-deposit')

/** 余额支付 */
export const balancePay = (orderId, couponId) => post(`/pay/balance/${orderId}`, { couponId })

/** 交易流水 */
export const getTransactions = (params) => get('/pay/transactions', params)

/** 签发客服会话 H5 支付用的短时效 payToken，供「联系客服支付」拉起企微客服会话携带 */
export const getPayKfToken = (orderId) => get(`/pay/kf/token/${orderId}`)

// ---- H5 独立支付页接口（均为公开接口 auth:false，凭 payToken 鉴权，不依赖登录态） ----

/** 服务号网页授权 code 换 openid */
export const h5Oauth = (code) => post('/pay/h5/oauth', { code }, { auth: false })

/** 只读查单（订单信息 + 已绑定优惠券，不支持改券） */
export const h5Order = (token) => get('/pay/h5/order', { token }, { auth: false })

/** 服务号 JSAPI 预下单，返回 chooseWXPay 所需参数 */
export const h5Prepay = (token, openid) => post('/pay/h5/prepay', { token, openid }, { auth: false })

/** JSSDK 签名（wx.config），url 为当前页面完整地址（不含 # 及其后内容） */
export const h5Jsconfig = (url) => get('/pay/h5/jsconfig', { url }, { auth: false })

/** 服务号 appid，用于拼接网页授权（snsapi_base）跳转链接 */
export const h5MpAppId = () => get('/pay/h5/mp-appid', {}, { auth: false })
