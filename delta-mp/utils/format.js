/**
 * 格式化工具函数
 */

function normalizeDateInput(dateStr) {
  if (!dateStr) return null
  // 兼容后端返回 "yyyy-MM-dd HH:mm:ss" 在 iOS 上无法解析的问题：
  // 统一替换为 "yyyy-MM-ddTHH:mm:ss"
  if (typeof dateStr === 'string' && dateStr.includes(' ') && !dateStr.includes('T')) {
    return new Date(dateStr.replace(' ', 'T'))
  }
  return new Date(dateStr)
}

/**
 * 格式化金额，保留两位小数，加 ¥ 符号
 */
export function formatPrice(price, symbol = '¥') {
  if (price === null || price === undefined) return `${symbol}0.00`
  return `${symbol}${Number(price).toFixed(2)}`
}

/**
 * 格式化金额（不带符号）
 */
export function formatAmount(amount) {
  if (amount === null || amount === undefined) return '0.00'
  return Number(amount).toFixed(2)
}

/**
 * 格式化时间 yyyy-MM-dd HH:mm:ss
 */
export function formatDateTime(dateStr) {
  if (!dateStr) return ''
  const d = normalizeDateInput(dateStr)
  if (!d || isNaN(d.getTime())) return dateStr
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/**
 * 格式化日期 yyyy-MM-dd
 */
export function formatDate(dateStr) {
  if (!dateStr) return ''
  const d = normalizeDateInput(dateStr)
  if (!d || isNaN(d.getTime())) return dateStr
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/**
 * 相对时间（几分钟前、几小时前等）
 */
export function formatRelativeTime(dateStr) {
  if (!dateStr) return ''
  const now = Date.now()
  const d = normalizeDateInput(dateStr)
  if (!d || isNaN(d.getTime())) return dateStr
  const diff = Math.floor((now - d.getTime()) / 1000)

  if (diff < 60) return '刚刚'
  if (diff < 3600) return `${Math.floor(diff / 60)}分钟前`
  if (diff < 86400) return `${Math.floor(diff / 3600)}小时前`
  if (diff < 604800) return `${Math.floor(diff / 86400)}天前`
  return formatDate(dateStr)
}

/**
 * 手机号脱敏 138****8888
 */
export function maskPhone(phone) {
  if (!phone || phone.length < 7) return phone || ''
  return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
}

/**
 * 游戏账号脱敏
 */
export function maskAccount(account) {
  if (!account || account.length < 4) return '****'
  return account.substring(0, 2) + '****' + account.substring(account.length - 2)
}

/**
 * 数字超过万显示 x.x万
 */
export function formatCount(num) {
  if (!num) return '0'
  if (num >= 10000) return (num / 10000).toFixed(1) + '万'
  return String(num)
}
