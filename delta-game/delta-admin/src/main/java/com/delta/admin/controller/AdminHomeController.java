package com.delta.admin.controller;

import com.delta.common.domain.R;
import com.delta.common.mapper.StatsMapper;
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

    @GetMapping("/dashboard")
    public R<Map<String, Object>> dashboard() {
        Map<String, Object> data = new HashMap<>();
        String today = LocalDate.now().toString();
        String yesterday = LocalDate.now().minusDays(1).toString();

        // ====== 今日核心 ======
        data.put("todayOrders", statsMapper.countOrdersByDate(today));
        data.put("todayAmount", statsMapper.sumOrderAmountByDate(today));
        data.put("todayNewUsers", statsMapper.countUsersByDate(today));
        data.put("todayNewPlayers", statsMapper.countPlayersByDate(today));

        // ====== 昨日对比 ======
        data.put("yesterdayOrders", statsMapper.countOrdersByDate(yesterday));
        data.put("yesterdayAmount", statsMapper.sumOrderAmountByDate(yesterday));
        data.put("yesterdayNewUsers", statsMapper.countUsersByDate(yesterday));

        // ====== 待办事项 ======
        data.put("pendingComplaints", statsMapper.countPendingComplaints());
        data.put("pendingWithdraws", statsMapper.countPendingWithdraws());
        data.put("pendingAssign", statsMapper.countPendingAssignOrders());
        data.put("inProgress", statsMapper.countInProgressOrders());

        // ====== 累计统计 ======
        data.put("totalUsers", statsMapper.countTotalUsers());
        data.put("totalPlayers", statsMapper.countTotalPlayers());
        data.put("totalOrders", statsMapper.countTotalOrders());
        data.put("totalAmount", statsMapper.sumTotalOrderAmount());

        // ====== 近7天订单趋势 ======
        data.put("orderTrend", statsMapper.orderTrend7Days());

        // ====== 订单状态分布 ======
        data.put("statusDistribution", statsMapper.orderStatusDistribution());

        return R.ok(data);
    }
}
