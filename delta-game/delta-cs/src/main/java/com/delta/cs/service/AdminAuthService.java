package com.delta.cs.service;

import com.delta.common.domain.R;
import com.delta.cs.dto.AdminLoginRequest;
import com.delta.cs.dto.AdminLoginResponse;

public interface AdminAuthService {
    AdminLoginResponse login(AdminLoginRequest request);
}
