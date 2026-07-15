package com.delta.common.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.domain.PageQuery;
import com.delta.common.domain.R;
import com.delta.common.entity.Coupon;
import com.delta.common.entity.UserCoupon;
import com.delta.common.mapper.CouponMapper;
import com.delta.common.mapper.UserCouponMapper;
import com.delta.common.security.utils.SecurityUtils;
import com.delta.common.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/user/coupon")
@RequiredArgsConstructor
public class UserCouponController {

    private final UserCouponMapper userCouponMapper;
    private final CouponMapper couponMapper;
    private final CouponService couponService;

    @GetMapping("/count")
    public R<Long> getCouponCount() {
        Long userId = SecurityUtils.getUserId();
        return R.ok(couponService.getUserCouponCount(userId));
    }

    @GetMapping("/list")
    public R<Page<UserCoupon>> getUserCoupons(PageQuery query,
                                              @RequestParam(required = false) String status) {
        Long userId = SecurityUtils.getUserId();
        Page<UserCoupon> page = couponService.getUserCouponPage(userId, query, status);
        return R.ok(page);
    }

    @GetMapping("/available")
    public R<List<UserCoupon>> getAvailableCoupons(@RequestParam BigDecimal amount) {
        Long userId = SecurityUtils.getUserId();
        List<UserCoupon> allCoupons = userCouponMapper.selectList(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
                        .eq(UserCoupon::getStatus, "UNUSED")
                        .gt(UserCoupon::getExpireTime, LocalDateTime.now())
        );

        List<UserCoupon> available = new ArrayList<>();
        for (UserCoupon uc : allCoupons) {
            Coupon coupon = couponMapper.selectById(uc.getCouponId());
            if (coupon != null) {
                uc.setCouponName(coupon.getName());
                uc.setCouponType(coupon.getType());
                uc.setDiscountRate(coupon.getDiscountRate());
                uc.setCashAmount(coupon.getCashAmount());
                uc.setMinAmount(coupon.getMinAmount());
                uc.setEffectiveStatus(CouponService.resolveEffectiveStatus(uc));

                if (coupon.getMinAmount().compareTo(amount) <= 0) {
                    available.add(uc);
                }
            }
        }

        return R.ok(available);
    }
}
