package com.delta.product.enums;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TrialLimitPeriodEnumTest {

    @Test
    void fromDays_1天_返回DAY_1() {
        assertEquals(TrialLimitPeriodEnum.DAY_1, TrialLimitPeriodEnum.fromDays(1));
    }

    @Test
    void fromDays_0_返回NONE() {
        assertEquals(TrialLimitPeriodEnum.NONE, TrialLimitPeriodEnum.fromDays(0));
    }

    @Test
    void resolveWindowStart_2天_回溯两天() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 16, 12, 0);
        assertEquals(
                LocalDateTime.of(2026, 6, 14, 12, 0),
                TrialLimitPeriodEnum.DAY_2.resolveWindowStart(now));
    }

    @Test
    void isLimited_NONE_返回false() {
        assertFalse(TrialLimitPeriodEnum.NONE.isLimited());
        assertTrue(TrialLimitPeriodEnum.WEEK.isLimited());
    }
}
