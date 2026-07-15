package com.delta.common.config;

import com.delta.common.security.filter.JwtAuthenticationFilter;
import com.delta.common.security.handler.AccessDeniedHandlerImpl;
import com.delta.common.security.handler.AuthenticationEntryPointImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthenticationEntryPointImpl authenticationEntryPoint;
    private final AccessDeniedHandlerImpl accessDeniedHandler;

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.ignoring().requestMatchers("/file/**");
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e.authenticationEntryPoint(authenticationEntryPoint)
                                     .accessDeniedHandler(accessDeniedHandler))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/user/auth/**", "/player/auth/**", "/cs/auth/**").permitAll()
                .requestMatchers("/pay/wx/notify").permitAll()
                .requestMatchers("/pay/wxkf/callback").permitAll()
                .requestMatchers("/pay/h5/**").permitAll()
                .requestMatchers("/maintenance/**").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers("/file/**", "/common/file/**").permitAll()
                .requestMatchers("/product/list", "/product/recommend", "/product/recommend/categories", "/product/{id}", "/product/category/list", "/product/category/all", "/product/category/tree", "/product/category/*/form-fields").permitAll()
                // 以上 product 接口供匿名访问（含首页热门推荐、热门分类 Tab）
                .requestMatchers("/app/product/**").permitAll()
                .requestMatchers("/order/review/product/**", "/order/review/player/**").permitAll()
                .requestMatchers("/system/banner/active", "/system/notice/active", "/system/content/key/**", "/system/config/site").permitAll()
                .requestMatchers("/doc.html", "/webjars/**", "/v3/api-docs/**", "/swagger-resources/**").permitAll()
                // 下单选打手：普通用户、打手、客服、管理员均可访问（复用客服指派打手接口）
                .requestMatchers("/cs/player/assign-list").hasAnyRole("ADMIN", "CS", "USER")
                // 聊天：普通用户、打手、客服、管理员均可访问
                .requestMatchers("/common/chat/**").hasAnyRole("ADMIN", "CS", "USER")
                // 聊天记录: 管理员和客服均可查看
                .requestMatchers("/admin/chat/**").hasAnyRole("ADMIN", "CS")
                // 角色权限控制: /admin/** 和 /system/** 仅管理员可访问
                .requestMatchers("/admin/**").hasRole("ADMIN")
                // 快捷回复: 管理员和客服均可访问
                .requestMatchers("/system/quick-reply/**").hasAnyRole("ADMIN", "CS")
                .requestMatchers("/system/admin/**", "/system/sensitive-word/**",
                        "/system/notice/**", "/system/banner/**",
                        "/system/config/**", "/system/content/**", "/system/operation-log/**",
                        "/system/statistics/**").hasRole("ADMIN")
                // /cs/** (auth已permitAll) 管理员和客服均可访问
                .requestMatchers("/cs/**").hasAnyRole("ADMIN", "CS")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
