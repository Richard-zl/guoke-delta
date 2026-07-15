import { get } from './request'

/** 商品列表（公开接口，无需登录） */
export const getProductList = (params) => get('/product/list', params, { auth: false })

/** 商品详情 - 基础（登录时附带体验单限购状态） */
export const getProductDetail = (id) => get(`/product/${id}`, {}, { auth: true })

/** 商品详情 - 聚合API（含规格+价格规则+评价概要+评分分布+分类名+最低价） */
export const getProductRichDetail = (id) => get(`/app/product/${id}`, {}, { auth: true })

/** 推荐商品（公开接口），categoryId 不传或空为全部热门 */
export const getRecommendProducts = (params = {}, opts = {}) => get('/product/recommend', params, { auth: false, ...opts })
/** 热门推荐分类列表（首页 Tab：全部、护航专区推荐、热门推荐等） */
export const getRecommendCategories = (opts = {}) => get('/product/recommend/categories', {}, { auth: false, ...opts })

/** 获取分类动态表单字段（自动解析父分类） */
export const getCategoryFormFields = (categoryId) => get(`/product/category/${categoryId}/form-fields`, {}, { auth: false })
