package com.delta.cs.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.annotation.OpLog;
import com.delta.common.domain.PageQuery;
import com.delta.common.domain.R;
import com.delta.common.event.BusinessEvent;
import com.delta.common.mapper.CrossModuleMapper;
import com.delta.common.security.utils.SecurityUtils;
import com.delta.order.entity.Order;
import com.delta.order.entity.OrderPlayer;
import com.delta.order.entity.OrderProgress;
import com.delta.order.entity.PlayerReplaceRequest;
import com.delta.order.service.OrderPlayerService;
import com.delta.order.service.OrderProgressService;
import com.delta.order.service.OrderService;
import com.delta.order.service.PlayerReplaceRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/cs/replace")
@RequiredArgsConstructor
public class CsReplaceController {

    private final PlayerReplaceRequestService replaceRequestService;
    private final OrderService orderService;
    private final OrderPlayerService orderPlayerService;
    private final OrderProgressService orderProgressService;
    private final ApplicationEventPublisher eventPublisher;
    private final CrossModuleMapper crossModuleMapper;

    @GetMapping("/list")
    public R<Page<PlayerReplaceRequest>> list(PageQuery query,
                                               @RequestParam(value = "status", required = false) String status) {
        LambdaQueryWrapper<PlayerReplaceRequest> w = new LambdaQueryWrapper<PlayerReplaceRequest>()
                .eq(status != null && !status.isEmpty(), PlayerReplaceRequest::getStatus, status)
                .orderByDesc(PlayerReplaceRequest::getCreatedAt);
        Page<PlayerReplaceRequest> page = replaceRequestService.page(
                new Page<>(query.getPageNum(), query.getPageSize()), w);
        // 填充展示字段
        for (PlayerReplaceRequest r : page.getRecords()) {
            Order order = orderService.getById(r.getOrderId());
            if (order != null) {
                r.setOrderNo(order.getOrderNo());
                r.setProductName(order.getProductName());
            }
            r.setUserNickname(crossModuleMapper.selectUserNickname(r.getUserId()));
            if (r.getOldPlayerId() != null) {
                r.setPlayerNickname(crossModuleMapper.selectPlayerNickname(r.getOldPlayerId()));
            }
        }
        return R.ok(page);
    }

    @OpLog(module = "order", operation = "同意换人")
    @PutMapping("/{id}/approve")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> approve(@PathVariable Long id, @RequestBody(required = false) java.util.Map<String, String> body) {
        PlayerReplaceRequest req = replaceRequestService.getById(id);
        if (req == null) return R.fail("申请不存在");
        if (!"PENDING".equals(req.getStatus())) return R.fail("该申请已处理");
        Long operatorId = SecurityUtils.getUserId();

        Order order = orderService.getById(req.getOrderId());
        if (order == null) return R.fail("订单不存在");

        Long oldPlayerId = order.getPlayerId();

        // 1. 将所有相关 OrderPlayer 标记为 REPLACED
        List<OrderPlayer> allPlayers = orderPlayerService.list(new LambdaQueryWrapper<OrderPlayer>()
                .eq(OrderPlayer::getOrderId, order.getId())
                .in(OrderPlayer::getStatus, "ACCEPTED", "INVITED"));
        for (OrderPlayer op : allPlayers) {
            op.setStatus("REPLACED");
            orderPlayerService.updateById(op);
        }

        // 2. 清除订单打手信息和指定打手信息，回到 PAID 状态（使用显式更新，确保字段被置空）
        String fromStatus = order.getStatus();
        orderService.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Order>()
                .eq(Order::getId, order.getId())
                .set(Order::getPlayerId, null)
                .set(Order::getAssignTime, null)
                .set(Order::getAcceptTime, null)
                .set(Order::getStartTime, null)
                .set(Order::getTeammateDeadline, null)
                .set(Order::getDesignatedPlayerId, null)
                .set(Order::getStatus, "PAID")
                .set(Order::getUpdatedAt, LocalDateTime.now()));

        // 3. 进度记录
        OrderProgress progress = new OrderProgress();
        progress.setOrderId(order.getId());
        progress.setType("STATUS_CHANGE");
        progress.setFromStatus(fromStatus);
        progress.setToStatus("PAID");
        progress.setOperatorType("CS");
        progress.setOperatorId(operatorId);
        progress.setContent("客服同意换人，订单等待重新指派");
        progress.setCreatedAt(LocalDateTime.now());
        orderProgressService.save(progress);

        // 4. 更新申请状态
        req.setStatus("APPROVED");
        req.setOperatorId(operatorId);
        req.setOperatorRemark(body != null ? body.get("remark") : null);
        req.setProcessedAt(LocalDateTime.now());
        replaceRequestService.updateById(req);

        // 5. 通知旧打手
        if (oldPlayerId != null) {
            eventPublisher.publishEvent(new BusinessEvent(this, "PLAYER_REPLACED",
                    "PLAYER", oldPlayerId, order.getId(), "您已被更换，订单将由其他打手接手"));
        }
        // 6. 通知用户
        eventPublisher.publishEvent(new BusinessEvent(this, "REPLACE_APPROVED",
                "USER", order.getUserId(), order.getId(), "您的换人申请已通过，正在重新指派打手"));

        log.info("换人通过: orderId={}, 旧打手={}, 操作人={}", order.getId(), oldPlayerId, operatorId);
        return R.ok();
    }

    @OpLog(module = "order", operation = "拒绝换人")
    @PutMapping("/{id}/reject")
    public R<Void> reject(@PathVariable Long id, @RequestBody(required = false) java.util.Map<String, String> body) {
        PlayerReplaceRequest req = replaceRequestService.getById(id);
        if (req == null) return R.fail("申请不存在");
        if (!"PENDING".equals(req.getStatus())) return R.fail("该申请已处理");

        req.setStatus("REJECTED");
        req.setOperatorId(SecurityUtils.getUserId());
        req.setOperatorRemark(body != null ? body.get("remark") : null);
        req.setProcessedAt(LocalDateTime.now());
        replaceRequestService.updateById(req);

        // 通知用户
        String remark = body != null ? body.get("remark") : "";
        eventPublisher.publishEvent(new BusinessEvent(this, "REPLACE_REJECTED",
                "USER", req.getUserId(), req.getOrderId(),
                "您的换人申请被拒绝" + (remark != null && !remark.isEmpty() ? "：" + remark : "")));
        return R.ok();
    }
}
