/**
 * 打手语音介绍的播放上下文。
 */

/**
 * 让音频播放不受 iOS 侧边静音开关影响。
 *
 * InnerAudioContext 实例上的 obeyMuteSwitch 从基础库 2.3.0 起已不生效，
 * 只有全局的 setInnerAudioOption 才对 iOS 生效，且必须在播放前设置好，
 * 否则会出现「播放状态正常、onPlay 已触发、却听不到声音」。
 * 该设置对当前小程序全局生效，iOS 回到前台后可能被重置，需要重新设置。
 */
export function bypassMuteSwitch() {
  try {
    uni.setInnerAudioOption({ obeyMuteSwitch: false })
  } catch (e) {}
}

/** 创建语音播放上下文。 */
export function createVoiceAudio(src) {
  bypassMuteSwitch()
  const ctx = uni.createInnerAudioContext()
  ctx.volume = 1
  ctx.src = src
  return ctx
}
