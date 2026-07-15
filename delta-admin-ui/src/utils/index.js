/**
 * 格式化日期时间
 */
export function formatDateTime(dateStr) {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/**
 * 格式化金额
 */
export function formatAmount(amount) {
  if (amount === null || amount === undefined) return '-'
  return `¥${Number(amount).toFixed(2)}`
}

/**
 * 订单状态映射
 */
export const ORDER_STATUS_MAP = {
  PENDING_PAYMENT: { label: '待支付', type: 'info' },
  PAID: { label: '已支付', type: 'warning' },
  ASSIGNED: { label: '已指派', type: '' },
  ACCEPTED: { label: '已接单', type: '' },
  WAITING_TEAMMATE: { label: '待组队', type: 'warning' },
  IN_PROGRESS: { label: '服务中', type: '' },
  COMPLETED: { label: '已完成', type: 'success' },
  CONFIRMED: { label: '已确认', type: 'success' },
  REVIEWED: { label: '已评价', type: 'success' },
  CANCELLED: { label: '已取消', type: 'info' },
  REFUNDING: { label: '退款中', type: 'danger' },
  REFUNDED: { label: '已退款', type: 'info' },
  DISPUTED: { label: '申诉中', type: 'danger' },
  ARBITRATED: { label: '已仲裁', type: 'warning' }
}

/**
 * 打手状态映射
 */
export const PLAYER_STATUS_MAP = {
  PENDING: { label: '待审核', type: 'warning' },
  ACTIVE: { label: '正常', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' },
  FROZEN: { label: '已冻结', type: 'info' }
}

/**
 * 投诉状态映射
 */
export const COMPLAINT_STATUS_MAP = {
  PENDING: { label: '待处理', type: 'warning' },
  PROCESSING: { label: '处理中', type: '' },
  RESOLVED: { label: '已处理', type: 'success' },
  APPEALED: { label: '已申诉', type: 'danger' }
}

/**
 * 提现状态映射
 */
export const WITHDRAW_STATUS_MAP = {
  PENDING: { label: '待处理', type: 'warning' },
  COMPLETED: { label: '已完成', type: 'success' },
  REJECTED: { label: '已拒绝', type: 'danger' }
}
