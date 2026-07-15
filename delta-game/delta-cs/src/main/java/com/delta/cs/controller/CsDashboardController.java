package com.delta.cs.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.delta.common.domain.R;
import com.delta.order.entity.Complaint;
import com.delta.order.entity.Order;
import com.delta.order.service.ComplaintService;
import com.delta.order.service.OrderService;
import com.delta.player.entity.Player;
import com.delta.player.service.PlayerService;
import com.delta.common.mapper.StatsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客服工作台Dashboard
 */
@RestController
@RequestMapping("/cs/dashboard")
@RequiredArgsConstructor
public class CsDashboardController {
    private final OrderService orderService;
    private final ComplaintService complaintService;
    private final PlayerService playerService;
    private final StatsMapper statsMapper;

    @GetMapping
    public R<Map<String, Object>> dashboard() {
        Map<String, Object> result = new HashMap<>();
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime todayEnd = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);

        // 1. 待指派订单数（PAID状态，未分配打手）
        long pendingAssign = orderService.count(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, "PAID")
                .isNull(Order::getPlayerId));
        result.put("pendingAssign", pendingAssign);

        // 2. 待处理投诉数（PENDING状态）
        long pendingComplaints = complaintService.count(new LambdaQueryWrapper<Complaint>()
                .eq(Complaint::getStatus, "PENDING"));
        result.put("pendingComplaints", pendingComplaints);

        // 3. 进行中仲裁数（PROCESSING状态）
        long processingComplaints = complaintService.count(new LambdaQueryWrapper<Complaint>()
                .eq(Complaint::getStatus, "PROCESSING"));
        result.put("processingComplaints", processingComplaints);

        // 3. 今日统计
        long todayOrders = orderService.count(new LambdaQueryWrapper<Order>()
                .between(Order::getCreatedAt, todayStart, todayEnd));
        long todayCompleted = orderService.count(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, "COMPLETED")
                .between(Order::getCompleteTime, todayStart, todayEnd));
        long todayComplaints = complaintService.count(new LambdaQueryWrapper<Complaint>()
                .between(Complaint::getCreatedAt, todayStart, todayEnd));
        result.put("todayOrders", todayOrders);
        result.put("todayCompleted", todayCompleted);
        result.put("todayComplaints", todayComplaints);

        // 4b. 今日成交额
        result.put("todayAmount", statsMapper.sumOrderAmountByDateRange(todayStart, todayEnd));

        // 4c. 在线打手数（ACTIVE状态打手数作为近似）
        long activePlayers = playerService.count(new LambdaQueryWrapper<Player>()
                .eq(Player::getStatus, "ACTIVE"));
        result.put("activePlayers", activePlayers);

        // 4d. 待回复聊天会话数（有未读消息的客服聊天会话）
        try {
            Long pendingChatSessions = statsMapper.countPendingChatSessions();
            result.put("pendingChatSessions", pendingChatSessions != null ? pendingChatSessions : 0L);
        } catch (Exception e) {
            result.put("pendingChatSessions", 0L);
        }

        // 5. 待审核提现
        result.put("pendingWithdraws", statsMapper.countPendingWithdraws());

        // 6. 进行中订单
        long inProgress = orderService.count(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, "IN_PROGRESS"));
        result.put("inProgress", inProgress);

        // 7. 总用户数 & 总打手数
        result.put("totalUsers", statsMapper.countTotalUsers());
        result.put("totalPlayers", statsMapper.countTotalPlayers());

        // 8. 待指派订单列表预览（取前10）
        List<Order> pendingOrders = orderService.list(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, "PAID")
                .isNull(Order::getPlayerId)
                .orderByAsc(Order::getCreatedAt)
                .last("LIMIT 10"));
        result.put("pendingOrders", pendingOrders);

        return R.ok(result);
    }
}
