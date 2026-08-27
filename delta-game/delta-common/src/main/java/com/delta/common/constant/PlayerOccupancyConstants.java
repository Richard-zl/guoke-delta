package com.delta.common.constant;

import java.util.List;

/**
 * 打手订单占用的统一配置与状态定义。
 */
public final class PlayerOccupancyConstants {

    public static final String MAX_ACTIVE_CONFIG_KEY = "order.max_active_per_player";
    public static final String DEFAULT_MAX_ACTIVE = "1";
    public static final List<String> ACTIVE_ORDER_STATUSES =
            List.of("ASSIGNED", "ACCEPTED", "WAITING_TEAMMATE", "IN_PROGRESS");

    private PlayerOccupancyConstants() {
    }
}
