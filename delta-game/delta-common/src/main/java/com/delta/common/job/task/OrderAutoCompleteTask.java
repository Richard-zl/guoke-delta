package com.delta.common.job.task;

import com.delta.common.event.OrderConfirmedEvent;
import com.delta.common.mapper.ScheduledTaskMapper;
import com.delta.common.redis.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderAutoCompleteTask {
    private final ScheduledTaskMapper scheduledTaskMapper;
    private final RedisService redisService;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(cron = "0 */10 * * * ?")
    public void execute() {
        if (!redisService.tryLock("lock:task:order_confirm", 9, TimeUnit.MINUTES)) return;
        try {
            // 1. 先查出待自动确认的订单ID
            List<Long> orderIds = scheduledTaskMapper.selectAutoConfirmOrderIds();
            if (!orderIds.isEmpty()) {
                // 查出订单的user_id用于通知
                List<Map<String, Object>> orderInfos = scheduledTaskMapper.selectAutoConfirmOrderInfos();
                // 2. 批量更新状态
                scheduledTaskMapper.confirmExpiredOrders();
                // 记录order_progress
                for (Long oid : orderIds) {
                    scheduledTaskMapper.insertOrderProgress(oid, "COMPLETED", "CONFIRMED", "系统自动确认");
                }
                // 通知用户系统自动确认
                for (Map<String, Object> info : orderInfos) {
                    Long oid = ((Number) info.get("id")).longValue();
                    Object uid = info.get("user_id");
                    if (uid != null) {
                        scheduledTaskMapper.insertSystemNotification("USER", ((Number) uid).longValue(),
                                "订单自动确认", "您的订单已超时自动确认，服务已完成", "ORDER", oid);
                    }
                }
                log.info("自动确认{}笔超时订单", orderIds.size());

                // 3. 对每笔订单发布确认事件，触发结算
                for (Long orderId : orderIds) {
                    try {
                        eventPublisher.publishEvent(new OrderConfirmedEvent(this, orderId, "SYSTEM"));
                    } catch (Exception e) {
                        log.error("订单{}自动确认结算失败", orderId, e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("订单自动确认任务异常", e);
        } finally { redisService.unlock("lock:task:order_confirm"); }
    }
}
