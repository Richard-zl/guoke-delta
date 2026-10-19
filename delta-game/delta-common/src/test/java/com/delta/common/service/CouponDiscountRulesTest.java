package com.delta.common.service;

import com.delta.common.entity.Coupon;
import com.delta.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponDiscountRulesTest {

    @Test
    void 九折到九点九折可以发放() {
        assertEquals(0, new BigDecimal("0.99").compareTo(CouponDiscountRules.toRate(new BigDecimal("9.9"))));
        assertEquals(0, new BigDecimal("0.90").compareTo(CouponDiscountRules.toRate(new BigDecimal("9.0"))));
        assertEquals(0, new BigDecimal("0.95").compareTo(CouponDiscountRules.toRate(new BigDecimal("9.50"))));
    }

    @Test
    void 低于九折或超过一位小数不能发放() {
        assertEquals("折扣需在9.0到9.9之间",
                assertThrows(BusinessException.class, () -> CouponDiscountRules.toRate(new BigDecimal("8.0"))).getMessage());
        assertEquals("折扣需在9.0到9.9之间",
                assertThrows(BusinessException.class, () -> CouponDiscountRules.toRate(new BigDecimal("7.5"))).getMessage());
        assertEquals("折扣最多保留一位小数",
                assertThrows(BusinessException.class, () -> CouponDiscountRules.toRate(new BigDecimal("9.25"))).getMessage());
    }

    @Test
    void 已发出的八折仍按原折扣计算() {
        Coupon coupon = new Coupon();
        coupon.setType("DISCOUNT_8");
        assertEquals(0, new BigDecimal("80.00").compareTo(
                CouponDiscountRules.discount(new BigDecimal("100"), coupon).setScale(2)));
        assertThrows(BusinessException.class, () -> CouponDiscountRules.assertGrantable(coupon));
    }

    @Test
    void 新折扣券按discountRate计算() {
        Coupon coupon = new Coupon();
        coupon.setType(CouponDiscountRules.TYPE_DISCOUNT);
        coupon.setDiscountRate(new BigDecimal("0.95"));
        assertEquals(0, new BigDecimal("95.00").compareTo(
                CouponDiscountRules.discount(new BigDecimal("100"), coupon).setScale(2)));
    }
}
