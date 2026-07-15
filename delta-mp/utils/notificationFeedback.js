/**
 * 全局收到消息时的提醒反馈：震动 + 提示音
 * 使用与 PC 端一致的三音阶提示音（见 static/sounds/notify.wav，由 scripts/generate-notify-wav.js 生成）
 */

/** 播放一次消息提醒（震动 + PC 端同款三音阶叮咚提示音） */
export function playMessageNotification() {
  // 1. 震动
  try {
    uni.vibrateShort({ type: 'medium' })
  } catch (e) {}

  // 2. 提示音（与 delta-admin-ui 客服端消息提示音一致）
  try {
    const ctx = uni.createInnerAudioContext()
    ctx.src = '/static/sounds/notify.wav'
    ctx.volume = 0.6
    ctx.autoplay = true
    ctx.obeyMuteSwitch = false // 静音开关关闭时也尽量震动/提醒
    ctx.onEnded(() => ctx.destroy())
    ctx.onError(() => ctx.destroy())
  } catch (e) {}
}
