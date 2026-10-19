package com.delta.order.service;

import com.delta.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RefundApproveReasonsTest {

    @Test
    void 固定原因原样保存() {
        assertEquals("无人接单", RefundApproveReasons.normalize(" 无人接单 "));
        assertEquals("协商一致", RefundApproveReasons.normalize("协商一致"));
    }

    @Test
    void 其他原因去掉首尾空格后保存() {
        assertEquals("其他：客人临时有事", RefundApproveReasons.normalize("其他：  客人临时有事  "));
    }

    @Test
    void 未选择或其他为空时拒绝() {
        assertEquals("请选择退款原因",
                assertThrows(BusinessException.class, () -> RefundApproveReasons.normalize(" ")).getMessage());
        assertEquals("请填写其他原因",
                assertThrows(BusinessException.class, () -> RefundApproveReasons.normalize("其他")).getMessage());
        assertEquals("请填写其他原因",
                assertThrows(BusinessException.class, () -> RefundApproveReasons.normalize("其他：  ")).getMessage());
    }

    @Test
    void 不在选项里的原因拒绝() {
        assertEquals("请选择退款原因",
                assertThrows(BusinessException.class, () -> RefundApproveReasons.normalize("随便写")).getMessage());
    }
}
