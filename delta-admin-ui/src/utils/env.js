/** 是否为测试/预发环境（dev/staging 构建或 API/WS 指向 test 域名） */
export function isStagingEnv() {
  const flag = (import.meta.env.VITE_APP_ENV || '').toLowerCase()
  if (flag === 'staging' || flag === 'test') return true
  if (import.meta.env.MODE === 'staging') return true
  const api = import.meta.env.VITE_API_BASE_URL || ''
  const ws = import.meta.env.VITE_WS_BASE_URL || ''
  return /test\.guokegames\.online/i.test(api) || /test\.guokegames\.online/i.test(ws)
}

export const STAGING_LABEL = '测试环境'
export const STAGING_HINT = '当前为测试环境，数据与正式站隔离，请勿用于真实交易'
