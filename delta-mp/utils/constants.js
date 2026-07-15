/**
 * 全局枚举常量
 */

// ========== 角色 ==========
export const ROLE = {
  USER: 'user',
  PLAYER: 'player',
  CS: 'cs'
}

// ========== 订单状态 ==========
export const ORDER_STATUS = {
  PENDING_PAYMENT: 'PENDING_PAYMENT',
  PAID: 'PAID',
  ASSIGNED: 'ASSIGNED',
  ACCEPTED: 'ACCEPTED',
  WAITING_TEAMMATE: 'WAITING_TEAMMATE',
  IN_PROGRESS: 'IN_PROGRESS',
  COMPLETED: 'COMPLETED',
  CONFIRMED: 'CONFIRMED',
  REVIEWED: 'REVIEWED',
  CANCELLED: 'CANCELLED',
  REFUNDING: 'REFUNDING',
  REFUNDED: 'REFUNDED',
  DISPUTED: 'DISPUTED',
  ARBITRATED: 'ARBITRATED'
}

export const ORDER_STATUS_TEXT = {
  PENDING_PAYMENT: '待支付',
  PAID: '待接单',
  ASSIGNED: '已指派',
  ACCEPTED: '已接单',
  WAITING_TEAMMATE: '组队中',
  IN_PROGRESS: '进行中',
  COMPLETED: '待确认',
  CONFIRMED: '已完成',
  REVIEWED: '已评价',
  CANCELLED: '已取消',
  REFUNDING: '退款中',
  REFUNDED: '已退款',
  DISPUTED: '争议中',
  ARBITRATED: '已仲裁'
}

export const ORDER_STATUS_COLOR = {
  PENDING_PAYMENT: '#ff9900',
  PAID: '#6366f1',
  ASSIGNED: '#9c27b0',
  ACCEPTED: '#6366f1',
  WAITING_TEAMMATE: '#818cf8',
  IN_PROGRESS: '#818cf8',
  COMPLETED: '#07c160',
  CONFIRMED: '#07c160',
  REVIEWED: '#07c160',
  CANCELLED: '#999999',
  REFUNDING: '#ee0a24',
  REFUNDED: '#ee0a24',
  DISPUTED: '#ee0a24',
  ARBITRATED: '#9c27b0'
}

// ========== 用户端订单Tab ==========
export const USER_ORDER_TABS = [
  { label: '全部', value: '' },
  { label: '待支付', value: 'PENDING_PAYMENT' },
  { label: '进行中', value: 'IN_PROGRESS' },
  { label: '待确认', value: 'COMPLETED' },
  { label: '已完成', value: 'CONFIRMED' }
]

// ========== 接单员端订单Tab ==========
export const PLAYER_ORDER_TABS = [
  { label: '全部', value: '' },
  { label: '待接单', value: 'ASSIGNED' },
  { label: '已接单', value: 'ACCEPTED' },
  { label: '进行中', value: 'IN_PROGRESS' },
  { label: '待确认', value: 'COMPLETED' },
  { label: '已完成', value: 'CONFIRMED' },
  { label: '已评价', value: 'REVIEWED' },
  { label: '争议中', value: 'DISPUTED' },
  { label: '已取消', value: 'CANCELLED' }
]

// ========== 接单员状态 ==========
export const PLAYER_STATUS = {
  PENDING: 'PENDING',
  ACTIVE: 'ACTIVE',
  REJECTED: 'REJECTED',
  FROZEN: 'FROZEN'
}

export const PLAYER_STATUS_TEXT = {
  PENDING: '待审核',
  ACTIVE: '正常',
  REJECTED: '已驳回',
  FROZEN: '已冻结'
}

export const PLAYER_STATUS_COLOR = {
  PENDING: '#ff9900',
  ACTIVE: '#07c160',
  REJECTED: '#ee0a24',
  FROZEN: '#999999'
}

// ========== 提现状态 ==========
export const WITHDRAW_STATUS = {
  PENDING: 'PENDING',
  APPROVED: 'APPROVED',
  REJECTED: 'REJECTED',
  COMPLETED: 'COMPLETED'
}

export const WITHDRAW_STATUS_TEXT = {
  PENDING: '审核中',
  APPROVED: '已批准',
  REJECTED: '已拒绝',
  COMPLETED: '已到账'
}

// ========== 投诉状态 ==========
export const COMPLAINT_STATUS = {
  PENDING: 'PENDING',
  PROCESSING: 'PROCESSING',
  RESOLVED: 'RESOLVED',
  APPEALING: 'APPEALING',
  APPEAL_RESOLVED: 'APPEAL_RESOLVED'
}

export const COMPLAINT_STATUS_TEXT = {
  PENDING: '待处理',
  PROCESSING: '处理中',
  RESOLVED: '已解决',
  APPEALING: '申诉中',
  APPEAL_RESOLVED: '申诉处理完毕'
}

// ========== 消息类型 ==========
export const MESSAGE_TYPE = {
  ORDER: 'ORDER',
  SYSTEM: 'SYSTEM',
  CHAT: 'CHAT',
  WITHDRAW: 'WITHDRAW'
}

/** 系统通知/站内消息类型中文（含订单、提现等事件类型） */
export const MESSAGE_TYPE_TEXT = {
  ORDER: '订单消息',
  SYSTEM: '系统通知',
  CHAT: '聊天消息',
  WITHDRAW: '提现通知',
  ORDER_PAID: '支付成功',
  ORDER_ACCEPTED: '订单已接单',
  ORDER_ASSIGNED: '指派订单',
  ORDER_COMPLETED: '服务已完成',
  ORDER_CONFIRMED: '订单已确认',
  ORDER_CANCEL_REFUND: '订单取消退款',
  ORDER_DISPUTED: '订单纠纷',
  TEAMMATE_INVITED: '组队邀请',
  INCOME_SETTLED: '收入到账',
  COMPLAINT_RESOLVED: '仲裁已出结果',
  WITHDRAW_COMPLETED: '提现成功',
  WITHDRAW_REJECTED: '提现被拒',
  WITHDRAW_REMIND: '提现待审核提醒',
  WITHDRAW_APPLY: '提现申请'
}

// ========== 支付方式 ==========
export const PAY_TYPE = {
  WECHAT: 'WECHAT',
  BALANCE: 'BALANCE'
}

// ========== 聊天消息类型 ==========
export const CHAT_MSG_TYPE = {
  TEXT: 'TEXT',
  IMAGE: 'IMAGE',
  SYSTEM: 'SYSTEM'
}
