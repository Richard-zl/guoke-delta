package com.delta.common.service;

import com.delta.common.enums.PlayerWorkStatusEnum;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 按统一优先级计算打手综合工作状态。
 */
@Component
public class PlayerWorkStatusCalculator {

    public PlayerWorkStatusEnum resolve(
            String accountStatus,
            LocalDateTime frozenUntil,
            Integer isOnline,
            int activeOrders,
            int pendingAssignedOrders,
            int maxConcurrent,
            LocalDateTime now) {
        if (!"ACTIVE".equals(accountStatus)
                || (frozenUntil != null && frozenUntil.isAfter(now))) {
            return PlayerWorkStatusEnum.ACCOUNT_ABNORMAL;
        }
        if (!Integer.valueOf(1).equals(isOnline)) {
            return PlayerWorkStatusEnum.OFFLINE;
        }
        if (pendingAssignedOrders > 0) {
            return PlayerWorkStatusEnum.ASSIGNED_PENDING;
        }
        if (activeOrders >= maxConcurrent) {
            return PlayerWorkStatusEnum.FULL;
        }
        if (activeOrders > 0) {
            return PlayerWorkStatusEnum.IN_SERVICE;
        }
        return PlayerWorkStatusEnum.AVAILABLE;
    }
}
