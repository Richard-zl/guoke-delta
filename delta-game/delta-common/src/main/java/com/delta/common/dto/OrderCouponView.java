package com.delta.common.dto;

import lombok.Data;
import java.math.BigDecimal;

/** 订单优惠券展示信息 */
@Data
public class OrderCouponView {
    private String couponName;
    private String couponType;
    private BigDecimal originalAmount;
    private BigDecimal couponDiscountAmount;
}
