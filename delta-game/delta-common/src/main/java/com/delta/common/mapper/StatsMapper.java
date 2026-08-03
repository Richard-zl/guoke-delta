package com.delta.common.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 统计/Dashboard聚合查询Mapper
 */
@Mapper
public interface StatsMapper {

    // ========== 按日期计数 ==========

    @Select("SELECT COUNT(*) FROM `order` WHERE DATE(created_at) = #{date}")
    Long countOrdersByDate(@Param("date") String date);

    @Select("SELECT COALESCE(SUM(amount),0) FROM `order` WHERE DATE(created_at) = #{date} AND status NOT IN ('CANCELLED','PENDING_PAYMENT')")
    BigDecimal sumOrderAmountByDate(@Param("date") String date);

    @Select("SELECT COUNT(*) FROM user WHERE DATE(created_at) = #{date}")
    Long countUsersByDate(@Param("date") String date);

    @Select("SELECT COUNT(*) FROM player WHERE DATE(created_at) = #{date}")
    Long countPlayersByDate(@Param("date") String date);

    @Select("SELECT COUNT(*) FROM complaint WHERE DATE(created_at) = #{date}")
    Long countComplaintsByDate(@Param("date") String date);

    @Select("SELECT COUNT(*) FROM complaint WHERE DATE(resolved_at) = #{date}")
    Long countResolvedComplaintsByDate(@Param("date") String date);

    @Select("SELECT COUNT(*) FROM `order` WHERE DATE(complete_time) = #{date} AND status IN ('COMPLETED','CONFIRMED','REVIEWED')")
    Long countCompletedOrdersByDate(@Param("date") String date);

    // ========== 待办/状态计数 ==========

    @Select("SELECT COUNT(*) FROM complaint WHERE status = 'PENDING'")
    Long countPendingComplaints();

    @Select("SELECT COUNT(*) FROM complaint WHERE status IN ('PENDING','PROCESSING')")
    Long countPendingProcessingComplaints();

    @Select("SELECT COUNT(*) FROM withdraw WHERE status = 'PENDING'")
    Long countPendingWithdraws();

    @Select("SELECT COUNT(*) FROM `order` WHERE status = 'PAID' AND player_id IS NULL")
    Long countPendingAssignOrders();

    @Select("SELECT COUNT(*) FROM `order` WHERE status = 'IN_PROGRESS'")
    Long countInProgressOrders();

    // ========== 累计统计 ==========

    @Select("SELECT COUNT(*) FROM user")
    Long countTotalUsers();

    @Select("SELECT COUNT(*) FROM player")
    Long countTotalPlayers();

    @Select("SELECT COUNT(*) FROM `order`")
    Long countTotalOrders();

    @Select("SELECT COALESCE(SUM(amount),0) FROM `order` WHERE status NOT IN ('CANCELLED','PENDING_PAYMENT')")
    BigDecimal sumTotalOrderAmount();

    @Select("SELECT COUNT(*) FROM player WHERE status = 'ACTIVE'")
    Long countActivePlayers();

    // ========== 趋势/分布 ==========

    @Select("SELECT DATE(created_at) AS date, COUNT(*) AS orders, COALESCE(SUM(amount),0) AS amount " +
            "FROM `order` WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) GROUP BY DATE(created_at) ORDER BY date")
    List<Map<String, Object>> orderTrend7Days();

    @Select("SELECT status, COUNT(*) AS count FROM `order` GROUP BY status")
    List<Map<String, Object>> orderStatusDistribution();

    @Select("SELECT status, COUNT(*) AS cnt FROM `order` GROUP BY status")
    List<Map<String, Object>> orderStatusDistributionCnt();

    @Select("SELECT DATE(created_at) AS dt, COUNT(*) AS cnt, COALESCE(SUM(amount),0) AS amt " +
            "FROM `order` WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) GROUP BY dt ORDER BY dt")
    List<Map<String, Object>> orderTrend7DaysDt();

    @Select("SELECT COUNT(*) FROM `order` WHERE status NOT IN ('PENDING_PAYMENT')")
    Long countOrdersExcludingPending();

    @Select("SELECT COUNT(*) FROM `order` WHERE status IN ('COMPLETED','CONFIRMED','REVIEWED')")
    Long countCompletedOrders();

    @Select("SELECT DATE(created_at) AS dt, COUNT(*) AS cnt FROM user " +
            "WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) GROUP BY dt ORDER BY dt")
    List<Map<String, Object>> newUserTrend30Days();

    @Select("SELECT COUNT(DISTINCT user_id) FROM `order` WHERE status NOT IN ('CANCELLED','PENDING_PAYMENT')")
    Long countPaidUsers();

    @Select("SELECT DATE(settle_time) AS statDate, COUNT(*) AS orderCount, " +
            "COALESCE(SUM(amount),0) AS orderAmount, " +
            "COALESCE(SUM(settle_amount),0) AS playerIncome, " +
            "COALESCE(SUM(amount - COALESCE(settle_amount, 0)),0) AS commissionIncome " +
            "FROM `order` " +
            "WHERE settled = 1 " +
            "AND settle_time IS NOT NULL " +
            "AND status IN ('CONFIRMED','REVIEWED') " +
            "AND settle_time >= #{start} AND settle_time < #{end} " +
            "GROUP BY DATE(settle_time) ORDER BY statDate")
    List<Map<String, Object>> incomeDailyStatsByRange(@Param("start") LocalDateTime start,
                                                      @Param("end") LocalDateTime end);

    @Select("SELECT DATE(o.settle_time) AS statDate, " +
            "o.id, o.order_no AS orderNo, o.product_name AS productName, " +
            "o.amount, o.settle_amount AS playerIncome, " +
            "(o.amount - COALESCE(o.settle_amount, 0)) AS commissionIncome, " +
            "o.created_at AS createdAt, o.settle_time AS settleTime, " +
            "u.nickname AS userNickname, p.nickname AS playerNickname " +
            "FROM `order` o " +
            "LEFT JOIN user u ON u.id = o.user_id " +
            "LEFT JOIN player p ON p.id = o.player_id " +
            "WHERE o.settled = 1 " +
            "AND o.settle_time IS NOT NULL " +
            "AND o.status IN ('CONFIRMED','REVIEWED') " +
            "AND o.settle_time >= #{start} AND o.settle_time < #{end} " +
            "ORDER BY o.settle_time DESC, o.id DESC")
    List<Map<String, Object>> incomeDailyOrderDetailsByRange(@Param("start") LocalDateTime start,
                                                             @Param("end") LocalDateTime end);

    @Select("SELECT FLOOR(avg_rating) AS rating_level, COUNT(*) AS cnt FROM player " +
            "WHERE avg_rating IS NOT NULL GROUP BY rating_level ORDER BY rating_level")
    List<Map<String, Object>> playerRatingDistribution();

    @Select("SELECT p.id, p.nickname, COALESCE(SUM(t.amount),0) AS total_income " +
            "FROM player p LEFT JOIN transaction t ON t.user_type='PLAYER' AND t.user_id=p.id AND t.type='INCOME' " +
            "GROUP BY p.id, p.nickname ORDER BY total_income DESC LIMIT 10")
    List<Map<String, Object>> playerIncomeRankTop10();

    // ========== 用户消费榜单 ==========

    @Select("SELECT u.id, u.nickname, u.avatar, COUNT(o.id) AS order_count, " +
            "COALESCE(SUM(o.amount),0) AS total_amount " +
            "FROM `order` o INNER JOIN user u ON u.id = o.user_id " +
            "WHERE o.status NOT IN ('CANCELLED','PENDING_PAYMENT') " +
            "GROUP BY u.id ORDER BY total_amount DESC LIMIT #{limit}")
    List<Map<String, Object>> userSpendingRank(@Param("limit") int limit);

    // ========== CS Dashboard 专用 ==========

    @Select("SELECT COALESCE(SUM(amount),0) FROM `order` WHERE status NOT IN ('PENDING_PAYMENT','CANCELLED') AND created_at BETWEEN #{start} AND #{end}")
    BigDecimal sumOrderAmountByDateRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("SELECT COUNT(DISTINCT cs.id) FROM chat_session cs " +
            "INNER JOIN chat_message cm ON cm.session_id = cs.id " +
            "WHERE (cs.id1 >= 3000000000 OR cs.id2 >= 3000000000) AND cs.status = 'ACTIVE' AND cm.is_read = 0 AND cm.sender_type != 'CS'")
    Long countPendingChatSessions();

    /** 客服未读投诉数（待处理且客服未查看） */
    @Select("SELECT COUNT(*) FROM complaint WHERE status IN ('PENDING','PROCESSING') AND cs_read_at IS NULL")
    Long countCsUnreadComplaints();

    // ========== 经营 GMV（payment 口径） ==========

    @Select("SELECT COALESCE(SUM(amount),0) FROM payment " +
            "WHERE biz_type = 'ORDER' AND paid_at IS NOT NULL AND DATE(paid_at) = #{date}")
    BigDecimal sumPaidGrossByDate(@Param("date") String date);

    @Select("SELECT COALESCE(SUM(refund_amount),0) FROM payment " +
            "WHERE biz_type = 'ORDER' AND refund_time IS NOT NULL " +
            "AND DATE(paid_at) = #{date} AND DATE(refund_time) = #{date}")
    BigDecimal sumSameDayRefundByDate(@Param("date") String date);

    @Select("SELECT COALESCE(SUM(refund_amount),0) FROM payment " +
            "WHERE biz_type = 'ORDER' AND refund_time IS NOT NULL AND DATE(refund_time) = #{date}")
    BigDecimal sumRefundAmountByDate(@Param("date") String date);

    @Select("SELECT COUNT(DISTINCT order_id) FROM payment " +
            "WHERE biz_type = 'ORDER' AND paid_at IS NOT NULL AND DATE(paid_at) = #{date}")
    Long countPaidOrdersByDate(@Param("date") String date);

    @Select("SELECT COALESCE(SUM(amount),0) FROM payment " +
            "WHERE biz_type = 'ORDER' AND paid_at IS NOT NULL")
    BigDecimal sumPaidGrossTotal();

    @Select("SELECT COALESCE(SUM(refund_amount),0) FROM payment " +
            "WHERE biz_type = 'ORDER' AND refund_time IS NOT NULL " +
            "AND paid_at IS NOT NULL AND DATE(paid_at) = DATE(refund_time)")
    BigDecimal sumSameDayRefundTotal();

    @Select("SELECT COALESCE(SUM(refund_amount),0) FROM payment " +
            "WHERE biz_type = 'ORDER' AND refund_time IS NOT NULL")
    BigDecimal sumRefundAmountTotal();

    @Select("SELECT COUNT(DISTINCT order_id) FROM payment " +
            "WHERE biz_type = 'ORDER' AND paid_at IS NOT NULL")
    Long countPaidOrdersTotal();

    @Select("SELECT DATE(paid_at) AS date, COUNT(DISTINCT order_id) AS paidOrderCount, " +
            "COALESCE(SUM(amount),0) AS paidGross " +
            "FROM payment WHERE biz_type = 'ORDER' AND paid_at IS NOT NULL " +
            "AND paid_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) " +
            "GROUP BY DATE(paid_at) ORDER BY date")
    List<Map<String, Object>> paidGrossTrend7Days();

    @Select("SELECT DATE(refund_time) AS date, COALESCE(SUM(refund_amount),0) AS refundAmount " +
            "FROM payment WHERE biz_type = 'ORDER' AND refund_time IS NOT NULL " +
            "AND refund_time >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) " +
            "GROUP BY DATE(refund_time) ORDER BY date")
    List<Map<String, Object>> refundTrend7Days();

    @Select("SELECT DATE(paid_at) AS date, COALESCE(SUM(refund_amount),0) AS sameDayRefund " +
            "FROM payment WHERE biz_type = 'ORDER' AND refund_time IS NOT NULL AND paid_at IS NOT NULL " +
            "AND DATE(paid_at) = DATE(refund_time) " +
            "AND paid_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) " +
            "GROUP BY DATE(paid_at) ORDER BY date")
    List<Map<String, Object>> sameDayRefundTrend7Days();
}
