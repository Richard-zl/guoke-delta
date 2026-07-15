import request from '@/utils/request'

export function getAdminDashboard() {
  return request({ url: '/admin/home/dashboard', method: 'get' })
}

export function getCsDashboard() {
  return request({ url: '/cs/dashboard', method: 'get' })
}

// ==================== 数据统计 ====================
export function getStatisticsOverview() {
  return request({ url: '/admin/statistics/overview', method: 'get' })
}
export function getStatisticsOrder(params) {
  return request({ url: '/admin/statistics/order', method: 'get', params })
}
export function getStatisticsUser() {
  return request({ url: '/admin/statistics/user', method: 'get' })
}
export function getStatisticsPlayer() {
  return request({ url: '/admin/statistics/player', method: 'get' })
}
export function getStatisticsIncomeDaily(params) {
  return request({ url: '/admin/statistics/income-daily', method: 'get', params })
}
export function getSpendingRank(params, role = 'admin') {
  const base = role === 'cs' ? '/cs/order/spending-rank' : '/admin/statistics/user-spending-rank'
  return request({ url: base, method: 'get', params })
}
