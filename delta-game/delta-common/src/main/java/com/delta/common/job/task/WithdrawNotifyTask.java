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
public class WithdrawNotifyTask {
    private final RedisService redisService;
    private final ScheduledTaskMapper scheduledTaskMapper;

    @Scheduled(cron = "0 0 * * * ?")
    public void execute() {
        if (!redisService.tryLock("lock:task:withdraw_notify", 50, TimeUnit.MINUTES)) return;
        try {
            // 查询PENDING且超过24小时未处理的提现申请
            List<Map<String, Object>> withdraws = scheduledTaskMapper.selectPendingWithdraws24h();
            for (Map<String, Object> w : withdraws) {
                Long withdrawId = ((Number) w.get("id")).longValue();
                // 给所有管理员发站内消息
                scheduledTaskMapper.notifyAdminsWithdraw(withdrawId);
            }
            if (!withdraws.isEmpty()) {
                log.info("发送{}笔提现待审核提醒", withdraws.size());
            }
        } catch (Exception e) {
            log.error("提现提醒任务异常", e);
        } finally { redisService.unlock("lock:task:withdraw_notify"); }
    }
}
