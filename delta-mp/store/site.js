import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getSiteConfig } from '@/api/config'
import { getAppVersion } from '@/utils/version'

export const useSiteStore = defineStore('site', () => {
  const siteName = ref('三角洲')
  const subtitle = ref('专业游戏平台')
  const logo = ref('')
  const adminTitle = ref('三角洲管理后台')
  /** 接单员端抽佣比例（百分比，如 10 表示 10%），由接口 /system/config/site 返回 player_commission_rate */
  const playerCommissionRate = ref(0)
  /** 打手入驻押金开关 */
  const depositRequired = ref(true)
  /** 后台配置的审核版本号 */
  const auditVersion = ref('')
  /** 当前小程序是否处于审核中（本地 versionName 与 audit_version 一致） */
  const isUnderReview = ref(false)
  /** 站点配置是否已拉取完成（审核页需等待后再渲染） */
  const configLoaded = ref(false)
  /** 客服联系方式：qrcode / wework / auto */
  const csContactMode = ref('qrcode')
  const csCorpId = ref('')
  const csServiceUrl = ref('')
  const csQrcodeUrl = ref('')
  const csContactTips = ref('长按识别二维码，添加客服微信')

  /** 完整平台名称（用于协议等文案） */
  const fullName = computed(() => siteName.value + '平台')

  async function fetchSiteConfig() {
    try {
      const version = getAppVersion()
      const res = await getSiteConfig(version)
      const data = res.data || {}
      if (data.site_name) siteName.value = data.site_name
      if (data.site_subtitle) subtitle.value = data.site_subtitle
      if (data.site_logo) logo.value = data.site_logo
      if (data.site_admin_title) adminTitle.value = data.site_admin_title
      if (data.player_commission_rate !== undefined && data.player_commission_rate !== null) {
        playerCommissionRate.value = Number(data.player_commission_rate) || 0
      }
      if (data.player_deposit_required !== undefined) {
        depositRequired.value = data.player_deposit_required === 'true'
      }
      auditVersion.value = data.audit_version || ''
      isUnderReview.value = data.is_under_review === 'true'
      if (data.cs_contact_mode) csContactMode.value = data.cs_contact_mode
      if (data.cs_corp_id) csCorpId.value = data.cs_corp_id
      if (data.cs_service_url) csServiceUrl.value = data.cs_service_url
      if (data.cs_qrcode_url) csQrcodeUrl.value = data.cs_qrcode_url
      if (data.cs_contact_tips) csContactTips.value = data.cs_contact_tips
    } catch (e) {
      console.warn('[SiteStore] fetch site config failed', e)
    } finally {
      configLoaded.value = true
    }
  }

  return {
    siteName,
    subtitle,
    logo,
    adminTitle,
    playerCommissionRate,
    depositRequired,
    auditVersion,
    isUnderReview,
    configLoaded,
    csContactMode,
    csCorpId,
    csServiceUrl,
    csQrcodeUrl,
    csContactTips,
    fullName,
    fetchSiteConfig
  }
})
