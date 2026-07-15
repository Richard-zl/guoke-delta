package com.delta.product.enums;

import java.time.LocalDateTime;

public enum ProductLimitTypeEnum {
    NONE(0, "不限购"),
    PERMANENT(1, "永久限购一次"),
    WEEK(2, "一周限购一次"),
    MONTH(3, "一月限购一次");

    private final int code;
    private final String label;

    ProductLimitTypeEnum(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public boolean isLimited() {
        return this != NONE;
    }

    public LocalDateTime resolveWindowStart(LocalDateTime now) {
        return switch (this) {
            case WEEK -> now.minusWeeks(1);
            case MONTH -> now.minusMonths(1);
            default -> null;
        };
    }

    public String getExceededMessage() {
        return switch (this) {
            case PERMANENT -> "该商品为永久限购商品，每个用户仅可购买一次";
            case WEEK -> "该商品一周内仅可购买一次，请稍后再试";
            case MONTH -> "该商品一个月内仅可购买一次，请稍后再试";
            default -> "该商品不可重复购买";
        };
    }

    public static ProductLimitTypeEnum fromCode(Integer code) {
        if (code != null) {
            for (ProductLimitTypeEnum value : values()) {
                if (value.code == code) {
                    return value;
                }
            }
        }
        return NONE;
    }

    public static ProductLimitTypeEnum resolve(Integer code, Integer legacyEnabled, Integer legacyCount) {
        if (code != null) {
            return fromCode(code);
        }
        if (Integer.valueOf(1).equals(legacyEnabled) && legacyCount != null && legacyCount > 0) {
            return PERMANENT;
        }
        return NONE;
    }
}
