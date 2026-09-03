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
import com.delta.order.entity.RefundRequest;
import com.delta.order.service.OrderService;
import com.delta.order.service.RefundRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/cs/refund")
@RequiredArgsConstructor
public class CsRefundController {

    private final RefundRequestService refundRequestService;
    private final OrderService orderService;
    private final CrossModuleMapper crossModuleMapper;
    private final ApplicationEventPublisher eventPublisher;

    @GetMapping("/list")
    public R<Page<RefundRequest>> list(PageQuery query,
                                       @RequestParam(value = "status", required = false) String status) {
        LambdaQueryWrapper<RefundRequest> w = new LambdaQueryWrapper<RefundRequest>()
                .eq(status != null && !status.isEmpty(), RefundRequest::getStatus, status)
                .orderByDesc(RefundRequest::getCreatedAt);
        Page<RefundRequest> page = refundRequestService.page(
                new Page<>(query.getPageNum(), query.getPageSize()), w);
        for (RefundRequest r : page.getRecords()) {
            Order order = orderService.getById(r.getOrderId());
            if (order != null) {
                r.setOrderNo(order.getOrderNo());
                r.setProductName(order.getProductName());
            }
            r.setUserNickname(crossModuleMapper.selectUserNickname(r.getUserId()));
            if (r.getPlayerId() != null) {
                r.setPlayerNickname(crossModuleMapper.selectPlayerNickname(r.getPlayerId()));
            }
        }
        return R.ok(page);
    }

    @OpLog(module = "order", operation = "同意退款")
    @PutMapping("/{id}/approve")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> approve(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        Long operatorId = SecurityUtils.getUserId();
        String remark = body != null ? body.get("remark") : null;
        RefundRequest req = refundRequestService.markApproved(id, operatorId, remark);
        orderService.refundAfterReview(req.getOrderId(), operatorId);
        return R.ok();
    }

    @OpLog(module = "order", operation = "拒绝退款")
    @PutMapping("/{id}/reject")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        String remark = body != null ? body.get("remark") : null;
        Long operatorId = SecurityUtils.getUserId();
        RefundRequest req = refundRequestService.reject(id, operatorId, remark);
        eventPublisher.publishEvent(new BusinessEvent(this, "REFUND_REJECTED",
                "USER", req.getUserId(), req.getOrderId(),
                "退款申请未通过：" + req.getOperatorRemark()));
        if (req.getPlayerId() != null) {
            eventPublisher.publishEvent(new BusinessEvent(this, "REFUND_REJECTED",
                    "PLAYER", req.getPlayerId(), req.getOrderId(), "用户退款申请未通过，请继续服务"));
        }
        return R.ok();
    }
}
