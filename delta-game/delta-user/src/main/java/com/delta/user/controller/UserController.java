package com.delta.user.controller;

import com.delta.common.domain.R;
import com.delta.common.security.utils.SecurityUtils;
import com.delta.user.dto.UserProfileVO;
import com.delta.user.entity.User;
import com.delta.user.entity.Wallet;
import com.delta.user.service.UserService;
import com.delta.user.service.WalletService;
import com.delta.user.service.PointsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final WalletService walletService;
    private final PointsService pointsService;

    @GetMapping("/profile")
    public R<UserProfileVO> profile() {
        Long userId = SecurityUtils.getUserId();
        User user = userService.getById(userId);
        Wallet wallet = walletService.getByUserId(userId);
        UserProfileVO vo = new UserProfileVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setPhone(user.getPhone());
        vo.setBalance(wallet != null ? wallet.getBalance() : java.math.BigDecimal.ZERO);
        return R.ok(vo);
    }

    @PutMapping("/profile")
    public R<Void> updateProfile(@RequestBody User user) {
        user.setId(SecurityUtils.getUserId());
        user.setOpenid(null);
        user.setStatus(null);
        userService.updateById(user);
        userService.syncUserProfileToPlayerIfSameOpenid(user.getId());
        return R.ok();
    }

    @PutMapping("/phone")
    public R<Void> bindPhone(@RequestBody java.util.Map<String, String> body) {
        String phone = body != null ? body.getOrDefault("phone", "") : "";
        if (phone.isEmpty()) return R.fail("phone参数不能为空");
        User user = new User();
        user.setId(SecurityUtils.getUserId());
        user.setPhone(phone);
        userService.updateById(user);
        return R.ok();
    }

    /**
     * 获取当前用户的积分明细
     */
    @GetMapping("/points/detail")
    public R<com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.delta.user.entity.PointsDetail>> getPointsDetail(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        Long userId = SecurityUtils.getUserId();
        return R.ok(pointsService.getPointsDetail(userId, pageNum, pageSize));
    }
}