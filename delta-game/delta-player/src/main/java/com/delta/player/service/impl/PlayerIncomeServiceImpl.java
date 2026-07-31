package com.delta.player.service.impl;

import com.delta.player.service.PlayerIncomeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 打手收入服务骨架。
 * 确认时记待入账已迁至 SettlementEventListener；
 * Task 4 将在此实现 releaseDueSettlements / deductForOrderRefund。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerIncomeServiceImpl implements PlayerIncomeService {

    /**
     * 已废弃：确认结算由 SettlementEventListener 统一处理，勿再调用。
     * Task 4 将移除此方法并改为 releaseDueSettlements。
     */
    @Override
    public void settleOrder(Long orderId) {
        log.warn("settleOrder 已废弃，确认结算由 SettlementEventListener 处理, orderId={}", orderId);
    }
}
