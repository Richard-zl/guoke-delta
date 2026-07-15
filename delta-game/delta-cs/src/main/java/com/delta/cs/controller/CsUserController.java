package com.delta.cs.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.annotation.OpLog;
import com.delta.common.domain.PageQuery;
import com.delta.common.domain.R;
import com.delta.common.entity.UserCoupon;
import com.delta.common.service.CouponService;
import com.delta.common.util.QueryDateUtils;
import com.delta.order.entity.Order;
import com.delta.order.service.OrderService;
import com.delta.user.dto.UserListQuery;
import com.delta.user.entity.User;
import com.delta.user.entity.Wallet;
import com.delta.user.service.UserQueryBuilder;
import com.delta.user.service.UserService;
import com.delta.user.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/cs/user")
@RequiredArgsConstructor
public class CsUserController {
    private final UserService userService;
    private final WalletService walletService;
    private final OrderService orderService;
    private final CouponService couponService;

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        Map<String, Object> data = new HashMap<>();
        data.put("user", userService.getById(id));
        data.put("wallet", walletService.getByUserId(id));
        long orderCount = orderService.count(new LambdaQueryWrapper<Order>().eq(Order::getUserId, id));
        data.put("orderCount", orderCount);
        return R.ok(data);
    }

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
            u.setAvailableCouponCount(couponService.getUserCouponCount(u.getId()));
        }
        return R.ok(page);
    }

    /**
     * 更新用户状态
     * H5端发送 query param: ?status=0/1
     * MP端发送 JSON body: {status: 'ACTIVE'/'DISABLED'} 或 {status: 0/1}
     */
    /**
     * 查询用户优惠券列表（只读）
     */
    @GetMapping("/{id}/coupons")
    public R<Page<UserCoupon>> listUserCoupons(@PathVariable Long id,
                                               PageQuery query,
                                               @RequestParam(required = false) String status) {
        return R.ok(couponService.getUserCouponPage(id, query, status));
    }

    @OpLog(module = "user", operation = "更新用户状态")
    @PutMapping("/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id,
                                 @RequestBody(required = false) Map<String, Object> body,
                                 @RequestParam(value = "status", required = false) Integer statusParam) {
        Integer status = statusParam;
        if (status == null && body != null && body.containsKey("status")) {
            Object val = body.get("status");
            if (val instanceof Number) {
                status = ((Number) val).intValue();
            } else if (val instanceof String s) {
                status = "ACTIVE".equalsIgnoreCase(s) || "1".equals(s) ? 1 : 0;
            }
        }
        if (status == null) return R.fail("status参数不能为空");
        User user = new User();
        user.setId(id);
        user.setStatus(status);
        userService.updateById(user);
        return R.ok();
    }
}
