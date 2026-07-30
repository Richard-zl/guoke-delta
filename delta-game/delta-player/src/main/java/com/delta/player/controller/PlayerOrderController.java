package com.delta.player.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.domain.PageQuery;
import com.delta.common.domain.R;
import com.delta.common.enums.OrderStatusEnum;
import com.delta.common.event.BusinessEvent;
import com.delta.common.exception.BusinessException;
import com.delta.common.utils.ImageListUtils;
import com.delta.common.security.utils.SecurityUtils;
import com.delta.common.chat.service.ChatSessionService;
import com.delta.common.chat.util.ChatParticipantId;
import com.delta.order.entity.Order;
import com.delta.order.entity.OrderPlayer;
import com.delta.order.entity.OrderProgress;
import com.delta.order.service.OrderDisplayEnricher;
import com.delta.order.service.OrderPlayerService;
import com.delta.order.service.OrderProgressService;
import com.delta.order.service.OrderService;
import com.delta.player.entity.Player;
import com.delta.player.service.PlayerService;
import com.delta.system.service.SysConfigService;
import com.delta.common.mapper.CrossModuleMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 打手订单操作接口（接单/拒绝指派/队友邀请）
 */
@RestController
@RequestMapping("/player/order")
@RequiredArgsConstructor
public class PlayerOrderController {
    private final OrderService orderService;
    private final OrderPlayerService orderPlayerService;
    private final OrderProgressService orderProgressService;
    private final PlayerService playerService;
    private final ChatSessionService chatSessionService;
    private final ApplicationEventPublisher eventPublisher;
    private final SysConfigService sysConfigService;
    private final CrossModuleMapper crossModuleMapper;
    private final OrderDisplayEnricher orderDisplayEnricher;

    /**
     * 接单大厅：浏览可接订单。
     * categoryIds：可选，逗号分隔的分类 id（主分类+子分类），筛选该主分类下所有子分类订单。
     */
    @GetMapping("/hall")
    public R<Page<Order>> hall(PageQuery query,
                               @RequestParam(required = false) String categoryIds) {
        Long playerId = SecurityUtils.getUserId();
        LambdaQueryWrapper<Order> w = new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, "PAID")
                .isNull(Order::getPlayerId)
                // 显示未指定打手的订单 + 指定给当前打手的订单
                .and(aw -> aw.isNull(Order::getDesignatedPlayerId)
                        .or().eq(Order::getDesignatedPlayerId, playerId))
                .orderByDesc(Order::getCreatedAt);
        List<Long> ids = parseCategoryIds(categoryIds);
        if (!ids.isEmpty()) {
            String inList = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
            w.inSql(Order::getProductId, "SELECT id FROM product WHERE category_id IN (" + inList + ")");
        }
        Page<Order> page = orderService.page(new Page<>(query.getPageNum(), query.getPageSize()), w);
        // 接单前隐藏游戏账号（安全要求）
        page.getRecords().forEach(o -> {
            o.setGameAccount(null);
        });
        return R.ok(page);
    }

    /**
     * 打手查看自己被指派的订单（包括作为主打手和辅助打手）
     */
    @GetMapping("/my-orders")
    public R<Page<Order>> myOrders(PageQuery query,
                                   @RequestParam(required = false) String status) {
        Long playerId = SecurityUtils.getUserId();
        LambdaQueryWrapper<Order> wrapper = orderDisplayEnricher.buildPlayerOwnedWrapper(playerId, status);
        Page<Order> page = orderService.page(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        orderDisplayEnricher.enrichList(page.getRecords());
        return R.ok(page);
    }

    /**
     * 队友邀请列表（支持 type=received/sent）
     */
    @GetMapping("/invite-list")
    public R<List<OrderPlayer>> inviteList(@RequestParam(defaultValue = "received") String type) {
        Long playerId = SecurityUtils.getUserId();
        LambdaQueryWrapper<OrderPlayer> w = new LambdaQueryWrapper<OrderPlayer>()
                .eq(OrderPlayer::getRole, "TEAMMATE")
                .orderByDesc(OrderPlayer::getInvitedAt);
        if ("sent".equals(type)) {
            w.eq(OrderPlayer::getInvitedBy, playerId);
        } else {
            w.eq(OrderPlayer::getPlayerId, playerId);
        }
        List<OrderPlayer> list = orderPlayerService.list(w);
        // 富化展示字段
        for (OrderPlayer op : list) {
            // 邀请人信息
            if (op.getInvitedBy() != null) {
                Player from = playerService.getById(op.getInvitedBy());
                if (from != null) {
                    op.setFromNickname(from.getNickname());
                    op.setFromAvatar(from.getAvatar());
                }
            }
            // 被邀请人信息
            Player to = playerService.getById(op.getPlayerId());
            if (to != null) {
                op.setToNickname(to.getNickname());
                op.setToAvatar(to.getAvatar());
            }
            // 订单号
            Order order = orderService.getById(op.getOrderId());
            if (order != null) {
                op.setOrderNo(order.getOrderNo());
            }
        }
        return R.ok(list);
    }

    /**
     * 查看订单进度（打手端）
     */
    @GetMapping("/{orderId}/progress")
    public R<java.util.List<OrderProgress>> viewProgress(@PathVariable Long orderId) {
        return R.ok(orderProgressService.list(
                new LambdaQueryWrapper<OrderProgress>()
                        .eq(OrderProgress::getOrderId, orderId)
                        .orderByAsc(OrderProgress::getCreatedAt)));
    }

    /**
     * 打手接单（校验打手状态后委托给OrderService）
     */
    @PostMapping("/{orderId}/accept")
    public R<Void> accept(@PathVariable Long orderId) {
        Long playerId = SecurityUtils.getUserId();
        Player player = playerService.getById(playerId);
        if (player == null || !"ACTIVE".equals(player.getStatus())) {
            // 冻结期间给出更明确的提示
            if (player != null && "FROZEN".equals(player.getStatus())) {
                return R.fail("账号已被冻结，冻结期间无法接单");
            }
            return R.fail("打手状态不允许接单");
        }
        // 接单上限校验
        int maxActive = Integer.parseInt(sysConfigService.getConfigValue("order.max_active_per_player", "5"));
        long activeCount = orderService.count(new LambdaQueryWrapper<Order>()
                .eq(Order::getPlayerId, playerId)
                .in(Order::getStatus, "ACCEPTED", "IN_PROGRESS", "WAITING_TEAMMATE"));
        if (activeCount >= maxActive) {
            return R.fail("您当前进行中的订单已达上限(" + maxActive + "单)");
        }
        orderService.acceptOrder(orderId, playerId);
        return R.ok();
    }

    /**
     * 打手拒绝指派
     */
    @PostMapping("/{orderId}/reject-assign")
    public R<Void> rejectAssign(@PathVariable Long orderId,
                                @RequestBody(required = false) java.util.Map<String, String> body,
                                @RequestParam(value = "reason", required = false) String reasonParam) {
        Long playerId = SecurityUtils.getUserId();
        String reason = reasonParam;
        if (reason == null && body != null) {
            reason = body.getOrDefault("reason", "");
        }
        if (reason == null) reason = "";
        orderService.rejectAssign(orderId, playerId, reason);
        return R.ok();
    }

    /**
     * 邀请队友
     */
    @PostMapping("/{orderId}/invite-teammate")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> inviteTeammate(@PathVariable Long orderId, @RequestBody InviteRequest req) {
        Long playerId = SecurityUtils.getUserId();
        Order order = orderService.getById(orderId);
        if (order == null) return R.fail("订单不存在");
        String st = order.getStatus();
        // 接单后（ACCEPTED）、等待队友（WAITING_TEAMMATE）以及服务进行中（IN_PROGRESS）均允许邀请队友
        if (!OrderStatusEnum.ACCEPTED.name().equals(st)
                && !OrderStatusEnum.WAITING_TEAMMATE.name().equals(st)
                && !OrderStatusEnum.IN_PROGRESS.name().equals(st)) {
            return R.fail("当前状态不允许邀请队友");
        }
        if (!playerId.equals(order.getPlayerId())) return R.fail("只有主打手可以邀请队友");

        // 校验不能邀请自己
        if (req.getInviteePlayerId().equals(playerId)) {
            return R.fail("不能邀请自己作为队友");
        }

        // 校验被邀请打手
        Player invitee = playerService.getById(req.getInviteePlayerId());
        if (invitee == null || !"ACTIVE".equals(invitee.getStatus())) {
            return R.fail("被邀请打手不可用");
        }

        // 自定义分成金额校验
        if ("CUSTOM".equals(req.getSplitType())) {
            if (req.getCustomAmount() == null || req.getCustomAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                return R.fail("自定义分成金额必须大于0");
            }
            // 校验不超过打手总收入（订单金额 * (1 - 抽成比例)）
            java.math.BigDecimal rate = order.getCommissionRate() != null
                    ? order.getCommissionRate()
                    : new java.math.BigDecimal(sysConfigService.getConfigValue("settlement.commission_rate", "0.2"));
            java.math.BigDecimal playerTotal = order.getAmount()
                    .multiply(java.math.BigDecimal.ONE.subtract(rate));
            if (req.getCustomAmount().compareTo(playerTotal) > 0) {
                return R.fail("自定义金额不可超过打手总收入");
            }
        }

        // 仅拦截当前有效队友（待处理/已接受）；换人后的 REPLACED 等历史记录允许重新邀请
        OrderPlayer activeRecord = orderPlayerService.getOne(new LambdaQueryWrapper<OrderPlayer>()
                .eq(OrderPlayer::getOrderId, orderId)
                .eq(OrderPlayer::getPlayerId, req.getInviteePlayerId())
                .in(OrderPlayer::getStatus, "INVITED", "ACCEPTED")
                .last("LIMIT 1"));
        if (activeRecord != null) {
            if ("ACCEPTED".equals(activeRecord.getStatus())) {
                return R.fail("该打手已是当前队友");
            }
            return R.fail("已邀请过该打手，请等待对方响应");
        }

        // 校验是否有未响应的邀请
        long pendingInvite = orderPlayerService.count(new LambdaQueryWrapper<OrderPlayer>()
                .eq(OrderPlayer::getOrderId, orderId)
                .eq(OrderPlayer::getStatus, "INVITED"));
        if (pendingInvite > 0) return R.fail("已有未响应的邀请，请等待或取消后重新邀请");

        long acceptedTeammate = orderPlayerService.count(new LambdaQueryWrapper<OrderPlayer>()
                .eq(OrderPlayer::getOrderId, orderId)
                .eq(OrderPlayer::getRole, "TEAMMATE")
                .eq(OrderPlayer::getStatus, "ACCEPTED"));
        if (acceptedTeammate > 0) return R.fail("已有队友，如需更换请使用换人功能");

        LocalDateTime now = LocalDateTime.now();
        OrderPlayer historical = orderPlayerService.getOne(new LambdaQueryWrapper<OrderPlayer>()
                .eq(OrderPlayer::getOrderId, orderId)
                .eq(OrderPlayer::getPlayerId, req.getInviteePlayerId())
                .last("LIMIT 1"));
        OrderPlayer op;
        if (historical != null) {
            op = historical;
            // 曾当过主接等场景会复用历史行，必须改成 TEAMMATE，否则邀请列表/超时任务按 role 过滤不到
            op.setRole("TEAMMATE");
            op.setStatus("INVITED");
            op.setSplitType(req.getSplitType());
            op.setSplitAmount(req.getCustomAmount());
            op.setInvitedBy(playerId);
            op.setInvitedAt(now);
            op.setInviteDeadline(now.plusMinutes(5));
            op.setAcceptedAt(null);
            op.setRejectedAt(null);
            orderPlayerService.updateById(op);
        } else {
            op = new OrderPlayer();
            op.setOrderId(orderId);
            op.setPlayerId(req.getInviteePlayerId());
            op.setRole("TEAMMATE");
            op.setStatus("INVITED");
            op.setSplitType(req.getSplitType());
            op.setSplitAmount(req.getCustomAmount());
            op.setInvitedBy(playerId);
            op.setInvitedAt(now);
            op.setInviteDeadline(now.plusMinutes(5));
            op.setCreatedAt(now);
            orderPlayerService.save(op);
        }

        // 如果当前是 ACCEPTED，自动转入 WAITING_TEAMMATE
        if (OrderStatusEnum.ACCEPTED.name().equals(order.getStatus())) {
            order.setStatus(OrderStatusEnum.WAITING_TEAMMATE.name());
            order.setTeammateDeadline(LocalDateTime.now().plusHours(2));
            order.setUpdatedAt(LocalDateTime.now());
            orderService.updateById(order);
        }

        // 记录进度（分成方式用中文）
        java.util.Map<String, String> splitNames = java.util.Map.of(
                "FIFTY_FIFTY", "五五开", "FORTY_SIXTY", "四六开",
                "THIRTY_SEVENTY", "三七开", "CUSTOM", "自定义金额");
        String splitCn = splitNames.getOrDefault(req.getSplitType(), "平分");
        addProgress(orderId, "TEAMMATE_INVITED", "PLAYER", playerId,
                "邀请队友 " + invitee.getNickname() + "，分成方式: " + splitCn);
        // 通知被邀请队友
        eventPublisher.publishEvent(new BusinessEvent(this, "TEAMMATE_INVITED",
                "PLAYER", req.getInviteePlayerId(), orderId,
                "您收到一份组队邀请，请尽快确认"));
        return R.ok();
    }

    /**
     * 接受队友邀请
     */
    @PostMapping("/{orderId}/accept-invite")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> acceptInvite(@PathVariable Long orderId) {
        Long playerId = SecurityUtils.getUserId();
        OrderPlayer op = orderPlayerService.getOne(new LambdaQueryWrapper<OrderPlayer>()
                .eq(OrderPlayer::getOrderId, orderId)
                .eq(OrderPlayer::getPlayerId, playerId)
                .eq(OrderPlayer::getStatus, "INVITED"));
        if (op == null) return R.fail("未找到邀请记录");
        if (op.getInviteDeadline() != null && op.getInviteDeadline().isBefore(LocalDateTime.now())) {
            return R.fail("邀请已过期");
        }
        op.setStatus("ACCEPTED");
        op.setAcceptedAt(LocalDateTime.now());
        orderPlayerService.updateById(op);

        // 检查是否已凑齐人数
        Order order = orderService.getById(orderId);
        long acceptedCount = orderPlayerService.count(new LambdaQueryWrapper<OrderPlayer>()
                .eq(OrderPlayer::getOrderId, orderId)
                .eq(OrderPlayer::getStatus, "ACCEPTED"));
        // 记录进度
        addProgress(orderId, "TEAMMATE_ACCEPTED", "PLAYER", playerId, "队友接受邀请");

        // 创建打手-队友私聊会话（编码ID：PLAYER=2e9+id）
        chatSessionService.findOrCreate(ChatParticipantId.encodePlayer(order.getPlayerId()), ChatParticipantId.encodePlayer(playerId));

        // 同步辅助打手到 order.player_id2（与客服双人指派一致，便于后台/列表展示）
        boolean orderChanged = false;
        if (order.getPlayerId2() == null) {
            order.setPlayerId2(playerId);
            orderChanged = true;
        }
        // 队友接受后，订单回到 ACCEPTED（主打手可继续邀请更多队友或开始服务）
        if (OrderStatusEnum.WAITING_TEAMMATE.name().equals(order.getStatus())) {
            order.setStatus(OrderStatusEnum.ACCEPTED.name());
            orderChanged = true;
        }
        if (orderChanged) {
            order.setUpdatedAt(LocalDateTime.now());
            orderService.updateById(order);
        }
        // 通知主接打手和用户
        eventPublisher.publishEvent(new BusinessEvent(this, "TEAMMATE_ACCEPTED",
                "PLAYER", order.getPlayerId(), orderId, "队友已加入，可以开始服务"));
        eventPublisher.publishEvent(new BusinessEvent(this, "TEAM_READY",
                "USER", order.getUserId(), orderId, "打手已组队完成"));
        return R.ok();
    }

    /**
     * 拒绝队友邀请
     */
    @PostMapping("/{orderId}/reject-invite")
    public R<Void> rejectInvite(@PathVariable Long orderId) {
        Long playerId = SecurityUtils.getUserId();
        OrderPlayer op = orderPlayerService.getOne(new LambdaQueryWrapper<OrderPlayer>()
                .eq(OrderPlayer::getOrderId, orderId)
                .eq(OrderPlayer::getPlayerId, playerId)
                .eq(OrderPlayer::getStatus, "INVITED"));
        if (op == null) return R.fail("未找到邀请记录");
        op.setStatus("REJECTED");
        op.setRejectedAt(LocalDateTime.now());
        orderPlayerService.updateById(op);

        // 记录进度
        addProgress(orderId, "TEAMMATE_REJECTED", "PLAYER", playerId, "队友拒绝邀请");
        // 通知主接打手
        Order order = orderService.getById(orderId);
        if (order != null && order.getPlayerId() != null) {
            eventPublisher.publishEvent(new BusinessEvent(this, "TEAMMATE_REJECTED",
                    "PLAYER", order.getPlayerId(), orderId, "队友已拒绝邀请，请邀请其他队友"));
        }
        return R.ok();
    }

    /**
     * 查询可邀请的队友列表（复用派单页面查询模式）
     */
    @GetMapping("/teammate/available")
    public R<java.util.Map<String, Object>> availableTeammates(
            PageQuery query,
            @RequestParam Long orderId,
            @RequestParam(value = "keyword", required = false) String keyword) {
        Long playerId = SecurityUtils.getUserId();
        Order order = orderService.getById(orderId);
        if (order == null) return R.fail("订单不存在");
        if (!playerId.equals(order.getPlayerId())) return R.fail("只有主接打手可查看");

        // 仅排除当前有效队友（TEAMMATE 角色且待处理/已接受），主打手 PRIMARY 记录不参与排除
        List<Long> activeTeammateIds = orderPlayerService.list(new LambdaQueryWrapper<OrderPlayer>()
                        .eq(OrderPlayer::getOrderId, orderId)
                        .eq(OrderPlayer::getRole, "TEAMMATE")
                        .in(OrderPlayer::getStatus, "INVITED", "ACCEPTED")
                        .select(OrderPlayer::getPlayerId))
                .stream().map(OrderPlayer::getPlayerId).toList();

        OrderPlayer pendingInvite = orderPlayerService.getOne(new LambdaQueryWrapper<OrderPlayer>()
                .eq(OrderPlayer::getOrderId, orderId)
                .eq(OrderPlayer::getRole, "TEAMMATE")
                .eq(OrderPlayer::getStatus, "INVITED")
                .last("LIMIT 1"));

        // 查询ACTIVE打手，排除自己和当前队友，支持搜索
        LambdaQueryWrapper<Player> w = new LambdaQueryWrapper<Player>()
                .eq(Player::getStatus, "ACTIVE")
                .ne(Player::getId, playerId);
        if (!activeTeammateIds.isEmpty()) {
            w.notIn(Player::getId, activeTeammateIds);
        }
        if (keyword != null && !keyword.isEmpty()) {
            w.and(qw -> qw.like(Player::getNickname, keyword).or().like(Player::getPhone, keyword));
        }
        w.orderByDesc(Player::getAvgRating);
        Page<Player> page = playerService.page(new Page<>(query.getPageNum(), query.getPageSize()), w);
        // 填充统计数据（和派单页面一样）
        for (Player p : page.getRecords()) {
            p.setCompletedOrders(crossModuleMapper.selectPlayerCompletedOrders(p.getId()));
            p.setActiveOrders(crossModuleMapper.selectPlayerActiveOrders(p.getId()));
        }
        long activePoolCount = playerService.count(new LambdaQueryWrapper<Player>()
                .eq(Player::getStatus, "ACTIVE")
                .ne(Player::getId, playerId));
        int maxConcurrent = Integer.parseInt(sysConfigService.getConfigValue("order.max_active_per_player", "5"));
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("players", page);
        result.put("maxConcurrent", maxConcurrent);
        result.put("activePoolCount", activePoolCount);
        if (pendingInvite != null) {
            Player invited = playerService.getById(pendingInvite.getPlayerId());
            java.util.Map<String, Object> pending = new java.util.HashMap<>();
            pending.put("playerId", pendingInvite.getPlayerId());
            pending.put("nickname", invited != null ? invited.getNickname() : "打手" + pendingInvite.getPlayerId());
            pending.put("inviteDeadline", pendingInvite.getInviteDeadline());
            result.put("pendingInvite", pending);
        }
        if (page.getRecords().isEmpty() && (keyword == null || keyword.isEmpty())) {
            if (pendingInvite != null) {
                Player invited = playerService.getById(pendingInvite.getPlayerId());
                String name = invited != null && invited.getNickname() != null && !invited.getNickname().isEmpty()
                        ? invited.getNickname() : "队友";
                result.put("emptyHint", "已邀请 " + name + "，请等待对方在「邀请列表」中确认");
            } else if (activePoolCount <= 0) {
                result.put("emptyHint", "当前暂无其他可用打手，请联系客服或等待更多打手入驻");
            }
        }
        return R.ok(result);
    }

    /**
     * 打手开始服务（仅主接打手可操作）
     */
    @PostMapping("/{orderId}/start")
    public R<Void> startService(@PathVariable Long orderId) {
        Long playerId = SecurityUtils.getUserId();
        orderService.startOrder(orderId, playerId);
        return R.ok();
    }

    /**
     * 打手标记完成（仅主接打手可操作）
     */
    @PostMapping("/{orderId}/complete")
    public R<Void> completeService(@PathVariable Long orderId,
                                   @RequestBody(required = false) ProgressRequest req) {
        Long playerId = SecurityUtils.getUserId();
        orderService.completeOrder(orderId, playerId, req != null ? req.getImages() : null);
        return R.ok();
    }

    /**
     * 打手更新服务进度
     */
    @PostMapping("/{orderId}/progress")
    public R<Void> updateProgress(@PathVariable Long orderId, @RequestBody ProgressRequest req) {
        Long playerId = SecurityUtils.getUserId();
        // 校验操作者是该订单的参与打手
        long isMember = orderPlayerService.count(new LambdaQueryWrapper<OrderPlayer>()
                .eq(OrderPlayer::getOrderId, orderId)
                .eq(OrderPlayer::getPlayerId, playerId)
                .eq(OrderPlayer::getStatus, "ACCEPTED"));
        if (isMember == 0) return R.fail("您不是该订单的参与打手");
        Order order = orderService.getById(orderId);
        if (order == null || !"IN_PROGRESS".equals(order.getStatus())) {
            return R.fail("订单状态不允许更新进度");
        }
        OrderProgress progress = new OrderProgress();
        progress.setOrderId(orderId);
        progress.setType("PROGRESS_UPDATE");
        progress.setOperatorType("PLAYER");
        progress.setOperatorId(playerId);
        progress.setContent(req.getContent());
        progress.setImages(ImageListUtils.normalize(req.getImages()));
        progress.setCreatedAt(LocalDateTime.now());
        orderProgressService.save(progress);
        // 通知用户
        eventPublisher.publishEvent(new BusinessEvent(this, "PROGRESS_UPDATE",
                "USER", order.getUserId(), orderId, "打手更新了服务进度"));
        return R.ok();
    }

    /**
     * 换人—换自己：主接单员退出，订单回到接单大厅
     */
    @PostMapping("/{orderId}/replace-self")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> replaceSelf(@PathVariable Long orderId) {
        Long playerId = SecurityUtils.getUserId();
        Order order = orderService.getById(orderId);
        if (order == null) return R.fail("订单不存在");
        if (!"IN_PROGRESS".equals(order.getStatus())) return R.fail("当前状态不允许换人");
        if (!playerId.equals(order.getPlayerId())) return R.fail("只有主接单员可以操作");
        // 清除主接单员，订单回到 PAID 等待重新接单（使用显式更新，确保字段被置空）
        orderService.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .set(Order::getPlayerId, null)
                .set(Order::getPlayerId2, null)
                .set(Order::getDesignatedPlayerId, null)
                .set(Order::getAssignTime, null)
                .set(Order::getAcceptTime, null)
                .set(Order::getStartTime, null)
                .set(Order::getTeammateDeadline, null)
                .set(Order::getStatus, "PAID")
                .set(Order::getUpdatedAt, LocalDateTime.now()));
        // 所有参与者标记为 REPLACED（包括主接单员和队友）
        markAllPlayersReplaced(orderId);
        addProgress(orderId, "PLAYER_REPLACED", "PLAYER", playerId, "接单员主动退出，订单回到接单大厅");
        // 通知用户：订单回到接单大厅
        eventPublisher.publishEvent(new BusinessEvent(this, "ORDER_BACK_TO_HALL",
                "USER", order.getUserId(), orderId, "接单员已退出，您的订单已重新回到接单大厅等待新的接单员"));
        return R.ok();
    }

    /**
     * 换人—两个都换：主接单员+队友全部退出
     */
    @PostMapping("/{orderId}/replace-all")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> replaceAll(@PathVariable Long orderId) {
        // 逻辑和 replace-self 一样，都是清除主接单员+队友回大厅
        return replaceSelf(orderId);
    }

    /**
     * 换人—换队友：踢掉队友，主接单员继续，可重新邀请
     */
    @PostMapping("/{orderId}/replace-teammate")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> replaceTeammate(@PathVariable Long orderId) {
        Long playerId = SecurityUtils.getUserId();
        Order order = orderService.getById(orderId);
        if (order == null) return R.fail("订单不存在");
        if (!"IN_PROGRESS".equals(order.getStatus())) return R.fail("当前状态不允许换人");
        if (!playerId.equals(order.getPlayerId())) return R.fail("只有主接单员可以操作");
        // 只标记队友为 REPLACED，主接单员保留
        List<OrderPlayer> replaced = markTeammatesReplaced(orderId);
        // 若辅助打手字段指向被踢队友，一并清空
        if (order.getPlayerId2() != null) {
            boolean cleared = replaced.stream()
                    .anyMatch(op -> order.getPlayerId2().equals(op.getPlayerId()));
            if (cleared) {
                orderService.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Order>()
                        .eq(Order::getId, orderId)
                        .set(Order::getPlayerId2, null)
                        .set(Order::getUpdatedAt, LocalDateTime.now()));
            }
        }
        addProgress(orderId, "TEAMMATE_REPLACED", "PLAYER", playerId, "主接单员更换队友，可重新邀请");
        // 通知被踢的队友
        for (OrderPlayer op : replaced) {
            eventPublisher.publishEvent(new BusinessEvent(this, "TEAMMATE_REMOVED",
                    "PLAYER", op.getPlayerId(), orderId, "您已被主接单员从订单中移除"));
        }
        return R.ok();
    }

    /** 标记所有参与者为 REPLACED（replaceSelf/replaceAll 用） */
    private void markAllPlayersReplaced(Long orderId) {
        List<OrderPlayer> all = orderPlayerService.list(new LambdaQueryWrapper<OrderPlayer>()
                .eq(OrderPlayer::getOrderId, orderId)
                .in(OrderPlayer::getStatus, "INVITED", "ACCEPTED"));
        for (OrderPlayer op : all) {
            op.setStatus("REPLACED");
            orderPlayerService.updateById(op);
        }
    }

    /** 只标记队友为 REPLACED，返回被标记的列表（replaceTeammate 用） */
    private List<OrderPlayer> markTeammatesReplaced(Long orderId) {
        List<OrderPlayer> teammates = orderPlayerService.list(new LambdaQueryWrapper<OrderPlayer>()
                .eq(OrderPlayer::getOrderId, orderId)
                .eq(OrderPlayer::getRole, "TEAMMATE")
                .in(OrderPlayer::getStatus, "INVITED", "ACCEPTED"));
        for (OrderPlayer op : teammates) {
            op.setStatus("REPLACED");
            orderPlayerService.updateById(op);
        }
        return teammates;
    }

    private void addProgress(Long orderId, String type, String operatorType, Long operatorId, String content) {
        OrderProgress progress = new OrderProgress();
        progress.setOrderId(orderId);
        progress.setType(type);
        progress.setOperatorType(operatorType);
        progress.setOperatorId(operatorId);
        progress.setContent(content);
        progress.setCreatedAt(LocalDateTime.now());
        orderProgressService.save(progress);
    }

    /** 解析逗号分隔的分类 id 为 List，忽略无效值 */
    private static List<Long> parseCategoryIds(String categoryIds) {
        if (categoryIds == null || categoryIds.trim().isEmpty()) return Collections.emptyList();
        List<Long> list = new ArrayList<>();
        for (String s : categoryIds.split(",")) {
            s = s.trim();
            if (s.isEmpty()) continue;
            try {
                list.add(Long.valueOf(s));
            } catch (NumberFormatException ignored) { }
        }
        return list;
    }

    @Data
    public static class InviteRequest {
        private Long inviteePlayerId;
        /** 分成方式: FIFTY_FIFTY / FORTY_SIXTY / THIRTY_SEVENTY / CUSTOM */
        private String splitType;
        /** 自定义金额（仅splitType=CUSTOM时有效） */
        private java.math.BigDecimal customAmount;
    }

    @Data
    public static class ProgressRequest {
        private String content;
        private String images;
    }

}