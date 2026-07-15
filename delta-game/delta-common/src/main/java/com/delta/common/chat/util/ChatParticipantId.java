package com.delta.common.chat.util;

/**
 * 私聊参与者ID编码工具
 * 避免 user/player/admin 三表 ID 冲突，统一编码：type*1e9 + id
 * USER=1, PLAYER=2, CS/ADMIN=3
 */
public final class ChatParticipantId {
    private static final long BASE = 1_000_000_000L;

    public static long encodeUser(long userId) {
        return BASE + userId;
    }

    public static long encodePlayer(long playerId) {
        return 2 * BASE + playerId;
    }

    public static long encodeCs(long csId) {
        return 3 * BASE + csId;
    }

    /** 根据 userType 编码：USER/PLAYER/CS/ADMIN */
    public static long encode(String userType, long id) {
        if (userType == null) return encodeUser(id);
        return switch (userType.toUpperCase()) {
            case "USER" -> encodeUser(id);
            case "PLAYER" -> encodePlayer(id);
            case "CS", "ADMIN" -> encodeCs(id);
            default -> encodeUser(id);
        };
    }

    public static int getEntityType(long encoded) {
        return (int) (encoded / BASE);
    }

    public static long getRawId(long encoded) {
        return encoded % BASE;
    }

    /** 判断是否为 USER 类型 */
    public static boolean isUser(long encoded) {
        return getEntityType(encoded) == 1;
    }

    /** 判断是否为 PLAYER 类型 */
    public static boolean isPlayer(long encoded) {
        return getEntityType(encoded) == 2;
    }

    /** 判断是否为 CS/ADMIN 类型 */
    public static boolean isCs(long encoded) {
        return getEntityType(encoded) == 3;
    }

    /** 规范化 (id1, id2)，保证 id1 < id2 */
    public static long[] normalize(long a, long b) {
        return a <= b ? new long[]{a, b} : new long[]{b, a};
    }
}
