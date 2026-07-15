/**
 * 获取小程序 versionName（与 manifest.json 中 versionName 一致）
 * 优先运行时 API，回退编译期注入的 VITE_APP_VERSION
 */
export function getAppVersion() {
  try {
    if (typeof uni !== 'undefined' && uni.getAppBaseInfo) {
      const info = uni.getAppBaseInfo()
      if (info?.appVersion) return info.appVersion
    }
  } catch (e) {
    // 部分平台不支持 getAppBaseInfo，忽略
  }
  return import.meta.env.VITE_APP_VERSION || '1.0.0'
}
