package com.delta.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.annotation.OpLog;
import com.delta.common.domain.PageQuery;
import com.delta.common.domain.R;
import com.delta.common.event.BusinessEvent;
import com.delta.common.exception.BusinessException;
import com.delta.common.security.utils.SecurityUtils;
import com.delta.pay.service.TransactionService;
import com.delta.player.entity.Player;
import com.delta.player.entity.PlayerAccount;
import com.delta.player.entity.PlayerWallet;
import com.delta.player.entity.Withdraw;
import com.delta.player.service.PlayerService;
import com.delta.player.service.PlayerWalletService;
import com.delta.player.service.PlayerAccountService;
import com.delta.player.service.WithdrawService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/admin/withdraw")
@RequiredArgsConstructor
public class AdminWithdrawController {
    private final WithdrawService withdrawService;
    private final PlayerService playerService;
    private final PlayerAccountService playerAccountService;
    private final PlayerWalletService playerWalletService;
    private final TransactionService transactionService;
    private final ApplicationEventPublisher eventPublisher;

    @GetMapping("/list")
    public R<Page<Withdraw>> list(PageQuery query, @RequestParam(value = "status", required = false) String status) {
        Page<Withdraw> page = withdrawService.page(new Page<>(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<Withdraw>().eq(status != null && !status.isEmpty(), Withdraw::getStatus, status)
                        .orderByDesc(Withdraw::getCreatedAt));
        // 填充打手信息和收款账户信息
        for (Withdraw w : page.getRecords()) {
            if (w.getPlayerId() != null) {
                Player player = playerService.getById(w.getPlayerId());
                if (player != null) {
                    w.setPlayerName(player.getNickname());
                    w.setPlayerPhone(player.getPhone());
                }
            }
            if (w.getAccountId() != null) {
                PlayerAccount account = playerAccountService.getById(w.getAccountId());
                if (account != null) {
                    w.setAccountType(account.getType());
                    w.setAccountNo(account.getAccountNo());
                    w.setAccountName(account.getAccountName());
                    w.setQrcodeUrl(account.getQrcodeUrl());
                }
            }
        }
        return R.ok(page);
    }

    @OpLog(module = "withdraw", operation = "处理提现")
    @PutMapping("/{id}/process")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> process(@PathVariable Long id, @RequestBody Withdraw w) {
        Withdraw withdraw = withdrawService.getById(id);
        if (withdraw == null || !"PENDING".equals(withdraw.getStatus()))
            throw new BusinessException("提现记录不存在或已处理");

        withdraw.setStatus("COMPLETED");
        withdraw.setPayMethod(w.getPayMethod());
        withdraw.setProofImage(w.getProofImage());
        withdraw.setProcessedBy(SecurityUtils.getUserId());
        withdraw.setProcessedAt(w.getProcessedAt() != null ? w.getProcessedAt() : LocalDateTime.now());
        withdrawService.updateById(withdraw);

        PlayerWallet wallet = playerWalletService.getByPlayerId(withdraw.getPlayerId());
        if (wallet != null) {
            BigDecimal balanceBefore = wallet.getBalance();
            wallet.setFrozenAmount(wallet.getFrozenAmount().subtract(withdraw.getAmount()));
            playerWalletService.updateById(wallet);
            transactionService.record("WITHDRAW_COMPLETE", "PLAYER", withdraw.getPlayerId(),
                    withdraw.getAmount().negate(), balanceBefore, wallet.getBalance(),
                    null, null, withdraw.getId(), "提现完成");
        }

        eventPublisher.publishEvent(new BusinessEvent(this, "WITHDRAW_COMPLETED",
                "PLAYER", withdraw.getPlayerId(), withdraw.getId(),
                "提现已完成，金额 " + withdraw.getAmount() + " 元，请查看打款凭证"));
        return R.ok();
    }

    @OpLog(module = "withdraw", operation = "拒绝提现")
    @PutMapping("/{id}/reject")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> reject(@PathVariable Long id, @RequestParam("reason") String reason) {
        Withdraw withdraw = withdrawService.getById(id);
        if (withdraw == null || !"PENDING".equals(withdraw.getStatus()))
            throw new BusinessException("提现记录不存在或已处理");

        withdraw.setStatus("REJECTED");
        withdraw.setRejectReason(reason);
        withdraw.setProcessedBy(SecurityUtils.getUserId());
        withdraw.setProcessedAt(LocalDateTime.now());
        withdrawService.updateById(withdraw);

        PlayerWallet wallet = playerWalletService.getByPlayerId(withdraw.getPlayerId());
        if (wallet != null) {
            BigDecimal balanceBefore = wallet.getBalance();
            wallet.setFrozenAmount(wallet.getFrozenAmount().subtract(withdraw.getAmount()));
            wallet.setBalance(wallet.getBalance().add(withdraw.getAmount()));
            playerWalletService.updateById(wallet);
            transactionService.record("WITHDRAW_REJECT", "PLAYER", withdraw.getPlayerId(),
                    withdraw.getAmount(), balanceBefore, wallet.getBalance(),
                    null, null, withdraw.getId(), "提现被拒绝，资金退回: " + reason);
        }

        eventPublisher.publishEvent(new BusinessEvent(this, "WITHDRAW_REJECTED",
                "PLAYER", withdraw.getPlayerId(), withdraw.getId(),
                "提现被拒绝，原因：" + reason + "，资金已退回余额"));
        return R.ok();
    }
}
