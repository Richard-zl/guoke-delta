package com.delta.common.service;

import com.delta.common.enums.PlayerWorkStatusEnum;
import com.delta.common.util.MaxConcurrentConfigParser;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerWorkStatusCalculatorTest {

    private final PlayerWorkStatusCalculator calculator = new PlayerWorkStatusCalculator();
    private final LocalDateTime now = LocalDateTime.of(2026, 8, 27, 14, 0);

    @Test
    void resolvesStatusesByPriority() {
        assertEquals(PlayerWorkStatusEnum.ACCOUNT_ABNORMAL,
                calculator.resolve("FROZEN", null, 1, 0, 0, 1, now));
        assertEquals(PlayerWorkStatusEnum.ACCOUNT_ABNORMAL,
                calculator.resolve("ACTIVE", now.plusHours(1), 1, 0, 0, 1, now));
        assertEquals(PlayerWorkStatusEnum.OFFLINE,
                calculator.resolve("ACTIVE", null, 0, 0, 0, 1, now));
        assertEquals(PlayerWorkStatusEnum.ASSIGNED_PENDING,
                calculator.resolve("ACTIVE", null, 1, 1, 1, 1, now));
        assertEquals(PlayerWorkStatusEnum.FULL,
                calculator.resolve("ACTIVE", null, 1, 1, 0, 1, now));
        assertEquals(PlayerWorkStatusEnum.IN_SERVICE,
                calculator.resolve("ACTIVE", null, 1, 1, 0, 2, now));
        assertEquals(PlayerWorkStatusEnum.AVAILABLE,
                calculator.resolve("ACTIVE", null, 1, 0, 0, 1, now));
    }

    @Test
    void parsesMaxConcurrentSafely() {
        assertEquals(5, MaxConcurrentConfigParser.parse("5"));
        assertEquals(1, MaxConcurrentConfigParser.parse(null));
        assertEquals(1, MaxConcurrentConfigParser.parse(""));
        assertEquals(1, MaxConcurrentConfigParser.parse("abc"));
        assertEquals(1, MaxConcurrentConfigParser.parse("0"));
        assertEquals(1, MaxConcurrentConfigParser.parse("-1"));
    }
}
