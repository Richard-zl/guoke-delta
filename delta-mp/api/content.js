import { get } from './request'

/** 获取内容配置 */
export const getContentByKey = (key) => get(`/system/content/key/${key}`)
