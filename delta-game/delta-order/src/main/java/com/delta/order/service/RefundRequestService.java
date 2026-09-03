package com.delta.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.delta.order.entity.Order;
import com.delta.order.entity.RefundRequest;

import java.util.Collection;
import java.util.List;

public interface RefundRequestService extends IService<RefundRequest> {

    /** 大厅/自动派单排除审核中订单 */
    String PENDING_ORDER_ID_SQL = "SELECT order_id FROM refund_request WHERE status = 'PENDING'";

    RefundRequest apply(Order order, Long userId, String reason);

    void assertNotPending(Long orderId);

    boolean isPending(Long orderId);

    List<Long> listPendingOrderIds(Collection<Long> orderIds);

    RefundRequest markApproved(Long requestId, Long operatorId, String remark);

    RefundRequest reject(Long requestId, Long operatorId, String remark);

    void closePendingAsApproved(Long orderId, Long operatorId, String remark);
}
