package com.delta.common.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 通用业务事件，用于触发异步通知
 */
@Getter
public class BusinessEvent extends ApplicationEvent {
    private final String eventType;
    private final String userType;
    private final Long userId;
    private final Long orderId;
    private final String message;

    public BusinessEvent(Object source, String eventType, String userType, Long userId, Long orderId, String message) {
        super(source);
        this.eventType = eventType;
        this.userType = userType;
        this.userId = userId;
        this.orderId = orderId;
        this.message = message;
    }
}
