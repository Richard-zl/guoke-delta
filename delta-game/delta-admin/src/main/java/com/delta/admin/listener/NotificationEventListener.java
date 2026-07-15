package com.delta.admin.listener;

import com.delta.admin.service.BusinessSmsNotificationService;
import com.delta.common.event.BusinessEvent;
import com.delta.common.event.OrderConfirmedEvent;
import com.delta.message.config.WxTemplateProperties;
import com.delta.message.service.SystemNotificationService;
import com.delta.message.service.WxSubscribeMessageService;
import com.delta.order.entity.Order;
import com.delta.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 统一业务事件通知监听器
 * 负责站内消息 + 微信订阅消息的分发
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {
    private final SystemNotificationService systemNotificationService;
    private final OrderService orderService;
    private final WxSubscribeMessageService wxSubscribeMessageService;
    private final WxTemplateProperties wxTemplate;
    private final BusinessSmsNotificationService businessSmsNotificationService;

    /** 需要同时发送微信订阅消息的事件类型 */
    private static final Set<String> WX_NOTIFY_EVENTS = Set.of(
            "ORDER_PAID", "ORDER_ACCEPTED", "ORDER_ASSIGNED",
            "ORDER_CANCELLED", "ORDER_REFUNDED",
            "TEAMMATE_INVITED", "ORDER_COMPLETED",
            "INCOME_SETTLED", "COMPLAINT_RESOLVED",
            "WITHDRAW_COMPLETED", "WITHDRAW_REJECTED"
    );

    /**
     * 监听通用业务事件
     */
    @Async
    @EventListener
    public void onBusinessEvent(BusinessEvent event) {
        try {
            systemNotificationService.send(event.getUserType(), event.getUserId(),
                    event.getEventType(), event.getMessage(), event.getEventType(), event.getOrderId());
            log.debug("发送站内通知: {} -> {}:{}", event.getEventType(), event.getUserType(), event.getUserId());
        } catch (Exception e) {
            log.error("发送站内通知失败: {}", event.getEventType(), e);
        }

        if (WX_NOTIFY_EVENTS.contains(event.getEventType())) {
            sendWxNotify(event);
        }

        try {
            businessSmsNotificationService.sendIfNeeded(event);
        } catch (Exception e) {
            log.error("发送业务短信失败: {}", event.getEventType(), e);
        }
    }

    /**
     * 订单确认后通知打手（站内 + 微信，与订单状态模板一致：phrase4/character_string1/thing2/amount3）
     * 用户点击确认或系统自动确认时触发
     */
    @Async
    @EventListener
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        try {
            Order order = orderService.getById(event.getOrderId());
            if (order == null || order.getPlayerId() == null) return;
            String operatorType = event.getOperatorType();

            // 站内系统通知
            systemNotificationService.send("PLAYER", order.getPlayerId(),
                    "订单已确认", buildConfirmedMessage(order, operatorType),
                    "ORDER_CONFIRMED", order.getId());

            // 微信订阅消息（订单状态模板）—— 通知主接打手
            Map<String, String> data = orderStatusData(order, confirmedStatusText(operatorType));
            wxSubscribeMessageService.sendAsync("PLAYER", order.getPlayerId(),
                    wxTemplate.getOrderStatus(), data,
                    "pages-player/order/detail?id=" + order.getId());
        } catch (Exception e) {
            log.error("订单确认通知失败", e);
        }
    }

    /**
     * 根据事件类型分发微信订阅消息
     */
    private void sendWxNotify(BusinessEvent event) {
        String type = event.getEventType();
        String userType = event.getUserType();
        Long userId = event.getUserId();
        Long orderId = event.getOrderId();

        try {
            switch (type) {
                // ===== 订单状态变更模板 =====
                // ORDER_PAID 不再向用户发订阅消息，第一次订阅提醒改为「被接单了」
                case "ORDER_PAID" -> { /* 用户端不发送支付成功订阅，仅保留站内通知 */ }
                case "ORDER_ACCEPTED" -> {
                    // 通知用户：被接单了（第一次订阅提醒）
                    if ("USER".equals(userType)) {
                        Order order = orderService.getById(orderId);
                        if (order != null) {
                            Map<String, String> data = orderStatusData(order, "已被接单");
                            wxSubscribeMessageService.sendAsync("USER", userId,
                                    wxTemplate.getOrderStatus(), data,
                                    "pages/order/detail?id=" + orderId);
                        }
                    }
                }
                case "ORDER_ASSIGNED" -> {
                    // 通知打手：被指派
                    if ("PLAYER".equals(userType)) {
                        Order order = orderService.getById(orderId);
                        if (order != null) {
                            Map<String, String> data = orderStatusData(order, "新指派订单");
                            wxSubscribeMessageService.sendAsync("PLAYER", userId,
                                    wxTemplate.getOrderStatus(), data,
                                    "pages-player/order/detail?id=" + orderId);
                        }
                    }
                }
                case "ORDER_CANCELLED" -> {
                    // 通知用户：订单已取消（未支付取消）
                    if ("USER".equals(userType)) {
                        Order order = orderService.getById(orderId);
                        if (order != null) {
                            Map<String, String> data = orderStatusData(order, "已取消");
                            wxSubscribeMessageService.sendAsync("USER", userId,
                                    wxTemplate.getOrderStatus(), data,
                                    "pages/order/detail?id=" + orderId);
                        }
                    }
                }
                case "ORDER_REFUNDED" -> {
                    // 通知用户：订单已退款
                    if ("USER".equals(userType)) {
                        Order order = orderService.getById(orderId);
                        if (order != null) {
                            Map<String, String> data = orderStatusData(order, "已退款");
                            wxSubscribeMessageService.sendAsync("USER", userId,
                                    wxTemplate.getOrderStatus(), data,
                                    "pages/order/detail?id=" + orderId);
                        }
                    }
                }
                case "ORDER_COMPLETED" -> {
                    // 通知用户：服务已完成（第二次订阅提醒）
                    if ("USER".equals(userType)) {
                        Order order = orderService.getById(orderId);
                        if (order != null) {
                            Map<String, String> data = orderStatusData(order, "服务已完成");
                            wxSubscribeMessageService.sendAsync("USER", userId,
                                    wxTemplate.getOrderStatus(), data,
                                    "pages/order/detail?id=" + orderId);
                        }
                    }
                }
                case "COMPLAINT_RESOLVED" -> {
                    // 通知用户和打手：仲裁结果
                    Order order = orderId != null ? orderService.getById(orderId) : null;
                    Map<String, String> data = order != null
                            ? orderStatusData(order, "仲裁已出结果")
                            : orderStatusDataFallback(orderId != null ? orderId.toString() : "-", "仲裁已出结果", truncate(event.getMessage(), 20), null);
                    wxSubscribeMessageService.sendAsync(userType, userId,
                            wxTemplate.getOrderStatus(), data, null);
                }

                // ===== 组队邀请模板 =====
                case "TEAMMATE_INVITED" -> {
                    if ("PLAYER".equals(userType)) {
                        Order order = orderService.getById(orderId);
                        Map<String, String> data = new LinkedHashMap<>();
                        data.put("thing1", "组队邀请");
                        data.put("thing2", order != null ? truncate(order.getProductName(), 20) : "代练订单");
                        data.put("thing3", "您收到一份组队邀请，请尽快确认");
                        wxSubscribeMessageService.sendAsync("PLAYER", userId,
                                wxTemplate.getTeamInvite(), data,
                                "pages-player/invite/list");
                    }
                }

                // ===== 收入到账模板 =====
                case "INCOME_SETTLED" -> {
                    if ("PLAYER".equals(userType)) {
                        Map<String, String> data = new LinkedHashMap<>();
                        data.put("thing1", "订单结算到账");
                        data.put("thing2", truncate(event.getMessage(), 20));
                        wxSubscribeMessageService.sendAsync("PLAYER", userId,
                                wxTemplate.getIncome(), data,
                                "pages-player/earnings/index");
                    }
                }
                case "WITHDRAW_COMPLETED", "WITHDRAW_REJECTED" -> {
                    if ("PLAYER".equals(userType)) {
                        Map<String, String> data = new LinkedHashMap<>();
                        data.put("thing1", "WITHDRAW_COMPLETED".equals(type) ? "提现成功" : "提现被拒绝");
                        data.put("thing2", truncate(event.getMessage(), 20));
                        wxSubscribeMessageService.sendAsync("PLAYER", userId,
                                wxTemplate.getIncome(), data,
                                "pages-player/withdraw/list");
                    }
                }
                default -> log.debug("事件类型{}无需微信通知", type);
            }
        } catch (Exception e) {
            log.error("微信订阅消息分发失败: type={}, userType={}, userId={}", type, userType, userId, e);
        }
    }

    /**
     * 构建订单状态变更模板数据（模板字段：phrase4 订单状态、character_string1 订单单号、thing2 订单类型、amount3 订单金额）
     */
    private Map<String, String> orderStatusData(Order order, String statusText) {
        if (order == null) return new LinkedHashMap<>();
        return orderStatusDataFallback(order.getOrderNo(), statusText,
                truncate(order.getProductName(), 20),
                order.getAmount());
    }

    private Map<String, String> orderStatusDataFallback(String orderNo, String statusText, String thing2, BigDecimal amount) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("phrase4", statusText);
        data.put("character_string1", orderNo != null ? orderNo : "-");
        data.put("thing2", thing2 != null ? thing2 : "订单");
        data.put("amount3", amount != null ? amount.setScale(2, java.math.RoundingMode.HALF_UP).toString() + "元" : "0.00元");
        return data;
    }

    private String buildConfirmedMessage(Order order, String operatorType) {
        String action = switch (operatorType) {
            case "ADMIN" -> "已由管理员确认";
            case "CS" -> "已由客服确认";
            case "SYSTEM" -> "已自动确认";
            default -> "已被用户确认";
        };
        return "订单" + order.getOrderNo() + action + "，收益即将到账";
    }

    private String confirmedStatusText(String operatorType) {
        return switch (operatorType) {
            case "ADMIN" -> "管理员确认";
            case "CS" -> "客服确认";
            case "SYSTEM" -> "系统自动确认";
            default -> "已确认";
        };
    }

    /**
     * 截断字符串（微信订阅消息字段有长度限制）
     */
    private String truncate(String str, int maxLen) {
        if (str == null) return "-";
        return str.length() > maxLen ? str.substring(0, maxLen) : str;
    }
}
