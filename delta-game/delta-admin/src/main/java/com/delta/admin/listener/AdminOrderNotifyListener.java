package com.delta.admin.listener;

import com.alibaba.fastjson2.JSON;
import com.delta.common.event.BusinessEvent;
import com.delta.common.websocket.manager.WebSocketSessionManager;
import com.delta.order.entity.Order;
import com.delta.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 支付成功、客人申请退款时，推送给当前在线的运营后台。
 * 后台登录连接走 CS 或 ADMIN 身份，不推给用户和打手。
 * 退款会同时发给打手一条 REFUND_REQUEST，这里只处理面向客服的那条。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminOrderNotifyListener {
    private static final Set<String> ADMIN_SESSION_TYPES = Set.of("CS", "ADMIN");

    private final OrderService orderService;
    private final WebSocketSessionManager sessionManager;

    @EventListener
    public void onAdminAlert(BusinessEvent event) {
        if (event.getOrderId() == null) return;
        String type = event.getEventType();
        if ("ORDER_PAID".equals(type)) {
            push(type, event.getOrderId(), "PAID");
        } else if ("REFUND_REQUEST".equals(type) && "CS".equals(event.getUserType())) {
            push(type, event.getOrderId(), "");
        }
    }

    private void push(String type, Long orderId, String defaultStatus) {
        try {
            Order order = orderService.getById(orderId);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", type);
            payload.put("orderId", orderId);
            payload.put("orderNo", order != null ? order.getOrderNo() : "");
            payload.put("productName", order != null ? order.getProductName() : "");
            payload.put("amount", formatAmount(order));
            payload.put("status", order != null && order.getStatus() != null ? order.getStatus() : defaultStatus);
            int sent = sessionManager.sendTextToUserTypes(ADMIN_SESSION_TYPES, JSON.toJSONString(payload));
            log.info("运营后台提醒已推送 type={} orderId={} sent={}", type, orderId, sent);
        } catch (Exception e) {
            log.error("推送运营后台提醒失败 type={} orderId={}", type, orderId, e);
        }
    }

    private String formatAmount(Order order) {
        if (order == null || order.getAmount() == null) return "";
        return order.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
