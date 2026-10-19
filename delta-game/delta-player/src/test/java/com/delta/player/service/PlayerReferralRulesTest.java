package com.delta.player.service;

import com.delta.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerReferralRulesTest {

    @Test
    void settle_takesReferralFromClubShare() {
        PlayerReferralRules.Split split = PlayerReferralRules.settle(
                new BigDecimal("100"), new BigDecimal("0.20"), new BigDecimal("0.05"));
        assertEquals(new BigDecimal("80.00"), split.playerPool());
        assertEquals(new BigDecimal("5.00"), split.referralFee());
        assertEquals(new BigDecimal("15.00"), split.clubNet());
    }

    @Test
    void settle_capsReferralAtClubCommission() {
        PlayerReferralRules.Split split = PlayerReferralRules.settle(
                new BigDecimal("100"), new BigDecimal("0.20"), new BigDecimal("0.25"));
        assertEquals(new BigDecimal("80.00"), split.playerPool());
        assertEquals(new BigDecimal("20.00"), split.referralFee());
        assertEquals(new BigDecimal("0.00"), split.clubNet());
    }

    @Test
    void settle_withoutReferralKeepsExistingSplit() {
        PlayerReferralRules.Split split = PlayerReferralRules.settle(
                new BigDecimal("100"), new BigDecimal("0.20"), BigDecimal.ZERO);
        assertEquals(new BigDecimal("80.00"), split.playerPool());
        assertEquals(new BigDecimal("0.00"), split.referralFee());
        assertEquals(new BigDecimal("20.00"), split.clubNet());
    }

    @Test
    void normalizeRate_convertsPercent() {
        assertEquals(new BigDecimal("0.0500"), PlayerReferralRules.normalizeRate(new BigDecimal("5")));
        assertEquals(new BigDecimal("0.0512"), PlayerReferralRules.normalizeRate(new BigDecimal("5.12")));
        assertEquals(new BigDecimal("1.0000"), PlayerReferralRules.normalizeRate(new BigDecimal("100")));
    }

    @Test
    void normalizeRate_rejectsOutOfRange() {
        assertThrows(BusinessException.class, () -> PlayerReferralRules.normalizeRate(BigDecimal.ZERO));
        assertThrows(BusinessException.class, () -> PlayerReferralRules.normalizeRate(new BigDecimal("100.01")));
        assertThrows(BusinessException.class, () -> PlayerReferralRules.normalizeRate(new BigDecimal("5.123")));
    }

    @Test
    void bindBlockReason_allowsActiveAndFrozenOnly() {
        assertNull(PlayerReferralRules.bindBlockReason("ACTIVE"));
        assertNull(PlayerReferralRules.bindBlockReason("FROZEN"));
        assertEquals("离职打手不能新增转介绍客户", PlayerReferralRules.bindBlockReason("RESIGNED"));
        assertEquals("只有正常或已冻结的打手可以新增转介绍客户", PlayerReferralRules.bindBlockReason("PENDING"));
    }

    @Test
    void clawback_capsCumulativeAtReferralAmount() {
        BigDecimal order = new BigDecimal("100");
        BigDecimal referral = new BigDecimal("5.00");
        BigDecimal first = PlayerReferralRules.clawbackDue(order, referral, BigDecimal.ZERO, new BigDecimal("33.33"));
        BigDecimal second = PlayerReferralRules.clawbackDue(order, referral, first, new BigDecimal("33.33"));
        BigDecimal third = PlayerReferralRules.clawbackDue(order, referral, first.add(second), new BigDecimal("33.34"));
        assertEquals(new BigDecimal("1.67"), first);
        assertEquals(new BigDecimal("1.67"), second);
        assertEquals(new BigDecimal("1.66"), third);
        assertEquals(new BigDecimal("5.00"), first.add(second).add(third));
    }

    @Test
    void remaining_neverGoesNegative() {
        assertEquals(new BigDecimal("2.50"),
                PlayerReferralRules.remaining(new BigDecimal("5.00"), new BigDecimal("2.50")));
        assertEquals(BigDecimal.ZERO,
                PlayerReferralRules.remaining(new BigDecimal("5.00"), new BigDecimal("5.00")));
    }
}
