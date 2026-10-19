package com.delta.common.service;

import com.delta.common.entity.Coupon;
import com.delta.common.exception.BusinessException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 优惠券折扣。新发放只能是 9.0 到 9.9 折；已发出的八折、7.5 折仍按原折扣核销。
 */
public final class CouponDiscountRules {

    public static final BigDecimal MIN_ZHE = new BigDecimal("9.0");
    public static final BigDecimal MAX_ZHE = new BigDecimal("9.9");
    /** 按折扣率发放的券，具体几折看 discountRate */
    public static final String TYPE_DISCOUNT = "DISCOUNT";

    private static final BigDecimal TEN = BigDecimal.TEN;
    private static final BigDecimal CASH_FIVE = new BigDecimal("5");

    private CouponDiscountRules() {
    }

    /**
     * 校验发放折扣。必须是 9.0 到 9.9，最多一位小数。
     */
    public static BigDecimal requireGrantZhe(BigDecimal zhe) {
        if (zhe == null) {
            throw new BusinessException("请填写折扣");
        }
        if (zhe.stripTrailingZeros().scale() > 1) {
            throw new BusinessException("折扣最多保留一位小数");
        }
        if (zhe.compareTo(MIN_ZHE) < 0 || zhe.compareTo(MAX_ZHE) > 0) {
            throw new BusinessException("折扣需在9.0到9.9之间");
        }
        return zhe.stripTrailingZeros();
    }

    /** 9.5 折 → 0.95 */
    public static BigDecimal toRate(BigDecimal zhe) {
        return requireGrantZhe(zhe).divide(TEN, 2, RoundingMode.HALF_UP);
    }

    public static boolean isCash(Coupon coupon) {
        return coupon != null && coupon.getType() != null && coupon.getType().startsWith("CASH");
    }

    /** 核销用的折扣率。现金券返回 null。 */
    public static BigDecimal resolveRate(Coupon coupon) {
        if (coupon == null || isCash(coupon)) {
            return null;
        }
        BigDecimal stored = coupon.getDiscountRate();
        if (stored != null && stored.signum() > 0 && stored.compareTo(BigDecimal.ONE) < 0) {
            return stored;
        }
        String type = coupon.getType();
        if ("DISCOUNT_9".equals(type)) {
            return new BigDecimal("0.9");
        }
        if ("DISCOUNT_8".equals(type)) {
            return new BigDecimal("0.8");
        }
        if ("DISCOUNT_75".equals(type)) {
            return new BigDecimal("0.75");
        }
        return null;
    }

    /** 旧的八折、7.5 折模板不能再发。 */
    public static void assertGrantable(Coupon coupon) {
        if (coupon == null) {
            throw new BusinessException("优惠券不存在");
        }
        if (isCash(coupon)) {
            return;
        }
        BigDecimal rate = resolveRate(coupon);
        if (rate == null) {
            throw new BusinessException("优惠券折扣无效");
        }
        try {
            requireGrantZhe(rate.multiply(TEN));
        } catch (BusinessException ex) {
            throw new BusinessException("该折扣已暂停发放，只能发放9.0到9.9折");
        }
    }

    public static BigDecimal discount(BigDecimal price, Coupon coupon) {
        if (price == null) {
            return BigDecimal.ZERO;
        }
        if (isCash(coupon)) {
            BigDecimal cash = coupon.getCashAmount() != null ? coupon.getCashAmount() : CASH_FIVE;
            return price.subtract(cash);
        }
        if ("CASH_5".equals(coupon != null ? coupon.getType() : null)) {
            return price.subtract(CASH_FIVE);
        }
        BigDecimal rate = resolveRate(coupon);
        if (rate == null) {
            return price;
        }
        return price.multiply(rate);
    }

    public static BigDecimal inferOriginal(BigDecimal finalAmount, Coupon coupon) {
        if (finalAmount == null) {
            return BigDecimal.ZERO;
        }
        if (isCash(coupon) || (coupon != null && "CASH_5".equals(coupon.getType()))) {
            BigDecimal cash = coupon.getCashAmount() != null ? coupon.getCashAmount() : CASH_FIVE;
            return finalAmount.add(cash);
        }
        BigDecimal rate = resolveRate(coupon);
        if (rate == null || rate.signum() == 0) {
            return finalAmount;
        }
        return finalAmount.divide(rate, 2, RoundingMode.HALF_UP);
    }
}
