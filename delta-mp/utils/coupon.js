/** 是否为现金券 */
export function isCashCoupon(coupon) {
  return coupon?.couponType?.startsWith('CASH')
}

/** 折扣券面额数字（如 9.5 折显示 9.5） */
export function getCouponAmountNum(coupon) {
  if (!coupon) return ''
  if (isCashCoupon(coupon)) {
    return Number(coupon.cashAmount || 0).toFixed(0)
  }
  const rate = resolveDiscountRate(coupon)
  if (rate == null) return ''
  return parseFloat((rate * 10).toFixed(1))
}

/** 券类型标签 */
export function getCouponTypeLabel(coupon) {
  if (!coupon) return ''
  if (isCashCoupon(coupon)) return '代金券'
  return `${getCouponAmountNum(coupon)}折优惠券`
}

/** 格式化过期时间 */
export function formatCouponExpire(dateStr) {
  if (!dateStr) return '--'
  return dateStr.slice(0, 10)
}

/** 折扣率。优先用券上的 discountRate，旧券没有该字段时按类型回退。 */
export function resolveDiscountRate(coupon) {
  if (!coupon || isCashCoupon(coupon)) return null
  const rate = Number(coupon.discountRate)
  if (rate > 0 && rate < 1) return rate
  if (coupon.couponType === 'DISCOUNT_9') return 0.9
  if (coupon.couponType === 'DISCOUNT_8') return 0.8
  if (coupon.couponType === 'DISCOUNT_75') return 0.75
  return null
}

/** 根据原价和券计算折后价 */
export function calcFinalAmount(originalAmount, coupon) {
  if (!coupon) return originalAmount
  let final = originalAmount
  const rate = resolveDiscountRate(coupon)
  if (rate != null) {
    final = originalAmount * rate
  } else if (coupon.couponType === 'CASH_5' || isCashCoupon(coupon)) {
    final = originalAmount - Number(coupon.cashAmount || 5)
  }
  return Math.max(0, Number(final.toFixed(2)))
}

/** 根据原价和券计算优惠金额 */
export function calcDiscountAmount(originalAmount, coupon) {
  if (!coupon) return 0
  return Number((originalAmount - calcFinalAmount(originalAmount, coupon)).toFixed(2))
}
