import { reactive } from 'vue'
import { useSiteStore } from '@/store/site'
import { ORDER_STATUS_TEXT } from '@/utils/constants'

/** 场景对应的弹窗标题 */
const SCENE_TITLE = {
  general: '联系客服',
  product: '咨询商品',
  complaint: '投诉反馈'
}

/**
 * 企微客服统一入口
 * 阶段一：二维码弹窗；绑定企微后 auto 模式优先 API 直跳
 */
export function useWeworkCs() {
  const siteStore = useSiteStore()

  const modalState = reactive({
    visible: false,
    title: '联系客服',
    qrcodeUrl: '',
    tips: '',
    copyText: ''
  })

  /** 生成复制到剪贴板的上下文文案 */
  function buildCopyText(scene, order, product) {
    if (scene === 'product' && product) {
      const price = Number(product.price || 0).toFixed(2)
      return `【商品咨询】${product.name} ¥${price}（ID:${product.id}）`
    }
    if (scene === 'complaint' && order) {
      const status = ORDER_STATUS_TEXT[order.status] || order.status || ''
      return `【投诉】订单号:${order.orderNo} 商品:${order.productName} 状态:${status}`
    }
    return ''
  }

  /** 绑定企微后，带入会话的小程序页面路径 */
  function buildMessagePath(scene, order, product) {
    if (scene === 'product' && product?.id) {
      return `/pages/product/detail?id=${product.id}`
    }
    if (scene === 'complaint' && order?.id) {
      return `/pages/order/detail?id=${order.id}`
    }
    return '/pages/index/index'
  }

  function showQrModal({ scene, order, product }) {
    modalState.title = SCENE_TITLE[scene] || '联系客服'
    modalState.qrcodeUrl = siteStore.csQrcodeUrl
    modalState.tips = siteStore.csContactTips
    modalState.copyText = buildCopyText(scene, order, product)
    modalState.visible = true
  }

  /** 打开企微客服（API 优先，失败或未配置时降级二维码） */
  async function openWeworkCs({ scene = 'general', order, product } = {}) {
    const { csContactMode, csCorpId, csServiceUrl, csQrcodeUrl } = siteStore
    const tryApi = (csContactMode === 'wework' || csContactMode === 'auto')
      && csCorpId && csServiceUrl

    // #ifdef MP-WEIXIN
    if (tryApi) {
      try {
        await new Promise((resolve, reject) => {
          const opts = {
            corpId: csCorpId,
            extInfo: { url: csServiceUrl },
            success: resolve,
            fail: reject
          }
          if (product || order) {
            opts.showMessageCard = true
            opts.sendMessageTitle = product?.name || `订单 ${order?.orderNo || ''}`
            opts.sendMessagePath = buildMessagePath(scene, order, product)
          }
          wx.openCustomerServiceChat(opts)
        })
        return
      } catch (e) {
        if (csContactMode === 'wework') {
          uni.showToast({ title: '打开客服失败，请稍后重试', icon: 'none' })
          return
        }
      }
    }
    // #endif

    if (!csQrcodeUrl) {
      uni.showToast({ title: '客服配置中，请稍后再试', icon: 'none' })
      return
    }
    showQrModal({ scene, order, product })
  }

  return { modalState, openWeworkCs }
}
