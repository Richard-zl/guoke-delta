package com.delta.common.dto;

import lombok.Data;
import java.math.BigDecimal;

/** 经营金额窗口指标（GMV / 退款 / 净成交） */
@Data
public class DashboardMoneyMetrics {
    /** 已支付订单数（按 paid_at） */
    private Long paidOrderCount;
    /** 支付毛额 */
    private BigDecimal paidGross;
    /** 同日退款 */
    private BigDecimal sameDayRefund;
    /** 退款额（窗口内全部退款） */
    private BigDecimal refundAmount;
    /** GMV = paidGross - sameDayRefund */
    private BigDecimal gmv;
    /** 净成交 = paidGross - refundAmount */
    private BigDecimal netAmount;
}
