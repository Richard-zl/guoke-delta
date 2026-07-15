package com.delta.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 订单确认完成事件，触发结算等后续流程
 */
@Getter
public class OrderConfirmedEvent extends ApplicationEvent {
    private final Long orderId;
    private final String operatorType;

    public OrderConfirmedEvent(Object source, Long orderId) {
        this(source, orderId, "USER");
    }

    public OrderConfirmedEvent(Object source, Long orderId, String operatorType) {
        super(source);
        this.orderId = orderId;
        this.operatorType = operatorType;
    }
}
