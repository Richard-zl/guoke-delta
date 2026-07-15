package com.delta.cs.controller;

import com.delta.common.domain.R;
import com.delta.common.security.utils.SecurityUtils;
import com.delta.system.entity.Admin;
import com.delta.system.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 客服个人资料：改名字、换头像
 */
@RestController
@RequestMapping("/cs/profile")
@RequiredArgsConstructor
public class CsProfileController {
    private final AdminService adminService;

    /** 获取当前客服资料 */
    @GetMapping
    public R<Admin> getProfile() {
        Long adminId = SecurityUtils.getUserId();
        Admin admin = adminService.getById(adminId);
        if (admin == null) return R.fail("用户不存在");
        admin.setPassword(null);
        return R.ok(admin);
    }

    /** 更新昵称和头像 */
    @PutMapping
    public R<Void> updateProfile(@RequestBody Map<String, String> body) {
        Long adminId = SecurityUtils.getUserId();
        Admin admin = adminService.getById(adminId);
        if (admin == null) return R.fail("用户不存在");
        if (body.containsKey("nickname")) admin.setNickname(body.get("nickname"));
        if (body.containsKey("avatar")) admin.setAvatar(body.get("avatar"));
        adminService.updateById(admin);
        return R.ok();
    }
}
