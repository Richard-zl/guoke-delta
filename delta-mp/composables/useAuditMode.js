import { computed } from 'vue'
import { useSiteStore } from '@/store/site'

export {
  AUDIT_BLOCKED_ROUTES,
  blockIfUnderReview,
  isAuditBlockedRoute,
  normalizeRoute,
  setupAuditRouteInterceptor,
  shouldBlockRouteNow
} from '@/composables/useAuditGuard'

/** 审核模式：当前小程序版本是否处于微信审核中 */
export function useAuditMode() {
  const siteStore = useSiteStore()
  return {
    isUnderReview: computed(() => siteStore.isUnderReview),
    auditVersion: computed(() => siteStore.auditVersion),
    configLoaded: computed(() => siteStore.configLoaded)
  }
}
