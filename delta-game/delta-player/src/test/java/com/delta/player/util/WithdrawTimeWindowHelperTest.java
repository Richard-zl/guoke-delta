package com.delta.player.util;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

class WithdrawTimeWindowHelperTest {
    @Test
    void tuesdayNoon_inWindow() {
        var windows = WithdrawTimeWindowHelper.defaultWindows();
        // 2026-07-28 为周二
        assertTrue(WithdrawTimeWindowHelper.isInWindow(
                LocalDateTime.of(2026, 7, 28, 12, 0), windows));
    }

    @Test
    void wednesdayNoon_outOfWindow_halfOpen() {
        var windows = WithdrawTimeWindowHelper.defaultWindows();
        assertFalse(WithdrawTimeWindowHelper.isInWindow(
                LocalDateTime.of(2026, 7, 29, 12, 0), windows));
    }

    @Test
    void monday_outOfWindow() {
        var windows = WithdrawTimeWindowHelper.defaultWindows();
        assertFalse(WithdrawTimeWindowHelper.isInWindow(
                LocalDateTime.of(2026, 7, 27, 15, 0), windows));
    }

    @Test
    void invalidJson_fallsBackToDefault() {
        assertEquals(2, WithdrawTimeWindowHelper.parseWindows("not-json").size());
    }

    @Test
    void invalidDow_fallsBackToDefault() {
        String json = "[{\"startDow\":0,\"startTime\":\"12:00\",\"endDow\":3,\"endTime\":\"12:00\"}]";
        assertEquals(2, WithdrawTimeWindowHelper.parseWindows(json).size());
    }
}
