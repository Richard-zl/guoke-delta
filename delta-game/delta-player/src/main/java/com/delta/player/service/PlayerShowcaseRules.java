package com.delta.player.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 打手风采上架/指定纯规则，供单测与 Service 共用。 */
public final class PlayerShowcaseRules {
    public static final int MIN_VOICE_SECONDS = 15;
    public static final int MAX_VOICE_SECONDS = 60;
    public static final int MAX_GALLERY = 9;
    public static final int MAX_TAGLINE = 8;
    public static final int MAX_BIO = 200;

    private PlayerShowcaseRules() {}

    public static String publishBlockReason(String playerStatus, String voiceUrl, String tagline) {
        if (!"ACTIVE".equals(playerStatus)) {
            return "仅 ACTIVE 可上架";
        }
        if (voiceUrl == null || voiceUrl.isBlank()) {
            return "该打手没有语音介绍，无法上架";
        }
        if (tagline == null || tagline.isBlank()) {
            return "上架需要填写一句话标签";
        }
        if (tagline.length() > MAX_TAGLINE) {
            return "一句话标签最多8字";
        }
        return null;
    }

    public static String voiceBlockReason(Integer seconds) {
        if (seconds == null || seconds < MIN_VOICE_SECONDS) {
            return "至少录15秒";
        }
        if (seconds > MAX_VOICE_SECONDS) {
            return "语音最长60秒";
        }
        return null;
    }

    /** 校验仅用于风采页的运营展示指标，空值表示使用真实数据。 */
    public static String metricBlockReason(
            BigDecimal displayRating,
            Integer displayCompletedOrders,
            BigDecimal displayCompleteRate) {
        if (displayRating != null
                && (displayRating.compareTo(BigDecimal.ONE) < 0
                || displayRating.compareTo(BigDecimal.valueOf(5)) > 0)) {
            return "展示评分应在1到5之间";
        }
        if (displayCompletedOrders != null && displayCompletedOrders < 0) {
            return "展示完成单不能小于0";
        }
        if (displayCompleteRate != null
                && (displayCompleteRate.compareTo(BigDecimal.ZERO) < 0
                || displayCompleteRate.compareTo(BigDecimal.valueOf(100)) > 0)) {
            return "展示完成率应在0到100之间";
        }
        return null;
    }

    public static String filterSelectedImages(String selectedCsv, String galleryCsv) {
        Set<String> gallery = csvSet(galleryCsv);
        List<String> kept = new ArrayList<>();
        for (String u : csvList(selectedCsv)) {
            if (gallery.contains(u)) {
                kept.add(u);
            }
        }
        return String.join(",", kept);
    }

    public static boolean canDesignate(String status, Integer isOnline, int activeOrders, int maxActive) {
        return "ACTIVE".equals(status)
                && isOnline != null && isOnline == 1
                && activeOrders < maxActive;
    }

    public static String designateBlockReason(String status, Integer isOnline, int activeOrders, int maxActive) {
        if (!"ACTIVE".equals(status)) {
            return "该打手暂未展示";
        }
        if (isOnline == null || isOnline != 1) {
            return "当前离线，无法指定";
        }
        if (activeOrders >= maxActive) {
            return "当前接单已满，无法指定";
        }
        return null;
    }

    public static List<String> csvList(String csv) {
        List<String> list = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return list;
        }
        for (String part : csv.split("[,，]")) {
            String v = part.trim();
            if (!v.isEmpty()) {
                list.add(v);
            }
        }
        return list;
    }

    public static Set<String> csvSet(String csv) {
        return new LinkedHashSet<>(csvList(csv));
    }

    public static String toCsv(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return "";
        }
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String u : urls) {
            if (u != null && !u.isBlank()) {
                set.add(u.trim());
            }
        }
        return String.join(",", set);
    }
}
