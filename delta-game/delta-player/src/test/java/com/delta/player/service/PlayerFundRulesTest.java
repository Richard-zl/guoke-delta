package com.delta.player.service;

import com.delta.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerFundRulesTest {

    @Test
    void signedAmount_addAndDeduct() {
        assertEquals(new BigDecimal("12.50"),
                PlayerFundRules.signedAmount("ADD", new BigDecimal("12.5")));
        assertEquals(new BigDecimal("-3.00"),
                PlayerFundRules.signedAmount("DEDUCT", new BigDecimal("3")));
    }

    @Test
    void signedAmount_rejectsInvalidInput() {
        assertThrows(BusinessException.class,
                () -> PlayerFundRules.signedAmount("ADD", BigDecimal.ZERO));
        assertThrows(BusinessException.class,
                () -> PlayerFundRules.signedAmount("OTHER", new BigDecimal("1")));
        assertThrows(BusinessException.class,
                () -> PlayerFundRules.signedAmount("DEDUCT", new BigDecimal("1.234")));
    }

    @Test
    void remark_requiresTextWithinLimit() {
        assertEquals("违规扣款", PlayerFundRules.normalizeRemark(" 违规扣款 "));
        assertThrows(BusinessException.class, () -> PlayerFundRules.normalizeRemark("  "));
        assertThrows(BusinessException.class,
                () -> PlayerFundRules.normalizeRemark("啊".repeat(201)));
    }

    @Test
    void resign_onlyActiveOrFrozen() {
        assertNull(PlayerFundRules.resignBlockReason("ACTIVE"));
        assertNull(PlayerFundRules.resignBlockReason("FROZEN"));
        assertEquals("该打手已经离职", PlayerFundRules.resignBlockReason("RESIGNED"));
        assertEquals("只有正常或已冻结的打手可以离职", PlayerFundRules.resignBlockReason("PENDING"));
    }

    @Test
    void delete_requiresResignedAndSettled() {
        assertNull(PlayerFundRules.deleteBlockReason(
                "RESIGNED", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0));
        assertEquals("只能删除已离职的打手",
                PlayerFundRules.deleteBlockReason("ACTIVE", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0));
        assertEquals("还有未完结订单，不能删除",
                PlayerFundRules.deleteBlockReason("RESIGNED", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 1, 0));
        assertEquals("可提现余额不为0，不能删除",
                PlayerFundRules.deleteBlockReason("RESIGNED", new BigDecimal("1.00"), BigDecimal.ZERO, BigDecimal.ZERO, 0, 0));
        assertEquals("还有待入账金额，不能删除",
                PlayerFundRules.deleteBlockReason("RESIGNED", BigDecimal.ZERO, new BigDecimal("2"), BigDecimal.ZERO, 0, 0));
        assertEquals("还有冻结金额，不能删除",
                PlayerFundRules.deleteBlockReason("RESIGNED", BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("3"), 0, 0));
        assertEquals("还有待审核提现，不能删除",
                PlayerFundRules.deleteBlockReason("RESIGNED", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 1));
    }
}
