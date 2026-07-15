package com.delta.common.security.filter;

import com.delta.common.security.service.TokenService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final TokenService tokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = tokenService.getToken(request);
        if (token != null) {
            Claims claims = tokenService.parseToken(token);
            if (claims != null) {
                String uuid = claims.get("uuid", String.class);
                Map<String, Object> userContext = tokenService.getUserContext(uuid);
                if (userContext != null) {
                    // 根据 userType 构建角色权限
                    List<org.springframework.security.core.GrantedAuthority> authorities = buildAuthorities(
                            (String) userContext.get("userType"));
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            userContext, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }
        chain.doFilter(request, response);
    }

    private List<org.springframework.security.core.GrantedAuthority> buildAuthorities(String userType) {
        if (userType == null) return Collections.emptyList();
        return switch (userType.toUpperCase()) {
            case "ADMIN" -> List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"));
            case "CS" -> List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CS"));
            case "USER", "PLAYER" -> List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"));
            default -> Collections.emptyList();
        };
    }
}
