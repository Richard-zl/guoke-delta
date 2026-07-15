package com.delta.cs.dto;

import lombok.Data;

@Data
public class AdminLoginResponse {
    private String token;
    private Long adminId;
    private String nickname;
    private String avatar;
    private String role;
}
