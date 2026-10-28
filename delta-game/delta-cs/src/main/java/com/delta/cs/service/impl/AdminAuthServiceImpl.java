package com.delta.cs.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.delta.common.constant.CacheConstants;
import com.delta.common.enums.UserTypeEnum;
import com.delta.common.exception.BusinessException;
import com.delta.common.redis.service.RedisService;
import com.delta.common.security.service.TokenService;
import com.delta.cs.dto.AdminLoginRequest;
import com.delta.cs.dto.AdminLoginResponse;
import com.delta.cs.service.AdminAuthService;
import com.delta.system.entity.Admin;
import com.delta.system.entity.OperationLog;
import com.delta.system.service.AdminService;
import com.delta.system.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthServiceImpl implements AdminAuthService {
    private static final int MAX_LOGIN_FAIL_COUNT = 5;
    private static final long LOCK_MINUTES = 30L;

    private final AdminService adminService;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final OperationLogService operationLogService;
    private final RedisService redisService;

    @Override
    public AdminLoginResponse login(AdminLoginRequest request) {
        LocalDateTime now = LocalDateTime.now();
        Admin admin = adminService.getOne(new LambdaQueryWrapper<Admin>().eq(Admin::getUsername, request.getUsername()));
        if (admin == null) throw new BusinessException("账号不存在");
        if (admin.getStatus() == 0) throw new BusinessException("账号已禁用");
        String failKey = buildFailKey(admin.getId());
        if (admin.getLockTime() != null && admin.getLockTime().isAfter(now))
            throw new BusinessException("账号已锁定，请稍后再试");
        boolean hasFailKey = Boolean.TRUE.equals(redisService.hasKey(failKey));
        if (admin.getLockTime() != null || (safeFailCount(admin) > 0 && !hasFailKey)) {
            clearLockState(admin, failKey, false);
        }
        if (!passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            handleLoginFailure(admin, failKey, now);
        }
        // 登录身份以账号角色为准，不再用手选角色
        String accountRole = resolveAccountRole(admin.getRole());
        if (accountRole.isEmpty()) {
            throw new BusinessException("账号角色无效");
        }
        clearLockState(admin, failKey, true);
        admin.setLastLoginAt(now);
        adminService.updateById(admin);
        // 运营、店长沿用管理员接口；菜单和按钮在前端按账号角色收口
        UserTypeEnum userType = "CS".equals(accountRole) ? UserTypeEnum.CS : UserTypeEnum.ADMIN;
        String token = tokenService.createToken(admin.getId(), userType);
        recordLoginLog(admin, accountRole);
        AdminLoginResponse resp = new AdminLoginResponse();
        resp.setToken(token);
        resp.setAdminId(admin.getId());
        resp.setNickname(admin.getNickname());
        resp.setAvatar(admin.getAvatar());
        resp.setRole(accountRole);
        return resp;
    }

    /** 账号角色：ADMIN / CS / OPERATOR / MANAGER。BOTH 视为管理员。 */
    private String resolveAccountRole(String dbRole) {
        if (dbRole == null) {
            return "";
        }
        return switch (dbRole.trim().toLowerCase()) {
            case "admin", "both" -> "ADMIN";
            case "cs" -> "CS";
            case "operator" -> "OPERATOR";
            case "manager" -> "MANAGER";
            default -> "";
        };
    }

    private void handleLoginFailure(Admin admin, String failKey, LocalDateTime now) {
        long failCount = incrementFailCount(failKey);
        int currentFailCount = Math.toIntExact(failCount);
        admin.setLoginFailCount(currentFailCount);
        if (currentFailCount >= MAX_LOGIN_FAIL_COUNT) {
            admin.setLoginFailCount(0);
            admin.setLockTime(now.plusMinutes(LOCK_MINUTES));
            redisService.delete(failKey);
            adminService.updateById(admin);
            throw new BusinessException("密码错误次数过多，账号已锁定30分钟");
        }
        admin.setLockTime(null);
        adminService.updateById(admin);
        throw new BusinessException("密码错误，还可尝试" + (MAX_LOGIN_FAIL_COUNT - currentFailCount) + "次");
    }

    private long incrementFailCount(String failKey) {
        Long failCount = redisService.increment(failKey);
        if (failCount == null) {
            throw new BusinessException("登录失败次数统计异常，请稍后重试");
        }
        if (failCount == 1L) {
            redisService.expire(failKey, LOCK_MINUTES, TimeUnit.MINUTES);
        }
        return failCount;
    }

    private void clearLockState(Admin admin, String failKey, boolean clearLockTime) {
        admin.setLoginFailCount(0);
        if (clearLockTime) {
            admin.setLockTime(null);
        } else if (admin.getLockTime() != null && !admin.getLockTime().isAfter(LocalDateTime.now())) {
            admin.setLockTime(null);
        }
        redisService.delete(failKey);
    }

    private int safeFailCount(Admin admin) {
        return admin.getLoginFailCount() == null ? 0 : admin.getLoginFailCount();
    }

    private String buildFailKey(Long adminId) {
        return CacheConstants.LOGIN_FAIL_KEY + "admin:" + adminId;
    }

    private void recordLoginLog(Admin admin, String role) {
        try {
            OperationLog opLog = new OperationLog();
            opLog.setModule("auth");
            opLog.setOperation("login");
            opLog.setOperatorType(role);
            opLog.setOperatorId(admin.getId());
            opLog.setOperatorName(admin.getNickname());
            opLog.setTargetType("admin");
            opLog.setTargetId(admin.getId());
            opLog.setDetail(admin.getUsername() + " 以 " + role + " 角色登录");
            // 获取请求IP
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                opLog.setIp(attrs.getRequest().getRemoteAddr());
            }
            opLog.setCreatedAt(LocalDateTime.now());
            operationLogService.save(opLog);
        } catch (Exception e) {
            log.warn("记录登录日志失败", e);
        }
    }
}
