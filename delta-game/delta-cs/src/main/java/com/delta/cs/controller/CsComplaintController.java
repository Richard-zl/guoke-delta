package com.delta.cs.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.annotation.OpLog;
import com.delta.common.domain.PageQuery;
import com.delta.common.domain.R;
import com.delta.common.event.BusinessEvent;
import com.delta.common.event.OrderConfirmedEvent;
import com.delta.common.security.utils.SecurityUtils;
import com.delta.common.utils.ImageListUtils;
import com.delta.order.entity.Complaint;
import com.delta.order.entity.Order;
import com.delta.order.entity.OrderProgress;
import com.delta.order.service.ComplaintService;
import com.delta.order.service.OrderService;
import com.delta.pay.entity.Payment;
import com.delta.pay.service.PaymentService;
import com.delta.order.service.OrderProgressService;
import com.delta.player.entity.Player;
import com.delta.player.service.PlayerService;
import com.delta.player.entity.PlayerWallet;
import com.delta.player.service.PlayerWalletService;
import com.delta.pay.service.TransactionService;
import com.delta.common.mapper.CrossModuleMapper;
import com.delta.order.dto.ComplaintDetailVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;

@Slf4j
@RestController
@RequestMapping("/cs/complaint")
@RequiredArgsConstructor
public class CsComplaintController {
    private final ComplaintService complaintService;
    private final OrderService orderService;
    private final OrderProgressService orderProgressService;
    private final PaymentService paymentService;
    private final PlayerService playerService;
    private final PlayerWalletService playerWalletService;
    private final TransactionService transactionService;
    private final ApplicationEventPublisher eventPublisher;
    private final CrossModuleMapper crossModuleMapper;

    /**
     * 客服代用户创建投诉工单
     */
    @OpLog(module = "complaint", operation = "创建投诉工单")
    @PostMapping("/create")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> create(@RequestBody Complaint complaint) {
        // 1. 校验订单存在
        Order order = orderService.getById(complaint.getOrderId());
        if (order == null) return R.fail("订单不存在");
        // 2. 校验用户ID与订单匹配
        if (!order.getUserId().equals(complaint.getUserId())) return R.fail("用户ID与订单不匹配");
        // 3. 校验是否已有进行中的投诉
        long existCount = complaintService.count(new LambdaQueryWrapper<Complaint>()
                .eq(Complaint::getOrderId, complaint.getOrderId())
                .in(Complaint::getStatus, "PENDING", "PROCESSING"));
        if (existCount > 0) return R.fail("该订单已有进行中的投诉");
        // 4. 创建投诉
        complaint.setPlayerId(order.getPlayerId());
        complaint.setImages(ImageListUtils.normalize(complaint.getImages()));
        complaint.setStatus("PENDING");
        complaintService.save(complaint);
        // 5. 更新订单状态为 DISPUTED（如果订单状态允许）
        String status = order.getStatus();
        if ("IN_PROGRESS".equals(status) || "COMPLETED".equals(status) || "CONFIRMED".equals(status)) {
            orderService.disputeOrder(complaint.getOrderId());
        }
        return R.ok();
    }

    /**
     * 投诉详情
     */
    @GetMapping("/{id}")
    public R<ComplaintDetailVO> detail(@PathVariable Long id) {
        Complaint c = complaintService.getById(id);
        if (c == null) return R.fail("投诉不存在");
        if (c.getCsReadAt() == null) {
            c.setCsReadAt(LocalDateTime.now());
            complaintService.updateById(c);
        }

        Order order = orderService.getById(c.getOrderId());

        ComplaintDetailVO vo = new ComplaintDetailVO();
        vo.setComplaint(c);
        vo.setOrder(order);

        if (order != null) {
            // 用户信息
            vo.setUserNickname(crossModuleMapper.selectUserNickname(order.getUserId()));
            vo.setUserAvatar(crossModuleMapper.selectUserAvatar(order.getUserId()));
            // 打手信息
            if (order.getPlayerId() != null) {
                vo.setPlayerNickname(crossModuleMapper.selectPlayerNickname(order.getPlayerId()));
                vo.setPlayerAvatar(crossModuleMapper.selectPlayerAvatar(order.getPlayerId()));
            }
            // 订单进度
            java.util.List<OrderProgress> progress = orderProgressService.list(
                    new LambdaQueryWrapper<OrderProgress>()
                            .eq(OrderProgress::getOrderId, order.getId())
                            .orderByAsc(OrderProgress::getCreatedAt)
            );
            vo.setProgress(progress);
        }

        return R.ok(vo);
    }

    /**
     * 客服受理投诉：设置状态为PROCESSING（处理中）
     * H5端发送 JSON body: { remark: '...' }
     * MP端发送 JSON body: { remark: '...' }
     */
    @OpLog(module = "complaint", operation = "受理投诉")
    @PutMapping("/{id}/process")
    public R<Void> process(@PathVariable Long id, @RequestBody(required = false) java.util.Map<String, String> body) {
        Complaint c = complaintService.getById(id);
        if (c == null) return R.fail("投诉不存在");
        if (!"PENDING".equals(c.getStatus())) return R.fail("只有待处理的投诉可以受理");
        c.setStatus("PROCESSING");
        if (c.getCsReadAt() == null) c.setCsReadAt(LocalDateTime.now());
        complaintService.updateById(c);
        return R.ok();
    }

    @GetMapping("/list")
    public R<Page<Complaint>> list(PageQuery query, @RequestParam(value = "status", required = false) String status) {
        LambdaQueryWrapper<Complaint> wrapper = new LambdaQueryWrapper<Complaint>()
                .orderByDesc(Complaint::getCreatedAt);
        // status 为空字符串时不作为筛选条件
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Complaint::getStatus, status);
        }
        Page<Complaint> page = complaintService.page(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        return R.ok(page);
    }

    /**
     * 仲裁处理：根据结果执行退款/确认/重做/处罚
     */
    @OpLog(module = "complaint", operation = "仲裁处理")
    @PutMapping("/{id}/resolve")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> resolve(@PathVariable Long id, @RequestBody Complaint complaint) {
        Complaint c = complaintService.getById(id);
        if (c == null) return R.fail("投诉不存在");
        Long operatorId = SecurityUtils.getUserId();

        c.setResult(complaint.getResult());
        c.setRefundAmount(complaint.getRefundAmount());
        c.setResultReason(complaint.getResultReason());
        c.setPlayerPenalty(complaint.getPlayerPenalty());
        c.setOperatorId(operatorId);
        c.setResolvedAt(LocalDateTime.now());
        c.setStatus("RESOLVED");
        complaintService.updateById(c);

        // 更新订单仲裁状态
        orderService.arbitrateOrder(c.getOrderId(), complaint.getResult(), operatorId);

        // 根据仲裁结果分支处理
        String result = complaint.getResult();
        Order order = orderService.getById(c.getOrderId());
        switch (result != null ? result : "") {
            case "FULL_REFUND" -> {
                // 全额退款
                Payment payment = findPaidPayment(c.getOrderId());
                if (payment != null) {
                    paymentService.refund(payment.getId(), order.getAmount(), "仲裁全额退款: " + complaint.getResultReason());
                    refundPlayerIncomeIfSettled(order, order.getAmount());
                }
                notifyBoth(order, c, "投诉已处理，全额退款中", "订单仲裁结果：全额退款");
            }
            case "PARTIAL_REFUND" -> {
                // 部分退款
                Payment payment = findPaidPayment(c.getOrderId());
                if (payment != null && complaint.getRefundAmount() != null) {
                    paymentService.refund(payment.getId(), complaint.getRefundAmount(), "仲裁部分退款: " + complaint.getResultReason());
                    refundPlayerIncomeIfSettled(order, complaint.getRefundAmount());
                }
                notifyBoth(order, c, "投诉已处理，部分退款 " + complaint.getRefundAmount() + " 元",
                        "订单仲裁结果：部分退款 " + complaint.getRefundAmount() + " 元");
            }
            case "REJECT" -> {
                // 驳回投诉：将订单状态改为CONFIRMED并触发结算
                String rejectFrom = order.getStatus();
                order.setStatus("CONFIRMED");
                order.setConfirmTime(LocalDateTime.now());
                order.setUpdatedAt(LocalDateTime.now());
                orderService.updateById(order);
                addProgress(order.getId(), rejectFrom, "CONFIRMED", "CS", operatorId, "仲裁驳回投诉，订单正常确认");
                eventPublisher.publishEvent(new OrderConfirmedEvent(this, order.getId(), "CS"));
                notifyBoth(order, c, "投诉已驳回，原因：" + complaint.getResultReason(),
                        "投诉已驳回，订单正常结算");
            }
            case "REDO" -> {
                // 重新服务：回退到ACCEPTED
                String redoFrom = order.getStatus();
                order.setStatus("ACCEPTED");
                order.setCompleteTime(null);
                order.setAutoConfirmDeadline(null);
                order.setUpdatedAt(LocalDateTime.now());
                orderService.updateById(order);
                addProgress(order.getId(), redoFrom, "ACCEPTED", "CS", operatorId, "仲裁要求重新服务");
                notifyBoth(order, c, "仲裁结果：打手将重新为您提供服务",
                        "订单仲裁结果：需重新提供服务");
            }
            default -> log.warn("未知仲裁结果类型: {}", result);
        }

        // 处罚打手
        if (complaint.getPlayerPenalty() != null && !"NONE".equals(complaint.getPlayerPenalty())) {
            handlePlayerPenalty(c.getOrderId(), complaint.getPlayerPenalty());
        }

        return R.ok();
    }

    private Payment findPaidPayment(Long orderId) {
        return paymentService.getOne(new LambdaQueryWrapper<Payment>()
                .eq(Payment::getOrderId, orderId)
                .eq(Payment::getStatus, "PAID")
                .last("LIMIT 1"));
    }

    private void notifyBoth(Order order, Complaint c, String userMsg, String playerMsg) {
        if (order != null) {
            eventPublisher.publishEvent(new BusinessEvent(this, "COMPLAINT_RESOLVED",
                    "USER", order.getUserId(), c.getId(), userMsg));
            if (order.getPlayerId() != null) {
                eventPublisher.publishEvent(new BusinessEvent(this, "COMPLAINT_RESOLVED",
                        "PLAYER", order.getPlayerId(), c.getId(), playerMsg));
            }
        }
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

    private void handlePlayerPenalty(Long orderId, String penalty) {
        Order order = orderService.getById(orderId);
        if (order == null || order.getPlayerId() == null) return;
        if ("FREEZE".equals(penalty)) {
            Player player = playerService.getById(order.getPlayerId());
            if (player != null) {
                player.setStatus("FROZEN");
                playerService.updateById(player);
                log.info("打手{}已被冻结，关联订单{}", order.getPlayerId(), orderId);
            }
        }
    }

    /**
     * 如果订单已经结算给打手，则在退款时从打手钱包中扣回对应收益
     */
    private void refundPlayerIncomeIfSettled(Order order, java.math.BigDecimal refundAmount) {
        if (refundAmount == null || refundAmount.compareTo(java.math.BigDecimal.ZERO) <= 0) return;
        if (order == null) return;
        // 未结算，不需要从打手端扣款
        if (order.getSettled() == null || order.getSettled() != 1) {
            return;
        }
        if (order.getPlayerId() == null) return;

        PlayerWallet wallet = playerWalletService.getByPlayerId(order.getPlayerId());
        if (wallet == null) {
            log.warn("退款扣款失败: 打手钱包不存在, playerId={}, orderId={}", order.getPlayerId(), order.getId());
            return;
        }
        java.math.BigDecimal balanceBefore = wallet.getBalance();
        if (balanceBefore == null) balanceBefore = java.math.BigDecimal.ZERO;
        // 为避免余额为负，扣款额不超过当前余额
        java.math.BigDecimal deduction = refundAmount.min(balanceBefore);
        if (deduction.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return;
        }
        java.math.BigDecimal balanceAfter = balanceBefore.subtract(deduction);
        wallet.setBalance(balanceAfter);
        playerWalletService.updateById(wallet);

        // 记录一条打手端退款扣款流水
        transactionService.record("REFUND", "PLAYER", order.getPlayerId(),
                deduction.negate(), // 扣款记为负数
                balanceBefore, balanceAfter,
                order.getId(), null, null,
                "投诉仲裁退款扣除收益，订单金额退款：" + refundAmount);
        log.info("投诉仲裁退款，已从打手{}钱包扣除{}，orderId={}", order.getPlayerId(), deduction, order.getId());
    }
}
