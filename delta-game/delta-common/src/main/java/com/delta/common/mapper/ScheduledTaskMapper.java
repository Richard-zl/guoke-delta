package com.delta.common.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

/**
 * 定时任务Mapper
 */
@Mapper
public interface ScheduledTaskMapper {

    // ========== OrderAutoAssignTask ==========

    @Select("SELECT id, required_player_count FROM `order` WHERE status = 'PAID' " +
            "AND player_id IS NULL AND designated_player_id IS NULL " +
            "AND created_at < DATE_SUB(NOW(), INTERVAL 10 MINUTE) LIMIT 20")
    List<Map<String, Object>> selectUnassignedOrders();

    @Select("SELECT p.id FROM player p WHERE p.status = 'ACTIVE' AND p.deleted = 0 " +
            "AND p.is_online = 1 " +
            "AND (p.frozen_until IS NULL OR p.frozen_until < NOW()) " +
            "AND (SELECT COUNT(*) FROM `order` o WHERE o.player_id = p.id " +
            "AND o.status IN ('ACCEPTED','IN_PROGRESS','WAITING_TEAMMATE')) < #{maxActive} " +
            "ORDER BY p.avg_rating DESC LIMIT 1")
    List<Map<String, Object>> selectAvailablePlayers(@Param("maxActive") int maxActive);

    @Select("SELECT config_value FROM sys_config WHERE config_key = #{key}")
    String selectConfigValue(@Param("key") String key);

    @Update("UPDATE `order` SET player_id = #{playerId}, assign_time = NOW(), " +
            "status = 'ASSIGNED', updated_at = NOW() WHERE id = #{orderId} AND status = 'PAID'")
    int assignOrderToPlayer(@Param("orderId") Long orderId, @Param("playerId") Long playerId);

    // ========== OrderAutoCancelTask ==========

    @Select("SELECT id FROM `order` WHERE status = 'PENDING_PAYMENT' AND pay_deadline < NOW()")
    List<Long> selectExpiredPendingOrderIds();

    @Update("UPDATE `order` SET status = 'CANCELLED', updated_at = NOW() WHERE status = 'PENDING_PAYMENT' AND pay_deadline < NOW()")
    int cancelExpiredOrders();

    @Update("UPDATE `order` SET status = 'CANCELLED', updated_at = NOW() WHERE id = #{orderId} AND status = 'PENDING_PAYMENT'")
    int cancelSingleExpiredOrder(@Param("orderId") Long orderId);

    // ========== OrderAutoCompleteTask ==========

    @Select("SELECT id FROM `order` WHERE status = 'COMPLETED' AND auto_confirm_deadline < NOW()")
    List<Long> selectAutoConfirmOrderIds();

    @Select("SELECT id, user_id FROM `order` WHERE status = 'COMPLETED' AND auto_confirm_deadline < NOW()")
    List<Map<String, Object>> selectAutoConfirmOrderInfos();

    @Update("UPDATE `order` SET status = 'CONFIRMED', confirm_time = NOW(), updated_at = NOW() " +
            "WHERE status = 'COMPLETED' AND auto_confirm_deadline < NOW()")
    int confirmExpiredOrders();

    // ========== TeammateInviteTimeoutTask ==========

    @Select("SELECT op.order_id, o.player_id FROM order_player op " +
            "JOIN `order` o ON o.id = op.order_id " +
            "WHERE op.role = 'TEAMMATE' AND op.status = 'INVITED' AND op.invite_deadline < NOW()")
    List<Map<String, Object>> selectExpiredInvites();

    @Update("UPDATE order_player SET status = 'EXPIRED' WHERE role = 'TEAMMATE' AND status = 'INVITED' AND invite_deadline < NOW()")
    int expireTeammateInvites();

    // ========== TeammateTimeoutTask ==========

    @Select("SELECT id FROM `order` WHERE status = 'WAITING_TEAMMATE' AND teammate_deadline < NOW()")
    List<Long> selectTeammateTimeoutOrderIds();

    @Select("SELECT id, player_id, user_id FROM `order` WHERE status = 'WAITING_TEAMMATE' AND teammate_deadline < NOW()")
    List<Map<String, Object>> selectTeammateTimeoutOrderInfos();

    @Update("UPDATE `order` SET status = 'PAID', player_id = NULL, updated_at = NOW() WHERE status = 'WAITING_TEAMMATE' AND teammate_deadline < NOW()")
    int releaseTeammateTimeoutOrders();

    @Update("UPDATE order_player SET status = 'RELEASED' WHERE order_id = #{orderId} AND status IN ('ACCEPTED','INVITED')")
    int releaseOrderPlayers(@Param("orderId") Long orderId);

    // ========== 公共：插入 order_progress ==========

    @Insert("INSERT INTO order_progress(order_id, type, from_status, to_status, operator_type, content, created_at) " +
            "VALUES(#{orderId}, 'STATUS_CHANGE', #{fromStatus}, #{toStatus}, 'SYSTEM', #{content}, NOW())")
    int insertOrderProgress(@Param("orderId") Long orderId, @Param("fromStatus") String fromStatus,
                            @Param("toStatus") String toStatus, @Param("content") String content);

    // ========== 公共：插入 system_notification ==========

    @Insert("INSERT INTO system_notification(receiver_type, receiver_id, title, content, biz_type, related_id, is_read, created_at) " +
            "VALUES(#{receiverType}, #{receiverId}, #{title}, #{content}, #{bizType}, #{relatedId}, 0, NOW())")
    int insertSystemNotification(@Param("receiverType") String receiverType, @Param("receiverId") Long receiverId,
                                 @Param("title") String title, @Param("content") String content,
                                 @Param("bizType") String bizType, @Param("relatedId") Long relatedId);

    // ========== WithdrawNotifyTask ==========

    @Select("SELECT id, player_id, amount FROM withdraw WHERE status = 'PENDING' AND created_at < DATE_SUB(NOW(), INTERVAL 24 HOUR)")
    List<Map<String, Object>> selectPendingWithdraws24h();

    @Insert("INSERT INTO system_notification(receiver_type, receiver_id, title, content, biz_type, related_id, is_read, created_at) " +
            "VALUES('CS', 0, '提现待审核提醒', CONCAT('提现申请#', #{withdrawId}, '超过24小时未处理'), 'WITHDRAW_REMIND', #{withdrawId}, 0, NOW())")
    int notifyAdminsWithdraw(@Param("withdrawId") Long withdrawId);

    // ========== StatisticsTask ==========

    @Insert("INSERT INTO statistics_daily (stat_date, new_orders, completed_orders, total_amount, " +
            "new_users, new_players, total_complaints, created_at) " +
            "SELECT CURDATE() - INTERVAL 1 DAY, " +
            "(SELECT COUNT(*) FROM `order` WHERE DATE(created_at) = CURDATE() - INTERVAL 1 DAY), " +
            "(SELECT COUNT(*) FROM `order` WHERE status = 'COMPLETED' AND DATE(complete_time) = CURDATE() - INTERVAL 1 DAY), " +
            "(SELECT COALESCE(SUM(amount),0) FROM payment WHERE status = 'PAID' AND DATE(paid_at) = CURDATE() - INTERVAL 1 DAY), " +
            "(SELECT COUNT(*) FROM user WHERE DATE(created_at) = CURDATE() - INTERVAL 1 DAY), " +
            "(SELECT COUNT(*) FROM player WHERE DATE(created_at) = CURDATE() - INTERVAL 1 DAY), " +
            "(SELECT COUNT(*) FROM complaint WHERE DATE(created_at) = CURDATE() - INTERVAL 1 DAY), " +
            "NOW() " +
            "ON DUPLICATE KEY UPDATE " +
            "new_orders = VALUES(new_orders), completed_orders = VALUES(completed_orders), " +
            "total_amount = VALUES(total_amount)")
    int insertDailyStatistics();
}
