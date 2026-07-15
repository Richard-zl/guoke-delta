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
public class TeammateInviteTimeoutTask {
    private final ScheduledTaskMapper scheduledTaskMapper;
    private final RedisService redisService;

    @Scheduled(cron = "0 */5 * * * ?")
    public void execute() {
        if (!redisService.tryLock("lock:task:invite_timeout", 4, TimeUnit.MINUTES)) return;
        try {
            // 先查出超时邀请的order_id和主接打手ID
            List<Map<String, Object>> expiredInvites = scheduledTaskMapper.selectExpiredInvites();
            if (expiredInvites.isEmpty()) return;
            // 批量更新状态
            int rows = scheduledTaskMapper.expireTeammateInvites();
            log.info("队友邀请超时处理: {}条", rows);
            // 通知主接打手
            for (Map<String, Object> invite : expiredInvites) {
                Long orderId = ((Number) invite.get("order_id")).longValue();
                Object pid = invite.get("player_id");
                if (pid != null) {
                    Long primaryPlayerId = ((Number) pid).longValue();
                    scheduledTaskMapper.insertSystemNotification("PLAYER", primaryPlayerId,
                            "队友邀请超时", "您的队友邀请已超时，请重新邀请其他队友", "ORDER", orderId);
                }
            }
        } finally { redisService.unlock("lock:task:invite_timeout"); }
    }
}
