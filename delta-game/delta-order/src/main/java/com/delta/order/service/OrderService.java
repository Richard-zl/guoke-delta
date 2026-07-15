package com.delta.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.delta.order.dto.CreateOrderRequest;
import com.delta.order.entity.Order;

public interface OrderService extends IService<Order> {
    Order createOrder(Long userId, CreateOrderRequest request);
    void cancelOrder(Long orderId, Long userId);
    void csCancelOrder(Long orderId, Long operatorId);
    void assignOrder(Long orderId, Long playerId);
    void assignOrder(Long orderId, Long playerId, Long playerId2, String operatorType, Long operatorId);
    void acceptOrder(Long orderId, Long playerId);
    void rejectAssign(Long orderId, Long playerId, String reason);
    void startOrder(Long orderId, Long playerId);
    void completeOrder(Long orderId, Long playerId, String endServiceImages);
    void confirmOrder(Long orderId, Long userId);
    void manualConfirmOrder(Long orderId, Long operatorId, String operatorType);
    void disputeOrder(Long orderId);
    void arbitrateOrder(Long orderId, String result, Long operatorId);
    void markReviewed(Long orderId);
}