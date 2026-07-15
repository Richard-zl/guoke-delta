package com.delta.pay.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * H5 支付页只读订单展示信息。仅返回展示所需字段，不包含可改券的优惠券列表，
 * 优惠券信息为下单时已绑定的只读结果（与 Spec 决议 #2 一致：H5 不支持改券）。
 */
public record H5OrderView(Long orderId,
                           String orderNo,
                           String productName,
                           BigDecimal amount,
                           String status,
                           LocalDateTime payDeadline,
                           String couponName,
                           BigDecimal couponDiscountAmount) {
}
