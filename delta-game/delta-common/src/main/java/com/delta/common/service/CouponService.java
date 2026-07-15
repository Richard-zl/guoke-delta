package com.delta.common.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.domain.PageQuery;
import com.delta.common.dto.OrderCouponView;
import com.delta.common.entity.Coupon;
import com.delta.common.entity.UserCoupon;
import com.delta.common.exception.BusinessException;
import com.delta.common.mapper.CouponMapper;
import com.delta.common.mapper.UserCouponMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;

    /** 当月最后一天 23:59:59 */
    public static LocalDateTime calculateMonthEndExpireTime() {
        return YearMonth.now().atEndOfMonth().atTime(23, 59, 59);
    }

    /** 计算券的有效展示状态 */
    public static String resolveEffectiveStatus(UserCoupon uc) {
        if ("USED".equals(uc.getStatus())) {
            return "USED";
        }
        if ("EXPIRED".equals(uc.getStatus())) {
            return "EXPIRED";
        }
        if ("UNUSED".equals(uc.getStatus())) {
            if (uc.getExpireTime() != null && !uc.getExpireTime().isAfter(LocalDateTime.now())) {
                return "EXPIRED";
            }
            return "UNUSED";
        }
        return uc.getStatus();
    }

    public Long getUserCouponCount(Long userId) {
        return userCouponMapper.selectCount(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
                        .eq(UserCoupon::getStatus, "UNUSED")
                        .gt(UserCoupon::getExpireTime, LocalDateTime.now())
        );
    }

    @Transactional
    public void adminGrantCoupon(Long userId, Long couponId, String remark) {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            throw new BusinessException("优惠券不存在");
        }
        if (coupon.getStatus() != null && coupon.getStatus() != 1) {
            throw new BusinessException("该优惠券模板已停用");
        }
        grantCouponToUser(userId, coupon);
        log.info("管理员发放优惠券给用户: {} -> {}, remark={}", userId, coupon.getName(), remark);
    }

    @Transactional
    public void adminRevokeCoupon(Long userId, Long userCouponId) {
        UserCoupon userCoupon = userCouponMapper.selectById(userCouponId);
        if (userCoupon == null || !userCoupon.getUserId().equals(userId)) {
            throw new BusinessException("优惠券不存在");
        }
        if (!"UNUSED".equals(userCoupon.getStatus())) {
            throw new BusinessException("仅可失效未使用的优惠券");
        }
        if (userCoupon.getExpireTime() != null && !userCoupon.getExpireTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException("优惠券已过期，无法操作");
        }
        userCoupon.setStatus("EXPIRED");
        userCouponMapper.updateById(userCoupon);
        log.info("管理员失效用户优惠券: userId={}, userCouponId={}", userId, userCouponId);
    }

    @Transactional
    public void markCouponUsed(Long userCouponId, Long orderId) {
        if (userCouponId == null) return;
        UserCoupon userCoupon = userCouponMapper.selectById(userCouponId);
        if (userCoupon == null) {
            throw new BusinessException("优惠券不存在");
        }
        if (!"UNUSED".equals(userCoupon.getStatus())) {
            throw new BusinessException("优惠券不可用");
        }
        if (userCoupon.getExpireTime() != null && userCoupon.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException("优惠券已过期");
        }
        userCoupon.setStatus("USED");
        userCoupon.setUsedAt(LocalDateTime.now());
        userCoupon.setOrderId(orderId);
        userCouponMapper.updateById(userCoupon);
        log.info("优惠券已核销: userCouponId={}, orderId={}", userCouponId, orderId);
    }

    @Transactional
    public void restoreCouponOnRefund(Long userCouponId, Long orderId) {
        if (userCouponId == null) return;

        UserCoupon userCoupon = userCouponMapper.selectById(userCouponId);
        if (userCoupon == null || !"USED".equals(userCoupon.getStatus())) return;

        LocalDateTime now = LocalDateTime.now();
        if (userCoupon.getExpireTime() != null && userCoupon.getExpireTime().isAfter(now)) {
            userCoupon.setStatus("UNUSED");
            userCoupon.setUsedAt(null);
            userCoupon.setOrderId(null);
            userCouponMapper.updateById(userCoupon);
            log.info("退款退还优惠券: userCouponId={}, orderId={}", userCouponId, orderId);
        } else {
            userCoupon.setStatus("EXPIRED");
            userCouponMapper.updateById(userCoupon);
            log.info("退款时优惠券已过期，标记为EXPIRED: userCouponId={}, orderId={}", userCouponId, orderId);
        }
    }

    public Page<UserCoupon> getUserCouponPage(Long userId, PageQuery query, String status) {
        LambdaQueryWrapper<UserCoupon> wrapper = buildStatusWrapper(userId, status)
                .orderByDesc(UserCoupon::getCreatedAt);

        Page<UserCoupon> page = userCouponMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);

        enrichCouponInfo(page.getRecords());
        return page;
    }

    private LambdaQueryWrapper<UserCoupon> buildStatusWrapper(Long userId, String status) {
        LambdaQueryWrapper<UserCoupon> wrapper = new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId);

        LocalDateTime now = LocalDateTime.now();
        if (status == null || status.isEmpty()) {
            return wrapper;
        }
        return switch (status) {
            case "UNUSED" -> wrapper.eq(UserCoupon::getStatus, "UNUSED").gt(UserCoupon::getExpireTime, now);
            case "USED" -> wrapper.eq(UserCoupon::getStatus, "USED");
            case "EXPIRED" -> wrapper.and(w -> w
                    .eq(UserCoupon::getStatus, "EXPIRED")
                    .or(sub -> sub.eq(UserCoupon::getStatus, "UNUSED").le(UserCoupon::getExpireTime, now)));
            default -> wrapper.eq(UserCoupon::getStatus, status);
        };
    }

    /** 构建订单优惠券展示信息 */
    public OrderCouponView buildOrderCouponView(Long userCouponId, BigDecimal orderAmount) {
        OrderCouponView view = new OrderCouponView();
        BigDecimal finalAmount = normalizeMoney(orderAmount != null ? orderAmount : BigDecimal.ZERO);
        view.setOriginalAmount(finalAmount);
        view.setCouponDiscountAmount(BigDecimal.ZERO);

        if (userCouponId == null) {
            return view;
        }
        UserCoupon userCoupon = userCouponMapper.selectById(userCouponId);
        if (userCoupon == null) {
            return view;
        }
        Coupon coupon = couponMapper.selectById(userCoupon.getCouponId());
        if (coupon == null) {
            return view;
        }
        BigDecimal originalAmount = inferOriginalAmount(finalAmount, coupon);
        view.setCouponName(coupon.getName());
        view.setCouponType(coupon.getType());
        view.setOriginalAmount(originalAmount);
        view.setCouponDiscountAmount(originalAmount.subtract(finalAmount).max(BigDecimal.ZERO));
        return view;
    }

    /** 由折后价反推原价 */
    public static BigDecimal inferOriginalAmount(BigDecimal finalAmount, Coupon coupon) {
        if (finalAmount == null) return BigDecimal.ZERO;
        BigDecimal original = finalAmount;
        if ("DISCOUNT_9".equals(coupon.getType())) {
            original = finalAmount.divide(new BigDecimal("0.9"), 2, RoundingMode.HALF_UP);
        } else if ("DISCOUNT_8".equals(coupon.getType())) {
            original = finalAmount.divide(new BigDecimal("0.8"), 2, RoundingMode.HALF_UP);
        } else if ("DISCOUNT_75".equals(coupon.getType())) {
            original = finalAmount.divide(new BigDecimal("0.75"), 2, RoundingMode.HALF_UP);
        } else if ("CASH_5".equals(coupon.getType())) {
            original = finalAmount.add(new BigDecimal("5"));
        }
        return normalizeMoneyStatic(original);
    }

    private BigDecimal normalizeMoney(BigDecimal amount) {
        return normalizeMoneyStatic(amount);
    }

    private static BigDecimal normalizeMoneyStatic(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    public void enrichCouponInfo(List<UserCoupon> records) {
        for (UserCoupon uc : records) {
            Coupon coupon = couponMapper.selectById(uc.getCouponId());
            if (coupon != null) {
                uc.setCouponName(coupon.getName());
                uc.setCouponType(coupon.getType());
                uc.setDiscountRate(coupon.getDiscountRate());
                uc.setCashAmount(coupon.getCashAmount());
                uc.setMinAmount(coupon.getMinAmount());
            }
            uc.setEffectiveStatus(resolveEffectiveStatus(uc));
        }
    }

    private void grantCouponToUser(Long userId, Coupon coupon) {
        UserCoupon userCoupon = new UserCoupon();
        userCoupon.setUserId(userId);
        userCoupon.setCouponId(coupon.getId());
        userCoupon.setCode(generateCouponCode());
        userCoupon.setStatus("UNUSED");
        userCoupon.setExpireTime(calculateMonthEndExpireTime());
        userCoupon.setCreatedAt(LocalDateTime.now());
        userCouponMapper.insert(userCoupon);
    }

    private String generateCouponCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }
}
