package com.delta.cs.controller;

import com.delta.common.annotation.OpLog;
import com.delta.common.domain.R;
import com.delta.common.exception.BusinessException;
import com.delta.common.security.utils.SecurityUtils;
import com.delta.system.entity.Admin;
import com.delta.system.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

/**
 * 后台个人资料：改名字、换头像、改自己的登录密码
 */
@RestController
@RequestMapping("/cs/profile")
@RequiredArgsConstructor
public class CsProfileController {
    private final AdminService adminService;
    private final PasswordEncoder passwordEncoder;

    /** 获取当前客服资料 */
    @GetMapping
    public R<Admin> getProfile() {
        Long adminId = SecurityUtils.getUserId();
        Admin admin = adminService.getById(adminId);
        if (admin == null) return R.fail("用户不存在");
        admin.setPassword(null);
        return R.ok(admin);
    }

    /** 更新昵称、头像，以及自己的登录密码 */
    @OpLog(module = "auth", operation = "更新个人资料")
    @PutMapping
    public R<Void> updateProfile(@RequestBody Map<String, String> body) {
        Long adminId = SecurityUtils.getUserId();
        Admin admin = adminService.getById(adminId);
        if (admin == null) return R.fail("用户不存在");
        if (body.containsKey("nickname")) admin.setNickname(body.get("nickname"));
        if (body.containsKey("avatar")) admin.setAvatar(body.get("avatar"));

        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");
        boolean changingPassword = newPassword != null && !newPassword.isBlank();
        if (changingPassword) {
            if (oldPassword == null || oldPassword.isBlank() || !passwordEncoder.matches(oldPassword, admin.getPassword())) {
                throw new BusinessException("原密码不正确");
            }
            if (newPassword.length() < 6) {
                throw new BusinessException("新密码至少 6 位");
            }
            admin.setPassword(passwordEncoder.encode(newPassword));
        }
        adminService.updateById(admin);
        setOpLogDetail(changingPassword ? "修改了登录密码" : "更新了昵称或头像");
        return R.ok();
    }

    private void setOpLogDetail(String detail) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return;
        }
        attrs.getRequest().setAttribute("opLogDetail", detail);
    }
}
