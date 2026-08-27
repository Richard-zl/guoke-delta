import { ElMessage } from 'element-plus'

/** 打手工作状态筛选项。 */
export const WORK_STATUS_OPTIONS = [
  { label: '全部', value: '' },
  { label: '可接单', value: 'AVAILABLE' },
  { label: '待接单确认', value: 'ASSIGNED_PENDING' },
  { label: '服务中', value: 'IN_SERVICE' },
  { label: '已满单', value: 'FULL' },
  { label: '离线', value: 'OFFLINE' },
  { label: '账号异常', value: 'ACCOUNT_ABNORMAL' },
]

const WORK_STATUS_META = {
  AVAILABLE: { label: '可接单', type: 'success' },
  ASSIGNED_PENDING: { label: '待接单确认', type: 'warning' },
  IN_SERVICE: { label: '服务中', type: 'warning' },
  FULL: { label: '已满单', type: 'danger' },
  OFFLINE: { label: '离线', type: 'info' },
  ACCOUNT_ABNORMAL: { label: '账号异常', type: 'danger' },
}

/** 返回 Element Plus 状态标签配置。 */
export function workStatusMeta(status) {
  return WORK_STATUS_META[status] || { label: status || '-', type: 'info' }
}

/** 格式化打手当前占用和待确认数量。 */
export function formatActiveOrders(player, fallbackMax = 1) {
  const active = Number(player?.activeOrders || 0)
  const max = Number(player?.maxConcurrent || fallbackMax || 1)
  const pending = Number(player?.pendingAssignedOrders || 0)
  return `进行中 ${active}/${max}${pending > 0 ? ` · 待确认 ${pending}` : ''}`
}

/** 判断打手是否还能被选择指派。 */
export function isPlayerSelectable(player, fallbackMax = 1) {
  const active = Number(player?.activeOrders || 0)
  const max = Number(player?.maxConcurrent || fallbackMax || 1)
  return player?.isOnline === 1
    && active < max
    && !['OFFLINE', 'FULL', 'ACCOUNT_ABNORMAL'].includes(player?.workStatus)
}

/** 离线或账号异常时灰显行。 */
export function isPlayerRowDimmed(player) {
  return player?.isOnline !== 1
    || ['OFFLINE', 'ACCOUNT_ABNORMAL'].includes(player?.workStatus)
}

/** 点击选择时进行防御校验并给出明确提示。 */
export function guardPlayerSelection(player, fallbackMax = 1) {
  if (isPlayerSelectable(player, fallbackMax)) return true

  const active = Number(player?.activeOrders || 0)
  const max = Number(player?.maxConcurrent || fallbackMax || 1)
  if (player?.workStatus === 'FULL' || active >= max) {
    ElMessage.warning('已达最大接单数')
  } else if (player?.workStatus === 'ACCOUNT_ABNORMAL') {
    ElMessage.warning('打手账号异常，无法指派')
  } else {
    ElMessage.warning('打手当前离线，无法指派')
  }
  return false
}
