/** 是否为现金券 */
export function isCashCoupon(coupon) {
  return coupon?.couponType?.startsWith('CASH')
}

/** 折扣券面额数字（如 8 折显示 8） */
export function getCouponAmountNum(coupon) {
  if (!coupon) return ''
  if (isCashCoupon(coupon)) {
    return Number(coupon.cashAmount || 0).toFixed(0)
  }
  return parseFloat((coupon.discountRate * 10).toFixed(1))
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

/** 根据原价和券类型计算折后价 */
export function calcFinalAmount(originalAmount, coupon) {
  if (!coupon) return originalAmount
  let final = originalAmount
  if (coupon.couponType === 'DISCOUNT_9') {
    final = originalAmount * 0.9
  } else if (coupon.couponType === 'DISCOUNT_8') {
    final = originalAmount * 0.8
  } else if (coupon.couponType === 'DISCOUNT_75') {
    final = originalAmount * 0.75
  } else if (coupon.couponType === 'CASH_5') {
    final = originalAmount - 5
  }
  return Math.max(0, Number(final.toFixed(2)))
}

/** 根据原价和券计算优惠金额 */
export function calcDiscountAmount(originalAmount, coupon) {
  if (!coupon) return 0
  return Number((originalAmount - calcFinalAmount(originalAmount, coupon)).toFixed(2))
}
