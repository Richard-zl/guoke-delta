package com.delta.cs.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.annotation.OpLog;
import com.delta.common.domain.PageQuery;
import com.delta.common.domain.R;
import com.delta.common.enums.OrderStatusEnum;
import com.delta.common.event.BusinessEvent;
import com.delta.common.mapper.CrossModuleMapper;
import com.delta.common.security.utils.SecurityUtils;
import com.delta.order.entity.Order;
import com.delta.order.entity.OrderPlayer;
import com.delta.order.entity.OrderRelayRequest;
import com.delta.order.service.OrderPlayerService;
import com.delta.order.service.OrderRelayRequestService;
import com.delta.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/cs/relay")
@RequiredArgsConstructor
public class CsRelayController {
    private final OrderRelayRequestService relayRequestService;
    private final OrderService orderService;
    private final OrderPlayerService orderPlayerService;
    private final CrossModuleMapper crossModuleMapper;
    private final ApplicationEventPublisher eventPublisher;

    @GetMapping("/list")
    public R<Page<OrderRelayRequest>> list(PageQuery query,
                                            @RequestParam(value = "status", required = false) String status) {
        LambdaQueryWrapper<OrderRelayRequest> w = new LambdaQueryWrapper<OrderRelayRequest>()
                .eq(status != null && !status.isEmpty(), OrderRelayRequest::getStatus, status)
                .orderByDesc(OrderRelayRequest::getCreatedAt);
        Page<OrderRelayRequest> page = relayRequestService.page(new Page<>(query.getPageNum(), query.getPageSize()), w);
        for (OrderRelayRequest r : page.getRecords()) {
            Order order = orderService.getById(r.getOrderId());
            if (order != null) {
                r.setOrderNo(order.getOrderNo());
                r.setProductName(order.getProductName());
                r.setOrderAmount(order.getAmount());
            }
            r.setOriginalPlayerName(crossModuleMapper.selectPlayerNickname(r.getOriginalPlayerId()));
            if (r.getNewPlayerId() != null) {
                r.setNewPlayerName(crossModuleMapper.selectPlayerNickname(r.getNewPlayerId()));
            }
        }
        return R.ok(page);
    }

    @OpLog(module = "relay", operation = "审核通过接力申请")
    @PostMapping("/{id}/approve")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> approve(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        OrderRelayRequest relay = relayRequestService.getById(id);
        if (relay == null) return R.fail("申请不存在");
        if (!"PENDING".equals(relay.getStatus())) return R.fail("申请已处理");
        Long newPlayerId = body.get("newPlayerId");
        if (newPlayerId == null) return R.fail("请选择接力打手");

        relay.setStatus("APPROVED");
        relay.setNewPlayerId(newPlayerId);
        relay.setReviewedBy(SecurityUtils.getUserId());
        relay.setReviewedAt(LocalDateTime.now());
        relay.setUpdatedAt(LocalDateTime.now());
        relayRequestService.updateById(relay);

        Order order = orderService.getById(relay.getOrderId());
        if (order != null) {
            // Update original player's OrderPlayer role
            OrderPlayer originalOp = orderPlayerService.getOne(new LambdaQueryWrapper<OrderPlayer>()
                    .eq(OrderPlayer::getOrderId, order.getId())
                    .eq(OrderPlayer::getPlayerId, relay.getOriginalPlayerId())
                    .eq(OrderPlayer::getRole, "PRIMARY")
                    .last("LIMIT 1"));
            if (originalOp != null) {
                originalOp.setRole("RELAY_OUT");
                originalOp.setSplitType(relay.getSplitType());
                originalOp.setSplitAmount(relay.getSplitAmount());
                orderPlayerService.updateById(originalOp);
            }

            // Create new OrderPlayer for relay player
            OrderPlayer newOp = new OrderPlayer();
            newOp.setOrderId(order.getId());
            newOp.setPlayerId(newPlayerId);
            newOp.setRole("RELAY_IN");
            newOp.setStatus("INVITED");
            newOp.setCreatedAt(LocalDateTime.now());
            orderPlayerService.save(newOp);

            // Set order to ASSIGNED for new player to accept
            order.setPlayerId(newPlayerId);
            order.setStatus(OrderStatusEnum.ASSIGNED.name());
            order.setAssignTime(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());
            orderService.updateById(order);

            eventPublisher.publishEvent(new BusinessEvent(this, "RELAY_APPROVED",
                    "PLAYER", newPlayerId, order.getId(), "您收到一个接力订单，请尽快接单"));
            eventPublisher.publishEvent(new BusinessEvent(this, "RELAY_APPROVED",
                    "PLAYER", relay.getOriginalPlayerId(), order.getId(), "接力申请已通过，新打手已指派"));
        }
        return R.ok();
    }

    @OpLog(module = "relay", operation = "拒绝接力申请")
    @PostMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        OrderRelayRequest relay = relayRequestService.getById(id);
        if (relay == null) return R.fail("申请不存在");
        if (!"PENDING".equals(relay.getStatus())) return R.fail("申请已处理");
        relay.setStatus("REJECTED");
        relay.setRejectReason(body != null ? body.getOrDefault("reason", "") : "");
        relay.setReviewedBy(SecurityUtils.getUserId());
        relay.setReviewedAt(LocalDateTime.now());
        relay.setUpdatedAt(LocalDateTime.now());
        relayRequestService.updateById(relay);
        eventPublisher.publishEvent(new BusinessEvent(this, "RELAY_REJECTED",
                "PLAYER", relay.getOriginalPlayerId(), relay.getOrderId(), "接力申请被拒绝"));
        return R.ok();
    }
}
