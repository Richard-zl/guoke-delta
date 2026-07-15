/** 是否为测试/预发环境（dev/staging 或 API/WS 指向 test 子域） */
export function isStagingEnv() {
  const flag = (import.meta.env.VITE_APP_ENV || '').toLowerCase()
  if (flag === 'staging' || flag === 'test') return true
  if (import.meta.env.MODE === 'staging') return true
  const api = import.meta.env.VITE_API_BASE_URL || ''
  const ws = import.meta.env.VITE_WS_BASE_URL || ''
  return /test\.guokegames\.online/i.test(api) || /test\.guokegames\.online/i.test(ws)
}

export const STAGING_LABEL = '测试环境'
export const STAGING_HINT = '测试环境·非正式数据'

/** 导航栏标题加测试前缀 */
export function stagingNavTitle(title) {
  const t = (title || '').trim()
  if (!t || t.startsWith('【测试】')) return t || '【测试】'
  return `【测试】${t}`
}

/** 测试环境导航栏配色 */
export function applyStagingNavStyle(title) {
  if (!isStagingEnv()) return
  uni.setNavigationBarColor({
    frontColor: '#ffffff',
    backgroundColor: '#b45309'
  })
  if (title) {
    uni.setNavigationBarTitle({ title: stagingNavTitle(title) })
  }
}
