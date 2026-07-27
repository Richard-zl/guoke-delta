import { reactive } from 'vue'
import { useSiteStore } from '@/store/site'
import { ORDER_STATUS_TEXT } from '@/utils/constants'

/** 场景对应的弹窗标题 */
const SCENE_TITLE = {
  general: '联系客服',
  product: '咨询商品',
  complaint: '投诉反馈',
  pay: '订单支付'
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

  /**
   * 打开企微客服（API 优先，失败或未配置时降级二维码）。
   * `scene: 'pay'`（订单支付引导至客服）时禁止二维码降级：二维码无法透传 scene_param，
   * 机器人侧收不到 payToken 就发不出支付链接，因此唤起失败/未配置 API 时只能提示重试。
   * `payToken` 会以 `scene_param` 追加到 `csServiceUrl`，供客服回调侧识别订单。
   */
  /**
   * @param {string} [serviceUrl] 支付场景优先用后端 /pay/kf/token 返回的带 enc_scene 链接；
   *   后台手工配置的 cs_service_url 往往不支持 scene_param 回传。
   */
  async function openWeworkCs({ scene = 'general', order, product, payToken, serviceUrl: apiServiceUrl } = {}) {
    const { csContactMode, csCorpId, csServiceUrl, csQrcodeUrl } = siteStore
    let serviceUrl = apiServiceUrl || csServiceUrl || ''
    const tryApi = (csContactMode === 'wework' || csContactMode === 'auto')
      && csCorpId && serviceUrl

    // 支付必须用后端 add_contact_way 返回的带 enc_scene 链接，否则 scene_param 不会回传
    if (scene === 'pay') {
      if (!apiServiceUrl) {
        console.warn('[WeworkCs] pay 场景缺少后端 serviceUrl')
        uni.showToast({ title: '支付客服链接无效，请更新后重试', icon: 'none' })
        return
      }
      if (!/[?&]enc_scene=/.test(apiServiceUrl) && !/[?&]encScene=/.test(apiServiceUrl)) {
        console.warn('[WeworkCs] pay serviceUrl 无 enc_scene', apiServiceUrl.slice(0, 80))
        uni.showToast({ title: '支付客服链接无效(无enc_scene)', icon: 'none' })
        return
      }
    }

    // 后端已拼好 scene_param 则不再追加；否则兼容旧逻辑在本地拼接（可能不回传）
    if (payToken && serviceUrl && !/[?&]scene_param=/.test(serviceUrl)) {
      // 企微要求 scene_param URLEncode 后 ≤128；超长会导致 openCustomerServiceChat 直接 fail
      const encoded = encodeURIComponent(payToken)
      if (encoded.length > 128) {
        console.warn('[WeworkCs] scene_param 超长', encoded.length)
        if (scene === 'pay') {
          uni.showToast({ title: '支付凭证过长，请重试', icon: 'none' })
          return
        }
      } else {
        const sep = serviceUrl.includes('?') ? '&' : '?'
        serviceUrl = `${serviceUrl}${sep}scene_param=${encoded}`
      }
    }

    // #ifdef MP-WEIXIN
    if (tryApi) {
      try {
        await new Promise((resolve, reject) => {
          const opts = {
            corpId: csCorpId,
            extInfo: { url: serviceUrl },
            success: resolve,
            fail: reject
          }
          // 支付场景不加消息卡片，与首页「联系客服」一致，减少 API 失败概率
          if (scene !== 'pay' && (product || order)) {
            opts.showMessageCard = true
            opts.sendMessageTitle = product?.name || `订单 ${order?.orderNo || ''}`
            opts.sendMessagePath = buildMessagePath(scene, order, product)
          }
          console.log('[WeworkCs] openCustomerServiceChat', {
            scene,
            corpId: csCorpId,
            urlLen: serviceUrl.length,
            hasEncScene: /[?&]enc_scene=/.test(serviceUrl) || /[?&]encScene=/.test(serviceUrl),
            hasSceneParam: /[?&]scene_param=/.test(serviceUrl),
            urlPreview: serviceUrl.replace(/scene_param=[^&]*/, 'scene_param=***')
          })
          wx.openCustomerServiceChat(opts)
        })
        // 进线偶发 state=4 无法秒回：提示用户发一句即可触发补发
        if (scene === 'pay') {
          uni.showToast({
            title: '正在为您发送支付链接；若未出现，请发送任意消息',
            icon: 'none',
            duration: 3000
          })
        }
        return
      } catch (e) {
        const errMsg = e?.errMsg || e?.message || String(e)
        console.warn('[WeworkCs] openCustomerServiceChat fail', {
          scene, corpId: csCorpId, mode: csContactMode, errMsg, err: e
        })
        if (scene === 'pay') {
          uni.showToast({
            title: errMsg.includes('cancel') ? '已取消' : '打开客服失败，请真机重试',
            icon: 'none'
          })
          return
        }
        if (csContactMode === 'wework') {
          uni.showToast({ title: '打开客服失败，请稍后重试', icon: 'none' })
          return
        }
      }
    } else if (scene === 'pay') {
      console.warn('[WeworkCs] pay 场景未配置企微API', {
        csContactMode, hasCorpId: !!csCorpId, hasServiceUrl: !!csServiceUrl
      })
      uni.showToast({ title: '请先在后台配置企微客服', icon: 'none' })
      return
    }
    // #endif

    if (scene === 'pay') {
      uni.showToast({ title: '客服暂时不可用，请稍后重试', icon: 'none' })
      return
    }

    if (!csQrcodeUrl) {
      uni.showToast({ title: '客服配置中，请稍后再试', icon: 'none' })
      return
    }
    showQrModal({ scene, order, product })
  }

  return { modalState, openWeworkCs }
}
