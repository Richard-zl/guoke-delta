package com.delta.common.mapper;

import com.delta.common.dto.PlayerActiveOrderStats;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 跨模块查询Mapper，避免循环依赖
 */
@Mapper
public interface CrossModuleMapper {

    @Select("SELECT status FROM player WHERE id = #{playerId}")
    String selectPlayerStatus(@Param("playerId") Long playerId);

    @Select("SELECT nickname FROM player WHERE id = #{playerId}")
    String selectPlayerNickname(@Param("playerId") Long playerId);

    @Select("SELECT avatar FROM player WHERE id = #{playerId}")
    String selectPlayerAvatar(@Param("playerId") Long playerId);

    @Update("UPDATE player SET avg_rating = (SELECT COALESCE(AVG(rating),0) FROM review WHERE player_id = #{playerId}) WHERE id = #{playerId}")
    int updatePlayerAvgRating(@Param("playerId") Long playerId);

    @Select("SELECT COUNT(*) FROM complaint WHERE player_id = #{playerId} AND status IN ('PENDING','PROCESSING')")
    Long selectPendingComplaintCount(@Param("playerId") Long playerId);

    @Select("SELECT nickname FROM user WHERE id = #{userId}")
    String selectUserNickname(@Param("userId") Long userId);

    @Select("SELECT avatar FROM user WHERE id = #{userId}")
    String selectUserAvatar(@Param("userId") Long userId);

    @Select("SELECT phone FROM user WHERE id = #{userId}")
    String selectUserPhone(@Param("userId") Long userId);

    @Select("SELECT total_points FROM user WHERE id = #{userId}")
    Integer selectUserTotalPoints(@Param("userId") Long userId);

    @Select("SELECT level_code FROM user WHERE id = #{userId}")
    String selectUserLevelCode(@Param("userId") Long userId);

    /** 订单成功支付方式：WECHAT / BALANCE */
    @Select("SELECT pay_method FROM payment WHERE order_id = #{orderId} AND status = 'PAID' ORDER BY id DESC LIMIT 1")
    String selectOrderPayMethod(@Param("orderId") Long orderId);

    @Select("SELECT avatar FROM user WHERE openid = #{openid} AND avatar IS NOT NULL AND avatar != '' LIMIT 1")
    String selectUserAvatarByOpenid(@Param("openid") String openid);

    /** 用户修改昵称/头像后，同步到同 openid 的打手资料 */
    @Update("UPDATE player SET nickname = #{nickname}, avatar = #{avatar} WHERE openid = #{openid}")
    int updatePlayerProfileByOpenid(@Param("openid") String openid, @Param("nickname") String nickname, @Param("avatar") String avatar);

    @Select("SELECT nickname FROM admin WHERE id = #{adminId}")
    String selectAdminNickname(@Param("adminId") Long adminId);

    @Select("SELECT avatar FROM admin WHERE id = #{adminId}")
    String selectAdminAvatar(@Param("adminId") Long adminId);

    @Select("SELECT phone FROM admin WHERE id = #{adminId}")
    String selectAdminPhone(@Param("adminId") Long adminId);

    @Select("SELECT phone FROM player WHERE id = #{playerId}")
    String selectPlayerPhone(@Param("playerId") Long playerId);

    @Select("SELECT config_value FROM sys_config WHERE config_key = #{key} LIMIT 1")
    String selectConfigValue(@Param("key") String key);

    @Select("SELECT content FROM chat_message WHERE session_id = #{sessionId} ORDER BY id DESC LIMIT 1")
    String selectLastMessageContent(@Param("sessionId") Long sessionId);

    @Select("SELECT type FROM chat_message WHERE session_id = #{sessionId} ORDER BY id DESC LIMIT 1")
    String selectLastMessageType(@Param("sessionId") Long sessionId);

    @Select("SELECT COUNT(*) FROM chat_message WHERE session_id = #{sessionId} AND is_read = 0 AND sender_type != #{excludeSenderType}")
    int selectUnreadCount(@Param("sessionId") Long sessionId, @Param("excludeSenderType") String excludeSenderType);

    /** 某参与者的聊天总未读数（所有会话中对方发且未读的消息数） */
    @Select("SELECT COUNT(*) FROM chat_message cm INNER JOIN chat_session cs ON cs.id = cm.session_id " +
            "WHERE (cs.id1 = #{encodedId} OR cs.id2 = #{encodedId}) AND cm.is_read = 0 AND cm.sender_type != #{excludeSenderType}")
    int selectTotalChatUnread(@Param("encodedId") long encodedId, @Param("excludeSenderType") String excludeSenderType);

    /** 根据编码ID获取昵称（USER=1e9+id, PLAYER=2e9+id, CS=3e9+id） */
    default String selectNicknameByEncodedId(long encodedId) {
        int type = (int) (encodedId / 1_000_000_000L);
        long rawId = encodedId % 1_000_000_000L;
        return switch (type) {
            case 1 -> selectUserNickname(rawId);
            case 2 -> selectPlayerNickname(rawId);
            case 3 -> selectAdminNickname(rawId);
            default -> null;
        };
    }

    /** 根据编码ID获取头像 */
    default String selectAvatarByEncodedId(long encodedId) {
        int type = (int) (encodedId / 1_000_000_000L);
        long rawId = encodedId % 1_000_000_000L;
        return switch (type) {
            case 1 -> selectUserAvatar(rawId);
            case 2 -> selectPlayerAvatar(rawId);
            case 3 -> selectAdminAvatar(rawId);
            default -> null;
        };
    }

    /** 根据编码ID获取手机号 */
    default String selectPhoneByEncodedId(long encodedId) {
        int type = (int) (encodedId / 1_000_000_000L);
        long rawId = encodedId % 1_000_000_000L;
        return switch (type) {
            case 1 -> selectUserPhone(rawId);
            case 2 -> selectPlayerPhone(rawId);
            case 3 -> selectAdminPhone(rawId);
            default -> null;
        };
    }

    /**
     * 打手参与订单的判定条件：主接 OR 辅助(player_id2) OR order_player 中已接受的队友。
     * 与 OrderDisplayEnricher.buildPlayerOwnedWrapper 口径保持一致。
     */
    String PLAYER_PARTICIPATED_CONDITION =
            " AND (o.player_id = #{playerId} OR o.player_id2 = #{playerId}" +
            " OR EXISTS (SELECT 1 FROM order_player op WHERE op.order_id = o.id" +
            " AND op.player_id = #{playerId} AND op.role = 'TEAMMATE' AND op.status = 'ACCEPTED'))";

    @Select("SELECT COUNT(DISTINCT o.id) FROM `order` o WHERE o.status IN ('COMPLETED','CONFIRMED','REVIEWED')"
            + PLAYER_PARTICIPATED_CONDITION)
    int selectPlayerCompletedOrders(@Param("playerId") Long playerId);

    /** 统计打手占用订单，包含主打手、辅助打手和已接受队友，同一订单只计一次。 */
    @Select("""
            SELECT COUNT(DISTINCT occupied.order_id)
            FROM (
                SELECT o.id AS order_id
                FROM `order` o
                WHERE o.player_id = #{playerId}
                  AND o.status IN ('ASSIGNED','ACCEPTED','WAITING_TEAMMATE','IN_PROGRESS')
                UNION
                SELECT o.id
                FROM `order` o
                WHERE o.player_id2 = #{playerId}
                  AND o.status IN ('ASSIGNED','ACCEPTED','WAITING_TEAMMATE','IN_PROGRESS')
                UNION
                SELECT op.order_id
                FROM order_player op
                INNER JOIN `order` o ON o.id = op.order_id
                WHERE op.player_id = #{playerId}
                  AND op.status = 'ACCEPTED'
                  AND o.status IN ('ASSIGNED','ACCEPTED','WAITING_TEAMMATE','IN_PROGRESS')
            ) occupied
            """)
    int selectPlayerActiveOrders(@Param("playerId") Long playerId);

    /** 接受指派时排除当前订单，避免预占名额阻断当前订单确认。 */
    @Select("""
            SELECT COUNT(DISTINCT occupied.order_id)
            FROM (
                SELECT o.id AS order_id
                FROM `order` o
                WHERE o.player_id = #{playerId}
                  AND o.id != #{excludedOrderId}
                  AND o.status IN ('ASSIGNED','ACCEPTED','WAITING_TEAMMATE','IN_PROGRESS')
                UNION
                SELECT o.id
                FROM `order` o
                WHERE o.player_id2 = #{playerId}
                  AND o.id != #{excludedOrderId}
                  AND o.status IN ('ASSIGNED','ACCEPTED','WAITING_TEAMMATE','IN_PROGRESS')
                UNION
                SELECT op.order_id
                FROM order_player op
                INNER JOIN `order` o ON o.id = op.order_id
                WHERE op.player_id = #{playerId}
                  AND op.order_id != #{excludedOrderId}
                  AND op.status = 'ACCEPTED'
                  AND o.status IN ('ASSIGNED','ACCEPTED','WAITING_TEAMMATE','IN_PROGRESS')
            ) occupied
            """)
    int selectPlayerActiveOrdersExcludingOrder(
            @Param("playerId") Long playerId,
            @Param("excludedOrderId") Long excludedOrderId);

    /** 批量返回工作状态所需的占用数和待确认指派数。 */
    @Select("""
            <script>
            SELECT occupied.player_id AS playerId,
                   COUNT(DISTINCT occupied.order_id) AS activeOrders,
                   COUNT(DISTINCT CASE WHEN occupied.order_status = 'ASSIGNED'
                                       THEN occupied.order_id END) AS pendingAssignedOrders
            FROM (
                SELECT o.player_id AS player_id, o.id AS order_id, o.status AS order_status
                FROM `order` o
                WHERE o.status IN ('ASSIGNED','ACCEPTED','WAITING_TEAMMATE','IN_PROGRESS')
                  AND o.player_id IN
                  <foreach collection="playerIds" item="id" open="(" separator="," close=")">
                      #{id}
                  </foreach>
                UNION
                SELECT o.player_id2, o.id, o.status
                FROM `order` o
                WHERE o.status IN ('ASSIGNED','ACCEPTED','WAITING_TEAMMATE','IN_PROGRESS')
                  AND o.player_id2 IN
                  <foreach collection="playerIds" item="id" open="(" separator="," close=")">
                      #{id}
                  </foreach>
                UNION
                SELECT op.player_id, op.order_id, o.status
                FROM order_player op
                INNER JOIN `order` o ON o.id = op.order_id
                WHERE op.status = 'ACCEPTED'
                  AND o.status IN ('ASSIGNED','ACCEPTED','WAITING_TEAMMATE','IN_PROGRESS')
                  AND op.player_id IN
                  <foreach collection="playerIds" item="id" open="(" separator="," close=")">
                      #{id}
                  </foreach>
            ) occupied
            GROUP BY occupied.player_id
            </script>
            """)
    List<PlayerActiveOrderStats> batchSelectPlayerActiveOrderStats(
            @Param("playerIds") List<Long> playerIds);

    /** 按指定状态集统计打手参与的订单数（含辅助打手） */
    @Select("<script>SELECT COUNT(DISTINCT o.id) FROM `order` o WHERE o.status IN "
            + "<foreach item='st' collection='statuses' open='(' separator=',' close=')'>#{st}</foreach>"
            + PLAYER_PARTICIPATED_CONDITION + "</script>")
    int countPlayerParticipatedOrders(@Param("playerId") Long playerId,
                                      @Param("statuses") List<String> statuses);

    /** 同 openid 下由用户 id 查打手 id（打手端与用户端同一 token，会话列表需按打手 id 匹配） */
    @Select("SELECT p.id FROM player p INNER JOIN user u ON u.openid = p.openid WHERE u.id = #{userId} LIMIT 1")
    Long selectPlayerIdByUserId(@Param("userId") Long userId);

    @Update("UPDATE player SET is_online = 1, last_online_at = NOW() WHERE id = #{playerId}")
    int setPlayerOnline(@Param("playerId") Long playerId);

    @Select("SELECT COALESCE(is_online, 0) FROM player WHERE id = #{playerId}")
    Integer selectPlayerIsOnline(@Param("playerId") Long playerId);

    /** 统计某用户已成功购买某商品的次数（已支付及以后状态） */
    @Select("SELECT COUNT(*) FROM `order` WHERE user_id = #{userId} AND product_id = #{productId} " +
            "AND status IN ('PAID','ASSIGNED','ACCEPTED','WAITING_TEAMMATE','IN_PROGRESS','COMPLETED','CONFIRMED','REVIEWED')")
    int countUserProductOrders(@Param("userId") Long userId, @Param("productId") Long productId);

    /** 统计某用户在指定时间窗口内已成功购买某商品的次数（已支付及以后状态） */
    @Select("SELECT COUNT(*) FROM `order` WHERE user_id = #{userId} AND product_id = #{productId} " +
            "AND status IN ('PAID','ASSIGNED','ACCEPTED','WAITING_TEAMMATE','IN_PROGRESS','COMPLETED','CONFIRMED','REVIEWED') " +
            "AND (#{startTime,jdbcType=TIMESTAMP} IS NULL OR created_at >= #{startTime,jdbcType=TIMESTAMP})")
    int countUserProductOrdersSince(@Param("userId") Long userId,
                                    @Param("productId") Long productId,
                                    @Param("startTime") LocalDateTime startTime);

    /** 统计用户在周期内已支付的体验单订单数（可按订单 ID 排除当前单） */
    @Select("<script>" +
            "SELECT COUNT(*) FROM `order` o " +
            "INNER JOIN product p ON o.product_id = p.id " +
            "WHERE o.user_id = #{userId} " +
            "AND o.status IN ('PAID','ASSIGNED','ACCEPTED','WAITING_TEAMMATE','IN_PROGRESS','COMPLETED','CONFIRMED','REVIEWED') " +
            "AND (#{startTime,jdbcType=TIMESTAMP} IS NULL OR o.created_at >= #{startTime,jdbcType=TIMESTAMP}) " +
            "AND (#{excludeOrderId,jdbcType=BIGINT} IS NULL OR o.id != #{excludeOrderId}) " +
            "AND p.category_id IN " +
            "<foreach collection='categoryIds' item='cid' open='(' separator=',' close=')'>" +
            "#{cid}" +
            "</foreach>" +
            "</script>")
    int countUserPaidTrialOrdersSince(@Param("userId") Long userId,
                                      @Param("categoryIds") List<Long> categoryIds,
                                      @Param("startTime") LocalDateTime startTime,
                                      @Param("excludeOrderId") Long excludeOrderId);
}
