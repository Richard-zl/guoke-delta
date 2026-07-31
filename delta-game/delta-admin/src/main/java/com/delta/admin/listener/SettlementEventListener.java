package com.delta.admin.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.delta.common.event.OrderConfirmedEvent;
import com.delta.order.entity.Order;
import com.delta.order.entity.OrderPlayer;
import com.delta.order.service.OrderPlayerService;
import com.delta.order.service.OrderService;
import com.delta.player.entity.PlayerWallet;
import com.delta.player.service.PlayerWalletService;
import com.delta.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单确认后记待入账 —— 累加 pending_balance，到期由定时任务释放到可提现余额
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementEventListener {

    private final OrderService orderService;
    private final OrderPlayerService orderPlayerService;
    private final PlayerWalletService playerWalletService;
    private final SysConfigService sysConfigService;

    @EventListener
    @Transactional(rollbackFor = Exception.class)
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        Long orderId = event.getOrderId();
        Order order = orderService.getById(orderId);
        if (order == null || order.getPlayerId() == null) {
            log.warn("结算跳过: 订单不存在或无打手, orderId={}", orderId);
            return;
        }
        // 防止重复结算（已入账或已记待入账）
        if (order.getSettled() != null && (order.getSettled() == 1 || order.getSettled() == 2)) {
            log.warn("结算跳过: 订单已结算或待入账, orderId={}, settled={}", orderId, order.getSettled());
            return;
        }

        // 优先使用订单下单时快照的抽佣比例，否则从系统配置读取
        BigDecimal commissionRate;
        if (order.getCommissionRate() != null) {
            commissionRate = order.getCommissionRate();
        } else {
            String rateStr = sysConfigService.getConfigValue("settlement.commission_rate", "0.2");
            commissionRate = new BigDecimal(rateStr);
        }

        BigDecimal orderAmount = order.getAmount();
        BigDecimal commission = orderAmount.multiply(commissionRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal playerTotalIncome = orderAmount.subtract(commission);

        Long primaryPlayerId = order.getPlayerId();

        // 查询队友记录（ACCEPTED 状态的 TEAMMATE）
        List<OrderPlayer> teammates = orderPlayerService.list(new LambdaQueryWrapper<OrderPlayer>()
                .eq(OrderPlayer::getOrderId, orderId)
                .eq(OrderPlayer::getRole, "TEAMMATE")
                .eq(OrderPlayer::getStatus, "ACCEPTED"));

        BigDecimal primaryIncome = playerTotalIncome; // 默认全归主打手

        // 给每位队友记待入账
        for (OrderPlayer tm : teammates) {
            BigDecimal tmIncome = calcTeammateIncome(playerTotalIncome, tm, teammates.size());
            if (tmIncome.compareTo(BigDecimal.ZERO) <= 0) continue;
            primaryIncome = primaryIncome.subtract(tmIncome);
            creditPending(tm.getPlayerId(), tmIncome, orderId,
                    String.format("队友分成: 订单金额¥%s, 分成¥%s", orderAmount.toPlainString(), tmIncome.toPlainString()));
            // 确认时只写 settleAmount，settledAt 留空待入账任务再写
            tm.setSettleAmount(tmIncome);
            orderPlayerService.updateById(tm);
            log.info("队友待入账: orderId={}, playerId={}, 分成={}", orderId, tm.getPlayerId(), tmIncome);
        }

        // 主打手待入账（扣除队友分成后的剩余）
        if (primaryIncome.compareTo(BigDecimal.ZERO) > 0) {
            creditPending(primaryPlayerId, primaryIncome, orderId,
                    String.format("订单金额:¥%s, 抽成:%s%%, 抽成¥%s, 打手总收入¥%s, 队友分成后实得¥%s",
                            orderAmount.toPlainString(),
                            commissionRate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString(),
                            commission.toPlainString(),
                            playerTotalIncome.toPlainString(),
                            primaryIncome.toPlainString()));
        }
        // 更新主打手 OrderPlayer 结算金额（settledAt 留空）
        List<OrderPlayer> primaryOps = orderPlayerService.list(new LambdaQueryWrapper<OrderPlayer>()
                .eq(OrderPlayer::getOrderId, orderId)
                .eq(OrderPlayer::getPlayerId, primaryPlayerId)
                .eq(OrderPlayer::getRole, "PRIMARY")
                .orderByDesc(OrderPlayer::getId)
                .last("LIMIT 1"));
        if (!primaryOps.isEmpty()) {
            OrderPlayer primaryOp = primaryOps.get(0);
            primaryOp.setSettleAmount(primaryIncome);
            orderPlayerService.updateById(primaryOp);
        }

        // 标记订单待入账，设置预计可入账时间
        int delayDays = Integer.parseInt(sysConfigService.getConfigValue("settlement.delay_days", "5"));
        LocalDateTime confirmTime = order.getConfirmTime() != null ? order.getConfirmTime() : LocalDateTime.now();
        order.setSettled(2);
        order.setSettleAmount(playerTotalIncome);
        order.setSettleAvailableAt(confirmTime.plusDays(delayDays));
        orderService.updateById(order);

        log.info("订单待入账完成: orderId={}, 订单金额={}, 抽成={}, 打手总收入={}, 主打手实得={}, 队友数={}, settleAvailableAt={}",
                orderId, orderAmount, commission, playerTotalIncome, primaryIncome, teammates.size(),
                order.getSettleAvailableAt());
    }

    /**
     * 计算队友分成金额
     */
    private BigDecimal calcTeammateIncome(BigDecimal playerTotalIncome, OrderPlayer tm, int teammateCount) {
        String splitType = tm.getSplitType();
        if (splitType == null) splitType = "FIFTY_FIFTY";
        return switch (splitType) {
            case "CUSTOM" -> tm.getSplitAmount() != null ? tm.getSplitAmount().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            case "FORTY_SIXTY" -> playerTotalIncome.multiply(new BigDecimal("0.4")).setScale(2, RoundingMode.HALF_UP);
            case "THIRTY_SEVENTY" -> playerTotalIncome.multiply(new BigDecimal("0.3")).setScale(2, RoundingMode.HALF_UP);
            // FIFTY_FIFTY 和默认：平分打手总收入
            default -> playerTotalIncome.divide(BigDecimal.valueOf(teammateCount + 1), 2, RoundingMode.HALF_UP);
        };
    }

    /**
     * 给指定打手累加待入账余额（不改 balance / totalIncome，不写 INCOME 流水）
     */
    private void creditPending(Long playerId, BigDecimal income, Long orderId, String remark) {
        PlayerWallet wallet = playerWalletService.getByPlayerId(playerId);
        if (wallet == null) {
            playerWalletService.initWallet(playerId);
            wallet = playerWalletService.getByPlayerId(playerId);
        }
        if (wallet.getPendingBalance() == null) {
            wallet.setPendingBalance(BigDecimal.ZERO);
        }
        wallet.setPendingBalance(wallet.getPendingBalance().add(income));
        playerWalletService.updateById(wallet);
        log.info("记待入账: orderId={}, playerId={}, amount={}, remark={}",
                orderId, playerId, income, remark);
    }
}
