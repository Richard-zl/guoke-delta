package com.delta.system.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.delta.common.annotation.OpLog;
import com.delta.common.domain.PageQuery;
import com.delta.common.domain.R;
import com.delta.system.entity.Admin;
import com.delta.system.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/system/admin")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService adminService;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/list")
    public R<Page<Admin>> list(PageQuery query, @RequestParam(value = "username", required = false) String username) {
        LambdaQueryWrapper<Admin> wrapper = new LambdaQueryWrapper<Admin>()
                .like(username != null, Admin::getUsername, username)
                .orderByDesc(Admin::getCreatedAt);
        return R.ok(adminService.page(new Page<>(query.getPageNum(), query.getPageSize()), wrapper));
    }

    @GetMapping("/{id}")
    public R<Admin> getById(@PathVariable Long id) {
        Admin admin = adminService.getById(id);
        if (admin != null) admin.setPassword(null);
        return R.ok(admin);
    }

    @OpLog(module = "system", operation = "新增管理员")
    @PostMapping
    public R<Void> add(@RequestBody Admin admin) {
        adminService.save(admin);
        return R.ok();
    }

    @OpLog(module = "system", operation = "编辑管理员")
    @PutMapping
    public R<Void> update(@RequestBody Admin admin) {
        if (StringUtils.hasText(admin.getPassword())) {
            admin.setPassword(passwordEncoder.encode(admin.getPassword()));
        } else {
            admin.setPassword(null);
        }
        adminService.updateById(admin);
        return R.ok();
    }

    @OpLog(module = "system", operation = "删除管理员")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        adminService.removeById(id);
        return R.ok();
    }
}
