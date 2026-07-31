package com.delta.player.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.delta.common.event.BusinessEvent;
import com.delta.order.entity.Order;
import com.delta.order.entity.OrderPlayer;
import com.delta.order.service.OrderPlayerService;
import com.delta.order.service.OrderService;
import com.delta.pay.service.TransactionService;
import com.delta.player.entity.PlayerWallet;
import com.delta.player.service.PlayerIncomeService;
import com.delta.player.service.PlayerWalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 打手收入服务：定时释放待入账；仲裁扣回由 Task 5 补全。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerIncomeServiceImpl implements PlayerIncomeService {

    private final OrderService orderService;
    private final OrderPlayerService orderPlayerService;
    private final PlayerWalletService playerWalletService;
    private final TransactionService transactionService;
    private final ApplicationEventPublisher eventPublisher;
    private final PlatformTransactionManager transactionManager;

    @Override
    public int releaseDueSettlements(int limit) {
        int batchSize = Math.max(1, limit);
        List<Order> due = orderService.list(new LambdaQueryWrapper<Order>()
                .eq(Order::getSettled, 2)
                .le(Order::getSettleAvailableAt, LocalDateTime.now())
                .orderByAsc(Order::getSettleAvailableAt)
                .last("LIMIT " + batchSize));
        if (due.isEmpty()) {
            return 0;
        }

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        int ok = 0;
        for (Order order : due) {
            Boolean success = tx.execute(status -> {
                try {
                    return releaseOne(order.getId());
                } catch (Exception e) {
                    log.error("释放待入账异常，整单回滚: orderId={}", order.getId(), e);
                    status.setRollbackOnly();
                    return false;
                }
            });
            if (Boolean.TRUE.equals(success)) {
                ok++;
            }
        }
        return ok;
    }

    /**
     * 单笔订单释放（在调用方事务中执行）。
     *
     * @return true 已入账或零额结清；false 跳过（不足/状态不符）
     */
    private boolean releaseOne(Long orderId) {
        Order order = orderService.getById(orderId);
        if (order == null || order.getSettled() == null || order.getSettled() != 2) {
            return false;
        }
        if (order.getSettleAvailableAt() != null
                && order.getSettleAvailableAt().isAfter(LocalDateTime.now())) {
            return false;
        }

        List<OrderPlayer> players = listReleasePlayers(orderId);
        LocalDateTime now = LocalDateTime.now();

        if (players.isEmpty()) {
            log.error("待入账释放跳过: order_player 行缺失, orderId={}", orderId);
            return false;
        }

        // 全部 settleAmount 为 0/null：只改订单状态
        boolean allZero = players.stream().allMatch(op -> amountOrZero(op.getSettleAmount()).signum() <= 0);
        if (allZero) {
            order.setSettled(1);
            order.setSettleTime(now);
            orderService.updateById(order);
            log.info("待入账零额结清: orderId={}", orderId);
            return true;
        }

        // 预检：每人 pendingBalance >= settleAmount
        List<ReleaseItem> items = new ArrayList<>();
        for (OrderPlayer op : players) {
            BigDecimal amount = amountOrZero(op.getSettleAmount());
            if (amount.signum() <= 0) {
                continue;
            }
            PlayerWallet wallet = playerWalletService.getByPlayerId(op.getPlayerId());
            if (wallet == null) {
                log.error("待入账释放跳过: 钱包不存在, orderId={}, playerId={}, settleAmount={}",
                        orderId, op.getPlayerId(), amount);
                return false;
            }
            BigDecimal pending = amountOrZero(wallet.getPendingBalance());
            if (pending.compareTo(amount) < 0) {
                log.error("待入账释放跳过: pending 不足, orderId={}, playerId={}, pending={}, settleAmount={}",
                        orderId, op.getPlayerId(), pending, amount);
                return false;
            }
            items.add(new ReleaseItem(op, wallet, amount));
        }

        // 入账：pending→balance，写 INCOME，发通知
        for (ReleaseItem item : items) {
            PlayerWallet wallet = item.wallet;
            BigDecimal amount = item.amount;
            BigDecimal pending = amountOrZero(wallet.getPendingBalance());
            BigDecimal balanceBefore = amountOrZero(wallet.getBalance());
            BigDecimal totalIncome = amountOrZero(wallet.getTotalIncome());

            wallet.setPendingBalance(pending.subtract(amount));
            wallet.setBalance(balanceBefore.add(amount));
            wallet.setTotalIncome(totalIncome.add(amount));
            playerWalletService.updateById(wallet);

            transactionService.record("INCOME", "PLAYER", item.op.getPlayerId(), amount,
                    balanceBefore, wallet.getBalance(), orderId, null, null, "待入账到期释放");

            item.op.setSettledAt(now);
            orderPlayerService.updateById(item.op);

            eventPublisher.publishEvent(new BusinessEvent(this, "INCOME_SETTLED",
                    "PLAYER", item.op.getPlayerId(), orderId,
                    "订单已结算，您的分成收入 " + amount.toPlainString() + " 元已到账"));
        }

        order.setSettled(1);
        order.setSettleTime(now);
        orderService.updateById(order);
        log.info("待入账释放成功: orderId={}, players={}", orderId, items.size());
        return true;
    }

    /**
     * PRIMARY + ACCEPTED TEAMMATE（按本单剩余 settleAmount 入账）。
     */
    private List<OrderPlayer> listReleasePlayers(Long orderId) {
        List<OrderPlayer> all = orderPlayerService.list(new LambdaQueryWrapper<OrderPlayer>()
                .eq(OrderPlayer::getOrderId, orderId)
                .in(OrderPlayer::getRole, "PRIMARY", "TEAMMATE"));
        List<OrderPlayer> result = new ArrayList<>();
        for (OrderPlayer op : all) {
            if ("PRIMARY".equals(op.getRole())) {
                result.add(op);
            } else if ("TEAMMATE".equals(op.getRole()) && "ACCEPTED".equals(op.getStatus())) {
                result.add(op);
            }
        }
        return result;
    }

    private static BigDecimal amountOrZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    @Override
    public void deductForOrderRefund(Order order, BigDecimal refundAmount) {
        // Task 5 补全：settled=2 先扣 pending/settleAmount，再扣 balance
        log.warn("deductForOrderRefund 尚未实现, orderId={}, refundAmount={}",
                order != null ? order.getId() : null, refundAmount);
    }

    private record ReleaseItem(OrderPlayer op, PlayerWallet wallet, BigDecimal amount) {}
}
