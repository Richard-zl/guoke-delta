package com.delta.admin.controller;

import com.delta.common.domain.R;
import com.delta.common.entity.Coupon;
import com.delta.common.mapper.CouponMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/coupon")
@RequiredArgsConstructor
public class AdminCouponController {

    private final CouponMapper couponMapper;

    @GetMapping("/list")
    public R<List<Coupon>> list() {
        List<Coupon> list = couponMapper.selectList(null);
        return R.ok(list);
    }
}