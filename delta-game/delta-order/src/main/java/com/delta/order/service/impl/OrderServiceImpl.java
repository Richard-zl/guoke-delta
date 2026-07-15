package com.delta.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.delta.common.enums.OrderStatusEnum;
import com.delta.common.event.BusinessEvent;
import com.delta.common.event.OrderConfirmedEvent;
import com.delta.common.exception.BusinessException;
import com.delta.common.utils.IdGenerator;
import com.delta.common.utils.ImageListUtils;
import com.delta.order.dto.CreateOrderRequest;
import com.delta.order.entity.Order;
import com.delta.order.entity.OrderPlayer;
import com.delta.order.entity.OrderProgress;
import com.delta.order.mapper.OrderMapper;
import com.delta.order.service.OrderPlayerService;
import com.delta.order.service.OrderProgressService;
import com.delta.order.service.OrderPriceResolver;
import com.delta.order.service.OrderService;
import com.delta.common.chat.service.ChatSessionService;
import com.delta.common.chat.util.ChatParticipantId;
import com.delta.product.entity.Product;
import com.delta.product.enums.ProductLimitTypeEnum;
import com.delta.product.service.ProductService;
import com.delta.product.service.ProductVariantService;
import com.delta.product.service.TrialOrderService;
import com.delta.system.service.SysConfigService;
import com.delta.common.mapper.UserCouponMapper;
import com.delta.common.mapper.CouponMapper;
import com.delta.common.entity.UserCoupon;
import com.delta.common.entity.Coupon;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {
    private final OrderProgressService orderProgressService;
    private final OrderPlayerService orderPlayerService;
    private final ProductService productService;
    private final ChatSessionService chatSessionService;
    private final org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final com.delta.common.mapper.CrossModuleMapper crossModuleMapper;
    private final SysConfigService sysConfigService;
    private final UserCouponMapper userCouponMapper;
    private final CouponMapper couponMapper;
    private final TrialOrderService trialOrderService;
    private final ProductVariantService productVariantService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Order createOrder(Long userId, CreateOrderRequest req) {
        // 1. 查询商品并校验状态
        Product product = productService.getById(req.getProductId());
        if (product == null) throw new BusinessException("商品不存在");
        if (product.getStatus() != 1) throw new BusinessException("商品已下架");
        LocalDateTime now = LocalDateTime.now();

        // 1.1 限购校验：按商品设置的周期限制重复购买
        ProductLimitTypeEnum limitType = ProductLimitTypeEnum.resolve(
                product.getPerUserLimitType(),
                product.getPerUserLimitEnabled(),
                product.getPerUserLimitCount());
        if (limitType.isLimited()) {
            LocalDateTime windowStart = limitType.resolveWindowStart(now);
            int boughtTimes = windowStart == null
                    ? crossModuleMapper.countUserProductOrders(userId, product.getId())
                    : crossModuleMapper.countUserProductOrdersSince(userId, product.getId(), windowStart);
            if (boughtTimes > 0) {
                throw new BusinessException(limitType.getExceededMessage());
            }
        }

        // 1.2 体验单限购：分类级周期内仅可购买一单（待支付不计入）
        trialOrderService.validateTrialOrderForCreate(userId, product);

        // 2. 服务端计价：规格单价 × 数量 + 优惠券 → 应付金额
        var variants = productVariantService.listActiveByProductId(product.getId());
        OrderPriceResolver.PriceBreakdown pricing = OrderPriceResolver.resolve(
                product, variants, req.getVariantId(), req.getQuantity());
        BigDecimal subtotal = pricing.getSubtotal();

        // 体验单不可使用优惠券
        trialOrderService.validateNoCoupon(product, req.getCouponId());

        BigDecimal finalAmount = subtotal;
        Long userCouponId = null;
        if (req.getCouponId() != null) {
            UserCoupon userCoupon = userCouponMapper.selectById(req.getCouponId());
            if (userCoupon == null || !userId.equals(userCoupon.getUserId())) {
                throw new BusinessException("优惠券不可用");
            }
            if (!"UNUSED".equals(userCoupon.getStatus())) {
                throw new BusinessException("优惠券不可用");
            }
            if (userCoupon.getExpireTime().isBefore(now)) {
                throw new BusinessException("优惠券已过期");
            }
            Coupon coupon = couponMapper.selectById(userCoupon.getCouponId());
            if (coupon == null || coupon.getStatus() != 1) {
                throw new BusinessException("优惠券不存在");
            }
            if (coupon.getMinAmount().compareTo(subtotal) > 0) {
                throw new BusinessException("订单金额未达到优惠券使用门槛");
            }
            finalAmount = applyCouponDiscount(subtotal, coupon);
            userCouponId = req.getCouponId();
        }

        // 校验客户端提交的应付金额与服务端计算一致（无券时等于原价，有券时为折后价）
        if (req.getAmount() == null || normalizeMoney(req.getAmount()).compareTo(finalAmount) != 0) {
            throw new BusinessException("价格异常，请刷新后重试");
        }

        // 3. 构建订单
        Order order = new Order();
        order.setOrderNo(IdGenerator.nextOrderNo());
        order.setUserId(userId);
        order.setProductId(req.getProductId());
        // 商品快照
        order.setProductName(product.getName());
        String specInfo = req.getSpecInfo();
        order.setSpecInfo(specInfo != null && !specInfo.isEmpty() ? specInfo : null);
        order.setUnitPrice(pricing.getUnitPrice());
        order.setQuantity(pricing.getQuantity());
        order.setVariantId(pricing.getVariantId());
        order.setVariantName(pricing.getVariantName());
        order.setAmount(finalAmount);
        // 保存使用的优惠券ID
        order.setUserCouponId(userCouponId);
        // 抽佣比例：商品级优先，否则用系统默认
        if (product.getCommissionRate() != null) {
            order.setCommissionRate(product.getCommissionRate());
        } else {
            String commissionRateStr = sysConfigService.getConfigValue("settlement.commission_rate", "0.1");
            try {
                order.setCommissionRate(new BigDecimal(commissionRateStr));
            } catch (Exception e) {
                order.setCommissionRate(new BigDecimal("0.1"));
            }
        }
        order.setGameAccount(req.getGameAccount() != null ? req.getGameAccount() : "");
        order.setContact(req.getContact() != null ? req.getContact() : "");
        order.setRemark(req.getRemark() != null ? req.getRemark() : "");
        // 动态表单字段
        if (req.getExtraFields() != null && !req.getExtraFields().isEmpty()) {
            order.setExtraFields(com.alibaba.fastjson2.JSON.toJSONString(req.getExtraFields()));
        }
        order.setDesignatedPlayerId(req.getDesignatedPlayerId());
        order.setStatus(OrderStatusEnum.PENDING_PAYMENT.name());
        int payDeadlineMinutes = Integer.parseInt(sysConfigService.getConfigValue("order.pay_deadline_minutes", "30"));
        order.setPayDeadline(now.plusMinutes(payDeadlineMinutes));
        order.setSettled(0);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        save(order);
        addProgress(order.getId(), null, OrderStatusEnum.PENDING_PAYMENT.name(), "USER", userId, "创建订单");

        // 写入Redis超时key，到期自动取消订单
        redisTemplate.opsForValue().set(
                "order:pay_timeout:" + order.getId(), String.valueOf(order.getId()),
                java.time.Duration.ofMinutes(payDeadlineMinutes));

        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long orderId, Long userId) {
        Order order = getById(orderId);
        if (order == null) throw new BusinessException("订单不存在");
        if (!order.getUserId().equals(userId)) throw new BusinessException("无权操作");
        String status = order.getStatus();
        if (OrderStatusEnum.PENDING_PAYMENT.name().equals(status)) {
            transition(order, OrderStatusEnum.CANCELLED, "USER", userId, "用户取消订单");
            redisTemplate.delete("order:pay_timeout:" + orderId);
            eventPublisher.publishEvent(new BusinessEvent(this, "ORDER_CANCELLED", "USER", userId, orderId, "订单已取消"));
        } else if (OrderStatusEnum.PAID.name().equals(status) && order.getPlayerId() == null) {
            transition(order, OrderStatusEnum.REFUNDING, "USER", userId, "用户申请退款");
            eventPublisher.publishEvent(new BusinessEvent(this, "ORDER_CANCEL_REFUND",
                    "USER", userId, orderId, "订单退款处理中"));
        } else if (OrderStatusEnum.ASSIGNED.name().equals(status)) {
            order.setPlayerId(null);
            order.setPlayerName(null);
            transition(order, OrderStatusEnum.REFUNDING, "USER", userId, "用户申请退款（已指派）");
            eventPublisher.publishEvent(new BusinessEvent(this, "ORDER_CANCEL_REFUND",
                    "USER", userId, orderId, "订单退款处理中"));
        } else {
            throw new BusinessException("当前状态不允许取消");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void csCancelOrder(Long orderId, Long operatorId) {
        Order order = getById(orderId);
        if (order == null) throw new BusinessException("订单不存在");
        String status = order.getStatus();
        if (!OrderStatusEnum.ASSIGNED.name().equals(status)
                && !OrderStatusEnum.IN_PROGRESS.name().equals(status)) {
            throw new BusinessException("当前状态不允许退款");
        }
        order.setPlayerId(null);
        order.setPlayerName(null);
        transition(order, OrderStatusEnum.REFUNDING, "CS", operatorId, "客服操作退款");
        eventPublisher.publishEvent(new BusinessEvent(this, "ORDER_CANCEL_REFUND",
                "CS", operatorId, orderId, "客服操作退款处理中"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignOrder(Long orderId, Long playerId) {
        assignOrder(orderId, playerId, null, "SYSTEM", null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignOrder(Long orderId, Long playerId, Long playerId2, String operatorType, Long operatorId) {
        Order order = getById(orderId);
        if (order == null) throw new BusinessException("订单不存在");
        String currentStatus = order.getStatus();
        if (!OrderStatusEnum.PAID.name().equals(currentStatus)
                && !OrderStatusEnum.ASSIGNED.name().equals(currentStatus)) {
            throw new BusinessException("当前状态不允许指派, 实际:" + currentStatus);
        }
        Long previousPlayerId = order.getPlayerId();
        Long previousPlayerId2 = order.getPlayerId2();
        boolean reassign = OrderStatusEnum.ASSIGNED.name().equals(currentStatus);
        if (reassign && previousPlayerId != null && previousPlayerId.equals(playerId)
                && Objects.equals(playerId2, previousPlayerId2)) {
            throw new BusinessException("订单已指派给相同打手，无需重复指派");
        }

        String normalizedOperatorType = normalizeAssignOperatorType(operatorType);
        boolean staffAssign = "ADMIN".equals(normalizedOperatorType) || "CS".equals(normalizedOperatorType);

        String playerStatus = crossModuleMapper.selectPlayerStatus(playerId);
        if (!"ACTIVE".equals(playerStatus)) {
            throw new BusinessException("目标打手状态不可用，无法指派");
        }

        int maxConcurrent = Integer.parseInt(sysConfigService.getConfigValue("order.max_active_per_player", "1"));
        int activeOrders = crossModuleMapper.selectPlayerActiveOrders(playerId);
        if (activeOrders >= maxConcurrent) {
            throw new BusinessException("该打手当前已有" + activeOrders + "个进行中订单，已达最大接单数" + maxConcurrent + "，无法指派");
        }

        order.setPlayerId(playerId);
        order.setAssignTime(LocalDateTime.now());

        if (playerId2 != null) {
            String playerStatus2 = crossModuleMapper.selectPlayerStatus(playerId2);
            if (!"ACTIVE".equals(playerStatus2)) {
                throw new BusinessException("第二个打手状态不可用，无法指派");
            }
            if (playerId.equals(playerId2)) {
                throw new BusinessException("不能指派同一个打手");
            }
            int activeOrders2 = crossModuleMapper.selectPlayerActiveOrders(playerId2);
            if (activeOrders2 >= maxConcurrent) {
                throw new BusinessException("第二个打手当前已有" + activeOrders2 + "个进行中订单，已达最大接单数" + maxConcurrent + "，无法指派");
            }
            order.setPlayerId2(playerId2);
            String playerName2 = crossModuleMapper.selectPlayerNickname(playerId2);
            order.setPlayerName2(playerName2);
            if (staffAssign) {
                upsertStaffAssignedTeammate(orderId, playerId2);
            }
        } else if (staffAssign) {
            retireActiveTeammates(orderId, null);
            order.setPlayerId2(null);
            order.setPlayerName2(null);
        }

        // 客服/管理员指派时同步 designatedPlayerId，避免改派后新打手接单被旧指定打手校验拦截
        if (staffAssign) {
            order.setDesignatedPlayerId(playerId);
        }
        String progressContent = buildAssignProgressContent(normalizedOperatorType, reassign);
        if (reassign) {
            order.setUpdatedAt(LocalDateTime.now());
            updateById(order);
            addProgress(order.getId(), currentStatus, OrderStatusEnum.ASSIGNED.name(),
                    normalizedOperatorType, operatorId, progressContent);
        } else {
            transition(order, OrderStatusEnum.ASSIGNED, normalizedOperatorType, operatorId, progressContent);
        }

        if (reassign && previousPlayerId != null) {
            eventPublisher.publishEvent(new BusinessEvent(this, "订单改派通知",
                    "PLAYER", previousPlayerId, orderId, "订单已被重新指派给其他打手"));
        }
        eventPublisher.publishEvent(new BusinessEvent(this, "ORDER_ASSIGNED",
                "PLAYER", playerId, orderId, reassign ? "您有一笔重新指派的订单，请确认" : "您有一笔新的指派订单，请确认"));
        if (playerId2 != null) {
            eventPublisher.publishEvent(new BusinessEvent(this, "ORDER_ASSIGNED",
                    "PLAYER", playerId2, orderId, "您被指派为协助打手"));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void acceptOrder(Long orderId, Long playerId) {
        String lockKey = "order:accept:" + orderId;
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1",
                java.time.Duration.ofSeconds(10));
        if (Boolean.FALSE.equals(locked)) throw new BusinessException("操作太频繁，请稍后重试");
        try {
            Order order = getById(orderId);
            if (order == null) throw new BusinessException("订单不存在");
            if (!OrderStatusEnum.PAID.name().equals(order.getStatus()) &&
                    !OrderStatusEnum.ASSIGNED.name().equals(order.getStatus()))
                throw new BusinessException("当前状态不允许接单");
            // 已指派订单：以 playerId 为准（客服改派后 designatedPlayerId 可能仍是用户原指定打手）
            if (OrderStatusEnum.ASSIGNED.name().equals(order.getStatus())) {
                if (order.getPlayerId() != null && !order.getPlayerId().equals(playerId)) {
                    throw new BusinessException("该订单已指派给其他打手");
                }
            } else if (order.getDesignatedPlayerId() != null
                    && !order.getDesignatedPlayerId().equals(playerId)) {
                throw new BusinessException("该订单已指定其他打手");
            }
            order.setPlayerId(playerId);
            order.setAcceptTime(LocalDateTime.now());

            OrderPlayer op = new OrderPlayer();
            op.setOrderId(orderId);
            op.setPlayerId(playerId);
            op.setRole("PRIMARY");
            op.setStatus("ACCEPTED");
            op.setCreatedAt(LocalDateTime.now());
            op.setAcceptedAt(LocalDateTime.now());
            orderPlayerService.save(op);

            if (order.getRequiredPlayerCount() != null && order.getRequiredPlayerCount() > 1) {
                order.setTeammateDeadline(LocalDateTime.now().plusHours(2));
                transition(order, OrderStatusEnum.WAITING_TEAMMATE, "PLAYER", playerId, "打手接单，等待组队");
            } else {
                transition(order, OrderStatusEnum.ACCEPTED, "PLAYER", playerId, "打手接单");
            }

            chatSessionService.findOrCreate(ChatParticipantId.encodeUser(order.getUserId()), ChatParticipantId.encodePlayer(playerId));
            eventPublisher.publishEvent(new BusinessEvent(this, "ORDER_ACCEPTED",
                    "USER", order.getUserId(), orderId, "您的订单已被打手接单"));
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectAssign(Long orderId, Long playerId, String reason) {
        Order order = getById(orderId);
        validateStatus(order, OrderStatusEnum.ASSIGNED);
        if (!playerId.equals(order.getPlayerId())) throw new BusinessException("无权操作");
        retireActiveTeammates(orderId, null);
        update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Order>()
                .eq(Order::getId, orderId)
                .set(Order::getPlayerId, null)
                .set(Order::getPlayerId2, null)
                .set(Order::getPlayerName2, null)
                .set(Order::getAssignTime, null)
                .set(Order::getStatus, OrderStatusEnum.PAID.name())
                .set(Order::getUpdatedAt, LocalDateTime.now()));
        addProgress(orderId, order.getStatus(), OrderStatusEnum.PAID.name(),
                "PLAYER", playerId, "打手拒绝指派: " + reason);
        eventPublisher.publishEvent(new BusinessEvent(this, "ASSIGN_REJECTED",
                "CS", null, orderId, "打手拒绝指派，请重新分配"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void startOrder(Long orderId, Long playerId) {
        Order order = getById(orderId);
        if (order == null) throw new BusinessException("订单不存在");
        String status = order.getStatus();
        if (!OrderStatusEnum.ACCEPTED.name().equals(status) && !OrderStatusEnum.WAITING_TEAMMATE.name().equals(status)) {
            throw new BusinessException("当前状态不允许开始服务");
        }
        if (!playerId.equals(order.getPlayerId())) throw new BusinessException("只有主接打手可以开始服务");
        long pendingInvites = orderPlayerService.count(new LambdaQueryWrapper<OrderPlayer>()
                .eq(OrderPlayer::getOrderId, orderId)
                .eq(OrderPlayer::getStatus, "INVITED"));
        if (pendingInvites > 0) {
            throw new BusinessException("有队友邀请待确认，请等待对方接受或取消邀请后再开始服务");
        }
        order.setStartTime(LocalDateTime.now());
        transition(order, OrderStatusEnum.IN_PROGRESS, "PLAYER", playerId, "开始服务");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeOrder(Long orderId, Long playerId, String endServiceImages) {
        Order order = getById(orderId);
        validateStatus(order, OrderStatusEnum.IN_PROGRESS);
        if (!playerId.equals(order.getPlayerId())) throw new BusinessException("只有主接打手可以标记完成");
        String normalizedImages = normalizeImages(endServiceImages);
        if (normalizedImages == null) {
            throw new BusinessException("结束服务进度必须上传至少一张图片");
        }
        OrderProgress progress = new OrderProgress();
        progress.setOrderId(orderId);
        progress.setType("PROGRESS_UPDATE");
        progress.setOperatorType("PLAYER");
        progress.setOperatorId(playerId);
        progress.setContent("结束服务");
        progress.setImages(normalizedImages);
        progress.setCreatedAt(LocalDateTime.now());
        orderProgressService.save(progress);
        order.setCompleteTime(LocalDateTime.now());
        int autoConfirmHours = Integer.parseInt(sysConfigService.getConfigValue("order.auto_confirm_hours", "48"));
        if (autoConfirmHours <= 0 || autoConfirmHours > 720) autoConfirmHours = 48;
        order.setAutoConfirmDeadline(LocalDateTime.now().plusHours(autoConfirmHours));
        transition(order, OrderStatusEnum.COMPLETED, "PLAYER", playerId, "服务完成");
        eventPublisher.publishEvent(new BusinessEvent(this, "ORDER_COMPLETED",
                "USER", order.getUserId(), orderId, "您的订单服务已完成，请在" + autoConfirmHours + "小时内确认"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmOrder(Long orderId, Long userId) {
        Order order = getById(orderId);
        validateStatus(order, OrderStatusEnum.COMPLETED);
        if (!order.getUserId().equals(userId)) throw new BusinessException("无权操作");
        order.setConfirmTime(LocalDateTime.now());
        transition(order, OrderStatusEnum.CONFIRMED, "USER", userId, "用户确认完成");
        eventPublisher.publishEvent(new OrderConfirmedEvent(this, orderId, "USER"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void manualConfirmOrder(Long orderId, Long operatorId, String operatorType) {
        Order order = getById(orderId);
        validateStatus(order, OrderStatusEnum.COMPLETED);
        String normalizedOperatorType = "ADMIN".equals(operatorType) ? "ADMIN" : "CS";
        order.setConfirmTime(LocalDateTime.now());
        transition(order, OrderStatusEnum.CONFIRMED, normalizedOperatorType, operatorId,
                buildManualConfirmContent(normalizedOperatorType));
        eventPublisher.publishEvent(new OrderConfirmedEvent(this, orderId, normalizedOperatorType));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disputeOrder(Long orderId) {
        Order order = getById(orderId);
        if (order == null) throw new BusinessException("订单不存在");
        String status = order.getStatus();
        if (!OrderStatusEnum.IN_PROGRESS.name().equals(status) &&
                !OrderStatusEnum.COMPLETED.name().equals(status) &&
                !OrderStatusEnum.CONFIRMED.name().equals(status)) {
            throw new BusinessException("当前状态不允许投诉");
        }
        transition(order, OrderStatusEnum.DISPUTED, "USER", order.getUserId(), "用户发起投诉");
        if (order.getPlayerId() != null) {
            eventPublisher.publishEvent(new BusinessEvent(this, "ORDER_DISPUTED",
                    "PLAYER", order.getPlayerId(), orderId, "您的订单被用户投诉，请关注处理结果"));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void arbitrateOrder(Long orderId, String result, Long operatorId) {
        Order order = getById(orderId);
        validateStatus(order, OrderStatusEnum.DISPUTED);
        transition(order, OrderStatusEnum.ARBITRATED, "CS", operatorId, "仲裁结果: " + result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markReviewed(Long orderId) {
        Order order = getById(orderId);
        validateStatus(order, OrderStatusEnum.CONFIRMED);
        transition(order, OrderStatusEnum.REVIEWED, "USER", order.getUserId(), "用户已评价");
    }

    private void validateStatus(Order order, OrderStatusEnum expected) {
        if (order == null) throw new BusinessException("订单不存在");
        if (!expected.name().equals(order.getStatus()))
            throw new BusinessException("当前状态不允许此操作, 期望:" + expected.name() + " 实际:" + order.getStatus());
    }

    private String normalizeAssignOperatorType(String operatorType) {
        if ("ADMIN".equals(operatorType) || "CS".equals(operatorType) || "USER".equals(operatorType)) {
            return operatorType;
        }
        return "SYSTEM";
    }

    private String buildAssignProgressContent(String operatorType, boolean reassign) {
        return switch (operatorType) {
            case "ADMIN" -> reassign ? "管理员重新指派打手" : "管理员指派打手";
            case "CS" -> reassign ? "客服重新指派打手" : "客服指派打手";
            case "USER" -> reassign ? "用户重新指定打手" : "用户指定打手";
            default -> reassign ? "系统重新指派打手" : "系统指派打手";
        };
    }

    private String buildManualConfirmContent(String operatorType) {
        return "ADMIN".equals(operatorType) ? "管理员手动结单" : "客服手动结单";
    }

    private String normalizeImages(String images) {
        return ImageListUtils.normalize(images);
    }

    private void transition(Order order, OrderStatusEnum to, String operatorType, Long operatorId, String content) {
        String from = order.getStatus();
        order.setStatus(to.name());
        order.setUpdatedAt(LocalDateTime.now());
        updateById(order);
        addProgress(order.getId(), from, to.name(), operatorType, operatorId, content);
    }

    private void addProgress(Long orderId, String from, String to, String operatorType, Long operatorId, String content) {
        OrderProgress progress = new OrderProgress();
        progress.setOrderId(orderId);
        progress.setType("STATUS_CHANGE");
        progress.setFromStatus(from);
        progress.setToStatus(to);
        progress.setOperatorType(operatorType);
        progress.setOperatorId(operatorId);
        progress.setContent(content);
        progress.setCreatedAt(LocalDateTime.now());
        orderProgressService.save(progress);
    }

    /** 金额统一保留两位小数 */
    private BigDecimal normalizeMoney(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 客服/管理员指派辅助打手：同步 order_player（TEAMMATE+ACCEPTED，默认五五开），无需辅助单独确认。
     */
    private void upsertStaffAssignedTeammate(Long orderId, Long assistPlayerId) {
        retireActiveTeammates(orderId, assistPlayerId);
        LocalDateTime now = LocalDateTime.now();
        OrderPlayer existing = orderPlayerService.getOne(new LambdaQueryWrapper<OrderPlayer>()
                .eq(OrderPlayer::getOrderId, orderId)
                .eq(OrderPlayer::getPlayerId, assistPlayerId)
                .eq(OrderPlayer::getRole, "TEAMMATE")
                .last("LIMIT 1"));
        if (existing != null) {
            existing.setStatus("ACCEPTED");
            existing.setSplitType("FIFTY_FIFTY");
            existing.setSplitAmount(null);
            existing.setAcceptedAt(now);
            existing.setRejectedAt(null);
            existing.setInvitedAt(null);
            existing.setInviteDeadline(null);
            existing.setInvitedBy(null);
            orderPlayerService.updateById(existing);
            return;
        }
        OrderPlayer op = new OrderPlayer();
        op.setOrderId(orderId);
        op.setPlayerId(assistPlayerId);
        op.setRole("TEAMMATE");
        op.setStatus("ACCEPTED");
        op.setSplitType("FIFTY_FIFTY");
        op.setAcceptedAt(now);
        op.setCreatedAt(now);
        orderPlayerService.save(op);
    }

    /** 将有效队友（INVITED/ACCEPTED）标记为 REPLACED，可保留 exceptPlayerId */
    private void retireActiveTeammates(Long orderId, Long exceptPlayerId) {
        LambdaQueryWrapper<OrderPlayer> wrapper = new LambdaQueryWrapper<OrderPlayer>()
                .eq(OrderPlayer::getOrderId, orderId)
                .eq(OrderPlayer::getRole, "TEAMMATE")
                .in(OrderPlayer::getStatus, "INVITED", "ACCEPTED");
        if (exceptPlayerId != null) {
            wrapper.ne(OrderPlayer::getPlayerId, exceptPlayerId);
        }
        for (OrderPlayer teammate : orderPlayerService.list(wrapper)) {
            teammate.setStatus("REPLACED");
            orderPlayerService.updateById(teammate);
        }
    }

    /** 根据优惠券类型计算折后金额 */
    private BigDecimal applyCouponDiscount(BigDecimal serverPrice, Coupon coupon) {
        BigDecimal finalAmount = serverPrice;
        if ("DISCOUNT_9".equals(coupon.getType())) {
            finalAmount = serverPrice.multiply(new BigDecimal("0.9"));
        } else if ("DISCOUNT_8".equals(coupon.getType())) {
            finalAmount = serverPrice.multiply(new BigDecimal("0.8"));
        } else if ("DISCOUNT_75".equals(coupon.getType())) {
            finalAmount = serverPrice.multiply(new BigDecimal("0.75"));
        } else if ("CASH_5".equals(coupon.getType())) {
            finalAmount = serverPrice.subtract(new BigDecimal("5"));
        }
        if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
            finalAmount = BigDecimal.ZERO;
        }
        return normalizeMoney(finalAmount);
    }
}