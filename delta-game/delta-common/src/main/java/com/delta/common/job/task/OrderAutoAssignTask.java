package com.delta.common.job.task;

import com.delta.common.mapper.ScheduledTaskMapper;
import com.delta.common.redis.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderAutoAssignTask {
    private final RedisService redisService;
    private final ScheduledTaskMapper scheduledTaskMapper;

    public void execute() {
        if (!redisService.tryLock("lock:task:order_assign", 2, TimeUnit.MINUTES)) return;
        try {
            // 查询PAID状态且超过10分钟未被接单的订单
            List<Map<String, Object>> orders = scheduledTaskMapper.selectUnassignedOrders();
            int maxActive = 5;
            try {
                String val = scheduledTaskMapper.selectConfigValue("order.max_active_per_player");
                if (val != null) maxActive = Integer.parseInt(val);
            } catch (Exception ignored) {}
            for (Map<String, Object> order : orders) {
                Long orderId = ((Number) order.get("id")).longValue();
                // 查找空闲的ACTIVE打手（进行中订单数最少）
                List<Map<String, Object>> players = scheduledTaskMapper.selectAvailablePlayers(maxActive);
                if (!players.isEmpty()) {
                    Long playerId = ((Number) players.get(0).get("id")).longValue();
                    int affected = scheduledTaskMapper.assignOrderToPlayer(orderId, playerId);
                    if (affected > 0) {
                        // 记录order_progress
                        scheduledTaskMapper.insertOrderProgress(orderId, "PAID", "ASSIGNED", "系统自动指派打手");
                        // 通知打手
                        scheduledTaskMapper.insertSystemNotification("PLAYER", playerId, "新指派订单", "您有一笔新的指派订单，请确认", "ORDER", orderId);
                        log.info("自动指派订单{}给打手{}", orderId, playerId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("订单自动分配任务异常", e);
        } finally { redisService.unlock("lock:task:order_assign"); }
    }
}
