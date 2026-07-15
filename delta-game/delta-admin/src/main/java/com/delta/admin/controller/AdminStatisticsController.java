package com.delta.admin.controller;

import com.delta.common.domain.R;
import com.delta.common.mapper.StatsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台数据统计
 */
@RestController
@RequestMapping("/admin/statistics")
@RequiredArgsConstructor
public class AdminStatisticsController {
    private final StatsMapper statsMapper;

    /** 数据概览 */
    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        Map<String, Object> data = new HashMap<>();
        String today = LocalDate.now().toString();
        String yesterday = LocalDate.now().minusDays(1).toString();

        // 今日新增用户
        data.put("todayNewUsers", statsMapper.countUsersByDate(today));
        data.put("yesterdayNewUsers", statsMapper.countUsersByDate(yesterday));

        // 今日订单
        data.put("todayOrders", statsMapper.countOrdersByDate(today));
        data.put("todayAmount", statsMapper.sumOrderAmountByDate(today));

        // 累计
        data.put("totalUsers", statsMapper.countTotalUsers());
        data.put("totalPlayers", statsMapper.countTotalPlayers());
        data.put("totalOrders", statsMapper.countTotalOrders());
        data.put("totalAmount", statsMapper.sumTotalOrderAmount());

        return R.ok(data);
    }

    /** 订单统计 */
    @GetMapping("/order")
    public R<Map<String, Object>> orderStats(@RequestParam(value = "period", defaultValue = "day") String period) {
        Map<String, Object> data = new HashMap<>();
        // 按状态分布
        data.put("statusDistribution", statsMapper.orderStatusDistributionCnt());

        // 近7天趋势
        data.put("trend", statsMapper.orderTrend7DaysDt());

        // 完成率
        Long total = statsMapper.countOrdersExcludingPending();
        Long completed = statsMapper.countCompletedOrders();
        data.put("completionRate", total > 0 ? completed * 100.0 / total : 0);

        return R.ok(data);
    }

    /** 用户统计 */
    @GetMapping("/user")
    public R<Map<String, Object>> userStats() {
        Map<String, Object> data = new HashMap<>();
        data.put("newUserTrend", statsMapper.newUserTrend30Days());

        Long paidUsers = statsMapper.countPaidUsers();
        Long totalUsers = statsMapper.countTotalUsers();
        data.put("paidUserRate", totalUsers > 0 ? paidUsers * 100.0 / totalUsers : 0);

        return R.ok(data);
    }

    /** 用户消费榜单 */
    @GetMapping("/user-spending-rank")
    public R<java.util.List<java.util.Map<String, Object>>> userSpendingRank(
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return R.ok(statsMapper.userSpendingRank(Math.min(limit, 200)));
    }

    /** 打手统计 */
    @GetMapping("/player")
    public R<Map<String, Object>> playerStats() {
        Map<String, Object> data = new HashMap<>();
        data.put("totalPlayers", statsMapper.countTotalPlayers());
        data.put("activePlayers", statsMapper.countActivePlayers());

        // 评分分布
        data.put("ratingDistribution", statsMapper.playerRatingDistribution());

        // 收入排行TOP10
        data.put("incomeRank", statsMapper.playerIncomeRankTop10());

        return R.ok(data);
    }

    /** 收益日报 */
    @GetMapping("/income-daily")
    public R<Map<String, Object>> incomeDaily(
            @RequestParam(value = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate resolvedEnd = endDate != null ? endDate : LocalDate.now();
        LocalDate resolvedStart = startDate != null ? startDate : resolvedEnd.minusDays(29);
        if (resolvedStart.isAfter(resolvedEnd)) {
            LocalDate temp = resolvedStart;
            resolvedStart = resolvedEnd;
            resolvedEnd = temp;
        }

        LocalDateTime start = resolvedStart.atStartOfDay();
        LocalDateTime end = resolvedEnd.plusDays(1).atStartOfDay();
        List<Map<String, Object>> rawList = statsMapper.incomeDailyStatsByRange(start, end);
        Map<String, Map<String, Object>> rawMap = new HashMap<>();
        for (Map<String, Object> row : rawList) {
            rawMap.put(String.valueOf(row.get("statDate")), row);
        }
        List<Map<String, Object>> orderDetailList = statsMapper.incomeDailyOrderDetailsByRange(start, end);
        Map<String, List<Map<String, Object>>> orderDetailMap = new HashMap<>();
        for (Map<String, Object> row : orderDetailList) {
            String statDate = String.valueOf(row.get("statDate"));
            orderDetailMap.computeIfAbsent(statDate, key -> new ArrayList<>()).add(row);
        }

        List<Map<String, Object>> list = new ArrayList<>();
        long totalOrderCount = 0L;
        BigDecimal totalOrderAmount = BigDecimal.ZERO;
        BigDecimal totalPlayerIncome = BigDecimal.ZERO;
        BigDecimal totalCommissionIncome = BigDecimal.ZERO;

        for (LocalDate date = resolvedStart; !date.isAfter(resolvedEnd); date = date.plusDays(1)) {
            String key = date.toString();
            Map<String, Object> raw = rawMap.get(key);
            long orderCount = raw != null ? toLong(raw.get("orderCount")) : 0L;
            BigDecimal orderAmount = raw != null ? toBigDecimal(raw.get("orderAmount")) : BigDecimal.ZERO;
            BigDecimal playerIncome = raw != null ? toBigDecimal(raw.get("playerIncome")) : BigDecimal.ZERO;
            BigDecimal commissionIncome = raw != null ? toBigDecimal(raw.get("commissionIncome")) : BigDecimal.ZERO;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("statDate", key);
            item.put("orderCount", orderCount);
            item.put("orderAmount", orderAmount);
            item.put("playerIncome", playerIncome);
            item.put("commissionIncome", commissionIncome);
            item.put("orders", orderDetailMap.getOrDefault(key, new ArrayList<>()));
            list.add(item);

            totalOrderCount += orderCount;
            totalOrderAmount = totalOrderAmount.add(orderAmount);
            totalPlayerIncome = totalPlayerIncome.add(playerIncome);
            totalCommissionIncome = totalCommissionIncome.add(commissionIncome);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("orderCount", totalOrderCount);
        summary.put("orderAmount", totalOrderAmount);
        summary.put("playerIncome", totalPlayerIncome);
        summary.put("commissionIncome", totalCommissionIncome);

        Map<String, Object> data = new HashMap<>();
        data.put("startDate", resolvedStart);
        data.put("endDate", resolvedEnd);
        data.put("summary", summary);
        data.put("list", list);
        return R.ok(data);
    }

    private long toLong(Object value) {
        if (value == null) return 0L;
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(String.valueOf(value));
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        return new BigDecimal(String.valueOf(value));
    }
}
