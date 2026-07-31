package com.delta.player.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.domain.PageQuery;
import com.delta.common.domain.R;
import com.delta.common.event.BusinessEvent;
import com.delta.common.exception.BusinessException;
import com.delta.common.security.utils.SecurityUtils;
import com.delta.pay.service.TransactionService;
import com.delta.player.dto.WithdrawRequest;
import com.delta.player.entity.Player;
import com.delta.player.entity.PlayerWallet;
import com.delta.player.entity.Withdraw;
import com.delta.player.service.PlayerService;
import com.delta.player.service.PlayerWalletService;
import com.delta.player.service.WithdrawService;
import com.delta.player.util.WithdrawTimeWindowHelper;
import com.delta.system.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/player/withdraw")
@RequiredArgsConstructor
public class WithdrawController {
    private final WithdrawService withdrawService;
    private final PlayerService playerService;
    private final PlayerWalletService playerWalletService;
    private final TransactionService transactionService;
    private final StringRedisTemplate redisTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final SysConfigService sysConfigService;

    @GetMapping({"" , "/list"})
    public R<Page<Withdraw>> list(PageQuery query) {
        Long playerId = SecurityUtils.getUserId();
        return R.ok(withdrawService.page(new Page<>(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<Withdraw>().eq(Withdraw::getPlayerId, playerId).orderByDesc(Withdraw::getCreatedAt)));
    }

    @GetMapping("/window")
    public R<Map<String, Object>> window() {
        var windows = WithdrawTimeWindowHelper.parseWindows(
                sysConfigService.getConfigValue("withdraw.time_windows", ""));
        boolean in = WithdrawTimeWindowHelper.isInWindow(LocalDateTime.now(), windows);
        String text = WithdrawTimeWindowHelper.buildWindowsText(windows);
        Map<String, Object> data = new HashMap<>();
        data.put("inWithdrawWindow", in);
        data.put("windowsText", text);
        data.put("windows", windows);
        data.put("nextWindowHint", in ? "" : text);
        return R.ok(data);
    }

    @GetMapping("/{id}")
    public R<Withdraw> detail(@PathVariable Long id) {
        Long playerId = SecurityUtils.getUserId();
        Withdraw w = withdrawService.getById(id);
        if (w == null || !w.getPlayerId().equals(playerId)) {
            throw new BusinessException("记录不存在");
        }
        return R.ok(w);
    }

    @PostMapping
    public R<Void> apply(@RequestBody WithdrawRequest req) {
        Long playerId = SecurityUtils.getUserId();

        // 0. 冻结期间不允许提现
        Player player = playerService.getById(playerId);
        if (player != null && "FROZEN".equals(player.getStatus())
                && player.getFrozenUntil() != null && player.getFrozenUntil().isAfter(LocalDateTime.now())) {
            throw new BusinessException("账号已被冻结，冻结期间无法提现");
        }

        // 1. 最低提现金额校验
        String minStr = sysConfigService.getConfigValue("withdraw.min_amount", "10");
        BigDecimal minAmount = new BigDecimal(minStr);
        if (req.getAmount() == null || req.getAmount().compareTo(minAmount) < 0) {
            throw new BusinessException("最低提现金额为" + minAmount + "元");
        }

        var windows = WithdrawTimeWindowHelper.parseWindows(
                sysConfigService.getConfigValue("withdraw.time_windows", ""));
        if (!WithdrawTimeWindowHelper.isInWindow(LocalDateTime.now(), windows)) {
            throw new BusinessException("当前不在提现时间。可提现时间："
                    + WithdrawTimeWindowHelper.buildWindowsText(windows));
        }

        // 1.5 每日提现次数上限校验
        int maxDailyCount = Integer.parseInt(sysConfigService.getConfigValue("withdraw.max_daily_count", "3"));
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        long todayCount = withdrawService.count(new LambdaQueryWrapper<Withdraw>()
                .eq(Withdraw::getPlayerId, playerId)
                .ge(Withdraw::getCreatedAt, todayStart));
        if (todayCount >= maxDailyCount) {
            throw new BusinessException("每日最多提现" + maxDailyCount + "次");
        }

        // 2. 分布式锁，防止并发提现
        String lockKey = "withdraw:" + playerId;
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", Duration.ofSeconds(10));
        if (Boolean.FALSE.equals(locked)) {
            throw new BusinessException("操作太频繁，请稍后重试");
        }
        try {
            doWithdraw(playerId, req);
        } finally {
            redisTemplate.delete(lockKey);
        }
        return R.ok();
    }

    @Transactional(rollbackFor = Exception.class)
    public void doWithdraw(Long playerId, WithdrawRequest req) {
        // 3. 校验余额
        PlayerWallet wallet = playerWalletService.getByPlayerId(playerId);
        if (wallet == null || wallet.getBalance().compareTo(req.getAmount()) < 0) {
            throw new BusinessException("余额不足");
        }

        BigDecimal balanceBefore = wallet.getBalance();

        // 4. 扣减余额，增加冻结
        wallet.setBalance(wallet.getBalance().subtract(req.getAmount()));
        wallet.setFrozenAmount(wallet.getFrozenAmount().add(req.getAmount()));
        playerWalletService.updateById(wallet);

        // 5. 创建提现记录
        Withdraw withdraw = new Withdraw();
        withdraw.setPlayerId(playerId);
        withdraw.setAccountId(req.getAccountId());
        withdraw.setAmount(req.getAmount());
        withdraw.setStatus("PENDING");
        withdrawService.save(withdraw);

        // 6. 创建交易记录
        transactionService.record(
                "WITHDRAW",
                "PLAYER",
                playerId,
                req.getAmount().negate(), // 提现为负数
                balanceBefore,
                wallet.getBalance(),
                null,
                null,
                withdraw.getId(),
                "提现申请"
        );

        // 7. 通知管理员有新提现待处理
        eventPublisher.publishEvent(new BusinessEvent(this, "WITHDRAW_APPLY",
                "ADMIN", null, withdraw.getId(), "新提现申请待处理，金额: " + req.getAmount() + "元"));
    }
}
