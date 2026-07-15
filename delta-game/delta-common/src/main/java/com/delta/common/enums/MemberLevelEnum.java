package com.delta.common.enums;

import lombok.Getter;

@Getter
public enum MemberLevelEnum {
    BRONZE("BRONZE", "青铜伴星", 0, 999, "0-999分", 0, 0, 0, 0),
    SILVER("SILVER", "白银伴星", 1000, 4999, "每月2张9折券", 2, 0, 0, 0),
    GOLD("GOLD", "黄金伴星", 5000, 14999, "每月4张9折券，优先派单权", 4, 0, 0, 0),
    DIAMOND("DIAMOND", "钻石伴星", 15000, 49999, "每月6张9折券，每月1张8折券，优先派单权，专属VIP群", 6, 1, 0, 0),
    KING("KING", "王者伴星", 50000, Integer.MAX_VALUE, "专属客服，专属VIP群，每月8张9折券，每月2张8折券，新品内测资格，年度定制礼品", 8, 2, 0, 0);

    private final String code;
    private final String name;
    private final int minPoints;
    private final int maxPoints;
    private final String description;
    private final int discount9Count;
    private final int discount8Count;
    private final int discount75Count;
    private final int cash5Count;

    MemberLevelEnum(String code, String name, int minPoints, int maxPoints, String description,
                    int discount9Count, int discount8Count, int discount75Count, int cash5Count) {
        this.code = code;
        this.name = name;
        this.minPoints = minPoints;
        this.maxPoints = maxPoints;
        this.description = description;
        this.discount9Count = discount9Count;
        this.discount8Count = discount8Count;
        this.discount75Count = discount75Count;
        this.cash5Count = cash5Count;
    }

    public static MemberLevelEnum getByPoints(int points) {
        for (MemberLevelEnum level : values()) {
            if (points >= level.minPoints && points <= level.maxPoints) {
                return level;
            }
        }
        return BRONZE;
    }
}