package com.delta.order.service.impl;

import com.delta.common.exception.BusinessException;
import com.delta.order.entity.Order;
import com.delta.order.entity.RefundRequest;
import com.delta.order.mapper.RefundRequestMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundRequestServiceImplTest {

    @Mock
    private RefundRequestMapper mapper;

    private RefundRequestServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RefundRequestServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    @Test
    void apply_待接单未指派_写入PENDING工单() {
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.insert(any(RefundRequest.class))).thenReturn(1);

        RefundRequest result = service.apply(paidOrder(), 10L, "不想要了");

        ArgumentCaptor<RefundRequest> captor = ArgumentCaptor.forClass(RefundRequest.class);
        verify(mapper).insert(captor.capture());
        RefundRequest saved = captor.getValue();
        assertEquals(1L, saved.getOrderId());
        assertEquals(10L, saved.getUserId());
        assertEquals("PAID", saved.getOrderStatusSnapshot());
        assertEquals("PENDING", saved.getStatus());
        assertEquals("不想要了", saved.getReason());
        assertNull(saved.getPlayerId());
        assertEquals("PENDING", result.getStatus());
    }

    @Test
    void apply_已指派_快照打手且保持PENDING() {
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.insert(any(RefundRequest.class))).thenReturn(1);

        service.apply(assignedOrder(), 10L, null);

        ArgumentCaptor<RefundRequest> captor = ArgumentCaptor.forClass(RefundRequest.class);
        verify(mapper).insert(captor.capture());
        assertEquals(88L, captor.getValue().getPlayerId());
        assertEquals("ASSIGNED", captor.getValue().getOrderStatusSnapshot());
    }

    @Test
    void apply_已接单状态_拒绝() {
        Order order = paidOrder();
        order.setStatus("ACCEPTED");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.apply(order, 10L, null));
        assertEquals("当前状态不允许取消", ex.getMessage());
        verify(mapper, never()).insert(any());
    }

    @Test
    void apply_同一订单第二次_拒绝() {
        when(mapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.apply(paidOrder(), 10L, null));
        assertEquals("该订单已申请过退款", ex.getMessage());
    }

    @Test
    void apply_非本人_拒绝() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.apply(paidOrder(), 99L, null));
        assertEquals("无权操作", ex.getMessage());
    }

    @Test
    void assertNotPending_有PENDING时抛出() {
        when(mapper.selectCount(any())).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.assertNotPending(1L));
        assertEquals("订单退款审核中", ex.getMessage());
    }

    @Test
    void assertNotPending_无PENDING时通过() {
        when(mapper.selectCount(any())).thenReturn(0L);
        assertDoesNotThrow(() -> service.assertNotPending(1L));
    }

    @Test
    void reject_备注为空_拒绝() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.reject(3L, 7L, "  "));
        assertEquals("请填写拒绝原因", ex.getMessage());
    }

    @Test
    void reject_PENDING工单改为REJECTED() {
        RefundRequest pending = pendingRequest();
        when(mapper.selectById(3L)).thenReturn(pending);
        when(mapper.updateById(any(RefundRequest.class))).thenReturn(1);

        RefundRequest result = service.reject(3L, 7L, "不符合退款条件");

        assertEquals("REJECTED", result.getStatus());
        assertEquals(7L, result.getOperatorId());
        assertEquals("不符合退款条件", result.getOperatorRemark());
        assertNotNull(result.getProcessedAt());
    }

    @Test
    void markApproved_非PENDING_拒绝() {
        RefundRequest done = pendingRequest();
        done.setStatus("REJECTED");
        when(mapper.selectById(3L)).thenReturn(done);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.markApproved(3L, 7L, "ok"));
        assertEquals("该申请已处理", ex.getMessage());
    }

    @Test
    void closePendingAsApproved_存在PENDING则关闭() {
        RefundRequest pending = pendingRequest();
        when(mapper.selectOne(any(), anyBoolean())).thenReturn(pending);
        when(mapper.updateById(any(RefundRequest.class))).thenReturn(1);

        service.closePendingAsApproved(1L, 7L, "客服一键退款");

        ArgumentCaptor<RefundRequest> captor = ArgumentCaptor.forClass(RefundRequest.class);
        verify(mapper).updateById(captor.capture());
        assertEquals("APPROVED", captor.getValue().getStatus());
        assertEquals("客服一键退款", captor.getValue().getOperatorRemark());
    }

    private Order paidOrder() {
        Order order = new Order();
        order.setId(1L);
        order.setUserId(10L);
        order.setStatus("PAID");
        return order;
    }

    private Order assignedOrder() {
        Order order = paidOrder();
        order.setStatus("ASSIGNED");
        order.setPlayerId(88L);
        return order;
    }

    private RefundRequest pendingRequest() {
        RefundRequest req = new RefundRequest();
        req.setId(3L);
        req.setOrderId(1L);
        req.setUserId(10L);
        req.setStatus("PENDING");
        return req;
    }
}
