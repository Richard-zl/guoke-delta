package com.delta.admin.controller;

import com.delta.common.domain.R;
import com.delta.common.dto.DashboardMoneyMetrics;
import com.delta.common.mapper.StatsMapper;
import com.delta.common.service.DashboardMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/home")
@RequiredArgsConstructor
public class AdminHomeController {
    private final StatsMapper statsMapper;
    private final DashboardMetricsService dashboardMetricsService;

    @GetMapping("/dashboard")
    public R<Map<String, Object>> dashboard() {
        Map<String, Object> data = new HashMap<>();
        String today = LocalDate.now().toString();
        String yesterday = LocalDate.now().minusDays(1).toString();

        DashboardMoneyMetrics todayM = dashboardMetricsService.metricsForDate(today);
        DashboardMoneyMetrics yestM = dashboardMetricsService.metricsForDate(yesterday);
        DashboardMoneyMetrics totalM = dashboardMetricsService.metricsTotal();

        // ====== 今日核心（经营口径） ======
        data.put("todayOrders", todayM.getPaidOrderCount());
        data.put("todayGmv", todayM.getGmv());
        data.put("todayRefundAmount", todayM.getRefundAmount());
        data.put("todayNetAmount", todayM.getNetAmount());
        // 兼容：旧字段语义升级为 GMV
        data.put("todayAmount", todayM.getGmv());
        data.put("todayNewUsers", statsMapper.countUsersByDate(today));
        data.put("todayNewPlayers", statsMapper.countPlayersByDate(today));

        // ====== 昨日对比 ======
        data.put("yesterdayOrders", yestM.getPaidOrderCount());
        data.put("yesterdayGmv", yestM.getGmv());
        data.put("yesterdayRefundAmount", yestM.getRefundAmount());
        data.put("yesterdayNetAmount", yestM.getNetAmount());
        data.put("yesterdayAmount", yestM.getGmv());
        data.put("yesterdayNewUsers", statsMapper.countUsersByDate(yesterday));

        // ====== 待办事项 ======
        data.put("pendingComplaints", statsMapper.countPendingComplaints());
        data.put("pendingWithdraws", statsMapper.countPendingWithdraws());
        data.put("pendingAssign", statsMapper.countPendingAssignOrders());
        data.put("inProgress", statsMapper.countInProgressOrders());

        // ====== 累计统计 ======
        data.put("totalUsers", statsMapper.countTotalUsers());
        data.put("totalPlayers", statsMapper.countTotalPlayers());
        data.put("totalOrders", totalM.getPaidOrderCount());
        data.put("totalGmv", totalM.getGmv());
        data.put("totalRefundAmount", totalM.getRefundAmount());
        data.put("totalNetAmount", totalM.getNetAmount());
        data.put("totalAmount", totalM.getGmv());

        // ====== 近7天经营趋势 ======
        data.put("orderTrend", dashboardMetricsService.trendLast7Days());

        // ====== 订单状态分布 ======
        data.put("statusDistribution", statsMapper.orderStatusDistribution());

        return R.ok(data);
    }
}
