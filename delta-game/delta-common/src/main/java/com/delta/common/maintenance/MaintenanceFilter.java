package com.delta.common.maintenance;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 全局维护模式过滤器：
 * 当 MaintenanceState.enabled=false 时，除 /maintenance/** 外的所有请求直接返回 503。
 */
@Component
@Order(1)
@RequiredArgsConstructor
public class MaintenanceFilter extends OncePerRequestFilter {

    private final MaintenanceState maintenanceState;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        // 维护页面本身始终允许访问
        boolean isMaintenancePath = uri.startsWith("/maintenance");

        if (!maintenanceState.isEnabled() && !isMaintenancePath) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":503,\"msg\":\"系统维护中，请稍后再试\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}

