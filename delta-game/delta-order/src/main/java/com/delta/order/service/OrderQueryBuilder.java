package com.delta.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.delta.order.dto.OrderListQuery;
import com.delta.order.entity.Order;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 管理端订单列表查询条件构建（Admin / CS 共用）
 */
public final class OrderQueryBuilder {

    private OrderQueryBuilder() {
    }

    public static LambdaQueryWrapper<Order> build(OrderListQuery query) {
        OrderListQuery q = query != null ? query : new OrderListQuery();
        LambdaQueryWrapper<Order> w = new LambdaQueryWrapper<>();

        String resolvedOrderNo = q.getOrderNo();
        if (isBlank(resolvedOrderNo) && !isBlank(q.getKeyword())) {
            resolvedOrderNo = q.getKeyword();
        }
        if (!isBlank(resolvedOrderNo)) {
            w.like(Order::getOrderNo, resolvedOrderNo.trim());
        }

        if (!isBlank(q.getStatus())) {
            w.eq(Order::getStatus, q.getStatus().trim());
        } else if (!isBlank(q.getStatusIn())) {
            List<String> statuses = Arrays.stream(q.getStatusIn().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            if (!statuses.isEmpty()) {
                w.in(Order::getStatus, statuses);
            }
        }

        if (q.getUserId() != null) {
            w.eq(Order::getUserId, q.getUserId());
        }
        if (q.getProductId() != null) {
            w.eq(Order::getProductId, q.getProductId());
        }

        // 打手筛选：同时匹配主接与辅助
        if (q.getPlayerId() != null) {
            Long playerId = q.getPlayerId();
            w.and(player -> player.eq(Order::getPlayerId, playerId)
                    .or()
                    .eq(Order::getPlayerId2, playerId));
        }

        LocalDateTime start = q.getCreatedAtStart();
        LocalDateTime end = q.getCreatedAtEnd();
        if (start != null) {
            w.ge(Order::getCreatedAt, start);
        }
        if (end != null) {
            w.le(Order::getCreatedAt, end);
        }

        if (Boolean.TRUE.equals(q.getUnassigned())) {
            w.eq(Order::getStatus, "PAID").isNull(Order::getPlayerId);
        }

        return w.orderByDesc(Order::getCreatedAt);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
