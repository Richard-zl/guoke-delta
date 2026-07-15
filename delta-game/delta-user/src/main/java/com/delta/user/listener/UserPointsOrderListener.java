package com.delta.user.listener;

import com.delta.common.event.OrderConfirmedEvent;
import com.delta.order.entity.Order;
import com.delta.order.service.OrderService;
import com.delta.user.service.PointsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单确认完成（CONFIRMED）后为用户发放积分
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserPointsOrderListener {

    private final OrderService orderService;
    private final PointsService pointsService;

    @EventListener
    @Transactional(rollbackFor = Exception.class)
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        Long orderId = event.getOrderId();
        Order order = orderService.getById(orderId);
        if (order == null || order.getUserId() == null) {
            log.warn("积分发放跳过: 订单不存在或无用户, orderId={}", orderId);
            return;
        }
        pointsService.addPointsByOrder(order.getUserId(), order.getAmount(), orderId);
    }
}
