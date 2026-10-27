package com.delta.player.service;

/**
 * 离职删除后重新入驻的主键和 openid 规则。
 * 删除是逻辑删除，旧行仍占着主键；openid 有唯一索引，不改写就无法再次插入。
 */
public final class PlayerReentryRules {

    public static final int OPENID_MAX_LENGTH = 64;

    private PlayerReentryRules() {
    }

    /**
     * 首次入驻沿用用户 ID。该 ID 已被占用（含已逻辑删除的打手）时返回 null，改由数据库分配新 ID。
     */
    public static Long playerIdForInsert(Long userId, boolean idTaken) {
        if (userId == null || idTaken) {
            return null;
        }
        return userId;
    }

    /** 给已删除打手改写 openid，腾出原值给重新入驻。结果不超过字段长度，且和原值不同。 */
    public static String releasedOpenid(String openid, Long playerId) {
        String raw = (openid == null ? "" : openid) + "#resigned#" + playerId;
        if (raw.length() <= OPENID_MAX_LENGTH) {
            return raw;
        }
        return raw.substring(raw.length() - OPENID_MAX_LENGTH);
    }
}
