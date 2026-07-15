package com.delta.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.delta.common.exception.BusinessException;
import com.delta.system.entity.Admin;
import com.delta.system.mapper.AdminMapper;
import com.delta.system.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl extends ServiceImpl<AdminMapper, Admin> implements AdminService {
    private final PasswordEncoder passwordEncoder;

    @Override
    public boolean save(Admin entity) {
        long count = count(new LambdaQueryWrapper<Admin>().eq(Admin::getUsername, entity.getUsername()));
        if (count > 0) throw new BusinessException("用户名已存在");
        entity.setPassword(passwordEncoder.encode(entity.getPassword()));
        return super.save(entity);
    }
}
