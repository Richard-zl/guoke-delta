package com.delta.common.service;

import com.delta.common.dto.DashboardMoneyMetrics;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class DashboardMetricsCalculatorTest {

    @Test
    void sameDayAndCrossDayRefund_exampleFromSpec() {
        // 今日支付 1000，同日退 100，跨日退 200 → refundTotal=300
        DashboardMoneyMetrics m = DashboardMetricsCalculator.of(
                2L,
                new BigDecimal("1000.00"),
                new BigDecimal("100.00"),
                new BigDecimal("300.00"));
        assertEquals(0, new BigDecimal("900.00").compareTo(m.getGmv()));
        assertEquals(0, new BigDecimal("300.00").compareTo(m.getRefundAmount()));
        assertEquals(0, new BigDecimal("700.00").compareTo(m.getNetAmount()));
        assertEquals(2L, m.getPaidOrderCount());
    }

    @Test
    void nullAmounts_treatedAsZero() {
        DashboardMoneyMetrics m = DashboardMetricsCalculator.of(0L, null, null, null);
        assertEquals(0, BigDecimal.ZERO.compareTo(m.getGmv()));
        assertEquals(0, BigDecimal.ZERO.compareTo(m.getNetAmount()));
    }

    @Test
    void identities_hold() {
        BigDecimal paid = new BigDecimal("500");
        BigDecimal sameDay = new BigDecimal("50");
        BigDecimal refund = new BigDecimal("120");
        DashboardMoneyMetrics m = DashboardMetricsCalculator.of(1L, paid, sameDay, refund);
        // 净成交 = paidGross - refundTotal
        assertEquals(0, paid.subtract(refund).compareTo(m.getNetAmount()));
        // 净成交 = GMV - crossDayRefund
        BigDecimal crossDay = refund.subtract(sameDay);
        assertEquals(0, m.getGmv().subtract(crossDay).compareTo(m.getNetAmount()));
    }
}
