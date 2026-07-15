import request from '@/utils/request'

// ==================== 商品管理 ====================
export function getProductList(params) {
  return request({ url: '/product/list', method: 'get', params })
}
export function getProductDetail(id) {
  return request({ url: `/product/${id}`, method: 'get' })
}
export function addProduct(data) {
  return request({ url: '/product', method: 'post', data })
}
export function updateProduct(data) {
  return request({ url: '/product', method: 'put', data })
}
export function deleteProduct(id) {
  return request({ url: `/product/${id}`, method: 'delete' })
}
export function updateProductStatus(id, status) {
  return request({ url: `/product/${id}/status`, method: 'put', params: { status } })
}

// ==================== 分类管理 ====================
export function getCategoryList() {
  return request({ url: '/product/category/list', method: 'get' })
}
export function getAllCategories() {
  return request({ url: '/product/category/all', method: 'get' })
}
export function getCategoryTree() {
  return request({ url: '/product/category/tree', method: 'get' })
}
/** 热门推荐分类（首页 Tab：全部、护航专区推荐、热门推荐等）- 公开 */
export function getRecommendCategories() {
  return request({ url: '/product/recommend/categories', method: 'get' })
}

// ==================== 热门分类管理（后台 CRUD） ====================
export function getRecommendCategoryList() {
  return request({ url: '/admin/recommend-category/list', method: 'get' })
}
export function addRecommendCategory(data) {
  return request({ url: '/admin/recommend-category', method: 'post', data })
}
export function updateRecommendCategory(data) {
  return request({ url: '/admin/recommend-category', method: 'put', data })
}
export function deleteRecommendCategory(id) {
  return request({ url: `/admin/recommend-category/${id}`, method: 'delete' })
}
// ==================== 分类字段管理 ====================
export function getCategoryFieldList(categoryId) {
  return request({ url: '/admin/category-field/list', method: 'get', params: { categoryId } })
}
export function addCategoryField(data) {
  return request({ url: '/admin/category-field', method: 'post', data })
}
export function updateCategoryField(data) {
  return request({ url: '/admin/category-field', method: 'put', data })
}
export function deleteCategoryField(id) {
  return request({ url: `/admin/category-field/${id}`, method: 'delete' })
}

export function addCategory(data) {
  return request({ url: '/product/category', method: 'post', data })
}
export function updateCategory(data) {
  return request({ url: '/product/category', method: 'put', data })
}
export function deleteCategory(id) {
  return request({ url: `/product/category/${id}`, method: 'delete' })
}

// ==================== 客服端分类管理 ====================
export function csCategoryList() {
  return request({ url: '/cs/category/list', method: 'get' })
}
export function csCategorySave(data) {
  return request({ url: '/cs/category', method: 'post', data })
}
export function csCategoryUpdateStatus(id, status) {
  return request({ url: '/cs/category/status', method: 'put', params: { id, status } })
}
