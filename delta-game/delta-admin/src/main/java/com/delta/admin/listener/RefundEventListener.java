package com.delta.admin.listener;

import com.delta.common.event.BusinessEvent;
import com.delta.pay.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 退款事件监听器 —— 处理订单取消后的退款流程
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundEventListener {
    private final PaymentService paymentService;

    @EventListener
    public void onBusinessEvent(BusinessEvent event) {
        if (!"ORDER_CANCEL_REFUND".equals(event.getEventType())) return;

        Long orderId = event.getOrderId();
        log.info("收到退款事件: orderId={}", orderId);
        try {
            paymentService.refundByOrderId(orderId, "用户取消订单退款");
        } catch (Exception e) {
            log.error("退款处理失败: orderId={}", orderId, e);
        }
    }
}
