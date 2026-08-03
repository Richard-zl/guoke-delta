package com.delta.common.service;

import com.delta.common.dto.DashboardMoneyMetrics;
import com.delta.common.dto.DashboardTrendPoint;
import com.delta.common.mapper.StatsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 仪表盘经营指标聚合服务（统一 payment 口径） */
@Service
@RequiredArgsConstructor
public class DashboardMetricsService {
    private final StatsMapper statsMapper;

    public DashboardMoneyMetrics metricsForDate(String date) {
        return DashboardMetricsCalculator.of(
                statsMapper.countPaidOrdersByDate(date),
                statsMapper.sumPaidGrossByDate(date),
                statsMapper.sumSameDayRefundByDate(date),
                statsMapper.sumRefundAmountByDate(date));
    }

    public DashboardMoneyMetrics metricsTotal() {
        return DashboardMetricsCalculator.of(
                statsMapper.countPaidOrdersTotal(),
                statsMapper.sumPaidGrossTotal(),
                statsMapper.sumSameDayRefundTotal(),
                statsMapper.sumRefundAmountTotal());
    }

    public List<DashboardTrendPoint> trendLast7Days() {
        // paidGrossTrend7Days 只查一次，同时提供 gross 与 orderCount
        List<Map<String, Object>> paidRows = statsMapper.paidGrossTrend7Days();
        Map<String, BigDecimal> paidGross = toDecimalMap(paidRows, "date", "paidGross");
        Map<String, Long> paidOrders = toLongMap(paidRows, "date", "paidOrderCount");
        Map<String, BigDecimal> refunds = toDecimalMap(statsMapper.refundTrend7Days(), "date", "refundAmount");
        Map<String, BigDecimal> sameDay = toDecimalMap(statsMapper.sameDayRefundTrend7Days(), "date", "sameDayRefund");

        List<DashboardTrendPoint> points = new ArrayList<>();
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(6);
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            String key = d.toString();
            DashboardMoneyMetrics m = DashboardMetricsCalculator.of(
                    paidOrders.getOrDefault(key, 0L),
                    paidGross.getOrDefault(key, BigDecimal.ZERO),
                    sameDay.getOrDefault(key, BigDecimal.ZERO),
                    refunds.getOrDefault(key, BigDecimal.ZERO));
            DashboardTrendPoint p = new DashboardTrendPoint();
            p.setDate(key);
            p.setPaidOrderCount(m.getPaidOrderCount());
            p.setGmv(m.getGmv());
            p.setRefundAmount(m.getRefundAmount());
            p.setNetAmount(m.getNetAmount());
            points.add(p);
        }
        return points;
    }

    private Map<String, BigDecimal> toDecimalMap(List<Map<String, Object>> rows, String keyField, String valueField) {
        Map<String, BigDecimal> map = new HashMap<>();
        if (rows == null) return map;
        for (Map<String, Object> row : rows) {
            Object k = row.get(keyField);
            if (k == null) continue;
            map.put(String.valueOf(k), toBigDecimal(row.get(valueField)));
        }
        return map;
    }

    private Map<String, Long> toLongMap(List<Map<String, Object>> rows, String keyField, String valueField) {
        Map<String, Long> map = new HashMap<>();
        if (rows == null) return map;
        for (Map<String, Object> row : rows) {
            Object k = row.get(keyField);
            if (k == null) continue;
            Object v = row.get(valueField);
            map.put(String.valueOf(k), v == null ? 0L : ((Number) v).longValue());
        }
        return map;
    }

    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        if (v instanceof BigDecimal bd) return bd;
        return new BigDecimal(v.toString());
    }
}
