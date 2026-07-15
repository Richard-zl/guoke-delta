package com.delta.common.job.task;

import com.delta.common.mapper.ScheduledTaskMapper;
import com.delta.common.redis.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderAutoCancelTask {
    private final ScheduledTaskMapper scheduledTaskMapper;
    private final RedisService redisService;

    @Scheduled(cron = "0 */5 * * * ?")
    public void execute() {
        if (!redisService.tryLock("lock:task:order_cancel", 4, TimeUnit.MINUTES)) return;
        try {
            // 先查出待取消订单ID
            List<Long> orderIds = scheduledTaskMapper.selectExpiredPendingOrderIds();
            if (orderIds.isEmpty()) return;
            // 批量更新状态
            scheduledTaskMapper.cancelExpiredOrders();
            // 为每个订单记录order_progress
            for (Long orderId : orderIds) {
                scheduledTaskMapper.insertOrderProgress(orderId, "PENDING_PAYMENT", "CANCELLED", "超时未支付，系统自动取消");
            }
            log.info("自动取消超时未支付订单: {}条", orderIds.size());
        } finally { redisService.unlock("lock:task:order_cancel"); }
    }
}
