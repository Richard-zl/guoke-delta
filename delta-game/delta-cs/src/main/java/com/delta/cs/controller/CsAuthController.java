package com.delta.cs.controller;

import com.delta.common.domain.R;
import com.delta.cs.dto.AdminLoginRequest;
import com.delta.cs.dto.AdminLoginResponse;
import com.delta.cs.service.AdminAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cs/auth")
@RequiredArgsConstructor
public class CsAuthController {
    private final AdminAuthService adminAuthService;

    @PostMapping("/login")
    public R<AdminLoginResponse> login(@RequestBody AdminLoginRequest request) {
        return R.ok(adminAuthService.login(request));
    }
}
