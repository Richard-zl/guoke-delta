package com.delta.player.service;

/**
 * 打手收入结算服务
 */
public interface PlayerIncomeService {
    /**
     * 订单确认后触发结算
     * @param orderId 订单ID
     */
    void settleOrder(Long orderId);
}
