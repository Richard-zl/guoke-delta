package com.delta.player.service;

import com.delta.order.entity.Order;

import java.math.BigDecimal;

/**
 * 打手收入结算服务：到期释放待入账、仲裁退款扣回。
 */
public interface PlayerIncomeService {

    /**
     * 释放到期的待入账订单到可提现余额。
     *
     * @param limit 单批处理上限
     * @return 成功入账订单数
     */
    int releaseDueSettlements(int limit);

    /**
     * 仲裁退款扣回：settled=2 先扣主打手本单剩余 settleAmount/pending，再扣 balance；
     * settled=1 只扣 balance。拟扣上限为 refundAmount（与现网一致，仅扣 order.playerId）。
     */
    void deductForOrderRefund(Order order, BigDecimal refundAmount);
}
