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

    /** 收益日报：确认口径（含待入账）+ 已入账口径 */
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

        Map<String, Map<String, Object>> confirmMap =
                indexByStatDate(statsMapper.incomeDailyConfirmStatsByRange(start, end));
        Map<String, Map<String, Object>> settledMap =
                indexByStatDate(statsMapper.incomeDailyStatsByRange(start, end));
        Map<String, List<Map<String, Object>>> confirmDetailMap =
                groupDetailsByStatDate(statsMapper.incomeDailyConfirmOrderDetailsByRange(start, end));
        Map<String, List<Map<String, Object>>> settledDetailMap =
                groupDetailsByStatDate(statsMapper.incomeDailyOrderDetailsByRange(start, end));

        List<Map<String, Object>> list = new ArrayList<>();
        long confirmTotalCount = 0L;
        BigDecimal confirmTotalAmount = BigDecimal.ZERO;
        BigDecimal confirmTotalPlayer = BigDecimal.ZERO;
        BigDecimal confirmTotalCommission = BigDecimal.ZERO;
        long settledTotalCount = 0L;
        BigDecimal settledTotalAmount = BigDecimal.ZERO;
        BigDecimal settledTotalPlayer = BigDecimal.ZERO;
        BigDecimal settledTotalCommission = BigDecimal.ZERO;

        for (LocalDate date = resolvedStart; !date.isAfter(resolvedEnd); date = date.plusDays(1)) {
            String key = date.toString();
            Map<String, Object> confirmRaw = confirmMap.get(key);
            Map<String, Object> settledRaw = settledMap.get(key);

            long confirmOrderCount = metricLong(confirmRaw, "orderCount");
            BigDecimal confirmOrderAmount = metricDecimal(confirmRaw, "orderAmount");
            BigDecimal confirmPlayerIncome = metricDecimal(confirmRaw, "playerIncome");
            BigDecimal confirmCommissionIncome = metricDecimal(confirmRaw, "commissionIncome");

            long settledOrderCount = metricLong(settledRaw, "orderCount");
            BigDecimal settledOrderAmount = metricDecimal(settledRaw, "orderAmount");
            BigDecimal settledPlayerIncome = metricDecimal(settledRaw, "playerIncome");
            BigDecimal settledCommissionIncome = metricDecimal(settledRaw, "commissionIncome");

            List<Map<String, Object>> confirmOrders = confirmDetailMap.getOrDefault(key, new ArrayList<>());
            List<Map<String, Object>> settledOrders = settledDetailMap.getOrDefault(key, new ArrayList<>());

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("statDate", key);
            item.put("confirmOrderCount", confirmOrderCount);
            item.put("confirmOrderAmount", confirmOrderAmount);
            item.put("confirmPlayerIncome", confirmPlayerIncome);
            item.put("confirmCommissionIncome", confirmCommissionIncome);
            item.put("settledOrderCount", settledOrderCount);
            item.put("settledOrderAmount", settledOrderAmount);
            item.put("settledPlayerIncome", settledPlayerIncome);
            item.put("settledCommissionIncome", settledCommissionIncome);
            // 兼容旧字段：映射为确认口径
            item.put("orderCount", confirmOrderCount);
            item.put("orderAmount", confirmOrderAmount);
            item.put("playerIncome", confirmPlayerIncome);
            item.put("commissionIncome", confirmCommissionIncome);
            item.put("confirmOrders", confirmOrders);
            item.put("settledOrders", settledOrders);
            item.put("orders", confirmOrders);
            list.add(item);

            confirmTotalCount += confirmOrderCount;
            confirmTotalAmount = confirmTotalAmount.add(confirmOrderAmount);
            confirmTotalPlayer = confirmTotalPlayer.add(confirmPlayerIncome);
            confirmTotalCommission = confirmTotalCommission.add(confirmCommissionIncome);
            settledTotalCount += settledOrderCount;
            settledTotalAmount = settledTotalAmount.add(settledOrderAmount);
            settledTotalPlayer = settledTotalPlayer.add(settledPlayerIncome);
            settledTotalCommission = settledTotalCommission.add(settledCommissionIncome);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("confirmOrderCount", confirmTotalCount);
        summary.put("confirmOrderAmount", confirmTotalAmount);
        summary.put("confirmPlayerIncome", confirmTotalPlayer);
        summary.put("confirmCommissionIncome", confirmTotalCommission);
        summary.put("settledOrderCount", settledTotalCount);
        summary.put("settledOrderAmount", settledTotalAmount);
        summary.put("settledPlayerIncome", settledTotalPlayer);
        summary.put("settledCommissionIncome", settledTotalCommission);
        summary.put("orderCount", confirmTotalCount);
        summary.put("orderAmount", confirmTotalAmount);
        summary.put("playerIncome", confirmTotalPlayer);
        summary.put("commissionIncome", confirmTotalCommission);

        Map<String, Object> data = new HashMap<>();
        data.put("startDate", resolvedStart);
        data.put("endDate", resolvedEnd);
        data.put("summary", summary);
        data.put("list", list);
        return R.ok(data);
    }

    private Map<String, Map<String, Object>> indexByStatDate(List<Map<String, Object>> rows) {
        Map<String, Map<String, Object>> map = new HashMap<>();
        if (rows == null) return map;
        for (Map<String, Object> row : rows) {
            String key = normalizeDateKey(row.get("statDate"));
            if (!key.isEmpty()) map.put(key, row);
        }
        return map;
    }

    private Map<String, List<Map<String, Object>>> groupDetailsByStatDate(List<Map<String, Object>> rows) {
        Map<String, List<Map<String, Object>>> map = new HashMap<>();
        if (rows == null) return map;
        for (Map<String, Object> row : rows) {
            String key = normalizeDateKey(row.get("statDate"));
            if (key.isEmpty()) continue;
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }
        return map;
    }

    private String normalizeDateKey(Object value) {
        if (value == null) return "";
        if (value instanceof java.sql.Date d) return d.toLocalDate().toString();
        if (value instanceof LocalDate d) return d.toString();
        if (value instanceof LocalDateTime dt) return dt.toLocalDate().toString();
        if (value instanceof java.util.Date d) {
            return d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString();
        }
        String s = String.valueOf(value);
        return s.length() >= 10 ? s.substring(0, 10) : s;
    }

    private long metricLong(Map<String, Object> raw, String field) {
        return raw != null ? toLong(raw.get(field)) : 0L;
    }

    private BigDecimal metricDecimal(Map<String, Object> raw, String field) {
        return raw != null ? toBigDecimal(raw.get(field)) : BigDecimal.ZERO;
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
