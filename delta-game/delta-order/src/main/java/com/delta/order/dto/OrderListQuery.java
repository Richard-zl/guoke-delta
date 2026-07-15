package com.delta.order.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端订单列表筛选参数
 */
@Data
public class OrderListQuery {
    private String orderNo;
    /** CS 端历史兼容：无 orderNo 时等同 orderNo 模糊匹配 */
    private String keyword;
    private String status;
    private String statusIn;
    private Long userId;
    private Long playerId;
    private Long productId;
    private LocalDateTime createdAtStart;
    private LocalDateTime createdAtEnd;
    private Boolean unassigned;
}
