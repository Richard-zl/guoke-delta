import { get } from './request'

/** 获取站点配置（公开接口，无需登录） */
export function getSiteConfig(version) {
  return get('/system/config/site', version ? { version } : {}, { auth: false, loading: false })
}
