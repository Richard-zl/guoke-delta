import { computed } from 'vue'
import { useSiteStore } from '@/store/site'

/** 站点品牌文案（俱乐部 ID 标签等） */
export function useSiteBranding() {
  const siteStore = useSiteStore()

  const clubIdLabel = computed(() => `${siteStore.siteName}ID`)
  const clubIdPlaceholder = computed(() => `如：${siteStore.siteName}xxx`)

  return { siteStore, clubIdLabel, clubIdPlaceholder }
}
