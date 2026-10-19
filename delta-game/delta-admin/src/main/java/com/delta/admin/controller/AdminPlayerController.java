package com.delta.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.annotation.OpLog;
import com.delta.common.domain.PageQuery;
import com.delta.common.domain.R;
import com.delta.common.event.BusinessEvent;
import com.delta.common.exception.BusinessException;
import com.delta.common.mapper.CrossModuleMapper;
import com.delta.order.entity.Order;
import com.delta.order.service.OrderService;
import com.delta.pay.entity.Transaction;
import com.delta.pay.service.TransactionService;
import com.delta.user.entity.User;
import com.delta.user.service.UserService;
import com.delta.player.entity.Player;
import com.delta.player.entity.PlayerReferral;
import com.delta.player.entity.PlayerWallet;
import com.delta.player.entity.Withdraw;
import com.delta.player.service.PlayerFundRules;
import com.delta.player.service.PlayerReferralRules;
import com.delta.player.service.PlayerReferralService;
import com.delta.player.service.PlayerService;
import com.delta.player.service.PlayerShowcaseService;
import com.delta.player.service.PlayerWalletService;
import com.delta.player.service.PlayerWorkStatusService;
import com.delta.player.service.WithdrawService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/player")
@RequiredArgsConstructor
public class AdminPlayerController {
    private final PlayerService playerService;
    private final PlayerShowcaseService playerShowcaseService;
    private final PlayerWalletService playerWalletService;
    private final PlayerWorkStatusService playerWorkStatusService;
    private final TransactionService transactionService;
    private final WithdrawService withdrawService;
    private final OrderService orderService;
    private final UserService userService;
    private final PlayerReferralService playerReferralService;
    private final CrossModuleMapper crossModuleMapper;
    private final ApplicationEventPublisher eventPublisher;

    /** 计入「完成订单数」的订单状态 */
    private static final List<String> COMPLETED_STATUSES = List.of("CONFIRMED", "REVIEWED", "SETTLED");

    @GetMapping("/list")
    public R<Page<Player>> list(PageQuery query,
                                @RequestParam(value = "status", required = false) String status,
                                @RequestParam(value = "keyword", required = false) String keyword,
                                @RequestParam(value = "workStatus", required = false) String workStatus) {
        LambdaQueryWrapper<Player> w = new LambdaQueryWrapper<Player>()
                .eq(status != null && !status.isEmpty(), Player::getStatus, status)
                .orderByDesc(Player::getCreatedAt);
        applyKeyword(w, keyword);
        Page<Player> page = playerWorkStatusService.queryPage(query, w, workStatus);
        for (Player p : page.getRecords()) {
            fillWallet(p);
            p.setCompletedOrders(crossModuleMapper.countPlayerParticipatedOrders(p.getId(), COMPLETED_STATUSES));
        }
        return R.ok(page);
    }

    @GetMapping("/{id}")
    public R<Player> detail(@PathVariable Long id) {
        Player p = playerService.getById(id);
        if (p != null) {
            fillWallet(p);
            p.setCompletedOrders(crossModuleMapper.countPlayerParticipatedOrders(id, COMPLETED_STATUSES));
            playerWorkStatusService.enrichOne(p);
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
        playerShowcaseService.unpublishByPlayerId(id);
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
        playerShowcaseService.unpublishByPlayerId(id);
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
        if (PlayerFundRules.STATUS_RESIGNED.equals(status)) {
            throw new BusinessException("请使用离职操作");
        }
        Player p = new Player();
        p.setId(id);
        p.setStatus(status);
        playerService.updateById(p);
        if (!"ACTIVE".equals(status)) {
            playerShowcaseService.unpublishByPlayerId(id);
        }
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

    /** 管理员补款或扣款，只改可提现余额，并写入资金流水。 */
    @OpLog(module = "player", operation = "调整打手余额")
    @PutMapping("/{id}/balance")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> adjustBalance(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Player player = requirePlayer(id);
        String direction = body.get("direction") == null ? "" : body.get("direction").toString();
        if (body.get("amount") == null || body.get("amount").toString().isBlank()) {
            throw new BusinessException("请填写调整金额");
        }
        BigDecimal amount;
        try {
            amount = new BigDecimal(body.get("amount").toString().trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException("调整金额格式不正确");
        }
        BigDecimal signed = PlayerFundRules.signedAmount(direction, amount);
        String remark = PlayerFundRules.normalizeRemark(
                body.get("remark") == null ? "" : body.get("remark").toString());

        String orderNo = body.get("orderNo") == null ? "" : body.get("orderNo").toString().trim();
        Long relatedOrderId = null;
        if (!orderNo.isEmpty()) {
            Order order = orderService.getOne(new LambdaQueryWrapper<Order>()
                    .eq(Order::getOrderNo, orderNo)
                    .last("LIMIT 1"));
            if (order == null) {
                throw new BusinessException("订单不存在");
            }
            relatedOrderId = order.getId();
            String withOrder = remark + "（订单" + order.getOrderNo() + "）";
            remark = withOrder.length() > 255 ? withOrder.substring(0, 255) : withOrder;
        }

        PlayerWallet wallet = playerWalletService.getByPlayerId(id);
        if (wallet == null) {
            playerWalletService.initWallet(id);
            wallet = playerWalletService.getByPlayerId(id);
        }
        BigDecimal before = wallet.getBalance() == null ? BigDecimal.ZERO : wallet.getBalance();
        BigDecimal after = before.add(signed);
        PlayerFundRules.assertBalanceEnough(after);

        LambdaUpdateWrapper<PlayerWallet> update = new LambdaUpdateWrapper<PlayerWallet>()
                .eq(PlayerWallet::getId, wallet.getId())
                .set(PlayerWallet::getBalance, after);
        if (wallet.getBalance() == null) {
            update.isNull(PlayerWallet::getBalance);
        } else {
            update.eq(PlayerWallet::getBalance, wallet.getBalance());
        }
        if (!playerWalletService.update(update)) {
            throw new BusinessException("余额已变化，请重试");
        }

        boolean add = PlayerFundRules.DIRECTION_ADD.equals(direction);
        transactionService.record(add ? "ADMIN_RECHARGE" : "ADMIN_DEDUCT", "PLAYER", id, signed,
                before, after, relatedOrderId, null, null, remark);
        setOpLogDetail((add ? "补款" : "扣款") + " ¥" + signed.abs().toPlainString()
                + "，打手" + player.getNickname() + "（ID:" + id + "），原因：" + remark
                + "，余额 " + before.toPlainString() + " → " + after.toPlainString());
        return R.ok();
    }

    /** 打手离职：停止接单、指派和提现。 */
    @OpLog(module = "player", operation = "打手离职")
    @PutMapping("/{id}/resign")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> resign(@PathVariable Long id) {
        Player player = requirePlayer(id);
        String block = PlayerFundRules.resignBlockReason(player.getStatus());
        if (block != null) {
            throw new BusinessException(block);
        }
        Player update = new Player();
        update.setId(id);
        update.setStatus(PlayerFundRules.STATUS_RESIGNED);
        update.setIsOnline(0);
        playerService.updateById(update);
        playerShowcaseService.unpublishByPlayerId(id);
        int unbound = playerReferralService.unbindAll(id);
        String detail = "打手离职：" + player.getNickname() + "（ID:" + id + "）";
        if (unbound > 0) {
            detail += "，解绑转介绍客户" + unbound + "人";
        }
        setOpLogDetail(detail);
        eventPublisher.publishEvent(new BusinessEvent(this, "PLAYER_RESIGNED",
                "PLAYER", id, null, "您的打手账号已离职，无法继续接单和提现"));
        return R.ok();
    }

    /** 删除已离职且资金、订单都已结清的打手。 */
    @OpLog(module = "player", operation = "删除离职打手")
    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> deleteResigned(@PathVariable Long id) {
        Player player = requirePlayer(id);
        PlayerWallet wallet = playerWalletService.getByPlayerId(id);
        BigDecimal balance = wallet == null ? BigDecimal.ZERO : wallet.getBalance();
        BigDecimal pending = wallet == null ? BigDecimal.ZERO : wallet.getPendingBalance();
        BigDecimal frozen = wallet == null ? BigDecimal.ZERO : wallet.getFrozenAmount();
        int openOrders = crossModuleMapper.countPlayerOpenOrders(id);
        int pendingWithdraws = Math.toIntExact(withdrawService.count(new LambdaQueryWrapper<Withdraw>()
                .eq(Withdraw::getPlayerId, id)
                .eq(Withdraw::getStatus, "PENDING")));
        String block = PlayerFundRules.deleteBlockReason(
                player.getStatus(), balance, pending, frozen, openOrders, pendingWithdraws);
        if (block != null) {
            throw new BusinessException(block);
        }
        // 释放 openid，离职后同一微信可以重新入驻
        String openid = player.getOpenid() == null ? "" : player.getOpenid();
        String released = openid + "#resigned#" + id;
        if (released.length() > 64) {
            released = released.substring(released.length() - 64);
        }
        playerService.update(new LambdaUpdateWrapper<Player>()
                .eq(Player::getId, id)
                .set(Player::getOpenid, released)
                .set(Player::getIsOnline, 0));
        playerService.removeById(id);
        setOpLogDetail("删除离职打手：" + player.getNickname() + "（ID:" + id + "）");
        return R.ok();
    }

    /** 按用户 ID、手机号、昵称精确查找可绑定的客户。多个结果交给前端选择。 */
    @GetMapping("/referrals/search")
    public R<List<Map<String, Object>>> searchReferralUsers(@RequestParam String keyword) {
        String text = keyword == null ? "" : keyword.trim();
        if (text.isEmpty()) {
            throw new BusinessException("请输入用户ID、手机号或昵称");
        }
        Map<Long, User> found = new LinkedHashMap<>();
        if (text.matches("\\d{1,18}")) {
            User byId = userService.getById(Long.valueOf(text));
            if (byId != null) {
                found.put(byId.getId(), byId);
            }
        }
        User byPhone = userService.getByPhone(text);
        if (byPhone != null) {
            found.put(byPhone.getId(), byPhone);
        }
        List<User> byName = userService.list(new LambdaQueryWrapper<User>().eq(User::getNickname, text));
        for (User user : byName) {
            found.put(user.getId(), user);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (User user : found.values()) {
            rows.add(userBrief(user));
        }
        return R.ok(rows);
    }

    @GetMapping("/{id}/referrals")
    public R<List<Map<String, Object>>> listReferrals(@PathVariable Long id) {
        requirePlayer(id);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PlayerReferral referral : playerReferralService.listActive(id)) {
            User user = userService.getById(referral.getUserId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", referral.getId());
            row.put("userId", referral.getUserId());
            row.put("nickname", user == null ? "" : user.getNickname());
            row.put("phone", user == null ? "" : user.getPhone());
            row.put("avatar", user == null ? "" : user.getAvatar());
            row.put("rate", referral.getRate());
            row.put("ratePercent", referral.getRate().multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString());
            row.put("boundAt", referral.getBoundAt());
            rows.add(row);
        }
        return R.ok(rows);
    }

    @OpLog(module = "player", operation = "新增转介绍客户")
    @PostMapping("/{id}/referrals")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> bindReferral(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Player player = requirePlayer(id);
        Long userId = longValue(body.get("userId"), "请选择客户");
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException("未找到客户");
        }
        BigDecimal rate = PlayerReferralRules.normalizeRate(decimalValue(body.get("ratePercent"), "请填写介绍比例"));
        playerReferralService.bind(id, userId, rate);
        setOpLogDetail("新增转介绍：打手" + player.getNickname() + "（ID:" + id + "），客户"
                + user.getNickname() + "（ID:" + userId + "），比例" + body.get("ratePercent") + "%");
        return R.ok();
    }

    @OpLog(module = "player", operation = "调整转介绍比例")
    @PutMapping("/{id}/referrals/{referralId}")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> updateReferralRate(@PathVariable Long id, @PathVariable Long referralId,
                                      @RequestBody Map<String, Object> body) {
        Player player = requirePlayer(id);
        BigDecimal rate = PlayerReferralRules.normalizeRate(decimalValue(body.get("ratePercent"), "请填写介绍比例"));
        playerReferralService.updateRate(id, referralId, rate);
        setOpLogDetail("调整转介绍比例：打手" + player.getNickname() + "（ID:" + id + "），绑定"
                + referralId + "，比例" + body.get("ratePercent") + "%");
        return R.ok();
    }

    @OpLog(module = "player", operation = "解绑转介绍客户")
    @DeleteMapping("/{id}/referrals/{referralId}")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> unbindReferral(@PathVariable Long id, @PathVariable Long referralId) {
        Player player = requirePlayer(id);
        playerReferralService.unbind(id, referralId);
        setOpLogDetail("解绑转介绍：打手" + player.getNickname() + "（ID:" + id + "），绑定" + referralId);
        return R.ok();
    }

    private Map<String, Object> userBrief(User user) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", user.getId());
        row.put("nickname", user.getNickname());
        row.put("phone", user.getPhone());
        row.put("avatar", user.getAvatar());
        return row;
    }

    private Long longValue(Object raw, String message) {
        if (raw == null || raw.toString().isBlank()) {
            throw new BusinessException(message);
        }
        try {
            return Long.valueOf(raw.toString().trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(message);
        }
    }

    private BigDecimal decimalValue(Object raw, String message) {
        if (raw == null || raw.toString().isBlank()) {
            throw new BusinessException(message);
        }
        try {
            return new BigDecimal(raw.toString().trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException("介绍比例格式不正确");
        }
    }

    private Player requirePlayer(Long id) {
        Player player = playerService.getById(id);
        if (player == null) {
            throw new BusinessException("打手不存在");
        }
        return player;
    }

    private void fillWallet(Player player) {
        PlayerWallet wallet = playerWalletService.getByPlayerId(player.getId());
        player.setBalance(wallet == null || wallet.getBalance() == null ? BigDecimal.ZERO : wallet.getBalance());
        player.setPendingBalance(wallet == null || wallet.getPendingBalance() == null
                ? BigDecimal.ZERO : wallet.getPendingBalance());
        player.setFrozenAmount(wallet == null || wallet.getFrozenAmount() == null
                ? BigDecimal.ZERO : wallet.getFrozenAmount());
    }

    /** 昵称、手机号模糊搜索；纯数字时同时按打手 ID 精确匹配。 */
    private void applyKeyword(LambdaQueryWrapper<Player> wrapper, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        String trimmed = keyword.trim();
        Long playerId = null;
        if (trimmed.matches("\\d{1,18}")) {
            playerId = Long.valueOf(trimmed);
        }
        Long matchedId = playerId;
        wrapper.and(qw -> {
            qw.like(Player::getNickname, trimmed).or().like(Player::getPhone, trimmed);
            if (matchedId != null) {
                qw.or().eq(Player::getId, matchedId);
            }
        });
    }

    private void setOpLogDetail(String detail) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return;
        }
        attrs.getRequest().setAttribute("opLogDetail", detail);
    }
}
