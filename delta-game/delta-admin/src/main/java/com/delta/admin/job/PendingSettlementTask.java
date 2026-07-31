package com.delta.admin.job;

import com.delta.common.redis.service.RedisService;
import com.delta.player.service.PlayerIncomeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 每小时释放到期待入账订单到可提现余额。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingSettlementTask {

    private final PlayerIncomeService playerIncomeService;
    private final RedisService redisService;

    @Scheduled(cron = "0 0 * * * ?")
    public void execute() {
        if (!redisService.tryLock("lock:task:pending_settlement", 50, TimeUnit.MINUTES)) {
            return;
        }
        try {
            int n = playerIncomeService.releaseDueSettlements(200);
            if (n > 0) {
                log.info("待入账释放完成: {} 单", n);
            }
        } finally {
            redisService.unlock("lock:task:pending_settlement");
        }
    }
}
