package com.delta.player.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.delta.common.domain.R;
import com.delta.common.security.utils.SecurityUtils;
import com.delta.order.entity.Order;
import com.delta.order.entity.OrderPlayer;
import com.delta.order.service.OrderPlayerService;
import com.delta.order.service.OrderService;
import com.delta.pay.entity.Transaction;
import com.delta.pay.service.TransactionService;
import com.delta.player.entity.PlayerWallet;
import com.delta.player.service.PlayerWalletService;
import com.delta.common.mapper.CrossModuleMapper;
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
 * 打手端首页聚合接口
 */
@RestController
@RequestMapping("/player/home")
@RequiredArgsConstructor
public class PlayerHomeController {
    private final OrderService orderService;
    private final PlayerWalletService playerWalletService;
    private final TransactionService transactionService;
    private final OrderPlayerService orderPlayerService;
    private final CrossModuleMapper crossModuleMapper;

    @GetMapping
    public R<Map<String, Object>> home() {
        Long playerId = SecurityUtils.getUserId();
        Map<String, Object> result = new HashMap<>();

        // 1. 钱包信息
        PlayerWallet wallet = playerWalletService.getByPlayerId(playerId);
        result.put("wallet", wallet);

        // 2. 今日统计（今日完成单数 & 今日收入）
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime todayEnd = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
        long todayCompleted = orderService.count(new LambdaQueryWrapper<Order>()
                .eq(Order::getPlayerId, playerId)
                .in(Order::getStatus, "CONFIRMED", "REVIEWED")
                .between(Order::getCompleteTime, todayStart, todayEnd));
        result.put("todayCompleted", todayCompleted);

        // 今日接单数
        long todayAccepted = orderService.count(new LambdaQueryWrapper<Order>()
                .eq(Order::getPlayerId, playerId)
                .isNotNull(Order::getAcceptTime)
                .between(Order::getAcceptTime, todayStart, todayEnd));
        result.put("todayAccepted", todayAccepted);

        // 今日收益（从transaction表统计）
        List<Transaction> todayTxns = transactionService.list(new LambdaQueryWrapper<Transaction>()
                .eq(Transaction::getUserType, "PLAYER")
                .eq(Transaction::getUserId, playerId)
                .eq(Transaction::getType, "INCOME")
                .between(Transaction::getCreatedAt, todayStart, todayEnd));
        BigDecimal todayIncome = todayTxns.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        result.put("todayIncome", todayIncome);

        // 3. 待处理事项
        long pendingAccept = orderService.count(new LambdaQueryWrapper<Order>()
                .eq(Order::getPlayerId, playerId)
                .eq(Order::getStatus, "ASSIGNED"));
        long inProgress = orderService.count(new LambdaQueryWrapper<Order>()
                .eq(Order::getPlayerId, playerId)
                .eq(Order::getStatus, "IN_PROGRESS"));
        result.put("pendingAccept", pendingAccept);
        result.put("inProgress", inProgress);

        // 待响应的队友邀请数
        long pendingInvites = orderPlayerService.count(new LambdaQueryWrapper<OrderPlayer>()
                .eq(OrderPlayer::getPlayerId, playerId)
                .eq(OrderPlayer::getStatus, "INVITED"));
        result.put("pendingInvites", pendingInvites);

        // 待处理投诉数
        Long pendingComplaints = crossModuleMapper.selectPendingComplaintCount(playerId);
        result.put("pendingComplaints", pendingComplaints != null ? pendingComplaints : 0);

        // 4. 可接单预览（PAID状态且未指定打手，取前5）
        List<Order> availableOrders = orderService.list(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, "PAID")
                .isNull(Order::getDesignatedPlayerId)
                .orderByDesc(Order::getCreatedAt)
                .last("LIMIT 5"));
        result.put("availableOrders", availableOrders);

        return R.ok(result);
    }
}
