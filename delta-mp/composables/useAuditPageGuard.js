import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { useSiteStore } from '@/store/site'

/** 审核期默认拦截，待配置拉取后再决定是否展示真实页面 */
export function useAuditPageGuard(options = {}) {
  const {
    placeholderTitle = '功能筹备中',
    navTitle = '功能筹备中'
  } = options

  const pageBlocked = ref(true)

  async function syncAuditState() {
    const siteStore = useSiteStore()
    if (!siteStore.configLoaded) {
      await siteStore.fetchSiteConfig()
    }
    pageBlocked.value = siteStore.isUnderReview
    if (pageBlocked.value && navTitle) {
      uni.setNavigationBarTitle({ title: navTitle })
    }
  }

  onLoad(syncAuditState)
  onShow(syncAuditState)

  return { pageBlocked, placeholderTitle }
}
