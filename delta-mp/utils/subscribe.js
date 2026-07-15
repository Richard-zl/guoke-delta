/**
 * 微信订阅消息工具
 * 封装 uni.requestSubscribeMessage，在关键业务操作前调用
 *
 * 使用方式：
 *   import { requestOrderSubscribe, requestIncomeSubscribe } from '@/utils/subscribe'
 *   await requestOrderSubscribe()  // 不会阻断主流程，用户拒绝也会resolve
 */

// ========== 模板ID配置（需替换为微信小程序后台申请到的实际ID） ==========
const TEMPLATE = {
  /** 订单状态变更通知（phrase4 订单状态、character_string1 订单单号、thing2 订单类型、amount3 订单金额） */
  ORDER_STATUS: 'sUrc4cGcXvz5Mi59q3b0Jp8jmNt9CGWd2_-pc0jiwGs',
  /** 收入到账通知 */
  INCOME: 'YOUR_TEMPLATE_ID_INCOME',
  /** 组队邀请通知 */
  TEAM_INVITE: 'YOUR_TEMPLATE_ID_TEAM_INVITE',
  /** 订单确认通知 */
  ORDER_CONFIRM: 'YOUR_TEMPLATE_ID_ORDER_CONFIRM'
}

/**
 * 核心请求函数 — 调用微信订阅消息授权弹窗
 * 注意：开发者工具中可能不弹窗或行为与真机不一致，请用「真机调试」验证；若用户曾拒绝过，需在小程序设置里重新开启订阅。
 * @param {string[]} tmplIds 模板ID数组（最多3个）
 * @returns {Promise<Object>} 授权结果，失败也resolve不抛异常
 */
function requestSubscribe(tmplIds) {
  // 过滤掉未配置的占位模板
  const validIds = tmplIds.filter(id => id && !id.startsWith('YOUR_TEMPLATE_ID'))
  if (validIds.length === 0) {
    console.log('[Subscribe] 无有效模板ID，跳过订阅请求')
    return Promise.resolve({})
  }

  return new Promise((resolve) => {
    // #ifdef MP-WEIXIN
    console.log('[Subscribe] 请求订阅消息, tmplIds:', validIds)
    uni.requestSubscribeMessage({
      tmplIds: validIds,
      success(res) {
        console.log('[Subscribe] 授权结果:', res)
        resolve(res)
      },
      fail(err) {
        // 用户拒绝或其他原因，不阻断主流程
        console.log('[Subscribe] 授权失败/拒绝:', err)
        // errCode 20001 表示模板不存在：请到微信公众平台-订阅消息-我的模板 添加该模板
        if (err && err.errCode === 20001) {
          console.warn('[Subscribe] 模板ID未在公众平台配置，请登录 mp.weixin.qq.com -> 订阅消息 -> 公共模板库 添加「订单状态」类模板并填入模板ID')
        }
        resolve({})
      },
      complete() {
        // 开发者工具中经常不弹窗，提示用真机验证
        try {
          const sys = typeof uni.getSystemInfoSync === 'function' ? uni.getSystemInfoSync() : {}
          if (sys.platform === 'devtools') {
            console.warn('[Subscribe] 当前为开发者工具，订阅弹窗可能不显示，请使用微信「真机调试」验证')
          }
        } catch (e) {}
      }
    })
    // #endif

    // 非微信小程序环境直接跳过
    // #ifndef MP-WEIXIN
    resolve({})
    // #endif
  })
}

/**
 * 请求订单相关订阅
 * - 支付前调用：用于「被接单了」提醒（第一次）
 * - 确认完成前调用：用于「服务已完成」提醒（第二次）
 * 一次最多请求3个模板
 */
export function requestOrderSubscribe() {
  return requestSubscribe([
    TEMPLATE.ORDER_STATUS,
    TEMPLATE.ORDER_CONFIRM
  ])
}

/**
 * 请求接单员收入相关订阅（接单前调用）
 */
export function requestPlayerSubscribe() {
  return requestSubscribe([
    TEMPLATE.ORDER_STATUS,
    TEMPLATE.INCOME,
    TEMPLATE.ORDER_CONFIRM
  ])
}

/**
 * 请求组队邀请订阅
 */
export function requestTeamSubscribe() {
  return requestSubscribe([
    TEMPLATE.TEAM_INVITE
  ])
}

/**
 * 请求收入/提现相关订阅
 */
export function requestIncomeSubscribe() {
  return requestSubscribe([
    TEMPLATE.INCOME
  ])
}

/** 导出模板ID常量，方便外部引用 */
export { TEMPLATE }
