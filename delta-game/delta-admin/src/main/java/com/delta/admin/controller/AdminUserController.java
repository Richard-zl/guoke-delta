package com.delta.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.annotation.OpLog;
import com.delta.common.domain.PageQuery;
import com.delta.common.domain.R;
import com.delta.common.exception.BusinessException;
import com.delta.common.entity.UserCoupon;
import com.delta.common.service.CouponService;
import com.delta.common.util.QueryDateUtils;
import com.delta.order.entity.Order;
import com.delta.order.service.OrderService;
import com.delta.pay.service.TransactionService;
import com.delta.user.dto.UserListQuery;
import com.delta.user.entity.User;
import com.delta.user.entity.Wallet;
import com.delta.user.service.UserQueryBuilder;
import com.delta.user.service.UserService;
import com.delta.user.service.WalletService;
import com.delta.user.service.PointsService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/user")
@RequiredArgsConstructor
public class AdminUserController {
    private final UserService userService;
    private final WalletService walletService;
    private final OrderService orderService;
    private final TransactionService transactionService;
    private final PointsService pointsService;
    private final CouponService couponService;

    @GetMapping("/list")
    public R<Page<User>> list(PageQuery query,
                              @RequestParam(value = "keyword", required = false) String keyword,
                              @RequestParam(value = "status", required = false) Integer status,
                              @RequestParam(value = "levelCode", required = false) String levelCode,
                              @RequestParam(value = "userId", required = false) Long userId,
                              @RequestParam(value = "createdAtStart", required = false) String createdAtStart,
                              @RequestParam(value = "createdAtEnd", required = false) String createdAtEnd) {
        UserListQuery listQuery = new UserListQuery();
        listQuery.setKeyword(keyword);
        listQuery.setStatus(status);
        listQuery.setLevelCode(levelCode);
        listQuery.setUserId(userId);
        listQuery.setCreatedAtStart(QueryDateUtils.parseStart(createdAtStart));
        listQuery.setCreatedAtEnd(QueryDateUtils.parseEnd(createdAtEnd));

        Page<User> page = userService.page(
                new Page<>(query.getPageNum(), query.getPageSize()),
                UserQueryBuilder.build(listQuery));
        for (User u : page.getRecords()) {
            Wallet wallet = walletService.getByUserId(u.getId());
            u.setBalance(wallet != null ? wallet.getBalance() : java.math.BigDecimal.ZERO);
            u.setAvailableCouponCount(couponService.getUserCouponCount(u.getId()));
        }
        return R.ok(page);
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        Map<String, Object> data = new HashMap<>();
        data.put("user", userService.getById(id));
        data.put("wallet", walletService.getByUserId(id));
        long orderCount = orderService.count(new LambdaQueryWrapper<Order>().eq(Order::getUserId, id));
        data.put("orderCount", orderCount);
        return R.ok(data);
    }

    @OpLog(module = "user", operation = "更新用户状态")
    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam("status") Integer status) {
        User user = new User();
        user.setId(id);
        user.setStatus(status);
        userService.updateById(user);
        return R.ok();
    }

    @OpLog(module = "user", operation = "调整用户余额")
    @PutMapping("/{id}/balance")
    @Transactional(rollbackFor = Exception.class)
    public R<Void> adjustBalance(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        String remark = body.getOrDefault("remark", "").toString();
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException("调整金额不能为0");
        }

        Wallet wallet = walletService.getByUserId(id);
        if (wallet == null) {
            walletService.initWallet(id);
            wallet = walletService.getByUserId(id);
        }

        BigDecimal balanceBefore = wallet.getBalance();
        BigDecimal balanceAfter = balanceBefore.add(amount);
        if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("调整后余额不能为负数");
        }

        wallet.setBalance(balanceAfter);
        walletService.updateById(wallet);

        String type = amount.compareTo(BigDecimal.ZERO) > 0 ? "ADMIN_RECHARGE" : "ADMIN_DEDUCT";
        transactionService.record(type, "USER", id, amount.abs(),
                balanceBefore, balanceAfter, null, null, null,
                "管理员调整: " + (remark.isEmpty() ? (amount.compareTo(BigDecimal.ZERO) > 0 ? "充值" : "扣款") : remark));

        // 后台储值赠积分（扣款不加分）
        if (amount.compareTo(BigDecimal.ZERO) > 0) {
            pointsService.addPointsByRecharge(id, amount);
        }

        return R.ok();
    }

    /**
     * 调整用户当前积分（不影响总积分与等级）
     */
    @OpLog(module = "user", operation = "调整当前积分")
    @PostMapping("/{id}/points/current")
    public R<Void> adjustCurrentPoints(@PathVariable Long id,
                                       @RequestParam Integer points,
                                       @RequestParam(required = false) String remark) {
        if (points == null || points == 0) {
            throw new BusinessException("调整积分不能为0");
        }
        pointsService.adminAdjustCurrentPoints(id, points, remark);
        return R.ok();
    }

    /**
     * 调整用户总积分（影响会员等级）
     */
    @OpLog(module = "user", operation = "调整总积分")
    @PostMapping("/{id}/points/total")
    public R<Void> adjustTotalPoints(@PathVariable Long id,
                                     @RequestParam Integer points,
                                     @RequestParam(required = false) String remark) {
        if (points == null || points == 0) {
            throw new BusinessException("调整积分不能为0");
        }
        pointsService.adminAdjustTotalPoints(id, points, remark);
        return R.ok();
    }

    /**
     * 兼容旧接口：同时调整当前积分与总积分
     */
    @OpLog(module = "user", operation = "调整积分")
    @PostMapping("/{id}/points")
    public R<Void> adjustPoints(@PathVariable Long id,
                                @RequestParam Integer points,
                                @RequestParam(required = false) String remark) {
        pointsService.adminAdjustPoints(id, points, remark);
        return R.ok();
    }

    /**
     * 发放优惠券给用户
     */
    @OpLog(module = "user", operation = "发放优惠券")
    @PostMapping("/{id}/coupon")
    public R<Void> grantCoupon(@PathVariable Long id,
                               @RequestParam Long couponId,
                               @RequestParam(required = false) String remark) {
        couponService.adminGrantCoupon(id, couponId, remark);
        return R.ok();
    }

    /**
     * 查询用户优惠券列表
     */
    @GetMapping("/{id}/coupons")
    public R<Page<UserCoupon>> listUserCoupons(@PathVariable Long id,
                                               PageQuery query,
                                               @RequestParam(required = false) String status) {
        return R.ok(couponService.getUserCouponPage(id, query, status));
    }

    /**
     * 失效用户优惠券
     */
    @OpLog(module = "user", operation = "失效优惠券")
    @PutMapping("/{id}/coupons/{userCouponId}/revoke")
    public R<Void> revokeCoupon(@PathVariable Long id, @PathVariable Long userCouponId) {
        couponService.adminRevokeCoupon(id, userCouponId);
        return R.ok();
    }
}