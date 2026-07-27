package com.delta.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.delta.common.enums.MemberLevelEnum;
import com.delta.common.exception.BusinessException;
import com.delta.common.mapper.CrossModuleMapper;
import com.delta.user.entity.User;
import com.delta.user.entity.PointsDetail;
import com.delta.user.mapper.UserMapper;
import com.delta.user.mapper.PointsDetailMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointsService {

    /** 1 元对应积分 */
    public static final int YUAN_TO_POINTS = 7;

    public static final String ACCOUNT_CURRENT = "CURRENT";
    public static final String ACCOUNT_TOTAL = "TOTAL";

    private final UserMapper userMapper;
    private final PointsDetailMapper pointsDetailMapper;
    private final CrossModuleMapper crossModuleMapper;

    /**
     * 订单确认完成后发放积分：折后实付 × 7（小数舍掉）；余额支付不加分
     */
    @Transactional
    public void addPointsByOrder(Long userId, BigDecimal amount, Long orderId) {
        if (orderId != null) {
            String payMethod = crossModuleMapper.selectOrderPayMethod(orderId);
            if ("BALANCE".equals(payMethod)) {
                log.info("余额支付订单不加积分: orderId={}", orderId);
                return;
            }
        }

        int points = calcOrderPoints(amount);
        if (points <= 0) {
            return;
        }

        if (orderId != null && pointsDetailMapper.selectCount(
                new LambdaQueryWrapper<PointsDetail>()
                        .eq(PointsDetail::getOrderId, orderId)
                        .eq(PointsDetail::getType, "ORDER_CONSUME")
                        .eq(PointsDetail::getAccountType, ACCOUNT_CURRENT)) > 0) {
            log.warn("订单积分已发放，跳过: orderId={}", orderId);
            return;
        }

        grantBothAccounts(userId, points, "ORDER_CONSUME", "订单消费获得积分", orderId);
        log.info("用户{}订单{}消费{}元，获得{}积分", userId, orderId, amount, points);
    }

    /**
     * 后台储值赠积分：金额 × 7 × 倍率（小数舍掉），双账户同增
     */
    @Transactional
    public void addPointsByRecharge(Long userId, BigDecimal rechargeAmount) {
        int points = calcRechargePoints(rechargeAmount);
        if (points <= 0) {
            return;
        }
        BigDecimal multiplier = resolveRechargeMultiplier(rechargeAmount);
        String remark = "储值赠积分(倍率" + multiplier.stripTrailingZeros().toPlainString() + ")";
        grantBothAccounts(userId, points, "RECHARGE", remark, null);
        log.info("用户{}储值{}元，倍率{}，获得{}积分", userId, rechargeAmount, multiplier, points);
    }

    /** 管理员调整当前积分（不影响总积分与等级） */
    @Transactional
    public void adminAdjustCurrentPoints(Long userId, int delta, String remark) {
        User user = requireUser(userId);
        int oldPoints = nz(user.getPoints());
        int newPoints = oldPoints + delta;
        if (newPoints < 0) {
            throw new BusinessException("当前积分不能为负数");
        }
        user.setPoints(newPoints);
        userMapper.updateById(user);

        String type = delta > 0 ? "ADMIN_ADD" : "ADMIN_DEDUCT";
        String finalRemark = (remark == null || remark.isBlank()) ? "管理员调整当前积分" : remark;
        addPointsDetail(userId, delta, newPoints, type, finalRemark, null, ACCOUNT_CURRENT);
        log.info("管理员调整用户{}当前积分：{}，调整后{}", userId, delta, newPoints);
    }

    /** 管理员调整总积分（影响等级；业务侧通常只增，后台允许下调） */
    @Transactional
    public void adminAdjustTotalPoints(Long userId, int delta, String remark) {
        User user = requireUser(userId);
        int oldTotal = nz(user.getTotalPoints());
        int newTotal = oldTotal + delta;
        if (newTotal < 0) {
            throw new BusinessException("总积分不能为负数");
        }
        user.setTotalPoints(newTotal);
        applyLevel(user, newTotal);
        userMapper.updateById(user);

        String type = delta > 0 ? "TOTAL_ADMIN_ADD" : "TOTAL_ADMIN_DEDUCT";
        String finalRemark = (remark == null || remark.isBlank()) ? "管理员调整总积分" : remark;
        addPointsDetail(userId, delta, newTotal, type, finalRemark, null, ACCOUNT_TOTAL);
        log.info("管理员调整用户{}总积分：{}，调整后{}，等级{}", userId, delta, newTotal, user.getLevelName());
    }

    /**
     * 兼容旧接口：同时调整当前积分与总积分（已废弃，请拆分调用）
     */
    @Deprecated
    @Transactional
    public void adminAdjustPoints(Long userId, int points, String remark) {
        if (points == 0) {
            throw new BusinessException("调整积分不能为0");
        }
        adminAdjustCurrentPoints(userId, points, remark);
        adminAdjustTotalPoints(userId, points, remark);
    }

    /** 订单积分：floor(实付 × 7) */
    public static int calcOrderPoints(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        return amount.multiply(BigDecimal.valueOf(YUAN_TO_POINTS)).setScale(0, RoundingMode.DOWN).intValue();
    }

    /** 储值积分：floor(金额 × 7 × 倍率) */
    public static int calcRechargePoints(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        BigDecimal multiplier = resolveRechargeMultiplier(amount);
        return amount.multiply(BigDecimal.valueOf(YUAN_TO_POINTS))
                .multiply(multiplier)
                .setScale(0, RoundingMode.DOWN)
                .intValue();
    }

    /** 储值倍率：取最高满足档 */
    public static BigDecimal resolveRechargeMultiplier(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ONE;
        }
        if (amount.compareTo(new BigDecimal("10000")) >= 0) {
            return new BigDecimal("1.5");
        }
        if (amount.compareTo(new BigDecimal("8000")) >= 0) {
            return new BigDecimal("1.3");
        }
        if (amount.compareTo(new BigDecimal("5000")) >= 0) {
            return new BigDecimal("1.2");
        }
        if (amount.compareTo(new BigDecimal("2000")) >= 0) {
            return new BigDecimal("1.1");
        }
        return BigDecimal.ONE;
    }

    private void grantBothAccounts(Long userId, int points, String type, String remark, Long orderId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            log.warn("积分发放跳过: 用户不存在 userId={}", userId);
            return;
        }
        int newPoints = nz(user.getPoints()) + points;
        int newTotal = nz(user.getTotalPoints()) + points;
        user.setPoints(newPoints);
        user.setTotalPoints(newTotal);
        applyLevel(user, newTotal);
        userMapper.updateById(user);

        addPointsDetail(userId, points, newPoints, type, remark, orderId, ACCOUNT_CURRENT);
        addPointsDetail(userId, points, newTotal, type, remark, orderId, ACCOUNT_TOTAL);
    }

    private void applyLevel(User user, int totalPoints) {
        MemberLevelEnum level = MemberLevelEnum.getByPoints(totalPoints);
        user.setLevelCode(level.getCode());
        user.setLevelName(level.getName());
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    private int nz(Integer v) {
        return v != null ? v : 0;
    }

    private void addPointsDetail(Long userId, Integer points, Integer balance,
                                 String type, String remark, Long orderId, String accountType) {
        PointsDetail detail = new PointsDetail();
        detail.setUserId(userId);
        detail.setPoints(points);
        detail.setBalance(balance);
        detail.setType(type);
        detail.setRemark(remark);
        detail.setOrderId(orderId);
        detail.setAccountType(accountType);
        detail.setCreatedAt(java.time.LocalDateTime.now());
        pointsDetailMapper.insert(detail);
    }

    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<PointsDetail> getPointsDetail(
            Long userId, Integer pageNum, Integer pageSize) {
        return getPointsDetail(userId, pageNum, pageSize, ACCOUNT_CURRENT);
    }

    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<PointsDetail> getPointsDetail(
            Long userId, Integer pageNum, Integer pageSize, String accountType) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<PointsDetail> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        LambdaQueryWrapper<PointsDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PointsDetail::getUserId, userId)
                .eq(accountType != null && !accountType.isBlank(), PointsDetail::getAccountType, accountType)
                .orderByDesc(PointsDetail::getCreatedAt);
        return pointsDetailMapper.selectPage(page, wrapper);
    }
}
