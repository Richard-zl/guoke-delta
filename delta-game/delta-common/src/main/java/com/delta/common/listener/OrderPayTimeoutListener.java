package com.delta.common.listener;

import com.delta.common.mapper.ScheduledTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * 监听Redis key过期事件，实现订单超时自动取消
 * 当 order:pay_timeout:{orderId} 过期时，自动将订单状态改为CANCELLED
 */
@Slf4j
@Component
public class OrderPayTimeoutListener extends KeyExpirationEventMessageListener {

    private static final String KEY_PREFIX = "order:pay_timeout:";

    private final ScheduledTaskMapper scheduledTaskMapper;

    public OrderPayTimeoutListener(RedisMessageListenerContainer listenerContainer,
                                   ScheduledTaskMapper scheduledTaskMapper) {
        super(listenerContainer);
        this.scheduledTaskMapper = scheduledTaskMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = message.toString();
        if (!expiredKey.startsWith(KEY_PREFIX)) {
            return;
        }
        String orderIdStr = expiredKey.substring(KEY_PREFIX.length());
        try {
            Long orderId = Long.parseLong(orderIdStr);
            int updated = scheduledTaskMapper.cancelSingleExpiredOrder(orderId);
            if (updated > 0) {
                scheduledTaskMapper.insertOrderProgress(orderId, "PENDING_PAYMENT", "CANCELLED", "超时未支付，系统自动取消");
                log.info("Redis过期自动取消订单: orderId={}", orderId);
            }
        } catch (Exception e) {
            log.error("Redis过期取消订单失败: key={}", expiredKey, e);
        }
    }
}
