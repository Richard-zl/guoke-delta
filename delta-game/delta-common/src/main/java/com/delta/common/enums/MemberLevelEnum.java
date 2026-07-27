package com.delta.common.enums;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * 会员等级：按累计总积分定级，享受永久折扣（任务/定制等排除分类除外）
 */
@Getter
public enum MemberLevelEnum {
    BRONZE("BRONZE", "青铜伴星", 0, 9999, "无会员折扣", "1.00"),
    SILVER("SILVER", "白银伴星", 10000, 34999, "永久9.8折（任务及定制单除外）", "0.98"),
    GOLD("GOLD", "黄金伴星", 35000, 104999, "永久9.6折（任务及定制单除外）", "0.96"),
    PLATINUM("PLATINUM", "铂金伴星", 105000, 244999, "永久9.4折（任务及定制单除外）", "0.94"),
    DIAMOND("DIAMOND", "钻石伴星", 245000, 559999, "永久9.2折（任务及定制单除外）", "0.92"),
    KING("KING", "王者伴星", 560000, Integer.MAX_VALUE, "永久9.0折（任务及定制单除外）", "0.90");

    private final String code;
    private final String name;
    private final int minPoints;
    private final int maxPoints;
    private final String description;
    /** 永久折扣率，1.00 表示无折扣 */
    private final BigDecimal discountRate;
    /** 兼容旧字段：系统不再自动发券，恒为 0 */
    private final int discount9Count;
    private final int discount8Count;
    private final int discount75Count;
    private final int cash5Count;

    MemberLevelEnum(String code, String name, int minPoints, int maxPoints, String description, String discountRate) {
        this.code = code;
        this.name = name;
        this.minPoints = minPoints;
        this.maxPoints = maxPoints;
        this.description = description;
        this.discountRate = new BigDecimal(discountRate);
        this.discount9Count = 0;
        this.discount8Count = 0;
        this.discount75Count = 0;
        this.cash5Count = 0;
    }

    /** 是否有实际会员折扣（折扣率 &lt; 1） */
    public boolean hasDiscount() {
        return discountRate.compareTo(BigDecimal.ONE) < 0;
    }

    public static MemberLevelEnum getByPoints(int points) {
        for (MemberLevelEnum level : values()) {
            if (points >= level.minPoints && points <= level.maxPoints) {
                return level;
            }
        }
        return BRONZE;
    }

    public static MemberLevelEnum getByCode(String code) {
        if (code == null || code.isBlank()) {
            return BRONZE;
        }
        for (MemberLevelEnum level : values()) {
            if (level.code.equals(code)) {
                return level;
            }
        }
        return BRONZE;
    }
}
