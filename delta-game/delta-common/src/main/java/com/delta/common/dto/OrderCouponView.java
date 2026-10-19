package com.delta.common.dto;

import lombok.Data;
import java.math.BigDecimal;

/** 订单优惠券展示信息 */
@Data
public class OrderCouponView {
    private String couponName;
    private String couponType;
    /** 折扣率，9.5 折为 0.95。现金券为空 */
    private BigDecimal discountRate;
    private BigDecimal originalAmount;
    private BigDecimal couponDiscountAmount;
}
