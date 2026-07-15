package com.delta.common.job.task;

import com.delta.common.mapper.ScheduledTaskMapper;
import com.delta.common.redis.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class TeammateTimeoutTask {
    private final ScheduledTaskMapper scheduledTaskMapper;
    private final RedisService redisService;

    @Scheduled(cron = "0 */10 * * * ?")
    public void execute() {
        if (!redisService.tryLock("lock:task:teammate_timeout", 9, TimeUnit.MINUTES)) return;
        try {
            // 1. 查出超时订单ID
            List<Long> orderIds = scheduledTaskMapper.selectTeammateTimeoutOrderIds();
            if (!orderIds.isEmpty()) {
                // 查出订单的player_id和user_id，用于后续通知
                List<Map<String, Object>> orderInfos = scheduledTaskMapper.selectTeammateTimeoutOrderInfos();
                // 2. 释放订单
                scheduledTaskMapper.releaseTeammateTimeoutOrders();
                // 3. 更新关联的order_player为RELEASED
                for (Long orderId : orderIds) {
                    scheduledTaskMapper.releaseOrderPlayers(orderId);
                }
                // 3.5 记录order_progress
                for (Long orderId : orderIds) {
                    scheduledTaskMapper.insertOrderProgress(orderId, "WAITING_TEAMMATE", "PAID", "组队超时，订单释放回接单池");
                }
                // 4. 通知主接打手和用户
                for (Map<String, Object> info : orderInfos) {
                    Long oid = ((Number) info.get("id")).longValue();
                    Object pid = info.get("player_id");
                    Object uid = info.get("user_id");
                    if (pid != null) {
                        scheduledTaskMapper.insertSystemNotification("PLAYER", ((Number) pid).longValue(),
                                "组队超时", "组队超时，订单已释放", "ORDER", oid);
                    }
                    if (uid != null) {
                        scheduledTaskMapper.insertSystemNotification("USER", ((Number) uid).longValue(),
                                "订单重新派单", "打手组队超时，订单已重新进入接单池", "ORDER", oid);
                    }
                }
                log.info("组队超时订单释放: {}条，order_player已更新为RELEASED", orderIds.size());
            }
        } finally { redisService.unlock("lock:task:teammate_timeout"); }
    }
}
