package com.delta.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.delta.order.dto.CreateOrderRequest;
import com.delta.order.entity.Order;

public interface OrderService extends IService<Order> {
    Order createOrder(Long userId, CreateOrderRequest request);
    void cancelOrder(Long orderId, Long userId);
    /** 订单列表直接退款。reason 必须是允许的退款原因。 */
    void csCancelOrder(Long orderId, Long operatorId, String reason);
    /** 审核通过后执行全额退款（仅 PAID / ASSIGNED），reason 为客服选择的退款原因 */
    void refundAfterReview(Long orderId, Long operatorId, String reason);
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