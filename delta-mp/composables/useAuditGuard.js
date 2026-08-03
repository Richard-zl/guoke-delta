import { useSiteStore } from '@/store/site'

/** 审核期需拦截的交易/代练相关页面（支持前缀匹配，以 / 结尾） */
export const AUDIT_BLOCKED_ROUTES = [
  '/pages/product/list',
  '/pages/product/detail',
  '/pages/order/create',
  '/pages/order/pay',
  '/pages/order/list',
  '/pages/order/detail',
  '/pages/chat/list',
  '/pages/chat/room',
  '/pages/review/create',
  '/pages/wallet/index',
  '/pages/mine/coupon-list',
  '/pages-player/',
  '/pages-cs/'
]

/** 解析路由路径（去掉 query） */
export function normalizeRoute(url = '') {
  const path = String(url).split('?')[0]
  return path.startsWith('/') ? path : `/${path}`
}

/** 当前 URL 是否属于审核期应拦截的页面 */
export function isAuditBlockedRoute(url) {
  const path = normalizeRoute(url)
  return AUDIT_BLOCKED_ROUTES.some((route) => {
    if (route.endsWith('/')) return path.startsWith(route)
    return path === route
  })
}

/**
 * 审核期拦截页面访问，返回 true 表示已拦截并跳转首页
 * @param {{ redirectUrl?: string }} options
 */
export async function blockIfUnderReview(options = {}) {
  const { redirectUrl = '/pages/index/index' } = options
  const siteStore = useSiteStore()
  if (!siteStore.configLoaded) {
    await siteStore.fetchSiteConfig()
  }
  if (!siteStore.isUnderReview) return false
  uni.switchTab({ url: redirectUrl })
  return true
}

/** 同步判断：配置已加载且处于审核期时，是否应拦截该路由 */
export function shouldBlockRouteNow(url) {
  const siteStore = useSiteStore()
  if (!siteStore.configLoaded || !siteStore.isUnderReview) return false
  return isAuditBlockedRoute(url)
}

/** 注册路由拦截器，防止组件内 navigateTo 进入交易页 */
export function setupAuditRouteInterceptor() {
  const intercept = (args) => {
    if (shouldBlockRouteNow(args?.url)) {
      uni.switchTab({ url: '/pages/index/index' })
      return false
    }
  }
  // switchTab 一并拦截，防止审核期切到消息/订单等 Tab 页
  ;['navigateTo', 'redirectTo', 'reLaunch', 'switchTab'].forEach((method) => {
    uni.addInterceptor(method, { invoke: intercept })
  })
}
