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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 打手收入服务：定时释放待入账；仲裁退款先扣待入账再扣余额。
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
    @Transactional(rollbackFor = Exception.class)
    public void deductForOrderRefund(Order order, BigDecimal refundAmount) {
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (order == null || order.getPlayerId() == null) {
            return;
        }
        Integer settled = order.getSettled();
        if (settled == null || (settled != 1 && settled != 2)) {
            return;
        }

        BigDecimal remain = refundAmount;
        Long playerId = order.getPlayerId();

        // settled=2：先扣主打手本单剩余待入账
        if (settled == 2) {
            remain = deductPendingForPrimary(order, playerId, remain, refundAmount);
        }

        // 不足部分（或 settled=1）扣可提现余额
        if (remain.compareTo(BigDecimal.ZERO) > 0) {
            deductBalance(order, playerId, remain, refundAmount);
        }
    }

    /**
     * 从 PRIMARY 行剩余 settleAmount 与钱包 pendingBalance 同步扣回。
     *
     * @return 扣完待入账后仍需继续扣的金额
     */
    private BigDecimal deductPendingForPrimary(Order order, Long playerId,
                                              BigDecimal remain, BigDecimal refundAmount) {
        OrderPlayer primary = findPrimaryOrderPlayer(order.getId(), playerId);
        BigDecimal pendingPart = primary != null ? amountOrZero(primary.getSettleAmount()) : BigDecimal.ZERO;
        BigDecimal deductPending = remain.min(pendingPart);
        if (deductPending.compareTo(BigDecimal.ZERO) <= 0) {
            return remain;
        }

        PlayerWallet wallet = playerWalletService.getByPlayerId(playerId);
        if (wallet == null) {
            log.warn("退款待入账扣回失败: 打手钱包不存在, playerId={}, orderId={}", playerId, order.getId());
            return remain;
        }
        // 避免 pending 为负：实际扣额不超过当前 pendingBalance
        BigDecimal walletPending = amountOrZero(wallet.getPendingBalance());
        deductPending = deductPending.min(walletPending);
        if (deductPending.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("退款待入账扣回跳过: pending 不足, playerId={}, orderId={}, settleAmount={}, pending={}",
                    playerId, order.getId(), pendingPart, walletPending);
            return remain;
        }

        BigDecimal pendingBefore = walletPending;
        wallet.setPendingBalance(pendingBefore.subtract(deductPending));
        playerWalletService.updateById(wallet);

        primary.setSettleAmount(pendingPart.subtract(deductPending));
        orderPlayerService.updateById(primary);

        transactionService.record("REFUND", "PLAYER", playerId, deductPending.negate(),
                pendingBefore, wallet.getPendingBalance(), order.getId(), null, null,
                "投诉仲裁退款待入账扣回，订单金额退款：" + refundAmount);
        log.info("投诉仲裁退款，已从打手{}待入账扣除{}，orderId={}", playerId, deductPending, order.getId());
        return remain.subtract(deductPending);
    }

    /** 与现网一致：只扣可提现余额，不超过当前 balance。 */
    private void deductBalance(Order order, Long playerId, BigDecimal remain, BigDecimal refundAmount) {
        PlayerWallet wallet = playerWalletService.getByPlayerId(playerId);
        if (wallet == null) {
            log.warn("退款扣款失败: 打手钱包不存在, playerId={}, orderId={}", playerId, order.getId());
            return;
        }
        BigDecimal balanceBefore = amountOrZero(wallet.getBalance());
        BigDecimal deduction = remain.min(balanceBefore);
        if (deduction.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        wallet.setBalance(balanceBefore.subtract(deduction));
        playerWalletService.updateById(wallet);
        transactionService.record("REFUND", "PLAYER", playerId, deduction.negate(),
                balanceBefore, wallet.getBalance(), order.getId(), null, null,
                "投诉仲裁退款扣除收益，订单金额退款：" + refundAmount);
        log.info("投诉仲裁退款，已从打手{}钱包扣除{}，orderId={}", playerId, deduction, order.getId());
    }

    /** PRIMARY + playerId，取最新一条（与结算监听一致）。 */
    private OrderPlayer findPrimaryOrderPlayer(Long orderId, Long playerId) {
        List<OrderPlayer> list = orderPlayerService.list(new LambdaQueryWrapper<OrderPlayer>()
                .eq(OrderPlayer::getOrderId, orderId)
                .eq(OrderPlayer::getPlayerId, playerId)
                .eq(OrderPlayer::getRole, "PRIMARY")
                .orderByDesc(OrderPlayer::getId)
                .last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    private record ReleaseItem(OrderPlayer op, PlayerWallet wallet, BigDecimal amount) {}
}
