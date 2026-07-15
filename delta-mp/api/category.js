import { get } from './request'

/** 全部分类（公开接口，无需登录） */
export const getAllCategories = (opts = {}) => get('/product/category/all', {}, { auth: false, ...opts })

/** 分类列表（公开接口，无需登录） */
export const getCategoryList = (params) => get('/product/category/list', params, { auth: false })

/** 分类树（父级+子级） */
export const getCategoryTree = (opts = {}) => get('/product/category/tree', {}, { auth: false, ...opts })