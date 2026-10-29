/** 运营后台账号角色。菜单和按钮按角色收口，运营和店长的接口令牌仍是管理员。 */

export const ROLE_OPTIONS = [
  { label: '管理员', value: 'admin' },
  { label: '客服', value: 'cs' },
  { label: '运营', value: 'operator' },
  { label: '店长', value: 'manager' },
]

const ROLE_LABEL = {
  admin: '管理员',
  cs: '客服',
  operator: '运营',
  manager: '店长',
}

const ROLE_TAG = {
  admin: 'danger',
  cs: 'warning',
  operator: 'success',
  manager: 'primary',
}

/** 把登录返回的 ADMIN/CS/OPERATOR/MANAGER 收成前端角色 */
export function normalizeRole(role) {
  const value = String(role || '').trim().toLowerCase()
  if (value === 'admin' || value === 'both') return 'admin'
  if (value === 'operator') return 'operator'
  if (value === 'manager') return 'manager'
  if (value === 'cs') return 'cs'
  return value
}

export function roleLabel(role) {
  return ROLE_LABEL[normalizeRole(role)] || role || ''
}

export function roleTagType(role) {
  return ROLE_TAG[normalizeRole(role)] || 'info'
}

/** 客服走 /cs 接口，其余角色走 /admin */
export function useAdminApi(role) {
  return normalizeRole(role) !== 'cs'
}

/** 用户余额：管理员、客服 */
export function canAdjustUserBalance(role) {
  const value = normalizeRole(role)
  return value === 'admin' || value === 'cs'
}

/** 用户积分：仅管理员 */
export function canAdjustUserPoints(role) {
  return normalizeRole(role) === 'admin'
}

/** 优惠券发放和失效：管理员、客服 */
export function canOperateUserCoupon(role) {
  const value = normalizeRole(role)
  return value === 'admin' || value === 'cs'
}

/** 封禁用户：管理员、客服 */
export function canBanUser(role) {
  const value = normalizeRole(role)
  return value === 'admin' || value === 'cs'
}

/** 运营、店长不展示完整手机号 */
export function shouldMaskUserPhone(role) {
  const value = normalizeRole(role)
  return value === 'operator' || value === 'manager'
}

/** 打手佣金加减：仅管理员 */
export function canAdjustPlayerFund(role) {
  return normalizeRole(role) === 'admin'
}

export function canUseChat(role) {
  const value = normalizeRole(role)
  return value === 'admin' || value === 'cs'
}

/** 退款审核页：管理员、客服 */
export function canReviewRefund(role) {
  const value = normalizeRole(role)
  return value === 'admin' || value === 'cs'
}

export function formatPhone(phone, masked) {
  if (!phone) return '-'
  const text = String(phone)
  if (!masked) return text
  if (text.length < 7) return `${text.slice(0, 2)}****`
  return `${text.slice(0, 3)}****${text.slice(-4)}`
}
