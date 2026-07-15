package com.delta.product.enums;

import java.time.LocalDateTime;

/**
 * 体验单全局限购周期（天数）
 */
public enum TrialLimitPeriodEnum {
    NONE(0, "不限购"),
    DAY_1(1, "1天限购1单"),
    DAY_2(2, "2天限购1单"),
    WEEK(7, "一周限购1单"),
    MONTH(30, "一月限购1单");

    private final int days;
    private final String label;

    TrialLimitPeriodEnum(int days, String label) {
        this.days = days;
        this.label = label;
    }

    public int getDays() {
        return days;
    }

    public String getLabel() {
        return label;
    }

    public boolean isLimited() {
        return this != NONE;
    }

    public LocalDateTime resolveWindowStart(LocalDateTime now) {
        if (!isLimited()) {
            return null;
        }
        return now.minusDays(days);
    }

    public String getExceededMessage() {
        return switch (this) {
            case DAY_1 -> "体验单每1天限购1单，请稍后再试";
            case DAY_2 -> "体验单每2天限购1单，请稍后再试";
            case WEEK -> "体验单每周限购1单，请稍后再试";
            case MONTH -> "体验单每月限购1单，请稍后再试";
            default -> "体验单不可重复购买";
        };
    }

    public String getLimitTip() {
        return switch (this) {
            case DAY_1 -> "体验单每1天限购1单";
            case DAY_2 -> "体验单每2天限购1单";
            case WEEK -> "体验单每周限购1单";
            case MONTH -> "体验单每月限购1单";
            default -> "";
        };
    }

    public static TrialLimitPeriodEnum fromDays(Integer days) {
        if (days == null) {
            return NONE;
        }
        for (TrialLimitPeriodEnum value : values()) {
            if (value.days == days) {
                return value;
            }
        }
        return NONE;
    }
}
