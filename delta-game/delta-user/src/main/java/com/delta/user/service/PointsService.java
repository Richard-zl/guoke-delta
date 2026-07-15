package com.delta.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.delta.common.enums.MemberLevelEnum;
import com.delta.common.exception.BusinessException;
import com.delta.user.entity.User;
import com.delta.user.entity.PointsDetail;
import com.delta.user.mapper.UserMapper;
import com.delta.user.mapper.PointsDetailMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointsService {

    private final UserMapper userMapper;
    private final PointsDetailMapper pointsDetailMapper;

    /**
     * 订单确认完成后发放积分：订单金额 1 元 = 1 积分（向下取整）
     */
    @Transactional
    public void addPointsByOrder(Long userId, BigDecimal amount, Long orderId) {
        int points = amount == null ? 0 : amount.intValue();
        if (points <= 0) return;

        if (orderId != null && pointsDetailMapper.selectCount(
                new LambdaQueryWrapper<PointsDetail>()
                        .eq(PointsDetail::getOrderId, orderId)
                        .eq(PointsDetail::getType, "ORDER_CONSUME")) > 0) {
            log.warn("订单积分已发放，跳过: orderId={}", orderId);
            return;
        }

        User user = userMapper.selectById(userId);
        if (user == null) return;

        int oldPoints = user.getPoints() != null ? user.getPoints() : 0;
        int oldTotalPoints = user.getTotalPoints() != null ? user.getTotalPoints() : 0;
        int newPoints = oldPoints + points;
        int newTotalPoints = oldTotalPoints + points;

        user.setPoints(newPoints);
        user.setTotalPoints(newTotalPoints);

        MemberLevelEnum newLevel = MemberLevelEnum.getByPoints(newTotalPoints);
        user.setLevelCode(newLevel.getCode());
        user.setLevelName(newLevel.getName());

        userMapper.updateById(user);

        // 记录积分明细
        addPointsDetail(userId, points, newPoints, "ORDER_CONSUME",
                "订单消费获得积分", orderId);

        log.info("用户{}订单{}消费{}元，获得{}积分，当前总积分{}，等级{}",
                userId, orderId, amount, points, newTotalPoints, newLevel.getName());
    }

    @Transactional
    public void adminAdjustPoints(Long userId, int points, String remark) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }

        int oldPoints = user.getPoints() != null ? user.getPoints() : 0;
        int newPoints = oldPoints + points;
        if (newPoints < 0) {
            throw new BusinessException("积分不能为负数");
        }

        int oldTotalPoints = user.getTotalPoints() != null ? user.getTotalPoints() : 0;
        int newTotalPoints = oldTotalPoints + points;
        if (newTotalPoints < 0) {
            throw new BusinessException("累计积分不能为负数");
        }

        user.setPoints(newPoints);
        user.setTotalPoints(newTotalPoints);

        MemberLevelEnum newLevel = MemberLevelEnum.getByPoints(newTotalPoints);
        user.setLevelCode(newLevel.getCode());
        user.setLevelName(newLevel.getName());

        userMapper.updateById(user);

        // 记录积分明细
        String type = points > 0 ? "ADMIN_ADD" : "ADMIN_DEDUCT";
        addPointsDetail(userId, points, newPoints, type, remark, null);

        log.info("管理员调整用户{}积分：{}，调整后积分{}，等级{}", userId, points, newPoints, newLevel.getName());
    }

    private void addPointsDetail(Long userId, Integer points, Integer balance,
                                 String type, String remark, Long orderId) {
        PointsDetail detail = new PointsDetail();
        detail.setUserId(userId);
        detail.setPoints(points);
        detail.setBalance(balance);
        detail.setType(type);
        detail.setRemark(remark);
        detail.setOrderId(orderId);
        detail.setCreatedAt(java.time.LocalDateTime.now());
        pointsDetailMapper.insert(detail);
    }

    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<PointsDetail> getPointsDetail(
            Long userId, Integer pageNum, Integer pageSize) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<PointsDetail> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PointsDetail> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(PointsDetail::getUserId, userId)
                .orderByDesc(PointsDetail::getCreatedAt);
        return pointsDetailMapper.selectPage(page, wrapper);
    }
}