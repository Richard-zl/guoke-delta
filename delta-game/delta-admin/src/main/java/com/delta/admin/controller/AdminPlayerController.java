package com.delta.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.annotation.OpLog;
import com.delta.common.domain.PageQuery;
import com.delta.common.domain.R;
import com.delta.common.event.BusinessEvent;
import com.delta.order.entity.Order;
import com.delta.order.service.OrderService;
import com.delta.pay.entity.Transaction;
import com.delta.pay.service.TransactionService;
import com.delta.player.entity.Player;
import com.delta.player.entity.PlayerWallet;
import com.delta.player.service.PlayerService;
import com.delta.player.service.PlayerWalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/admin/player")
@RequiredArgsConstructor
public class AdminPlayerController {
    private final PlayerService playerService;
    private final PlayerWalletService playerWalletService;
    private final OrderService orderService;
    private final TransactionService transactionService;
    private final ApplicationEventPublisher eventPublisher;

    @GetMapping("/list")
    public R<Page<Player>> list(PageQuery query,
                                @RequestParam(value = "status", required = false) String status,
                                @RequestParam(value = "keyword", required = false) String keyword) {
        LambdaQueryWrapper<Player> w = new LambdaQueryWrapper<Player>()
                .eq(status != null && !status.isEmpty(), Player::getStatus, status)
                .and(keyword != null && !keyword.isEmpty(),
                        qw -> qw.like(Player::getNickname, keyword).or().like(Player::getPhone, keyword))
                .orderByDesc(Player::getCreatedAt);
        Page<Player> page = playerService.page(new Page<>(query.getPageNum(), query.getPageSize()), w);
        for (Player p : page.getRecords()) {
            PlayerWallet wallet = playerWalletService.getByPlayerId(p.getId());
            p.setBalance(wallet != null ? wallet.getBalance() : BigDecimal.ZERO);
            long completed = orderService.count(new LambdaQueryWrapper<Order>()
                    .eq(Order::getPlayerId, p.getId())
                    .in(Order::getStatus, "CONFIRMED", "REVIEWED", "SETTLED"));
            p.setCompletedOrders((int) completed);
        }
        return R.ok(page);
    }

    @GetMapping("/{id}")
    public R<Player> detail(@PathVariable Long id) {
        Player p = playerService.getById(id);
        if (p != null) {
            PlayerWallet wallet = playerWalletService.getByPlayerId(id);
            p.setBalance(wallet != null ? wallet.getBalance() : BigDecimal.ZERO);
            long completed = orderService.count(new LambdaQueryWrapper<Order>()
                    .eq(Order::getPlayerId, id)
                    .in(Order::getStatus, "CONFIRMED", "REVIEWED", "SETTLED"));
            p.setCompletedOrders((int) completed);
        }
        return R.ok(p);
    }

    @GetMapping("/{id}/transactions")
    public R<Page<Transaction>> transactions(@PathVariable Long id, PageQuery query) {
        LambdaQueryWrapper<Transaction> w = new LambdaQueryWrapper<Transaction>()
                .eq(Transaction::getUserType, "PLAYER")
                .eq(Transaction::getUserId, id)
                .orderByDesc(Transaction::getCreatedAt);
        return R.ok(transactionService.page(new Page<>(query.getPageNum(), query.getPageSize()), w));
    }

    @OpLog(module = "player", operation = "审核通过")
    @PutMapping("/{id}/approve")
    public R<Void> approve(@PathVariable Long id) {
        Player p = new Player();
        p.setId(id);
        p.setStatus("ACTIVE");
        playerService.updateById(p);
        eventPublisher.publishEvent(new BusinessEvent(this, "PLAYER_APPROVED",
                "PLAYER", id, null, "您的入驻申请已通过审核"));
        return R.ok();
    }

    @OpLog(module = "player", operation = "审核驳回")
    @PutMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id, @RequestParam("reason") String reason) {
        Player p = new Player();
        p.setId(id);
        p.setStatus("REJECTED");
        p.setRejectReason(reason);
        playerService.updateById(p);
        eventPublisher.publishEvent(new BusinessEvent(this, "PLAYER_REJECTED",
                "PLAYER", id, null, "入驻申请被驳回：" + reason));
        return R.ok();
    }

    @OpLog(module = "player", operation = "冻结打手")
    @PutMapping("/{id}/freeze")
    public R<Void> freeze(@PathVariable Long id, @RequestBody Player body) {
        Player p = new Player();
        p.setId(id);
        p.setStatus("FROZEN");
        p.setFrozenUntil(body.getFrozenUntil());
        playerService.updateById(p);
        eventPublisher.publishEvent(new BusinessEvent(this, "PLAYER_FROZEN",
                "PLAYER", id, null, "您的账号已被冻结，冻结期间无法接单和提现"));
        return R.ok();
    }

    @OpLog(module = "player", operation = "解冻打手")
    @PutMapping("/{id}/unfreeze")
    public R<Void> unfreeze(@PathVariable Long id) {
        Player p = new Player();
        p.setId(id);
        p.setStatus("ACTIVE");
        p.setFrozenUntil(null);
        playerService.updateById(p);
        eventPublisher.publishEvent(new BusinessEvent(this, "PLAYER_UNFROZEN",
                "PLAYER", id, null, "您的账号已解冻"));
        return R.ok();
    }

    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam("status") String status) {
        Player p = new Player();
        p.setId(id);
        p.setStatus(status);
        playerService.updateById(p);
        return R.ok();
    }

    @OpLog(module = "player", operation = "更新打手信息")
    @PutMapping("/{id}")
    public R<Void> updatePlayer(@PathVariable Long id, @RequestBody Player body) {
        Player p = new Player();
        p.setId(id);
        p.setNickname(body.getNickname());
        p.setRealName(body.getRealName());
        playerService.updateById(p);
        return R.ok();
    }
}
