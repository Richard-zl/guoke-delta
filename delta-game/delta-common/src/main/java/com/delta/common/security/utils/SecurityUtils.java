package com.delta.common.security.utils;

import com.delta.common.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Map;

public class SecurityUtils {
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getLoginUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Map) {
            return (Map<String, Object>) auth.getPrincipal();
        }
        throw new BusinessException(401, "未登录");
    }

    public static Long getUserId() {
        Object id = getLoginUser().get("userId");
        return id instanceof Number ? ((Number) id).longValue() : Long.parseLong(id.toString());
    }

    public static String getUserType() {
        return (String) getLoginUser().get("userType");
    }

    /**
     * 获取当前登录用户的有效角色 (ADMIN / CS)
     */
    public static String getRole() {
        return getUserType();
    }
}
