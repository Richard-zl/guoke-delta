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
import com.delta.player.service.PlayerReferralRules;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        BigDecimal referralRemain = PlayerReferralRules.remaining(
                order.getReferralAmount(), order.getReferralClawedAmount());
        boolean releaseReferral = order.getReferralPlayerId() != null && referralRemain.signum() > 0;

        // 全部 settleAmount 为 0/null 且没有介绍费：只改订单状态
        boolean allZero = players.stream().allMatch(op -> amountOrZero(op.getSettleAmount()).signum() <= 0);
        if (allZero && !releaseReferral) {
            order.setSettled(1);
            order.setSettleTime(now);
            orderService.updateById(order);
            log.info("待入账零额结清: orderId={}", orderId);
            return true;
        }

        // 预检：同一人的接单收入和介绍费加在一起，pending 必须够
        Map<Long, BigDecimal> required = new HashMap<>();
        List<OrderPlayer> paying = new ArrayList<>();
        for (OrderPlayer op : players) {
            BigDecimal amount = amountOrZero(op.getSettleAmount());
            if (amount.signum() <= 0) {
                continue;
            }
            required.merge(op.getPlayerId(), amount, BigDecimal::add);
            paying.add(op);
        }
        if (releaseReferral) {
            required.merge(order.getReferralPlayerId(), referralRemain, BigDecimal::add);
        }

        Map<Long, PlayerWallet> wallets = new HashMap<>();
        for (Map.Entry<Long, BigDecimal> entry : required.entrySet()) {
            PlayerWallet wallet = playerWalletService.getByPlayerId(entry.getKey());
            if (wallet == null) {
                log.error("待入账释放跳过: 钱包不存在, orderId={}, playerId={}, need={}",
                        orderId, entry.getKey(), entry.getValue());
                return false;
            }
            BigDecimal pending = amountOrZero(wallet.getPendingBalance());
            if (pending.compareTo(entry.getValue()) < 0) {
                log.error("待入账释放跳过: pending 不足, orderId={}, playerId={}, pending={}, need={}",
                        orderId, entry.getKey(), pending, entry.getValue());
                return false;
            }
            wallets.put(entry.getKey(), wallet);
        }

        List<ReleaseItem> items = new ArrayList<>();
        for (OrderPlayer op : paying) {
            items.add(new ReleaseItem(op, wallets.get(op.getPlayerId()), amountOrZero(op.getSettleAmount())));
        }

        // 入账：pending→balance，写 INCOME，发通知。同一钱包对象连续扣，介绍费不会被接单收入覆盖。
        for (ReleaseItem item : items) {
            PlayerWallet wallet = item.wallet();
            BigDecimal amount = item.amount();
            BigDecimal pending = amountOrZero(wallet.getPendingBalance());
            BigDecimal balanceBefore = amountOrZero(wallet.getBalance());
            BigDecimal totalIncome = amountOrZero(wallet.getTotalIncome());

            wallet.setPendingBalance(pending.subtract(amount));
            wallet.setBalance(balanceBefore.add(amount));
            wallet.setTotalIncome(totalIncome.add(amount));
            playerWalletService.updateById(wallet);

            transactionService.record("INCOME", "PLAYER", item.op().getPlayerId(), amount,
                    balanceBefore, wallet.getBalance(), orderId, null, null, "待入账到期释放");

            item.op().setSettledAt(now);
            orderPlayerService.updateById(item.op());

            eventPublisher.publishEvent(new BusinessEvent(this, "INCOME_SETTLED",
                    "PLAYER", item.op().getPlayerId(), orderId,
                    "订单已结算，您的分成收入 " + amount.toPlainString() + " 元已到账"));
        }

        if (releaseReferral) {
            PlayerWallet wallet = wallets.get(order.getReferralPlayerId());
            BigDecimal pending = amountOrZero(wallet.getPendingBalance());
            BigDecimal balanceBefore = amountOrZero(wallet.getBalance());
            wallet.setPendingBalance(pending.subtract(referralRemain));
            wallet.setBalance(balanceBefore.add(referralRemain));
            wallet.setTotalIncome(amountOrZero(wallet.getTotalIncome()).add(referralRemain));
            playerWalletService.updateById(wallet);
            String remark = "转介绍分成，订单" + order.getOrderNo() + "，介绍费¥" + referralRemain.toPlainString();
            transactionService.record(PlayerReferralRules.TX_REFERRAL, "PLAYER", order.getReferralPlayerId(),
                    referralRemain, balanceBefore, wallet.getBalance(), orderId, null, null, remark);
            eventPublisher.publishEvent(new BusinessEvent(this, "REFERRAL_SETTLED",
                    "PLAYER", order.getReferralPlayerId(), orderId,
                    "转介绍分成 " + referralRemain.toPlainString() + " 元已到账"));
        }

        order.setSettled(1);
        order.setSettleTime(now);
        orderService.updateById(order);
        log.info("待入账释放成功: orderId={}, players={}, referral={}", orderId, items.size(), referralRemain);
        return true;
    }

    /**
     * 最新 PRIMARY（LIMIT 1）+ 全部 ACCEPTED TEAMMATE（按本单剩余 settleAmount 入账）。
     */
    private List<OrderPlayer> listReleasePlayers(Long orderId) {
        List<OrderPlayer> result = new ArrayList<>();
        // 仅取最新 PRIMARY，与结算记待入账时一致，避免重复入账
        List<OrderPlayer> primaryOps = orderPlayerService.list(new LambdaQueryWrapper<OrderPlayer>()
                .eq(OrderPlayer::getOrderId, orderId)
                .eq(OrderPlayer::getRole, "PRIMARY")
                .orderByDesc(OrderPlayer::getId)
                .last("LIMIT 1"));
        result.addAll(primaryOps);
        List<OrderPlayer> teammates = orderPlayerService.list(new LambdaQueryWrapper<OrderPlayer>()
                .eq(OrderPlayer::getOrderId, orderId)
                .eq(OrderPlayer::getRole, "TEAMMATE")
                .eq(OrderPlayer::getStatus, "ACCEPTED"));
        result.addAll(teammates);
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
        clawReferral(order, refundAmount);
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

    /**
     * 按退款占订单金额的比例扣回介绍费。
     * 待入账时扣 pending，已入账时扣可提现余额。扣不到的部分不再补扣。
     */
    private void clawReferral(Order order, BigDecimal refundAmount) {
        if (order.getReferralPlayerId() == null) {
            return;
        }
        BigDecimal due = PlayerReferralRules.clawbackDue(
                order.getAmount(), order.getReferralAmount(), order.getReferralClawedAmount(), refundAmount);
        if (due.signum() <= 0) {
            return;
        }
        boolean pendingPhase = order.getSettled() != null && order.getSettled() == 2;
        PlayerWallet wallet = playerWalletService.getByPlayerId(order.getReferralPlayerId());
        if (wallet == null) {
            log.warn("转介绍扣回跳过: 钱包不存在, playerId={}, orderId={}", order.getReferralPlayerId(), order.getId());
            return;
        }
        BigDecimal before = pendingPhase
                ? amountOrZero(wallet.getPendingBalance())
                : amountOrZero(wallet.getBalance());
        BigDecimal actual = due.min(before);
        if (actual.signum() <= 0) {
            log.warn("转介绍扣回跳过: 余额不足, playerId={}, orderId={}, due={}",
                    order.getReferralPlayerId(), order.getId(), due);
            return;
        }
        if (pendingPhase) {
            wallet.setPendingBalance(before.subtract(actual));
        } else {
            wallet.setBalance(before.subtract(actual));
        }
        playerWalletService.updateById(wallet);
        order.setReferralClawedAmount(amountOrZero(order.getReferralClawedAmount()).add(actual));
        orderService.updateById(order);
        BigDecimal after = pendingPhase ? wallet.getPendingBalance() : wallet.getBalance();
        transactionService.record(PlayerReferralRules.TX_REFERRAL, "PLAYER", order.getReferralPlayerId(),
                actual.negate(), before, after, order.getId(), null, null,
                "转介绍分成扣回，订单" + order.getOrderNo() + "，扣回¥" + actual.toPlainString());
        log.info("转介绍分成扣回: orderId={}, playerId={}, amount={}", order.getId(), order.getReferralPlayerId(), actual);
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
