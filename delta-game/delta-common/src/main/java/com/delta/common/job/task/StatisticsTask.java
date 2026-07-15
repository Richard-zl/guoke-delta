package com.delta.common.job.task;

import com.delta.common.mapper.ScheduledTaskMapper;
import com.delta.common.redis.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class StatisticsTask {
    private final RedisService redisService;
    private final ScheduledTaskMapper scheduledTaskMapper;

    @Scheduled(cron = "0 0 1 * * ?")
    public void execute() {
        if (!redisService.tryLock("lock:task:statistics", 50, TimeUnit.MINUTES)) return;
        try {
            // 计算前一天统计指标并写入statistics_daily
            scheduledTaskMapper.insertDailyStatistics();
            log.info("每日统计预计算任务完成");
        } catch (Exception e) {
            log.error("每日统计任务异常", e);
        } finally { redisService.unlock("lock:task:statistics"); }
    }
}
