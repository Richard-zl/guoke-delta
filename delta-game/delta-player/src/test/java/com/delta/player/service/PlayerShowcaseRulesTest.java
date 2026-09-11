package com.delta.player.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerShowcaseRulesTest {

    @Test
    void publish_requiresActiveVoiceAndTagline() {
        assertEquals("仅 ACTIVE 可上架",
                PlayerShowcaseRules.publishBlockReason("FROZEN", "http://a.mp3", "绝密"));
        assertEquals("该打手没有语音介绍，无法上架",
                PlayerShowcaseRules.publishBlockReason("ACTIVE", "", "绝密"));
        assertEquals("上架需要填写一句话标签",
                PlayerShowcaseRules.publishBlockReason("ACTIVE", "http://a.mp3", "  "));
        assertEquals("一句话标签最多8字",
                PlayerShowcaseRules.publishBlockReason("ACTIVE", "http://a.mp3", "123456789"));
        assertNull(PlayerShowcaseRules.publishBlockReason("ACTIVE", "http://a.mp3", "绝密护航"));
    }

    @Test
    void selectedImages_dropDeletedGalleryUrls() {
        assertEquals("b.jpg",
                PlayerShowcaseRules.filterSelectedImages("a.jpg,b.jpg", "b.jpg,c.jpg"));
    }

    @Test
    void designate_offlineAndFull() {
        assertEquals("当前离线，无法指定",
                PlayerShowcaseRules.designateBlockReason("ACTIVE", 0, 0, 1));
        assertEquals("当前接单已满，无法指定",
                PlayerShowcaseRules.designateBlockReason("ACTIVE", 1, 1, 1));
        assertTrue(PlayerShowcaseRules.canDesignate("ACTIVE", 1, 0, 1));
    }

    @Test
    void voice_secondsRange() {
        assertEquals("至少录15秒", PlayerShowcaseRules.voiceBlockReason(10));
        assertNull(PlayerShowcaseRules.voiceBlockReason(18));
        assertEquals("语音最长60秒", PlayerShowcaseRules.voiceBlockReason(61));
    }

    @Test
    void validatesShowcaseDisplayMetrics() {
        assertNull(PlayerShowcaseRules.metricBlockReason(null, null, null));
        assertNull(PlayerShowcaseRules.metricBlockReason(
                new BigDecimal("4.90"), 120, new BigDecimal("98.50")));
        assertEquals("展示评分应在1到5之间",
                PlayerShowcaseRules.metricBlockReason(new BigDecimal("5.01"), null, null));
        assertEquals("展示评分应在1到5之间",
                PlayerShowcaseRules.metricBlockReason(new BigDecimal("0.99"), null, null));
        assertEquals("展示完成单不能小于0",
                PlayerShowcaseRules.metricBlockReason(null, -1, null));
        assertEquals("展示完成率应在0到100之间",
                PlayerShowcaseRules.metricBlockReason(null, null, new BigDecimal("100.01")));
        assertEquals("展示完成率应在0到100之间",
                PlayerShowcaseRules.metricBlockReason(null, null, new BigDecimal("-0.01")));
    }
}
