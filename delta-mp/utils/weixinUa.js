/**
 * H5 支付页 UA 拦截：非微信内打开一律全屏拦截（Spec 决议 #11），
 * 不请求后端、不消耗 payToken 校验次数。
 */
export function isWeixinBrowser() {
  if (typeof navigator === 'undefined' || !navigator.userAgent) return false
  return /MicroMessenger/i.test(navigator.userAgent)
}
