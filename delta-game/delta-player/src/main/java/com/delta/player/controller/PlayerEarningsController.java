package com.delta.player.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.domain.PageQuery;
import com.delta.common.domain.R;
import com.delta.common.security.utils.SecurityUtils;
import com.delta.order.entity.Order;
import com.delta.order.service.OrderService;
import com.delta.pay.entity.Transaction;
import com.delta.pay.service.TransactionService;
import com.delta.player.entity.PlayerWallet;
import com.delta.player.service.PlayerWalletService;
import com.delta.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 打手收益统计
 */
@RestController
@RequestMapping("/player/earnings")
@RequiredArgsConstructor
public class PlayerEarningsController {
    private final TransactionService transactionService;
    private final PlayerWalletService playerWalletService;
    private final OrderService orderService;
    private final SysConfigService sysConfigService;

    /** 收益汇总 */
    @GetMapping("/summary")
    public R<Map<String, Object>> summary() {
        Long playerId = SecurityUtils.getUserId();
        Map<String, Object> data = new HashMap<>();

        // 钱包
        PlayerWallet wallet = playerWalletService.getByPlayerId(playerId);
        data.put("balance", wallet != null ? wallet.getBalance() : BigDecimal.ZERO);
        data.put("frozenAmount", wallet != null ? wallet.getFrozenAmount() : BigDecimal.ZERO);
        data.put("totalIncome", wallet != null ? wallet.getTotalIncome() : BigDecimal.ZERO);
        data.put("pendingBalance", wallet != null && wallet.getPendingBalance() != null
                ? wallet.getPendingBalance() : BigDecimal.ZERO);
        data.put("delayDays", Integer.parseInt(
                sysConfigService.getConfigValue("settlement.delay_days", "5")));

        // 今日收益
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime todayEnd = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
        List<Transaction> todayTxns = transactionService.list(new LambdaQueryWrapper<Transaction>()
                .eq(Transaction::getUserType, "PLAYER")
                .eq(Transaction::getUserId, playerId)
                .eq(Transaction::getType, "INCOME")
                .between(Transaction::getCreatedAt, todayStart, todayEnd));
        BigDecimal todayIncome = todayTxns.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        data.put("todayIncome", todayIncome);

        // 本周收益
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.minusDays(today.getDayOfWeek().getValue() - 1);
        List<Transaction> weekTxns = transactionService.list(new LambdaQueryWrapper<Transaction>()
                .eq(Transaction::getUserType, "PLAYER")
                .eq(Transaction::getUserId, playerId)
                .eq(Transaction::getType, "INCOME")
                .between(Transaction::getCreatedAt, LocalDateTime.of(weekStart, LocalTime.MIN), todayEnd));
        BigDecimal weekIncome = weekTxns.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        data.put("weekIncome", weekIncome);

        // 本月收益
        LocalDateTime monthStart = LocalDateTime.of(LocalDate.now().withDayOfMonth(1), LocalTime.MIN);
        List<Transaction> monthTxns = transactionService.list(new LambdaQueryWrapper<Transaction>()
                .eq(Transaction::getUserType, "PLAYER")
                .eq(Transaction::getUserId, playerId)
                .eq(Transaction::getType, "INCOME")
                .between(Transaction::getCreatedAt, monthStart, todayEnd));
        BigDecimal monthIncome = monthTxns.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        data.put("monthIncome", monthIncome);

        // --- 订单统计 ---
        // 累计订单（已完成/已确认/已评价）
        long totalOrders = orderService.count(new LambdaQueryWrapper<Order>()
                .eq(Order::getPlayerId, playerId)
                .in(Order::getStatus, "COMPLETED", "CONFIRMED", "REVIEWED"));
        data.put("totalOrders", totalOrders);

        // 本月订单
        long monthOrders = orderService.count(new LambdaQueryWrapper<Order>()
                .eq(Order::getPlayerId, playerId)
                .in(Order::getStatus, "COMPLETED", "CONFIRMED", "REVIEWED")
                .ge(Order::getCreatedAt, monthStart));
        data.put("monthOrders", monthOrders);

        // 单均收益
        BigDecimal totalIncomeVal = wallet != null && wallet.getTotalIncome() != null ? wallet.getTotalIncome() : BigDecimal.ZERO;
        BigDecimal avgOrderAmount = totalOrders > 0
                ? totalIncomeVal.divide(BigDecimal.valueOf(totalOrders), 2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        data.put("avgOrderAmount", avgOrderAmount);

        // 完成率 = 已完成订单 / 全部接过的订单（排除CANCELLED之外的所有）
        long allOrders = orderService.count(new LambdaQueryWrapper<Order>()
                .eq(Order::getPlayerId, playerId)
                .ne(Order::getStatus, "CANCELLED"));
        String completionRate = allOrders > 0
                ? String.valueOf(totalOrders * 100 / allOrders)
                : "0";
        data.put("completionRate", completionRate);

        return R.ok(data);
    }

    /** 收益明细（支持日期范围过滤） */
    @GetMapping("/detail")
    public R<Page<Transaction>> detail(PageQuery query,
                                        @RequestParam(required = false) String startDate,
                                        @RequestParam(required = false) String endDate) {
        Long playerId = SecurityUtils.getUserId();
        LambdaQueryWrapper<Transaction> w = new LambdaQueryWrapper<Transaction>()
                .eq(Transaction::getUserType, "PLAYER")
                .eq(Transaction::getUserId, playerId);
        if (startDate != null && !startDate.isEmpty()) {
            w.ge(Transaction::getCreatedAt, LocalDateTime.of(LocalDate.parse(startDate), LocalTime.MIN));
        }
        if (endDate != null && !endDate.isEmpty()) {
            w.le(Transaction::getCreatedAt, LocalDateTime.of(LocalDate.parse(endDate), LocalTime.MAX));
        }
        w.orderByDesc(Transaction::getCreatedAt);
        return R.ok(transactionService.page(new Page<>(query.getPageNum(), query.getPageSize()), w));
    }
}
