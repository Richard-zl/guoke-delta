package com.delta.player.service;

/**
 * 打手收入结算服务。
 * 确认记待入账由 SettlementEventListener 负责；
 * Task 4 将扩展 releaseDueSettlements / deductForOrderRefund。
 */
public interface PlayerIncomeService {
    /**
     * @deprecated 确认结算已迁至 SettlementEventListener，勿再调用。
     *             Task 4 将移除此方法。
     */
    @Deprecated
    void settleOrder(Long orderId);
}
