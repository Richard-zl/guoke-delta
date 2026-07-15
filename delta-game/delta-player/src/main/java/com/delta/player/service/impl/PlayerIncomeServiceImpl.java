package com.delta.player.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.delta.common.event.BusinessEvent;
import com.delta.common.event.OrderConfirmedEvent;
import com.delta.common.exception.BusinessException;
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
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerIncomeServiceImpl implements PlayerIncomeService {
    private final OrderService orderService;
    private final OrderPlayerService orderPlayerService;
    private final PlayerWalletService playerWalletService;
    private final TransactionService transactionService;
    private final ApplicationEventPublisher eventPublisher;

    @EventListener
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        settleOrder(event.getOrderId());
    }

    /** 平台抽成比例（默认20%） */
    private static final BigDecimal PLATFORM_RATE = new BigDecimal("0.20");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void settleOrder(Long orderId) {
        Order order = orderService.getById(orderId);
        if (order == null) throw new BusinessException("订单不存在");
        if (order.getSettled() != null && order.getSettled() == 1) {
            log.warn("订单{}已结算，跳过", orderId);
            return;
        }

        // 计算打手可分金额 = 订单金额 * (1 - 平台抽成)
        BigDecimal totalSettleAmount = order.getAmount()
                .multiply(BigDecimal.ONE.subtract(PLATFORM_RATE))
                .setScale(2, RoundingMode.HALF_UP);

        // 查询该订单的所有已接受打手
        List<OrderPlayer> players = orderPlayerService.list(new LambdaQueryWrapper<OrderPlayer>()
                .eq(OrderPlayer::getOrderId, orderId)
                .eq(OrderPlayer::getStatus, "ACCEPTED"));

        if (players.isEmpty()) {
            // 无order_player记录，直接用order.playerId（兜底）
            settleToPlayer(order.getPlayerId(), orderId, totalSettleAmount, "主接结算");
        } else if (players.size() == 1) {
            // 单人订单：全额归主接打手
            OrderPlayer op = players.get(0);
            op.setSettleAmount(totalSettleAmount);
            op.setSettledAt(LocalDateTime.now());
            orderPlayerService.updateById(op);
            settleToPlayer(op.getPlayerId(), orderId, totalSettleAmount, "主接结算");
        } else {
            // 多人订单：根据分成方案计算
            settleMultiPlayer(players, totalSettleAmount, orderId);
        }

        // 更新订单结算标记
        order.setSettled(1);
        order.setSettleAmount(totalSettleAmount);
        order.setSettleTime(LocalDateTime.now());
        orderService.updateById(order);
        log.info("订单{}结算完成，结算金额{}", orderId, totalSettleAmount);
    }

    /**
     * 多人订单分成结算：根据队友的split_type计算
     */
    private void settleMultiPlayer(List<OrderPlayer> players, BigDecimal totalAmount, Long orderId) {
        // 找出主接打手和队友
        OrderPlayer primary = players.stream()
                .filter(p -> "PRIMARY".equals(p.getRole())).findFirst().orElse(null);
        OrderPlayer teammate = players.stream()
                .filter(p -> "TEAMMATE".equals(p.getRole())).findFirst().orElse(null);

        if (primary == null) {
            // 兜底：如果没有PRIMARY标记，取第一个作为主接
            primary = players.get(0);
            teammate = players.size() > 1 ? players.get(1) : null;
        }

        BigDecimal primaryAmount;
        BigDecimal teammateAmount = BigDecimal.ZERO;

        if (teammate != null && teammate.getSplitType() != null) {
            // 根据分成类型计算队友收入
            teammateAmount = switch (teammate.getSplitType()) {
                case "FIFTY_FIFTY" -> totalAmount.multiply(new BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP);
                case "FORTY_SIXTY" -> totalAmount.multiply(new BigDecimal("0.40")).setScale(2, RoundingMode.HALF_UP);
                case "THIRTY_SEVENTY" -> totalAmount.multiply(new BigDecimal("0.30")).setScale(2, RoundingMode.HALF_UP);
                case "CUSTOM" -> teammate.getSplitAmount() != null ? teammate.getSplitAmount() : BigDecimal.ZERO;
                default -> totalAmount.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
            };
            // 校验自定义金额不超过总收入
            if (teammateAmount.compareTo(totalAmount) > 0) {
                teammateAmount = totalAmount.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
            }
            primaryAmount = totalAmount.subtract(teammateAmount);
        } else {
            // 无分成方案，均分
            primaryAmount = totalAmount.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
            teammateAmount = totalAmount.subtract(primaryAmount);
        }

        // 结算主接打手
        primary.setSettleAmount(primaryAmount);
        primary.setSettledAt(LocalDateTime.now());
        orderPlayerService.updateById(primary);
        settleToPlayer(primary.getPlayerId(), orderId, primaryAmount, "主接分成");

        // 结算队友
        if (teammate != null) {
            teammate.setSettleAmount(teammateAmount);
            teammate.setSettledAt(LocalDateTime.now());
            orderPlayerService.updateById(teammate);
            settleToPlayer(teammate.getPlayerId(), orderId, teammateAmount, "队友分成");
            // 通知队友
            eventPublisher.publishEvent(new BusinessEvent(this, "INCOME_SETTLED",
                    "PLAYER", teammate.getPlayerId(), orderId,
                    "订单已结算，您的分成收入 " + teammateAmount + " 元已到账"));
        }

        // 通知主接打手
        eventPublisher.publishEvent(new BusinessEvent(this, "INCOME_SETTLED",
                "PLAYER", primary.getPlayerId(), orderId,
                "订单已结算，您的分成收入 " + primaryAmount + " 元已到账"));
    }

    private void settleToPlayer(Long playerId, Long orderId, BigDecimal amount, String remark) {
        PlayerWallet wallet = playerWalletService.getByPlayerId(playerId);
        if (wallet == null) {
            log.error("打手{}钱包不存在，订单{}结算失败", playerId, orderId);
            return;
        }
        BigDecimal balanceBefore = wallet.getBalance();
        wallet.setBalance(wallet.getBalance().add(amount));
        wallet.setTotalIncome(wallet.getTotalIncome().add(amount));
        playerWalletService.updateById(wallet);

        // 创建交易记录
        transactionService.record("INCOME", "PLAYER", playerId, amount,
                balanceBefore, wallet.getBalance(), orderId, null, null, remark);
    }
}
