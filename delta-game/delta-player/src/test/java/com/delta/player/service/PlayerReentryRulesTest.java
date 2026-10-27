package com.delta.player.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerReentryRulesTest {

    @Test
    void insertUsesUserIdOnlyWhenPrimaryKeyIsFree() {
        assertEquals(88L, PlayerReentryRules.playerIdForInsert(88L, false));
        assertNull(PlayerReentryRules.playerIdForInsert(88L, true));
        assertNull(PlayerReentryRules.playerIdForInsert(null, false));
    }

    @Test
    void releasedOpenidFreesTheOriginalValue() {
        String openid = "oABC1234567890";
        String released = PlayerReentryRules.releasedOpenid(openid, 369L);
        assertNotEquals(openid, released);
        assertTrue(released.endsWith("#resigned#369"));
        assertTrue(released.length() <= PlayerReentryRules.OPENID_MAX_LENGTH);
    }

    @Test
    void releasedOpenidStaysWithinColumnLength() {
        String openid = "o".repeat(64);
        String released = PlayerReentryRules.releasedOpenid(openid, 12L);
        assertEquals(64, released.length());
        assertTrue(released.endsWith("#resigned#12"));
        assertNotEquals(openid, released);
    }
}
