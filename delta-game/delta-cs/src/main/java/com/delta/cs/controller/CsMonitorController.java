package com.delta.cs.controller;

import com.delta.common.domain.R;
import com.delta.common.mapper.StatsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 客服端实时监控
 */
@RestController
@RequestMapping("/cs/monitor")
@RequiredArgsConstructor
public class CsMonitorController {
    private final StatsMapper statsMapper;

    /** 实时监控数据 */
    @GetMapping("/realtime")
    public R<Map<String, Object>> realtime() {
        Map<String, Object> data = new HashMap<>();
        data.put("inProgressOrders", statsMapper.countInProgressOrders());
        data.put("pendingAssignOrders", statsMapper.countPendingAssignOrders());
        data.put("pendingComplaints", statsMapper.countPendingProcessingComplaints());
        return R.ok(data);
    }

    /** 今日概览 */
    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        Map<String, Object> data = new HashMap<>();
        String today = LocalDate.now().toString();

        data.put("todayOrders", statsMapper.countOrdersByDate(today));
        data.put("todayCompleted", statsMapper.countCompletedOrdersByDate(today));
        data.put("todayComplaints", statsMapper.countComplaintsByDate(today));
        data.put("todayResolvedComplaints", statsMapper.countResolvedComplaintsByDate(today));
        data.put("todayIncome", statsMapper.sumOrderAmountByDate(today));
        return R.ok(data);
    }
}
