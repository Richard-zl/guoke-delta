package com.delta.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.delta.common.enums.OrderStatusEnum;
import com.delta.common.exception.BusinessException;
import com.delta.order.entity.Order;
import com.delta.order.entity.RefundRequest;
import com.delta.order.mapper.RefundRequestMapper;
import com.delta.order.service.RefundRequestService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Service
public class RefundRequestServiceImpl extends ServiceImpl<RefundRequestMapper, RefundRequest>
        implements RefundRequestService {

    @Override
    public RefundRequest apply(Order order, Long userId, String reason) {
        if (order == null) throw new BusinessException("订单不存在");
        if (!userId.equals(order.getUserId())) throw new BusinessException("无权操作");
        String status = order.getStatus();
        boolean paidUnassigned = OrderStatusEnum.PAID.name().equals(status) && order.getPlayerId() == null;
        boolean assigned = OrderStatusEnum.ASSIGNED.name().equals(status);
        if (!paidUnassigned && !assigned) {
            throw new BusinessException("当前状态不允许取消");
        }
        long existing = count(new LambdaQueryWrapper<RefundRequest>()
                .eq(RefundRequest::getOrderId, order.getId()));
        if (existing > 0) {
            throw new BusinessException("该订单已申请过退款");
        }
        RefundRequest req = new RefundRequest();
        req.setOrderId(order.getId());
        req.setUserId(userId);
        req.setPlayerId(order.getPlayerId());
        req.setOrderStatusSnapshot(status);
        req.setReason(reason);
        req.setStatus("PENDING");
        req.setCreatedAt(LocalDateTime.now());
        save(req);
        return req;
    }

    @Override
    public void assertNotPending(Long orderId) {
        if (isPending(orderId)) {
            throw new BusinessException("订单退款审核中");
        }
    }

    @Override
    public boolean isPending(Long orderId) {
        if (orderId == null) return false;
        return count(new LambdaQueryWrapper<RefundRequest>()
                .eq(RefundRequest::getOrderId, orderId)
                .eq(RefundRequest::getStatus, "PENDING")) > 0;
    }

    @Override
    public List<Long> listPendingOrderIds(Collection<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) return List.of();
        return list(new LambdaQueryWrapper<RefundRequest>()
                .select(RefundRequest::getOrderId)
                .eq(RefundRequest::getStatus, "PENDING")
                .in(RefundRequest::getOrderId, orderIds))
                .stream()
                .map(RefundRequest::getOrderId)
                .toList();
    }

    @Override
    public RefundRequest markApproved(Long requestId, Long operatorId, String remark) {
        RefundRequest req = requirePending(requestId);
        req.setStatus("APPROVED");
        req.setOperatorId(operatorId);
        req.setOperatorRemark(remark);
        req.setProcessedAt(LocalDateTime.now());
        updateById(req);
        return req;
    }

    @Override
    public RefundRequest reject(Long requestId, Long operatorId, String remark) {
        if (remark == null || remark.isBlank()) {
            throw new BusinessException("请填写拒绝原因");
        }
        RefundRequest req = requirePending(requestId);
        req.setStatus("REJECTED");
        req.setOperatorId(operatorId);
        req.setOperatorRemark(remark.trim());
        req.setProcessedAt(LocalDateTime.now());
        updateById(req);
        return req;
    }

    @Override
    public void closePendingAsApproved(Long orderId, Long operatorId, String remark) {
        RefundRequest pending = getOne(new LambdaQueryWrapper<RefundRequest>()
                .eq(RefundRequest::getOrderId, orderId)
                .eq(RefundRequest::getStatus, "PENDING")
                .last("LIMIT 1"));
        if (pending == null) return;
        pending.setStatus("APPROVED");
        pending.setOperatorId(operatorId);
        pending.setOperatorRemark(remark);
        pending.setProcessedAt(LocalDateTime.now());
        updateById(pending);
    }

    private RefundRequest requirePending(Long requestId) {
        RefundRequest req = getById(requestId);
        if (req == null) throw new BusinessException("申请不存在");
        if (!"PENDING".equals(req.getStatus())) throw new BusinessException("该申请已处理");
        return req;
    }
}
