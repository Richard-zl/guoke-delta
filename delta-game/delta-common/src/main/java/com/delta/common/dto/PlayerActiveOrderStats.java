package com.delta.common.dto;

import lombok.Data;

/**
 * 打手当前订单占用聚合结果。
 */
@Data
public class PlayerActiveOrderStats {
    private Long playerId;
    private Integer activeOrders;
    private Integer pendingAssignedOrders;
}
