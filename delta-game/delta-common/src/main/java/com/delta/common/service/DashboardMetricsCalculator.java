package com.delta.common.service;

import com.delta.common.dto.DashboardMoneyMetrics;
import java.math.BigDecimal;

/** 经营指标派生（纯函数，便于单测） */
public final class DashboardMetricsCalculator {
    private DashboardMetricsCalculator() {}

    public static DashboardMoneyMetrics of(Long paidOrderCount,
                                           BigDecimal paidGross,
                                           BigDecimal sameDayRefund,
                                           BigDecimal refundTotal) {
        BigDecimal gross = nz(paidGross);
        BigDecimal sameDay = nz(sameDayRefund);
        BigDecimal refund = nz(refundTotal);
        DashboardMoneyMetrics m = new DashboardMoneyMetrics();
        m.setPaidOrderCount(paidOrderCount == null ? 0L : paidOrderCount);
        m.setPaidGross(gross);
        m.setSameDayRefund(sameDay);
        m.setRefundAmount(refund);
        m.setGmv(gross.subtract(sameDay));
        m.setNetAmount(gross.subtract(refund));
        return m;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
